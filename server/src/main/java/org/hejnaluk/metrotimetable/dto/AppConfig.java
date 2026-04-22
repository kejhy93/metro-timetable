package org.hejnaluk.metrotimetable.dto;

public record AppConfig(
        long departuresRefreshIntervalSeconds,
        long tripDetailRefreshIntervalSeconds
) {
}
