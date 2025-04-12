package org.hejnaluk.metrotimetable.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hejnaluk.metrotimetable.client.PIDClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pid")
@RequiredArgsConstructor
@Slf4j
public class PIDController {

    private final PIDClient pidClient;

    @GetMapping
    public ResponseEntity<Void> get() {
        log.info("Hello");
        pidClient.getData();
        return ResponseEntity.ok().build();
    }

}