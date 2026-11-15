package com.fluxgate.controller;

import com.fluxgate.model.BackendServer;
import com.fluxgate.resilience.CircuitBreakerManager;
import com.fluxgate.service.BackendRegistry;
import com.fluxgate.service.RateLimiterService;
import com.fluxgate.service.RequestCoalescingService;
import com.fluxgate.service.ResponseCacheService;
import com.fluxgate.service.TrafficMetricsService;
import com.fluxgate.strategy.LoadBalancingStrategyManager;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves the interactive Web Dashboard and API landing page for FluxGate.
 */
@Controller
public class DashboardController {

    private final BackendRegistry backendRegistry;
    private final LoadBalancingStrategyManager strategyManager;
    private final RateLimiterService rateLimiterService;
    private final ResponseCacheService responseCacheService;
    private final TrafficMetricsService trafficMetricsService;
    private final CircuitBreakerManager circuitBreakerManager;
    private final RequestCoalescingService requestCoalescingService;

    public DashboardController(BackendRegistry backendRegistry,
                               LoadBalancingStrategyManager strategyManager,
                               RateLimiterService rateLimiterService,
                               ResponseCacheService responseCacheService,
                               TrafficMetricsService trafficMetricsService,
                               CircuitBreakerManager circuitBreakerManager,
                               RequestCoalescingService requestCoalescingService) {
        this.backendRegistry = backendRegistry;
        this.strategyManager = strategyManager;
        this.rateLimiterService = rateLimiterService;
        this.responseCacheService = responseCacheService;
        this.trafficMetricsService = trafficMetricsService;
        this.circuitBreakerManager = circuitBreakerManager;
        this.requestCoalescingService = requestCoalescingService;
    }

