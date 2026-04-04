package org.hejnaluk.metrotimetable.ui.presentation.station

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.hejnaluk.metrotimetable.ui.presentation.line.LinesViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationScreen(
    lineId: String,
    onStationSelected: (station: String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: LinesViewModel = koinViewModel()
) {
    val lines by viewModel.lines.collectAsState()
    val line = lines.firstOrNull { it.id == lineId }
    val stations = line?.stations ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Line ${line?.name ?: lineId}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Filled.Home, contentDescription = "Home")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(stations) { station ->
                Text(
                    text = station,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStationSelected(station) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
                HorizontalDivider()
            }
        }
    }
}
