# 🛡️ FluxGate

**FluxGate** is a lightweight, high-performance Java reverse proxy and load balancing system built with **Spring Boot 3** and **Java 21**. It acts as a unified ingress gateway for application traffic, intelligently distributing incoming HTTP requests across backend servers using thread-safe Round Robin load balancing, automated periodic health checking, and fault-tolerant error handling.

---

## 🏗️ Architecture

```
                                    ┌────────────────────────┐
                                    │ Backend 1 (:8081 / :80) │
                                    └────────────────────────┘
                                               ▲
                                               │ (Round Robin)
Clients  ──────▶ [ FluxGate:8080 ] ────────────┼────────────────────────┐
 (HTTP)            Reverse Proxy               │                        │
                   & Load Balancer             ▼                        ▼
                                    ┌────────────────────────┐  ┌────────────────────────┐
                                    │ Backend 2 (:8082 / :80) │  │ Backend 3 (:8083 / :80) │
                                    └────────────────────────┘  └────────────────────────┘
```

1. **Client Ingress**: FluxGate listens on port `8080` (or dynamic `${PORT}` on cloud providers).
2. **Strategy Selection**: Selects a healthy backend server using a thread-safe **Round Robin** algorithm.
3. **Transparent Proxying**: Forwards the exact HTTP method, path, query parameters, headers, and request body, appending standard `X-Forwarded-*` headers.
4. **Health Monitoring**: Probes `/health` on all registered backends periodically, isolating unhealthy instances automatically.

---

## ✨ Implemented Features

