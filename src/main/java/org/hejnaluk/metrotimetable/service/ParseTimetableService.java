package org.hejnaluk.metrotimetable.service;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    public static final Set<String> ROUTE_IDS = Set.of("L991", "L992", "L993");

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
    public static final String FIRST_DIRECTION = "0";
    public static final String SECOND_DIRECTION = "1";
    public static final String ERROR_READING_FILE_ERROR_MESSAGE = "Error reading file: {}";

    public void parseTimetableFiles() {
        final var routesList = parseRoutes();
        final var routeStopsList = parseRouteStops();
        final var stopsList = parseStops();
        final var stopTimesList = parseStopTime();
        final var tripList = parseTrip();

        log.info("Routes: {}", routesList.stream().map(Route::toString).collect(Collectors.joining(", ", "[ ", " ]")));
        log.info("Routes stops: {}", routeStopsList.stream().map(RouteStop::toString).collect(Collectors.joining(", ", "[", " ]")));
        log.info("Stops: {}", stopsList.stream().map(Stop::toString).collect(Collectors.joining(", ", "[", " ]")));

        final var routesLines = calculateRouteLine(ROUTE_IDS, routeStopsList, stopsList);
    }

    private List<RouteLine> calculateRouteLine(Set<String> routeIds, List<RouteStop> routeStopsList, List<Stop> stopsList) {
        final var routeLinesList = new ArrayList<RouteLine>();
        for (final var routeId : routeIds) {
            log.info("Calculate route line for routeId: {}", routeId);
            final var stopsListFirstDirection = calculateStopForGivenRouteAndDirections(routeId, routeStopsList, stopsList, FIRST_DIRECTION);
            final var stopsListSecondDirection = calculateStopForGivenRouteAndDirections(routeId, routeStopsList, stopsList, SECOND_DIRECTION);

            log.info("Route stops for first direction: {}", stopsListFirstDirection.stream()
                    .map(Stop::toString)
                    .collect(Collectors.joining("\n\t", "\n[\n\t", "\n]")));
            log.info("Route stops for second direction: {}", stopsListSecondDirection.stream()
                    .map(Stop::toString)
                    .collect(Collectors.joining("\n\t", "\n[\n\t", "\n]")));

            final var firstRouteLine = RouteLine.builder()
                    .routeId(routeId)
                    .directionId(FIRST_DIRECTION)
                    .stops(stopsListFirstDirection)
                    .build();
            final var secondRouteLine = RouteLine.builder()
                    .routeId(routeId)
                    .directionId(SECOND_DIRECTION)
                    .stops(stopsListSecondDirection)
                    .build();
            routeLinesList.add(firstRouteLine);
            routeLinesList.add(secondRouteLine);
        }
        return routeLinesList;
    }

    /**
     * Parses the `trips.txt` file and returns a list of Trip objects.
     * <p>
     * The file is expected to have the following format:
     * route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,block_id,shape_id,wheelchair_accessible,bikes_allowed,exceptional,sub_agency_id
     * <p>
     * Each line is split using the specified delimiter, and the resulting data is mapped
     * to a Trip object using the builder pattern.
     * <p>
     * If an error occurs while reading the file, an empty list is returned, and an error
     * message is logged.
     *
     * @return A list of Trip objects parsed from the `trips.txt` file.
     */
    private List<Trip> parseTrip() {
        try {
            return Files.readString(Path.of(TRIP_FILE_NAME))
                    .lines()
                    .map(line -> line.split(DELIMITER))
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
                    .map(line -> line.split(DELIMITER))
                    .map(line -> StopTime.builder()
                            .tripId(line[STOP_TIME_TRIP_ID])
                            .arrivalTime(line[STOP_TIME_ARRIVAL_TIME])
                            .departureTime(line[STOP_TIME_DEPARTURE_TIME])
                            .stopId(line[STOP_TIME_STOP_ID])
                            .build())
                    .toList();
        } catch (IOException e) {
            log.error(ERROR_READING_FILE_ERROR_MESSAGE, STOPS_FILE_NAME, e);
            return List.of();
        }
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
        return routeStopsList.stream()
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
                    .filter(line -> ROUTE_IDS.contains(line[ROUTE_ROUTE_ID]))
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

    @Builder
    record Trip(String routeId, String tripId, String tripHeadsign, String tripShortName, String directionId) {

    }

    @Builder
    record StopTime(String tripId, String arrivalTime, String departureTime, String stopId) {
    }

    @Builder
    record RouteLine(String routeId, String directionId, List<Stop> stops) {
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
