package carnage.actionLogger.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import carnage.actionLogger.models.LogEntry;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class LogViewerGUI {

    private static final int SIZE = 54;
    private static final int LOGS_PER_PAGE = 45;
    ;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public static void open(Player player, List<LogEntry> logs, int page) {
        Inventory inv = Bukkit.createInventory(player, SIZE, Component.text("Action Logs — Page " + (page + 1)).color(NamedTextColor.AQUA));

        int start = page * LOGS_PER_PAGE;
        int end = Math.min(start + LOGS_PER_PAGE, logs.size());

        for (int i = start, slot = 0; i < end; i++, slot++) {
            inv.setItem(slot, createItem(logs.get(i)));
        }

        if (page > 0) inv.setItem(SIZE - 9, item("Previous Page", Material.ARROW));
        if (end < logs.size()) inv.setItem(SIZE - 1, item("Next Page", Material.ARROW));

        player.openInventory(inv);
    }

    private static ItemStack createItem(LogEntry e) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text("[" + e.getAction() + "] " + e.getPlayerName())
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Detail: ").color(NamedTextColor.GRAY)
                .append(Component.text(e.getDetail()).color(NamedTextColor.WHITE)));
        lore.add(Component.text("Time: ").color(NamedTextColor.GRAY)
                .append(Component.text(e.getTimestamp().format(TIME)).color(NamedTextColor.WHITE)));
        lore.add(Component.text("Location: ").color(NamedTextColor.GRAY)
                .append(Component.text(locStr(e.getLocation())).color(NamedTextColor.WHITE)));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack item(String name, Material mat) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(name).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            i.setItemMeta(m);
        }
        return i;
    }

    private static String locStr(org.bukkit.Location l) {
        if (l == null || l.getWorld() == null) return "Unknown";
        return String.format("%s (%d, %d, %d)",
                l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }
}