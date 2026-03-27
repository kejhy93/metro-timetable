package org.hejnaluk.metrotimetable.dto;

import java.time.LocalTime;
import java.util.List;

public record TrainDeparture(
        String routeId,
        int directionId,
        LocalTime departureTime,
        String destination,
        List<String> upcomingStations
) {}
