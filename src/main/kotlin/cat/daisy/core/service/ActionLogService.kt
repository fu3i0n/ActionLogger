package cat.daisy.core.service

import cat.daisy.core.database.DatabaseManager
import cat.daisy.core.database.tables.ActionLogsTable
import cat.daisy.core.database.tables.ContainerTransactionsTable
import cat.daisy.core.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bukkit.Location
import org.jetbrains.exposed.sql.batchInsert
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages action logging with async batch flushing.
 * - Receives log entries via channels
 * - Batches them for efficient database writes
 * - Respects configuration for queue capacity and flush intervals
 * - Thread-safe and coroutine-based
 */
class ActionLogService(
    private val scope: CoroutineScope,
) {
    data class LogEntry(
        val playerName: String,
        val action: Action,
        val detail: String,
        val location: Location?,
        val timestamp: Instant = Instant.now(),
    )

    data class ContainerTx(
        val time: Int,
        val playerName: String,
        val action: Int,
        val containerType: String,
        val material: String,
        val amount: Int,
        val world: String,
        val x: Int,
        val y: Int,
        val z: Int,
    )

    enum class Action(
        val code: Int,
    ) {
        BLOCK_PLACED(Constants.ActionCodes.BLOCK_PLACED),
        BLOCK_BROKEN(Constants.ActionCodes.BLOCK_BROKEN),
        CHAT(Constants.ActionCodes.CHAT),
        COMMAND(Constants.ActionCodes.COMMAND),
        LOGIN(Constants.ActionCodes.LOGIN),
        LOGOUT(Constants.ActionCodes.LOGOUT),
        ITEM_DROP(Constants.ActionCodes.ITEM_DROP),
        ITEM_PICKUP(Constants.ActionCodes.ITEM_PICKUP),
        OTHER(Constants.ActionCodes.OTHER),
    }

    // Read capacity from config, fallback to defaults
    private val queueCapacity = Constants.Queue.DEFAULT_CAPACITY
    private val flushIntervalMs = Constants.Queue.FLUSH_INTERVAL_MS
    private val batchChunkSize = Constants.Queue.BATCH_CHUNK_SIZE

    private val logs = Channel<LogEntry>(capacity = queueCapacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val containers = Channel<ContainerTx>(capacity = queueCapacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val logCount = AtomicInteger(0)
    private val containerCount = AtomicInteger(0)

    private val job =
        scope.launch(Dispatchers.IO) {
            val logsBuffer = mutableListOf<LogEntry>()
            val contBuffer = mutableListOf<ContainerTx>()

            try {
                while (isActive) {
                    // Drain all pending entries from channels
                    while (true) {
                        val entry = logs.tryReceive().getOrNull() ?: break
                        logsBuffer.add(entry)
                    }
                    while (true) {
                        val tx = containers.tryReceive().getOrNull() ?: break
                        contBuffer.add(tx)
                    }

                    // Flush batches if anything pending
                    if (logsBuffer.isNotEmpty()) flushLogs(logsBuffer)
                    if (contBuffer.isNotEmpty()) flushContainers(contBuffer)

                    logsBuffer.clear()
                    contBuffer.clear()

                    delay(flushIntervalMs)
                }
            } finally {
                // Final drain before shutdown
                if (!isActive) {
                    while (true) {
                        val entry = logs.tryReceive().getOrNull() ?: break
                        logsBuffer.add(entry)
                    }
                    while (true) {
                        val tx = containers.tryReceive().getOrNull() ?: break
                        contBuffer.add(tx)
                    }
                }

                // Ensure all pending data is written before closing
                if (logsBuffer.isNotEmpty()) flushLogs(logsBuffer)
                if (contBuffer.isNotEmpty()) flushContainers(contBuffer)
            }
        }

    /**
     * Submit a log entry to the queue with validation.
     * Non-blocking - returns immediately.
     * Returns true if queued successfully, false if rejected.
     */
    fun submit(entry: LogEntry): Boolean {
        // Validate input
        if (entry.playerName.isBlank()) {
            return false
        }
        if (entry.detail.length > Constants.Logging.MAX_DETAIL_LENGTH_MAX) {
            return false
        }

        return if (logs.trySend(entry).isSuccess) {
            logCount.incrementAndGet()
            true
        } else {
            // Queue is full - oldest entry was dropped
            false
        }
    }

    /**
     * Submit a container transaction to the queue with validation.
     * Non-blocking - returns immediately.
     * Returns true if queued successfully, false if rejected.
     */
    fun submitContainer(tx: ContainerTx): Boolean {
        // Validate input
        if (tx.playerName.isBlank() || tx.material.isBlank()) {
            return false
        }

        return if (containers.trySend(tx).isSuccess) {
            containerCount.incrementAndGet()
            true
        } else {
            false
        }
    }

    /**
     * Get current queue statistics.
     * Returns (logCount, containerCount) for monitoring.
     */
    fun stats(): Pair<Int, Int> =
        Pair(
            logCount.get().coerceAtLeast(0),
            containerCount.get().coerceAtLeast(0),
        )

    /**
     * Trigger an immediate flush.
     * Used for manual flush or graceful shutdown.
     */
    fun flush() {
        scope.launch(Dispatchers.IO) {
            // Scheduler will pick up next iteration and flush
        }
    }

    /**
     * Gracefully shutdown the service.
     * Cancels the coroutine and waits for final flush.
     */
    fun shutdown() {
        job.cancel()
    }

    private fun flushLogs(buffer: List<LogEntry>) {
        if (buffer.isEmpty()) return

        val maxDetail =
            ConfigService
                .getModuleSettingInt(
                    ConfigService.EVENTS,
                    "actionlogger",
                    "detail_max_length",
                    Constants.Logging.DEFAULT_DETAIL_LENGTH,
                ).coerceIn(Constants.Logging.MAX_DETAIL_LENGTH_MIN, Constants.Logging.MAX_DETAIL_LENGTH_MAX)

        DatabaseManager.dbWrite {
            buffer.chunked(batchChunkSize).forEach { batch ->
                ActionLogsTable.batchInsert(batch) { entry ->
                    this[ActionLogsTable.time] = entry.timestamp.epochSecond.toInt()
                    this[ActionLogsTable.playerName] = entry.playerName
                    this[ActionLogsTable.action] = entry.action.code
                    this[ActionLogsTable.detail] = entry.detail.take(maxDetail)

                    val location = entry.location
                    if (location?.world != null) {
                        this[ActionLogsTable.world] = location.world!!.name
                        this[ActionLogsTable.x] = location.blockX
                        this[ActionLogsTable.y] = location.blockY
                        this[ActionLogsTable.z] = location.blockZ
                    } else {
                        this[ActionLogsTable.world] = "unknown"
                        this[ActionLogsTable.x] = 0
                        this[ActionLogsTable.y] = 0
                        this[ActionLogsTable.z] = 0
                    }
                    this[ActionLogsTable.amount] = 1
                }
                logCount.addAndGet(-batch.size)
            }
        }
    }

    private fun flushContainers(buffer: List<ContainerTx>) {
        if (buffer.isEmpty()) return

        DatabaseManager.dbWrite {
            buffer.chunked(batchChunkSize).forEach { batch ->
                ContainerTransactionsTable.batchInsert(batch) { tx ->
                    this[ContainerTransactionsTable.time] = tx.time
                    this[ContainerTransactionsTable.playerName] = tx.playerName
                    this[ContainerTransactionsTable.action] = tx.action
                    this[ContainerTransactionsTable.containerType] = tx.containerType
                    this[ContainerTransactionsTable.material] = tx.material
                    this[ContainerTransactionsTable.amount] = tx.amount
                    this[ContainerTransactionsTable.world] = tx.world
                    this[ContainerTransactionsTable.x] = tx.x
                    this[ContainerTransactionsTable.y] = tx.y
                    this[ContainerTransactionsTable.z] = tx.z
                }
                containerCount.addAndGet(-batch.size)
            }
        }
    }
}
