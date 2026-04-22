package org.hejnaluk.metrotimetable.ui.data

import org.hejnaluk.metrotimetable.ui.data.api.MetroApiClient
import org.hejnaluk.metrotimetable.ui.data.api.TrainDeparture
import org.hejnaluk.metrotimetable.ui.data.api.TripDetail
import org.hejnaluk.metrotimetable.ui.data.local.LineDataSource
import org.hejnaluk.metrotimetable.ui.data.local.MetroLine

class MetroRepository(
    private val apiClient: MetroApiClient,
    private val localDataSource: LineDataSource,
) {
    private val lineByRouteId: Map<String, MetroLine> by lazy {
        localDataSource.getLines().associateBy { it.id }
    }

    suspend fun fetchLines(): Result<List<MetroLine>> = runCatching {
        apiClient.fetchLines()
            .groupBy { it.routeId }
            .mapNotNull { (routeId, directions) ->
                val dir0 = directions.firstOrNull { it.directionId == 0 }
                val dir1 = directions.firstOrNull { it.directionId == 1 }
                val line = lineByRouteId[routeId]
                if (dir0 == null || dir1 == null || line == null) {
                    println("MetroRepository: skipping routeId=$routeId (missingDir0=${dir0 == null}, missingDir1=${dir1 == null}, missingMeta=${line == null})")
                    return@mapNotNull null
                }
                MetroLine(
                    id = routeId,
                    name = line.name,
                    color = line.color,
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

    suspend fun fetchTripDetail(
        routeId: String,
        directionId: Int,
        departureTime: String
    ): Result<TripDetail> =
        runCatching { apiClient.fetchTripDetail(routeId, directionId, departureTime) }
}