- **🔄 Reverse Proxying**: Preserves all HTTP methods (`GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `HEAD`, `OPTIONS`), paths, queries, request bodies, and headers (while stripping hop-by-hop headers per RFC 7230).
- **⚖️ Thread-Safe Round Robin**: Distributes requests evenly across healthy backend instances using lock-free atomic modular arithmetic.
- **💓 Automated Health Checks**: Probes `/health` on each backend periodically (`10s` default). Unhealthy nodes are removed from rotation and reinstated once healthy.
- **🛡️ Robust Error Handling**:
  - `502 Bad Gateway`: When upstream backend connection fails or is refused.
  - `504 Gateway Timeout`: When upstream backend exceeds read/connection timeout.
  - `503 Service Unavailable`: When all backend nodes are offline or unhealthy.
  - Exposes zero internal stack traces to clients.
- **📊 Real-time Observability**:
  - Structured logging with method, path, query, target backend, response code, and latency in milliseconds.
  - Self-monitoring endpoints (`/fluxgate/health`, `/fluxgate/status`).
- **🐳 Cloud-Ready (Render & Docker)**: Fully containerized with dynamic `$PORT` support and configurable backend registries via environment variables.

---

## 📂 Project Structure

```text
Projects/Flux_Gate/
├── Dockerfile                      # Multi-stage production container build
├── docker-compose.yml              # Local testing with 3 whoami backends
├── pom.xml                         # Spring Boot 3.4.2 & Java 21 configuration
└── src/
    ├── main/
    │   ├── java/com/fluxgate/
    │   │   ├── FluxgateApplication.java    # Main Spring Boot entry point
    │   │   ├── config/
    │   │   │   ├── AppConfig.java          # HttpClient & Scheduling beans
    │   │   │   └── FluxGateProperties.java # Typed configuration bindings
    │   │   ├── controller/
    │   │   │   ├── FluxGateManagementController.java # /fluxgate/health & status
    │   │   │   ├── GlobalExceptionHandler.java       # Clean error responses
    │   │   │   └── ReverseProxyController.java       # Catch-all request dispatcher
    │   │   ├── model/
    │   │   │   ├── BackendServer.java      # State, health, and metrics model
    │   │   │   ├── ErrorResponse.java      # Standardized JSON error payload
    │   │   │   └── ProxyResponse.java      # Internal response container
    │   │   ├── service/
    │   │   │   ├── BackendRegistry.java    # Dynamic server registry
    │   │   │   ├── HealthCheckService.java # Scheduled health check runner
    │   │   │   └── ProxyService.java       # HTTP forwarding engine
    │   │   └── strategy/
    │   │       ├── LoadBalancingStrategy.java # Strategy interface
    │   │       └── RoundRobinStrategy.java    # Atomic round robin implementation
    │   └── resources/
    │       └── application.properties      # Default port and backends
    └── test/
        └── java/com/fluxgate/              # Full suite of unit & integration tests
```

---

## 🚀 Running Locally

### Option 1: Native Maven (Local JVM)

**Prerequisites**: Java 21 JDK installed.

1. **Clone the repository**:
   ```bash
   git clone https://github.com/SohamSood/FLuxGate.git
   cd FLuxGate
   ```
2. **Build and test**:
   ```bash
   ./mvnw clean test
   ```
3. **Run the proxy**:
   ```bash
   ./mvnw spring-boot:run
   ```
   *FluxGate will start on port `8080` forwarding to the configured backends.*

---

### Option 2: Docker Compose (Recommended for Complete Local Testing)

A complete local cluster featuring **FluxGate** and **3 independent backend instances** (`traefik/whoami` on ports 8081, 8082, 8083) is included:

```bash
docker-compose up --build
```

**Test Round Robin load balancing**:
```bash
curl http://localhost:8080/
```
*Run it multiple times — notice the returned `Hostname` alternate between `fluxgate-backend-1`, `fluxgate-backend-2`, and `fluxgate-backend-3`.*

---

## ⚙️ Configuration & Environment Variables

| Variable | Property Key | Default | Description |
| :--- | :--- | :--- | :--- |
| `PORT` | `server.port` | `8080` | Port FluxGate listens on (automatically injected by Render) |
| `FLUXGATE_BACKENDS` | `fluxgate.backends` | `http://localhost:8081,http://localhost:8082,http://localhost:8083` | Comma-separated list of backend URLs |
| `FLUXGATE_HEALTH_CHECK_ENABLED` | `fluxgate.health-check.enabled` | `true` | Enable/disable background health checking |
| `FLUXGATE_HEALTH_CHECK_INTERVAL_MS`| `fluxgate.health-check.interval-ms` | `10000` | Health check probe interval (ms) |
| `FLUXGATE_HEALTH_CHECK_TIMEOUT_MS` | `fluxgate.health-check.timeout-ms` | `2000` | Probe timeout before marking unhealthy (ms) |
| `FLUXGATE_PROXY_READ_TIMEOUT_MS`   | `fluxgate.proxy.read-timeout-ms` | `10000` | Upstream response read timeout (ms) |

---

## 🌐 Deploying to Render

Deploying FluxGate as a Docker Web Service on Render takes under 2 minutes:

1. **Push your code to GitHub**:
   Ensure your latest code is on branch `main` at `https://github.com/SohamSood/FLuxGate`.
2. **Create New Web Service on Render**:
   - Go to [dashboard.render.com](https://dashboard.render.com).
   - Click **New +** → **Web Service**.
   - Connect your **`FLuxGate`** repository.
3. **Configure the Service**:
   - **Environment**: `Docker`
   - **Region**: Choose the region closest to your backends.
   - **Branch**: `main`
   - Render automatically detects the `Dockerfile` at the root.
4. **Set Environment Variables**:
   Under the **Environment Variables** tab, add your live upstream backend URLs:
   - `FLUXGATE_BACKENDS` = `https://api-1.example.com,https://api-2.example.com,https://api-3.example.com`
   *(Note: Render automatically injects the `PORT` environment variable; FluxGate binds to it automatically).*
5. **Health Check Path**:
   - Set Health Check Path to: `/fluxgate/health`
6. **Deploy**:
   - Click **Create Web Service**. FluxGate will build and deploy!

---

## 📡 API & Management Endpoints

| Endpoint | Method | Description |
| :--- | :---: | :--- |
| `/fluxgate/health` | `GET` | Self-health check reporting `UP` and active backend count |
| `/fluxgate/status` | `GET` | Detailed live metrics (all backends, request counts, failure reasons) |
| `/fluxgate/backends` | `POST` | Dynamically register a new backend (`{"url": "http://new-server:8080"}`) |
| `/fluxgate/backends?url=...` | `DELETE`| Dynamically deregister a backend instance |
| `/**` | Any | Proxied transparently to upstream backends via Round Robin |

---

## 🧪 Running Automated Tests

FluxGate includes comprehensive unit and integration test suites covering concurrent thread-safety, request header/body fidelity, round-robin distribution, and fault tolerance:

```bash
./mvnw test
```

---

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
