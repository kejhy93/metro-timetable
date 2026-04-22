package org.hejnaluk.metrotimetable.ui.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.hejnaluk.metrotimetable.ui.data.api.TripStop
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainDetailScreen(
    routeId: String,
    directionId: Int,
    departureTime: String,
    station: String,
    onBack: () -> Unit,
    viewModel: TrainDetailViewModel = koinViewModel()
) {
    LaunchedEffect(routeId, directionId, departureTime) {
        viewModel.start(routeId, directionId, departureTime)
    }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            val titleText = when (val s = state) {
                is TrainDetailState.Success -> {
                    val now = Clock.System.now()
                    val currentIdx = currentPositionIndex(s.detail.stops, now)
                    val currentStopName = currentIdx?.let { s.detail.stops[it].stopName } ?: s.detail.destination
                    "$currentStopName → ${s.detail.destination}"
                }
                else -> "→ …"
            }
            TopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val s = state) {
                is TrainDetailState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                is TrainDetailState.Error -> Text(
                    text = "Error: ${s.message}",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )

                is TrainDetailState.Success -> TripStopList(
                    stops = s.detail.stops,
                    station = station,
                    updatedAt = s.updatedAt
                )
            }
        }
    }
}

@Composable
private fun TripStopList(
    stops: List<TripStop>,
    station: String,
    updatedAt: LocalTime
) {
    val now = Clock.System.now()
    val currentPositionIdx = currentPositionIndex(stops, now)
    val selectedStationIdx = selectedStationIndex(stops, station)

    val listState = rememberLazyListState()
    val hasScrolled = remember { mutableStateOf(false) }

    LaunchedEffect(stops) {
        if (!hasScrolled.value) {
            val scrollTarget = currentPositionIdx ?: 0
            listState.animateScrollToItem(scrollTarget)
            hasScrolled.value = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Updated: $updatedAt",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(stops) { index, stop ->
                TripStopRow(
                    stop = stop,
                    isCurrentPosition = index == currentPositionIdx,
                    isSelectedStation = index == selectedStationIdx,
                    now = now
                )
            }
        }
    }
}

@Composable
private fun TripStopRow(
    stop: TripStop,
    isCurrentPosition: Boolean,
    isSelectedStation: Boolean,
    now: Instant
) {
    val timeInstant = (stop.arrivalTime ?: stop.departureTime)?.let { Instant.parse(it) }
    val isPast = timeInstant != null && timeInstant < now
    val minutesRelative = timeInstant?.let { (it - now).inWholeMinutes }
    val localTime = timeInstant?.toLocalDateTime(TimeZone.currentSystemDefault())?.time

    val timeText = localTime?.let {
        "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
    } ?: ""

    val countdownText = minutesRelative?.let {
        when {
            it in -1..1 -> "Now"
            it > 0 -> "in $it min"
            else -> "${-it} min ago"
        }
    } ?: ""

    val dimmed = isPast && !isCurrentPosition && !isSelectedStation
    val containerColor = when {
        isCurrentPosition -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val outerModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 3.dp)
        .let { m ->
            if (isSelectedStation) m.border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
            else m
        }

    Surface(
        modifier = outerModifier,
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (dimmed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    else containerColor
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stop.stopName,
                style = if (isCurrentPosition)
                    MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                else
                    MaterialTheme.typography.bodyLarge,
                color = if (dimmed)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Column(horizontalAlignment = Alignment.End) {
                if (timeText.isNotEmpty()) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dimmed)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
                if (countdownText.isNotEmpty()) {
                    Text(
                        text = countdownText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dimmed)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
