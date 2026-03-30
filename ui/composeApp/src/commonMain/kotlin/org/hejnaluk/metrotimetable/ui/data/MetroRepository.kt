package org.hejnaluk.metrotimetable.ui.data

import org.hejnaluk.metrotimetable.ui.data.api.MetroApiClient
import org.hejnaluk.metrotimetable.ui.data.api.TrainDeparture

class MetroRepository(private val apiClient: MetroApiClient) {

    suspend fun fetchDepartures(
        station: String,
        direction: Int,
        limit: Int = 10
    ): Result<List<TrainDeparture>> =
        runCatching { apiClient.fetchDepartures(station, direction, limit) }
}
