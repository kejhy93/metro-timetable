package org.hejnaluk.metrotimetable.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request body for the {@code POST /pid/station} endpoint.
 *
 * @param station   the station name to query (case-insensitive); must not be blank
 * @param direction optional direction filter ({@code 0} or {@code 1}); {@code null} returns both directions
 * @param limit     maximum number of results to return; defaults to {@link #DEFAULT_LIMIT} when {@code null}
 */
public record StationRequest(
        @NotBlank String station,
        @Min(0) @Max(1) Integer direction,
        @Positive Integer limit
) {
    public static final int DEFAULT_LIMIT = 5;
}
