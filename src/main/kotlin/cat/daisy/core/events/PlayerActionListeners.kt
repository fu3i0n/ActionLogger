package cat.daisy.core.events

import cat.daisy.core.service.ActionLogService
import cat.daisy.core.service.ActionLogService.Action
import cat.daisy.core.service.ConfigService
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent

/**
 * Listens for player block placement/breaking, chat, and command events.
 * Submits logs to ActionLogService for async batch processing.
 */
class PlayerActionListeners(
    private val logs: ActionLogService,
) : Listener {
    private inline fun shouldLog(toggle: String): Boolean =
        ConfigService.getModuleSettingBoolean(ConfigService.EVENTS, "actionlogger", "enabled", true) &&
            ConfigService.getModuleSettingBoolean(ConfigService.EVENTS, "actionlogger", toggle, true)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (!shouldLog("capture.block_place")) return

        logs.submit(
            ActionLogService.LogEntry(
                playerName = event.player.name,
                action = Action.BLOCK_PLACED,
                detail = event.block.type.name,
                location = event.block.location,
            ),
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (!shouldLog("capture.block_break")) return

        logs.submit(
            ActionLogService.LogEntry(
                playerName = event.player.name,
                action = Action.BLOCK_BROKEN,
                detail = event.block.type.name,
                location = event.block.location,
            ),
        )
    }

    @Suppress("DEPRECATION") // AsyncPlayerChatEvent deprecation is for Paper API reasons
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        if (!shouldLog("capture.chat")) return

        logs.submit(
            ActionLogService.LogEntry(
                playerName = event.player.name,
                action = Action.CHAT,
                detail = event.message,
                location = event.player.location,
            ),
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        if (!shouldLog("capture.command")) return

        logs.submit(
            ActionLogService.LogEntry(
                playerName = event.player.name,
                action = Action.COMMAND,
                detail = event.message,
                location = event.player.location,
            ),
        )
    }
}
