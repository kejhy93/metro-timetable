package org.hejnaluk.metrotimetable.ui

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import kotlin.time.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

actual fun currentLocalTime(): LocalTime =
    kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time

actual fun httpClientEngine(): HttpClientEngine = Java.create()
