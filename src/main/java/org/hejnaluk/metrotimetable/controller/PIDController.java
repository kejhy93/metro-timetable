package org.hejnaluk.metrotimetable.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hejnaluk.metrotimetable.client.PIDClient;
import org.hejnaluk.metrotimetable.dto.TrainDeparture;
import org.hejnaluk.metrotimetable.service.ParseTimetableService;
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

    private final PIDClient pidClient;
    private final ParseTimetableService parseTimetableService;

    @GetMapping
    public ResponseEntity<Void> getTimetableChange() {
        log.info("Hello");
        pidClient.getData();

        parseTimetableService.parseTimetableFiles();

        return ResponseEntity.ok().build();
    }

    @GetMapping("/station")
    public ResponseEntity<List<TrainDeparture>> getTrainsForStation(
            @RequestParam String station,
            @RequestParam(required = false) Integer direction,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(parseTimetableService.getTrainsForStation(station, direction, limit));
    }

}