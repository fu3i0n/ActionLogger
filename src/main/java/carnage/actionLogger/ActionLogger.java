package carnage.actionLogger;

import carnage.actionLogger.database.DatabaseManager;
import carnage.actionLogger.listeners.*;
import carnage.actionLogger.queue.LogQueue;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class ActionLogger extends JavaPlugin {

    private static ActionLogger instance;
    private DatabaseManager database;
    private LogQueue logQueue;

    private final DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (!setupDatabase()) {
            getLogger().severe("Failed to connect to MySQL! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        logQueue = new LogQueue(database, this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerActionListeners(logQueue), this);
        pm.registerEvents(new ContainerListeners(logQueue), this);
        pm.registerEvents(new MiscListeners(logQueue), this);

        getCommand("logviewer").setExecutor(new LogViewerCommand(this));
        getCommand("logger").setExecutor(this);
        getCommand("dashboard").setExecutor(new ActionLoggerDashboard(this, database));

        getLogger().info("ActionLogger enabled – logging to " + database.getCurrentDatabase());
    }

    private boolean setupDatabase() {
        String host = getConfig().getString("mysql.host", "localhost");
        int port = getConfig().getInt("mysql.port", 3306);
        String base = getConfig().getString("mysql.database", "minecraft_logs");
        String user = getConfig().getString("mysql.username");
        String pass = getConfig().getString("mysql.password");

        String today = base + "_" + LocalDate.now().format(df);

        database = new DatabaseManager(host, port, today, user, pass);
        try {
            database.setup();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender s, org.bukkit.command.Command c, String l, String[] a) {
        if (!c.getName().equalsIgnoreCase("logger")) return false;
        if (a.length == 0) {
            s.sendMessage("§e/logger stats | flush | pool");
            return true;
        }
        return switch (a[0].toLowerCase()) {
            case "stats" -> {
                var st = logQueue.getStats();
                s.sendMessage("§eLog queue: §f" + st.logQueueSize());
                s.sendMessage("§eContainer queue: §f" + st.containerQueueSize());
                yield true;
            }
            case "flush" -> {
                logQueue.flush();
                s.sendMessage("§aFlushed!");
                yield true;
            }
            case "pool" -> {
                s.sendMessage("§e" + database.getPoolStats());
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public void onDisable() {
        if (logQueue != null) logQueue.shutdown();
        if (database != null) database.close();
    }

    public DatabaseManager getDatabase() { return database; }
    public static ActionLogger get() { return instance; }
}