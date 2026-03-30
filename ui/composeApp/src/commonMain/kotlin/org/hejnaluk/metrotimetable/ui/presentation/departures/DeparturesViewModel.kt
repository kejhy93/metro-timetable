package org.hejnaluk.metrotimetable.ui.presentation.departures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hejnaluk.metrotimetable.ui.currentLocalTime
import org.hejnaluk.metrotimetable.ui.data.MetroRepository

class DeparturesViewModel(private val repository: MetroRepository) : ViewModel() {

    private val _state = MutableStateFlow<DeparturesState>(DeparturesState.Loading)
    val state: StateFlow<DeparturesState> = _state.asStateFlow()

    fun start(station: String, directionId: Int) {
        viewModelScope.launch {
            while (true) {
                load(station, directionId)
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun load(station: String, directionId: Int) {
        repository.fetchDepartures(station, directionId).fold(
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

    companion object {
        private const val REFRESH_INTERVAL_MS = 30_000L
    }
}
