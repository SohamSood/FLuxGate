# 🛡️ FluxGate

<div align="center">

[![Render Live Deployment](https://img.shields.io/badge/Render-Live%20Dashboard-46E3B7?style=for-the-badge&logo=render&logoColor=white)](https://fluxgate-bvjt.onrender.com/)
[![Java 21](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)

<p align="center">
  <b>🌐 Live Web Dashboard & Proxy Console: <a href="https://fluxgate-bvjt.onrender.com/">https://fluxgate-bvjt.onrender.com</a></b>
</p>

</div>

---

**FluxGate** is an enterprise-grade, high-performance reverse proxy and load balancing system built with **Spring Boot 3** and **Java 21**. It functions as a unified ingress gateway that dynamically routes, balances, and monitors traffic across distributed backend microservices.

Featuring an **interactive Web Dashboard**, **Traffic Guardian Adaptive Rate Limiter** (token-bucket per IP), **real-time Ingress Stream observability**, **dynamic algorithm switching** (Round Robin, Least Connections, Random), **automated health monitoring**, **embedded demo backend servers**, and **runtime backend registration**.

---

## 🌐 Live Preview & Endpoints

FluxGate is actively deployed on **Render**:

| Interface / Endpoint | Method | Live Link | Description |
| :--- | :---: | :--- | :--- |
| **Interactive Dashboard** | `GET` | [`/`](https://fluxgate-bvjt.onrender.com/) or [`/dashboard`](https://fluxgate-bvjt.onrender.com/dashboard) | Real-time visual metrics, rate limiter control, live traffic stream, backend manager & proxy simulator |
| **System Status** | `GET` | [`/fluxgate/status`](https://fluxgate-bvjt.onrender.com/fluxgate/status) | Real-time health metrics, active strategy, rate limiter state & backend telemetry (JSON) |
| **Self-Health Probe** | `GET` | [`/fluxgate/health`](https://fluxgate-bvjt.onrender.com/fluxgate/health) | Orchestrator health check (Render / K8s / ECS) |
| **Rate Limiter Policy** | `GET`/`POST` | [`/fluxgate/rate-limit`](https://fluxgate-bvjt.onrender.com/fluxgate/rate-limit) | Inspect or dynamically reconfigure token-bucket capacity and refill rate |
| **Traffic Stream & Metrics** | `GET`/`DELETE` | [`/fluxgate/metrics`](https://fluxgate-bvjt.onrender.com/fluxgate/metrics) | Real-time stream of recent transactions, status code breakdowns, and latency metrics |
| **Strategy Control** | `GET`/`POST` | [`/fluxgate/strategy`](https://fluxgate-bvjt.onrender.com/fluxgate/strategy) | View or update active load balancing strategy dynamically |
| **Manual Probe** | `POST` | `/fluxgate/health-check` | Triggers an immediate health probe across all registered backends |
| **Dynamic Backends** | `POST`/`DELETE` | `/fluxgate/backends` | Add or remove backend instances at runtime |
| **Reverse Proxy Route** | `ANY` | [`/hello`](https://fluxgate-bvjt.onrender.com/hello) | Transparently balanced & rate-limited reverse proxy route returning upstream identity |

---

## 🏗️ Architecture

```
                                                 ┌──────────────────────────────┐
                                                 │ Backend 1 (demo-backend-1)   │
                                                 └──────────────────────────────┘
                                                                ▲
                                                                │  [Round Robin /
Clients  ────────▶ [ FluxGate:8080 ] ───────────────────────────┼── Least Connections /
 (HTTP)               Reverse Proxy & Load Balancer             │   Random]
                      ├── Live Web Dashboard (/)                ▼
                      ├── Management API (/fluxgate/*)   ┌──────────────────────────────┐
                      └── Embedded Demo Cluster Workers  │ Backend 2 (demo-backend-2)   │
                                                         └──────────────────────────────┘
                                                                │
                                                                ▼
                                                 ┌──────────────────────────────┐
                                                 │ Backend 3 (demo-backend-3)   │
                                                 └──────────────────────────────┘
```

1. **Client Ingress**: FluxGate listens on port `8080` (or dynamic `${PORT}` on cloud platforms like Render).
2. **Interactive UI**: Visiting `/` in a browser renders the live dark-mode dashboard with cluster cards, instant strategy switcher, and traffic simulator.
3. **Multi-Strategy Routing**: Pluggable strategies orchestrated by `LoadBalancingStrategyManager`:
   - 🔄 **Round Robin**: Thread-safe sequential rotation across healthy instances.
   - ⚖️ **Least Connections**: Dispatches to the node with fewest active in-flight requests.
   - 🎲 **Random**: Uniform stochastic distribution across healthy nodes.
4. **Transparent Proxying**: Forwards the exact HTTP method, path, query parameters, headers, and request bodies, injecting `X-Forwarded-For`, `X-Proxy-By: FluxGate`, and `X-Upstream-Backend`.
5. **Fault-Tolerant Health Monitoring**: Periodically tests `/health` on all registered backends every 10 seconds; isolates failing nodes automatically and recovers them when they come back up.
6. **Zero-Config Embedded Cluster**: Includes lightweight embedded backend workers using Java 21's virtual-thread HTTP server, making FluxGate immediately functional with 3 healthy backends out-of-the-box upon deployment.

---

## ⚡ Quick Start (Local)

### 1. Run with Maven (Standalone with Embedded Backends)

```bash
# Clone the repository
git clone https://github.com/SohamSood/FLuxGate.git
cd FLuxGate

# Run all tests (17 passing unit & integration tests)
./mvnw clean test

# Run application locally on port 8080
./mvnw spring-boot:run
```

Open your browser at: **[http://localhost:8080](http://localhost:8080)**

---

### 2. Run with Docker Compose (Full Multi-Container Stack)

```bash
docker compose up --build
```

This starts:
- `fluxgate`: Reverse proxy listening on `http://localhost:8080`
- `backend1`: Node.js demo backend on `http://localhost:8081` (`backend-1`)
- `backend2`: Node.js demo backend on `http://localhost:8082` (`backend-2`)
- `backend3`: Node.js demo backend on `http://localhost:8083` (`backend-3`)

---

## 🧪 Testing Load Balancing

### Single Request Test
```bash
curl -i https://fluxgate-bvjt.onrender.com/hello
```
Response headers will include:
```http
HTTP/1.1 200 OK
X-Proxy-By: FluxGate
X-Upstream-Backend: http://localhost:8081
X-Proxy-Latency: 4ms
```

### 10 Consecutive Requests (Verifying Distribution)
```bash
for i in {1..10}; do curl -s https://fluxgate-bvjt.onrender.com/hello | grep "server"; done
```

---

## ⚙️ Configuration Properties

| Property | Default | Environment Variable | Description |
| :--- | :--- | :--- | :--- |
| `server.port` | `8080` | `PORT` | Listening port for FluxGate |
| `fluxgate.backends` | `http://localhost:8081,...` | `FLUXGATE_BACKENDS` | Comma-separated list of target backend URLs |
| `fluxgate.demo-backends.enabled` | `true` | `FLUXGATE_DEMO_BACKENDS_ENABLED` | Auto-start embedded workers for single-container cloud hosting |
| `fluxgate.health-check.interval-ms` | `10000` | `FLUXGATE_HEALTH_INTERVAL` | Health check probe frequency (ms) |
| `fluxgate.health-check.timeout-ms` | `2000` | `FLUXGATE_HEALTH_TIMEOUT` | Timeout per probe (ms) |
| `fluxgate.proxy.connect-timeout-ms` | `3000` | - | Timeout when connecting to upstream backends |
| `fluxgate.proxy.read-timeout-ms` | `10000` | - | Upstream response read timeout |

---

## ☁️ Deployment on Render

FluxGate is deployed as a Docker Web Service on Render:

1. **Create Web Service** on [Render](https://render.com).
2. Connect repository `https://github.com/SohamSood/FLuxGate`.
3. Set **Runtime** to `Docker`.
4. Deploy! FluxGate will detect Render's `$PORT`, boot up the embedded backend workers, and serve both the **Web Dashboard** and **Reverse Proxy** on your live `.onrender.com` URL.

To connect external backends instead of embedded workers, set:
```text
FLUXGATE_BACKENDS=https://api1.yourdomain.com,https://api2.yourdomain.com
```

---

## 🛠️ Project Structure

```
FLuxGate/
├── demo-backend/              # Independent Node.js demo backend microservice
│   ├── Dockerfile
│   ├── package.json
│   └── server.js
├── src/
│   ├── main/
│   │   ├── java/com/fluxgate/
│   │   │   ├── config/        # FluxGateProperties, AppConfig
│   │   │   ├── controller/    # DashboardController, ReverseProxyController, FluxGateManagementController
│   │   │   ├── model/         # BackendServer, ProxyResponse, ErrorResponse
│   │   │   ├── service/       # BackendRegistry, HealthCheckService, ProxyService, EmbeddedBackendService
│   │   │   └── strategy/      # LoadBalancingStrategy, RoundRobin, LeastConnections, Random, StrategyManager
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       ├── java/com/fluxgate/ # Comprehensive unit & integration tests
│       └── resources/
├── docker-compose.yml         # Multi-service local composition
├── Dockerfile                 # Multi-stage Java 21 build
└── pom.xml                    # Maven dependencies
```

---

## 📜 License

Licensed under the [MIT License](LICENSE).
