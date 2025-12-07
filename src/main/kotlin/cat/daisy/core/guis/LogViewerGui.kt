package cat.daisy.core.guis

import cat.daisy.core.Core
import cat.daisy.core.database.repositories.LogRepository
import cat.daisy.core.service.ConfigService
import cat.daisy.core.utils.TextUtils.mm
import me.tech.mcchestui.GUI
import me.tech.mcchestui.GUIType
import me.tech.mcchestui.item.item
import me.tech.mcchestui.utils.gui
import me.tech.mcchestui.utils.openGUI
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

data class LogViewerFilter(
    val player: String? = null,
    val action: Int? = null,
    val fromEpochSec: Int? = null,
    val toEpochSec: Int? = null,
)

fun Player.openLogViewer(
    page: Int = 0,
    pageSize: Int = ConfigService.getModuleSettingInt(ConfigService.GUIS, "logviewer", "page_size", 45),
    filter: LogViewerFilter = LogViewerFilter(),
) {
    val effectiveSize = pageSize.coerceIn(9, 100)
    val gui = buildLogViewerGui(this, page.coerceAtLeast(0), effectiveSize, filter)
    this.openGUI(gui)
}

/**
 * Get action label from config (with color) or fallback to default mapping
 */
private fun getActionLabel(code: Int): String {
    val actionNames = ConfigService.getModuleSettingSection(ConfigService.GUIS, "logviewer", "action_names")
    if (actionNames != null) {
        val label = actionNames.getString(code.toString())
        if (label != null) {
            return label
        }
    }
    // Fallback to hardcoded labels if config missing
    return when (code) {
        0 -> "<#4ECDC4>Block Placed"
        1 -> "<#FF6B6B>Block Broken"
        4 -> "<#FFD700>Chat"
        5 -> "<#A3E4A1>Command"
        6 -> "<#95E1D3>Login"
        7 -> "<#FF8B8B>Logout"
        8 -> "<#C7CEEA>Item Drop"
        9 -> "<#B4D7FF>Item Pickup"
        else -> "<gray>Unknown"
    }
}

/**
 * Get action icon emoji
 */
private fun getActionIcon(code: Int): String =
    when (code) {
        0 -> "🧱"
        1 -> "💥"
        4 -> "💬"
        5 -> "⚙"
        6 -> "✅"
        7 -> "❌"
        8 -> "📤"
        9 -> "📥"
        else -> "❓"
    }

/**
 * Get action name without color codes
 */
private fun getActionName(code: Int): String =
    when (code) {
        0 -> "Block Placed"
        1 -> "Block Broken"
        4 -> "Chat Message"
        5 -> "Command Executed"
        6 -> "Player Login"
        7 -> "Player Logout"
        8 -> "Item Dropped"
        9 -> "Item Picked Up"
        else -> "Unknown Action"
    }

/**
 * Get human-readable time ago string
 */
private fun getTimeAgo(instant: Instant): String {
    val now = Instant.now()
    val seconds =
        java.time.Duration
            .between(instant, now)
            .seconds

    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        seconds < 86400 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        seconds < 604800 -> "${seconds / 86400}d ${(seconds % 86400) / 3600}h"
        else -> "${seconds / 604800}w ${(seconds % 604800) / 86400}d"
    }
}

