package org.hejnaluk.metrotimetable.ui.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.hejnaluk.metrotimetable.ui.data.api.AppConfig
import org.hejnaluk.metrotimetable.ui.data.api.MetroApiClient

private val DEFAULT_CONFIG = AppConfig(
    departuresRefreshIntervalSeconds = 30L,
    tripDetailRefreshIntervalSeconds = 10L
)
private const val CONFIG_FETCH_INTERVAL_MS = 60_000L

class AppConfigStore(private val apiClient: MetroApiClient) {

    private val _config = MutableStateFlow(DEFAULT_CONFIG)
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    /**
     * Fetches the config from the server and retries on a fixed interval.
     * Call this from a [kotlinx.coroutines.CoroutineScope] tied to the app's lifetime
     * (e.g. inside a [androidx.compose.runtime.LaunchedEffect] in the root composable).
     * Falls back to defaults on any error and retries on the next cycle.
     */
    suspend fun startPolling() {
        while (true) {
            try {
                _config.value = apiClient.fetchConfig()
            } catch (_: Exception) {
                // keep current value (default on first failure, last successful on later failures)
            }
            delay(CONFIG_FETCH_INTERVAL_MS)
        }
    }
}
