package cat.daisy.core.database.repositories

import cat.daisy.core.database.DatabaseManager
import cat.daisy.core.database.tables.ActionLogsTable
import cat.daisy.core.service.ConfigService
import cat.daisy.core.utils.Constants
import org.bukkit.Bukkit
import org.bukkit.Location
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Database repository for action logs.
 * Handles all queries against the ActionLogsTable with advanced filtering and statistics.
 */
object LogRepository {
    data class LogRow(
        val id: Long,
        val time: Instant,
        val playerName: String,
        val action: Int,
        val detail: String?,
        val location: Location?,
        val amount: Int,
    )

    // Cache for recentPlayers() with TTL
    private var cachedRecentPlayers: List<String>? = null
    private var cacheTimestamp: AtomicLong = AtomicLong(0L)

    /**
     * Get recent unique players from the database.
     * Results are cached to avoid repeated database queries.
     * Cache TTL is configurable via config.
     */
    fun recentPlayers(limit: Int = Constants.Cache.RECENT_PLAYERS_DEFAULT_LIMIT): List<String> {
        val ttlSeconds =
            ConfigService.getModuleSettingInt(
                ConfigService.EVENTS,
                "actionlogger",
                "cache.recent_players_ttl_seconds",
                Constants.Cache.RECENT_PLAYERS_TTL_SECONDS,
            )
        val now = System.currentTimeMillis()

        // Return cached result if still valid
        if (cachedRecentPlayers != null && now - cacheTimestamp.get() < ttlSeconds * 1000L) {
            return cachedRecentPlayers!!.take(limit)
        }

        // Refresh cache from database
        val players =
            DatabaseManager.dbQuery {
                ActionLogsTable
                    .selectAll()
                    .orderBy(ActionLogsTable.time, SortOrder.DESC)
                    .map { it[ActionLogsTable.playerName] }
                    .distinct()
                    .take(limit.coerceAtLeast(Constants.Cache.RECENT_PLAYERS_DEFAULT_LIMIT))
            }

        cachedRecentPlayers = players
        cacheTimestamp.set(now)
        return players.take(limit)
    }

    /**
     * Get all distinct action codes that have been logged.
     */
    fun distinctActions(): List<Int> =
        DatabaseManager.dbQuery {
            ActionLogsTable.selectAll().map { it[ActionLogsTable.action] }.distinct()
        }

    /**
     * Count logs matching optional filters.
     * Efficient use of database indices for fast queries.
     */
    fun countLogs(
        player: String? = null,
        action: Int? = null,
        fromEpochSec: Int? = null,
        toEpochSec: Int? = null,
    ): Long =
        DatabaseManager.dbQuery {
            var query = ActionLogsTable.selectAll()

            if (!player.isNullOrBlank()) query = query.andWhere { ActionLogsTable.playerName eq player }
            if (action != null) query = query.andWhere { ActionLogsTable.action eq action }
            if (fromEpochSec != null) query = query.andWhere { ActionLogsTable.time greaterEq fromEpochSec }
            if (toEpochSec != null) query = query.andWhere { ActionLogsTable.time lessEq toEpochSec }

            query.count()
        }

    /**
     * Retrieve logs with pagination and filtering.
     * Results are sorted by time (descending by default for newest-first viewing).
     */
    fun list(
        player: String? = null,
        action: Int? = null,
        fromEpochSec: Int? = null,
        toEpochSec: Int? = null,
        page: Int = 0,
        size: Int = Constants.Queue.DEFAULT_PAGE_SIZE,
        sortDesc: Boolean = true,
    ): List<LogRow> =
        DatabaseManager.dbQuery {
            val order = if (sortDesc) SortOrder.DESC else SortOrder.ASC
            var query = ActionLogsTable.selectAll()

            if (!player.isNullOrBlank()) query = query.andWhere { ActionLogsTable.playerName eq player }
            if (action != null) query = query.andWhere { ActionLogsTable.action eq action }
            if (fromEpochSec != null) query = query.andWhere { ActionLogsTable.time greaterEq fromEpochSec }
            if (toEpochSec != null) query = query.andWhere { ActionLogsTable.time lessEq toEpochSec }

            query
                .orderBy(ActionLogsTable.time to order)
                .limit(size)
                .offset((page * size).toLong())
                .map { toRow(it) }
        }

    /**
     * Get action counts grouped by action code within optional timeframe.
     * Useful for analytics and statistics.
     */
    fun countByAction(
        player: String? = null,
        fromEpochSec: Int? = null,
        toEpochSec: Int? = null,
    ): Map<Int, Long> =
        DatabaseManager.dbQuery {
            var query = ActionLogsTable.selectAll()

            if (!player.isNullOrBlank()) query = query.andWhere { ActionLogsTable.playerName eq player }
            if (fromEpochSec != null) query = query.andWhere { ActionLogsTable.time greaterEq fromEpochSec }
            if (toEpochSec != null) query = query.andWhere { ActionLogsTable.time lessEq toEpochSec }

            query
                .toList()
                .groupingBy { it[ActionLogsTable.action] }
                .eachCount()
                .mapValues { it.value.toLong() }
        }

    /**
     * Convert database result row to LogRow data class.
     * Safely handles null locations and missing worlds.
     */
    private fun toRow(rs: ResultRow): LogRow {
        val worldName = rs[ActionLogsTable.world]
        val world = Bukkit.getWorld(worldName)
        val location =
            if (world != null) {
                Location(world, rs[ActionLogsTable.x].toDouble(), rs[ActionLogsTable.y].toDouble(), rs[ActionLogsTable.z].toDouble())
            } else {
                null
            }

        return LogRow(
            id = rs[ActionLogsTable.id],
            time = Instant.ofEpochSecond(rs[ActionLogsTable.time].toLong()),
            playerName = rs[ActionLogsTable.playerName],
            action = rs[ActionLogsTable.action],
            detail = rs[ActionLogsTable.detail],
            location = location,
            amount = rs[ActionLogsTable.amount],
        )
    }
}
