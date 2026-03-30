package org.hejnaluk.metrotimetable.controller;

import org.hejnaluk.metrotimetable.dto.StationRequest;
import org.hejnaluk.metrotimetable.dto.TrainDeparture;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PIDControllerTest {

    @Mock
    private TimetableRefreshService timetableRefreshService;
    @Mock
    private ParseTimetableService parseTimetableService;

    private PIDController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new PIDController(timetableRefreshService, parseTimetableService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
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
        when(parseTimetableService.getTrainsForStation(station, direction, limit)).thenReturn(List.of());

        controller.getTrainsForStation(new StationRequest(station, direction, limit));

        Mockito.verify(parseTimetableService, times(1)).getTrainsForStation(station, direction, limit);
    }

    @Test
    void getTrainsForStation_returnsServiceResult() {
        String station = "Muzeum";
        int limit = 5;
        TrainDeparture departure = new TrainDeparture("L991", 0, Instant.parse("2026-03-30T12:00:00Z"), "Depo Hostivař", List.of("Muzeum", "Depo Hostivař"));
        when(parseTimetableService.getTrainsForStation(station, null, limit)).thenReturn(List.of(departure));

        var response = controller.getTrainsForStation(new StationRequest(station, null, limit));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(departure);
    }

    @Test
    void getTrainsForStation_usesDefaultLimitWhenNotProvided() {
        String station = "Muzeum";
        Integer direction = null;
        TrainDeparture departure = new TrainDeparture("L991", 0, Instant.parse("2026-03-30T12:00:00Z"), "Depo Hostivař", List.of("Muzeum", "Depo Hostivař"));
        when(parseTimetableService.getTrainsForStation(station, direction, StationRequest.DEFAULT_LIMIT)).thenReturn(List.of(departure));

        var response = controller.getTrainsForStation(new StationRequest(station, direction, null));

        Mockito.verify(parseTimetableService, times(1)).getTrainsForStation(station, direction, StationRequest.DEFAULT_LIMIT);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(departure);
    }

    @Test
    void getTrainsForStation_defaultLimit() {
        String station = "Muzeum";
        when(parseTimetableService.getTrainsForStation(station, null, StationRequest.DEFAULT_LIMIT)).thenReturn(List.of());

        controller.getTrainsForStation(new StationRequest(station, null, StationRequest.DEFAULT_LIMIT));

        Mockito.verify(parseTimetableService, times(1)).getTrainsForStation(station, null, StationRequest.DEFAULT_LIMIT);
    }

    @Test
    void getTrainsForStation_noDirection() {
        String station = "Muzeum";
        int limit = 3;
        when(parseTimetableService.getTrainsForStation(station, null, limit)).thenReturn(List.of());

        controller.getTrainsForStation(new StationRequest(station, null, limit));

        Mockito.verify(parseTimetableService, times(1)).getTrainsForStation(station, null, limit);
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
}
