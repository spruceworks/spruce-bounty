package dev.spruceworks.bounty.util;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Single choke point for scheduler access.
 *
 * <p>Built on Paper's region-aware schedulers ({@link GlobalRegionScheduler},
 * {@link AsyncScheduler}) instead of {@code Bukkit.getScheduler()}. On plain
 * Paper they behave exactly like the legacy scheduler (global tasks run on the
 * main thread); on Folia they are the only schedulers that exist —
 * {@code Bukkit.getScheduler()} throws there. One code path, both platforms,
 * no runtime detection to get wrong.
 *
 * <p>Nothing in this plugin needs entity or location affinity: storage work is
 * async, the economy retry and the cooldown sweep are global. If a future task
 * must mutate a specific player's world state on Folia, add a method here that
 * routes through {@code entity.getScheduler()} — never the global scheduler.
 */
public final class SchedulerAdapter {

    private static final long MS_PER_TICK = 50L;

    private final Plugin plugin;

    public SchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Runs on the global region (Paper: main thread) as soon as possible. */
    public void runSync(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(this.plugin, task);
    }

    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(this.plugin, scheduled -> task.run());
    }

    /** Folia rejects a zero delay; the legacy scheduler treated it as "next tick". */
    public void runLaterSync(Runnable task, long delayTicks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, scheduled -> task.run(),
                Math.max(1L, delayTicks));
    }

    public void runTimerAsync(Runnable task, long delayTicks, long periodTicks) {
        Bukkit.getAsyncScheduler().runAtFixedRate(this.plugin, scheduled -> task.run(),
                Math.max(1L, delayTicks) * MS_PER_TICK,
                Math.max(1L, periodTicks) * MS_PER_TICK,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Cancels everything this plugin scheduled. The legacy scheduler did this
     * implicitly on plugin disable; the region schedulers do not, so call it
     * from {@code onDisable()} before closing resources a late task might touch.
     */
    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(this.plugin);
        Bukkit.getAsyncScheduler().cancelTasks(this.plugin);
    }
}
