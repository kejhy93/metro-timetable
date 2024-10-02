package org.hejnaluk.metrotimetable.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PIDClient {

    @Value("${pid.client.path:''}")
    private String pathTOFile;

    private WebClient webClient;

    @Autowired
    public PIDClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(pathTOFile).build();
    }

    public void getData() {
        log.info("PIDClient");
        // Use WebClient to download the ZIP file as a byte array
        webClient
                .get()
                .header(HttpHeaders.ACCEPT, "application/zip")
                .retrieve()
                .bodyToMono(byte[].class)
                .flatMap(this::extractZipInMemory)
                .then(Mono.just("ZIP file downloaded and extracted successfully in memory!"))
                .onErrorResume(e -> {
                    log.error("Failed to download or extract ZIP", e);
                    return Mono.error(e);
                });
    }

    // Method to extract the ZIP file in memory from a byte array
    private Mono<Void> extractZipInMemory(byte[] zipData) {
        if (zipData == null) {
            log.error("Zip data is null");
            return Mono.error(new IllegalArgumentException("Zip data must not be null"));
        }
        try (InputStream inputStream = new java.io.ByteArrayInputStream(zipData);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                log.info("Extracting: {}", entry.getName());

                // If the entry is a file, read the content
                if (!entry.isDirectory()) {
                    try(ByteArrayOutputStream fileOutputStream = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zipInputStream.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, length);
                        }

                        // File content is now in memory
                        byte[] fileContent = fileOutputStream.toByteArray();
                        log.info("File size: {} bytes", fileContent.length);
                    }
                    // You can process the file content here (e.g., store, analyze, etc.)
                }

                zipInputStream.closeEntry();
            }

            return Mono.empty();
        } catch (IOException e) {
            log.error("Failed to download data.", e);
            return Mono.error(e);
        }
    }
}