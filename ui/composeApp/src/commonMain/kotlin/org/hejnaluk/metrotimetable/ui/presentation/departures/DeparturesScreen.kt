package org.hejnaluk.metrotimetable.ui.presentation.departures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalTime
import org.hejnaluk.metrotimetable.ui.currentLocalTime
import org.hejnaluk.metrotimetable.ui.data.api.TrainDeparture
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeparturesScreen(
    station: String,
    directionId: Int,
    destination: String,
    onBack: () -> Unit,
    viewModel: DeparturesViewModel = koinViewModel()
) {
    LaunchedEffect(station, directionId) {
        viewModel.start(station, directionId)
    }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("$station → $destination") })
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val s = state) {
                is DeparturesState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                is DeparturesState.Error -> Text(
                    text = "Error: ${s.message}",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )

                is DeparturesState.Success -> DeparturesList(
                    departures = s.departures,
                    updatedAt = s.updatedAt
                )
            }
        }
    }
}

@Composable
private fun DeparturesList(departures: List<TrainDeparture>, updatedAt: LocalTime) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Updated: $updatedAt",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(departures) { departure ->
                DepartureItem(departure = departure)
            }
        }
    }
}

@Composable
private fun DepartureItem(departure: TrainDeparture) {
    val now = currentLocalTime()
    val minutesUntil = minutesUntil(departure.departureTime, now)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = departure.destination,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = departure.departureTime,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = if (minutesUntil <= 0) "Now" else "${minutesUntil} min",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun minutesUntil(departureTime: String, now: LocalTime): Long {
    val parts = departureTime.split(":")
    val depSeconds = parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
    val nowSeconds = now.hour.toLong() * 3600 + now.minute.toLong() * 60 + now.second.toLong()
    var diff = depSeconds - nowSeconds
    if (diff < 0) diff += 86400L
    return diff / 60
}
