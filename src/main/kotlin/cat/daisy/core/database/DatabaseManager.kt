package cat.daisy.core.database

import cat.daisy.core.database.tables.ActionLogsTable
import cat.daisy.core.database.tables.ContainerTransactionsTable
import cat.daisy.core.utils.Constants
import cat.daisy.core.utils.TextUtils.log
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

/**
 * Centralized database connection and transaction management.
 * Handles SQLite database setup, connection pooling via HikariCP, and all DB operations.
 */
object DatabaseManager {
    private lateinit var dataSource: HikariDataSource

    var isConnected = false
        private set

    fun connect(pluginFolder: String): Boolean =
        try {
            val dbFile = File(pluginFolder, "database.db")
            dbFile.parentFile?.mkdirs()

            dataSource = createDataSource(dbFile.absolutePath)
            Database.connect(dataSource)

            createAllTables()

            isConnected = true
            log("Database connected successfully (SQLite: ${dbFile.absolutePath})", "SUCCESS")
            true
        } catch (e: Exception) {
            log("Database connection failed: ${e.message}", "ERROR", e)
            isConnected = false
            false
        }

    private fun createDataSource(dbPath: String): HikariDataSource {
        val config =
            HikariConfig().apply {
                driverClassName = "org.sqlite.JDBC"
                jdbcUrl = "jdbc:sqlite:$dbPath"
                maximumPoolSize = Constants.Database.MAX_POOL_SIZE
                minimumIdle = Constants.Database.MIN_IDLE_CONNECTIONS
                connectionTimeout = Constants.Database.CONNECTION_TIMEOUT_MS
                idleTimeout = Constants.Database.IDLE_TIMEOUT_MS
                maxLifetime = Constants.Database.MAX_LIFETIME_MS

                addDataSourceProperty("journal_mode", "WAL")
                addDataSourceProperty("synchronous", "NORMAL")
                addDataSourceProperty("cache_size", Constants.Database.CACHE_SIZE)
                addDataSourceProperty("foreign_keys", "ON")
                addDataSourceProperty("busy_timeout", Constants.Database.BUSY_TIMEOUT_MS.toString())

                poolName = "ActionLoggerPool"
                connectionTestQuery = "SELECT 1"
                leakDetectionThreshold = Constants.Database.LEAK_DETECTION_THRESHOLD_MS
                isAutoCommit = true
            }

        return HikariDataSource(config)
    }

    private fun createAllTables() {
        transaction {
            SchemaUtils.create(
                ActionLogsTable,
                ContainerTransactionsTable,
            )
        }
    }

    fun disconnect() {
        if (::dataSource.isInitialized) {
            try {
                transaction { TransactionManager.current().commit() }
            } catch (e: Exception) {
                log("Error during final commit: ${e.message}", "WARNING")
            } finally {
                isConnected = false
                dataSource.close()
                log("Database disconnected", "INFO")
            }
        }
    }

    /**
     * Execute a database write operation within a transaction with retry logic.
     * Use for INSERT, UPDATE, DELETE operations.
     * Automatically retries on transient failures (SQLite busy, etc.)
     */
    fun <T> dbWrite(
        maxRetries: Int = 3,
        block: () -> T,
    ): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return transaction { block() }
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1 && isTransientError(e)) {
                    Thread.sleep((attempt + 1) * 50L) // Exponential backoff
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: IllegalStateException("Transaction failed after $maxRetries attempts")
    }

    /**
     * Execute a database read operation within a transaction.
     * Use for SELECT operations (optimized, may use read-only connections if available).
     */
    fun <T> dbQuery(block: () -> T): T =
        try {
            transaction { block() }
        } catch (e: Exception) {
            log("Database query error: ${e.message}", "WARNING", e)
            throw e
        }

    /**
     * Check if an exception is a transient error that can be retried.
     */
    private fun isTransientError(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""
        return message.contains("database is locked") ||
            message.contains("busy") ||
            message.contains("timeout")
    }

    /**
     * Get detailed HikariCP connection pool statistics.
     */
    fun getPoolStats(): String =
        if (::dataSource.isInitialized) {
            val pool = dataSource.hikariPoolMXBean
            val active = pool.activeConnections
            val idle = pool.idleConnections
            val total = pool.totalConnections
            val waiting = pool.threadsAwaitingConnection
            "Active: $active, Idle: $idle, Total: $total, Waiting: $waiting"
        } else {
            "Pool not initialized"
        }
}
