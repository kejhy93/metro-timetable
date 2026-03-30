package org.hejnaluk.metrotimetable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

/**
 * A single upcoming train departure from a queried station.
 *
 * @param routeId          the GTFS route ID (e.g. {@code L991} for metro line A)
 * @param directionId      the direction of travel ({@code 0} or {@code 1})
 * @param departureTime    the departure time from the queried station in UTC
 * @param destination      the name of the last stop on this trip
 * @param upcomingStations ordered list of stop names from the queried station to the terminus (inclusive)
 */
public record TrainDeparture(
        String routeId,
        int directionId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant departureTime,
        String destination,
        List<String> upcomingStations
) {}
