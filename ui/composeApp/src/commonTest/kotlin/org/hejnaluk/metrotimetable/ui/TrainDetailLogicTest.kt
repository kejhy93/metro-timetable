package org.hejnaluk.metrotimetable.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import org.hejnaluk.metrotimetable.ui.data.api.TripStop
import org.hejnaluk.metrotimetable.ui.presentation.detail.currentPositionIndex
import org.hejnaluk.metrotimetable.ui.presentation.detail.selectedStationIndex

class TrainDetailLogicTest {

    private val t10 = "2026-04-17T10:00:00Z"
    private val t11 = "2026-04-17T11:00:00Z"
    private val t12 = "2026-04-17T12:00:00Z"
    private val t13 = "2026-04-17T13:00:00Z"
    private val t14 = "2026-04-17T14:00:00Z"

    /** Builds a simple stop with the given arrival (null for index 0) and departure (null for last). */
    private fun stop(name: String, arrival: String?, departure: String?) =
        TripStop(stopName = name, arrivalTime = arrival, departureTime = departure)

    @Test
    fun currentPositionIndex_isLastStopWithArrivalTimeInPast() {
        val stops = listOf(
            stop("A", null, t10),    // first stop — arrivalTime null, never counts as past
            stop("B", t11, t11),     // arrivalTime 11:00, past at 12:30
            stop("C", t12, t12),     // arrivalTime 12:00, past at 12:30
            stop("D", t13, t13),     // arrivalTime 13:00, future at 12:30
            stop("E", t14, null)     // last stop
        )
        val now = Instant.parse("2026-04-17T12:30:00Z")

        val result = currentPositionIndex(stops, now)

        assertEquals(2, result)  // C is the last stop with arrivalTime in the past
    }

    @Test
    fun currentPositionIndex_isFirstStop_whenAllStopsAreInFuture() {
        val stops = listOf(
            stop("A", null, t13),    // first stop — arrivalTime null
            stop("B", t13, t13),
            stop("C", t14, null)     // last stop
        )
        val now = Instant.parse("2026-04-17T12:00:00Z")  // before all arrivalTimes

        val result = currentPositionIndex(stops, now)

        assertEquals(0, result)
    }

    @Test
    fun currentPositionIndex_isNull_whenAllStopsAreInPast() {
        val stops = listOf(
            stop("A", null, t10),
            stop("B", t10, t11),
            stop("C", t11, null)     // last stop — arrivalTime 11:00 is in the past
        )
        val now = Instant.parse("2026-04-17T13:00:00Z")  // after all arrivalTimes

        val result = currentPositionIndex(stops, now)

        assertNull(result)
    }

    @Test
    fun selectedStationIndex_matchesPassedStationName() {
        val stops = listOf(
            stop("Depo Hostivař", null, t10),
            stop("Muzeum", t11, t11),
            stop("Zličín", t12, null)
        )

        val result = selectedStationIndex(stops, "Muzeum")

        assertEquals(1, result)
    }

    @Test
    fun selectedStationIndex_isCaseInsensitive() {
        val stops = listOf(
            stop("Depo Hostivař", null, t10),
            stop("Muzeum", t11, null)
        )

        assertEquals(1, selectedStationIndex(stops, "muzeum"))
        assertEquals(1, selectedStationIndex(stops, "MUZEUM"))
    }

    @Test
    fun selectedStationIndex_isNull_whenStationNotInList() {
        val stops = listOf(
            stop("Depo Hostivař", null, t10),
            stop("Muzeum", t11, null)
        )

        assertNull(selectedStationIndex(stops, "Zličín"))
    }
}