private fun buildLogViewerGui(
    player: Player,
    page: Int,
    pageSize: Int,
    filter: LogViewerFilter,
): GUI =
    gui(
        plugin = Core.instance,
        title = "<gradient:#00D4FF:#7B2FF7><bold>⚡ Action Logs</bold></gradient>".mm(),
        type = GUIType.Chest(rows = 6),
    ) {
        // Sleek dark background
        for (i in 1..54) {
            slot(i, 1) {
                item = item(Material.BLACK_STAINED_GLASS_PANE) { name = Component.text(" ") }
            }
        }

        val total =
            LogRepository.countLogs(
                player = filter.player,
                action = filter.action,
                fromEpochSec = filter.fromEpochSec,
                toEpochSec = filter.toEpochSec,
            )

        val maxPages = ((total + pageSize - 1) / pageSize).toInt().coerceAtLeast(1)
        val currentPage = page.coerceIn(0, maxPages - 1)

        val logs =
            LogRepository.list(
                player = filter.player,
                action = filter.action,
                fromEpochSec = filter.fromEpochSec,
                toEpochSec = filter.toEpochSec,
                page = currentPage,
                size = pageSize,
                sortDesc = true,
            )

        // Logs display: Rows 1-4 (slots 1-36)
        logs.take(36).forEachIndexed { idx, row ->
            slot(idx + 1, 1) {
                val material =
                    when (row.action) {
                        0 -> Material.GRASS_BLOCK
                        1 -> Material.TNT
                        4 -> Material.PAPER
                        5 -> Material.COMMAND_BLOCK
                        6 -> Material.EMERALD
                        7 -> Material.REDSTONE
                        8 -> Material.DROPPER
                        9 -> Material.HOPPER
                        else -> Material.BOOK
                    }

                item =
                    item(material) {
                        val whenText = timeFmt.format(row.time)
                        val locText = row.location?.let { "${it.world?.name} [${it.blockX}, ${it.blockY}, ${it.blockZ}]" } ?: "Unknown"
                        val timeAgo = getTimeAgo(row.time)

                        // Beautiful gradient title
                        name =
                            "<gradient:#00D4FF:#7B2FF7><bold>${getActionIcon(
                                row.action,
                            )} ${getActionName(row.action)}</bold></gradient>".mm()

                        lore =
                            buildList {
                                add(Component.empty())
                                add("<#00D4FF>⏰ Time: <white>$whenText".mm())
                                add("<dark_gray>   ($timeAgo ago)".mm())
                                add(Component.empty())
                                add("<#7B2FF7>👤 Player: <white>${row.playerName}".mm())
                                add("<#00FF9F>🌍 Location: <white>$locText".mm())
                                add(Component.empty())
                                add("<#FFD93D>📝 Details:".mm())
                                add("<white>   ${(row.detail ?: "No details").take(40)}".mm())
                                if ((row.detail?.length ?: 0) > 40) add("<gray>   ...".mm())
                                add(Component.empty())
                                add("<gradient:#FF3864:#FFD93D><bold>▶ Click to teleport!</bold></gradient>".mm())
                            }

                        onClick {
                            val enableTp = ConfigService.getModuleSettingBoolean(ConfigService.GUIS, "logviewer", "enable_teleport", true)
                            if (!enableTp) {
                                player.sendMessage("<red>Teleportation is disabled!".mm())
                                return@onClick
                            }
                            teleportPlayerSafe(player, row.location)
                        }
                    }
            }
        }

        // Row 5 - Decorative gradient separator
        slot(37, 1) { item = item(Material.BLUE_STAINED_GLASS_PANE) { name = Component.text(" ") } }
        slot(38, 1) { item = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE) { name = Component.text(" ") } }
        slot(39, 1) { item = item(Material.CYAN_STAINED_GLASS_PANE) { name = Component.text(" ") } }
        slot(40, 1) { item = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE) { name = Component.text(" ") } }
        slot(41, 1) { item = item(Material.WHITE_STAINED_GLASS_PANE) { name = Component.text(" ") } }
        slot(42, 1) { item = item(Material.PINK_STAINED_GLASS_PANE) { name = Component.text(" ") } }
        slot(43, 1) { item = item(Material.MAGENTA_STAINED_GLASS_PANE) { name = Component.text(" ") } }
        slot(44, 1) { item = item(Material.PURPLE_STAINED_GLASS_PANE) { name = Component.text(" ") } }
        slot(45, 1) { item = item(Material.BLUE_STAINED_GLASS_PANE) { name = Component.text(" ") } }

        // Row 6 - Navigation (slots 46-54)

        // Slot 46 - Filters
        slot(46, 1) {
            val hasFilters = filter.player != null || filter.action != null || filter.fromEpochSec != null
            item =
                item(Material.SPYGLASS) {
                    name = "<gradient:#00D4FF:#7B2FF7><bold>🔍 Filters</bold></gradient>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<#7B2FF7>👤 Player: <white>${filter.player ?: "All Players"}".mm(),
                            "<#00D4FF>⚡ Action: <white>${filter.action?.let { getActionName(it) } ?: "All Types"}".mm(),
                            "<#FFD93D>⏰ Time: <white>${if (filter.fromEpochSec != null) "Filtered" else "All Time"}".mm(),
                            Component.empty(),
                            if (hasFilters) "<#00FF9F>✓ Filters active".mm() else "<gray>No filters applied".mm(),
                            Component.empty(),
                            "<yellow>▶ Click to edit filters".mm(),
                        )
                    if (hasFilters) glowing = true
                    onClick {
                        player.openFilterGui(currentPage, pageSize, filter)
                    }
                }
        }

        // Slot 47 - Stats
        slot(47, 1) {
            item =
                item(Material.ENCHANTED_BOOK) {
                    name = "<gradient:#FFD93D:#FF3864><bold>📊 Statistics</bold></gradient>".mm()
                    val showing = logs.size
                    lore =
                        listOf(
                            Component.empty(),
                            "<#00FF9F>📈 Total Records: <white>$total".mm(),
                            "<#00D4FF>📄 Showing: <white>$showing".mm(),
                            "<#7B2FF7>📑 Pages: <white>$maxPages".mm(),
                            Component.empty(),
                            "<gradient:#00D4FF:#7B2FF7>Real-time tracking</gradient>".mm(),
                        )
                    glowing = true
                }
        }

        // Slot 48 - Previous
        slot(48, 1) {
            val hasPrev = currentPage > 0
            item =
                item(if (hasPrev) Material.ARROW else Material.GRAY_DYE) {
                    name =
                        if (hasPrev) {
                            "<gradient:#00FF9F:#00D4FF><bold>⬅ Previous</bold></gradient>".mm()
                        } else {
                            "<gray>⬅ Previous".mm()
                        }
                    lore =
                        if (hasPrev) {
                            listOf(
                                Component.empty(),
                                "<white>Go to page <#00D4FF>$currentPage".mm(),
                                Component.empty(),
                            )
                        } else {
                            listOf(Component.empty(), "<dark_gray>First page".mm())
                        }
                }
            onClick {
                if (currentPage > 0) player.openLogViewer(currentPage - 1, pageSize, filter)
            }
        }

        // Slot 49 - Spacer
        slot(49, 1) {
            item = item(Material.GRAY_STAINED_GLASS_PANE) { name = Component.text(" ") }
        }

        // Slot 50 - Page indicator (CENTER)
        slot(50, 1) {
            item =
                item(Material.NETHER_STAR) {
                    name = "<gradient:#FFD93D:#FF3864><bold>📖 Page ${currentPage + 1} / $maxPages</bold></gradient>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<#7B2FF7>Current: <white>Page ${currentPage + 1}".mm(),
                            "<#00FF9F>Total: <white>$total records".mm(),
                            Component.empty(),
                        )
                    glowing = true
                }
        }

        // Slot 51 - Spacer
        slot(51, 1) {
            item = item(Material.GRAY_STAINED_GLASS_PANE) { name = Component.text(" ") }
        }

        // Slot 52 - Next
        slot(52, 1) {
            val hasNext = currentPage < maxPages - 1
            item =
                item(if (hasNext) Material.ARROW else Material.GRAY_DYE) {
                    name =
                        if (hasNext) {
                            "<gradient:#00D4FF:#7B2FF7><bold>Next ➡</bold></gradient>".mm()
                        } else {
                            "<gray>Next ➡".mm()
                        }
                    lore =
                        if (hasNext) {
                            listOf(
                                Component.empty(),
                                "<white>Go to page <#7B2FF7>${currentPage + 2}".mm(),
                                Component.empty(),
                            )
                        } else {
                            listOf(Component.empty(), "<dark_gray>Last page".mm())
                        }
                }
            onClick {
                if (currentPage < maxPages - 1) player.openLogViewer(currentPage + 1, pageSize, filter)
            }
        }

        // Slot 53 - Refresh
        slot(53, 1) {
            item =
                item(Material.CLOCK) {
                    name = "<gradient:#00FF9F:#00D4FF><bold>🔄 Refresh</bold></gradient>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<white>Reload current page".mm(),
                            "<gray>See latest logs".mm(),
                            Component.empty(),
                        )
                }
            onClick { player.openLogViewer(currentPage, pageSize, filter) }
        }

        // Slot 54 - Close
        slot(54, 1) {
            item =
                item(Material.BARRIER) {
                    name = "<gradient:#FF3864:#FFD93D><bold>✕ Close</bold></gradient>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<gray>Close this menu".mm(),
                        )
                }
            onClick { player.closeInventory() }
        }
    }

