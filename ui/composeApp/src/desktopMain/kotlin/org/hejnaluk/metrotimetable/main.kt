package org.hejnaluk.metrotimetable

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Metro Timetable",
    ) {
        App()
    }
}
