package org.hejnaluk.metrotimetable.ui.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.hejnaluk.metrotimetable.ui.APP_VERSION

class MetroApiClient(engine: HttpClientEngine, private val baseUrl: String) {

    private val client = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        defaultRequest {
            header("X-Client-Version", APP_VERSION)
        }
    }

    suspend fun fetchLines(): List<LineInfo> =
        client.get("$baseUrl/pid/lines").body()

    suspend fun fetchDepartures(
        station: String,
        direction: Int?,
        limit: Int = 10,
        routeId: String? = null
    ): List<TrainDeparture> =
        client.post("$baseUrl/pid/station") {
            contentType(ContentType.Application.Json)
            setBody(StationRequest(station, direction, limit, routeId))
        }.body()

}
