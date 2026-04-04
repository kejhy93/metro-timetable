package org.hejnaluk.metrotimetable.ui.data.local

import androidx.compose.ui.graphics.Color

data class MetroLine(
    val id: String,
    val name: String,
    val color: Color,
    val stations: List<String>,
    val terminus0: String,
    val terminus1: String,
    val terminus0DirectionId: Int,
    val terminus1DirectionId: Int,
)
