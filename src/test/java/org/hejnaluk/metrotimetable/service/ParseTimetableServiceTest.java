package org.hejnaluk.metrotimetable.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hejnaluk.metrotimetable.dto.TrainDeparture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.assertj.core.api.Assertions.assertThat;

class ParseTimetableServiceTest {

    private static final LocalTime FIXED_NOW = LocalTime.of(12, 0);

    private ParseTimetableService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ParseTimetableService(new SimpleMeterRegistry(), Set.of()) {
            @Override
            protected LocalTime getNow() {
                return FIXED_NOW;
            }
        };
        setMaxLimit(service, 15);
        service.resetForTest();
    }

    @Test
    void returnsUpcomingTrainsForStation() {
        // first trip: Muzeum departure at 10:01 (before FIXED_NOW 12:00) → filtered out
        // second trip: Muzeum departure at 13:01 (after FIXED_NOW 12:00) → included
        populateCache("L991-0", Map.of(
                LocalTime.of(10, 0), stopsAt(LocalTime.of(10, 0), "Depo Hostivař", "Muzeum", "Zličín"),
                LocalTime.of(13, 0), stopsAt(LocalTime.of(13, 0), "Depo Hostivař", "Muzeum", "Zličín")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 10);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().departureTime()).isEqualTo(LocalTime.of(13, 1));
    }

    @Test
    void returnsEmptyList_whenCacheEmpty() {
        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyList_whenStationNotFound() {
        populateCache("L991-0", Map.of(
                LocalTime.of(13, 0), stops("Depo Hostivař", "Náměstí Míru", "Zličín")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void isCaseInsensitive() {
        populateCache("L991-0", Map.of(
                LocalTime.of(13, 0), stops("Depo Hostivař", "Muzeum", "Zličín")
        ));

        assertThat(service.getTrainsForStation("muzeum", null, 10)).hasSize(1);
        assertThat(service.getTrainsForStation("MUZEUM", null, 10)).hasSize(1);
    }

    @Test
    void filtersByDirection() {
        populateCache("L991-0", Map.of(
                LocalTime.of(13, 0), stops("Depo Hostivař", "Muzeum", "Zličín")
        ));
        populateCache("L991-1", Map.of(
                LocalTime.of(14, 0), stops("Zličín", "Muzeum", "Depo Hostivař")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", 0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().directionId()).isZero();
    }

    @Test
    void respectsLimit() {
        populateCache("L991-0", Map.of(
                LocalTime.of(13, 0), stopsAt(LocalTime.of(13, 0), "Depo Hostivař", "Muzeum", "Zličín"),
                LocalTime.of(14, 0), stopsAt(LocalTime.of(14, 0), "Depo Hostivař", "Muzeum", "Zličín"),
                LocalTime.of(15, 0), stopsAt(LocalTime.of(15, 0), "Depo Hostivař", "Muzeum", "Zličín"),
                LocalTime.of(16, 0), stopsAt(LocalTime.of(16, 0), "Depo Hostivař", "Muzeum", "Zličín")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 2);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(TrainDeparture::departureTime)
                .containsExactly(
                        LocalTime.of(13, 1),
                        LocalTime.of(14, 1)
                );
    }

    @Test
    void returnsTrainsFromBothDirections_whenDirectionNull() {
        populateCache("L991-0", Map.of(
                LocalTime.of(13, 0), stops("Depo Hostivař", "Muzeum", "Zličín")
        ));
        populateCache("L991-1", Map.of(
                LocalTime.of(14, 0), stops("Zličín", "Muzeum", "Depo Hostivař")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 10);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TrainDeparture::directionId).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void defaultLimit_returnsFive() {
        populateCache("L991-0", Map.of(
                LocalTime.of(13, 0), stops("Depo Hostivař", "Muzeum", "Zličín"),
                LocalTime.of(13, 10), stops("Depo Hostivař", "Muzeum", "Zličín"),
                LocalTime.of(13, 20), stops("Depo Hostivař", "Muzeum", "Zličín"),
                LocalTime.of(13, 30), stops("Depo Hostivař", "Muzeum", "Zličín"),
                LocalTime.of(13, 40), stops("Depo Hostivař", "Muzeum", "Zličín"),
                LocalTime.of(13, 50), stops("Depo Hostivař", "Muzeum", "Zličín")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 5);

        assertThat(result).hasSize(5);
    }

    @Test
    void limitCappedAtMaximum() {
        var cacheEntries = new java.util.HashMap<LocalTime, List<ParseTimetableService.CompleteStop>>();
        for (int i = 0; i < 20; i++) {
            cacheEntries.put(LocalTime.of(13, i), stops("Depo Hostivař", "Muzeum", "Zličín"));
        }
        populateCache("L991-0", cacheEntries);

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 20);

        assertThat(result).hasSize(15);
    }

    @Test
    void upcomingStations_correctOrder() {
        populateCache("L991-0", Map.of(
                LocalTime.of(13, 0), stops("Depo Hostivař", "Skalka", "Muzeum", "Dejvická", "Zličín")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 10);

        assertThat(result).hasSize(1);
        TrainDeparture departure = result.getFirst();
        assertThat(departure.upcomingStations()).containsExactly("Muzeum", "Dejvická", "Zličín");
        assertThat(departure.destination()).isEqualTo("Zličín");
    }

    // --- helpers ---

    /** Builds stops with departure times starting at baseTime, incrementing by 1 minute each stop. */
    private List<ParseTimetableService.CompleteStop> stopsAt(LocalTime baseTime, String... names) {
        List<ParseTimetableService.CompleteStop> list = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            LocalTime t = baseTime.plusMinutes(i);
            list.add(new ParseTimetableService.CompleteStop(
                    ParseTimetableService.Stop.builder().stopId("stop-" + i).stopName(names[i]).build(), t, t));
        }
        return list;
    }

    /** Convenience: builds stops with departure times in the 13:xx range (always after FIXED_NOW 12:00). */
    private List<ParseTimetableService.CompleteStop> stops(String... names) {
        return stopsAt(LocalTime.of(13, 0), names);
    }

    private void populateCache(String key, Map<LocalTime, List<ParseTimetableService.CompleteStop>> trips) {
        service.populateForTest(key, new ConcurrentSkipListMap<>(trips));
    }

    private static void setMaxLimit(ParseTimetableService svc, int value) throws Exception {
        java.lang.reflect.Field field = ParseTimetableService.class.getDeclaredField("maxLimit");
        field.setAccessible(true);
        field.set(svc, value);
    }
}
