package org.hejnaluk.metrotimetable.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hejnaluk.metrotimetable.client.PIDClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableRefreshService {

    private final PIDClient pidClient;
    private final ParseTimetableService parseTimetableService;
    private final MeterRegistry meterRegistry;

    private final Map<String, Counter> refreshCounters = new HashMap<>();

    @PostConstruct
    void registerMetrics() {
        for (String trigger : List.of("startup", "scheduled", "manual")) {
            for (String outcome : List.of("success", "failure")) {
                refreshCounters.put(trigger + "." + outcome, Counter.builder("timetable.refresh.total")
                        .description("Number of timetable refresh attempts by trigger and outcome")
                        .tag("trigger", trigger)
                        .tag("outcome", outcome)
                        .register(meterRegistry));
            }
        }
    }

    /**
     * Warm the cache on startup. Always parses after startup regardless of whether new data
     * was downloaded, because the in-memory cache is empty after a JVM restart.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Application ready — warming timetable cache");
        try {
            pidClient.getData();
            parseTimetableService.parseTimetableFiles();
            refreshCounter("startup", "success").increment();
        } catch (Exception e) {
            log.error("Startup timetable refresh failed", e);
            refreshCounter("startup", "failure").increment();
            throw e;
        }
    }

    /**
     * Scheduled daily refresh. Only re-parses when the GTFS data was actually re-downloaded,
     * since parsing stale data already in the cache would be redundant.
     * <p>
     * The cron expression is configurable via {@code pid.refresh.cron} (default: daily at 04:00).
     */
    @Scheduled(cron = "${pid.refresh.cron:0 0 4 * * *}")
    public void scheduledRefresh() {
        log.info("Scheduled timetable refresh triggered");
        try {
            boolean newData = pidClient.getData();
            if (newData) {
                parseTimetableService.parseTimetableFiles();
            } else {
                log.info("No new GTFS data — skipping parse");
            }
            refreshCounter("scheduled", "success").increment();
        } catch (Exception e) {
            log.error("Scheduled timetable refresh failed", e);
            refreshCounter("scheduled", "failure").increment();
            throw e;
        }
    }

    /**
     * Forces a full download-and-parse cycle regardless of data freshness.
     * Called by the manual refresh endpoint.
     */
    public void refresh() {
        log.info("Manual timetable refresh triggered");
        try {
            pidClient.getData();
            parseTimetableService.parseTimetableFiles();
            refreshCounter("manual", "success").increment();
        } catch (Exception e) {
            log.error("Manual timetable refresh failed", e);
            refreshCounter("manual", "failure").increment();
            throw e;
        }
    }

    private Counter refreshCounter(String trigger, String outcome) {
        return refreshCounters.get(trigger + "." + outcome);
    }
}
