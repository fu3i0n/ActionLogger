package cat.daisy.core

import cat.daisy.core.commands.staff.registerLogViewerCommand
import cat.daisy.core.commands.staff.registerLoggerCommand
import cat.daisy.core.database.DatabaseManager
import cat.daisy.core.events.ContainerListeners
import cat.daisy.core.events.MiscListeners
import cat.daisy.core.events.PlayerActionListeners
import cat.daisy.core.service.ActionLogService
import cat.daisy.core.service.ConfigService
import cat.daisy.core.service.DashboardService
import cat.daisy.core.utils.TextUtils
import cat.daisy.core.utils.TextUtils.log
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * ActionLogger - Production action logging plugin
 * Tracks player actions (blocks, chat, commands, items, etc.)
 * Provides GUI viewer and REST API
 */
class Core : JavaPlugin() {
    private var shuttingDown = false
    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var actionLogService: ActionLogService
    private var dashboardService: DashboardService? = null

    fun getActionLogService(): ActionLogService = actionLogService

    companion object {
        lateinit var instance: Core
            private set
    }

    override fun onLoad() {
        CommandAPI.onLoad(CommandAPIBukkitConfig(this).silentLogs(true))
    }

    override fun onEnable() {
        instance = this
        val startTime = System.currentTimeMillis()

        try {
            CommandAPI.onEnable()
            TextUtils.initialize(this)

            log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "INFO")
            log("  ActionLogger Pro - Production Ready", "SUCCESS")
            log("  Version: 1.0.0", "INFO")
            log("  Author: Daisy", "INFO")
            log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "INFO")

            if (!DatabaseManager.connect(dataFolder.absolutePath)) {
                log("❌ Database connection failed. Disabling plugin.", "ERROR")
                server.pluginManager.disablePlugin(this)
                return
            }

            setupConfigurations()
            initializeManagers()
            registerEvents()
            registerPlaceholders()
            checkDependencies()

            // Register commands AFTER everything is initialized
            registerCommands()

            // Start health monitoring
            startHealthMonitoring()

            val loadTime = System.currentTimeMillis() - startTime
            log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "SUCCESS")
            log("✅ ActionLogger enabled in ${loadTime}ms!", "SUCCESS")
            log("📊 Dashboard: http://localhost:${dashboardService?.let { "8080" } ?: "disabled"}", "INFO")
            log("⚡ Real-time logging active!", "SUCCESS")
            log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "SUCCESS")
        } catch (e: Exception) {
            log("❌ Critical error during initialization", "ERROR", e)
            server.pluginManager.disablePlugin(this)
        }
    }

    /**
     * Start background health monitoring task.
     * Monitors queue sizes, database health, and performance metrics.
     */
    private fun startHealthMonitoring() {
        server.scheduler.runTaskTimerAsynchronously(
            this,
            Runnable {
                try {
                    val (logQueue, containerQueue) = actionLogService.stats()

                    // Warn if queues are getting full
                    if (logQueue > 200_000) {
                        log("⚠️ Warning: Log queue is high ($logQueue). Consider optimizing.", "WARNING")
                    }
                    if (containerQueue > 200_000) {
                        log("⚠️ Warning: Container queue is high ($containerQueue). Consider optimizing.", "WARNING")
                    }

                    // Log periodic health status
                    if (logQueue > 1000 || containerQueue > 1000) {
                        log("💓 Health: Logs=$logQueue, Containers=$containerQueue, DB=${DatabaseManager.getPoolStats()}", "INFO")
                    }
                } catch (e: Exception) {
                    log("Health monitoring error: ${e.message}", "WARNING")
                }
            },
            20L * 60,
            20L * 60,
        ) // Every 60 seconds
    }

    override fun onDisable() {
        if (shuttingDown) return
        shuttingDown = true

        try {
            log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "INFO")
            log("Beginning graceful shutdown...", "INFO")

            if (::actionLogService.isInitialized) {
                val (finalLogs, finalContainers) = actionLogService.stats()
                log("Final stats - Logs: $finalLogs, Containers: $finalContainers", "INFO")
                actionLogService.shutdown()
                log("ActionLogService shutdown complete", "SUCCESS")
            }

            dashboardService?.stop()
            log("Dashboard service stopped", "SUCCESS")

            CommandAPI.onDisable()
            pluginScope.cancel("Plugin shutting down")

            DatabaseManager.disconnect()
            log("Database disconnected", "SUCCESS")

            log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "SUCCESS")
            log("✅ ActionLogger disabled successfully", "SUCCESS")
            log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "SUCCESS")
        } catch (e: Exception) {
            log("❌ Error during shutdown", "ERROR", e)
        }
    }

    private fun setupConfigurations() {
        try {
            dataFolder.mkdirs()
            listOf(
                File(dataFolder, "modules"),
                File(dataFolder, "modules/${ConfigService.COMMANDS}"),
                File(dataFolder, "modules/${ConfigService.EVENTS}"),
                File(dataFolder, "modules/${ConfigService.GUIS}"),
            ).forEach { it.mkdirs() }
            ConfigService.loadConfigs()
            log("Configurations loaded", "SUCCESS")
        } catch (e: Exception) {
            log("Failed to setup configurations", "ERROR", e)
            throw e
        }
    }

    private fun initializeManagers() {
        try {
            actionLogService = ActionLogService(pluginScope)

            // Dashboard config
            runCatching {
                val cfg = ConfigService.loadConfig(this, "dashboard.yml", validate = false)
                val port = cfg.getInt("port", 8080)
                val token = cfg.getString("token")?.takeIf { it.isNotBlank() }
                dashboardService = DashboardService(port, token).also { it.start() }
            }.onFailure { e ->
                log("Dashboard failed to start", "WARNING", e)
            }

            log("Managers initialized", "SUCCESS")
        } catch (e: Exception) {
            log("Failed to initialize managers", "ERROR", e)
            throw e
        }
    }

    private fun registerEvents() {
        try {
            server.pluginManager.registerEvents(PlayerActionListeners(actionLogService), this)
            server.pluginManager.registerEvents(ContainerListeners(actionLogService), this)
            server.pluginManager.registerEvents(MiscListeners(actionLogService), this)
            log("Events registered", "SUCCESS")
        } catch (e: Exception) {
            log("Failed to register events", "ERROR", e)
            throw e
        }
    }

    private fun registerCommands() {
        runCatching {
            val commands =
                listOf(
                    ::registerLogViewerCommand,
                    ::registerLoggerCommand,
                )

            commands.forEach { it() }
            log("${commands.size} commands registered", "SUCCESS")
        }.onFailure { e ->
            log("Command registration failed", "ERROR", e)
            throw e
        }
    }

    private fun registerPlaceholders() {
        // Optional: PlaceholderAPI integration can be added here if needed
    }

    private fun checkDependencies() {
        // ActionLogger has minimal dependencies - all bundled or provided by Paper
    }
}
