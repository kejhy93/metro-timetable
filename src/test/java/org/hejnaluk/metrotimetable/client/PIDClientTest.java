package org.hejnaluk.metrotimetable.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.fail;


@RestClientTest(PIDClient.class)
class PIDClientTest {


    PIDClient client;
    private MockWebServer server;
    @Autowired
    private WebClient.Builder webClientBuilder;

    private static byte[] createZipFile() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        // Create a ZIP entry
        ZipEntry entry = new ZipEntry("testfile.txt");
        zos.putNextEntry(entry);

        // Write some content to the file inside the ZIP
        zos.write("Hello, this is a test file.".getBytes());

        zos.closeEntry();
        zos.close();
        return baos.toByteArray();
    }

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        client = new PIDClient(webClientBuilder);
    }

    @Test
    public void testClient() {
        // Expect a request to a specific URL and mock a response
        byte[] zipContent = null;
        try {
            zipContent = createZipFile();
        } catch (IOException e) {
            fail();
        }

        // Enqueue a response with the ZIP file content and Content-Type
        server.enqueue(new MockResponse()
                .setBody(new okio.Buffer().write(zipContent))  // Set the ZIP file as the body
                .addHeader("Content-Type", "application/zip"));

        client.getData();
    }
}