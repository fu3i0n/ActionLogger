package cat.daisy.core.events

import cat.daisy.core.service.ActionLogService
import cat.daisy.core.service.ConfigService
import org.bukkit.Material
import org.bukkit.block.BlockState
import org.bukkit.block.DoubleChest
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class ContainerListeners(
    private val logs: ActionLogService,
) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onClick(e: InventoryClickEvent) {
        if (!ConfigService.getModuleSettingBoolean(ConfigService.EVENTS, "actionlogger", "enabled", true)) return
        if (!ConfigService.getModuleSettingBoolean(ConfigService.EVENTS, "actionlogger", "capture.container", true)) return
        val p = e.whoClicked as? org.bukkit.entity.Player ?: return
        val clicked = e.clickedInventory ?: return
        if (!isContainer(clicked.type)) return
        val loc = getContainerLocation(clicked) ?: return

        val current = e.currentItem
        val cursor = e.cursor
        val container = clicked.type.name

        when (e.action) {
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_SOME,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE,
            -> {
                if (current != null && current.type != Material.AIR) {
                    val amt =
                        when (e.action) {
                            InventoryAction.PICKUP_HALF -> (current.amount + 1) / 2
                            InventoryAction.PICKUP_ONE -> 1
                            else -> current.amount
                        }
                    logs.submitContainer(
                        ActionLogService.ContainerTx(
                            time = (System.currentTimeMillis() / 1000L).toInt(),
                            playerName = p.name,
                            action = 0, // pickup/remove
                            containerType = container,
                            material = current.type.name,
                            amount = amt,
                            world = loc.world!!.name,
                            x = loc.blockX,
                            y = loc.blockY,
                            z = loc.blockZ,
                        ),
                    )
                }
            }

            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_SOME,
            InventoryAction.PLACE_ONE,
            -> {
                if (cursor != null && cursor.type != Material.AIR) {
                    val amt = if (e.action == InventoryAction.PLACE_ONE) 1 else cursor.amount
                    logs.submitContainer(
                        ActionLogService.ContainerTx(
                            time = (System.currentTimeMillis() / 1000L).toInt(),
                            playerName = p.name,
                            action = 1, // place/insert
                            containerType = container,
                            material = cursor.type.name,
                            amount = amt,
                            world = loc.world!!.name,
                            x = loc.blockX,
                            y = loc.blockY,
                            z = loc.blockZ,
                        ),
                    )
                }
            }

            InventoryAction.MOVE_TO_OTHER_INVENTORY -> {
                if (current != null && current.type != Material.AIR) {
                    val dir: Int = if (e.inventory == clicked) 0 else 1
                    logs.submitContainer(
                        ActionLogService.ContainerTx(
                            time = (System.currentTimeMillis() / 1000L).toInt(),
                            playerName = p.name,
                            action = dir,
                            containerType = container,
                            material = current.type.name,
                            amount = current.amount,
                            world = loc.world!!.name,
                            x = loc.blockX,
                            y = loc.blockY,
                            z = loc.blockZ,
                        ),
                    )
                }
            }

            else -> {}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrag(e: InventoryDragEvent) {
        if (!ConfigService.getModuleSettingBoolean(ConfigService.EVENTS, "actionlogger", "enabled", true)) return
        if (!ConfigService.getModuleSettingBoolean(ConfigService.EVENTS, "actionlogger", "capture.container", true)) return
        val p = e.whoClicked as? org.bukkit.entity.Player ?: return
        if (!isContainer(e.inventory.type)) return
        val loc = getContainerLocation(e.inventory) ?: return

        val dragged = e.oldCursor ?: return
        if (dragged.type == Material.AIR) return

        val total = e.newItems.values.sumOf(ItemStack::getAmount)
        if (total > 0) {
            logs.submitContainer(
                ActionLogService.ContainerTx(
                    time = (System.currentTimeMillis() / 1000L).toInt(),
                    playerName = p.name,
                    action = 1,
                    containerType = e.inventory.type.name,
                    material = dragged.type.name,
                    amount = total,
                    world = loc.world!!.name,
                    x = loc.blockX,
                    y = loc.blockY,
                    z = loc.blockZ,
                ),
            )
        }
    }

    private fun isContainer(t: InventoryType): Boolean =
        when (t) {
            InventoryType.CHEST,
            InventoryType.ENDER_CHEST,
            InventoryType.BARREL,
            InventoryType.SHULKER_BOX,
            InventoryType.HOPPER,
            InventoryType.DROPPER,
            InventoryType.DISPENSER,
            InventoryType.FURNACE,
            InventoryType.BLAST_FURNACE,
            InventoryType.SMOKER,
            InventoryType.BREWING,
            InventoryType.ANVIL,
            InventoryType.BEACON,
            InventoryType.ENCHANTING,
            InventoryType.MERCHANT,
            InventoryType.CARTOGRAPHY,
            InventoryType.GRINDSTONE,
            InventoryType.LECTERN,
            InventoryType.LOOM,
            InventoryType.STONECUTTER,
            InventoryType.SMITHING,
            -> true

            else -> false
        }

    private fun getContainerLocation(i: Inventory) =
        when (val h = i.holder) {
            is BlockState -> h.location
            is DoubleChest -> h.location
            else -> null
        }
}
