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

    @GetMapping
    public ResponseEntity<Void> getTimetableChange() {
        log.info("GET /pid - timetable refresh requested");
        timetableRefreshService.refresh();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/station")
    public ResponseEntity<List<TrainDeparture>> getTrainsForStation(
            @RequestParam String station,
            @RequestParam(required = false) Integer direction,
            @RequestParam(defaultValue = "5") int limit) {
        log.info("GET /pid/station - station={}, direction={}, limit={}", station, direction, limit);
        return ResponseEntity.ok(parseTimetableService.getTrainsForStation(station, direction, limit));
    }

}