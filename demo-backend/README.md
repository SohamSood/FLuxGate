# 🚀 FluxGate Demo Backend

A lightweight, zero-dependency Node.js HTTP service designed to test **FluxGate Reverse Proxy & Load Balancer**.

## Features

- **`GET /health`**: Health check probe returning `{"status": "UP", "backend": "backend-1", "uptimeSeconds": 42}`.
- **`GET /hello`**: Greeting endpoint returning the backend's identity and port.
- **Echo for all paths**: Accepts any HTTP method (`GET`, `POST`, `PUT`, `DELETE`) and returns received headers, queries, and bodies.
- **Identifiable response header**: Returns `X-Served-By: <SERVER_NAME>`.

## Environment Variables

| Variable | Default | Description |
| :--- | :--- | :--- |
| `PORT` | `8081` | Port to listen on (automatically injected by Render) |
| `SERVER_NAME` | `backend-<PORT>` | Unique name identifying this instance (e.g., `backend-1`) |

## Running Locally

```bash
# Run instance 1 on port 8081
PORT=8081 SERVER_NAME=backend-1 node server.js

# Run instance 2 on port 8082
PORT=8082 SERVER_NAME=backend-2 node server.js

# Run instance 3 on port 8083
PORT=8083 SERVER_NAME=backend-3 node server.js
```

