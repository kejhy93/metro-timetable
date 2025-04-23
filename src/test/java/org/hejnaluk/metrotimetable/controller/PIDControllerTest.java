package org.hejnaluk.metrotimetable.controller;

import org.hejnaluk.metrotimetable.client.PIDClient;
import org.hejnaluk.metrotimetable.service.ParseTimetableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.times;

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
}