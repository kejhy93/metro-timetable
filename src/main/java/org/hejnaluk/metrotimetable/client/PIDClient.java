package org.hejnaluk.metrotimetable.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PIDClient {

    private final static String PATH_TO_FILE = "https://data.pid.cz/PID_GTFS.zip";

    public void getData() {
        log.info("PIDClient");
        try {
            // 1. Download ZIP file as InputStream
            final var zipInputStream = downloadZipFileAsStream(PATH_TO_FILE);

            // 2. Extract the ZIP file in-memory
            extractZipInMemory(zipInputStream);

            log.info("ZIP file downloaded and extracted in-memory successfully!");
        } catch (IOException e) {
            log.error("Failed to download or extract the ZIP file in-memory.", e);
        } catch (InterruptedException e) {
            log.error("Failed to download or extract the ZIP file in-memory.", e);
            Thread.currentThread().interrupt();
        }
    }

    // Method to download the ZIP file as an InputStream using HttpClient
    private InputStream downloadZipFileAsStream(final String zipFileUrl) throws IOException, InterruptedException {
        log.info("Requesting ZIP file from URL: {}", zipFileUrl);
        final var client = HttpClient.newHttpClient();
        final var request = HttpRequest.newBuilder()
                .uri(URI.create(zipFileUrl))
                .build();

        // Send the request and get the response body as an InputStream
        final var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download file, HTTP status: " + response.statusCode());
        }
        return response.body();
    }

    // Method to extract the ZIP file in-memory
    private void extractZipInMemory(final InputStream inputStream) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                log.info("Extracting: {}", entry.getName());

                // If the entry is a file, read the content
                if (!entry.isDirectory()) {
                    try (ByteArrayOutputStream fileOutputStream = new ByteArrayOutputStream()) {
                        final var buffer = new byte[1024];
                        int length;
                        while ((length = zipInputStream.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, length);
                        }

                        // Here you have the file content in memory as a byte array
                        final var fileContent = fileOutputStream.toByteArray();
                        log.info("File size: {} bytes", fileContent.length);
                    }
                    // You can now handle the file content as needed (e.g., store in database, process, etc.)
                }

                zipInputStream.closeEntry();
            }
        }
    }
}