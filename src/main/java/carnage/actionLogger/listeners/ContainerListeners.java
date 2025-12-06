package carnage.actionLogger.listeners;

import carnage.actionLogger.models.ContainerTransaction;
import carnage.actionLogger.queue.LogQueue;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

public class ContainerListeners implements Listener {
    private final LogQueue queue;
    public ContainerListeners(LogQueue queue) { this.queue = queue; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player p)) return;
        var clicked = e.getClickedInventory();
        if (clicked == null || !isContainer(clicked.getType())) return;
        var loc = getContainerLocation(clicked);
        if (loc == null) return;

        var current = e.getCurrentItem();
        var cursor = e.getCursor();
        var container = clicked.getType().name();

        switch (e.getAction()) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE -> {
                if (current != null && current.getType() != Material.AIR) {
                    int amt = switch (e.getAction()) {
                        case PICKUP_HALF -> (current.getAmount() + 1) / 2;
                        case PICKUP_ONE -> 1;
                        default -> current.getAmount();
                    };
                    queue.addContainerTransaction(new ContainerTransaction(p.getName(), (byte)0, container,
                            current.getType().name(), amt, loc.getWorld().getName(),
                            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                }
            }
            case PLACE_ALL, PLACE_SOME, PLACE_ONE -> {
                if (cursor != null && cursor.getType() != Material.AIR) {
                    int amt = e.getAction() == InventoryAction.PLACE_ONE ? 1 : cursor.getAmount();
                    queue.addContainerTransaction(new ContainerTransaction(p.getName(), (byte)1, container,
                            cursor.getType().name(), amt, loc.getWorld().getName(),
                            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                }
            }
            case MOVE_TO_OTHER_INVENTORY -> {
                if (current != null && current.getType() != Material.AIR) {
                    byte dir = e.getInventory().equals(clicked) ? (byte)0 : (byte)1;
                    queue.addContainerTransaction(new ContainerTransaction(p.getName(), dir, container,
                            current.getType().name(), current.getAmount(), loc.getWorld().getName(),
                            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player p)) return;
        if (!isContainer(e.getInventory().getType())) return;
        var loc = getContainerLocation(e.getInventory());
        if (loc == null) return;

        var dragged = e.getOldCursor();
        if (dragged == null || dragged.getType() == Material.AIR) return;

        int total = e.getNewItems().values().stream()
                .mapToInt(ItemStack::getAmount)
                .sum();

        if (total > 0) {
            queue.addContainerTransaction(new ContainerTransaction(p.getName(), (byte)1,
                    e.getInventory().getType().name(), dragged.getType().name(), total,
                    loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        }
    }

    private boolean isContainer(InventoryType t) {
        return switch (t) {
            case CHEST, ENDER_CHEST, BARREL, SHULKER_BOX, HOPPER, DROPPER,
                 DISPENSER, FURNACE, BLAST_FURNACE, SMOKER, BREWING,
                 ANVIL, BEACON, ENCHANTING, MERCHANT, CARTOGRAPHY,
                 GRINDSTONE, LECTERN, LOOM, STONECUTTER, SMITHING -> true;
            default -> false;
        };
    }

    private Location getContainerLocation(Inventory i) {
        var h = i.getHolder();
        if (h instanceof BlockState bs) return bs.getLocation();
        if (h instanceof DoubleChest dc) return dc.getLocation();
        return null;
    }
}