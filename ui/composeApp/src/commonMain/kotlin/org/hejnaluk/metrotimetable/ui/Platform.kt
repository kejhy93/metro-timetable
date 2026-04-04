package org.hejnaluk.metrotimetable.ui

import io.ktor.client.engine.HttpClientEngine
import kotlinx.datetime.LocalTime

const val PROD_BASE_URL = "https://hejnaluk.dev"

expect fun currentLocalTime(): LocalTime
expect fun httpClientEngine(): HttpClientEngine
expect fun apiBaseUrl(): String
