package xyz.dimseal.godHand.engine;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xyz.dimseal.godHand.hand.HandManager;

/** Owns the single 20 Hz GodHand update/render loop. */
public final class GodHandEngine {

    private final JavaPlugin plugin;
    private final HandManager handManager;
    private BukkitTask task;

    public GodHandEngine(JavaPlugin plugin, HandManager handManager) {
        this.plugin = plugin;
        this.handManager = handManager;
    }

    public void start() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, handManager::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        handManager.clear();
    }
}
