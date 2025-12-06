package carnage.actionLogger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import carnage.actionLogger.database.DatabaseManager;
import carnage.actionLogger.models.LogEntry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ActionLoggerDashboard implements CommandExecutor {

    private static final Logger LOGGER = Logger.getLogger(ActionLoggerDashboard.class.getName());
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private HttpServer server;
    private final String dashboardUrl;

    public ActionLoggerDashboard(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        this.dashboardUrl = plugin.getConfig().getString("dashboard.url", "http://localhost:8080");
        startServer();
        registerCommand();
    }

    private void startServer() {
        int port = plugin.getConfig().getInt("dashboard.port", 8080);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new DashboardHandler());
            server.createContext("/logs", new LogsHandler());
            server.createContext("/players", new PlayerListHandler());
            server.createContext("/actions", new ActionListHandler());
            server.setExecutor(null);
            server.start();
            LOGGER.info("Dashboard server started at " + dashboardUrl + " on port " + port);
        } catch (IOException e) {
            LOGGER.severe("Failed to start dashboard server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerCommand() {
        var command = plugin.getCommand("dashboard");
        if (command != null) {
            command.setExecutor(this);
        } else {
            LOGGER.severe("Failed to register dashboard command");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Component clickable = Component.text("[Action Logs Dashboard]")
                .clickEvent(ClickEvent.openUrl(dashboardUrl))
                .hoverEvent(HoverEvent.showText(Component.text("Click to open the action logs dashboard")));
        if (sender instanceof Player player) {
            player.sendMessage(Component.text("Open the action logs dashboard: ").append(clickable));
        } else {
            sender.sendMessage("Dashboard available at: " + dashboardUrl);
        }
        return true;
    }

    public void stopServer() {
        if (server != null) {
            server.stop(0);
            LOGGER.info("Dashboard server stopped");
        }
    }

    class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Action Logger Dashboard</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        :root {
            --bg-primary: #0f172a;
            --bg-secondary: #1e293b;
            --bg-tertiary: #334155;
            --accent: #3b82f6;
            --accent-hover: #2563eb;
            --success: #10b981;
            --danger: #ef4444f4;
            --warning: #f59e0b;
            --text-primary: #f1f5f9;
            --text-secondary: #94a3b8;
            --border: #334155;
        }
        body { font-family: 'Inter', sans-serif; background: var(--bg-primary); color: var(--text-primary); line-height: 1.6; }
        .container { max-width: 1600px; margin: 0 auto; padding: 2rem; }
        header { background: linear-gradient(135deg, var(--accent) 0%, #8b5cf6 100%); padding: 2rem; border-radius: 16px; margin-bottom: 2rem; box-shadow: 0 20px 60px rgba(59, 130, 246, 0.3); }
        h1 { font-size: 2.5rem; font-weight: 700; margin-bottom: 0.5rem; }
        .subtitle { font-size: 1rem; opacity: 0.9; }
        .controls { background: var(--bg-secondary); padding: 1.5rem; border-radius: 12px; border: 1px solid var(--border); margin-bottom: 2rem; }
        .controls-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 1rem; }
        .control-group { display: flex; flex-direction: column; }
        label { font-size: 0.875rem; color: var(--text-secondary); margin-bottom: 0.5rem; font-weight: 500; }
        input, select { background: var(--bg-tertiary); border: 1px solid var(--border); color: var(--text-primary); padding: 0.75rem; border-radius: 8px; font-family: inherit; font-size: 0.875rem; transition: border-color 0.2s, box-shadow 0.2s; }
        input:focus, select:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1); }
        .button-group { display: flex; gap: 0.75rem; flex-wrap: wrap; align-items: center; }
        button { background: var(--accent); color: white; border: none; padding: 0.75rem 1.5rem; border-radius: 8px; font-weight: 500; cursor: pointer; transition: all 0.2s; font-size: 0.875rem; }
        button:hover { background: var(--accent-hover); transform: translateY(-1px); box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4); }
        button:active { transform: translateY(0); }
        button:disabled { opacity: 0.5; cursor: not-allowed; transform: none; }
        button.secondary { background: var(--bg-tertiary); }
        button.secondary:hover { background: var(--border); }
        .table-container { background: var(--bg-secondary); border-radius: 12px; border: 1px solid var(--border); overflow: hidden; }
        table { width: 100%; border-collapse: collapse; }
        thead { background: var(--bg-tertiary); }
        th { text-align: left; padding: 1rem; font-weight: 600; font-size: 0.875rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-secondary); }
        td { padding: 1rem; border-top: 1px solid var(--border); font-size: 0.875rem; }
        tbody tr { transition: background 0.2s; }
        tbody tr:hover { background: var(--bg-tertiary); }
        tbody tr.row-break { border-left: 3px solid var(--success); }
        .action-badge { display: inline-block; padding: 0.25rem 0.75rem; border-radius: 6px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; }
        .badge-place { background: rgba(16, 185, 129, 0.2); color: var(--success); }
        .badge-break { background: rgba(16, 185, 129, 0.2); color: var(--success); }
        .badge-drop { background: rgba(245, 158, 11, 0.2); color: var(--warning); }
        .badge-pickup { background: rgba(59, 130, 246, 0.2); color: var(--accent); }
        .badge-command { background: rgba(139, 92, 246, 0.2); color: #8b5cf6; }
        .badge-default { background: rgba(148, 163, 184, 0.2); color: var(--text-secondary); }
        .location-link { color: var(--accent); cursor: pointer; text-decoration: underline; transition: color 0.2s; }
        .location-link:hover { color: var(--accent-hover); }
        .pagination { display: flex; justify-content: center; align-items: center; gap: 1rem; margin-top: 1.5rem; padding: 1.5rem; }
        .page-info { color: var(--text-secondary); font-size: 0.875rem; }
        .toast { position: fixed; bottom: 2rem; right: 2rem; background: var(--success); color: white; padding: 1rem 1.5rem; border-radius: 8px; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3); display: none; align-items: center; gap: 0.75rem; font-weight: 500; animation: slideIn 0.3s ease; z-index: 1000; }
        @keyframes slideIn { from { transform: translateX(400px); opacity: 0; } to { transform: translateX(0); opacity: 1; } }
        .loading { text-align: center; padding: 3rem; color: var(--text-secondary); }
        .spinner { border: 3px solid var(--border); border-top: 3px solid var(--accent); border-radius: 50%; width: 40px; height: 40px; animation: spin 1s linear infinite; margin: 0 auto 1rem; }
        @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
        .refresh-indicator { display: inline-flex; align-items: center; gap: 0.5rem; color: var(--text-secondary); font-size: 0.875rem; }
        .refresh-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--success); animation: pulse 2s ease-in-out infinite; }
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
        .empty-state { text-align: center; padding: 4rem 2rem; color: var(--text-secondary); }
    </style>
