package org.hejnaluk.metrotimetable.ui

import io.ktor.client.engine.HttpClientEngine
import kotlinx.datetime.LocalTime

expect fun currentLocalTime(): LocalTime
expect fun httpClientEngine(): HttpClientEngine
