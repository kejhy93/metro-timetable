package org.hejnaluk.metrotimetable.ui.data.api

import kotlinx.serialization.Serializable

@Serializable
data class StationRequest(
    val station: String,
    val direction: Int?,
    val limit: Int = 5
)
