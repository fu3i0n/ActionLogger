package carnage.actionLogger.listeners;

import carnage.actionLogger.models.LogEntry;
import carnage.actionLogger.queue.LogQueue;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

public class MiscListeners implements Listener {
    private final LogQueue queue;

    public MiscListeners(LogQueue queue) { this.queue = queue; }

    @EventHandler public void onJoin(PlayerJoinEvent e) { queue.addLog(new LogEntry(e.getPlayer().getName(), "Login", "", e.getPlayer().getLocation())); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { queue.addLog(new LogEntry(e.getPlayer().getName(), "Logout", "", e.getPlayer().getLocation())); }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        var stack = e.getItemDrop().getItemStack();
        String detail = stack.getType() + " x" + stack.getAmount();
        queue.addLog(new LogEntry(e.getPlayer().getName(), "Item Drop", detail, e.getPlayer().getLocation()));
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent e) {
        var stack = e.getItem().getItemStack();
        String detail = stack.getType() + " x" + stack.getAmount();
        queue.addLog(new LogEntry(e.getPlayer().getName(), "Item Pickup", detail, e.getPlayer().getLocation()));
    }
}