</head>
<body>
    <div class="container">
        <header><h1>Action Logger Dashboard</h1><div class="subtitle">Real-time server activity monitoring</div></header>
        <div class="controls">
            <div class="controls-grid">
                <div class="control-group"><label>Player</label><input type="text" id="playerSearch" placeholder="Search player..."></div>
                <div class="control-group"><label>Action</label><select id="actionFilter"><option value="">All Actions</option></select></div>
                <div class="control-group"><label>Item/Container</label><input type="text" id="itemSearch" placeholder="Search item..."></div>
                <div class="control-group"><label>Page Size</label><input type="number" id="pageSize" value="25" min="10" max="100"></div>
            </div>
            <div class="button-group">
                <button id="searchBtn">Search</button>
                <button id="refreshBtn" class="secondary">Refresh</button>
                <button id="sortBtn" class="secondary">Latest First</button>
                <button id="clearBtn" class="secondary">Clear Filters</button>
                <span class="refresh-indicator"><span class="refresh-dot"></span>Auto-refresh: <span id="autoRefreshStatus">ON</span></span>
            </div>
        </div>
        <div class="table-container">
            <table>
                <thead><tr><th>Player</th><th>Action</th><th>Details</th><th>Location</th><th>Timestamp</th></tr></thead>
                <tbody id="logsTable"><tr><td colspan="5"><div class="loading"><div class="spinner"></div>Loading logs...</div></td></tr></tbody>
            </table>
        </div>
        <div class="pagination">
            <button id="prevBtn" class="secondary">Previous</button>
            <span class="page-info" id="pageInfo">Page 1</span>
            <button id="nextBtn" class="secondary">Next</button>
        </div>
        <div id="toast" class="toast">Success!<span id="toastText">Success!</span></div>
    </div>
    <script>
        let page = 1;
        let size = 25;
        let total = 0;
        let sort = 'DESC';
        let autoRefresh = true;
        let refreshInterval;
       
        const toast = document.getElementById('toast');
        const toastText = document.getElementById('toastText');
       
        function showToast(message) {
            toastText.textContent = message;
            toast.style.display = 'flex';
            setTimeout(() => toast.style.display = 'none', 3000);
        }
       
        function copyToClipboard(text) {
            navigator.clipboard.writeText(text)
                .then(() => showToast('Copied to clipboard!'))
                .catch(() => showToast('Failed to copy'));
        }
       
        function getActionBadgeClass(action) {
            if (action.includes('Place')) return 'badge-place';
            if (action.includes('Break')) return 'badge-break';
            if (action.includes('Drop')) return 'badge-drop';
            if (action.includes('Pickup')) return 'badge-pickup';
            if (action.includes('Command')) return 'badge-command';
            return 'badge-default';
        }
       
        function formatTimestamp(timestamp) {
            const d = new Date(timestamp);
            const pad = (n) => n < 10 ? '0' + n : n;
            return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' +
                   pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
        }
       
        async function loadLogs(pageNum = page) {
            const player = document.getElementById('playerSearch').value;
            const action = document.getElementById('actionFilter').value;
            const item = document.getElementById('itemSearch').value;
           
            try {
                const response = await fetch(`/logs?player=${encodeURIComponent(player)}&action=${encodeURIComponent(action)}&itemContainer=${encodeURIComponent(item)}&page=${pageNum}&size=${size}&sort=${sort}`);
                const data = await response.json();
                const tbody = document.getElementById('logsTable');
                tbody.innerHTML = '';
               
                if (data.logs.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="5"><div class="empty-state">No logs found</div></td></tr>';
                } else {
                    data.logs.forEach(log => {
                        const tr = document.createElement('tr');
                        const badgeClass = getActionBadgeClass(log.action);
                        if (log.action.includes('Break')) tr.className = 'row-break';
                        const location = log.location === 'Unknown' ?
                            '<span style="color:var(--text-secondary);">Unknown</span>' :
                            `<a class="location-link" onclick="copyToClipboard('${log.location}')">${log.location}</a>`;
                        tr.innerHTML = `
                            <td><strong>${log.playerName}</strong></td>
                            <td><span class="action-badge ${badgeClass}">${log.action}</span></td>
                            <td>${log.detail}</td>
                            <td>${location}</td>
                            <td>${formatTimestamp(log.timestamp)}</td>
                        `;
                        tbody.appendChild(tr);
                    });
                }
               
                total = data.total;
                page = pageNum;
                document.getElementById('pageInfo').textContent = `Page ${page} of ${Math.ceil(total / size) || 1}`;
                document.getElementById('prevBtn').disabled = page === 1;
                document.getElementById('nextBtn').disabled = page * size >= total;
            } catch (error) {
                console.error('Load error:', error);
                showToast('Failed to load logs');
            }
        }
       
        async function loadActions() {
            try {
                const response = await fetch('/actions');
                const actions = await response.json();
                const select = document.getElementById('actionFilter');
                actions.forEach(action => {
                    const option = document.createElement('option');
                    option.value = action;
                    option.textContent = action;
                    select.appendChild(option);
                });
            } catch (error) {
                console.error('Actions error:', error);
            }
        }
       
        function setupAutoRefresh() {
            if (autoRefresh) {
                refreshInterval = setInterval(() => {
                    loadLogs(page);
                }, 5000);
            }
        }
       
        document.getElementById('searchBtn').addEventListener('click', () => {
            size = parseInt(document.getElementById('pageSize').value) || 25;
            loadLogs(1);
        });
       
        document.getElementById('refreshBtn').addEventListener('click', () => {
            loadLogs(page);
            showToast('Refreshed!');
        });
       
        document.getElementById('sortBtn').addEventListener('click', () => {
            sort = sort === 'DESC' ? 'ASC' : 'DESC';
            document.getElementById('sortBtn').textContent = sort === 'DESC' ? 'Latest First' : 'Oldest First';
            loadLogs(1);
        });
       
        document.getElementById('clearBtn').addEventListener('click', () => {
            document.getElementById('playerSearch').value = '';
            document.getElementById('actionFilter').value = '';
            document.getElementById('itemSearch').value = '';
            loadLogs(1);
        });
       
        document.getElementById('prevBtn').addEventListener('click', () => {
            if (page > 1) loadLogs(page - 1);
        });
       
        document.getElementById('nextBtn').addEventListener('click', () => {
            if (page * size < total) loadLogs(page + 1);
        });
       
        document.querySelectorAll('input').forEach(input => {
            input.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') document.getElementById('searchBtn').click();
            });
        });
       
        // Initialize
        loadActions();
        loadLogs(1);
        setupAutoRefresh();
    </script>
