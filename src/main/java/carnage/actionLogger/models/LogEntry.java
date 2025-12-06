package carnage.actionLogger.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import java.time.LocalDateTime;

public class LogEntry {
    public final String playerName, action, detail;
    public final Location location;
    public final LocalDateTime timestamp;

    public LogEntry(String playerName, String action, String detail, Location location) {
        this.playerName = playerName;
        this.action = action;
        this.detail = detail != null ? detail : "";
        this.location = location;
        this.timestamp = LocalDateTime.now();
    }

    public LogEntry(String playerName, String action, String detail, String world, int x, int y, int z, LocalDateTime timestamp) {
        this.playerName = playerName;
        this.action = action;
        this.detail = detail;
        this.timestamp = timestamp;
        this.location = world != null ? new Location(Bukkit.getWorld(world), x, y, z) : null;
    }

    public String getPlayerName() { return playerName; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public Location getLocation() { return location; }
    public LocalDateTime getTimestamp() { return timestamp; }
}