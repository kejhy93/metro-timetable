package org.hejnaluk.metrotimetable.ui.presentation.line

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hejnaluk.metrotimetable.ui.data.MetroRepository
import org.hejnaluk.metrotimetable.ui.data.local.LineDataSource
import org.hejnaluk.metrotimetable.ui.data.local.MetroLine

class LinesViewModel(
    private val repository: MetroRepository,
    localDataSource: LineDataSource
) : ViewModel() {

    private val _lines = MutableStateFlow<List<MetroLine>>(localDataSource.getLines())
    val lines: StateFlow<List<MetroLine>> = _lines.asStateFlow()

    init {
        viewModelScope.launch {
            repository.fetchLines().onSuccess { remote ->
                if (remote.isNotEmpty()) _lines.value = remote
            }
        }
    }
}
