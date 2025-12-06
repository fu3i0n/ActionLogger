package carnage.actionLogger.queue;

import carnage.actionLogger.database.DatabaseManager;
import carnage.actionLogger.models.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class LogQueue {

    private final BlockingQueue<LogEntry> logQueue = new LinkedBlockingQueue<>(100_000);
    private final BlockingQueue<ContainerTransaction> containerQueue = new LinkedBlockingQueue<>(100_000);
    private final DatabaseManager db;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ActionLogger-Writer");
        t.setDaemon(true);
        return t;
    });

    public LogQueue(DatabaseManager db, JavaPlugin plugin) {
        this.db = db;
        scheduler.scheduleAtFixedRate(this::flushAll, 1, 1, TimeUnit.SECONDS);
    }

    public void addLog(LogEntry e) { logQueue.offer(e); }
    public void addContainerTransaction(ContainerTransaction t) { containerQueue.offer(t); }

    private void flushAll() {
        flushLogs();
        flushContainers();
    }

    private void flushLogs() {
        var batch = new ArrayList<LogEntry>(500);
        logQueue.drainTo(batch, 500);
        if (batch.isEmpty()) return;

        try (var c = db.getConnection()) {
            c.setAutoCommit(false);
            try (var ps = c.prepareStatement(
                    "INSERT INTO logs (time,playerName,action,detail,world,x,y,z,amount) VALUES (?,?,?,?,?,?,?,?,?)")) {
                for (var e : batch) {
                    ps.setInt(1, (int)e.timestamp.atZone(java.time.ZoneId.systemDefault()).toEpochSecond());
                    ps.setString(2, e.playerName);
                    ps.setByte(3, actionToByte(e.action));
                    ps.setString(4, e.detail.length() > 255 ? e.detail.substring(0,255) : e.detail);
                    if (e.location != null && e.location.getWorld() != null) {
                        ps.setString(5, e.location.getWorld().getName());
                        ps.setInt(6, e.location.getBlockX());
                        ps.setShort(7, (short)e.location.getBlockY());
                        ps.setInt(8, e.location.getBlockZ());
                    } else {
                        ps.setString(5, "unknown"); ps.setInt(6,0); ps.setShort(7,(short)0); ps.setInt(8,0);
                    }
                    ps.setShort(9, (short)1);
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            }
        } catch (Exception ignored) {}
    }

    private void flushContainers() {
        var batch = new ArrayList<ContainerTransaction>(500);
        containerQueue.drainTo(batch, 500);
        if (batch.isEmpty()) return;

        try (var c = db.getConnection()) {
            c.setAutoCommit(false);
            try (var ps = c.prepareStatement(
                    "INSERT INTO container_transactions (time,playerName,action,container_type,material,amount,world,x,y,z) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                for (var t : batch) {
                    ps.setInt(1, t.time); ps.setString(2, t.playerName); ps.setByte(3, t.action);
                    ps.setString(4, t.containerType); ps.setString(5, t.material); ps.setShort(6, t.amount);
                    ps.setString(7, t.world); ps.setInt(8, t.x); ps.setShort(9, (short)t.y); ps.setInt(10, t.z);
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            }
        } catch (Exception ignored) {}
    }

    private byte actionToByte(String a) {
        return switch (a) {
            case "Block Placed" -> 0;
            case "Block Broken" -> 1;
            case "Chat" -> 4;
            case "Command" -> 5;
            case "Login" -> 6;
            case "Logout" -> 7;
            case "Item Drop" -> 8;
            case "Item Pickup" -> 9;
            default -> 99;
        };
    }

    public void flush() { flushAll(); }
    public void shutdown() { scheduler.shutdown(); flush(); }
    public Stats getStats() { return new Stats(logQueue.size(), containerQueue.size()); }
    public static record Stats(int logQueueSize, int containerQueueSize) {}
}