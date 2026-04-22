package org.hejnaluk.metrotimetable.ui.presentation.detail

import kotlinx.datetime.LocalTime
import org.hejnaluk.metrotimetable.ui.data.api.TripDetail

sealed class TrainDetailState {
    data object Loading : TrainDetailState()
    data class Success(
        val detail: TripDetail,
        val updatedAt: LocalTime
    ) : TrainDetailState()
    data class Error(val message: String) : TrainDetailState()
}
