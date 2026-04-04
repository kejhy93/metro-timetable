package org.hejnaluk.metrotimetable.ui.presentation.direction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.hejnaluk.metrotimetable.ui.presentation.line.LinesViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionScreen(
    lineId: String,
    station: String,
    onDirectionSelected: (directionId: Int, destination: String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: LinesViewModel = koinViewModel()
) {
    val lines by viewModel.lines.collectAsState()
    val line = lines.firstOrNull { it.id == lineId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(station) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select direction",
                style = MaterialTheme.typography.titleMedium
            )
            if (line != null) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onDirectionSelected(line.terminus0DirectionId, line.terminus0) }
                ) {
                    Text("→ ${line.terminus0}")
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onDirectionSelected(line.terminus1DirectionId, line.terminus1) }
                ) {
                    Text("→ ${line.terminus1}")
                }
            }
        }
    }
}
