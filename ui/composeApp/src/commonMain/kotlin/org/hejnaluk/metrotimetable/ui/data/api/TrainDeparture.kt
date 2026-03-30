package org.hejnaluk.metrotimetable.ui.data.api

import kotlinx.serialization.Serializable

@Serializable
data class TrainDeparture(
    val routeId: String,
    val directionId: Int,
    val departureTime: String,
    val destination: String,
    val upcomingStations: List<String>
)
