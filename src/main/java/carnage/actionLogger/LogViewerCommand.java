package carnage.actionLogger;

import carnage.actionLogger.models.LogEntry;
import carnage.actionLogger.gui.LogViewerGUI;
import org.bukkit.entity.Player;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LogViewerCommand implements org.bukkit.command.CommandExecutor {
    private final ActionLogger plugin;

    public LogViewerCommand(ActionLogger plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender s, org.bukkit.command.Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;
        if (a.length == 0) {
            p.sendMessage("§cUsage: /logviewer <player>");
            return true;
        }

        String target = a[0];
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            var logs = loadLogs(target);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (logs.isEmpty()) p.sendMessage("§cNo logs for " + target);
                else LogViewerGUI.open(p, logs, 0);
            });
        });
        return true;
    }

    private List<LogEntry> loadLogs(String player) {
        var list = new ArrayList<LogEntry>();
        try (var c = plugin.getDatabase().getConnection();
             var ps = c.prepareStatement("SELECT * FROM logs WHERE playerName = ? ORDER BY time DESC LIMIT 1000")) {
            ps.setString(1, player);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new LogEntry(
                            rs.getString("playerName"),
                            actionName(rs.getByte("action")),
                            rs.getString("detail"),
                            rs.getString("world"),
                            rs.getInt("x"), rs.getShort("y"), rs.getInt("z"),
                            java.time.Instant.ofEpochSecond(rs.getInt("time"))
                                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    ));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    private String actionName(byte b) {
        return switch (b) {
            case 0 -> "Block Placed";
            case 1 -> "Block Broken";
            case 4 -> "Chat";
            case 5 -> "Command";
            case 6 -> "Login";
            case 7 -> "Logout";
            case 8 -> "Item Drop";
            case 9 -> "Item Pickup";
            default -> "Unknown";
        };
    }
}