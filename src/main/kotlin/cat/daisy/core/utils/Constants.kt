package cat.daisy.core.utils

/**
 * Core constants and configuration for the ActionLogger plugin.
 * Centralizes all magic numbers, timeouts, and defaults.
 */

object Constants {
    // === Database Configuration ===
    object Database {
        // Connection pool sizing for SQLite - keep low to avoid contention
        const val MAX_POOL_SIZE = 4 // Increased for better concurrent access
        const val MIN_IDLE_CONNECTIONS = 2 // Always keep 2 connections warm

        // Timeout configurations - production optimized
        const val CONNECTION_TIMEOUT_MS = 10_000L // 10 seconds
        const val IDLE_TIMEOUT_MS = 300_000L // 5 minutes
        const val MAX_LIFETIME_MS = 1_800_000L // 30 minutes (increased for stability)
        const val BUSY_TIMEOUT_MS = 30_000L // 30 seconds
        const val LEAK_DETECTION_THRESHOLD_MS = 60_000L // 1 minute

        // SQLite performance tuning
        const val CACHE_SIZE = "-128000" // 128MB cache (increased for better performance)
    }

    // === Channel/Queue Configuration ===
    object Queue {
        // Async queue sizing - production optimized for high-traffic servers
        const val DEFAULT_CAPACITY = 250_000 // Increased for large servers
        const val FLUSH_INTERVAL_MS = 500L // Reduced to 500ms for better real-time updates
        const val BATCH_CHUNK_SIZE = 1000 // Increased batch size for better throughput

        // Pagination defaults
        const val DEFAULT_PAGE_SIZE = 28 // Matches new GUI layout (4 rows * 7 cols)
        const val MIN_PAGE_SIZE = 9
        const val MAX_PAGE_SIZE = 100
        const val DEFAULT_API_PAGE_SIZE = 50
        const val MAX_API_PAGE_SIZE = 200
    }

    // === Cache Configuration ===
    object Cache {
        const val RECENT_PLAYERS_TTL_SECONDS = 300
        const val RECENT_PLAYERS_DEFAULT_LIMIT = 50
        const val MAX_CACHE_SIZE = 100
        const val TARGET_CACHE_SIZE = 50
        const val CACHE_CLEANUP_INTERVAL_MINUTES = 30L
        const val CACHE_TTL_MINUTES = 60L
    }

    // === Logging Configuration ===
    object Logging {
        const val MAX_DETAIL_LENGTH_MIN = 32
        const val MAX_DETAIL_LENGTH_MAX = 1024
        const val DEFAULT_DETAIL_LENGTH = 255
        const val SKIN_IMAGE_SIZE = 8
        const val CONNECTION_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
    }

    // === GUI Configuration ===
    object GUI {
        const val TELEPORT_HEIGHT_BOOST_DEFAULT = 1
        const val FILTER_SLOT_PREVIOUS = 46
        const val FILTER_SLOT_NEXT = 52
        const val FILTER_SLOT_FILTERS = 47
        const val FILTER_SLOT_CLOSE = 49
    }

    // === Dashboard Configuration ===
    object Dashboard {
        const val DEFAULT_PORT = 8080
        const val DEFAULT_TIMEOUT_MS = 5000
    }

    // === Permission Keys ===
    object Permissions {
        const val VIEWER = "daisy.logger.viewer"
        const val ADMIN = "daisy.logger.admin"
        const val TELEPORT = "daisy.logger.teleport"
    }

    // === Color Codes ===
    object Colors {
        const val PRIMARY = "#3498db"
        const val SECONDARY = "#2ecc71"
        const val ERROR = "#e74c3c"
        const val SUCCESS = "#2ecc71"
        const val WARNING = "#f1c40f"
        const val INFO = "#3498db"
        const val BROADCAST = "#9b59b6"
        const val SYSTEM = "#34495e"
        const val ACCENT = "#4ECDC4"
        const val GOLD = "#FFD700"
    }

    // === Action Code Definitions ===
    object ActionCodes {
        const val BLOCK_PLACED = 0
        const val BLOCK_BROKEN = 1
        const val CHAT = 4
        const val COMMAND = 5
        const val LOGIN = 6
        const val LOGOUT = 7
        const val ITEM_DROP = 8
        const val ITEM_PICKUP = 9
        const val OTHER = 99
    }
}
