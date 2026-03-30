package org.hejnaluk.metrotimetable.ui.presentation.departures

import kotlinx.datetime.LocalTime
import org.hejnaluk.metrotimetable.ui.data.api.TrainDeparture

sealed class DeparturesState {
    data object Loading : DeparturesState()
    data class Success(
        val departures: List<TrainDeparture>,
        val updatedAt: LocalTime
    ) : DeparturesState()
    data class Error(val message: String) : DeparturesState()
}
