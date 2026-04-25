package org.hejnaluk.metrotimetable.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.*;

class PIDClientTest {

    private static final Path SYNC_FILE = Path.of("/tmp/timetable", PIDClient.SYNCHRONIZED_FILE_NAME);
    private static final Path EXTRACTED_FILE = Path.of("/tmp/timetable/testfile.txt");

    private PIDClient client;
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new PIDClient("http://localhost:" + server.getPort(), new SimpleMeterRegistry());
        Files.deleteIfExists(SYNC_FILE);
        Files.deleteIfExists(EXTRACTED_FILE);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
        Files.deleteIfExists(SYNC_FILE);
        Files.deleteIfExists(EXTRACTED_FILE);
    }

    @Test
    void getData_downloadsAndExtractsZip_whenNoSyncFile() throws IOException, InterruptedException {
        server.enqueue(new MockResponse()
                .setBody(new okio.Buffer().write(createZipFile()))
                .addHeader("Content-Type", "application/zip"));

        client.getData();

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("application/zip", request.getHeader("Accept"));

        assertTrue(Files.exists(EXTRACTED_FILE));
        assertEquals("Hello, this is a test file.", Files.readString(EXTRACTED_FILE));
        assertTrue(Files.exists(SYNC_FILE));
    }

    @Test
    void getData_skipsDownload_whenSyncFileIsRecent() throws IOException {
        // daysOffset is 0 in tests (no Spring injection), so "now.isAfter(futureTimestamp)" = false → skip
        Files.writeString(SYNC_FILE, ZonedDateTime.now(ZoneOffset.UTC).plusHours(1).toString());

        client.getData();

        assertEquals(0, server.getRequestCount());
    }

    @Test
    void getData_downloadsAgain_whenSyncFileIsStale() throws IOException {
        // daysOffset is 0 in tests, so "now.isAfter(pastTimestamp)" = true → download
        Files.writeString(SYNC_FILE, ZonedDateTime.now(ZoneOffset.UTC).minusHours(1).toString());

        server.enqueue(new MockResponse()
                .setBody(new okio.Buffer().write(createZipFile()))
                .addHeader("Content-Type", "application/zip"));

        client.getData();

        assertEquals(1, server.getRequestCount());
    }

    private static byte[] createZipFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("testfile.txt"));
            zos.write("Hello, this is a test file.".getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
