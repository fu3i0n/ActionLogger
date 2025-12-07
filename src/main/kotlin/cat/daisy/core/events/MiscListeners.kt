package cat.daisy.core.events

import cat.daisy.core.service.ActionLogService
import cat.daisy.core.service.ActionLogService.Action
import cat.daisy.core.service.ConfigService
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerPickupItemEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Listens for miscellaneous player events: login, logout, item drop/pickup.
 */
class MiscListeners(
    private val logs: ActionLogService,
) : Listener {
    private inline fun isEnabled(): Boolean = ConfigService.getModuleSettingBoolean(ConfigService.EVENTS, "actionlogger", "enabled", true)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        if (!isEnabled()) return
        if (!ConfigService.getModuleSettingBoolean(ConfigService.EVENTS, "actionlogger", "capture.login", true)) return

        logs.submit(
            ActionLogService.LogEntry(
                playerName = event.player.name,
                action = Action.LOGIN,
                detail = "",
                location = event.player.location,
            ),
        )
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        if (!isEnabled()) return
        if (!ConfigService.getModuleSettingBoolean(ConfigService.EVENTS, "actionlogger", "capture.logout", true)) return

        logs.submit(
            ActionLogService.LogEntry(
                playerName = event.player.name,
                action = Action.LOGOUT,
                detail = "",
                location = event.player.location,
            ),
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        if (!isEnabled()) return
        if (!ConfigService.getModuleSettingBoolean(ConfigService.EVENTS, "actionlogger", "capture.item_drop", true)) return

        val itemStack = event.itemDrop.itemStack
        val detail = "${itemStack.type} x${itemStack.amount}"

        logs.submit(
            ActionLogService.LogEntry(
                playerName = event.player.name,
                action = Action.ITEM_DROP,
                detail = detail,
                location = event.player.location,
            ),
        )
    }

    @Suppress("DEPRECATION") // PlayerPickupItemEvent is deprecated but necessary for this feature
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPickup(event: PlayerPickupItemEvent) {
        if (!isEnabled()) return
        if (!ConfigService.getModuleSettingBoolean(ConfigService.EVENTS, "actionlogger", "capture.item_pickup", true)) return

        val itemStack = event.item.itemStack
        val detail = "${itemStack.type} x${itemStack.amount}"

        logs.submit(
            ActionLogService.LogEntry(
                playerName = event.player.name,
                action = Action.ITEM_PICKUP,
                detail = detail,
                location = event.player.location,
            ),
        )
    }
}
