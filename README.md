# 🛡️ FluxGate

<div align="center">

[![Render Live Deployment](https://img.shields.io/badge/Render-Live%20Deployment-46E3B7?style=for-the-badge&logo=render&logoColor=white)](https://fluxgate-bvjt.onrender.com/fluxgate/health)
[![Java 21](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)

<p align="center">
  <b>🌐 Live Render Deployment: <a href="https://fluxgate-bvjt.onrender.com/fluxgate/health">https://fluxgate-bvjt.onrender.com</a></b>
</p>

</div>

---

**FluxGate** is a lightweight, high-performance Java reverse proxy and load balancing system built with **Spring Boot 3** and **Java 21**. It acts as a unified ingress gateway for application traffic, intelligently distributing incoming HTTP requests across backend servers using thread-safe Round Robin load balancing, automated periodic health checking, and fault-tolerant error handling.

---

## 🌐 Live Preview & Endpoints

FluxGate is actively deployed on **Render**:

| Endpoint | Method | Live Link | Description |
| :--- | :---: | :--- | :--- |
| **FluxGate Health** | `GET` | [`/fluxgate/health`](https://fluxgate-bvjt.onrender.com/fluxgate/health) | Reports proxy operational status & healthy backend count |
| **Backend Metrics** | `GET` | [`/fluxgate/status`](https://fluxgate-bvjt.onrender.com/fluxgate/status) | Real-time health state, latency, and request distribution |
| **Proxy Gateway** | `GET` | [`/hello`](https://fluxgate-bvjt.onrender.com/hello) | Load-balanced forwarding to upstream backend instances |

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

1. **Client Ingress**: FluxGate listens on port `8080` (or dynamic `${PORT}` on cloud providers like Render).
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
├── Dockerfile                      # FluxGate multi-stage container build
├── docker-compose.yml              # Local testing cluster (FluxGate + 3 demo backends)
├── pom.xml                         # Spring Boot 3.4.2 & Java 21 configuration
│
├── demo-backend/                   # Independent demo backend service for testing
│   ├── Dockerfile                  # Lightweight Node.js container
│   ├── package.json                # Zero-dependency HTTP service
│   ├── server.js                   # Handles /health, /hello, and request echo
│   └── README.md
│
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

### Option 1: Docker Compose (Full Cluster Test)

Run FluxGate and **3 backend instances** with a single command:

```bash
docker-compose up --build
```

**Test Round Robin load balancing**:
```bash
curl http://localhost:8080/hello
```
*Run it multiple times — notice the response alternate across `backend-1`, `backend-2`, and `backend-3`.*

---

### Option 2: Native Execution

1. **Start the 3 demo backends** (in separate terminals or background):
   ```bash
   cd demo-backend
   PORT=8081 SERVER_NAME=backend-1 node server.js
   PORT=8082 SERVER_NAME=backend-2 node server.js
   PORT=8083 SERVER_NAME=backend-3 node server.js
   ```

2. **Start FluxGate**:
   ```bash
   ./mvnw spring-boot:run
   ```

---

## 🌐 Deploying 3 Backend Instances on Render

The repository includes a dedicated, zero-dependency demo backend in [`demo-backend/`](demo-backend/) designed specifically for instant deployment on Render.

Follow these steps to deploy **3 independent backend instances**:

### Step 1: Deploy Backend 1
1. Log in to [Render Dashboard](https://dashboard.render.com).
2. Click **New +** → **Web Service**.
3. Connect your repository: **`SohamSood/FLuxGate`**.
4. Configure service settings:
   - **Name**: `fluxgate-backend-1`
   - **Language / Runtime**: `Docker`
   - **Root Directory**: `demo-backend`  *(⚠️ Important: specifies the subfolder)*
   - **Region**: Same region as your FluxGate service
   - **Instance Type**: `Free`
5. Under **Environment Variables**, add:
   - `SERVER_NAME` = `backend-1`
6. Click **Create Web Service**. Note the deployed URL (e.g., `https://fluxgate-backend-1.onrender.com`).

---

### Step 2: Deploy Backend 2 & Backend 3
Repeat the exact same steps twice:
- **Backend 2**:
  - **Name**: `fluxgate-backend-2`
  - **Root Directory**: `demo-backend`
  - **Environment Variable**: `SERVER_NAME` = `backend-2`
- **Backend 3**:
  - **Name**: `fluxgate-backend-3`
  - **Root Directory**: `demo-backend`
  - **Environment Variable**: `SERVER_NAME` = `backend-3`

---

### Step 3: Connect Backends to FluxGate on Render
Once all three backends are live:

1. Open your existing **`fluxgate-bvjt`** service on Render.
2. Navigate to the **Environment** tab.
3. Add or update the **`FLUXGATE_BACKENDS`** variable:
   ```text
   FLUXGATE_BACKENDS = https://fluxgate-backend-1.onrender.com,https://fluxgate-backend-2.onrender.com,https://fluxgate-backend-3.onrender.com
   ```
4. Click **Save Changes**. Render will automatically redeploy FluxGate.
5. Check [`https://fluxgate-bvjt.onrender.com/fluxgate/health`](https://fluxgate-bvjt.onrender.com/fluxgate/health) — you will see:
   ```json
   {
     "status": "UP",
     "service": "FluxGate",
     "healthyBackends": 3,
     "totalBackends": 3
   }
   ```
6. Now visit [`https://fluxgate-bvjt.onrender.com/hello`](https://fluxgate-bvjt.onrender.com/hello) and refresh — you will see responses seamlessly cycle between **`backend-1`**, **`backend-2`**, and **`backend-3`**!

---

## ⚙️ Configuration Reference

| Variable | Property Key | Default | Description |
| :--- | :--- | :--- | :--- |
| `PORT` | `server.port` | `8080` | Port FluxGate listens on (injected automatically by Render) |
| `FLUXGATE_BACKENDS` | `fluxgate.backends` | `http://localhost:8081,http://localhost:8082,http://localhost:8083` | Comma-separated upstream backend URLs |
| `FLUXGATE_HEALTH_CHECK_ENABLED` | `fluxgate.health-check.enabled` | `true` | Enable/disable background health checking |
| `FLUXGATE_HEALTH_CHECK_INTERVAL_MS`| `fluxgate.health-check.interval-ms` | `10000` | Health check probe interval (ms) |
| `FLUXGATE_HEALTH_CHECK_TIMEOUT_MS` | `fluxgate.health-check.timeout-ms` | `2000` | Probe timeout before marking unhealthy (ms) |
| `FLUXGATE_PROXY_READ_TIMEOUT_MS`   | `fluxgate.proxy.read-timeout-ms` | `10000` | Upstream response read timeout (ms) |

---

## 🧪 Automated Tests

```bash
./mvnw test
```

---

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
