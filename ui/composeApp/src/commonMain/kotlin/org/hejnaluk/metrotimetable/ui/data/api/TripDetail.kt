package org.hejnaluk.metrotimetable.ui.data.api

import kotlinx.serialization.Serializable

@Serializable
data class TripDetail(
    val routeId: String,
    val directionId: Int,
    val destination: String,
    val stops: List<TripStop>
)
