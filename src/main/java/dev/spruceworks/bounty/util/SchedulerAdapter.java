package dev.spruceworks.bounty.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Single choke point for scheduler access. If Folia support is added later,
 * only this class has to change (swap the Bukkit scheduler for the region
 * and async schedulers) — the rest of the plugin stays untouched.
 */
public final class SchedulerAdapter {

    private final Plugin plugin;

    public SchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    public void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(this.plugin, task);
    }

    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, task);
    }

    public void runLaterSync(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(this.plugin, task, delayTicks);
    }

    public void runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, task, delayTicks, periodTicks);
    }
}
