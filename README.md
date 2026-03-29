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
- Queries upcoming train departures by station name with optional direction filtering.
- Caches parsed data in-memory; re-downloads only when the data is older than a configurable threshold.
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

**Example:**

```bash
curl -X POST http://localhost:8080/pid/station \
  -H "Content-Type: application/json" \
  -d '{"station": "Muzeum", "direction": 0, "limit": 3}'
```

**Response** — array of `TrainDeparture` objects:

```json
[
  {
    "routeId": "L991",
    "directionId": 0,
    "departureTime": "14:32:00",
    "destination": "Depo Hostivař",
    "upcomingStations": ["Muzeum", "Náměstí Míru", "Jiřího z Poděbrad", "...]
  }
]
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

To open Grafana against a local minikube cluster:

```bash
./port-forward-grafana.sh        # serves on http://localhost:3000
./port-forward-grafana.sh 8080   # custom port
```

Login: `admin` / `admin`.

In production Grafana is exposed at `https://hejnaluk.dev/grafana` via a TLS ingress (`k8s/monitoring/grafana-ingress.yaml`).

### Prometheus stack setup

The monitoring stack (Prometheus Operator + Grafana) is expected to be installed in the `monitoring` namespace via the `kube-prometheus-stack` Helm chart. The `ServiceMonitor` carries the label `release: prometheus` so it is picked up by the operator automatically.

## Technologies Used

- **Java 25** + **Spring Boot 3.4.4**: Core runtime and framework.
- **Spring WebFlux (`WebClient`)**: Reactive HTTP client for downloading GTFS data.
- **Maven**: Build and dependency management.
- **JUnit 5 + Mockito + OkHttp MockWebServer**: Unit and integration testing.
- **Prometheus + Grafana**: Metrics collection and dashboards.
- **SonarCloud**: Code quality and security analysis.
- **CircleCI**: Continuous integration and deployment.

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