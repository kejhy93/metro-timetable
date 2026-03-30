package org.hejnaluk.metrotimetable.ui

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@JsFun("() => Date.now()")
private external fun dateNow(): Double

actual fun currentLocalTime(): LocalTime =
    Instant.fromEpochMilliseconds(dateNow().toLong())
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .time

actual fun httpClientEngine(): HttpClientEngine = Js.create()
