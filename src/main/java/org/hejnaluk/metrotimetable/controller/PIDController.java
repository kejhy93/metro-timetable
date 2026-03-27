package org.hejnaluk.metrotimetable.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hejnaluk.metrotimetable.dto.TrainDeparture;
import org.hejnaluk.metrotimetable.service.ParseTimetableService;
import org.hejnaluk.metrotimetable.service.TimetableRefreshService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pid")
@RequiredArgsConstructor
@Slf4j
public class PIDController {

    private final TimetableRefreshService timetableRefreshService;
    private final ParseTimetableService parseTimetableService;

    /**
     * Triggers a full timetable download-and-parse cycle.
     *
     * @return {@code 200 OK} with no body once the refresh completes
     */
    @GetMapping
    public ResponseEntity<Void> getTimetableChange() {
        log.info("GET /pid - timetable refresh requested");
        timetableRefreshService.refresh();
        return ResponseEntity.ok().build();
    }

    /**
     * Returns upcoming train departures for a given station.
     *
     * @param station   the station name to query (case-insensitive)
     * @param direction optional direction filter ({@code 0} or {@code 1}); omit to return both directions
     * @param limit     maximum number of results to return (default {@code 5}, capped by server-side max)
     * @return {@code 200 OK} with the list of upcoming {@link TrainDeparture}s, sorted by departure time
     */
    @GetMapping("/station")
    public ResponseEntity<List<TrainDeparture>> getTrainsForStation(
            @RequestParam String station,
            @RequestParam(required = false) Integer direction,
            @RequestParam(defaultValue = "5") Integer limit) {
        int effectiveLimit = limit != null ? limit : 5;
        log.info("GET /pid/station - station={}, direction={}, limit={}", station, direction, effectiveLimit);
        return ResponseEntity.ok(parseTimetableService.getTrainsForStation(station, direction, effectiveLimit));
    }

}