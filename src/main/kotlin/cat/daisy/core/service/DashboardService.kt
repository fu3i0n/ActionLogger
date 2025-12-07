package cat.daisy.core.service

import cat.daisy.core.Core
import cat.daisy.core.database.repositories.LogRepository
import cat.daisy.core.utils.TextUtils.log
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class DashboardService(
    private val port: Int,
    private val token: String?,
) {
    private var server: HttpServer? = null

    fun start() {
        if (server != null) return
        server = HttpServer.create(InetSocketAddress(port), 0)
        server?.apply {
            createContext("/", DashboardUIHandler())
            createContext("/health", HealthHandler())
            createContext("/logs", LogsHandler(token))
            createContext("/stats", StatsHandler(token))
            createContext("/summary", SummaryHandler(token))
            executor =
                java.util.concurrent.Executors
                    .newCachedThreadPool()
            start()
        }
        log("Dashboard started on :$port", "SUCCESS")
    }

    fun stop() {
        server?.stop(0)
        server = null
        log("Dashboard stopped", "INFO")
    }

    // --- Handlers ---
    private class DashboardUIHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            if (exchange.requestMethod != "GET") {
                exchange.sendResponseHeaders(405, -1)
                return
            }

            val html = getDashboardHTML()
            val bytes = html.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.apply {
                add("Content-Type", "text/html; charset=utf-8")
                add("Cache-Control", "no-cache")
            }
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private class HealthHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            exchange.json(200, "{" + "\"status\":\"ok\"" + "}")
        }
    }

    private class StatsHandler(
        private val token: String?,
    ) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            if (!exchange.ensureGet()) return
            val params = parseQuery(exchange.requestURI.rawQuery)
            if (!exchange.checkToken(token, params)) return

            val (lq, cq) = Core.instance.getActionLogService().stats()
            val pool =
                cat.daisy.core.database.DatabaseManager
                    .getPoolStats()
            val body =
                "{" +
                    "\"logQueue\":" + lq + "," +
                    "\"containerQueue\":" + cq + "," +
                    "\"pool\":\"" + escape(pool) + "\"" +
                    "}"
            exchange.json(200, body)
        }
    }

    private class LogsHandler(
        private val token: String?,
    ) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            if (!exchange.ensureGet()) return
            val params = parseQuery(exchange.requestURI.rawQuery)
            if (!exchange.checkToken(token, params)) return

            val player = params["player"]?.firstOrNull()
            val action = params["action"]?.firstOrNull()?.toIntOrNull()
            val from = params["from"]?.firstOrNull()?.toIntOrNull()
            val to = params["to"]?.firstOrNull()?.toIntOrNull()
            val page = (params["page"]?.firstOrNull()?.toIntOrNull() ?: 0).coerceAtLeast(0)
            val size = (params["size"]?.firstOrNull()?.toIntOrNull() ?: 50).coerceIn(1, 200)

            val rows =
                LogRepository.list(
                    player = player,
                    action = action,
                    fromEpochSec = from,
                    toEpochSec = to,
                    page = page,
                    size = size,
                    sortDesc = true,
                )

            val json =
                buildString {
                    append('[')
                    rows.forEachIndexed { i, r ->
                        if (i > 0) append(',')
                        append('{')
                        append("\"id\":").append(r.id).append(',')
                        append("\"time\":").append(r.time.epochSecond).append(',')
                        append("\"player\":\"").append(escape(r.playerName)).append("\",")
                        append("\"action\":").append(r.action).append(',')
                        append("\"detail\":\"").append(escape(r.detail ?: "")).append("\",")
                        val loc = r.location
                        if (loc != null && loc.world != null) {
                            append("\"world\":\"").append(escape(loc.world!!.name)).append("\",")
                            append("\"x\":").append(loc.blockX).append(',')
                            append("\"y\":").append(loc.blockY).append(',')
                            append("\"z\":").append(loc.blockZ)
                        } else {
                            append("\"world\":\"\",\"x\":0,\"y\":0,\"z\":0")
                        }
                        append('}')
                    }
                    append(']')
                }
            exchange.json(200, json)
        }
    }

    private class SummaryHandler(
        private val token: String?,
    ) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            if (!exchange.ensureGet()) return
            val params = parseQuery(exchange.requestURI.rawQuery)
            if (!exchange.checkToken(token, params)) return

            val player = params["player"]?.firstOrNull()
            val from = params["from"]?.firstOrNull()?.toIntOrNull()
            val to = params["to"]?.firstOrNull()?.toIntOrNull()
            val lastMin = params["last"]?.firstOrNull()?.toIntOrNull()

            val now = (System.currentTimeMillis() / 1000L).toInt()
            val (fromEff, toEff) =
                when {
                    from != null && to != null -> from to to
                    lastMin != null -> (now - lastMin.coerceIn(1, 24 * 60) * 60) to now
                    else -> (now - 3600) to now // default 1h
                }

            val counts = LogRepository.countByAction(player = player, fromEpochSec = fromEff, toEpochSec = toEff)
            val total = counts.values.sum()

            val json =
                buildString {
                    append('{')
                    append("\"total\":").append(total).append(',')
                    append("\"from\":").append(fromEff).append(',')
                    append("\"to\":").append(toEff).append(',')
                    append("\"counts\":{")
                    counts.entries.sortedBy { it.key }.forEachIndexed { i, e ->
                        if (i > 0) append(',')
                        append('"')
                            .append(e.key)
                            .append('"')
                            .append(':')
                            .append(e.value)
                    }
                    append('}')
                    append('}')
                }
            exchange.json(200, json)
        }
    }
}

