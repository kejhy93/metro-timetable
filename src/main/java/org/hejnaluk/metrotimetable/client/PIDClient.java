package org.hejnaluk.metrotimetable.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hejnaluk.metrotimetable.exception.WriteSyncFileException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PIDClient {

    public static final String SYNCHRONIZED_FILE_NAME = "synchronized.txt";
    public static final int MAX_WEBCLIENT_MEMORY_IN_MB = 128;
    public static final String ROOT_PATH_FILE = "/tmp/timetable/";

    @Value("${pid.client.days.offset:7}")
    private int daysOffset;

    private String pathTOFile;

    private WebClient webClient;

    private final File folder;

    @Autowired
    public PIDClient(WebClient.Builder webClientBuilder,
                     @Value("${pid.client.path:''}")
                     String pathTOFile) {
        this.pathTOFile = pathTOFile;
        log.info("Init PIDClient address is: {}", pathTOFile);
        this.webClient = webClientBuilder
                .baseUrl(pathTOFile)
                .codecs(config -> config
                        .defaultCodecs()
                        .maxInMemorySize(MAX_WEBCLIENT_MEMORY_IN_MB * 1024 * 1024)) // 128 MB
                .build();
        log.info("WebClient initialized with base URL: {}", pathTOFile);
        log.info("WebClient initialized with max memory size: {} MB", MAX_WEBCLIENT_MEMORY_IN_MB);

        folder = Paths.get("/tmp", "/timetable").toFile();
        if ( folder.exists() && folder.isDirectory() ) {
            log.info("Folder {} exists", folder.getAbsolutePath());
        } else {
            log.info("Folder {} does not exist, creating it", folder.getAbsolutePath());
            if ( folder.mkdirs() ) {
                log.info("Folder {} created successfully", folder.getAbsolutePath());
            } else {
                log.error("Failed to create folder {}", folder.getAbsolutePath());
            }
        }
    }

    public void getData() {
        log.info("PIDClient address is: {}", pathTOFile);
        if ( !isDoClientCall() ) {
            log.info("Client call is not needed");
            return;
        }

        // REACTIVE
        // Use WebClient to download the ZIP file as a byte array
        webClient
                .get()
                .header(HttpHeaders.ACCEPT, "application/zip")
                .retrieve()
                .bodyToMono(byte[].class)
                .checkpoint("After bodyToMono")
                .doOnNext(bytes -> log.info("Received ZIP file of size: {}", bytes.length))
                .flatMap(this::extractZipInMemory)
                .checkpoint("After extractZipInMemory")
                .doOnSuccess(v -> log.info("ZIP extraction completed"))
                .then(Mono.just("ZIP file downloaded and extracted successfully in memory!"))
                .onErrorResume(e -> {
                    log.error("Failed to download or extract ZIP", e);
                    return Mono.error(e);
                })
                .block();

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
        if (isNotPubliclyWritable(path)) {
            return;
        }
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
            if (isNotPubliclyWritable(syncPath)) {
                return false;
            }
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

    // Method to extract the ZIP file in memory from a byte array
    private Mono<Void> extractZipInMemory(byte[] zipData) {
        return Mono.<Void>create(sink -> {
            try (InputStream inputStream = new java.io.ByteArrayInputStream(zipData);
                 ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    log.info("Extracting: {}", entry.getName());
                    // If the entry is a file, read the content
                    if (!entry.isDirectory()) {
                        try (ByteArrayOutputStream fileOutputStream = new ByteArrayOutputStream()) {
                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = zipInputStream.read(buffer)) > 0) {
                                fileOutputStream.write(buffer, 0, length);
                            }

                            // File content is now in memory
                            byte[] fileContent = fileOutputStream.toByteArray();
                            log.info("File size: {} bytes", fileContent.length);

                            log.info("File name is {}", entry.getName());
                            final var path = Path.of(folder.getAbsolutePath(), entry.getName());
                            if (isNotPubliclyWritable(path)) {
                                return;
                            }
                            log.info("Target file path is {}", path.toAbsolutePath());
                            try (FileOutputStream finalFileOutputStream = new FileOutputStream(path.toAbsolutePath().toFile())) {
                                finalFileOutputStream.write(fileContent);
                                log.info("File created successfully.");
                            }
                        }
                        // You can process the file content here (e.g., store, analyze, etc.)
                    }
                    zipInputStream.closeEntry();
                    sink.success();
                }
            } catch (IOException e) {
                log.error("Failed to download data.", e);
                sink.error( e);
            }

        }).subscribeOn(Schedulers.boundedElastic());
    }

    public static boolean isNotPubliclyWritable(Path directory) {
        try {
            // Get the POSIX file permissions of the directory
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(directory);

            // Check if the directory is writable by "others"
            return !permissions.contains(PosixFilePermission.OTHERS_WRITE);
        } catch (IOException e) {
            log.error("Failed to check directory permissions: {}", e.getMessage());
            return true;
        } catch (UnsupportedOperationException e) {
            log.error("POSIX file permissions are not supported on this file system: {}", e.getMessage());
            return true;
        }
    }
}