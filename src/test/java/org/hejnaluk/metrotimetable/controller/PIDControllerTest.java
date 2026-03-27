package org.hejnaluk.metrotimetable.controller;

import org.hejnaluk.metrotimetable.client.PIDClient;
import org.hejnaluk.metrotimetable.dto.TrainDeparture;
import org.hejnaluk.metrotimetable.service.ParseTimetableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class PIDControllerTest {

    @Mock
    private PIDClient pidClient;
    @Mock
    private ParseTimetableService parseTimetableService;

    private PIDController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new PIDController(pidClient, parseTimetableService);
    }

    @Test
    void getTimetableChange() {
        controller.getTimetableChange();

        Mockito.verify(pidClient, times(1)).getData();
    }

    @Test
    void getTrainsForStation_callsService() {
        String station = "Muzeum";
        Integer direction = 0;
        int limit = 5;
        when(parseTimetableService.getTrainsForStation(station, direction, limit)).thenReturn(List.of());

        controller.getTrainsForStation(station, direction, limit);

        Mockito.verify(parseTimetableService, times(1)).getTrainsForStation(station, direction, limit);
    }

    @Test
    void getTrainsForStation_returnsServiceResult() {
        String station = "Muzeum";
        int limit = 5;
        TrainDeparture departure = new TrainDeparture("L991", 0, LocalTime.of(14, 0), "Depo Hostivař", List.of("Muzeum", "Depo Hostivař"));
        when(parseTimetableService.getTrainsForStation(station, null, limit)).thenReturn(List.of(departure));

        var response = controller.getTrainsForStation(station, null, limit);

        assertThat(response.getBody()).containsExactly(departure);
    }

    @Test
    void getTrainsForStation_defaultLimit() {
        String station = "Muzeum";
        int defaultLimit = 5;
        when(parseTimetableService.getTrainsForStation(station, null, defaultLimit)).thenReturn(List.of());

        controller.getTrainsForStation(station, null, defaultLimit);

        Mockito.verify(parseTimetableService, times(1)).getTrainsForStation(station, null, defaultLimit);
    }

    @Test
    void getTrainsForStation_noDirection() {
        String station = "Muzeum";
        int limit = 3;
        when(parseTimetableService.getTrainsForStation(station, null, limit)).thenReturn(List.of());

        controller.getTrainsForStation(station, null, limit);

        Mockito.verify(parseTimetableService, times(1)).getTrainsForStation(station, null, limit);
    }
}