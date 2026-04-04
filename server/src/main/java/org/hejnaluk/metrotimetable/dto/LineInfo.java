package org.hejnaluk.metrotimetable.dto;

import java.util.List;

/**
 * A single metro line direction with its ordered station list.
 *
 * @param routeId          the GTFS route ID (e.g. {@code L991} for metro line A)
 * @param directionId      the direction of travel ({@code 0} or {@code 1})
 * @param finalDestination the name of the last stop on this line/direction
 * @param stations         ordered list of all stop names from first to last
 */
public record LineInfo(
        String routeId,
        int directionId,
        String finalDestination,
        List<String> stations
) {}
