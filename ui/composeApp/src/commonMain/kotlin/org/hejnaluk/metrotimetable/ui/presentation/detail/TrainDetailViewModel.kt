package org.hejnaluk.metrotimetable.ui.presentation.detail

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

class TrainDetailViewModel(
    private val repository: MetroRepository,
    private val appConfigStore: AppConfigStore
) : ViewModel() {

    private val _state = MutableStateFlow<TrainDetailState>(TrainDetailState.Loading)
    val state: StateFlow<TrainDetailState> = _state.asStateFlow()

    fun start(routeId: String, directionId: Int, departureTime: String) {
        viewModelScope.launch {
            while (true) {
                load(routeId, directionId, departureTime)
                delay(appConfigStore.config.value.tripDetailRefreshIntervalSeconds * 1_000L)
            }
        }
    }

    private suspend fun load(routeId: String, directionId: Int, departureTime: String) {
        repository.fetchTripDetail(routeId, directionId, departureTime).fold(
            onSuccess = { detail ->
                _state.value = TrainDetailState.Success(detail, currentLocalTime())
            },
            onFailure = { error ->
                _state.value = TrainDetailState.Error(error.message ?: "Unknown error")
            }
        )
    }
}
