package org.hejnaluk.metrotimetable.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hejnaluk.metrotimetable.client.PIDClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableRefreshService {

    private final PIDClient pidClient;
    private final ParseTimetableService parseTimetableService;

    /**
     * Warm the cache on startup. Always parses after startup regardless of whether new data
     * was downloaded, because the in-memory cache is empty after a JVM restart.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Application ready — warming timetable cache");
        pidClient.getData();
        parseTimetableService.parseTimetableFiles();
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
        boolean newData = pidClient.getData();
        if (newData) {
            parseTimetableService.parseTimetableFiles();
        } else {
            log.info("No new GTFS data — skipping parse");
        }
    }

    /**
     * Forces a full download-and-parse cycle regardless of data freshness.
     * Called by the manual refresh endpoint.
     */
    public void refresh() {
        pidClient.getData();
        parseTimetableService.parseTimetableFiles();
    }
}
