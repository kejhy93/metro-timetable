package org.hejnaluk.metrotimetable.ui.data.api

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val departuresRefreshIntervalSeconds: Long,
    val tripDetailRefreshIntervalSeconds: Long
)
