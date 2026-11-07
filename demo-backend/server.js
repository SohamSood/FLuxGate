const http = require('http');

const PORT = parseInt(process.env.PORT, 10) || 8081;
const SERVER_NAME = process.env.SERVER_NAME || `backend-${PORT}`;

const server = http.createServer((req, res) => {
    const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    const pathname = parsedUrl.pathname;
    const searchParams = Object.fromEntries(parsedUrl.searchParams.entries());

    // Collect request body if present
    const chunks = [];
    req.on('data', chunk => chunks.push(chunk));
    req.on('end', () => {
        const rawBody = Buffer.concat(chunks).toString('utf-8');
        let parsedJson = null;
        if (rawBody && req.headers['content-type'] && req.headers['content-type'].includes('application/json')) {
            try {
                parsedJson = JSON.parse(rawBody);
            } catch (e) {
                // Keep rawBody
            }
        }

        // Set response headers
        res.setHeader('Content-Type', 'application/json');
        res.setHeader('X-Served-By', SERVER_NAME);
        res.setHeader('X-Backend-Port', PORT.toString());

        // 1. Health check endpoint: GET /health
        if (pathname === '/health') {
            res.writeHead(200);
            return res.end(JSON.stringify({
                status: 'UP',
                backend: SERVER_NAME,
                port: PORT,
                uptimeSeconds: Math.floor(process.uptime()),
                timestamp: new Date().toISOString()
            }));
        }

        // 2. Hello endpoint: GET /hello (or root /)
        if (pathname === '/hello' || pathname === '/') {
            res.writeHead(200);
            return res.end(JSON.stringify({
                message: `Hello from ${SERVER_NAME}!`,
                backend: SERVER_NAME,
                port: PORT,
                path: pathname,
                query: searchParams,
                timestamp: new Date().toISOString()
            }));
        }

        // 3. General endpoint echo
        res.writeHead(200);
        res.end(JSON.stringify({
            message: `Echo from ${SERVER_NAME}`,
            backend: SERVER_NAME,
            port: PORT,
            method: req.method,
            path: pathname,
            query: searchParams,
            headers: req.headers,
            body: parsedJson || (rawBody || null),
            timestamp: new Date().toISOString()
        }));
    });
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`[FluxGate Backend Instance] ${SERVER_NAME} listening on 0.0.0.0:${PORT}`);
});
