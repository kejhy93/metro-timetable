package org.hejnaluk.metrotimetable.controller;

import org.hejnaluk.metrotimetable.client.PIDClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.times;

class PIDControllerTest {

    @Mock
    private PIDClient client;

    private PIDController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new PIDController(client);
    }

    @Test
    void get() {
        controller.get();

        Mockito.verify(client, times(1)).getData();
    }
}