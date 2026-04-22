package org.hejnaluk.metrotimetable.dto;

import java.util.List;

public record TripDetail(
        String routeId,
        int directionId,
        String destination,
        List<TripStop> stops
) {
}
