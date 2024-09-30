package org.hejnaluk.metrotimetable.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PIDClientTest {

    PIDClient client;

    @BeforeEach
    void setUp() {
        client = new PIDClient();
    }

    @Test
    void getData() {
        client.getData();
    }
}