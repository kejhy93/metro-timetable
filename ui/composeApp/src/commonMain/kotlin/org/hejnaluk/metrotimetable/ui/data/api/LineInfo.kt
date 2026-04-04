package org.hejnaluk.metrotimetable.ui.data.api

import kotlinx.serialization.Serializable

@Serializable
data class LineInfo(
    val routeId: String,
    val directionId: Int,
    val finalDestination: String,
    val stations: List<String>
)
