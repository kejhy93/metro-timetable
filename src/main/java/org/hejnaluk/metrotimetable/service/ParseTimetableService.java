package org.hejnaluk.metrotimetable.service;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    *
    * format:
    * route_id,agency_id,route_short_name,route_long_name,route_type,route_url,route_color,route_text_color,is_night,is_regional,is_substitute_transport
    *
    * route_id,route_short_name,route_long_name,route_url,route_color,route_text_color
    * */
    public static final String ROUTES_FILE_NAME = "routes.txt";

    /**
    * Columns key for routes.txt
    * */
    public static final int ROUTE_ROUTE_ID = 0;
    public static final int ROUTE_ROUTE_SHORT_NAME = 2;
    public static final int ROUTE_ROUTE_LONG_NAME = 3;
    public static final int ROUTE_ROUTE_URL = 5;
    public static final int ROUTE_ROUTE_COLOR = 6;
    public static final int ROUTE_ROUTE_TEXT_COLOR = 7;

    /**
     * Get path to the file containing stops information
     *
     * format:
     * stop_id,stop_name,stop_lat,stop_lon,zone_id,stop_url,location_type,parent_station,wheelchair_boarding,level_id,platform_code,asw_node_id,asw_stop_id,zone_region_type
     *
     * stop_id,stop_name
     */
    public static final String STOPS_FILE_NAME = "stops.txt";

    /**
     * Columns key for stops.txt
     */
    public static final int STOPS_STOP_ID = 0;
    public static final int STOPS_STOP_NAME = 1;


    /**
    * Set of all interesting columns
    * */
    public static final Set<Integer> ROUTE_COLUMNS = Set.of(ROUTE_ROUTE_ID, ROUTE_ROUTE_SHORT_NAME, ROUTE_ROUTE_LONG_NAME, ROUTE_ROUTE_URL, ROUTE_ROUTE_COLOR, ROUTE_ROUTE_TEXT_COLOR);
    /**
    * Expected set of routes to be parsed
    * */
    public static final Set<String> ROUTE_IDS = Set.of("L991", "L992", "L993");

    /**
     * route_id,direction_id,stop_id,stop_sequence
     */
    public static final String ROUTE_STOPS_FILE_NAME = "route_stops.txt";
    /**
     * Columns key for route_stops.txt
     * */
    public static final int ROUTE_STOP_ROUTE_ID = 0;
    public static final int ROUTE_STOP_DIRECTION_ID = 1;
    public static final int ROUTE_STOP_STOP_ID = 2;
    public static final int ROUTE_STOP_STOP_SEQUENCE = 3;

    public static final String DELIMITER = ",";


    public void parseTimetableFiles() {
        final var routesList = parseRoutes();
        final var routeStopsList = parseRouteStops();
        final var stopsList = parseStops();

        log.info("Routes: {}", routesList.stream().map(Route::toString).collect(Collectors.joining(", ", "[ ", " ]")));
        log.info("Routes stops: {}", routeStopsList.stream().map(RouteStop::toString).collect(Collectors.joining(", ", "[", " ]")));
        log.info("Stops: {}", stopsList.stream().map(Stop::toString).collect(Collectors.joining(", ", "[", " ]")));

        final var routesLines = calculateRouteLine(ROUTE_IDS, routeStopsList, stopsList);
    }

    private List<RouteLine> calculateRouteLine(Set<String> routeIds, List<RouteStop> routeStopsList, List<Stop> stopsList) {
        for ( final var routeId : routeIds) {
            log.info("Calculate route line for routeId: {}", routeId);
            final var stopsListFirstDirection = calculateStopForGivenRouteAndDirections(routeId, routeStopsList, stopsList, "0");
            final var stopsListSecondDirection = calculateStopForGivenRouteAndDirections(routeId, routeStopsList, stopsList, "1");


            log.info("Route stops for first direction: {}", stopsListFirstDirection.stream()
                    .map(Stop::toString)
                    .collect(Collectors.joining("\n\t", "\n[\n\t", "\n]")));
            log.info("Route stops for second direction: {}", stopsListSecondDirection.stream()
                    .map(Stop::toString)
                    .collect(Collectors.joining("\n\t", "\n[\n\t", "\n]")));

        }
        return List.of();
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

    @Builder
    record RouteLine(String routeId, String directionId, List<Stop> stops) {
        public void addStop(Stop stop) {
            this.stops.add(stop);
        }
    }

    private List<Stop> parseStops() {
        try {
            return Files.readString(Path.of(STOPS_FILE_NAME))
                    .lines()
                    .map(line -> line.split(DELIMITER))
//                    .filter(line -> ROUTE_IDS.contains(line[ROUTE_ROUTE_ID]))
                    .map(line -> Stop.builder()
                            .stopId(line[STOPS_STOP_ID])
                            .stopName(line[STOPS_STOP_NAME])
                            .build())
                    .toList();
        } catch (IOException e) {
            log.error("Error reading file: {}", STOPS_FILE_NAME, e);
            return List.of();
        }
    }

    private List<RouteStop> parseRouteStops() {
        try {
            return Files.readString(Path.of(ROUTE_STOPS_FILE_NAME))
                    .lines()
                    .map(line -> line.split(DELIMITER))
//                    .filter(line -> ROUTE_IDS.contains(line[ROUTE_ROUTE_ID]))
                    .map(line -> RouteStop.builder()
                            .routeId(line[ROUTE_STOP_ROUTE_ID])
                            .directionId(line[ROUTE_STOP_DIRECTION_ID])
                            .stopId(line[ROUTE_STOP_STOP_ID])
                            .stopSequence(line[ROUTE_STOP_STOP_SEQUENCE])
                            .build())
                    .toList();
        } catch (IOException e) {
            log.error("Error reading file: {}", ROUTE_STOPS_FILE_NAME, e);
            return List.of();
        }
    }

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
            log.error("Error reading file: {}", ROUTES_FILE_NAME, e);
            return List.of();
        }
    }

    @Builder
    record Stop(String stopId, String stopName) {
    }

    @Builder
    record RouteStop(String routeId, String directionId, String stopId, String stopSequence) {
    }

    @Builder
    record Route(String routeId, String routeShortName, String routeLongName, String routeUrl, String routeColor, String routeTextColor) {
    }
}