/**
 * Safely teleport player to a location with validation
 */
private fun teleportPlayerSafe(
    player: Player,
    location: org.bukkit.Location?,
) {
    if (location == null) {
        player.sendMessage(
            ConfigService
                .getModuleLangString(
                    ConfigService.GUIS,
                    "logviewer",
                    "messages.teleport_fail",
                    "<red>Cannot teleport: location unavailable",
                ).mm(),
        )
        return
    }

    val world = location.world
    if (world == null || Bukkit.getWorld(world.name) == null) {
        player.sendMessage(
            ConfigService
                .getModuleLangString(
                    ConfigService.GUIS,
                    "logviewer",
                    "messages.teleport_fail",
                    "<red>Cannot teleport: world not loaded",
                ).mm(),
        )
        return
    }

    // Apply optional height boost to avoid suffocation
    val heightBoost = ConfigService.getModuleSettingInt(ConfigService.GUIS, "logviewer", "teleport_height_boost", 1)
    val safeLocation = location.clone().add(0.0, heightBoost.toDouble(), 0.0)

    player.teleportAsync(safeLocation).thenRun {
        player.sendMessage(
            ConfigService
                .getModuleLangString(
                    ConfigService.GUIS,
                    "logviewer",
                    "messages.teleport_success",
                    "<green>Teleported!",
                ).mm(),
        )
    }
}