// --- Helpers ---
private fun HttpExchange.json(
    code: Int,
    body: String,
) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    responseHeaders.apply {
        add("Content-Type", "application/json; charset=utf-8")
        add("Access-Control-Allow-Origin", "*")
        add("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        add("Access-Control-Allow-Headers", "Content-Type, Accept")
        add("Cache-Control", "no-cache, no-store, must-revalidate")
    }
    sendResponseHeaders(code, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

private fun HttpExchange.errorJson(
    code: Int,
    error: String,
    message: String = "",
) {
    val body =
        buildString {
            append('{')
            append("\"error\":\"").append(escape(error)).append("\",")
            append("\"message\":\"").append(escape(message)).append('"')
            append('}')
        }
    json(code, body)
}

private fun HttpExchange.ensureGet(): Boolean {
    if (requestMethod != "GET" && requestMethod != "OPTIONS") {
        errorJson(405, "method_not_allowed", "Only GET requests are allowed")
        return false
    }
    if (requestMethod == "OPTIONS") {
        responseHeaders.apply {
            add("Access-Control-Allow-Origin", "*")
            add("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
            add("Access-Control-Allow-Headers", "Content-Type, Accept")
        }
        sendResponseHeaders(204, -1)
        return false
    }
    return true
}

private fun HttpExchange.checkToken(
    token: String?,
    params: Map<String, List<String>>,
): Boolean {
    if (token == null) return true
    val provided = params["token"]?.firstOrNull()
    if (provided == null || provided != token) {
        errorJson(401, "unauthorized", "Invalid or missing token")
        return false
    }
    return true
}

private fun parseQuery(query: String?): Map<String, List<String>> {
    if (query.isNullOrBlank()) return emptyMap()
    return query
        .split('&')
        .mapNotNull {
            val idx = it.indexOf('=')
            if (idx <= 0) {
                null
            } else {
                val k = URLDecoder.decode(it.substring(0, idx), StandardCharsets.UTF_8)
                val v = URLDecoder.decode(it.substring(idx + 1), StandardCharsets.UTF_8)
                k to v
            }
        }.groupBy({ it.first }, { it.second })
}

private fun escape(s: String): String =
    buildString {
        for (ch in s) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

private fun getDashboardHTML(): String =
    """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ActionLogger Pro - Advanced Analytics Dashboard</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800;900&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <style>
        :root {
            --bg-primary: #0a0a0f;
            --bg-secondary: #12121a;
            --bg-card: rgba(18, 18, 26, 0.8);
            --bg-card-hover: rgba(25, 25, 40, 0.9);
            --accent-primary: #00d4ff;
            --accent-secondary: #7c3aed;
            --accent-tertiary: #f43f5e;
            --accent-success: #10b981;
            --accent-warning: #f59e0b;
            --text-primary: #ffffff;
            --text-secondary: #94a3b8;
            --text-muted: #64748b;
            --border-color: rgba(148, 163, 184, 0.1);
            --border-glow: rgba(0, 212, 255, 0.3);
            --glass: rgba(255, 255, 255, 0.03);
            --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.3);
            --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.4);
            --shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.5);
            --shadow-xl: 0 20px 25px -5px rgba(0, 0, 0, 0.5);
            --shadow-glow: 0 0 40px rgba(0, 212, 255, 0.15);
            --shadow-glow-purple: 0 0 40px rgba(124, 58, 237, 0.15);
            --radius-sm: 8px;
            --radius-md: 12px;
            --radius-lg: 16px;
            --radius-xl: 24px;
            --radius-2xl: 32px;
            --transition-fast: 150ms cubic-bezier(0.4, 0, 0.2, 1);
            --transition-base: 250ms cubic-bezier(0.4, 0, 0.2, 1);
            --transition-slow: 350ms cubic-bezier(0.4, 0, 0.2, 1);
        }
        
        * { margin: 0; padding: 0; box-sizing: border-box; }
        
        html {
            font-size: 16px;
            scroll-behavior: smooth;
        }
        
        @media (max-width: 1400px) { html { font-size: 15px; } }
        @media (max-width: 1200px) { html { font-size: 14px; } }
        @media (max-width: 768px) { html { font-size: 13px; } }
        
        body {
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            background: var(--bg-primary);
            min-height: 100vh;
            color: var(--text-primary);
            overflow-x: hidden;
            line-height: 1.6;
            -webkit-font-smoothing: antialiased;
            -moz-osx-font-smoothing: grayscale;
        }
        
        /* Animated Background */
        .bg-effects {
            position: fixed;
            inset: 0;
            z-index: -1;
            overflow: hidden;
        }
        
        .bg-effects::before {
            content: '';
            position: absolute;
            width: 150%;
            height: 150%;
            top: -25%;
            left: -25%;
            background: 
                radial-gradient(ellipse at 20% 0%, rgba(124, 58, 237, 0.15) 0%, transparent 50%),
                radial-gradient(ellipse at 80% 0%, rgba(0, 212, 255, 0.15) 0%, transparent 50%),
                radial-gradient(ellipse at 50% 100%, rgba(244, 63, 94, 0.1) 0%, transparent 50%);
            animation: bgFloat 20s ease-in-out infinite;
        }
        
        @keyframes bgFloat {
            0%, 100% { transform: translate(0, 0) rotate(0deg); }
            33% { transform: translate(2%, 2%) rotate(1deg); }
            66% { transform: translate(-2%, 1%) rotate(-1deg); }
        }
        
        .bg-grid {
            position: absolute;
            inset: 0;
            background-image: 
                linear-gradient(rgba(255, 255, 255, 0.02) 1px, transparent 1px),
                linear-gradient(90deg, rgba(255, 255, 255, 0.02) 1px, transparent 1px);
            background-size: 60px 60px;
            mask-image: radial-gradient(ellipse at center, black 0%, transparent 70%);
        }
        
        .container {
            max-width: 1600px;
            margin: 0 auto;
            padding: 2rem;
        }
        
        /* Header */
        header {
            text-align: center;
            margin-bottom: 3rem;
            padding: 2rem 0;
            position: relative;
            display: flex;
            flex-direction: column;
            align-items: center;
        }
        
        .logo-wrapper {
            display: flex;
            align-items: center;
            gap: 1.5rem;
            margin-bottom: 1.5rem;
            flex-wrap: wrap;
            justify-content: center;
        }
        
        .logo-icon {
            width: 80px;
            height: 80px;
            background: linear-gradient(135deg, var(--accent-primary), var(--accent-secondary));
            border-radius: var(--radius-xl);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 2.5rem;
            box-shadow: 
                0 0 0 1px rgba(255, 255, 255, 0.1),
                var(--shadow-glow),
                var(--shadow-xl);
            animation: logoFloat 3s ease-in-out infinite;
            position: relative;
        }
        
        .logo-icon::before {
            content: '';
            position: absolute;
            inset: -3px;
            border-radius: inherit;
            background: linear-gradient(135deg, var(--accent-primary), var(--accent-secondary));
            z-index: -1;
            opacity: 0.5;
            filter: blur(15px);
            animation: logoPulse 2s ease-in-out infinite;
        }
        
        @keyframes logoFloat {
            0%, 100% { transform: translateY(0); }
            50% { transform: translateY(-8px); }
        }
        
        @keyframes logoPulse {
            0%, 100% { opacity: 0.3; transform: scale(1); }
            50% { opacity: 0.6; transform: scale(1.1); }
        }
        
        .brand-text h1 {
            font-size: 2.75rem;
            font-weight: 900;
            letter-spacing: -0.03em;
            background: linear-gradient(135deg, #fff 0%, rgba(255, 255, 255, 0.8) 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
            line-height: 1.1;
        }
        
        .brand-text p {
            font-size: 1rem;
            color: var(--text-secondary);
            font-weight: 500;
            margin-top: 0.25rem;
        }
        
        .status-bar {
            display: inline-flex;
            align-items: center;
            gap: 2rem;
            background: var(--bg-card);
            backdrop-filter: blur(20px);
            border: 1px solid var(--border-color);
            padding: 0.75rem 1.5rem;
            border-radius: 100px;
            margin-top: 1.5rem;
        }
        
        .status-item {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            font-size: 0.875rem;
            font-weight: 600;
        }
        
        .status-dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: var(--accent-success);
            box-shadow: 0 0 10px var(--accent-success);
            animation: statusPulse 2s ease-in-out infinite;
        }
        
        @keyframes statusPulse {
            0%, 100% { opacity: 1; box-shadow: 0 0 10px var(--accent-success); }
            50% { opacity: 0.6; box-shadow: 0 0 20px var(--accent-success); }
        }
        
        .status-item.live { color: var(--accent-success); }
        .status-item.time { color: var(--text-secondary); }
        
        .divider {
            width: 1px;
            height: 20px;
            background: var(--border-color);
        }
        
        /* Stats Grid */
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(4, 1fr);
            gap: 1.5rem;
            margin-bottom: 2rem;
        }
        
        @media (max-width: 1200px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
        @media (max-width: 600px) { .stats-grid { grid-template-columns: 1fr; } }
        
        .stat-card {
            background: var(--bg-card);
            backdrop-filter: blur(20px);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-xl);
            padding: 1.75rem;
            position: relative;
            overflow: hidden;
            transition: var(--transition-base);
            cursor: default;
        }
        
        .stat-card::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            height: 3px;
            background: linear-gradient(90deg, var(--card-accent, var(--accent-primary)), transparent);
            opacity: 0;
            transition: var(--transition-base);
        }
        
        .stat-card:hover {
            background: var(--bg-card-hover);
            border-color: var(--border-glow);
            transform: translateY(-4px);
            box-shadow: var(--shadow-glow);
        }
        
        .stat-card:hover::before { opacity: 1; }
        
        .stat-card.accent-cyan { --card-accent: var(--accent-primary); }
        .stat-card.accent-purple { --card-accent: var(--accent-secondary); }
        .stat-card.accent-pink { --card-accent: var(--accent-tertiary); }
        .stat-card.accent-green { --card-accent: var(--accent-success); }
        
        .stat-header {
            display: flex;
            justify-content: space-between;
            align-items: flex-start;
            margin-bottom: 1rem;
        }
        
        .stat-title {
            font-size: 0.8rem;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.1em;
            color: var(--text-muted);
        }
        
        .stat-icon {
            width: 44px;
            height: 44px;
            border-radius: var(--radius-lg);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.25rem;
            background: linear-gradient(135deg, rgba(0, 212, 255, 0.1), rgba(124, 58, 237, 0.1));
            border: 1px solid rgba(255, 255, 255, 0.05);
        }
        
        .stat-value {
            font-size: 2.5rem;
            font-weight: 800;
            letter-spacing: -0.02em;
            line-height: 1;
            margin-bottom: 0.5rem;
            font-family: 'JetBrains Mono', monospace;
        }
        
        .stat-card.accent-cyan .stat-value { color: var(--accent-primary); }
        .stat-card.accent-purple .stat-value { color: var(--accent-secondary); }
        .stat-card.accent-pink .stat-value { color: var(--accent-tertiary); }
        .stat-card.accent-green .stat-value { color: var(--accent-success); }
        
        .stat-label {
            font-size: 0.875rem;
            color: var(--text-secondary);
        }
        
        .stat-badge {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            padding: 4px 10px;
            border-radius: 100px;
            font-size: 0.75rem;
            font-weight: 600;
            margin-top: 0.75rem;
            background: rgba(16, 185, 129, 0.1);
            color: var(--accent-success);
            border: 1px solid rgba(16, 185, 129, 0.2);
        }
        
        /* Charts Section */
        .charts-section {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 1.5rem;
            margin-bottom: 2rem;
        }
        
        @media (max-width: 1000px) { .charts-section { grid-template-columns: 1fr; } }
        
        .card {
            background: var(--bg-card);
            backdrop-filter: blur(20px);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-xl);
            padding: 1.5rem;
            transition: var(--transition-base);
        }
        
        .card:hover {
            border-color: var(--border-glow);
            box-shadow: var(--shadow-glow);
        }
        
        .card-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 1.5rem;
            padding-bottom: 1rem;
            border-bottom: 1px solid var(--border-color);
        }
        
        .card-title {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            font-size: 1.1rem;
            font-weight: 700;
            color: var(--text-primary);
        }
        
        .card-title-icon {
            width: 32px;
            height: 32px;
            border-radius: var(--radius-md);
            display: flex;
            align-items: center;
            justify-content: center;
            background: linear-gradient(135deg, rgba(0, 212, 255, 0.15), rgba(124, 58, 237, 0.15));
            font-size: 1rem;
        }
        
        .chart-container {
            position: relative;
            height: 300px;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        
        /* Filters Section */
        .filters-card {
            background: var(--bg-card);
            backdrop-filter: blur(20px);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-xl);
            padding: 1.5rem;
            margin-bottom: 2rem;
        }
        
        .filters-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 1.5rem;
        }
        
        .filters-title {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            font-size: 1.1rem;
            font-weight: 700;
        }
        
        .filters-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 1rem;
            margin-bottom: 1.5rem;
        }
        
        .filter-group {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
        }
        
        .filter-label {
            font-size: 0.75rem;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.05em;
            color: var(--text-muted);
        }
        
        .filter-input, .filter-select {
            padding: 0.75rem 1rem;
            background: rgba(0, 0, 0, 0.2);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-md);
            font-size: 0.9rem;
            font-family: inherit;
            color: var(--text-primary);
            transition: var(--transition-fast);
        }
        
        .filter-input::placeholder { color: var(--text-muted); }
        
        .filter-input:focus, .filter-select:focus {
            outline: none;
            border-color: var(--accent-primary);
            box-shadow: 0 0 0 3px rgba(0, 212, 255, 0.1);
        }
        
        .filter-select option {
            background: var(--bg-secondary);
            color: var(--text-primary);
        }
        
        .btn-group {
            display: flex;
            flex-wrap: wrap;
            gap: 0.75rem;
        }
        
        .btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 0.5rem;
            padding: 0.75rem 1.5rem;
            border-radius: var(--radius-md);
            font-size: 0.875rem;
            font-weight: 600;
            font-family: inherit;
            cursor: pointer;
            transition: var(--transition-fast);
            border: none;
            white-space: nowrap;
        }
        
        .btn-primary {
            background: linear-gradient(135deg, var(--accent-primary), var(--accent-secondary));
            color: white;
            box-shadow: 0 4px 15px rgba(0, 212, 255, 0.25);
        }
        
        .btn-primary:hover {
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(0, 212, 255, 0.35);
        }
        
        .btn-secondary {
            background: rgba(255, 255, 255, 0.05);
            color: var(--text-secondary);
            border: 1px solid var(--border-color);
        }
        
        .btn-secondary:hover {
            background: rgba(255, 255, 255, 0.1);
            color: var(--text-primary);
            border-color: var(--text-muted);
        }
        
        .btn-icon {
            font-size: 1rem;
        }
        
        /* Logs Table Section */
        .logs-card {
            background: var(--bg-card);
            backdrop-filter: blur(20px);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-xl);
            overflow: hidden;
        }
        
        .logs-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 1.5rem;
            border-bottom: 1px solid var(--border-color);
        }
        
        .logs-title {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            font-size: 1.1rem;
            font-weight: 700;
        }
        
        .pagination {
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }
        
        .page-btn {
            padding: 0.5rem 1rem;
            background: rgba(255, 255, 255, 0.05);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-md);
            color: var(--text-secondary);
            font-size: 0.875rem;
            font-weight: 600;
            font-family: inherit;
            cursor: pointer;
            transition: var(--transition-fast);
        }
        
        .page-btn:hover:not(:disabled) {
            background: rgba(255, 255, 255, 0.1);
            color: var(--text-primary);
        }
        
        .page-btn:disabled {
            opacity: 0.4;
            cursor: not-allowed;
        }
        
        .page-info {
            font-size: 0.875rem;
            font-weight: 600;
            color: var(--text-muted);
            padding: 0 0.5rem;
        }
        
        .logs-table-wrapper {
            overflow-x: auto;
        }
        
        .logs-table {
            width: 100%;
            border-collapse: collapse;
        }
        
        .logs-table th {
            padding: 1rem 1.25rem;
            text-align: left;
            font-size: 0.7rem;
            font-weight: 700;
            text-transform: uppercase;
            letter-spacing: 0.1em;
            color: var(--text-muted);
            background: rgba(0, 0, 0, 0.2);
            border-bottom: 1px solid var(--border-color);
            white-space: nowrap;
        }
        
        .logs-table td {
            padding: 1rem 1.25rem;
            font-size: 0.9rem;
            color: var(--text-primary);
            border-bottom: 1px solid var(--border-color);
            vertical-align: middle;
        }
        
        .logs-table tbody tr {
            transition: var(--transition-fast);
        }
        
        .logs-table tbody tr:hover {
            background: rgba(0, 212, 255, 0.03);
        }
        
        .logs-table tbody tr:last-child td { border-bottom: none; }
        
        .timestamp {
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.8rem;
            color: var(--text-secondary);
        }
        
        .player-name {
            font-weight: 600;
            color: var(--accent-primary);
        }
        
        .action-badge {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 4px 10px;
            border-radius: 100px;
            font-size: 0.75rem;
            font-weight: 600;
            white-space: nowrap;
        }
        
        .action-badge.action-0 { background: rgba(0, 212, 255, 0.15); color: #00d4ff; }
        .action-badge.action-1 { background: rgba(244, 63, 94, 0.15); color: #f43f5e; }
        .action-badge.action-4 { background: rgba(245, 158, 11, 0.15); color: #f59e0b; }
        .action-badge.action-5 { background: rgba(16, 185, 129, 0.15); color: #10b981; }
        .action-badge.action-6 { background: rgba(124, 58, 237, 0.15); color: #7c3aed; }
        .action-badge.action-7 { background: rgba(236, 72, 153, 0.15); color: #ec4899; }
        .action-badge.action-8 { background: rgba(139, 92, 246, 0.15); color: #8b5cf6; }
        .action-badge.action-9 { background: rgba(6, 182, 212, 0.15); color: #06b6d4; }
        
        .detail-text {
            max-width: 300px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            color: var(--text-secondary);
            font-size: 0.85rem;
        }
        
        .location-text {
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.8rem;
            color: var(--text-muted);
            background: rgba(0, 0, 0, 0.2);
            padding: 4px 8px;
            border-radius: var(--radius-sm);
        }
        
        /* Loading & Empty States */
        .loading-state, .empty-state {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 4rem 2rem;
            text-align: center;
        }
        
        .spinner {
            width: 48px;
            height: 48px;
            border: 3px solid var(--border-color);
            border-top-color: var(--accent-primary);
            border-radius: 50%;
            animation: spin 0.8s linear infinite;
            margin-bottom: 1.5rem;
        }
        
        @keyframes spin {
            to { transform: rotate(360deg); }
        }
        
        .loading-text, .empty-text {
            font-size: 1rem;
            color: var(--text-secondary);
        }
        
        .empty-icon {
            font-size: 3rem;
            margin-bottom: 1rem;
            opacity: 0.5;
        }
        
        .empty-title {
            font-size: 1.25rem;
            font-weight: 700;
            color: var(--text-primary);
            margin-bottom: 0.5rem;
        }
        
        /* Footer */
        footer {
            text-align: center;
            padding: 2rem 0;
            margin-top: 2rem;
            border-top: 1px solid var(--border-color);
        }
        
        .footer-text {
            font-size: 0.875rem;
            color: var(--text-muted);
        }
        
        .footer-link {
            color: var(--accent-primary);
            text-decoration: none;
            font-weight: 600;
            transition: var(--transition-fast);
        }
        
        .footer-link:hover {
            color: var(--text-primary);
        }
        
        /* Scrollbar */
        ::-webkit-scrollbar {
            width: 8px;
            height: 8px;
        }
        
        ::-webkit-scrollbar-track {
            background: transparent;
        }
        
        ::-webkit-scrollbar-thumb {
            background: var(--text-muted);
            border-radius: 4px;
        }
        
        ::-webkit-scrollbar-thumb:hover {
            background: var(--text-secondary);
        }
        
        /* Animations */
        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(10px); }
            to { opacity: 1; transform: translateY(0); }
        }
        
        .animate-in {
            animation: fadeIn 0.5s ease-out forwards;
        }
        
        .delay-1 { animation-delay: 0.1s; opacity: 0; }
        .delay-2 { animation-delay: 0.2s; opacity: 0; }
        .delay-3 { animation-delay: 0.3s; opacity: 0; }
        .delay-4 { animation-delay: 0.4s; opacity: 0; }
    </style>
</head>
<body>
    <div class="bg-effects">
        <div class="bg-grid"></div>
    </div>
    
    <div class="container">
        <!-- Header -->
        <header class="animate-in">
            <div class="logo-wrapper">
                <div class="logo-icon">⚡</div>
                <div class="brand-text">
                    <h1>ActionLogger Pro</h1>
                    <p>Real-Time Analytics Dashboard</p>
                </div>
            </div>
            <div class="status-bar">
                <div class="status-item live">
                    <span class="status-dot"></span>
                    <span>System Online</span>
                </div>
                <div class="divider"></div>
                <div class="status-item time" id="currentTime">--:--:--</div>
                <div class="divider"></div>
                <div class="status-item" id="refreshStatus">Auto-refresh: 10s</div>
            </div>
        </header>
        
        <!-- Stats Grid -->
        <div class="stats-grid">
            <div class="stat-card accent-cyan animate-in delay-1">
                <div class="stat-header">
                    <span class="stat-title">Total Logs</span>
                    <div class="stat-icon">📊</div>
                </div>
                <div class="stat-value" id="totalLogs">0</div>
                <div class="stat-label">All recorded actions</div>
                <div class="stat-badge">⚡ Live Tracking</div>
            </div>
            <div class="stat-card accent-purple animate-in delay-2">
                <div class="stat-header">
                    <span class="stat-title">Log Queue</span>
                    <div class="stat-icon">⏳</div>
                </div>
                <div class="stat-value" id="logQueue">0</div>
                <div class="stat-label">Pending writes</div>
            </div>
            <div class="stat-card accent-pink animate-in delay-3">
                <div class="stat-header">
                    <span class="stat-title">Container Queue</span>
                    <div class="stat-icon">📦</div>
                </div>
                <div class="stat-value" id="containerQueue">0</div>
                <div class="stat-label">Transaction buffer</div>
            </div>
            <div class="stat-card accent-green animate-in delay-4">
                <div class="stat-header">
                    <span class="stat-title">System Status</span>
                    <div class="stat-icon">💚</div>
                </div>
                <div class="stat-value" style="font-size: 1.5rem;">HEALTHY</div>
                <div class="stat-label" id="poolStatus">All systems operational</div>
            </div>
        </div>
        
        <!-- Charts Section -->
        <div class="charts-section">
            <div class="card">
                <div class="card-header">
                    <div class="card-title">
                        <div class="card-title-icon">📊</div>
                        <span>Action Distribution</span>
                    </div>
                </div>
                <div class="chart-container">
                    <canvas id="actionChart"></canvas>
                </div>
            </div>
            <div class="card">
                <div class="card-header">
                    <div class="card-title">
                        <div class="card-title-icon">📈</div>
                        <span>Activity Timeline (24h)</span>
                    </div>
                </div>
                <div class="chart-container">
                    <canvas id="timelineChart"></canvas>
                </div>
            </div>
        </div>
        
        <!-- Filters Section -->
        <div class="filters-card">
            <div class="filters-header">
                <div class="filters-title">
                    <div class="card-title-icon">🔍</div>
                    <span>Filters & Search</span>
                </div>
            </div>
            <div class="filters-grid">
                <div class="filter-group">
                    <label class="filter-label">Player Name</label>
                    <input type="text" class="filter-input" id="playerFilter" placeholder="Search players...">
                </div>
                <div class="filter-group">
                    <label class="filter-label">Action Type</label>
                    <select class="filter-select" id="actionFilter">
                        <option value="">All Actions</option>
                        <option value="0">🧱 Block Placed</option>
                        <option value="1">💥 Block Broken</option>
                        <option value="4">💬 Chat</option>
                        <option value="5">⚙️ Command</option>
                        <option value="6">✅ Login</option>
                        <option value="7">❌ Logout</option>
                        <option value="8">📤 Item Drop</option>
                        <option value="9">📥 Item Pickup</option>
                    </select>
                </div>
                <div class="filter-group">
                    <label class="filter-label">Time Range</label>
                    <select class="filter-select" id="timeRange">
                        <option value="60">Last Hour</option>
                        <option value="360">Last 6 Hours</option>
                        <option value="1440" selected>Last 24 Hours</option>
                        <option value="10080">Last Week</option>
                        <option value="">All Time</option>
                    </select>
                </div>
                <div class="filter-group">
                    <label class="filter-label">Results Per Page</label>
                    <select class="filter-select" id="pageSize">
                        <option value="25">25 results</option>
                        <option value="50" selected>50 results</option>
                        <option value="100">100 results</option>
                        <option value="200">200 results</option>
                    </select>
                </div>
            </div>
            <div class="btn-group">
                <button class="btn btn-primary" onclick="applyFilters()">
                    <span class="btn-icon">🔍</span> Apply Filters
                </button>
                <button class="btn btn-secondary" onclick="clearFilters()">
                    <span class="btn-icon">✕</span> Clear
                </button>
                <button class="btn btn-secondary" onclick="refreshData()">
                    <span class="btn-icon">🔄</span> Refresh
                </button>
                <button class="btn btn-secondary" onclick="exportLogs()">
                    <span class="btn-icon">📥</span> Export CSV
                </button>
            </div>
        </div>
        
        <!-- Logs Table -->
        <div class="logs-card">
            <div class="logs-header">
                <div class="logs-title">
                    <div class="card-title-icon">📋</div>
                    <span>Action Logs</span>
                </div>
                <div class="pagination">
                    <button class="page-btn" onclick="prevPage()" id="prevBtn">← Prev</button>
                    <span class="page-info" id="pageInfo">Page 1</span>
                    <button class="page-btn" onclick="nextPage()" id="nextBtn">Next →</button>
                </div>
            </div>
            <div class="logs-table-wrapper">
                <div id="logsContent">
                    <div class="loading-state">
                        <div class="spinner"></div>
                        <span class="loading-text">Loading logs...</span>
                    </div>
                </div>
            </div>
        </div>
        
        <!-- Footer -->
        <footer>
            <p class="footer-text">
                ActionLogger Pro v1.0.0 • Made with ❤️ by <a href="https://daisy.cat" class="footer-link" target="_blank">Daisy</a> • 
                Press <kbd style="background: rgba(255,255,255,0.1); padding: 2px 6px; border-radius: 4px; font-size: 0.8em;">Ctrl+R</kbd> to refresh
            </p>
        </footer>
    </div>
    
    <script>
        // State
        let currentPage = 0;
        let currentFilters = {};
        let actionChart, timelineChart;
        let allLogs = [];
        
        // Update clock
        function updateClock() {
            const now = new Date();
            document.getElementById('currentTime').textContent = now.toLocaleTimeString();
        }
        setInterval(updateClock, 1000);
        updateClock();
        
        const actionLabels = {
            0: 'Block Placed',
            1: 'Block Broken',
            4: 'Chat',
            5: 'Command',
            6: 'Login',
            7: 'Logout',
            8: 'Item Drop',
            9: 'Item Pickup'
        };
        
        const actionIcons = {
            0: '🧱',
            1: '💥',
            4: '💬',
            5: '⚙️',
            6: '✅',
            7: '❌',
            8: '📤',
            9: '📥'
        };
        
        const actionColors = {
            0: 'rgba(0, 212, 255, 0.8)',
            1: 'rgba(255, 56, 100, 0.8)',
            4: 'rgba(255, 217, 61, 0.8)',
            5: 'rgba(0, 255, 159, 0.8)',
            6: 'rgba(123, 47, 247, 0.8)',
            7: 'rgba(255, 107, 157, 0.8)',
            8: 'rgba(186, 85, 211, 0.8)',
            9: 'rgba(0, 191, 255, 0.8)'
        };
        
        function initCharts() {
            const chartOptions = {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: true,
                        position: 'bottom',
                        labels: {
                            color: '#94a3b8',
                            font: { family: 'Inter', size: 11, weight: '500' },
                            padding: 16,
                            usePointStyle: true,
                            pointStyle: 'circle'
                        }
                    },
                    tooltip: {
                        backgroundColor: 'rgba(18, 18, 26, 0.95)',
                        titleColor: '#fff',
                        bodyColor: '#94a3b8',
                        borderColor: 'rgba(148, 163, 184, 0.2)',
                        borderWidth: 1,
                        cornerRadius: 8,
                        padding: 12,
                        titleFont: { family: 'Inter', size: 13, weight: '600' },
                        bodyFont: { family: 'Inter', size: 12 },
                        displayColors: true,
                        boxPadding: 4
                    }
                }
            };
            
            // Action Distribution Chart - Doughnut
            const actionCtx = document.getElementById('actionChart');
            actionChart = new Chart(actionCtx, {
                type: 'doughnut',
                data: {
                    labels: [],
                    datasets: [{
                        data: [],
                        backgroundColor: [],
                        borderColor: '#12121a',
                        borderWidth: 2,
                        hoverBorderWidth: 3,
                        hoverBorderColor: '#fff'
                    }]
                },
                options: {
                    ...chartOptions,
                    cutout: '65%',
                    plugins: {
                        ...chartOptions.plugins,
                        legend: {
                            ...chartOptions.plugins.legend,
                            position: 'right'
                        }
                    }
                }
            });
            
            // Timeline Chart - Area Line
            const timelineCtx = document.getElementById('timelineChart');
            timelineChart = new Chart(timelineCtx, {
                type: 'line',
                data: {
                    labels: [],
                    datasets: [{
                        label: 'Actions',
                        data: [],
                        borderColor: '#00d4ff',
                        backgroundColor: (context) => {
                            const ctx = context.chart.ctx;
                            const gradient = ctx.createLinearGradient(0, 0, 0, 280);
                            gradient.addColorStop(0, 'rgba(0, 212, 255, 0.3)');
                            gradient.addColorStop(1, 'rgba(0, 212, 255, 0)');
                            return gradient;
                        },
                        borderWidth: 2.5,
                        fill: true,
                        tension: 0.4,
                        pointRadius: 0,
                        pointHoverRadius: 6,
                        pointHoverBackgroundColor: '#00d4ff',
                        pointHoverBorderColor: '#fff',
                        pointHoverBorderWidth: 2
                    }]
                },
                options: {
                    ...chartOptions,
                    interaction: {
                        mode: 'index',
                        intersect: false
                    },
                    scales: {
                        y: {
                            beginAtZero: true,
                            grid: { 
                                color: 'rgba(148, 163, 184, 0.08)',
                                drawBorder: false
                            },
                            ticks: { 
                                color: '#64748b',
                                font: { family: 'Inter', size: 11 },
                                padding: 8
                            }
                        },
                        x: {
                            grid: { 
                                display: false 
                            },
                            ticks: { 
                                color: '#64748b',
                                font: { family: 'Inter', size: 11 },
                                maxRotation: 0
                            }
                        }
                    }
                }
            });
        }
        
        function updateCharts(summaryData) {
            if (!summaryData || !actionChart) return;
            
            const counts = summaryData.counts || {};
            const labels = [];
            const data = [];
            const colors = [];
            
            Object.entries(counts).forEach(([action, count]) => {
                labels.push(actionLabels[action] || 'Unknown');
                data.push(count);
                colors.push(actionColors[action] || 'rgba(150, 150, 150, 0.8)');
            });
            
            actionChart.data.labels = labels;
            actionChart.data.datasets[0].data = data;
            actionChart.data.datasets[0].backgroundColor = colors;
            actionChart.update();
            
            // Update timeline (simplified - shows last 24 hours)
            updateTimeline();
        }
        
        function updateTimeline() {
            if (!allLogs.length || !timelineChart) return;
            
            const now = Math.floor(Date.now() / 1000);
            const hours = 24;
            const hourlyData = new Array(hours).fill(0);
            const labels = [];
            
            for (let i = hours - 1; i >= 0; i--) {
                const hour = new Date((now - i * 3600) * 1000).getHours();
                labels.push(`${'$'}{hour}:00`);
            }
            
            allLogs.forEach(log => {
                const hoursDiff = Math.floor((now - log.time) / 3600);
                if (hoursDiff < hours) {
                    hourlyData[hours - 1 - hoursDiff]++;
                }
            });
            
            timelineChart.data.labels = labels;
            timelineChart.data.datasets[0].data = hourlyData;
            timelineChart.update();
        }
        
        async function fetchStats() {
            try {
                const response = await fetch('/stats');
                const data = await response.json();
                document.getElementById('logQueue').textContent = data.logQueue.toLocaleString();
                document.getElementById('containerQueue').textContent = data.containerQueue.toLocaleString();
                document.getElementById('poolStatus').textContent = data.pool || 'All systems operational';
            } catch (error) {
                console.error('Failed to fetch stats:', error);
            }
        }
        
        async function fetchSummary() {
            try {
                const timeRange = document.getElementById('timeRange').value;
                const url = timeRange ? `/summary?last=${'$'}{timeRange}` : '/summary';
                const response = await fetch(url);
                const data = await response.json();
                document.getElementById('totalLogs').textContent = data.total.toLocaleString();
                updateCharts(data);
            } catch (error) {
                console.error('Failed to fetch summary:', error);
            }
        }
        
        async function fetchLogs() {
            const pageSize = document.getElementById('pageSize').value;
            const params = new URLSearchParams({
                page: currentPage,
                size: pageSize,
                ...currentFilters
            });
            
            try {
                const response = await fetch('/logs?' + params);
                const logs = await response.json();
                allLogs = logs;
                renderLogs(logs);
                updateTimeline();
            } catch (error) {
                console.error('Failed to fetch logs:', error);
                document.getElementById('logsContent').innerHTML = 
                    '<div class="empty-state"><div class="empty-state-icon">❌</div><h3>Failed to load logs</h3><p>Please check console for errors</p></div>';
            }
        }
        
        function renderLogs(logs) {
            const content = document.getElementById('logsContent');
            
            if (logs.length === 0) {
                content.innerHTML = `
                    <div class="empty-state">
                        <div class="empty-icon">📭</div>
                        <div class="empty-title">No logs found</div>
                        <div class="empty-text">Try adjusting your filters or check back later</div>
                    </div>
                `;
                return;
            }
            
            const table = `
                <table class="logs-table">
                    <thead>
                        <tr>
                            <th>Timestamp</th>
                            <th>Player</th>
                            <th>Action</th>
                            <th>Details</th>
                            <th>Location</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${'$'}{logs.map(log => `
                            <tr>
                                <td><span class="timestamp">${'$'}{new Date(log.time * 1000).toLocaleString('en-US', {
                                    month: 'short',
                                    day: 'numeric',
                                    hour: '2-digit',
                                    minute: '2-digit',
                                    second: '2-digit'
                                })}</span></td>
                                <td><span class="player-name">${'$'}{escapeHtml(log.player)}</span></td>
                                <td>
                                    <span class="action-badge action-${'$'}{log.action}">
                                        ${'$'}{actionIcons[log.action] || '❓'} ${'$'}{actionLabels[log.action] || 'Unknown'}
                                    </span>
                                </td>
                                <td><span class="detail-text" title="${'$'}{escapeHtml(log.detail || '')}">${'$'}{escapeHtml((log.detail || '-').substring(0, 60))}${'$'}{log.detail && log.detail.length > 60 ? '...' : ''}</span></td>
                                <td><span class="location-text">${'$'}{log.world} ${'$'}{log.x}, ${'$'}{log.y}, ${'$'}{log.z}</span></td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            `;
            
            content.innerHTML = table;
            updatePagination();
        }
        
        function updatePagination() {
            document.getElementById('pageInfo').textContent = `Page ${'$'}{currentPage + 1}`;
            document.getElementById('prevBtn').disabled = currentPage === 0;
        }
        
        function applyFilters() {
            currentPage = 0;
            currentFilters = {};
            
            const player = document.getElementById('playerFilter').value.trim();
            const action = document.getElementById('actionFilter').value;
            const timeRange = document.getElementById('timeRange').value;
            
            if (player) currentFilters.player = player;
            if (action) currentFilters.action = action;
            if (timeRange) {
                const now = Math.floor(Date.now() / 1000);
                currentFilters.from = now - (parseInt(timeRange) * 60);
                currentFilters.to = now;
            }
            
            fetchLogs();
            fetchSummary();
        }
        
        function clearFilters() {
            document.getElementById('playerFilter').value = '';
            document.getElementById('actionFilter').value = '';
            document.getElementById('timeRange').value = '1440';
            document.getElementById('pageSize').value = '50';
            currentFilters = {};
            currentPage = 0;
            fetchLogs();
            fetchSummary();
        }
        
        function nextPage() {
            currentPage++;
            fetchLogs();
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }
        
        function prevPage() {
            if (currentPage > 0) {
                currentPage--;
                fetchLogs();
                window.scrollTo({ top: 0, behavior: 'smooth' });
            }
        }
        
        function refreshData() {
            fetchStats();
            fetchSummary();
            fetchLogs();
        }
        
        function exportLogs() {
            if (!allLogs.length) {
                alert('No logs to export!');
                return;
            }
            
            const headers = ['Timestamp', 'Player', 'Action', 'Detail', 'World', 'X', 'Y', 'Z'];
            const rows = allLogs.map(log => [
                new Date(log.time * 1000).toISOString(),
                log.player,
                actionLabels[log.action] || 'Unknown',
                (log.detail || '').replace(/"/g, '""'),
                log.world,
                log.x,
                log.y,
                log.z
            ]);
            
            const csv = [
                headers.join(','),
                ...rows.map(row => row.map(cell => `"${'$'}{cell}"`).join(','))
            ].join('\n');
            
            const blob = new Blob([csv], { type: 'text/csv' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `actionlogs-${'$'}{new Date().toISOString().split('T')[0]}.csv`;
            a.click();
            URL.revokeObjectURL(url);
        }
        
        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
        
        // Initial load
        initCharts();
        refreshData();
        
        // Auto-refresh every 10 seconds
        setInterval(refreshData, 10000);
        
        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            if (e.key === 'r' && (e.ctrlKey || e.metaKey)) {
                e.preventDefault();
                refreshData();
            }
            if (e.key === 'ArrowLeft' && currentPage > 0) {
                prevPage();
            }
            if (e.key === 'ArrowRight') {
                nextPage();
            }
        });
    </script>
</body>
</html>
    """.trimIndent()
