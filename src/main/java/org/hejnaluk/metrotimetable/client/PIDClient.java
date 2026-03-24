package org.hejnaluk.metrotimetable.client;

import lombok.extern.slf4j.Slf4j;
import org.hejnaluk.metrotimetable.exception.WriteSyncFileException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class PIDClient {

    public static final String SYNCHRONIZED_FILE_NAME = "synchronized.txt";
    public static final String ROOT_PATH_FILE = "/tmp/timetable/";

    @Value("${pid.client.days.offset:7}")
    private int daysOffset;

    private final String pathToFile;
    private final RestClient restClient;
    private final File folder;

    public PIDClient(RestClient.Builder restClientBuilder,
                     @Value("${pid.client.path:''}") String pathToFile) {
        this.pathToFile = pathToFile;
        log.info("Init PIDClient address is: {}", pathToFile);
        this.restClient = restClientBuilder
                .baseUrl(pathToFile)
                .build();

        folder = Paths.get("/tmp", "/timetable").toFile();
        if (folder.exists() && folder.isDirectory()) {
            log.info("Folder {} exists", folder.getAbsolutePath());
        } else {
            log.info("Folder {} does not exist, creating it", folder.getAbsolutePath());
            if (folder.mkdirs()) {
                log.info("Folder {} created successfully", folder.getAbsolutePath());
            } else {
                log.error("Failed to create folder {}", folder.getAbsolutePath());
            }
        }
    }

    public void getData() {
        log.info("PIDClient address is: {}", pathToFile);
        if (!isDoClientCall()) {
            log.info("Client call is not needed");
            return;
        }

        byte[] zipData = restClient.get()
                .header(HttpHeaders.ACCEPT, "application/zip")
                .retrieve()
                .body(byte[].class);

        log.info("Received ZIP file of size: {}", zipData.length);
        extractZip(zipData);
        log.info("ZIP extraction completed");

        writeSuccessful();
        log.info("All files are downloaded and extracted successfully in memory!");
    }

    /**
     * Writes a synchronization file with the current timestamp.
     * The file is named as specified by the `SYNCHRONIZED_FILE_NAME` constant.
     * If the file cannot be written, an error is logged, and a `RuntimeException` is thrown.
     */
    private void writeSuccessful() {
        final var now = Instant.now();
        final var path = Path.of(folder.getAbsolutePath(), SYNCHRONIZED_FILE_NAME);
        try {
            Files.writeString(path, ZonedDateTime.ofInstant(now, ZoneOffset.UTC).toString());
        } catch (IOException e) {
            log.error("Failed to write synchronized file.", e);
            throw new WriteSyncFileException(e);
        }
    }

    /**
     * Determines whether a client call should be made based on the existence and content
     * of a synchronization file. If the file exists, it checks whether the last synchronization
     * date is older than 7 days. If the file does not exist, a client call is required.
     *
     * @return `true` if a client call is needed, `false` otherwise
     * @throws RuntimeException if an I/O error occurs while reading the synchronization file
     */
    private boolean isDoClientCall() {
        boolean doClientCall;
        try {
            final var syncPath = Path.of(folder.getAbsolutePath(), SYNCHRONIZED_FILE_NAME);
            log.info("Synchronized file path is {}", syncPath.toAbsolutePath());
            final var exists = Files.exists(syncPath);
            if (exists) {
                log.info("Synchronized file exists");
                final var syncString = Files.readString(syncPath);

                final var parsedDateTime = getParsedDateTime(syncString);

                final var now = Instant.now();
                ZonedDateTime nowZonedDateTime = ZonedDateTime.ofInstant(now, ZoneOffset.UTC).minusDays(daysOffset);

                doClientCall = nowZonedDateTime.isAfter(parsedDateTime.orElse(ZonedDateTime.now()));
            } else {
                log.info("Synchronized file does not exist");
                doClientCall = true;
            }
        } catch (IOException e) {
            log.error("Failed to read synchronized file.", e);
            throw new WriteSyncFileException(e);
        }
        return doClientCall;
    }

    /**
     * Parses a string into a `ZonedDateTime` object.
     *
     * @param stringToParse the string to parse into a `ZonedDateTime`
     * @return an `Optional` containing the parsed `ZonedDateTime` if successful,
     * or an empty `Optional` if parsing fails
     */
    private Optional<ZonedDateTime> getParsedDateTime(String stringToParse) {
        try {
            return Optional.of(ZonedDateTime.parse(stringToParse));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private void extractZip(byte[] zipData) {
        try (InputStream inputStream = new ByteArrayInputStream(zipData);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                log.info("Extracting: {}", entry.getName());
                if (!entry.isDirectory()) {
                    try (ByteArrayOutputStream fileOutputStream = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zipInputStream.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, length);
                        }

                        byte[] fileContent = fileOutputStream.toByteArray();
                        log.info("File size: {} bytes", fileContent.length);

                        final var path = Path.of(folder.getAbsolutePath(), entry.getName());
                        log.info("Target file path is {}", path.toAbsolutePath());
                        try (FileOutputStream finalFileOutputStream = new FileOutputStream(path.toAbsolutePath().toFile())) {
                            finalFileOutputStream.write(fileContent);
                            log.info("File created successfully.");
                        }
                    }
                }
                zipInputStream.closeEntry();
            }
        } catch (IOException e) {
            log.error("Failed to extract ZIP.", e);
            throw new UncheckedIOException(e);
        }
    }
}