/**
 * Open the filter selection GUI
 */
private fun Player.openFilterGui(
    returnPage: Int,
    pageSize: Int,
    currentFilter: LogViewerFilter,
) {
    val filterGui = buildFilterGui(this, returnPage, pageSize, currentFilter)
    this.openGUI(filterGui)
}

/**
 * Build the filter selection GUI
 */
private fun buildFilterGui(
    player: Player,
    returnPage: Int,
    pageSize: Int,
    currentFilter: LogViewerFilter,
): GUI =
    gui(
        plugin = Core.instance,
        title = "<gradient:#7B2FF7:#FF3864><bold>🔍 Filter Options</bold></gradient>".mm(),
        type = GUIType.Chest(rows = 5),
    ) {
        // Dark background
        for (i in 1..45) {
            slot(i, 1) {
                item = item(Material.BLACK_STAINED_GLASS_PANE) { name = Component.text(" ") }
            }
        }

        // Row 1: Header decoration
        for (i in listOf(1, 9)) {
            slot(i, 1) {
                item = item(Material.PURPLE_STAINED_GLASS_PANE) { name = Component.text(" ") }
            }
        }

        // Slot 5 - All Actions (center of row 1)
        slot(5, 1) {
            val isSelected = currentFilter.action == null
            item =
                item(Material.NETHER_STAR) {
                    name = "<gradient:#FFD93D:#FF3864><bold>⭐ All Actions</bold></gradient>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<gray>Show all action types".mm(),
                            Component.empty(),
                            if (isSelected) "<#00FF9F>✓ Currently selected".mm() else "<yellow>▶ Click to select".mm(),
                        )
                    if (isSelected) glowing = true
                    onClick {
                        player.openLogViewer(0, pageSize, currentFilter.copy(action = null))
                    }
                }
        }

        // Row 2: Action Type Filters (slots 10-18) - all 8 action types nicely spaced
        // Slot 11 - Block Placed
        slot(11, 1) {
            val isSelected = currentFilter.action == 0
            item =
                item(Material.GRASS_BLOCK) {
                    name = "<#00D4FF><bold>🧱 Block Placed</bold>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<gray>Filter by block placements".mm(),
                            Component.empty(),
                            if (isSelected) "<#00FF9F>✓ Currently selected".mm() else "<yellow>▶ Click to select".mm(),
                        )
                    if (isSelected) glowing = true
                    onClick { player.openLogViewer(0, pageSize, currentFilter.copy(action = 0)) }
                }
        }

        // Slot 12 - Block Broken
        slot(12, 1) {
            val isSelected = currentFilter.action == 1
            item =
                item(Material.TNT) {
                    name = "<#FF3864><bold>💥 Block Broken</bold>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<gray>Filter by block breaks".mm(),
                            Component.empty(),
                            if (isSelected) "<#00FF9F>✓ Currently selected".mm() else "<yellow>▶ Click to select".mm(),
                        )
                    if (isSelected) glowing = true
                    onClick { player.openLogViewer(0, pageSize, currentFilter.copy(action = 1)) }
                }
        }

        // Slot 13 - Chat
        slot(13, 1) {
            val isSelected = currentFilter.action == 4
            item =
                item(Material.PAPER) {
                    name = "<#FFD93D><bold>💬 Chat</bold>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<gray>Filter by chat messages".mm(),
                            Component.empty(),
                            if (isSelected) "<#00FF9F>✓ Currently selected".mm() else "<yellow>▶ Click to select".mm(),
                        )
                    if (isSelected) glowing = true
                    onClick { player.openLogViewer(0, pageSize, currentFilter.copy(action = 4)) }
                }
        }

        // Slot 14 - Commands
        slot(14, 1) {
            val isSelected = currentFilter.action == 5
            item =
                item(Material.COMMAND_BLOCK) {
                    name = "<#00FF9F><bold>⚙ Commands</bold>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<gray>Filter by commands".mm(),
                            Component.empty(),
                            if (isSelected) "<#00FF9F>✓ Currently selected".mm() else "<yellow>▶ Click to select".mm(),
                        )
                    if (isSelected) glowing = true
                    onClick { player.openLogViewer(0, pageSize, currentFilter.copy(action = 5)) }
                }
        }

        // Slot 15 - Login
        slot(15, 1) {
            val isSelected = currentFilter.action == 6
            item =
                item(Material.EMERALD) {
                    name = "<#00FF9F><bold>✅ Login</bold>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<gray>Filter by player logins".mm(),
                            Component.empty(),
                            if (isSelected) "<#00FF9F>✓ Currently selected".mm() else "<yellow>▶ Click to select".mm(),
                        )
                    if (isSelected) glowing = true
                    onClick { player.openLogViewer(0, pageSize, currentFilter.copy(action = 6)) }
                }
        }

        // Slot 16 - Logout
        slot(16, 1) {
            val isSelected = currentFilter.action == 7
            item =
                item(Material.REDSTONE) {
                    name = "<#FF3864><bold>❌ Logout</bold>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<gray>Filter by player logouts".mm(),
                            Component.empty(),
                            if (isSelected) "<#00FF9F>✓ Currently selected".mm() else "<yellow>▶ Click to select".mm(),
                        )
                    if (isSelected) glowing = true
                    onClick { player.openLogViewer(0, pageSize, currentFilter.copy(action = 7)) }
                }
        }

        // Slot 17 - Item Drop
        slot(17, 1) {
            val isSelected = currentFilter.action == 8
            item =
                item(Material.DROPPER) {
                    name = "<#7B2FF7><bold>📤 Item Drop</bold>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<gray>Filter by item drops".mm(),
                            Component.empty(),
                            if (isSelected) "<#00FF9F>✓ Currently selected".mm() else "<yellow>▶ Click to select".mm(),
                        )
                    if (isSelected) glowing = true
                    onClick { player.openLogViewer(0, pageSize, currentFilter.copy(action = 8)) }
                }
        }

        // Slot 18 - Item Pickup
        slot(18, 1) {
            val isSelected = currentFilter.action == 9
            item =
                item(Material.HOPPER) {
                    name = "<#00D4FF><bold>📥 Item Pickup</bold>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<gray>Filter by item pickups".mm(),
                            Component.empty(),
                            if (isSelected) "<#00FF9F>✓ Currently selected".mm() else "<yellow>▶ Click to select".mm(),
                        )
                    if (isSelected) glowing = true
                    onClick { player.openLogViewer(0, pageSize, currentFilter.copy(action = 9)) }
                }
        }

        // Row 3: Separator
        for (i in 19..27) {
            slot(i, 1) {
                item = item(Material.GRAY_STAINED_GLASS_PANE) { name = Component.text(" ") }
            }
        }

        // Row 4: Time filters (slots 28-36)
        // Slot 29 - Time header
        slot(29, 1) {
            item =
                item(Material.CLOCK) {
                    name = "<gradient:#FFD93D:#FF3864><bold>⏰ Time Range</bold></gradient>".mm()
                    lore = listOf(Component.empty(), "<gray>Select time range →".mm())
                }
        }

        // Slot 30 - Last Hour
        slot(30, 1) {
            item =
                item(Material.LIME_DYE) {
                    name = "<#00FF9F><bold>1 Hour</bold>".mm()
                    lore = listOf(Component.empty(), "<gray>Last 60 minutes".mm(), Component.empty(), "<yellow>▶ Click".mm())
                    onClick {
                        val now = (System.currentTimeMillis() / 1000L).toInt()
                        player.openLogViewer(0, pageSize, currentFilter.copy(fromEpochSec = now - 3600, toEpochSec = now))
                    }
                }
        }

        // Slot 31 - Last 24 Hours
        slot(31, 1) {
            item =
                item(Material.YELLOW_DYE) {
                    name = "<#FFD93D><bold>24 Hours</bold>".mm()
                    lore = listOf(Component.empty(), "<gray>Last day".mm(), Component.empty(), "<yellow>▶ Click".mm())
                    onClick {
                        val now = (System.currentTimeMillis() / 1000L).toInt()
                        player.openLogViewer(0, pageSize, currentFilter.copy(fromEpochSec = now - 86400, toEpochSec = now))
                    }
                }
        }

        // Slot 32 - Last Week
        slot(32, 1) {
            item =
                item(Material.ORANGE_DYE) {
                    name = "<#FF8C00><bold>7 Days</bold>".mm()
                    lore = listOf(Component.empty(), "<gray>Last week".mm(), Component.empty(), "<yellow>▶ Click".mm())
                    onClick {
                        val now = (System.currentTimeMillis() / 1000L).toInt()
                        player.openLogViewer(0, pageSize, currentFilter.copy(fromEpochSec = now - 604800, toEpochSec = now))
                    }
                }
        }

        // Slot 33 - All Time
        slot(33, 1) {
            val isSelected = currentFilter.fromEpochSec == null
            item =
                item(Material.PURPLE_DYE) {
                    name = "<#7B2FF7><bold>All Time</bold>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<gray>No time filter".mm(),
                            Component.empty(),
                            if (isSelected) "<#00FF9F>✓ Selected".mm() else "<yellow>▶ Click".mm(),
                        )
                    if (isSelected) glowing = true
                    onClick { player.openLogViewer(0, pageSize, currentFilter.copy(fromEpochSec = null, toEpochSec = null)) }
                }
        }

        // Row 5: Control buttons
        // Slot 37 - Back
        slot(37, 1) {
            item =
                item(Material.ARROW) {
                    name = "<gradient:#00FF9F:#00D4FF><bold>← Back to Logs</bold></gradient>".mm()
                    lore = listOf(Component.empty(), "<gray>Return to log viewer".mm())
                    onClick { player.openLogViewer(returnPage, pageSize, currentFilter) }
                }
        }

        // Slot 41 - Current filters (center)
        slot(41, 1) {
            val hasFilters = currentFilter.player != null || currentFilter.action != null || currentFilter.fromEpochSec != null
            item =
                item(Material.BOOK) {
                    name = "<gradient:#FFD93D:#FF3864><bold>📋 Active Filters</bold></gradient>".mm()
                    lore =
                        listOf(
                            Component.empty(),
                            "<#7B2FF7>👤 Player: <white>${currentFilter.player ?: "All"}".mm(),
                            "<#00D4FF>⚡ Action: <white>${currentFilter.action?.let { getActionName(it) } ?: "All"}".mm(),
                            "<#FFD93D>⏰ Time: <white>${if (currentFilter.fromEpochSec != null) "Filtered" else "All"}".mm(),
                            Component.empty(),
                        )
                    if (hasFilters) glowing = true
                }
        }

        // Slot 45 - Clear All
        slot(45, 1) {
            item =
                item(Material.WATER_BUCKET) {
                    name = "<gradient:#00D4FF:#7B2FF7><bold>🧹 Clear Filters</bold></gradient>".mm()
                    lore = listOf(Component.empty(), "<gray>Remove all filters".mm(), Component.empty(), "<yellow>▶ Click to clear".mm())
                    onClick { player.openLogViewer(0, pageSize, LogViewerFilter()) }
                }
        }
    }
