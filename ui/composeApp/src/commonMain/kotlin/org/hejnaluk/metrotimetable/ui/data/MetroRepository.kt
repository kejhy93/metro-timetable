package org.hejnaluk.metrotimetable.ui.data

import androidx.compose.ui.graphics.Color
import org.hejnaluk.metrotimetable.ui.data.api.MetroApiClient
import org.hejnaluk.metrotimetable.ui.data.api.TrainDeparture
import org.hejnaluk.metrotimetable.ui.data.local.MetroLine

private data class LineMetadata(val name: String, val color: Color)

private val LINE_METADATA = mapOf(
    "L991" to LineMetadata("A", Color(0xFF00A562)),
    "L992" to LineMetadata("B", Color(0xFFFFD700)),
    "L993" to LineMetadata("C", Color(0xFFC8102E)),
)

class MetroRepository(private val apiClient: MetroApiClient) {

    suspend fun fetchLines(): Result<List<MetroLine>> = runCatching {
        apiClient.fetchLines()
            .groupBy { it.routeId }
            .mapNotNull { (routeId, directions) ->
                val dir0 = directions.firstOrNull { it.directionId == 0 } ?: return@mapNotNull null
                val dir1 = directions.firstOrNull { it.directionId == 1 } ?: return@mapNotNull null
                val meta = LINE_METADATA[routeId] ?: return@mapNotNull null
                MetroLine(
                    id = routeId,
                    name = meta.name,
                    color = meta.color,
                    stations = dir0.stations,
                    terminus0 = dir0.finalDestination,
                    terminus1 = dir1.finalDestination,
                    terminus0DirectionId = dir0.directionId,
                    terminus1DirectionId = dir1.directionId
                )
            }
            .sortedBy { it.name }
    }

    suspend fun fetchDepartures(
        station: String,
        direction: Int,
        limit: Int = 10,
        routeId: String? = null
    ): Result<List<TrainDeparture>> =
        runCatching { apiClient.fetchDepartures(station, direction, limit, routeId) }
}
