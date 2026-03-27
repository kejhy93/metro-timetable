package org.hejnaluk.metrotimetable.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hejnaluk.metrotimetable.dto.TrainDeparture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
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

    private static volatile TimetableData timetableData = TimetableData.empty();

    public void parseTimetableFiles() {
        log.info("----------------------- PARSING START ------------------------");
        io.micrometer.core.instrument.Timer fileParseTimer = io.micrometer.core.instrument.Timer.builder("file.parse")
                .description("Time taken to parse files")
                .register(meterRegistry);
        io.micrometer.core.instrument.Timer.Sample fileParseTimeSample = Timer.start();

        // Phase 1: Parse route stops (filtered by routeIds) + trips in parallel
        final var routeStopsFuture = CompletableFuture.supplyAsync(this::parseRouteStops);
        final var tripFuture = CompletableFuture.supplyAsync(() -> parseTrip(routeIds));
        CompletableFuture.allOf(routeStopsFuture, tripFuture).join();

        final var routeStopsList = routeStopsFuture.join();
        final var tripList = tripFuture.join();

        // Phase 2: Load only the stops that are actually referenced by the filtered route stops
        final var neededStopIds = routeStopsList.stream()
                .map(RouteStop::stopId)
                .collect(Collectors.toSet());
        final var stopsMap = parseStops(neededStopIds);

        // Phase 3: Parse stop times directly into a grouped map (no intermediate list)
        final var relevantTripIds = tripList.stream().map(Trip::tripId).collect(Collectors.toSet());
        final var stopTimesByTripId = parseStopTime(relevantTripIds);

        fileParseTimeSample.stop(fileParseTimer);

        io.micrometer.core.instrument.Timer logicBuildTimer = io.micrometer.core.instrument.Timer.builder("create.releationship")
                .description("Time taken to create relationships")
                .register(meterRegistry);
        io.micrometer.core.instrument.Timer.Sample logicBuildTimeSample = Timer.start();
        final var routesLines = calculateRouteLine(routeStopsList, stopsMap, tripList, stopTimesByTripId);
        logicBuildTimeSample.stop(logicBuildTimer);
        log.info("----------------------- PARSING DONE ------------------------");

        log.info("------------------------ CACHE START ------------------------");
        io.micrometer.core.instrument.Timer cacheBuildTimer = io.micrometer.core.instrument.Timer.builder("create.cache")
                .description("Time taken to create cache")
                .register(meterRegistry);
        io.micrometer.core.instrument.Timer.Sample cacheBuildTimeSample = Timer.start();

        // Build into a local map — never mutate the live cache while queries may be reading it.
        final Map<String, ConcurrentSkipListMap<LocalTime, List<CompleteStop>>> newRouteCache = new HashMap<>();
        for (final var routeLine : routesLines) {
            log.debug("Route line: {}", routeLine.toString());
            final var key = getKeyForRouteIdDirectionId(routeLine);
            final var routeLineStops = routeLine.routeLineStops;
            var firstArrivalTime = routeLine.routeLineStops.getFirst().stopTime.arrivalTime;
            var listOfCompleteStop = new ArrayList<CompleteStop>();
            for (final var route : routeLineStops) {
                log.debug("Process route line for routeId: {}, directionId: {}, arrivalTime: {}",
                        routeLine.routeId, routeLine.directionId, route.stopTime.arrivalTime);
                listOfCompleteStop.add(CompleteStop.builder()
                        .stop(route.stop())
                        .arrivalTime(route.stopTime.arrivalTime)
                        .departureTime(route.stopTime.departureTime)
                        .build());
            }
            newRouteCache.computeIfAbsent(key, k -> new ConcurrentSkipListMap<>())
                    .put(firstArrivalTime, listOfCompleteStop);
        }

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

    protected LocalTime getNow() {
        return LocalTime.now();
    }

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

        final List<TrainDeparture> result = new ArrayList<>();
        final LocalTime now = getNow();

        for (Map.Entry<String, Integer> indexEntry : keyToStopIndex.entrySet()) {
            final String key = indexEntry.getKey();
            final int stopIndex = indexEntry.getValue();

            if (direction != null && !key.endsWith("-" + direction)) continue;

            final int lastDash = key.lastIndexOf('-');
            final String routeId = key.substring(0, lastDash);
            final int directionId = Integer.parseInt(key.substring(lastDash + 1));

            final ConcurrentSkipListMap<LocalTime, List<CompleteStop>> trips = snapshot.routeCache().get(key);
            if (trips == null) continue;

            for (List<CompleteStop> stops : trips.values()) {
                if (stopIndex >= stops.size()) continue;
                final LocalTime departureTime = stops.get(stopIndex).departureTime();
                if (!departureTime.isBefore(now)) {
                    final String destination = stops.getLast().stop().stopName();
                    final List<String> upcomingStations = stops.subList(stopIndex, stops.size()).stream()
                            .map(cs -> cs.stop().stopName())
                            .toList();
                    result.add(new TrainDeparture(routeId, directionId, departureTime, destination, upcomingStations));
                }
            }
        }

        int effectiveLimit = Math.min(limit, maxLimit);
        List<TrainDeparture> finalResult = result.stream()
                .sorted(Comparator.comparing(TrainDeparture::departureTime))
                .limit(effectiveLimit)
                .toList();

        sample.stop(timer);
        return finalResult;
    }

    private String getKeyForRouteIdDirectionId(org.hejnaluk.metrotimetable.service.ParseTimetableService.RouteLine routeLine) {
        return routeLine.routeId + "-" + routeLine.directionId;
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

    static void resetForTest() {
        timetableData = TimetableData.empty();
    }

    static void populateForTest(String key, ConcurrentSkipListMap<LocalTime, List<CompleteStop>> trips) {
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
     * Calculates the list of RouteLine objects for the given route stops, stops, trips, and stop times.
     * <p>
     * This method iterates through the provided trips and, for each trip, calculates the stops and stop times
     * for the corresponding route and direction. It then creates a RouteLine object containing this information.
     * <p>
     * The resulting list of RouteLine objects represents the routes and their associated stops and stop times.
     *
     * @param routeStopsList    The list of all route stops.
     * @param stopsMap          All stops keyed by stopId for O(1) lookup.
     * @param tripList          The list of all trips.
     * @param stopTimesByTripId All stop times grouped by tripId for O(1) lookup.
     * @return A list of RouteLine objects, each representing a route and its associated stops and stop times.
     */
    private List<RouteLine> calculateRouteLine(List<RouteStop> routeStopsList, Map<String, Stop> stopsMap, List<Trip> tripList, Map<String, List<StopTime>> stopTimesByTripId) {
        final var routeLinesList = new ArrayList<RouteLine>();
        for (final var trip : tripList) {
            final var routeId = trip.routeId();
            final var directionId = trip.directionId();
            log.debug("Calculate route line for routeId: {}, directionId: {}", routeId, directionId);
            final var stopTimes = calculateRouteLineStop(stopsMap, stopTimesByTripId, trip);
            final var routeLine = RouteLine.builder()
                    .routeId(routeId)
                    .directionId(directionId)
                    .routeLineStops(stopTimes)
                    .build();
            routeLinesList.add(routeLine);
        }
        return routeLinesList;
    }

    /**
     * Builds the list of RouteLineStop objects for a single trip by joining its stop times with stop details.
     * <p>
     * Stop times are retrieved in O(1) via {@code stopTimesByTripId} and each stop is resolved in O(1)
     * via {@code stopsMap}, avoiding any linear scans.
     *
     * @param stopsMap          All stops keyed by stopId for O(1) lookup.
     * @param stopTimesByTripId All stop times grouped by tripId for O(1) lookup.
     * @param trip              The trip whose stops are to be resolved.
     * @return A list of RouteLineStop objects for the given trip.
     */
    private List<RouteLineStop> calculateRouteLineStop(Map<String, Stop> stopsMap, Map<String, List<StopTime>> stopTimesByTripId, Trip trip) {
        final var stopTimes = stopTimesByTripId.getOrDefault(trip.tripId, List.of()).stream()
                .map(stopTime -> {
                    final var stopId = stopTime.stopId;
                    final var stop = Optional.ofNullable(stopsMap.get(stopId))
                            .orElse(stopsMap.get(findOppositeStopId(stopId)));
                    return RouteLineStop.builder()
                            .stop(stop)
                            .stopTime(stopTime)
                            .build();
                })
                .toList();
        log.debug("Stop times for trip {}: {}", trip.tripId, stopTimes.stream()
                .map(RouteLineStop::toString)
                .collect(Collectors.joining(",\n\t", "\n[\n\t", "\n]")));
        return stopTimes;
    }

    private String findOppositeStopId(String stopId) {
        char secondLastChar = stopId.charAt(stopId.length() - 2);
        char newSecondLastChar = secondLastChar == '1' ? '2' : (secondLastChar == '2' ? '1' : secondLastChar);
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
                    .map(line -> line.split(DELIMITER))
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
     * Parses the `stop_times.txt` file and returns only the StopTime objects whose trip ID is in {@code tripIds}.
     * <p>
     * The file is expected to have the following format:
     * trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,trip_operation_type,bikes_allowed
     * <p>
     * The file is read lazily via {@link Files#lines} to avoid loading the entire file into memory at once.
     * Rows whose trip ID is not in {@code tripIds} are discarded before any object allocation occurs.
     * <p>
     * If an error occurs while reading the file, an empty list is returned, and an error message is logged.
     *
     * @param tripIds The set of trip IDs to retain; all other rows are skipped.
     * @return A list of StopTime objects for the requested trips.
     */
    private Map<String, List<StopTime>> parseStopTime(Set<String> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        try (Stream<String> lines = Files.lines(Path.of(ROOT_PATH_FILE, STOP_TIME_FILE_NAME))) {
            return lines
                    .skip(1) // skip first line because it is column description
                    .filter(line -> {
                        // Extract only the trip_id (first field) before splitting the whole line.
                        // stop_times.txt has ~4M rows; splitting every line into 11 strings
                        // causes massive GC pressure. This avoids allocating String[] for filtered rows.
                        int comma = line.indexOf(DELIMITER);
                        return comma > 0 && tripIds.contains(line.substring(0, comma));
                    })
                    .map(line -> line.split(DELIMITER))
                    .map(line -> {
                        final var arrivalTime = reformatHoursFormat(line, STOP_TIME_ARRIVAL_TIME);
                        final var departureTime = reformatHoursFormat(line, STOP_TIME_DEPARTURE_TIME);
                        return StopTime.builder()
                                .tripId(line[STOP_TIME_TRIP_ID])
                                .arrivalTime(LocalTime.parse(arrivalTime))
                                .departureTime(LocalTime.parse(departureTime))
                                .stopId(line[STOP_TIME_STOP_ID])
                                .build();
                    })
                    .collect(Collectors.groupingBy(st -> st.tripId));
        } catch (IOException e) {
            log.error(ERROR_READING_FILE_ERROR_MESSAGE, STOP_TIME_FILE_NAME, e);
            return Map.of();
        }
    }

    /**
     * Reformats a time string from the input array to ensure it adheres to a 24-hour format.
     * <p>
     * The method takes a time string in the format `HH:mm:ss` and ensures that:
     * - Hours are modulo 24.
     * - Minutes are modulo 60.
     * - Seconds are modulo 60.
     * <p>
     * Example:
     * Input: "25:61:61"
     * Reformatted: "01:01:01"
     *
     * @param line  The array of strings containing the time data.
     * @param index The index of the time string in the array.
     * @return A reformatted time string in the format `HH:mm:ss`.
     */
    private String reformatHoursFormat(String[] line, int index) {
        final var splittedString = line[index].split(":");
        final var hour = splittedString[0];
        final var minute = splittedString[1];
        final var second = splittedString[2].isEmpty() || splittedString[2].isBlank() ? "0" : splittedString[2];

        final var time = String.format("%02d:%02d:%02d",
                Integer.parseInt(hour) % 24,
                Integer.parseInt(minute) % 60,
                Integer.parseInt(second) % 60);
        log.debug("Reformat time: {} to {}", line[index], time);
        return time;
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
                    .map(line -> line.split(DELIMITER))
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
     * Parses the `route_stops.txt` file and returns a list of RouteStop objects.
     * <p>
     * The file is expected to have the following format:
     * route_id,direction_id,stop_id,stop_sequence
     * <p>
     * Each line is split using the specified delimiter, and the resulting data is mapped
     * to a RouteStop object using the builder pattern.
     * <p>
     * If an error occurs while reading the file, an empty list is returned, and an error
     * message is logged.
     *
     * @return A list of RouteStop objects parsed from the `route_stops.txt` file.
     */
    private List<RouteStop> parseRouteStops() {
        try (Stream<String> lines = Files.lines(Path.of(ROOT_PATH_FILE, ROUTE_STOPS_FILE_NAME))) {
            return lines
                    .map(line -> line.split(DELIMITER))
                    .filter(line -> routeIds.contains(line[ROUTE_STOP_ROUTE_ID]))
                    .map(line -> RouteStop.builder()
                            .routeId(line[ROUTE_STOP_ROUTE_ID])
                            .directionId(line[ROUTE_STOP_DIRECTION_ID])
                            .stopId(line[ROUTE_STOP_STOP_ID])
                            .stopSequence(line[ROUTE_STOP_STOP_SEQUENCE])
                            .build())
                    .toList();
        } catch (IOException e) {
            log.error(ERROR_READING_FILE_ERROR_MESSAGE, ROUTE_STOPS_FILE_NAME, e);
            return List.of();
        }
    }

    /**
     * Parses the routes file and returns a list of Route objects.
     * <p>
     * The file is expected to have the following format:
     * route_id,agency_id,route_short_name,route_long_name,route_type,route_url,route_color,route_text_color,is_night,is_regional,is_substitute_transport
     * <p>
     * Only routes with IDs present in the ROUTE_IDS set are included in the result.
     *
     * @return A list of Route objects parsed from the routes file.
     * Returns an empty list if an error occurs while reading the file.
     */
    private List<Route> parseRoutes() {
        try (Stream<String> lines = Files.lines(Path.of(ROOT_PATH_FILE, ROUTES_FILE_NAME))) {
            return lines
                    .map(line -> line.split(DELIMITER))
                    .filter(line -> routeIds.contains(line[ROUTE_ROUTE_ID]))
                    .map(line -> Route.builder()
                            .routeId(line[ROUTE_ROUTE_ID])
                            .routeShortName(line[ROUTE_ROUTE_SHORT_NAME])
                            .routeLongName(line[ROUTE_ROUTE_LONG_NAME])
                            .routeUrl(line[ROUTE_ROUTE_URL])
                            .routeColor(line[ROUTE_ROUTE_COLOR])
                            .routeTextColor(line[ROUTE_ROUTE_TEXT_COLOR])
                            .build())
                    .toList();
        } catch (IOException e) {
            log.error(ERROR_READING_FILE_ERROR_MESSAGE, ROUTES_FILE_NAME, e);
            return List.of();
        }
    }

    /**
     * Represents a route line, which includes information about a route, its direction,
     * the list of stops, and the associated stop times.
     *
     * @param routeId        The ID of the route.
     * @param directionId    The direction ID (e.g., "0" or "1").
     * @param routeLineStops The list of stops and stops time for the route.
     */
    @Builder
    record RouteLine(String routeId, String directionId, List<RouteLineStop> routeLineStops) {
        @Override
        public String toString() {
            return "RouteLine{" +
                    "routeId='" + routeId + '\'' +
                    ", directionId='" + directionId + '\'' +
                    ", routeLineStops=" + routeLineStops.stream().map(RouteLineStop::toString).collect(Collectors.joining(",", "[", "]")) +
                    '}';
        }
    }

    /**
     * Represents a combination of a stop and its associated stop time.
     *
     * @param stop     The stop information.
     * @param stopTime The stop time information for the stop.
     */
    @Builder
    record RouteLineStop(Stop stop, StopTime stopTime) {
        @Override
        public String toString() {
            return "{" +
                    "stop=" + stop +
                    ", stopTime=" + stopTime +
                    '}';
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
    record StopTime(String tripId, LocalTime arrivalTime, LocalTime departureTime, String stopId) {

    }

    @Builder
    record Stop(String stopId, String stopName) {
    }

    @Builder
    record RouteStop(String routeId, String directionId, String stopId, String stopSequence) {
    }

    @Builder
    record Route(String routeId, String routeShortName, String routeLongName, String routeUrl, String routeColor,
                 String routeTextColor) {
    }
}
