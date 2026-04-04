package org.hejnaluk.metrotimetable.ui

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

actual fun currentLocalTime(): LocalTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time

actual fun httpClientEngine(): HttpClientEngine = Js.create()

actual fun apiBaseUrl(): String = ""
