package org.hejnaluk.metrotimetable.ui.presentation.detail

import kotlin.time.Instant
import org.hejnaluk.metrotimetable.ui.data.api.TripStop

/**
 * Returns the index of the stop representing the train's current timetable position.
 *
 * Rules:
 * - Find the last stop whose [TripStop.arrivalTime] is in the past (≤ [now]).
 *   First stops have a null [TripStop.arrivalTime] and are never counted as past.
 * - If no stop has passed yet → highlight the first stop (index 0).
 * - If all stops are in the past → the train has reached terminus, return null (no highlight).
 */
fun currentPositionIndex(stops: List<TripStop>, now: Instant): Int? {
    val lastPastIdx = stops.indexOfLast { stop ->
        val time = stop.arrivalTime ?: return@indexOfLast false
        Instant.parse(time) <= now
    }
    return when {
        lastPastIdx == -1 -> 0                     // all in the future → first stop
        lastPastIdx == stops.size - 1 -> null      // all past → train at terminus
        else -> lastPastIdx
    }
}

/**
 * Returns the index of the stop matching [stationName] (case-insensitive), or null if not found.
 */
fun selectedStationIndex(stops: List<TripStop>, stationName: String): Int? =
    stops.indexOfFirst { it.stopName.equals(stationName, ignoreCase = true) }
        .takeIf { it != -1 }
