package org.hejnaluk.metrotimetable.ui.presentation.line

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hejnaluk.metrotimetable.ui.data.local.LineDataSource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineScreen(
    onLineSelected: (lineId: String) -> Unit,
    dataSource: LineDataSource = koinInject()
) {
    val lines = dataSource.getLines()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Prague Metro") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select line",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            lines.forEach { line ->
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = line.color),
                    onClick = { onLineSelected(line.id) }
                ) {
                    Text(
                        text = line.name,
                        fontSize = 28.sp
                    )
                }
            }
        }
    }
}
