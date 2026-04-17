package org.hejnaluk.metrotimetable.ui.data.api

import kotlinx.serialization.Serializable

@Serializable
data class TripStop(
    val stopName: String,
    val arrivalTime: String?,   // ISO 8601 Instant, null for first stop (terminus — no inbound)
    val departureTime: String?  // ISO 8601 Instant, null for last stop (train terminates here)
)
