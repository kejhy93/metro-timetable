[![CircleCI](https://dl.circleci.com/status-badge/img/gh/kejhy93/metro-timetable/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/kejhy93/metro-timetable/tree/main)
<br>
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=kejhy93_metro-timetable&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=kejhy93_metro-timetable)
<br>
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=kejhy93_metro-timetable&metric=bugs)](https://sonarcloud.io/summary/new_code?id=kejhy93_metro-timetable)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=kejhy93_metro-timetable&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=kejhy93_metro-timetable)


# Metro Timetable

This repository contains a Java-based application that provides metro timetable functionalities. It is designed to help users access and manage metro schedules efficiently.

## Features

- Fetches and parses Prague PID GTFS timetable data automatically.
- Filters trips by active service IDs for today's date using `calendar.txt` (weekly schedule) and `calendar_dates.txt` (public holidays and exception overrides), so only trips scheduled to run today appear in results.
- Queries upcoming train departures by station name with optional direction filtering.
- Returns the full stop list for any specific trip, with arrival/departure times as ISO 8601 instants.
- Caches parsed data in-memory; re-downloads only when the data is older than a configurable threshold.
- Exposes UI refresh intervals via `GET /pid/config` so they can be tuned server-side without a client release.
- Exposes Prometheus metrics for observability.
- GTFS format: https://gtfs.org/documentation/schedule/reference/#stop_timestxt

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/kejhy93/metro-timetable.git
   ```
2. Navigate to the project directory:
   ```bash
   cd metro-timetable
   ```
3. Build and run:
   ```bash
   mvn spring-boot:run
   ```

## API

### `GET /pid`

Triggers a full timetable download and parse cycle. Returns `200 OK` with no body.

```bash
curl http://localhost:8080/pid
```

### `POST /pid/station`

Returns upcoming train departures for a given station.

**Request body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `station` | string | yes | Station name (case-insensitive) |
| `direction` | integer | no | Direction filter: `0` or `1`; omit to return both |
| `limit` | integer | no | Max results to return; must be positive (default: `StationRequest.DEFAULT_LIMIT = 5`) |
| `routeId` | string | no | Route filter (e.g. `"L991"`); omit to return departures from all routes |

**Example:**

```bash
curl -X POST http://localhost:8080/pid/station \
  -H "Content-Type: application/json" \
  -d '{"station": "Muzeum", "direction": 0, "limit": 3, "routeId": "L991"}'
```

**Response** — array of `TrainDeparture` objects:

```json
[
  {
    "routeId": "L991",
    "directionId": 0,
    "departureTime": "2026-04-17T12:32:00Z",
    "destination": "Depo Hostivař",
    "upcomingStations": ["Muzeum", "Náměstí Míru", "Jiřího z Poděbrad", "..."]
  }
]
```

### `GET /pid/trip`

Returns all stops for a specific trip. Pass `departureTime` exactly as received in the `TrainDeparture.departureTime` field.

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `routeId` | string | yes | Route identifier (e.g. `"L991"`) |
| `directionId` | integer | yes | Direction: `0` or `1` |
| `departureTime` | string | yes | ISO 8601 instant from the `TrainDeparture.departureTime` field |

**Example:**

```bash
curl "http://localhost:8080/pid/trip?routeId=L991&directionId=0&departureTime=2026-04-17T12:32:00Z"
```

**Response** — `TripDetail` object:

```json
{
  "routeId": "L991",
  "directionId": 0,
  "destination": "Depo Hostivař",
  "stops": [
    { "stopName": "Zličín",          "arrivalTime": null,                  "departureTime": "2026-04-17T12:20:00Z" },
    { "stopName": "Muzeum",          "arrivalTime": "2026-04-17T12:32:00Z","departureTime": "2026-04-17T12:32:00Z" },
    { "stopName": "Depo Hostivař",   "arrivalTime": "2026-04-17T12:45:00Z","departureTime": null }
  ]
}
```

`arrivalTime` is `null` for the first stop; `departureTime` is `null` for the last stop.

Returns `404` when the trip is no longer in cache (e.g. data refreshed between the departures fetch and the tap). Returns `400` when `departureTime` is not a valid ISO 8601 instant or a required parameter is missing.

### `GET /pid/config`

Returns the server-configured UI refresh intervals. Clients should re-fetch this periodically so interval changes take effect without a client release.

**Example:**

```bash
curl http://localhost:8080/pid/config
```

**Response:**

```json
{
  "departuresRefreshIntervalSeconds": 30,
  "tripDetailRefreshIntervalSeconds": 10
}
```

Configured via `application.properties`:

```properties
pid.refresh.departures.interval.seconds=30
pid.refresh.trip-detail.interval.seconds=10
```

## Monitoring

The application exposes Prometheus metrics via Spring Boot Actuator at `/actuator/prometheus`. A `ServiceMonitor` resource (in `k8s/base/servicemonitor.yaml`) tells the Prometheus Operator to scrape that endpoint every 30 seconds.

### Metrics

| Metric | Type | Description |
|---|---|---|
| `file_parse_seconds` | Timer | Time to parse GTFS text files |
| `create_releationship_seconds` | Timer | Time to build route→stop relationships |
| `create_cache_seconds` | Timer | Time to populate in-memory caches |
| `station_query_seconds` | Timer | Latency of `/pid/station` queries |
| `http_server_requests_seconds` | Timer | Standard Spring MVC request metrics |
| `jvm_memory_*_bytes` | Gauge | JVM heap and non-heap memory |
| `jvm_gc_pause_seconds` | Timer | GC pause rate and duration by action/cause |
| `jvm_threads_live_threads` | Gauge | Live thread count |
| `jvm_classes_loaded_classes` | Gauge | Loaded class count |
| `process_cpu_usage` / `system_cpu_usage` | Gauge | CPU utilisation |

### Grafana Dashboard

The dashboard JSON is at `grafana-dashboard/grafana.json`. Import it via **Dashboards → Import** in Grafana. It is parameterised by `namespace` (auto-populated from `jvm_memory_used_bytes` labels) so it works across `metro-dev`, `metro-test`, and `metro-prod`.

**Rows:**
- **Parse timetable** — p95/p99 and average parse durations
- **Station query** — p50/p95/p99 latency for station lookups
- **HTTP** — request rate and average response time
- **JVM Heap Memory Detailed** — used, committed, and max heap per memory pool
- **JVM Runtime** — GC pause rate, GC avg pause duration, non-heap/metaspace, CPU usage, live threads, loaded classes

### Local access

`deploy.sh --env local` automatically starts a blocking port-forward on `http://localhost:3000` after the install completes. To reconnect later (without re-running the full deploy), use the standalone script instead:

```bash
./port-forward-grafana.sh        # serves on http://localhost:3001
./port-forward-grafana.sh 8080   # custom port
```

Login: `admin` / `admin`.

In production Grafana is exposed at `https://hejnaluk.dev/grafana` via a Traefik TLS ingress backed by a cert-manager `letsencrypt-prod` certificate.

### Monitoring stack setup

The monitoring stack lives in a separate repository — [github.com/kejhy93/monitoring](https://github.com/kejhy93/monitoring) — under the `k8s/` directory. Clone it alongside this repo before running the commands below.

```bash
git clone git@github.com:kejhy93/monitoring.git
cd monitoring/k8s
```

It bundles:

| Component | Role |
|---|---|
| `kube-prometheus-stack` | Prometheus Operator + Grafana + Alertmanager |
| Loki (SingleBinary) | Log aggregation; filesystem storage, 30-day retention |
| Promtail | Log shipper — collects pod logs and forwards them to Loki |

Grafana is pre-configured with Loki as an additional data source, so metrics and logs are available in the same UI.

**Deploy the stack:**

```bash
cd monitoring/k8s

# Local (minikube) — port-forwards Grafana on http://localhost:3000 after install
./deploy.sh --env local

# Production (k3s on VPS) — applies TLS ingress at https://hejnaluk.dev/grafana
./deploy.sh --env prod
```

The script installs/upgrades all three Helm releases in the `monitoring` namespace, then generates and applies Grafana dashboard ConfigMaps.

**Dashboard workflow:**

Dashboard JSON files live in `monitoring/k8s/dashboards/` (e.g. `metro-timetable.json`). `deploy.sh` converts each JSON to a Kubernetes ConfigMap via `dashboard-to-configmap.sh` and labels it `grafana_dashboard: "1"`. The `grafana-sc-dashboard` sidecar inside the Grafana pod watches for ConfigMaps with that label and loads them automatically; `deploy.sh` also restarts Grafana after applying changes to ensure they take effect.

To update a dashboard: edit the JSON in `dashboards/`, then re-run `deploy.sh`.

**ServiceMonitor:** Each project (including this one) applies its own `ServiceMonitor` pointing at this stack. The `ServiceMonitor` in `server/k8s/base/servicemonitor.yaml` carries the label `release: prometheus` so the Prometheus Operator picks it up automatically.

## UI (Kotlin Multiplatform)

The `ui/` directory contains a Kotlin Multiplatform (KMP) app built with Compose Multiplatform targeting Android, iOS, Desktop (JVM), and Web (JS + Wasm).

### Structure

```
ui/
└── composeApp/
    └── src/
        ├── commonMain/       # Shared Compose UI and business logic
        │   ├── data/
        │   │   ├── api/      # Ktor HTTP client (station, trip detail, config)
        │   │   ├── local/    # Hard-coded metro line/station data
        │   │   ├── AppConfigStore.kt  # Polls GET /pid/config; StateFlow<AppConfig>
        │   │   └── MetroRepository.kt
        │   ├── di/           # Koin dependency injection module
        │   ├── navigation/   # Type-safe Compose Navigation
        │   └── presentation/
        │       ├── line/       # Line selection screen
        │       ├── station/    # Station list screen
        │       ├── direction/  # Direction selection screen
        │       ├── departures/ # Upcoming departures (tappable cards)
        │       └── detail/     # Train detail screen (all stops, highlights)
        ├── androidMain/      # Android entry point (MainActivity)
        ├── iosMain/          # iOS entry point (MainViewController)
        ├── jvmMain/          # Desktop entry point
        ├── jsMain/           # JS/browser entry point
        └── wasmJsMain/       # Wasm/browser entry point
```

### User flow

**Line** → **Station** → **Direction** → **Departures** → **Train Detail**

1. Pick a metro line (A / B / C, colour-coded).
2. Pick a station along that line.
3. Pick a direction (terminus 0 or terminus 1).
4. View upcoming train departures. Tap a card to open the train detail screen.
5. See all stops for that trip with scheduled times and a relative countdown. Two distinct highlights show where the train currently is (filled `primaryContainer`) and where your station is (secondary-colour border). The list auto-scrolls to the train's current position on first load and refreshes every 10 seconds.

### Key dependencies

| Library | Role |
|---|---|
| Compose Multiplatform | Shared UI across all targets |
| Ktor | HTTP client for `POST /pid/station` |
| Koin | Dependency injection |
| Jetpack Navigation (Compose) | Type-safe screen navigation |
| `kotlinx.datetime` | Date/time handling |
| `kotlinx.serialization` | JSON serialisation |

### Backend connection

`MetroApiClient` calls `POST /pid/station`, `GET /pid/trip`, and `GET /pid/config`. In production the nginx container reverse-proxies `/pid/` to the backend `metro-timetable` service, so the frontend and API share the same origin (`https://hejnaluk.dev`). To point at a local backend, change `BASE_URL` in `ui/composeApp/src/commonMain/kotlin/.../data/api/MetroApiClient.kt`.

The app fetches `GET /pid/config` on startup and re-polls it every 60 seconds. If the endpoint is unreachable, hardcoded defaults (30 s / 10 s) are used and polling continues in the background.

### Build and run

All commands run from the `ui/` directory.

**Android:**
```bash
./gradlew :composeApp:assembleDebug
```

**Desktop (JVM):**
```bash
./gradlew :composeApp:run
```

**Web (Wasm — modern browsers):**
```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

**Web (JS — wider browser support):**
```bash
./gradlew :composeApp:jsBrowserDevelopmentRun
```

**iOS:** Open `ui/iosApp` in Xcode and run.

## Technologies Used

- **Java 25** + **Spring Boot 3.4.4**: Core runtime and framework.
- **Spring WebFlux (`WebClient`)**: Reactive HTTP client for downloading GTFS data.
- **Maven**: Build and dependency management.
- **JUnit 5 + Mockito + OkHttp MockWebServer**: Unit and integration testing.
- **Prometheus + Grafana**: Metrics collection and dashboards.
- **SonarCloud**: Code quality and security analysis.
- **GitHub Actions**: CI/CD — unified `deploy-prod.yml` builds and deploys server, UI, and desktop in one workflow.
- **nginx**: Serves the wasmJs web frontend and reverse-proxies `/pid/` API calls to the backend.
- **Kotlin Multiplatform + Compose Multiplatform**: Cross-platform UI (Android, iOS, Desktop, Web).
- **Ktor**: Multiplatform HTTP client used in the KMP UI.
- **Koin**: Dependency injection for the KMP UI.

## Release

Releases are triggered by pushing a version tag. This runs three automated deployments in parallel via GitHub Actions.

### How to release

Use the provided script — it reads the latest tag, prompts for release type, and pushes the new tag:

```bash
./release.sh
```

The script will show the current tag, let you pick patch / minor / major, and ask for confirmation before tagging and pushing.

### What happens

All three builds run in parallel. A `gate` job (requiring **production environment approval**) must pass before any deployment proceeds. Once approved, the three deploy jobs run in parallel.

| Job | What it does | Output |
|---|---|---|
| `build-server` → `deploy-server` | Builds server JAR + Docker image, pushes to GHCR, then SSHes to VPS and runs `kubectl set image` | New server image live in **k8s** (`metro-prod`) |
| `build-ui` → `deploy-ui` | Builds `wasmJs` bundle, bakes it into an **nginx Docker image**, pushes to GHCR, then SSHes to VPS and runs `kubectl set image` | New UI image live in **k8s** (`metro-prod`) at `https://hejnaluk.dev/metro` |
| `build-desktop` → `deploy-desktop` | Builds uber JAR | JAR attached to **GitHub Release** `v1.2.3` |

The nginx container serves the static wasmJs frontend at `/metro/` and reverse-proxies `/pid/` requests to the backend `metro-timetable` service, keeping all traffic on a single origin.

### Version tag format

Tags must match `v*.*.*` (e.g. `v1.0.0`, `v2.3.1`). Non-matching tags do not trigger any deployment.

### Deployed URLs

| Artefact | URL |
|---|---|
| Web app (nginx / k8s) | https://hejnaluk.dev/metro |
| Desktop JAR (GitHub Release) | https://github.com/kejhy93/metro-timetable/releases/latest |

## Contributing

Contributions are welcome! If you'd like to contribute:
1. Fork the repository.
2. Create a new branch for your feature or bug fix:
   ```bash
   git checkout -b feature-name
   ```
3. Commit your changes:
   ```bash
   git commit -m "Add new feature"
   ```
4. Push to your branch:
   ```bash
   git push origin feature-name
   ```
5. Create a pull request.

## License

This project is licensed under the [MIT License](LICENSE).

## Contact

For any inquiries or support, please contact [kejhy93](https://github.com/kejhy93).

---