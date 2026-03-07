package dev.erotoro.explorationjournal;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Utility class for scheduling tasks on both Folia and Paper.
 * Handles region-specific threading, async operations, and global tasks.
 */
public final class SchedulerUtil {

    public static final boolean IS_FOLIA = isFoliaPresent();

    private static boolean isFoliaPresent() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private SchedulerUtil() {}

    /**
     * Run a task on the region thread that owns the entity.
     *
     * @param plugin The plugin instance.
     * @param entity The entity to schedule for.
     * @param task   The task to run.
     */
    public static void runOnEntity(Plugin plugin, Entity entity, Runnable task) {
        if (IS_FOLIA) {
            entity.getScheduler().run(plugin, t -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run a task on the region thread that owns the location.
     *
     * @param plugin The plugin instance.
     * @param loc    The location to schedule for.
     * @param task   The task to run.
     */
    public static void runOnLocation(Plugin plugin, Location loc, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getRegionScheduler().run(plugin, loc, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run an asynchronous task.
     *
     * @param plugin The plugin instance.
     * @param task   The task to run.
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Run a repeating task on the global region scheduler (Folia) or main thread (Paper).
     *
     * @param plugin      The plugin instance.
     * @param task        The task to run.
     * @param delayTicks  Delay in ticks before first run.
     * @param periodTicks Period in ticks between runs.
     */
    public static void runGlobalRepeating(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    /**
     * Cancel all tasks for the plugin.
     *
     * @param plugin The plugin instance.
     */
    public static void cancelAll(Plugin plugin) {
        if (IS_FOLIA) {
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }
}
