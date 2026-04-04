package org.hejnaluk.metrotimetable.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hejnaluk.metrotimetable.dto.LineInfo;
import org.hejnaluk.metrotimetable.dto.TrainDeparture;
import org.hejnaluk.metrotimetable.service.ParseTimetableService;
import org.hejnaluk.metrotimetable.service.TimetableRefreshService;
import org.hejnaluk.metrotimetable.dto.StationRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
     * @param request body containing station name, optional direction filter, and optional result limit
     * @return {@code 200 OK} with the list of upcoming {@link TrainDeparture}s, sorted by departure time
     */
    @PostMapping("/station")
    public ResponseEntity<List<TrainDeparture>> getTrainsForStation(@Valid @RequestBody StationRequest request) {
        int effectiveLimit = request.limit() != null ? request.limit() : StationRequest.DEFAULT_LIMIT;
        log.info("POST /pid/station - station={}, direction={}, limit={}, routeId={}", request.station(), request.direction(), effectiveLimit, request.routeId());
        return ResponseEntity.ok(parseTimetableService.getTrainsForStation(request.station(), request.direction(), effectiveLimit, request.routeId()));
    }

    /**
     * Returns all known metro line/direction combinations with their ordered station lists.
     * <p>
     * Intended to be called once on app startup to build the local navigation structure.
     * Returns an empty list if the timetable cache has not been populated yet.
     *
     * @return {@code 200 OK} with the list of {@link LineInfo} records, one per route-direction pair
     */
    @GetMapping("/lines")
    public ResponseEntity<List<LineInfo>> getLines() {
        log.info("GET /pid/lines - line list requested");
        return ResponseEntity.ok(parseTimetableService.getLines());
    }

}