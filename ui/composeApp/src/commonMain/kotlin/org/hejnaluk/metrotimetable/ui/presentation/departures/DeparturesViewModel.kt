package org.hejnaluk.metrotimetable.ui.presentation.departures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hejnaluk.metrotimetable.ui.currentLocalTime
import org.hejnaluk.metrotimetable.ui.data.AppConfigStore
import org.hejnaluk.metrotimetable.ui.data.MetroRepository

class DeparturesViewModel(
    private val repository: MetroRepository,
    private val appConfigStore: AppConfigStore
) : ViewModel() {

    private val _state = MutableStateFlow<DeparturesState>(DeparturesState.Loading)
    val state: StateFlow<DeparturesState> = _state.asStateFlow()

    fun start(station: String, directionId: Int, routeId: String? = null) {
        viewModelScope.launch {
            while (true) {
                load(station, directionId, routeId)
                delay(appConfigStore.config.value.departuresRefreshIntervalSeconds * 1_000L)
            }
        }
    }

    private suspend fun load(station: String, directionId: Int, routeId: String? = null) {
        repository.fetchDepartures(station, directionId, routeId = routeId).fold(
            onSuccess = { departures ->
                _state.value = DeparturesState.Success(
                    departures = departures,
                    updatedAt = currentLocalTime()
                )
            },
            onFailure = { error ->
                _state.value = DeparturesState.Error(error.message ?: "Unknown error")
            }
        )
    }
}
