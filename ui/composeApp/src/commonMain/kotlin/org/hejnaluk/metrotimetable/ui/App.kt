package org.hejnaluk.metrotimetable.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.hejnaluk.metrotimetable.ui.data.AppConfigStore
import org.hejnaluk.metrotimetable.ui.di.appModule
import org.hejnaluk.metrotimetable.ui.navigation.AppNavigation
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

@Composable
fun App() {
    KoinApplication(application = {
        modules(appModule)
    }) {
        val appConfigStore = koinInject<AppConfigStore>()
        LaunchedEffect(Unit) {
            appConfigStore.startPolling()
        }
        MaterialTheme {
            AppNavigation()
        }
    }
}
