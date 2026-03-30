package org.hejnaluk.metrotimetable.ui.data.local

interface LineDataSource {
    fun getLines(): List<MetroLine>
    fun getStations(lineId: String): List<String>
}
