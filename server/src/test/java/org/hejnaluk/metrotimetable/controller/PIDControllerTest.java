package org.hejnaluk.metrotimetable.controller;

import org.hejnaluk.metrotimetable.dto.AppConfig;
import org.hejnaluk.metrotimetable.dto.LineInfo;
import org.hejnaluk.metrotimetable.dto.StationRequest;
import org.hejnaluk.metrotimetable.dto.TrainDeparture;
import org.hejnaluk.metrotimetable.dto.TripDetail;
import org.hejnaluk.metrotimetable.dto.TripStop;
import org.hejnaluk.metrotimetable.service.ParseTimetableService;
import org.hejnaluk.metrotimetable.service.TimetableRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PIDControllerTest {

    @Mock
    private TimetableRefreshService timetableRefreshService;
    @Mock
    private ParseTimetableService parseTimetableService;

    private PIDController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        controller = new PIDController(timetableRefreshService, parseTimetableService);
        // Inject @Value fields that Spring would normally populate
        setField(controller, "departuresRefreshIntervalSeconds", 30L);
        setField(controller, "tripDetailRefreshIntervalSeconds", 10L);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void getTimetableChange() {
        controller.getTimetableChange();

        Mockito.verify(timetableRefreshService, times(1)).refresh();
    }

    @Test
    void getTrainsForStation_callsService() {
        String station = "Muzeum";
        Integer direction = 0;
        int limit = 5;
        when(parseTimetableService.getTrainsForStation(station, direction, limit, null)).thenReturn(List.of());

        controller.getTrainsForStation(new StationRequest(station, direction, limit, null));

        Mockito.verify(parseTimetableService, times(1)).getTrainsForStation(station, direction, limit, null);
    }

    @Test
    void getTrainsForStation_returnsServiceResult() {
        String station = "Muzeum";
        int limit = 5;
        TrainDeparture departure = new TrainDeparture("L991", 0, Instant.parse("2026-03-30T12:00:00Z"), "Depo Hostivař", List.of("Muzeum", "Depo Hostivař"));
        when(parseTimetableService.getTrainsForStation(station, null, limit, null)).thenReturn(List.of(departure));

        var response = controller.getTrainsForStation(new StationRequest(station, null, limit, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(departure);
    }

    @Test
    void getTrainsForStation_usesDefaultLimitWhenNotProvided() {
        String station = "Muzeum";
        Integer direction = null;
        TrainDeparture departure = new TrainDeparture("L991", 0, Instant.parse("2026-03-30T12:00:00Z"), "Depo Hostivař", List.of("Muzeum", "Depo Hostivař"));
        when(parseTimetableService.getTrainsForStation(station, direction, StationRequest.DEFAULT_LIMIT, null)).thenReturn(List.of(departure));

        var response = controller.getTrainsForStation(new StationRequest(station, direction, null, null));

        Mockito.verify(parseTimetableService, times(1)).getTrainsForStation(station, direction, StationRequest.DEFAULT_LIMIT, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(departure);
    }

    @Test
    void getTrainsForStation_defaultLimit() {
        String station = "Muzeum";
        when(parseTimetableService.getTrainsForStation(station, null, StationRequest.DEFAULT_LIMIT, null)).thenReturn(List.of());

        controller.getTrainsForStation(new StationRequest(station, null, StationRequest.DEFAULT_LIMIT, null));

        Mockito.verify(parseTimetableService, times(1)).getTrainsForStation(station, null, StationRequest.DEFAULT_LIMIT, null);
    }

    @Test
    void getTrainsForStation_noDirection() {
        String station = "Muzeum";
        int limit = 3;
        when(parseTimetableService.getTrainsForStation(station, null, limit, null)).thenReturn(List.of());

        controller.getTrainsForStation(new StationRequest(station, null, limit, null));

        Mockito.verify(parseTimetableService, times(1)).getTrainsForStation(station, null, limit, null);
    }

    @Test
    void getTrainsForStation_passesRouteIdToService() {
        String station = "Muzeum";
        int limit = 5;
        String routeId = "L991";
        when(parseTimetableService.getTrainsForStation(station, null, limit, routeId)).thenReturn(List.of());

        controller.getTrainsForStation(new StationRequest(station, null, limit, routeId));

        Mockito.verify(parseTimetableService, times(1)).getTrainsForStation(station, null, limit, routeId);
    }

    @Test
    void getTrainsForStation_rejects_blankStation() throws Exception {
        mockMvc.perform(post("/pid/station")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"station\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTrainsForStation_rejects_nullStation() throws Exception {
        mockMvc.perform(post("/pid/station")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"station\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTrainsForStation_rejects_invalidDirection() throws Exception {
        mockMvc.perform(post("/pid/station")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"station\":\"Muzeum\",\"direction\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTrainsForStation_rejects_zeroLimit() throws Exception {
        mockMvc.perform(post("/pid/station")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"station\":\"Muzeum\",\"limit\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTrainsForStation_rejects_negativeLimit() throws Exception {
        mockMvc.perform(post("/pid/station")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"station\":\"Muzeum\",\"limit\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getLines_callsService() {
        when(parseTimetableService.getLines()).thenReturn(List.of());

        controller.getLines();

        Mockito.verify(parseTimetableService, times(1)).getLines();
    }

    @Test
    void getLines_returnsServiceResult() {
        LineInfo line = new LineInfo("L991", 0, "Zličín", List.of("Depo Hostivař", "Muzeum", "Zličín"));
        when(parseTimetableService.getLines()).thenReturn(List.of(line));

        var response = controller.getLines();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(line);
    }

    @Test
    void getLines_returnsEmptyList_whenCacheEmpty() throws Exception {
        when(parseTimetableService.getLines()).thenReturn(List.of());

        mockMvc.perform(get("/pid/lines"))
                .andExpect(status().isOk());
    }

    @Test
    void getTripDetail_callsService() {
        String routeId = "L991";
        int directionId = 0;
        Instant departureTime = Instant.parse("2026-03-30T12:00:00Z");
        when(parseTimetableService.getTripDetail(routeId, directionId, departureTime)).thenReturn(Optional.empty());

        controller.getTripDetail(routeId, directionId, departureTime.toString());

        Mockito.verify(parseTimetableService, times(1)).getTripDetail(routeId, directionId, departureTime);
    }

    @Test
    void getTripDetail_returnsServiceResult() {
        String routeId = "L991";
        int directionId = 0;
        Instant departureTime = Instant.parse("2026-03-30T12:00:00Z");
        TripDetail detail = new TripDetail(routeId, directionId, "Zličín", List.of(
                new TripStop("Depo Hostivař", null, departureTime),
                new TripStop("Zličín", departureTime, null)
        ));
        when(parseTimetableService.getTripDetail(routeId, directionId, departureTime)).thenReturn(Optional.of(detail));

        var response = controller.getTripDetail(routeId, directionId, departureTime.toString());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(detail);
    }

    @Test
    void getTripDetail_returns404_whenNotFound() throws Exception {
        when(parseTimetableService.getTripDetail("L991", 0, Instant.parse("2026-03-30T12:00:00Z")))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/pid/trip")
                        .param("routeId", "L991")
                        .param("directionId", "0")
                        .param("departureTime", "2026-03-30T12:00:00Z"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTripDetail_rejects_missingRouteId() throws Exception {
        mockMvc.perform(get("/pid/trip")
                        .param("directionId", "0")
                        .param("departureTime", "2026-03-30T12:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTripDetail_rejects_missingDepartureTime() throws Exception {
        mockMvc.perform(get("/pid/trip")
                        .param("routeId", "L991")
                        .param("directionId", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTripDetail_rejects_invalidDepartureTimeFormat() throws Exception {
        mockMvc.perform(get("/pid/trip")
                        .param("routeId", "L991")
                        .param("directionId", "0")
                        .param("departureTime", "not-an-instant"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getConfig_returnsConfiguredValues() throws Exception {
        mockMvc.perform(get("/pid/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departuresRefreshIntervalSeconds").value(30))
                .andExpect(jsonPath("$.tripDetailRefreshIntervalSeconds").value(10));
    }
}
