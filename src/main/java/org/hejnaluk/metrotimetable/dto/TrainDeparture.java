package org.hejnaluk.metrotimetable.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * A single upcoming train departure from a queried station.
 *
 * @param routeId          the GTFS route ID (e.g. {@code L991} for metro line A)
 * @param directionId      the direction of travel ({@code 0} or {@code 1})
 * @param departureTime    the departure time from the queried station
 * @param destination      the name of the last stop on this trip
 * @param upcomingStations ordered list of stop names from the queried station to the terminus (inclusive)
 */
public record TrainDeparture(
        String routeId,
        int directionId,
        LocalTime departureTime,
        String destination,
        List<String> upcomingStations
) {}
