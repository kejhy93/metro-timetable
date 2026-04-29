package org.hejnaluk.metrotimetable.client;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.hejnaluk.metrotimetable.config.PidClientProperties;
import org.hejnaluk.metrotimetable.exception.WriteSyncFileException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class PIDClient {

    public static final String SYNCHRONIZED_FILE_NAME = "synchronized.txt";

    private final int daysOffset;
    private final Set<String> routeIds;
    private final String pathToFile;
    private final RestClient restClient;
    private final File folder;
    private final MeterRegistry meterRegistry;

    private final Timer downloadTimer;
    private final Counter downloadSkippedCounter;
    private final Counter downloadSuccessCounter;
    private final Counter downloadFailureCounter;

    @Autowired
    public PIDClient(PidClientProperties properties, MeterRegistry meterRegistry) {
        this.pathToFile = properties.getPath();
        this.daysOffset = properties.getDaysOffset();
        this.routeIds = properties.getRouteIds();
        this.meterRegistry = meterRegistry;
        log.info("Init PIDClient address is: {}", pathToFile);
        this.restClient = RestClient.builder()
                .baseUrl(pathToFile)
                .build();

        folder = Paths.get(properties.getRootPath()).toFile();
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

        downloadTimer = Timer.builder("gtfs.download.duration")
                .description("Time taken to download the GTFS ZIP from the remote source")
                .register(meterRegistry);
        downloadSkippedCounter = downloadCounter("skipped");
        downloadSuccessCounter = downloadCounter("success");
        downloadFailureCounter = downloadCounter("failure");
    }

    /**
     * Downloads and extracts the GTFS ZIP if the local copy is stale.
     *
     * @return {@code true} if fresh data was downloaded and extracted, {@code false} if the local copy was still fresh.
     */
    public boolean getData() {
        log.info("PIDClient address is: {}", pathToFile);
        if (!isDoClientCall()) {
            log.info("Client call is not needed");
            downloadSkippedCounter.increment();
            return false;
        }

        try {
            Timer.Sample downloadSample = Timer.start();
            byte[] zipData = restClient.get()
                    .header(HttpHeaders.ACCEPT, "application/zip")
                    .retrieve()
                    .body(byte[].class);
            downloadSample.stop(downloadTimer);

            log.info("Received ZIP file of size: {}", Optional.ofNullable(zipData).map(data -> data.length).orElse(0));
            extractZip(zipData);
            log.info("ZIP extraction completed");

            filterStopTimes();
            log.info("stop_times.txt pre-filtered");

            writeSuccessful();
            log.info("All files are downloaded and extracted successfully in memory!");
            downloadSuccessCounter.increment();
            return true;
        } catch (Exception e) {
            downloadFailureCounter.increment();
            throw e;
        }
    }

    private Counter downloadCounter(String result) {
        return Counter.builder("gtfs.download.total")
                .description("Number of GTFS download attempts by result")
                .tag("result", result)
                .register(meterRegistry);
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
                final var nowZonedDateTime = ZonedDateTime.ofInstant(now, ZoneOffset.UTC).minusDays(daysOffset);

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

    /**
     * Pre-filters {@code stop_times.txt} to only retain rows for the configured routes.
     * <p>
     * The full Prague GTFS {@code stop_times.txt} has ~4 million rows across all transit modes.
     * This method reduces it to only the rows needed by the configured route IDs (e.g. metro line A),
     * so subsequent parses read ~10k rows instead of ~4M rows.
     * <p>
     * Steps:
     * <ol>
     *   <li>Read {@code trips.txt} (small) to collect trip IDs belonging to the configured routes.</li>
     *   <li>Stream {@code stop_times.txt} line by line into a temp file, writing only the header
     *       and rows whose trip ID is in the collected set.</li>
     *   <li>Atomically replace {@code stop_times.txt} with the filtered temp file.</li>
     * </ol>
     */
    private void filterStopTimes() {
        if (routeIds == null || routeIds.isEmpty()) {
            log.warn("No route IDs configured, skipping stop_times.txt pre-filtering");
            return;
        }
        final Path tripsPath = Path.of(folder.getAbsolutePath(), "trips.txt");
        final Path stopTimesPath = Path.of(folder.getAbsolutePath(), "stop_times.txt");
        final Path tempPath = Path.of(folder.getAbsolutePath(), "stop_times_tmp.txt");

        // Step 1: collect trip IDs for the configured routes from trips.txt.
        // trips.txt format: route_id,service_id,trip_id,...
        // route_id is field 0, trip_id is field 2.
        final Set<String> relevantTripIds;
        try (var lines = Files.lines(tripsPath)) {
            relevantTripIds = lines
                    .filter(line -> {
                        int comma = line.indexOf(',');
                        return comma > 0 && routeIds.contains(line.substring(0, comma));
                    })
                    .map(line -> line.split(",")[2])
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            log.error("Failed to read trips.txt while filtering stop_times.txt", e);
            return;
        }
        log.info("Collected {} trip IDs for routes {}", relevantTripIds.size(), routeIds);

        if (relevantTripIds.isEmpty()) {
            log.warn("No trips found for configured routes {}. Skipping stop_times.txt filtering; file will remain unchanged.", routeIds);
            return;
        }

        // Step 2: stream stop_times.txt into a temp file, keeping header + matching rows.
        try (BufferedReader reader = Files.newBufferedReader(stopTimesPath);
             BufferedWriter writer = Files.newBufferedWriter(tempPath)) {
            // Always write the header line so ParseTimetableService can still skip(1).
            String header = reader.readLine();
            if (header != null) {
                writer.write(header);
                writer.newLine();
            }
            String line;
            while ((line = reader.readLine()) != null) {
                int comma = line.indexOf(',');
                if (comma > 0 && relevantTripIds.contains(line.substring(0, comma))) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            log.error("Failed to filter stop_times.txt", e);
            return;
        }

        // Step 3: atomically replace the original with the filtered file.
        try {
            Files.move(tempPath, stopTimesPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to replace stop_times.txt with filtered version", e);
        }
    }

    /**
     * Extracts all non-directory entries from a ZIP byte array into the configured root path.
     * <p>
     * Each entry is written using its bare filename (directory components are stripped),
     * so nested ZIP paths are flattened into the single target directory.
     *
     * @param zipData the raw bytes of the ZIP archive
     * @throws UncheckedIOException if any I/O error occurs during extraction
     */
    private void extractZip(byte[] zipData) {
        try (InputStream inputStream = new ByteArrayInputStream(zipData);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    Path path = Path.of(folder.getAbsolutePath(), Paths.get(entry.getName()).getFileName().toString());
                    Files.createDirectories(path.getParent());
                    try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = zipInputStream.read(buffer)) > 0) {
                            out.write(buffer, 0, length);
                        }
                    }
                    log.info("Extracted: {}", entry.getName());
                }
                zipInputStream.closeEntry();
            }
        } catch (IOException e) {
            log.error("Failed to extract ZIP.", e);
            throw new UncheckedIOException(e);
        }
    }
}