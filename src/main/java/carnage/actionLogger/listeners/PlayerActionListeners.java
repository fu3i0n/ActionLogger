package carnage.actionLogger.listeners;

import carnage.actionLogger.models.LogEntry;
import carnage.actionLogger.queue.LogQueue;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;

public class PlayerActionListeners implements Listener {
    private final LogQueue queue;

    public PlayerActionListeners(LogQueue queue) { this.queue = queue; }

    @EventHandler public void onPlace(BlockPlaceEvent e) { log(e.getPlayer().getName(), "Block Placed", e.getBlock().getType().name(), e.getBlock().getLocation()); }
    @EventHandler public void onBreak(BlockBreakEvent e) { log(e.getPlayer().getName(), "Block Broken", e.getBlock().getType().name(), e.getBlock().getLocation()); }
    @SuppressWarnings("deprecation")
    @EventHandler public void onChat(AsyncPlayerChatEvent e) { log(e.getPlayer().getName(), "Chat", e.getMessage(), e.getPlayer().getLocation()); }
    @EventHandler public void onCommand(PlayerCommandPreprocessEvent e) { log(e.getPlayer().getName(), "Command", e.getMessage(), e.getPlayer().getLocation()); }

    private void log(String p, String a, String d, org.bukkit.Location l) {
        queue.addLog(new LogEntry(p, a, d, l));
    }
}