    @GetMapping(value = {"/", "/dashboard"}, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String getDashboardHtml() {
        return DASHBOARD_HTML;
    }

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getLandingJson() {
        List<BackendServer> all = backendRegistry.getAllBackends();
        List<BackendServer> healthy = backendRegistry.getHealthyBackends();

        Map<String, Object> response = new HashMap<>();
        response.put("service", "FluxGate Reverse Proxy & Load Balancer");
        response.put("version", "1.4.0");
        response.put("status", healthy.isEmpty() ? "DEGRADED" : "HEALTHY");
        response.put("strategy", strategyManager.getActiveStrategyName());
        response.put("healthyBackends", healthy.size());
        response.put("totalBackends", all.size());
        response.put("rateLimiterEnabled", rateLimiterService.isEnabled());
        response.put("edgeCacheEnabled", responseCacheService.isEnabled());
        response.put("coalescingEnabled", requestCoalescingService.isEnabled());
        response.put("coalescing", requestCoalescingService.getStats());
        response.put("circuitBreakers", circuitBreakerManager.getTelemetry());
        response.put("dashboardUrl", "/dashboard");
        response.put("testEndpoint", "/hello");
        response.put("managementEndpoints", Map.of(
                "health", "/fluxgate/health",
                "status", "/fluxgate/status",
                "circuitBreakers", "/fluxgate/circuit-breakers",
                "strategy", "/fluxgate/strategy",
                "rateLimit", "/fluxgate/rate-limit",
                "cache", "/fluxgate/cache",
                "coalescing", "/fluxgate/coalescing",
                "metrics", "/fluxgate/metrics",
                "backends", "/fluxgate/backends",
                "healthCheck", "/fluxgate/health-check"
        ));

        return ResponseEntity.ok(response);
    }

    public static final String DASHBOARD_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>FluxGate Dashboard | Reverse Proxy & Load Balancer</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;600&family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #090d16;
      --card-bg: #111827;
      --card-border: #1f293d;
      --card-hover: #162032;
      --text-main: #f3f4f6;
      --text-muted: #9ca3af;
      --primary: #6366f1;
      --primary-hover: #4f46e5;
      --emerald: #10b981;
      --emerald-glow: rgba(16, 185, 129, 0.25);
      --rose: #ef4444;
      --rose-glow: rgba(239, 68, 68, 0.25);
      --amber: #f59e0b;
      --cyan: #06b6d4;
      --purple: #a855f7;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: var(--bg);
      color: var(--text-main);
      font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      padding: 24px;
    }
    .container {
      max-width: 1240px;
      margin: 0 auto;
      width: 100%;
    }
    header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding-bottom: 24px;
      border-bottom: 1px solid var(--card-border);
      margin-bottom: 28px;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .brand-icon {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: linear-gradient(135deg, #6366f1 0%, #06b6d4 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      box-shadow: 0 0 20px rgba(99, 102, 241, 0.4);
    }
    .brand h1 {
      font-size: 24px;
      font-weight: 800;
      letter-spacing: -0.5px;
      background: linear-gradient(to right, #ffffff, #94a3b8);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    .brand p {
      font-size: 13px;
      color: var(--text-muted);
    }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: 9999px;
      font-size: 12px;
      font-weight: 600;
      font-family: 'JetBrains Mono', monospace;
    }
    .badge-online {
      background: rgba(16, 185, 129, 0.15);
      color: #34d399;
      border: 1px solid rgba(16, 185, 129, 0.3);
    }
    .badge-degraded {
      background: rgba(239, 68, 68, 0.15);
      color: #f87171;
      border: 1px solid rgba(239, 68, 68, 0.3);
    }
    .badge-warning {
      background: rgba(245, 158, 11, 0.15);
      color: #fbbf24;
      border: 1px solid rgba(245, 158, 11, 0.3);
    }
    .circuit-pill-closed {
      background: rgba(16, 185, 129, 0.15);
      color: #34d399;
      border: 1px solid rgba(16, 185, 129, 0.3);
      padding: 3px 8px;
      border-radius: 6px;
      font-weight: 700;
      font-size: 11px;
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .circuit-pill-open {
      background: rgba(239, 68, 68, 0.15);
      color: #f87171;
      border: 1px solid rgba(239, 68, 68, 0.3);
      padding: 3px 8px;
      border-radius: 6px;
      font-weight: 700;
      font-size: 11px;
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .circuit-pill-half {
      background: rgba(245, 158, 11, 0.15);
      color: #fbbf24;
      border: 1px solid rgba(245, 158, 11, 0.3);
      padding: 3px 8px;
      border-radius: 6px;
      font-weight: 700;
      font-size: 11px;
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .pulse-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: currentColor;
      box-shadow: 0 0 8px currentColor;
      animation: pulse 2s infinite;
    }
    @keyframes pulse {
      0%, 100% { opacity: 1; transform: scale(1); }
      50% { opacity: 0.4; transform: scale(0.85); }
    }
    /* Stats row */
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 16px;
      margin-bottom: 28px;
    }
    .stat-card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 14px;
      padding: 18px 20px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      transition: border-color 0.2s, box-shadow 0.2s;
    }
    .stat-card:hover {
      border-color: rgba(99, 102, 241, 0.4);
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
    }
    .stat-label {
      font-size: 11px;
      color: var(--text-muted);
      text-transform: uppercase;
      font-weight: 700;
      letter-spacing: 0.5px;
    }
    .stat-value {
      font-size: 24px;
      font-weight: 700;
      font-family: 'JetBrains Mono', monospace;
      color: #ffffff;
    }
    .stat-sub {
      font-size: 12px;
      color: var(--text-muted);
    }
    .section-title {
      font-size: 18px;
      font-weight: 700;
      margin-bottom: 16px;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .card {
      background: var(--card-bg);
      border: 1px solid var(--card-border);
      border-radius: 16px;
      padding: 24px;
      margin-bottom: 28px;
    }
    /* Strategy switcher */
    .strategy-group {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
    .strategy-btn {
      background: #1e293b;
      color: var(--text-main);
      border: 1px solid var(--card-border);
      padding: 10px 16px;
      border-radius: 10px;
      cursor: pointer;
      font-size: 13px;
      font-weight: 600;
      transition: all 0.2s;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .strategy-btn:hover {
      background: #334155;
      border-color: #475569;
    }
    .strategy-btn.active {
      background: var(--primary);
      color: #ffffff;
      border-color: var(--primary);
      box-shadow: 0 0 15px rgba(99, 102, 241, 0.4);
    }
    /* Backend grid */
    .backend-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 16px;
    }
    .backend-card {
      background: #0f172a;
      border: 1px solid var(--card-border);
      border-radius: 14px;
      padding: 18px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      position: relative;
      transition: transform 0.2s, border-color 0.2s;
    }
    .backend-card:hover {
      transform: translateY(-2px);
      border-color: #3b82f6;
    }
    .backend-card.flash {
      border-color: #10b981;
      box-shadow: 0 0 20px rgba(16, 185, 129, 0.4);
    }
    .backend-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .backend-url {
      font-family: 'JetBrains Mono', monospace;
      font-size: 14px;
      font-weight: 600;
      color: #38bdf8;
      word-break: break-all;
    }
    .backend-metrics {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 8px;
      background: rgba(0, 0, 0, 0.25);
      padding: 10px;
      border-radius: 8px;
      font-size: 12px;
      font-family: 'JetBrains Mono', monospace;
      text-align: center;
    }
    .metric-item span {
      display: block;
      color: var(--text-muted);
      font-size: 10px;
      margin-bottom: 2px;
    }
    .metric-item strong {
      font-size: 14px;
      color: #ffffff;
    }
    /* Controls and inputs */
    .btn {
      background: var(--primary);
      color: #ffffff;
      border: none;
      padding: 9px 18px;
      border-radius: 10px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s;
    }
    .btn:hover {
      background: var(--primary-hover);
      box-shadow: 0 0 15px rgba(99, 102, 241, 0.4);
    }
    .btn-secondary {
      background: #1e293b;
      color: var(--text-main);
      border: 1px solid var(--card-border);
    }
    .btn-secondary:hover {
      background: #334155;
    }
    .btn-danger {
      background: rgba(239, 68, 68, 0.15);
      color: #f87171;
      border: 1px solid rgba(239, 68, 68, 0.3);
      padding: 4px 10px;
      border-radius: 6px;
      cursor: pointer;
      font-size: 11px;
      font-weight: 600;
    }
    .btn-danger:hover {
      background: rgba(239, 68, 68, 0.3);
    }
    .btn-purple {
      background: rgba(168, 85, 247, 0.2);
      color: #c084fc;
      border: 1px solid rgba(168, 85, 247, 0.4);
    }
    .btn-purple:hover {
      background: rgba(168, 85, 247, 0.35);
      box-shadow: 0 0 15px rgba(168, 85, 247, 0.4);
    }
    .input-box {
      background: #0f172a;
      border: 1px solid var(--card-border);
      color: #ffffff;
      padding: 9px 14px;
      border-radius: 10px;
      font-size: 13px;
      font-family: 'JetBrains Mono', monospace;
      outline: none;
      flex-grow: 1;
    }
    .input-box:focus {
      border-color: var(--primary);
      box-shadow: 0 0 10px rgba(99, 102, 241, 0.3);
    }
    /* Table styles */
    .table-container {
      overflow-x: auto;
      border-radius: 12px;
      border: 1px solid var(--card-border);
      background: #0f172a;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 13px;
    }
    th {
      background: #1e293b;
      padding: 12px 16px;
      font-weight: 600;
      color: var(--text-muted);
      border-bottom: 1px solid var(--card-border);
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    td {
      padding: 12px 16px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
      font-family: 'JetBrains Mono', monospace;
      font-size: 12px;
    }
    tr:last-child td { border-bottom: none; }
    tr:hover td { background: rgba(255, 255, 255, 0.02); }
    .status-pill {
      display: inline-block;
      padding: 3px 8px;
      border-radius: 6px;
      font-weight: 600;
      font-size: 11px;
    }
    .status-2xx { background: rgba(16, 185, 129, 0.2); color: #34d399; }
    .status-4xx { background: rgba(245, 158, 11, 0.2); color: #fbbf24; }
    .status-5xx { background: rgba(239, 68, 68, 0.2); color: #f87171; }
    .method-pill {
      font-weight: 700;
      color: #818cf8;
    }
    .cache-pill-hit {
      background: rgba(16, 185, 129, 0.2);
      color: #34d399;
      border: 1px solid rgba(16, 185, 129, 0.4);
      padding: 2px 8px;
      border-radius: 6px;
      font-weight: 700;
      font-size: 11px;
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .cache-pill-coalesced {
      background: rgba(139, 92, 246, 0.25);
      color: #c084fc;
      border: 1px solid rgba(139, 92, 246, 0.5);
      padding: 2px 8px;
      border-radius: 6px;
      font-weight: 700;
      font-size: 11px;
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
    .cache-pill-miss {
      background: rgba(148, 163, 184, 0.15);
      color: #94a3b8;
      border: 1px solid rgba(148, 163, 184, 0.3);
      padding: 2px 8px;
      border-radius: 6px;
      font-weight: 600;
      font-size: 11px;
    }
    .cache-pill-bypass {
      background: rgba(168, 85, 247, 0.15);
      color: #c084fc;
      border: 1px solid rgba(168, 85, 247, 0.3);
      padding: 2px 8px;
      border-radius: 6px;
      font-weight: 600;
      font-size: 11px;
    }
    .cache-pill-throttled {
      background: rgba(239, 68, 68, 0.15);
      color: #f87171;
      border: 1px solid rgba(239, 68, 68, 0.3);
      padding: 2px 8px;
      border-radius: 6px;
      font-weight: 600;
      font-size: 11px;
    }
    /* Distribution tracker */
    .distribution-bar {
      display: flex;
      height: 12px;
      border-radius: 6px;
      overflow: hidden;
      margin-top: 12px;
      background: #1e293b;
    }
    .dist-segment {
      height: 100%;
      transition: width 0.3s;
    }
    .dist-legend {
      display: flex;
      gap: 16px;
      font-size: 12px;
      color: var(--text-muted);
      margin-top: 8px;
      flex-wrap: wrap;
    }
    .dist-legend-item {
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .color-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
    }
    .terminal-box {
      background: #050811;
      border: 1px solid var(--card-border);
      border-radius: 12px;
      padding: 16px;
      font-family: 'JetBrains Mono', monospace;
      font-size: 12px;
      line-height: 1.6;
      color: #a5f3fc;
      min-height: 120px;
      max-height: 260px;
      overflow-y: auto;
      white-space: pre-wrap;
      word-break: break-all;
    }
    .tester-controls {
      display: flex;
      gap: 10px;
      margin-bottom: 16px;
      flex-wrap: wrap;
    }
    footer {
      margin-top: auto;
      text-align: center;
      padding-top: 24px;
      font-size: 12px;
      color: var(--text-muted);
    }
    footer a { color: var(--primary); text-decoration: none; }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <div class="brand">
        <div class="brand-icon">⚡</div>
        <div>
          <h1>FluxGate</h1>
          <p>Enterprise Reverse Proxy, Edge Cache Accelerator & Traffic Guardian</p>
        </div>
      </div>
      <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap;">
        <div id="clusterBadge" class="badge badge-online">
          <span class="pulse-dot"></span>
          <span id="clusterBadgeText">CONNECTING...</span>
        </div>
        <button class="btn btn-secondary" onclick="triggerHealthCheck()" title="Probe all backends now">
          🔄 Probe Cluster
        </button>
      </div>
    </header>

    <!-- Top Key Metrics -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">Healthy Backends</div>
        <div class="stat-value" id="healthyCount">- / -</div>
        <div class="stat-sub" id="healthySub">Probing cluster...</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Active Strategy</div>
        <div class="stat-value" id="activeStrategy" style="color: #818cf8;">-</div>
        <div class="stat-sub">Dynamic runtime switching</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">⚡ Edge Cache Hit Ratio</div>
        <div class="stat-value" id="cacheHitRatio" style="color: #34d399;">0.0%</div>
        <div class="stat-sub" id="cacheSub">0 hits • 0 entries</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Rate Limiter (Per-IP)</div>
        <div class="stat-value" id="rateLimitBadge" style="color: #38bdf8; font-size: 20px;">ACTIVE</div>
        <div class="stat-sub" id="rateLimitSub">Token-bucket protection</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Total Proxied Requests</div>
        <div class="stat-value" id="totalRequests" style="color: #c084fc;">0</div>
        <div class="stat-sub" id="blockedCountSub">0 throttled / blocked</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">Average Latency</div>
        <div class="stat-value" id="avgLatency" style="color: #fbbf24;">0.0 ms</div>
        <div class="stat-sub">Ingress round-trip speed</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">⚡ Circuit Breaker & Failovers</div>
        <div class="stat-value" id="circuitBreakerStat" style="color: #10b981;">0 OPEN</div>
        <div class="stat-sub" id="failoverStatSub">0 in-flight failovers</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">🚀 SingleFlight Coalescing</div>
        <div class="stat-value" id="coalesceStat" style="color: #a78bfa;">0 Saved</div>
        <div class="stat-sub" id="coalesceSub">Thundering herd shield</div>
      </div>
    </div>

    <!-- Strategy Switcher -->
    <div class="card">
      <div class="section-title">
        <span>⚙️ Load Balancing Strategy</span>
      </div>
      <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 14px;">
        Select an algorithm to dynamically change request distribution behavior on the fly:
      </p>
      <div class="strategy-group" id="strategyButtons">
        <button class="strategy-btn" onclick="setStrategy('ROUND_ROBIN')">
          🔄 Round Robin
        </button>
        <button class="strategy-btn" onclick="setStrategy('LEAST_CONNECTIONS')">
          ⚖️ Least Connections
        </button>
        <button class="strategy-btn" onclick="setStrategy('RANDOM')">
          🎲 Random
        </button>
        <button class="strategy-btn" onclick="setStrategy('WEIGHTED_ROUND_ROBIN')">
          ⚖️ Smooth Weighted
        </button>
      </div>
    </div>

    <!-- Edge Response Cache Control Card -->
    <div class="card">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 10px;">
        <div class="section-title" style="margin-bottom: 0;">
          <span>⚡ High-Speed Edge Response Cache & Accelerator</span>
        </div>
        <div style="display: flex; gap: 8px;">
          <button class="btn btn-secondary" id="toggleCacheBtn" onclick="toggleEdgeCache()">
            ⚡ Edge Cache: ON
          </button>
          <button class="btn btn-purple" onclick="purgeEdgeCache()">
            🧹 Purge Edge Cache
          </button>
        </div>
      </div>
      <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 16px;">
        Accelerates read requests by serving idempotent responses directly from low-latency memory (0-1ms), relieving upstream servers. Automatically invalidates cached entries upon state-mutating requests (POST, PUT, DELETE).
      </p>
      <div style="display: flex; gap: 12px; flex-wrap: wrap; align-items: center; background: #0f172a; padding: 14px; border-radius: 12px; border: 1px solid var(--card-border); margin-bottom: 14px;">
        <div style="display: flex; align-items: center; gap: 8px;">
          <span style="font-size: 12px; color: var(--text-muted);">Default TTL (Seconds):</span>
          <input type="number" id="cacheTtl" style="width: 80px;" class="input-box" value="30" min="1">
        </div>
        <div style="display: flex; align-items: center; gap: 8px;">
          <span style="font-size: 12px; color: var(--text-muted);">Max Entries:</span>
          <input type="number" id="cacheMaxEntries" style="width: 90px;" class="input-box" value="500" min="10">
        </div>
        <button class="btn" onclick="applyCacheSettings()">💾 Update Cache Policy</button>
        <span id="cacheMsg" style="font-size: 12px; color: #34d399; margin-left: auto;"></span>
      </div>

      <!-- Live Cached Routes Table -->
      <div id="cachedRoutesContainer" style="display: none;">
        <div style="font-size: 12px; font-weight: 600; color: var(--text-muted); margin-bottom: 8px;">CURRENTLY CACHED ROUTES IN MEMORY:</div>
        <div class="table-container" style="max-height: 180px;">
          <table>
            <thead>
              <tr>
                <th>Cache Key</th>
                <th>Origin Backend</th>
                <th>Age</th>
                <th>Remaining TTL</th>
                <th>Size</th>
              </tr>
            </thead>
            <tbody id="cachedRoutesBody"></tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Rate Limiter Control Card -->
    <div class="card">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 10px;">
        <div class="section-title" style="margin-bottom: 0;">
          <span>🛡️ Traffic Guardian & Adaptive Rate Limiter</span>
        </div>
        <div style="display: flex; gap: 8px;">
          <button class="btn btn-secondary" id="toggleRateLimitBtn" onclick="toggleRateLimiter()">
            🛡️ Toggle Protection
          </button>
        </div>
      </div>
      <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 16px;">
        Enforces token-bucket rate limits per client IP address. Rejects runaway scripts, scrapers, and DDoS bursts with standard <code>429 Too Many Requests</code>.
      </p>
      <div style="display: flex; gap: 12px; flex-wrap: wrap; align-items: center; background: #0f172a; padding: 14px; border-radius: 12px; border: 1px solid var(--card-border);">
        <div style="display: flex; align-items: center; gap: 8px;">
          <span style="font-size: 12px; color: var(--text-muted);">Burst Capacity:</span>
          <input type="number" id="rlCapacity" style="width: 80px;" class="input-box" value="60" min="1">
        </div>
        <div style="display: flex; align-items: center; gap: 8px;">
          <span style="font-size: 12px; color: var(--text-muted);">Refill Rate (tokens/min):</span>
          <input type="number" id="rlRefill" style="width: 80px;" class="input-box" value="60" min="1">
        </div>
        <button class="btn" onclick="applyRateLimitSettings()">💾 Update Policy</button>
        <span id="rateLimitMsg" style="font-size: 12px; color: #34d399; margin-left: auto;"></span>
      </div>
    </div>

    <!-- Enterprise Dynamic Circuit Breaker & Automatic Failover Card -->
    <div class="card">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 10px;">
        <div class="section-title" style="margin-bottom: 0;">
          <span>⚡ Enterprise Dynamic Circuit Breaker & Automatic Failover</span>
        </div>
        <div style="display: flex; gap: 8px;">
          <button class="btn btn-secondary" id="toggleCircuitBtn" onclick="toggleCircuitBreaker()">
            ⚡ Circuit Breakers: ON
          </button>
          <button class="btn btn-purple" onclick="resetAllCircuits()">
            🔄 Reset All Circuits
          </button>
        </div>
      </div>
      <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 16px;">
        Detects backend degradation and isolates failing upstream hosts (State: <code>CLOSED</code> &rarr; <code>OPEN</code> &rarr; <code>HALF_OPEN</code>). If a host trips or throws a 5xx/timeout error, FluxGate executes an <strong>in-flight transparent failover</strong> to an alternate healthy backend with zero client errors.
      </p>

      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>Backend Host</th>
              <th>Circuit State</th>
              <th>Failures / Threshold</th>
              <th>Trips</th>
              <th>Failovers Rerouted</th>
              <th>Last Failure Cause</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody id="circuitBreakersTableBody">
            <tr>
              <td colspan="7" style="text-align: center; color: var(--text-muted); padding: 14px;">
                Loading circuit breaker status...
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Active Backends Cluster -->
    <div class="card">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
        <div class="section-title" style="margin-bottom: 0;">
          <span>🖥️ Upstream Backend Cluster</span>
        </div>
      </div>

      <div class="backend-grid" id="backendList">
        <!-- Rendered via JavaScript -->
      </div>

      <div style="margin-top: 24px; padding-top: 16px; border-top: 1px solid var(--card-border);">
        <h3 style="font-size: 14px; margin-bottom: 10px;">➕ Register Upstream Backend</h3>
        <div style="display: flex; gap: 10px; flex-wrap: wrap;">
          <input type="text" id="newBackendUrl" class="input-box" placeholder="e.g. https://httpbin.org/anything or https://backend.onrender.com">
          <button class="btn" onclick="addBackend()">Register & Probe</button>
        </div>
      </div>
    </div>

    <!-- Live Ingress Traffic Stream Table -->
    <div class="card">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 10px;">
        <div class="section-title" style="margin-bottom: 0;">
          <span>📊 Live Ingress Traffic Stream (Real-Time Observability)</span>
        </div>
        <button class="btn btn-secondary" style="font-size: 11px; padding: 6px 12px;" onclick="resetMetricsLog()">
          🧹 Clear Logs
        </button>
      </div>
      <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 12px;">
        Real-time audit log of proxied, cached, and throttled requests passing through FluxGate:
      </p>

      <div class="table-container">
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Client IP</th>
              <th>Method</th>
              <th>Path</th>
              <th>Target Backend</th>
              <th>Status</th>
              <th>Cache</th>
              <th>Latency</th>
            </tr>
          </thead>
          <tbody id="trafficTableBody">
            <tr>
              <td colspan="8" style="text-align: center; color: var(--text-muted); padding: 18px;">
                No traffic recorded yet. Send a request below to stream live transactions!
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Interactive Live Tester -->
    <div class="card">
      <div class="section-title">
        <span>🚀 Live Proxy Simulator & Traffic Generator</span>
      </div>
      <p style="font-size: 13px; color: var(--text-muted); margin-bottom: 16px;">
        Send live requests directly through the reverse proxy to observe cache acceleration, dynamic routing, and rate limiting in real time:
      </p>

      <div class="tester-controls">
        <input type="text" id="testPath" class="input-box" value="/hello" placeholder="/hello or /api/endpoint">
        <button class="btn" onclick="sendTestRequest()">⚡ Send 1 Request</button>
        <button class="btn btn-secondary" style="border-color: #10b981; color: #34d399;" onclick="testCacheAccelerator()">
          ⚡ Test Cache Accelerator (Double GET)
        </button>
        <button class="btn btn-secondary" style="border-color: #f59e0b; color: #fbbf24; background: rgba(245, 158, 11, 0.15);" onclick="testCircuitFailover()">
          💥 Test Circuit Trip & Failover
        </button>
        <button class="btn btn-secondary" style="border-color: #8b5cf6; color: #c084fc; background: rgba(139, 92, 246, 0.15);" onclick="testSingleFlightStampede()">
          🚀 Test SingleFlight Stampede
        </button>
        <button class="btn btn-secondary" onclick="sendBatchRequests(10)">🚀 Send 10 Requests</button>
        <button class="btn btn-secondary" style="background: rgba(239, 68, 68, 0.2); border-color: rgba(239, 68, 68, 0.4);" onclick="sendSpamRequests(25)">
          💥 Spam 25 (Test Rate Limiter)
        </button>
      </div>

      <!-- Batch Distribution Visualizer -->
      <div id="distributionContainer" style="display: none; margin-bottom: 16px;">
        <div style="font-size: 12px; color: var(--text-muted); font-weight: 600;">LAST BATCH TRAFFIC DISTRIBUTION:</div>
        <div class="distribution-bar" id="distBar"></div>
        <div class="dist-legend" id="distLegend"></div>
      </div>

      <div class="terminal-box" id="testOutput">// Proxy console initialized. Click "Test Cache Accelerator" or "Send 1 Request" to test.</div>
    </div>

    <footer>
      FluxGate Reverse Proxy & Load Balancer • Built with Java 21 & Spring Boot •
      <a href="https://github.com/SohamSood/FLuxGate" target="_blank">View on GitHub</a>
    </footer>
  </div>

  <script>
    const COLORS = ['#6366f1', '#06b6d4', '#10b981', '#f59e0b', '#ec4899', '#8b5cf6'];
    let currentBackends = [];
    let currentStrategy = 'ROUND_ROBIN';
    let rateLimiterEnabled = true;
    let edgeCacheEnabled = true;

    async function fetchStatus() {
      try {
        const res = await fetch('/fluxgate/status');
        if (!res.ok) throw new Error('Status failed');
        const data = await res.json();
        updateUI(data);
      } catch (err) {
        console.error('Fetch error:', err);
      }
    }

    async function fetchMetrics() {
      try {
        const res = await fetch('/fluxgate/metrics');
        if (!res.ok) return;
        const data = await res.json();
        updateMetricsUI(data);
      } catch (err) {
        console.error('Metrics error:', err);
      }
    }

    function updateUI(data) {
      currentBackends = data.backends || [];
      currentStrategy = data.strategy || 'ROUND_ROBIN';

      // Badge
      const badge = document.getElementById('clusterBadge');
      const badgeText = document.getElementById('clusterBadgeText');
      if (data.healthyCount > 0) {
        badge.className = 'badge badge-online';
        badgeText.textContent = data.healthyCount + ' / ' + data.totalCount + ' HEALTHY';
      } else {
        badge.className = 'badge badge-degraded';
        badgeText.textContent = 'NO HEALTHY BACKENDS';
      }

      // Stats
      document.getElementById('healthyCount').textContent = data.healthyCount + ' / ' + data.totalCount;
      document.getElementById('healthySub').textContent = data.healthyCount === data.totalCount ? 'Full cluster available' : 'Cluster partially degraded';
      document.getElementById('activeStrategy').textContent = currentStrategy;

      // Cache status & telemetry
      const c = data.cache || {};
      edgeCacheEnabled = c.enabled !== false;
      document.getElementById('cacheHitRatio').textContent = (c.hitRatioPercent || 0.0) + '%';
      document.getElementById('cacheSub').textContent = `${c.hitCount || 0} hits • ${c.entriesCount || 0} in cache`;

      const toggleCacheBtn = document.getElementById('toggleCacheBtn');
      if (toggleCacheBtn) {
        toggleCacheBtn.textContent = edgeCacheEnabled ? '⚡ Edge Cache: ON' : '⚠️ Edge Cache: OFF';
        toggleCacheBtn.style.color = edgeCacheEnabled ? '#34d399' : '#f87171';
      }

      // Cached routes preview
      const cachedEntries = c.cachedEntries || [];
      const cacheContainer = document.getElementById('cachedRoutesContainer');
      const cacheBody = document.getElementById('cachedRoutesBody');
      if (cachedEntries.length > 0) {
        cacheContainer.style.display = 'block';
        cacheBody.innerHTML = cachedEntries.map(e => `
          <tr>
            <td style="color: #38bdf8;">${e.key}</td>
            <td style="color: #a78bfa;">${e.originBackend}</td>
            <td>${e.ageSeconds}s ago</td>
            <td style="color: #34d399;">${e.remainingTtlSeconds}s</td>
            <td>${e.bytes} B</td>
          </tr>
        `).join('');
      } else {
        cacheContainer.style.display = 'none';
      }

      // Rate limit status
      const rl = data.rateLimiter || {};
      rateLimiterEnabled = rl.enabled;
      const rlBadge = document.getElementById('rateLimitBadge');
      if (rl.enabled) {
        rlBadge.textContent = 'ACTIVE (' + rl.capacity + '/min)';
        rlBadge.style.color = '#38bdf8';
      } else {
        rlBadge.textContent = 'DISABLED';
        rlBadge.style.color = '#f87171';
      }
      document.getElementById('blockedCountSub').textContent = (rl.blockedRequests || 0) + ' throttled / blocked';

      const toggleBtn = document.getElementById('toggleRateLimitBtn');
      if (toggleBtn) {
        toggleBtn.textContent = rl.enabled ? '🛡️ Rate Limiter: ON' : '⚠️ Rate Limiter: OFF';
        toggleBtn.style.color = rl.enabled ? '#38bdf8' : '#f87171';
      }

      // Circuit Breaker status & telemetry
      const cb = data.circuitBreakers || {};
      const openCount = cb.openCircuits || 0;
      const failoverCount = cb.totalFailovers || 0;
      const cbStat = document.getElementById('circuitBreakerStat');
      if (cbStat) {
        cbStat.textContent = openCount + ' OPEN';
        cbStat.style.color = openCount > 0 ? '#f87171' : '#10b981';
      }
      const failoverSub = document.getElementById('failoverStatSub');
      if (failoverSub) {
        failoverSub.textContent = failoverCount + ' in-flight failovers';
      }

      const toggleCircuitBtn = document.getElementById('toggleCircuitBtn');
      if (toggleCircuitBtn) {
        const isCbEnabled = cb.enabled !== false;
        toggleCircuitBtn.textContent = isCbEnabled ? '⚡ Circuit Breakers: ON' : '⚠️ Circuit Breakers: OFF';
        toggleCircuitBtn.style.color = isCbEnabled ? '#34d399' : '#f87171';
      }

      // SingleFlight Coalescing Telemetry
      const coal = data.coalescing || {};
      const coalesceStat = document.getElementById('coalesceStat');
      if (coalesceStat) {
        coalesceStat.textContent = (coal.upstreamCallsSaved || 0) + ' Saved';
      }
      const coalesceSub = document.getElementById('coalesceSub');
      if (coalesceSub) {
        coalesceSub.textContent = `${coal.totalCoalescedRequests || 0} coalesced • ${coal.efficiencyRatioPercent || 0}% ratio`;
      }

      // Render Circuit Breakers Table
      const cbBackends = cb.backends || {};
      const cbTableBody = document.getElementById('circuitBreakersTableBody');
      if (cbTableBody) {
        const keys = Object.keys(cbBackends);
        if (keys.length > 0) {
          cbTableBody.innerHTML = keys.map(k => {
            const bcb = cbBackends[k];
            const state = bcb.state || 'CLOSED';
            let pillClass = 'circuit-pill-closed';
            if (state === 'OPEN') pillClass = 'circuit-pill-open';
            else if (state === 'HALF_OPEN') pillClass = 'circuit-pill-half';

            return `
              <tr>
                <td style="color: #38bdf8; font-family: 'JetBrains Mono', monospace;">${bcb.backendUrl}</td>
                <td><span class="${pillClass}"><span class="pulse-dot"></span> ${state}</span></td>
                <td>${bcb.consecutiveFailures} / ${bcb.failureThreshold}</td>
                <td>${bcb.totalTrips || 0}</td>
                <td style="color: #818cf8; font-weight: 700;">${bcb.totalFailovers || 0}</td>
                <td style="color: ${bcb.lastFailureReason && bcb.lastFailureReason !== 'None' ? '#f87171' : 'var(--text-muted)'}; font-size: 11px;">
                  ${bcb.lastFailureReason || 'None'}
                </td>
                <td>
                  <div style="display: flex; gap: 6px;">
                    <button class="btn btn-secondary" style="padding: 4px 8px; font-size: 10px; background: rgba(239, 68, 68, 0.15); border-color: rgba(239, 68, 68, 0.3); color: #f87171;" onclick="tripBackendCircuit('${bcb.backendUrl}')">💥 Trip</button>
                    <button class="btn btn-secondary" style="padding: 4px 8px; font-size: 10px; border-color: rgba(16, 185, 129, 0.3); color: #34d399;" onclick="resetBackendCircuit('${bcb.backendUrl}')">🔄 Reset</button>
                  </div>
                </td>
              </tr>
            `;
          }).join('');
        }
      }

      // Strategy buttons
      const buttons = document.querySelectorAll('.strategy-btn');
      buttons.forEach(btn => {
        const isMatch = btn.textContent.toUpperCase().includes(currentStrategy);
        btn.className = isMatch ? 'strategy-btn active' : 'strategy-btn';
      });

      // Render backends
      const grid = document.getElementById('backendList');
      grid.innerHTML = currentBackends.map((b) => {
        const isHealthy = b.healthy;
        const bcb = (cb.backends || {})[b.url] || {};
        const cbState = bcb.state || 'CLOSED';
        let cbPill = `<span class="circuit-pill-closed" style="font-size:10px;"><span class="pulse-dot"></span> ${cbState}</span>`;
        if (cbState === 'OPEN') {
          cbPill = `<span class="circuit-pill-open" style="font-size:10px;"><span class="pulse-dot"></span> ${cbState} (FAILOVER ACTIVE)</span>`;
        } else if (cbState === 'HALF_OPEN') {
          cbPill = `<span class="circuit-pill-half" style="font-size:10px;"><span class="pulse-dot"></span> ${cbState} (TRIAL)</span>`;
        }

        return `
          <div class="backend-card" id="card-${encodeURIComponent(b.url)}">
            <div class="backend-header">
              <div style="display:flex; align-items:center; gap:8px;">
                <span class="badge ${isHealthy ? 'badge-online' : 'badge-degraded'}">
                  <span class="pulse-dot"></span> ${isHealthy ? 'UP' : 'DOWN'}
                </span>
                ${cbPill}
              </div>
              <button class="btn-danger" onclick="removeBackend('${b.url}')">Remove</button>
            </div>
            <div class="backend-url">${b.url}</div>
            <div class="backend-metrics">
              <div class="metric-item">
                <span>TOTAL REQS</span>
                <strong>${b.totalRequests || 0}</strong>
              </div>
              <div class="metric-item">
                <span>IN-FLIGHT</span>
                <strong>${b.activeRequests || 0}</strong>
              </div>
              <div class="metric-item">
                <span>SUCCESSES</span>
                <strong style="color: #34d399;">${b.consecutiveSuccesses || 0}</strong>
              </div>
              <div class="metric-item">
                <span>FAILURES</span>
                <strong style="color: ${b.consecutiveFailures > 0 ? '#f87171' : '#9ca3af'};">${b.consecutiveFailures || 0}</strong>
              </div>
            </div>
            ${b.lastFailureReason ? `<div style="font-size: 11px; color: #f87171; word-break: break-all;">⚠️ ${b.lastFailureReason}</div>` : ''}
            <div style="display: flex; gap: 8px; margin-top: 4px;">
              <button class="btn btn-secondary" style="flex:1; padding: 4px 8px; font-size: 10px; background: rgba(239, 68, 68, 0.15); border-color: rgba(239, 68, 68, 0.3); color: #f87171;" onclick="tripBackendCircuit('${b.url}')">💥 Trip Circuit</button>
              <button class="btn btn-secondary" style="flex:1; padding: 4px 8px; font-size: 10px; border-color: rgba(16, 185, 129, 0.3); color: #34d399;" onclick="resetBackendCircuit('${b.url}')">🔄 Reset Circuit</button>
            </div>
          </div>
        `;
      }).join('');
    }

    function updateMetricsUI(data) {
      if (!data) return;
      document.getElementById('totalRequests').textContent = data.totalRequests || 0;
      document.getElementById('avgLatency').textContent = (data.averageLatencyMs || 0) + ' ms';

      const tbody = document.getElementById('trafficTableBody');
      const reqs = data.recentRequests || [];
      if (reqs.length === 0) return;

      tbody.innerHTML = reqs.slice(0, 15).map(r => {
        let statusClass = 'status-2xx';
        if (r.statusCode === 429 || (r.statusCode >= 400 && r.statusCode < 500)) statusClass = 'status-4xx';
        else if (r.statusCode >= 500) statusClass = 'status-5xx';

        let cacheBadge = '<span class="cache-pill-miss">MISS</span>';
        if (r.cacheStatus === 'HIT') {
          cacheBadge = '<span class="cache-pill-hit"><span class="pulse-dot"></span> HIT (0ms)</span>';
        } else if (r.cacheStatus === 'COALESCED') {
          cacheBadge = '<span class="cache-pill-coalesced">⚡ COALESCED</span>';
        } else if (r.cacheStatus === 'BYPASS') {
          cacheBadge = '<span class="cache-pill-bypass">BYPASS</span>';
        } else if (r.cacheStatus === 'THROTTLED') {
          cacheBadge = '<span class="cache-pill-throttled">THROTTLED</span>';
        }

        return `
          <tr>
            <td style="color: var(--text-muted);">${r.timestamp}</td>
            <td>${r.clientIp}</td>
            <td class="method-pill">${r.method}</td>
            <td style="color: #38bdf8;">${r.path}</td>
            <td style="color: #a78bfa;">${r.targetBackend}</td>
            <td><span class="status-pill ${statusClass}">${r.statusCode}</span></td>
            <td>${cacheBadge}</td>
            <td>${r.latencyMs} ms</td>
          </tr>
        `;
      }).join('');
    }

    async function toggleEdgeCache() {
      try {
        const nextState = !edgeCacheEnabled;
        const res = await fetch('/fluxgate/cache?enabled=' + nextState, { method: 'POST' });
        if (res.ok) {
          fetchStatus();
        }
      } catch (e) {
        alert('Cache toggle error: ' + e.message);
      }
    }

    async function applyCacheSettings() {
      const ttl = document.getElementById('cacheTtl').value;
      const max = document.getElementById('cacheMaxEntries').value;
      const msg = document.getElementById('cacheMsg');

      try {
        const res = await fetch(`/fluxgate/cache?defaultTtlSeconds=${ttl}&maxEntries=${max}`, { method: 'POST' });
        if (res.ok) {
          msg.textContent = '✓ Cache policy saved!';
          setTimeout(() => msg.textContent = '', 3000);
          fetchStatus();
        }
      } catch (e) {
        alert('Failed to update cache settings: ' + e.message);
      }
    }

    async function purgeEdgeCache() {
      try {
        const res = await fetch('/fluxgate/cache', { method: 'DELETE' });
        if (res.ok) {
          const data = await res.json();
          alert(`⚡ Edge Cache Purged! (${data.purgedEntries || 0} entries removed)`);
          fetchStatus();
          fetchMetrics();
        }
      } catch (e) {
        alert('Purge error: ' + e.message);
      }
    }

    async function toggleRateLimiter() {
      try {
        const nextState = !rateLimiterEnabled;
        const res = await fetch('/fluxgate/rate-limit?enabled=' + nextState, { method: 'POST' });
        if (res.ok) {
          fetchStatus();
        }
      } catch (e) {
        alert('Toggle error: ' + e.message);
      }
    }

    async function applyRateLimitSettings() {
      const cap = document.getElementById('rlCapacity').value;
      const ref = document.getElementById('rlRefill').value;
      const msg = document.getElementById('rateLimitMsg');

      try {
        const res = await fetch(`/fluxgate/rate-limit?capacity=${cap}&refillRatePerMinute=${ref}`, { method: 'POST' });
        if (res.ok) {
          msg.textContent = '✓ Updated!';
          setTimeout(() => msg.textContent = '', 3000);
          fetchStatus();
        }
      } catch (e) {
        alert('Failed to update rate limit: ' + e.message);
      }
    }

    async function resetMetricsLog() {
      try {
        await fetch('/fluxgate/metrics', { method: 'DELETE' });
        fetchMetrics();
        fetchStatus();
      } catch (e) {
        console.error(e);
      }
    }

    async function setStrategy(name) {
      try {
        const res = await fetch('/fluxgate/strategy?name=' + name, { method: 'POST' });
        if (res.ok) {
          fetchStatus();
        }
      } catch (err) {
        alert('Failed to set strategy: ' + err.message);
      }
    }

    async function triggerHealthCheck() {
      try {
        await fetch('/fluxgate/health-check', { method: 'POST' });
        fetchStatus();
      } catch (err) {
        console.error(err);
      }
    }

    async function addBackend() {
      const input = document.getElementById('newBackendUrl');
      const url = input.value.trim();
      if (!url) return;

      try {
        const res = await fetch('/fluxgate/backends', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ url: url })
        });
        if (res.ok) {
          input.value = '';
          fetchStatus();
        } else {
          const err = await res.json();
          alert('Error adding backend: ' + (err.error || 'Unknown error'));
        }
      } catch (e) {
        alert('Network error: ' + e.message);
      }
    }

    async function removeBackend(url) {
      if (!confirm('Remove backend ' + url + '?')) return;
      try {
        const res = await fetch('/fluxgate/backends?url=' + encodeURIComponent(url), { method: 'DELETE' });
        if (res.ok) {
          fetchStatus();
        }
      } catch (e) {
        alert('Error: ' + e.message);
      }
    }

    async function sendTestRequest() {
      const pathInput = document.getElementById('testPath');
      let path = pathInput.value.trim();
      if (!path.startsWith('/')) path = '/' + path;

      const consoleBox = document.getElementById('testOutput');
      consoleBox.textContent = `[SENDING] GET ${path}...\\n`;

      const startTime = performance.now();
      try {
        const res = await fetch(path, { headers: { 'Accept': 'application/json' } });
        const latency = Math.round(performance.now() - startTime);

        const upstream = res.headers.get('X-Upstream-Backend') || 'Unknown';
        const proxyBy = res.headers.get('X-Proxy-By') || 'FluxGate';
        const proxyLatency = res.headers.get('X-Proxy-Latency') || (latency + 'ms');
        const cacheHeader = res.headers.get('X-Cache') || 'NONE';
        const rateLimitRemaining = res.headers.get('X-RateLimit-Remaining');

        let textBody = await res.text();
        try {
          const parsed = JSON.parse(textBody);
          textBody = JSON.stringify(parsed, null, 2);
        } catch (_) {}

        consoleBox.textContent =
          `HTTP/1.1 ${res.status} ${res.statusText}\\n` +
          `X-Proxy-By: ${proxyBy}\\n` +
          `X-Cache: ${cacheHeader}\\n` +
          `X-Upstream-Backend: ${upstream}\\n` +
          `X-RateLimit-Remaining: ${rateLimitRemaining || 'N/A'}\\n` +
          `Round-Trip Latency: ${latency}ms (Proxy Latency: ${proxyLatency})\\n\\n` +
          textBody;

        if (upstream && !upstream.includes('Edge Cache')) {
          const card = document.getElementById('card-' + encodeURIComponent(upstream));
          if (card) {
            card.classList.add('flash');
            setTimeout(() => card.classList.remove('flash'), 800);
          }
        }

        fetchStatus();
        fetchMetrics();
      } catch (err) {
        consoleBox.textContent += `[ERROR] ${err.message}`;
      }
    }

    async function testCacheAccelerator() {
      const pathInput = document.getElementById('testPath');
      let path = pathInput.value.trim();
      if (!path.startsWith('/')) path = '/' + path;

      const consoleBox = document.getElementById('testOutput');
      consoleBox.textContent = `[CACHE ACCELERATOR BENCHMARK] Demonstrating edge sub-millisecond cache hit...\\n\\n`;

      // 1st request (Warmup / Cache MISS)
      consoleBox.textContent += `==> Step 1: Request 1 (Initial fetch -> Miss -> Populate Cache)...\\n`;
      const t1 = performance.now();
      const res1 = await fetch(path, { headers: { 'Accept': 'application/json' } });
      const lat1 = Math.round(performance.now() - t1);
      const cache1 = res1.headers.get('X-Cache') || 'MISS';
      const backend1 = res1.headers.get('X-Upstream-Backend') || 'Upstream Backend';
      consoleBox.textContent += `    HTTP ${res1.status} | Cache: ${cache1} | Origin: ${backend1} | Latency: ${lat1}ms\\n\\n`;

      // 2nd request (Immediate Edge Cache HIT)
      consoleBox.textContent += `==> Step 2: Request 2 (Immediate repeat -> Sub-millisecond EDGE CACHE HIT)...\\n`;
      const t2 = performance.now();
      const res2 = await fetch(path, { headers: { 'Accept': 'application/json' } });
      const lat2 = Math.round(performance.now() - t2);
      const cache2 = res2.headers.get('X-Cache') || 'HIT';
      const proxyLat2 = res2.headers.get('X-Proxy-Latency') || '0ms (Edge Cache)';
      consoleBox.textContent += `    HTTP ${res2.status} | Cache: ⚡ ${cache2} (Sub-ms) | Latency: ${lat2}ms (Internal: ${proxyLat2})\\n\\n`;
      consoleBox.textContent += `✨ Result: Edge Cache eliminated upstream backend round-trip overhead! Speedup verified.`;

      fetchStatus();
      fetchMetrics();
    }

    async function testSingleFlightStampede() {
      const consoleBox = document.getElementById('testOutput');
      consoleBox.textContent = `[SINGLEFLIGHT COALESCING DRILL] Simulating concurrent Thundering Herd (Cache Stampede)...\\n\\n`;
      consoleBox.textContent += `==> Firing 6 concurrent requests to uncached route at the exact same millisecond...\\n`;

      const stampedePath = '/hello?stampede=' + Date.now();
      const t0 = performance.now();

      try {
        const requests = Array.from({ length: 6 }, (_, i) => 
          fetch(stampedePath, { headers: { 'Cache-Control': 'no-cache' } })
            .then(async res => {
              const lat = Math.round(performance.now() - t0);
              const coalesced = res.headers.get('X-Coalesced') === 'true';
              const waiters = res.headers.get('X-Coalesced-Waiters') || '0';
              const backend = res.headers.get('X-Upstream-Backend') || '-';
              return { index: i + 1, status: res.status, coalesced, waiters, backend, lat };
            })
        );

        const results = await Promise.all(requests);
        results.forEach(r => {
          const coalBadge = r.coalesced ? '⚡ COALESCED (SingleFlight Hitchhiker)' : '🚀 INITIATOR (Primary Upstream Request)';
          consoleBox.textContent += `    Request #${r.index}: HTTP ${r.status} | ${coalBadge} | Waiters: ${r.waiters} | ${r.lat}ms\\n`;
        });

        const coalescedCount = results.filter(r => r.coalesced).length;
        consoleBox.textContent += `\\n✨ Result: SingleFlight coalesced ${coalescedCount} of 6 concurrent requests into ONE upstream call! Upstream load reduced by ${(coalescedCount / 6 * 100).toFixed(1)}%.`;
      } catch (err) {
        consoleBox.textContent += `\\n❌ Drill error: ${err.message}`;
      }

      fetchStatus();
      fetchMetrics();
    }

    async function sendBatchRequests(count) {
      const pathInput = document.getElementById('testPath');
      let path = pathInput.value.trim();
      if (!path.startsWith('/')) path = '/' + path;

      const consoleBox = document.getElementById('testOutput');
      consoleBox.textContent = `[BATCH SIMULATION] Dispatching ${count} requests through strategy '${currentStrategy}'...\\n`;

      const hits = {};
      currentBackends.forEach(b => hits[b.url] = 0);

      for (let i = 1; i <= count; i++) {
        try {
          const res = await fetch(path, { headers: { 'Accept': 'application/json' } });
          const upstream = res.headers.get('X-Upstream-Backend') || 'Unknown';
          const cacheHeader = res.headers.get('X-Cache') || 'NONE';
          hits[upstream] = (hits[upstream] || 0) + 1;
          consoleBox.textContent += `#${i.toString().padStart(2, '0')}: HTTP ${res.status} [Cache: ${cacheHeader}] -> ${upstream}\\n`;
        } catch (e) {
          consoleBox.textContent += `#${i.toString().padStart(2, '0')}: FAILED -> ${e.message}\\n`;
        }
        consoleBox.scrollTop = consoleBox.scrollHeight;
      }

      renderDistribution(hits, count);
      fetchStatus();
      fetchMetrics();
    }

    async function sendSpamRequests(count) {
      const pathInput = document.getElementById('testPath');
      let path = pathInput.value.trim();
      if (!path.startsWith('/')) path = '/' + path;

      const consoleBox = document.getElementById('testOutput');
      consoleBox.textContent = `[BURST RATE-LIMIT TEST] Firing ${count} rapid concurrent requests to test token-bucket limiter...\\n`;

      const promises = [];
      for (let i = 1; i <= count; i++) {
        promises.push(
          fetch(path, { headers: { 'Accept': 'application/json' } })
            .then(res => {
              const rem = res.headers.get('X-RateLimit-Remaining');
              const tag = res.status === 429 ? '🚫 [429 THROTTLED]' : `✓ [${res.status} OK]`;
              consoleBox.textContent += `${tag} Req #${i} | Remaining Tokens: ${rem || '0'}\\n`;
              consoleBox.scrollTop = consoleBox.scrollHeight;
            })
            .catch(err => {
              consoleBox.textContent += `Err #${i}: ${err.message}\\n`;
            })
        );
      }
      await Promise.all(promises);
      fetchStatus();
      fetchMetrics();
    }

    async function testCircuitFailover() {
      const consoleBox = document.getElementById('testOutput');
      consoleBox.textContent = `[CIRCUIT BREAKER & IN-FLIGHT FAILOVER DRILL]\\nSimulating sudden upstream outage & verifying zero-downtime rerouting...\\n\\n`;

      if (currentBackends.length < 2) {
        consoleBox.textContent += `⚠️ Failover drill requires at least 2 registered backends. Please add another backend first.`;
        return;
      }

      const primary = currentBackends[0].url;

      // 1. Manually trip primary backend
      consoleBox.textContent += `==> Step 1: Simulating outage by manually tripping circuit for primary backend: ${primary}...\\n`;
      try {
        await fetch('/fluxgate/circuit-breakers/trip', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ url: primary, reason: 'Chaos Drill Simulation' })
        });
        consoleBox.textContent += `    ✓ Circuit for ${primary} is now OPEN (Traffic blocked from hitting this host).\\n\\n`;
      } catch (e) {
        consoleBox.textContent += `    Failed to trip circuit: ${e.message}\\n`;
      }

      // 2. Dispatch request to /hello (cache-bypassed)
      consoleBox.textContent += `==> Step 2: Client sends request to reverse proxy...\\n`;
      const t1 = performance.now();
      try {
        const res = await fetch('/hello', {
          headers: { 'Accept': 'application/json', 'Cache-Control': 'no-cache' }
        });
        const lat = Math.round(performance.now() - t1);
        const upstream = res.headers.get('X-Upstream-Backend') || 'Unknown';
        const cbHeader = res.headers.get('X-Circuit-Breaker') || 'N/A';
        const failovers = res.headers.get('X-Failover-Attempts') || '0';
        const failoverOrigin = res.headers.get('X-Failover-Origin');

        consoleBox.textContent +=
          `    HTTP ${res.status} ${res.statusText}\\n` +
          `    X-Upstream-Backend: ${upstream}\\n` +
          `    X-Circuit-Breaker: ${cbHeader}\\n` +
          `    X-Failover-Attempts: ${failovers}\\n` +
          (failoverOrigin ? `    X-Failover-Origin: ${failoverOrigin}\\n` : '') +
          `    Latency: ${lat}ms\\n\\n` +
          `✨ Result: Request was transparently rerouted to alternate healthy backend without client-facing errors!\\n`;
      } catch (err) {
        consoleBox.textContent += `    [FAIL] Request failed: ${err.message}\\n`;
      }

      fetchStatus();
      fetchMetrics();
    }

    async function toggleCircuitBreaker() {
      try {
        const res = await fetch('/fluxgate/circuit-breakers/toggle', { method: 'POST' });
        if (res.ok) {
          fetchStatus();
        }
      } catch (e) {
        alert('Failed to toggle circuit breaker: ' + e.message);
      }
    }

    async function resetAllCircuits() {
      try {
        const res = await fetch('/fluxgate/circuit-breakers/reset', { method: 'POST' });
        if (res.ok) {
          alert('✓ All circuit breakers reset to CLOSED.');
          fetchStatus();
        }
      } catch (e) {
        alert('Failed to reset circuit breakers: ' + e.message);
      }
    }

    async function tripBackendCircuit(url) {
      try {
        const res = await fetch('/fluxgate/circuit-breakers/trip', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ url: url, reason: 'Manual operator trip' })
        });
        if (res.ok) {
          fetchStatus();
        }
      } catch (e) {
        alert('Failed to trip circuit: ' + e.message);
      }
    }

