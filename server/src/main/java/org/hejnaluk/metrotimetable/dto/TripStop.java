package org.hejnaluk.metrotimetable.dto;

import java.time.Instant;

public record TripStop(
        String stopName,
        Instant arrivalTime,
        Instant departureTime
) {
}
