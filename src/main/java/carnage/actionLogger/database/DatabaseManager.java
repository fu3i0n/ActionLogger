package carnage.actionLogger.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;

public class DatabaseManager {

    private final String host, dbName, user, pass;
    private final int port;
    private HikariDataSource ds;

    public DatabaseManager(String host, int port, String dbName, String user, String pass) {
        this.host = host; this.port = port; this.dbName = dbName;
        this.user = user; this.pass = pass;
    }

    public void setup() throws SQLException {
        createDatabaseIfNeeded();

        var c = new HikariConfig();
        c.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName +
                "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true");
        c.setUsername(user);
        c.setPassword(pass);
        c.setMaximumPoolSize(25);
        c.setMinimumIdle(5);
        c.addDataSourceProperty("cachePrepStmts", "true");
        c.addDataSourceProperty("prepStmtCacheSize", "250");
        c.addDataSourceProperty("useServerPrepStmts", "true");
        c.addDataSourceProperty("useConfigs", "maxPerformance");

        ds = new HikariDataSource(c);
        createTables();
    }

    private void createDatabaseIfNeeded() throws SQLException {
        try (var conn = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/?useSSL=false", user, pass);
             var st = conn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private void createTables() throws SQLException {
        try (var c = ds.getConnection(); var s = c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS logs (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    time INT UNSIGNED NOT NULL,
                    playerName VARCHAR(16) NOT NULL,
                    action TINYINT NOT NULL,
                    detail VARCHAR(255),
                    world VARCHAR(50) NOT NULL,
                    x INT NOT NULL, y SMALLINT NOT NULL, z INT NOT NULL,
                    amount SMALLINT DEFAULT 1,
                    INDEX idx_time (time),
                    INDEX idx_player (playerName, time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS container_transactions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    time INT UNSIGNED NOT NULL,
                    playerName VARCHAR(16) NOT NULL,
                    action TINYINT NOT NULL,
                    container_type VARCHAR(50) NOT NULL,
                    material VARCHAR(100) NOT NULL,
                    amount SMALLINT NOT NULL,
                    world VARCHAR(50) NOT NULL,
                    x INT NOT NULL, y SMALLINT NOT NULL, z INT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        }
    }

    public Connection getConnection() throws SQLException { return ds.getConnection(); }
    public String getPoolStats() {
        var pool = ds.getHikariPoolMXBean();
        return String.format("Active: %d, Idle: %d, Total: %d, Waiting: %d",
                pool.getActiveConnections(), pool.getIdleConnections(),
                pool.getTotalConnections(), pool.getThreadsAwaitingConnection());
    }
    public void close() { if (ds != null) ds.close(); }
    public String getCurrentDatabase() { return dbName; }
}