</body>
</html>
""";

            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    class LogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String playerName = "";
            String action = "";
            String itemContainer = "";
            int page = 1;
            int size = 25;
            String sort = "DESC";
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2) {
                        String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                        switch (pair[0]) {
                            case "player": playerName = value; break;
                            case "action": action = value; break;
                            case "itemContainer": itemContainer = value; break;
                            case "page": page = Integer.parseInt(value); break;
                            case "size": size = Integer.parseInt(value); break;
                            case "sort": sort = value.equalsIgnoreCase("ASC") ? "ASC" : "DESC"; break;
                        }
                    }
                }
            }
            List<LogEntry> logs = fetchLogs(playerName, action, itemContainer, page, size, sort);
            int total = fetchLogCount(playerName, action, itemContainer);
            String json = "{\"logs\":[" + logs.stream().map(log ->
                    String.format("{\"playerName\":\"%s\",\"action\":\"%s\",\"detail\":\"%s\",\"location\":\"%s\",\"timestamp\":\"%s\"}",
                            escapeJson(log.getPlayerName()),
                            escapeJson(log.getAction()),
                            escapeJson(log.getDetail()),
                            escapeJson(formatTeleportCommand(log.getLocation())),
                            log.getTimestamp().toString())
            ).collect(Collectors.joining(",")) + "],\"total\":" + total + "}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes());
            }
        }

        private String getActionName(byte actionType) {
            return switch (actionType) {
                case 0 -> "Block Placed";
                case 1 -> "Block Broken";
                case 2 -> "Container Open";
                case 3 -> "Container Close";
                case 4 -> "Chat";
                case 5 -> "Command";
                case 6 -> "Login";
                case 7 -> "Logout";
                case 8 -> "Item Drop";
                case 9 -> "Item Pickup";
                default -> "Unknown";
            };
        }

        private List<LogEntry> fetchLogs(String playerName, String action, String itemContainer, int page, int size, String sort) {
            List<LogEntry> logs = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT * FROM logs");
            List<String> conditions = new ArrayList<>();
            if (!playerName.isEmpty()) conditions.add("playerName = ?");
            if (!action.isEmpty()) conditions.add("action = ?");
            if (!itemContainer.isEmpty()) conditions.add("detail LIKE ?");
            if (!conditions.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" AND ", conditions));
            }
            sql.append(" ORDER BY time ").append(sort).append(" LIMIT ? OFFSET ?");
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                int paramIndex = 1;
                if (!playerName.isEmpty()) stmt.setString(paramIndex++, playerName);
                if (!action.isEmpty()) stmt.setString(paramIndex++, action);
                if (!itemContainer.isEmpty()) stmt.setString(paramIndex++, "%" + itemContainer + "%");
                stmt.setInt(paramIndex++, size);
                stmt.setInt(paramIndex, (page - 1) * size);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        logs.add(new LogEntry(
                                rs.getString("playerName"),
                                getActionName(rs.getByte("action")),
                                rs.getString("detail"),
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z"),
                                java.time.Instant.ofEpochSecond(rs.getInt("time"))
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDateTime()
                        ));
                    }
                }
            } catch (SQLException e) {
                LOGGER.severe("Failed to fetch logs: " + e.getMessage());
                e.printStackTrace();
            }
            return logs;
        }

        private int fetchLogCount(String playerName, String action, String itemContainer) {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM logs");
            List<String> conditions = new ArrayList<>();
            if (!playerName.isEmpty()) conditions.add("playerName = ?");
            if (!action.isEmpty()) conditions.add("action = ?");
            if (!itemContainer.isEmpty()) conditions.add("detail LIKE ?");
            if (!conditions.isEmpty()) {
                sql.append(" WHERE ").append(String.join(" AND ", conditions));
            }
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                int paramIndex = 1;
                if (!playerName.isEmpty()) stmt.setString(paramIndex++, playerName);
                if (!action.isEmpty()) stmt.setString(paramIndex++, action);
                if (!itemContainer.isEmpty()) stmt.setString(paramIndex++, "%" + itemContainer + "%");
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                LOGGER.severe("Failed to fetch log count: " + e.getMessage());
            }
            return 0;
        }

        private String formatTeleportCommand(org.bukkit.Location location) {
            if (location == null || location.getWorld() == null) {
                return "Unknown";
            }
            return String.format("/tp %d %d %d",
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ());
        }

        private String escapeJson(String str) {
            if (str == null) return "";
            return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        }
    }

    class PlayerListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<String> players = new ArrayList<>();
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT DISTINCT playerName FROM logs WHERE playerName IS NOT NULL");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String playerName = rs.getString("playerName");
                    if (playerName != null) {
                        players.add(playerName);
                    }
                }
            } catch (SQLException e) {
                LOGGER.severe("Failed to fetch players: " + e.getMessage());
            }
            String json = "[" + players.stream().map(name -> "\"" + escapeJson(name) + "\"").collect(Collectors.joining(",")) + "]";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes());
            }
        }

        private String escapeJson(String str) {
            if (str == null) return "";
            return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        }
    }

    class ActionListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<String> actions = new ArrayList<>();
            try (Connection conn = database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT DISTINCT action FROM logs WHERE action IS NOT NULL");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String actionName = rs.getString("action");
                    if (actionName != null) {
                        actions.add(actionName);
                    }
                }
            } catch (SQLException e) {
                LOGGER.severe("Failed to fetch actions: " + e.getMessage());
            }
            String json = "[" + actions.stream().map(action -> "\"" + escapeJson(action) + "\"").collect(Collectors.joining(",")) + "]";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes());
            }
        }

        private String escapeJson(String str) {
            if (str == null) return "";
            return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        }
    }
}