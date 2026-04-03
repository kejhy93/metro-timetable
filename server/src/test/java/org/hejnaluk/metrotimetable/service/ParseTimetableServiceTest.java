package org.hejnaluk.metrotimetable.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hejnaluk.metrotimetable.dto.TrainDeparture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.assertj.core.api.Assertions.assertThat;

class ParseTimetableServiceTest {

    private static final LocalTime FIXED_NOW = LocalTime.of(12, 0);
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 4, 2); // Wednesday
    private static final ZoneId PRAGUE_ZONE = ZoneId.of("Europe/Prague");

    private static final Path FIXTURE_DIR = fixtureDir();

    private static Path fixtureDir() {
        try {
            return Path.of(ParseTimetableServiceTest.class.getClassLoader()
                    .getResource("timetable").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot resolve timetable fixture directory", e);
        }
    }

    private ParseTimetableService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ParseTimetableService(new SimpleMeterRegistry(), Set.of()) {
            @Override
            protected LocalTime getNow() {
                return FIXED_NOW;
            }

            @Override
            protected LocalDate getToday() {
                return FIXED_TODAY;
            }

            @Override
            protected Path getRootPath() {
                return FIXTURE_DIR;
            }
        };
        setMaxLimit(service, 15);
        service.resetForTest();
    }

    // --- getTrainsForStation tests ---

    @Test
    void returnsUpcomingTrainsForStation() {
        // first trip: Muzeum departure at 10:01 (before FIXED_NOW 12:00) → filtered out
        // second trip: Muzeum departure at 13:01 (after FIXED_NOW 12:00) → included
        populateCache("L991-0", Map.of(
                LocalTime.of(10, 0), stopsAt(LocalTime.of(10, 0), "Depo Hostivař", "Muzeum", "Zličín"),
                LocalTime.of(13, 0), stopsAt(LocalTime.of(13, 0), "Depo Hostivař", "Muzeum", "Zličín")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 10, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().departureTime().atZone(PRAGUE_ZONE).toLocalTime()).isEqualTo(LocalTime.of(13, 1));
    }

    @Test
    void returnsEmptyList_whenCacheEmpty() {
        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 10, null);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyList_whenStationNotFound() {
        populateCache("L991-0", Map.of(
                LocalTime.of(13, 0), stops("Depo Hostivař", "Náměstí Míru", "Zličín")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 10, null);

        assertThat(result).isEmpty();
    }

    @Test
    void isCaseInsensitive() {
        populateCache("L991-0", Map.of(
                LocalTime.of(13, 0), stops("Depo Hostivař", "Muzeum", "Zličín")
        ));

        assertThat(service.getTrainsForStation("muzeum", null, 10, null)).hasSize(1);
        assertThat(service.getTrainsForStation("MUZEUM", null, 10, null)).hasSize(1);
    }

    @Test
    void filtersByDirection() {
        populateCache("L991-0", Map.of(
                LocalTime.of(13, 0), stops("Depo Hostivař", "Muzeum", "Zličín")
        ));
        populateCache("L991-1", Map.of(
                LocalTime.of(14, 0), stops("Zličín", "Muzeum", "Depo Hostivař")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", 0, 10, null);

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

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 2, null);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(d -> d.departureTime().atZone(PRAGUE_ZONE).toLocalTime())
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

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 10, null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TrainDeparture::directionId).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void filtersByRouteId_returnsOnlyMatchingRoute() {
        populateCache("L991-0", Map.of(
                LocalTime.of(13, 0), stops("Depo Hostivař", "Muzeum", "Zličín")
        ));
        populateCache("L992-0", Map.of(
                LocalTime.of(13, 30), stops("Černý Most", "Muzeum", "Zličín")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 10, "L991");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().routeId()).isEqualTo("L991");
    }

    @Test
    void returnsAllRoutes_whenRouteIdNull() {
        populateCache("L991-0", Map.of(
                LocalTime.of(13, 0), stops("Depo Hostivař", "Muzeum", "Zličín")
        ));
        populateCache("L992-0", Map.of(
                LocalTime.of(13, 30), stops("Černý Most", "Muzeum", "Zličín")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 10, null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TrainDeparture::routeId).containsExactlyInAnyOrder("L991", "L992");
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

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 5, null);

        assertThat(result).hasSize(5);
    }

    @Test
    void limitCappedAtMaximum() {
        var cacheEntries = new java.util.HashMap<LocalTime, List<ParseTimetableService.CompleteStop>>();
        for (int i = 0; i < 20; i++) {
            cacheEntries.put(LocalTime.of(13, i), stops("Depo Hostivař", "Muzeum", "Zličín"));
        }
        populateCache("L991-0", cacheEntries);

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 20, null);

        assertThat(result).hasSize(15);
    }

    @Test
    void upcomingStations_correctOrder() {
        populateCache("L991-0", Map.of(
                LocalTime.of(13, 0), stops("Depo Hostivař", "Skalka", "Muzeum", "Dejvická", "Zličín")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 10, null);

        assertThat(result).hasSize(1);
        TrainDeparture departure = result.getFirst();
        assertThat(departure.upcomingStations()).containsExactly("Muzeum", "Dejvická", "Zličín");
        assertThat(departure.destination()).isEqualTo("Zličín");
    }

    @Test
    void stationIndex_usesLongestTrip_notFirstEntry() {
        // Trip at 12:01 is firstEntry (earliest) but has only 1 stop — "Muzeum" is absent.
        // Trip at 14:00 has the most stops and includes "Muzeum" at index 1.
        // The station index must be built from the longer trip so "Muzeum" is reachable.
        // With the old firstEntry() approach, "Muzeum" would never be indexed.
        populateCache("L991-0", Map.of(
                LocalTime.of(12, 1), stopsAt(LocalTime.of(12, 1), "Depo Hostivař"),
                LocalTime.of(14, 0), stopsAt(LocalTime.of(14, 0), "Depo Hostivař", "Muzeum", "Skalka", "Zličín")
        ));

        List<TrainDeparture> result = service.getTrainsForStation("Muzeum", null, 10, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().upcomingStations()).containsExactly("Muzeum", "Skalka", "Zličín");
    }

    // --- parseActiveServiceIds tests ---

    @ParameterizedTest(name = "{2}")
    @CsvSource({
        // serviceId, expectedPresent, description
        "MON_FRI,      true,  MON_FRI included on Wednesday (weekday flags match)",
        "SAT_SUN,      false, SAT_SUN excluded on Wednesday (weekend-only flags)",
        "FUTURE_SVC,   false, FUTURE_SVC excluded before its start date 2026-05-01",
        "PAST_SVC,     false, PAST_SVC excluded after its end date 2026-03-31",
        "NEW_EXCEPTION, true, NEW_EXCEPTION added via exception_type=1 on 2026-04-02",
        "ALWAYS_ACTIVE, false, ALWAYS_ACTIVE removed via exception_type=2 on 2026-04-02",
        "ADDED_NEXT_DAY, false, ADDED_NEXT_DAY exception_type=1 is for 2026-04-03 not today",
    })
    void activeServiceIds(String serviceId, boolean expectedPresent, String description) {
        Set<String> result = service.parseActiveServiceIds(FIXED_TODAY);

        if (expectedPresent) {
            assertThat(result).contains(serviceId);
        } else {
            assertThat(result).doesNotContain(serviceId);
        }
    }

    // --- parseTrip tests ---

    @Test
    void parseTrip_emptyServiceIds_returnsEmpty() {
        List<ParseTimetableService.Trip> result = service.parseTrip(Set.of("L991"), Set.of());

        assertThat(result).isEmpty();
    }

    @Test
    void parseTrip_withActiveServiceId_returnsOnlyMatchingRouteAndService() {
        // trips.txt fixture has 2 L991 trips with 1111100-1 and 1 with 1111111-1, plus 1 L992 trip
        List<ParseTimetableService.Trip> result = service.parseTrip(Set.of("L991"), Set.of("1111100-1"));

        assertThat(result).hasSize(2).allMatch(t -> t.routeId().equals("L991"));
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