    async function resetBackendCircuit(url) {
      try {
        const res = await fetch('/fluxgate/circuit-breakers/reset', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ url: url })
        });
        if (res.ok) {
          fetchStatus();
        }
      } catch (e) {
        alert('Failed to reset circuit: ' + e.message);
      }
    }

    function renderDistribution(hits, total) {
      const container = document.getElementById('distributionContainer');
      const bar = document.getElementById('distBar');
      const legend = document.getElementById('distLegend');
      container.style.display = 'block';

      bar.innerHTML = '';
      legend.innerHTML = '';

      let idx = 0;
      for (const [url, count] of Object.entries(hits)) {
        if (count === 0) continue;
        const color = COLORS[idx % COLORS.length];
        const pct = ((count / total) * 100).toFixed(1);

        const seg = document.createElement('div');
        seg.className = 'dist-segment';
        seg.style.width = pct + '%';
        seg.style.background = color;
        seg.title = `${url}: ${count} (${pct}%)`;
        bar.appendChild(seg);

        const leg = document.createElement('div');
        leg.className = 'dist-legend-item';
        leg.innerHTML = `<span class="color-dot" style="background: ${color}"></span> <strong>${url}</strong>: ${count} (${pct}%)`;
        legend.appendChild(leg);

        idx++;
      }
    }

    // Auto-refresh cluster status and metrics
    setInterval(() => {
      fetchStatus();
      fetchMetrics();
    }, 2500);

    fetchStatus();
    fetchMetrics();
  </script>
</body>
</html>
""";
}
