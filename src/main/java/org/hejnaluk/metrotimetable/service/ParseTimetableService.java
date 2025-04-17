package org.hejnaluk.metrotimetable.service;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParseTimetableService {

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

    private static final Map<String, List<Stop>> cacheListStopByRouteIdAndDirectionId = new ConcurrentHashMap<>();

    private static final Map<String, ConcurrentSkipListMap<LocalTime, List<CompleteStop>>> routeIdDirectionCache = new ConcurrentHashMap<>();

    public void parseTimetableFiles() {
        log.info("----------------------- PARSING START ------------------------");
        final var routesList = parseRoutes();
        final var routeStopsList = parseRouteStops();
        final var stopsList = parseStops();
        final var stopTimesList = parseStopTime();
        final var tripList = parseTrip(routeIds);

        log.debug("Routes: {}", routesList.stream().map(Route::toString).collect(Collectors.joining(", ", "[ ", " ]")));
        log.debug("Routes stops: {}", routeStopsList.stream().map(RouteStop::toString).collect(Collectors.joining(", ", "[", " ]")));
        log.debug("Stops: {}", stopsList.stream().map(Stop::toString).collect(Collectors.joining(", ", "[", " ]")));

        final var routesLines = calculateRouteLine(routeStopsList, stopsList, tripList, stopTimesList);
        log.info("----------------------- PARSING DONE ------------------------");

        log.info("------------------------ CACHE START ------------------------");
        for (final var routeLine : routesLines) {
            log.debug("Route line: {}", routeLine.toString());
            final var key = getKeyForRouteIdDirectionId(routeLine);
            final var routeLineStops = routeLine.routeLineStops;
            var firstArrivalTime = routeLine.routeLineStops.getFirst().stopTime.arrivalTime;
            var listOfCompleteStop = new ArrayList<CompleteStop>();
            for (final var route : routeLineStops) {
                log.debug("Process route line for routeId: {}, directionId: {}, arrivalTime: {}", routeLine.routeId, routeLine.directionId, route.stopTime.arrivalTime);
                listOfCompleteStop.add(CompleteStop.builder()
                        .stopName(route.stop().stopName())
                        .stopId(route.stop.stopId)
                        .arrivalTime(route.stopTime.arrivalTime)
                        .departureTime(route.stopTime.departureTime)
                        .build());
            }
            log.info("First arrival time: {}", firstArrivalTime);
            log.info("Store key: {} in cacheListStopByRouteIdAndDirectionId", key);
            final var cache = routeIdDirectionCache.getOrDefault(key, new ConcurrentSkipListMap<>());
            cache.put(firstArrivalTime, listOfCompleteStop);
            routeIdDirectionCache.put(key, cache);
        }
        log.info("------------------------ CACHE DONE ------------------------");


        log.info("------------------------ VERIFY START ------------------------");
        for (final var entry : routeIdDirectionCache.entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();
            log.debug("Start Key: {} value: {}", key, value);
            for (final var entry2 : value.entrySet()) {
                final var arrivalTime = entry2.getKey();
                final var listOfCompleteStop = entry2.getValue();
                log.debug("Key: {} value: {}", arrivalTime, listOfCompleteStop.stream().map(CompleteStop::toString).collect(Collectors.joining(",", "[", "]")));
                log.info("Key: {}", getFormattedTime(arrivalTime));
            }
            log.debug("End Key: {} value: {}", key, value);
        }
        log.info("------------------------ VERIFY DONE ------------------------");
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
        return DateTimeFormatter.ofPattern("HH:mm:ss").format(localTime);
    }

    /**
     * Calculates the list of RouteLine objects for the given route stops, stops, trips, and stop times.
     * <p>
     * This method iterates through the provided trips and, for each trip, calculates the stops and stop times
     * for the corresponding route and direction. It then creates a RouteLine object containing this information.
     * <p>
     * The resulting list of RouteLine objects represents the routes and their associated stops and stop times.
     *
     * @param routeStopsList The list of all route stops.
     * @param stopsList      The list of all stops.
     * @param tripList       The list of all trips.
     * @param stopTimeList   The list of all stop times.
     * @return A list of RouteLine objects, each representing a route and its associated stops and stop times.
     */
    private List<RouteLine> calculateRouteLine(List<RouteStop> routeStopsList, List<Stop> stopsList, List<Trip> tripList, List<StopTime> stopTimeList) {
        final var routeLinesList = new ArrayList<RouteLine>();
        for (final var trip : tripList) {
            final var routeId = trip.routeId();
            final var directionId = trip.directionId();
            log.debug("Calculate route line for routeId: {}, directionId: {}", routeId, directionId);
            final var stopList = calculateStopForGivenRouteAndDirections(routeId, routeStopsList, stopsList, directionId);
            final var stopTimes = calculateRouteLineStop(stopsList, stopTimeList, trip);
            final var routeLine = RouteLine.builder()
                    .routeId(routeId)
                    .directionId(directionId)
                    .routeLineStops(stopTimes)
                    .build();
            log.debug("Route stops for direction: {}", stopList.stream()
                    .map(Stop::toString)
                    .collect(Collectors.joining(NEW_LINE_AND_TAB, "\n[\n\t", "\n]")));
            routeLinesList.add(routeLine);
        }
        return routeLinesList;
    }

    /**
     * Calculates the list of RouteLineStop objects for a given route and direction.
     * <p>
     * This method checks if the result is already cached. If not, it processes the stop times
     * and matches them with the corresponding stops to create RouteLineStop objects.
     * The result is then cached for future use.
     *
     * @param stopsList    The list of all stops.
     * @param stopTimeList The list of all stop times.
     * @param trip         The trip object containing route and direction information.
     * @return A list of RouteLineStop objects for the given route and direction.
     */
    private List<RouteLineStop> calculateRouteLineStop(List<Stop> stopsList, List<StopTime> stopTimeList, Trip trip) {
        final var stopTimes = stopTimeList.stream()
                .filter(stopTime -> trip.tripId.equals(stopTime.tripId))
                .map(stopTime -> {
                    final var stopId = stopTime.stopId;
                    final var stop = stopsList.stream()
                            .filter(s -> stopId.equals(s.stopId))
                            .findAny()
                            .orElseThrow(() -> new IllegalArgumentException("Stop not found: " + stopId));
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

    /**
     * Calculates the list of stops for a given route and direction.
     *
     * @param routeId        The ID of the route for which stops are to be calculated.
     * @param routeStopsList The list of all route stops.
     * @param stopsList      The list of all stops.
     * @param direction      The direction ID (e.g., "0" or "1") for which stops are to be calculated.
     * @return A list of stops corresponding to the given route and direction.
     */
    private List<Stop> calculateStopForGivenRouteAndDirections(String routeId, List<RouteStop> routeStopsList, List<Stop> stopsList, String direction) {
        final var key = getKeyForStopsMap(routeId, direction);

        if (cacheListStopByRouteIdAndDirectionId.containsKey(key)) {
            log.debug("Found key: {} in cacheListStopByRouteIdAndDirectionId", key);
            return cacheListStopByRouteIdAndDirectionId.get(key);
        }
        final var value = routeStopsList.stream()
                // filter only route stops for the given route
                .filter(routeStop -> routeId.equals(routeStop.routeId))
                // filter only route stops for the given direction
                .filter(routeStop -> direction.equals(routeStop.directionId))
                // find corresponding stop in the stops list
                .map(routeStop -> {
                    final var stopId = routeStop.stopId;
                    return stopsList.stream().filter(stop -> stopId.equals(stop.stopId)).findAny();
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        log.info("New value for key: {} in cacheListStopByRouteIdAndDirectionId", key);
        cacheListStopByRouteIdAndDirectionId.put(key, value);

        return value;
    }

    private String getKeyForStopsMap(String routeId, String direction) {
        return routeId + "-" + direction;
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
        try {
            return Files.readString(Path.of(TRIP_FILE_NAME))
                    .lines()
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
     * Parses the `stop_times.txt` file and returns a list of StopTime objects.
     * <p>
     * The file is expected to have the following format:
     * trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,trip_operation_type,bikes_allowed
     * <p>
     * Each line is split using the specified delimiter, and the resulting data is mapped
     * to a StopTime object using the builder pattern.
     * <p>
     * If an error occurs while reading the file, an empty list is returned, and an error
     * message is logged.
     *
     * @return A list of StopTime objects parsed from the `stop_times.txt` file.
     */
    private List<StopTime> parseStopTime() {
        try {
            return Files.readString(Path.of(STOP_TIME_FILE_NAME))
                    .lines()
                    .skip(1) // skip first line because it is column description
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
                    .toList();
        } catch (IOException e) {
            log.error(ERROR_READING_FILE_ERROR_MESSAGE, STOPS_FILE_NAME, e);
            return List.of();
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
     * Parses the `stops.txt` file and returns a list of Stop objects.
     * <p>
     * The file is expected to have the following format:
     * stop_id,stop_name,stop_lat,stop_lon,zone_id,stop_url,location_type,parent_station,wheelchair_boarding,level_id,platform_code,asw_node_id,asw_stop_id,zone_region_type
     * <p>
     * Each line is split using the specified delimiter, and the resulting data is mapped
     * to a Stop object using the builder pattern.
     * <p>
     * If an error occurs while reading the file, an empty list is returned, and an error
     * message is logged.
     *
     * @return A list of Stop objects parsed from the `stops.txt` file.
     */
    private List<Stop> parseStops() {
        try {
            return Files.readString(Path.of(STOPS_FILE_NAME))
                    .lines()
                    .map(line -> line.split(DELIMITER))
                    .map(line -> Stop.builder()
                            .stopId(line[STOPS_STOP_ID])
                            .stopName(line[STOPS_STOP_NAME])
                            .build())
                    .toList();
        } catch (IOException e) {
            log.error(ERROR_READING_FILE_ERROR_MESSAGE, STOPS_FILE_NAME, e);
            return List.of();
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
        try {
            return Files.readString(Path.of(ROUTE_STOPS_FILE_NAME))
                    .lines()
                    .map(line -> line.split(DELIMITER))
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
        try {
            return Files.readString(Path.of(ROUTES_FILE_NAME))
                    .lines()
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
    record CompleteStop(String stopId, String stopName, LocalTime arrivalTime, LocalTime departureTime) {
        @Override
        public String toString() {
            return "CompleteStop{" +
                    "stopId='" + stopId + '\'' +
                    ", stopName='" + stopName + '\'' +
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
