package org.hejnaluk.metrotimetable.ui.presentation.direction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.hejnaluk.metrotimetable.ui.data.local.LineDataSource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectionScreen(
    lineId: String,
    station: String,
    onDirectionSelected: (directionId: Int, destination: String) -> Unit,
    dataSource: LineDataSource = koinInject()
) {
    val line = dataSource.getLines().firstOrNull { it.id == lineId }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(station) })
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
                    onClick = { onDirectionSelected(1, line.terminus0) }
                ) {
                    Text("→ ${line.terminus0}")
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onDirectionSelected(0, line.terminus1) }
                ) {
                    Text("→ ${line.terminus1}")
                }
            }
        }
    }
}
