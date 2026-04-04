package org.hejnaluk.metrotimetable.ui.di

import org.hejnaluk.metrotimetable.ui.data.MetroRepository
import org.hejnaluk.metrotimetable.ui.data.api.MetroApiClient
import org.hejnaluk.metrotimetable.ui.data.local.LineDataSource
import org.hejnaluk.metrotimetable.ui.data.local.LocalLineDataSource
import org.hejnaluk.metrotimetable.ui.apiBaseUrl
import org.hejnaluk.metrotimetable.ui.httpClientEngine
import org.hejnaluk.metrotimetable.ui.presentation.departures.DeparturesViewModel
import org.hejnaluk.metrotimetable.ui.presentation.line.LinesViewModel
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<LineDataSource> { LocalLineDataSource() }
    single { MetroApiClient(httpClientEngine(), apiBaseUrl()) }
    single { MetroRepository(get()) }
    viewModel { DeparturesViewModel(get()) }
    viewModel { LinesViewModel(get(), get()) }
}
