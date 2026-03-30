package org.hejnaluk.metrotimetable.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.hejnaluk.metrotimetable.ui.di.appModule
import org.hejnaluk.metrotimetable.ui.navigation.AppNavigation
import org.koin.compose.KoinApplication

@Composable
fun App() {
    KoinApplication(application = {
        modules(appModule)
    }) {
        MaterialTheme {
            AppNavigation()
        }
    }
}
