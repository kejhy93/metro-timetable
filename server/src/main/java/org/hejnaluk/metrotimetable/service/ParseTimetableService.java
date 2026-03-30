package org.hejnaluk.metrotimetable.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hejnaluk.metrotimetable.dto.TrainDeparture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hejnaluk.metrotimetable.client.PIDClient.ROOT_PATH_FILE;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParseTimetableService {

    private final MeterRegistry meterRegistry;

    /**
     * Get path to the file containing routes information
     * <p>
     * format:
     * route_id,agency_id,route_short_name,route_long_name,route_type,route_url,route_color,route_text_color,is_night,is_regional,is_substitute_transport
     * <p>
     * route_id,route_short_name,route_long_name,route_url,route_color,route_text_color
     */
    public static final String ROUTES_FILE_NAME = "routes.txt";

    /**
     * Columns key for routes.txt
     */
    public static final int ROUTE_ROUTE_ID = 0;
    public static final int ROUTE_ROUTE_SHORT_NAME = 2;
    public static final int ROUTE_ROUTE_LONG_NAME = 3;
    public static final int ROUTE_ROUTE_URL = 5;
    public static final int ROUTE_ROUTE_COLOR = 6;
    public static final int ROUTE_ROUTE_TEXT_COLOR = 7;

    /**
     * Get path to the file containing stops information
     * <p>
     * format:
     * stop_id,stop_name,stop_lat,stop_lon,zone_id,stop_url,location_type,parent_station,wheelchair_boarding,level_id,platform_code,asw_node_id,asw_stop_id,zone_region_type
     * <p>
     * stop_id,stop_name
     */
    public static final String STOPS_FILE_NAME = "stops.txt";

    /**
     * Columns key for stops.txt
     */
    public static final int STOPS_STOP_ID = 0;
    public static final int STOPS_STOP_NAME = 1;

    /**
     * Expected set of routes to be parsed
     */
    @Value("${pid.client.routes.ids:}")
    public final Set<String> routeIds;

    @Value("${pid.client.station.max-limit:15}")
    private int maxLimit;

    /**
     * route_id,direction_id,stop_id,stop_sequence
     */
    public static final String ROUTE_STOPS_FILE_NAME = "route_stops.txt";
    /**
     * Columns key for route_stops.txt
     */
    public static final int ROUTE_STOP_ROUTE_ID = 0;
    public static final int ROUTE_STOP_DIRECTION_ID = 1;
    public static final int ROUTE_STOP_STOP_ID = 2;
    public static final int ROUTE_STOP_STOP_SEQUENCE = 3;

    /**
     * Get path to the file containing stop times information
     * <p>
     * format:
     * trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,trip_operation_type,bikes_allowed
     * <p>
     * trip_id,arrival_time,departure_time,stop_id
     */
    public static final String STOP_TIME_FILE_NAME = "stop_times.txt";
    /**
     * Columns key for stop_times.txt
     */
    public static final int STOP_TIME_TRIP_ID = 0;
    public static final int STOP_TIME_ARRIVAL_TIME = 1;
    public static final int STOP_TIME_DEPARTURE_TIME = 2;
    public static final int STOP_TIME_STOP_ID = 3;

    /**
     * Get path to the file containing trips information
     * <p>
     * format:
     * route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id,wheelchair_accessible,bikes_allowed,exceptional,sub_agency_id
     * <p>
     * route_id,trip_id,trip_headsign,trip_short_name,direction_id
     */
    public static final String TRIP_FILE_NAME = "trips.txt";
    /**
     * Columns key for trips.txt
     */
    public static final int TRIP_ROUTE_ID = 0;
    public static final int TRIP_TRIP_ID = 2;
    public static final int TRIP_TRIP_HEADSIGN = 3;
    public static final int TRIP_TRIP_SHORT_NAME = 4;
    public static final int TRIP_DIRECTION_ID = 5;

    public static final String DELIMITER = ",";
    public static final String ERROR_READING_FILE_ERROR_MESSAGE = "Error reading file: {}";
    public static final String NEW_LINE_AND_TAB = "\n\t";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ZoneId PRAGUE_ZONE = ZoneId.of("Europe/Prague");

    /**
     * Immutable snapshot of parsed timetable data, swapped atomically on each refresh.
     *
     * @param routeCache   maps "routeId-directionId" → sorted map of departure time → stops for that trip
     * @param stationIndex maps station name (lowercase) → cache key → stop position within a trip,
     *                     enabling O(1) station lookup without scanning every trip
     */
    private record TimetableData(
            Map<String, ConcurrentSkipListMap<LocalTime, List<CompleteStop>>> routeCache,
            Map<String, Map<String, Integer>> stationIndex
    ) {
        static TimetableData empty() {
            return new TimetableData(Map.of(), Map.of());
        }
    }

    private volatile TimetableData timetableData = TimetableData.empty();

    /**
     * Downloads (if stale), parses all GTFS files, and atomically replaces the in-memory cache.
     * <p>
     * Runs in two timed phases:
     * <ol>
     *   <li><b>file.parse</b> — reads {@code routes.txt}, {@code stops.txt}, {@code trips.txt},
     *       {@code route_stops.txt}, and {@code stop_times.txt} in parallel where possible.</li>
     *   <li><b>create.cache</b> — builds the route cache and station index directly from parsed
     *       data, then swaps them atomically so in-flight queries always see a consistent snapshot.</li>
     * </ol>
     */
    public void parseTimetableFiles() {
        log.info("----------------------- PARSING START ------------------------");
        io.micrometer.core.instrument.Timer fileParseTimer = io.micrometer.core.instrument.Timer.builder("file.parse")
                .description("Time taken to parse files")
                .register(meterRegistry);
        io.micrometer.core.instrument.Timer.Sample fileParseTimeSample = Timer.start();

        // Phase 1: Parse route stop IDs (filtered by routeIds) + trips in parallel
        final var neededStopIdsFuture = CompletableFuture.supplyAsync(this::parseRouteStopIds);
        final var tripFuture = CompletableFuture.supplyAsync(() -> parseTrip(routeIds));
        CompletableFuture.allOf(neededStopIdsFuture, tripFuture).join();

        final var neededStopIds = neededStopIdsFuture.join();
        final var tripList = tripFuture.join();

        // Phase 2: Load only the stops that are actually referenced by the filtered route stops
        final var stopsMap = parseStops(neededStopIds);

        // Build tripId → cache key; small map, only relevant trips present.
        final Map<String, String> tripToKey = HashMap.newHashMap(tripList.size());
        for (final Trip trip : tripList) {
            tripToKey.put(trip.tripId(), trip.routeId() + "-" + trip.directionId());
        }

        fileParseTimeSample.stop(fileParseTimer);
        log.info("----------------------- PARSING DONE ------------------------");

        log.info("------------------------ CACHE START ------------------------");
        io.micrometer.core.instrument.Timer cacheBuildTimer = io.micrometer.core.instrument.Timer.builder("create.cache")
                .description("Time taken to create cache")
                .register(meterRegistry);
        io.micrometer.core.instrument.Timer.Sample cacheBuildTimeSample = Timer.start();

        // Stream stop_times.txt trip-by-trip directly into the cache.
        // ASSUMPTION: rows are sorted by trip_id (standard GTFS ordering from PID).
        // Peak memory = one trip's CompleteStop list + growing cache,
        // instead of all trips' data collected into a map before cache building starts.
        final Map<String, ConcurrentSkipListMap<LocalTime, List<CompleteStop>>> newRouteCache = new HashMap<>();
        streamStopTimesIntoCache(stopsMap, tripToKey, newRouteCache);

        // Build station index: station name (lowercase) → cache key → stop position within a trip.
        // Uses the first trip per key to determine stop positions (all trips share the same stop order).
        final Map<String, Map<String, Integer>> newStationIndex = new HashMap<>();
        for (Map.Entry<String, ConcurrentSkipListMap<LocalTime, List<CompleteStop>>> entry : newRouteCache.entrySet()) {
            final String key = entry.getKey();
            final var trips = entry.getValue();
            if (trips.isEmpty()) continue;
            final List<CompleteStop> firstTrip = trips.firstEntry().getValue();
            for (int i = 0; i < firstTrip.size(); i++) {
                final String name = firstTrip.get(i).stop().stopName().toLowerCase();
                newStationIndex.computeIfAbsent(name, k -> new HashMap<>()).put(key, i);
            }
        }

        // Atomic swap: readers always see a complete, consistent snapshot.
        timetableData = new TimetableData(newRouteCache, newStationIndex);

        cacheBuildTimeSample.stop(cacheBuildTimer);
        log.info("------------------------ CACHE DONE: {} route-direction keys, {} stations ------------------------",
                newRouteCache.size(), newStationIndex.size());
    }

    /**
     * Returns the current Prague local time. Overridable in tests to control the clock.
     * GTFS timetable data from PID uses Prague local time, so comparisons must use the same zone.
     *
     * @return the current {@link LocalTime} in Europe/Prague
     */
    protected LocalTime getNow() {
        return LocalTime.now(PRAGUE_ZONE);
    }

    /**
     * Returns upcoming train departures from the given station.
     * <p>
     * Looks up the station in the station index for O(1) access, then collects departures
     * whose departure time is not before the current time. Results are sorted by departure
     * time and capped at {@code limit} (clamped to {@code [0, maxLimit]}).
     *
     * @param stationName the station name to query (case-insensitive)
     * @param direction   optional direction filter ({@code 0} or {@code 1}); {@code null} returns both directions
     * @param limit       maximum number of departures to return; clamped to {@code [0, maxLimit]}
     * @return list of upcoming {@link TrainDeparture}s sorted by departure time, or an empty list if the station is not found
     */
    public List<TrainDeparture> getTrainsForStation(String stationName, Integer direction, int limit) {
        Timer timer = Timer.builder("station.query")
                .description("Time taken to query trains for a station")
                .register(meterRegistry);
        Timer.Sample sample = Timer.start();

        // Snapshot once — guarantees a consistent route cache + station index pair.
        final TimetableData snapshot = timetableData;
        final Map<String, Integer> keyToStopIndex = snapshot.stationIndex().get(stationName.toLowerCase());
        if (keyToStopIndex == null) {
            sample.stop(timer);
            return List.of();
        }

        final LocalTime now = getNow();
        final int effectiveLimit = Math.clamp(limit, 0, maxLimit);

        List<TrainDeparture> result = keyToStopIndex.entrySet().stream()
                .filter(e -> direction == null || e.getKey().endsWith("-" + direction))
                .flatMap(e -> collectDepartures(e.getKey(), e.getValue(), snapshot, now))
                .sorted(Comparator.comparing(TrainDeparture::departureTime))
                .limit(effectiveLimit)
                .toList();

        sample.stop(timer);
        return result;
    }

    /**
     * Streams {@link TrainDeparture}s for a single route-direction key at the given stop index.
     * <p>
     * Only trips where the stop at {@code stopIndex} departs at or after {@code now} are included.
     * The destination is taken from the last stop of the trip; upcoming stations are all stops
     * from {@code stopIndex} onwards.
     *
     * @param key       the cache key in {@code "routeId-directionId"} format
     * @param stopIndex the position of the queried station within each trip's stop list
     * @param snapshot  the consistent timetable snapshot to read from
     * @param now       the current time used to filter out past departures
     * @return a stream of departures for this key, possibly empty
     */
    private Stream<TrainDeparture> collectDepartures(String key, int stopIndex, TimetableData snapshot, LocalTime now) {
        final int lastDash = key.lastIndexOf('-');
        final String routeId = key.substring(0, lastDash);
        final int directionId = Integer.parseInt(key.substring(lastDash + 1));

        final ConcurrentSkipListMap<LocalTime, List<CompleteStop>> trips = snapshot.routeCache().get(key);
        if (trips == null) return Stream.empty();

        // TODO: LocalDate.now(PRAGUE_ZONE) is inaccurate for GTFS trips that belong to the
        //  previous service day (i.e. originally >24h times, normalized via modulo in parseGtfsTime).
        //  Around midnight, these trips should use the previous day's service date rather than today.
        //  A proper fix requires threading the service date (from calendar.txt/calendar_dates.txt)
        //  through the data model.
        final LocalDate today = LocalDate.now(PRAGUE_ZONE);
        return trips.values().stream()
                .filter(stops -> stopIndex < stops.size())
                .filter(stops -> !stops.get(stopIndex).departureTime().isBefore(now))
                .map(stops -> new TrainDeparture(
                        routeId,
                        directionId,
                        stops.get(stopIndex).departureTime().atDate(today).atZone(PRAGUE_ZONE).toInstant(),
                        stops.getLast().stop().stopName(),
                        stops.subList(stopIndex, stops.size()).stream().map(cs -> cs.stop().stopName()).toList()
                ));
    }

    /**
     * Formats a given `LocalTime` object into a string representation.
     * <p>
     * The time is formatted using the pattern `HH:mm:ss`, which represents
     * hours, minutes, and seconds in a 24-hour format.
     *
     * @param localTime The `LocalTime` object to format.
     * @return A string representation of the time in `HH:mm:ss` format.
     */
    private static String getFormattedTime(LocalTime localTime) {
        return TIME_FORMATTER.format(localTime);
    }

    // --- Package-private test support ---

    void resetForTest() {
        timetableData = TimetableData.empty();
    }

    void populateForTest(String key, ConcurrentSkipListMap<LocalTime, List<CompleteStop>> trips) {
        final Map<String, ConcurrentSkipListMap<LocalTime, List<CompleteStop>>> routeCache =
                new HashMap<>(timetableData.routeCache());
        routeCache.put(key, trips);

        final Map<String, Map<String, Integer>> stationIdx = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> e : timetableData.stationIndex().entrySet()) {
            stationIdx.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        if (!trips.isEmpty()) {
            final List<CompleteStop> firstTrip = trips.firstEntry().getValue();
            for (int i = 0; i < firstTrip.size(); i++) {
                final String name = firstTrip.get(i).stop().stopName().toLowerCase();
                stationIdx.computeIfAbsent(name, k -> new HashMap<>()).put(key, i);
            }
        }
        timetableData = new TimetableData(routeCache, stationIdx);
    }

    /**
     * Returns the stop ID for the platform on the opposite track.
     * <p>
     * Prague metro stop IDs encode the platform direction in the second-to-last character:
     * {@code '1'} and {@code '2'} are opposing platforms of the same physical station.
     * This method swaps {@code '1'} ↔ {@code '2'} to find the counterpart stop when
     * a stop ID from {@code stop_times.txt} is not present in {@code stops.txt}.
     *
     * @param stopId the original stop ID, may be {@code null} or shorter than 2 characters
     * @return the stop ID with the second-to-last character toggled between {@code '1'} and {@code '2'},
     *         or the original value unchanged if it does not match this pattern
     */
    private String findOppositeStopId(String stopId) {
        if (stopId == null || stopId.length() < 2) {
            log.warn("Cannot find opposite stop ID for invalid stopId: {}", stopId);
            return stopId;
        }
        char secondLastChar = stopId.charAt(stopId.length() - 2);
        char newSecondLastChar = switch (secondLastChar) {
            case '1' -> '2';
            case '2' -> '1';
            default -> secondLastChar;
        };
        return stopId.substring(0, stopId.length() - 2) + newSecondLastChar + stopId.charAt(stopId.length() - 1);
    }

    /**
     * Parses the `trips.txt` file and returns a list of Trip objects.
     * <p>
     * The file is expected to have the following format:
     * route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id,wheelchair_accessible,bikes_allowed,exceptional,sub_agency_id
     * <p>
     * Only trips with route IDs present in the provided `routeIds` set are included in the result.
     * Each line is split using the specified delimiter, and the resulting data is mapped
     * to a Trip object using the builder pattern.
     * <p>
     * If an error occurs while reading the file, an empty list is returned, and an error
     * message is logged.
     *
     * @param routeIds A set of route IDs to filter the trips.
     * @return A list of Trip objects parsed from the `trips.txt` file.
     */
    private List<Trip> parseTrip(Set<String> routeIds) {
        try (Stream<String> lines = Files.lines(Path.of(ROOT_PATH_FILE, TRIP_FILE_NAME))) {
            return lines
                    .map(line -> line.split(DELIMITER, TRIP_DIRECTION_ID + 2))
                    .filter(line -> routeIds.contains(line[TRIP_ROUTE_ID]))
                    .map(line -> Trip.builder()
                            .routeId(line[TRIP_ROUTE_ID])
                            .tripId(line[TRIP_TRIP_ID])
                            .tripHeadsign(line[TRIP_TRIP_HEADSIGN])
                            .tripShortName(line[TRIP_TRIP_SHORT_NAME])
                            .directionId(line[TRIP_DIRECTION_ID])
                            .build())
                    .toList();
        } catch (IOException e) {
            log.error(ERROR_READING_FILE_ERROR_MESSAGE, TRIP_FILE_NAME, e);
            return List.of();
        }
    }

    /**
     * Reads {@code stop_times.txt} trip-by-trip and builds cache entries on-the-fly.
     * <p>
     * Relies on the file being sorted by {@code trip_id} (standard GTFS ordering from PID).
     * At any point only the current trip's {@link CompleteStop} list accumulates in memory;
     * once the trip_id changes, the list is flushed into {@code cache} and the reference dropped,
     * so peak memory is O(stops_per_trip) rather than O(all_stops_across_all_trips).
     * <p>
     * Stop resolution and opposite-platform fallback are applied inline, eliminating the
     * intermediate {@code StopTime} collection that previously existed as a full in-memory map.
     * Irrelevant rows are filtered by a fast trip_id prefix check before any {@code split} occurs.
     */
    private void streamStopTimesIntoCache(
            Map<String, Stop> stopsMap,
            Map<String, String> tripToKey,
            Map<String, ConcurrentSkipListMap<LocalTime, List<CompleteStop>>> cache) {
        try (BufferedReader reader = Files.newBufferedReader(Path.of(ROOT_PATH_FILE, STOP_TIME_FILE_NAME))) {
            String headerLine = reader.readLine();
            if (headerLine == null)
                return; // skip header; empty file → nothing to parse

            final TripAccumulator acc = new TripAccumulator();
            String line;
            while ((line = reader.readLine()) != null) {
                // Extract trip_id before any split — skips irrelevant rows with zero allocation.
                // When comma <= 0 (malformed line), tripId is null and tripToKey.get(null) returns null,
                // so both guard cases collapse into the single key == null check below.
                int comma = line.indexOf(DELIMITER);
                final String tripId = comma > 0 ? line.substring(0, comma) : null;
                final String key = tripToKey.get(tripId);
                if (key == null) continue;

                if (!tripId.equals(acc.currentTripId)) {
                    acc.onNewTrip(tripId, key, cache);
                }
                acc.currentStops.add(parseCompleteStop(line, stopsMap));
            }

            acc.onNewTrip(null, null, cache); // flush the last trip
        } catch (IOException e) {
            log.error(ERROR_READING_FILE_ERROR_MESSAGE, STOP_TIME_FILE_NAME, e);
        }
    }

    /**
     * Accumulates stops for the current trip and flushes them into the cache on trip transitions.
     * Tracks flushed trip IDs to warn if {@code stop_times.txt} is not sorted by {@code trip_id}.
     */
    private class TripAccumulator {
        String currentTripId = null;
        String currentKey = null;
        List<CompleteStop> currentStops = new ArrayList<>();
        private final Set<String> flushedTripIds = new HashSet<>();

        void onNewTrip(String tripId, String key, Map<String, ConcurrentSkipListMap<LocalTime, List<CompleteStop>>> cache) {
            warnIfUnsorted(tripId);
            if (currentTripId != null) flushedTripIds.add(currentTripId);
            flush(cache);
            currentTripId = tripId;
            currentKey = key;
            currentStops = new ArrayList<>();
        }

        private void warnIfUnsorted(String tripId) {
            if (flushedTripIds.contains(tripId)) {
                log.warn("stop_times.txt is not sorted by trip_id: '{}' reappears after being flushed; cache may be incomplete", tripId);
            }
        }

        /**
         * Adds the accumulated stops for a trip into the cache, keyed by the first stop's arrival time.
         * Does nothing if {@code currentKey} is null (no relevant trip started yet) or {@code currentStops} is empty.
         */
        private void flush(Map<String, ConcurrentSkipListMap<LocalTime, List<CompleteStop>>> cache) {
            if (currentKey == null || currentStops.isEmpty()) return;
            cache.computeIfAbsent(currentKey, k -> new ConcurrentSkipListMap<>())
                    .put(currentStops.getFirst().arrivalTime(), currentStops);
        }
    }

    /**
     * Parses a single {@code stop_times.txt} row into a {@link CompleteStop}.
     * Resolves the stop via {@link #resolveStop}, which falls back to the opposite platform if needed.
     */
    private CompleteStop parseCompleteStop(String line, Map<String, Stop> stopsMap) {
        final String[] parts = line.split(DELIMITER, STOP_TIME_STOP_ID + 2);
        return CompleteStop.builder()
                .stop(resolveStop(parts[STOP_TIME_STOP_ID], stopsMap))
                .arrivalTime(parseGtfsTime(parts[STOP_TIME_ARRIVAL_TIME]))
                .departureTime(parseGtfsTime(parts[STOP_TIME_DEPARTURE_TIME]))
                .build();
    }

    /**
     * Looks up a stop by ID, falling back to the opposite platform if the primary ID is absent.
     *
     * @throws IllegalArgumentException if neither the stop nor its opposite platform is found
     */
    private Stop resolveStop(String stopId, Map<String, Stop> stopsMap) {
        Stop stop = stopsMap.get(stopId);
        if (stop == null) stop = stopsMap.get(findOppositeStopId(stopId));
        if (stop == null) throw new IllegalArgumentException("Stop not found: " + stopId);
        return stop;
    }

    /**
     * Parses a GTFS time string (which may exceed 24h) directly to a {@link LocalTime}.
     * <p>
     * Hours are taken modulo 24, minutes and seconds modulo 60. No intermediate strings are allocated.
     * Example: "25:01:00" → LocalTime.of(1, 1, 0)
     *
     * @param time a GTFS time string in {@code H:mm:ss} or {@code HH:mm:ss} format
     * @return the normalized {@link LocalTime}
     */
    private static LocalTime parseGtfsTime(String time) {
        int colon1 = time.indexOf(':');
        int colon2 = time.indexOf(':', colon1 + 1);
        int h = Integer.parseInt(time, 0, colon1, 10) % 24;
        int m = Integer.parseInt(time, colon1 + 1, colon2, 10) % 60;
        int s = Integer.parseInt(time, colon2 + 1, time.length(), 10) % 60;
        return LocalTime.of(h, m, s);
    }

    /**
     * Parses the `stops.txt` file and returns all stops indexed by their stop ID.
     * <p>
     * The file is expected to have the following format:
     * stop_id,stop_name,stop_lat,stop_lon,zone_id,stop_url,location_type,parent_station,wheelchair_boarding,level_id,platform_code,asw_node_id,asw_stop_id,zone_region_type
     * <p>
     * Returning a {@code Map} instead of a list allows O(1) stop resolution by ID in downstream methods.
     * <p>
     * If an error occurs while reading the file, an empty map is returned, and an error message is logged.
     *
     * @return All parsed stops keyed by stopId.
     */
    private Map<String, Stop> parseStops(Set<String> neededStopIds) {
        try (Stream<String> lines = Files.lines(Path.of(ROOT_PATH_FILE, STOPS_FILE_NAME))) {
            return lines
                    .map(line -> line.split(DELIMITER, STOPS_STOP_NAME + 2))
                    .filter(line -> neededStopIds.contains(line[STOPS_STOP_ID]))
                    .map(line -> Stop.builder()
                            .stopId(line[STOPS_STOP_ID])
                            .stopName(line[STOPS_STOP_NAME].replace("\"", ""))
                            .build())
                    .collect(Collectors.toMap(Stop::stopId, s -> s));
        } catch (IOException e) {
            log.error(ERROR_READING_FILE_ERROR_MESSAGE, STOPS_FILE_NAME, e);
            return Map.of();
        }
    }

    /**
     * Parses the {@code route_stops.txt} file and returns only the stop IDs for routes in {@code routeIds}.
     * Direction and sequence columns are intentionally ignored; only stop IDs are needed to pre-filter
     * which stops to load from {@code stops.txt}.
     * <p>
     * The file is expected to have the following format:
     * route_id,direction_id,stop_id,stop_sequence
     * <p>
     * If an error occurs while reading the file, an empty set is returned, and an error message is logged.
     *
     * @return the set of stop IDs referenced by the configured routes
     */
    private Set<String> parseRouteStopIds() {
        try (Stream<String> lines = Files.lines(Path.of(ROOT_PATH_FILE, ROUTE_STOPS_FILE_NAME))) {
            return lines
                    .map(line -> line.split(DELIMITER, ROUTE_STOP_STOP_ID + 2))
                    .filter(line -> routeIds.contains(line[ROUTE_STOP_ROUTE_ID]))
                    .map(line -> line[ROUTE_STOP_STOP_ID])
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            log.error(ERROR_READING_FILE_ERROR_MESSAGE, ROUTE_STOPS_FILE_NAME, e);
            return Set.of();
        }
    }

    @Builder
    record Trip(String routeId, String tripId, String tripHeadsign, String tripShortName, String directionId) {

    }

    @Builder
    record CompleteStop(Stop stop, LocalTime arrivalTime, LocalTime departureTime) {
        @Override
        public String toString() {
            return "CompleteStop{" +
                    "stopId='" + stop.stopId() + '\'' +
                    ", stopName='" + stop.stopName() + '\'' +
                    ", arrivalTime=" + getFormattedTime(arrivalTime) +
                    ", departureTime=" + getFormattedTime(departureTime) +
                    '}';
        }
    }

    @Builder
    record Stop(String stopId, String stopName) {
    }

}
