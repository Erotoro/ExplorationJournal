package dev.erotoro.explorationjournal.listeners;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.SchedulerUtil;
import dev.erotoro.explorationjournal.managers.JournalManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for tracking player biome movements and recording biome discoveries.
 * Uses ConcurrentHashMap for thread-safe caching on both Paper and Folia.
 * Implements 2000ms throttle to prevent excessive database writes.
 */
public class BiomeListener implements Listener {

    /**
     * Cache mapping player UUID to their last known biome.
     * ConcurrentHashMap ensures thread-safety across Folia region threads.
     */
    private final Map<UUID, Biome> biomeCache = new ConcurrentHashMap<>();

    /**
     * Cache mapping player UUID to the timestamp of their last biome check.
     * Used for throttling biome detection (2000ms minimum interval).
     */
    private final Map<UUID, Long> lastCheckCache = new ConcurrentHashMap<>();

    /**
     * The plugin instance for accessing managers and configuration.
     */
    private final ExplorationJournal plugin;

    /**
     * The journal manager for registering biome discoveries.
     */
    private final JournalManager journalManager;

    /**
     * Throttle interval in milliseconds (2000ms = 2 seconds).
     */
    private static final long THROTTLE_MS = 2000L;

    /**
     * Creates a new BiomeListener with the given plugin instance.
     *
     * @param plugin the plugin instance (must not be null)
     * @throws IllegalArgumentException if plugin is null
     */
    public BiomeListener(@NotNull ExplorationJournal plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.journalManager = plugin.getJournalManager();
    }

    /**
     * Handles player movement to detect biome changes.
     * Throttled to 2000ms intervals to prevent excessive processing.
     *
     * @param event the player move event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Throttle check - skip if too soon since last check
        long currentTime = System.currentTimeMillis();
        Long lastCheck = lastCheckCache.get(playerUuid);
        if (lastCheck != null && (currentTime - lastCheck) < THROTTLE_MS) {
            return;
        }

        // Update last check timestamp
        lastCheckCache.put(playerUuid, currentTime);

        // Get current biome at player's location
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Biome currentBiome = to.getWorld().getBiome(to);
        if (currentBiome == null) {
            return;
        }

        // Check if biome actually changed
        Biome previousBiome = biomeCache.get(playerUuid);
        if (previousBiome != null && previousBiome.equals(currentBiome)) {
            return;
        }

        // Biome changed - record the discovery
        Biome oldBiome = previousBiome;
        biomeCache.put(playerUuid, currentBiome);

        // Record biome discovery asynchronously
        recordBiomeDiscovery(player, currentBiome, oldBiome, to);
    }

    /**
     * Handles world changes to clear biome cache and record the new world's biome.
     *
     * @param event the player changed world event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Clear cache for this player - they're in a new world
        biomeCache.remove(playerUuid);

        // Record the biome in the new world after a short delay
        // This ensures the player is fully loaded in the new world
        SchedulerUtil.runOnEntity(plugin, player, () -> {
            Location loc = player.getLocation();
            Biome biome = loc.getWorld().getBiome(loc);
            if (biome != null) {
                biomeCache.put(playerUuid, biome);
                // Reset throttle to allow immediate recording
                lastCheckCache.remove(playerUuid);
            }
        });
    }

    /**
     * Records a biome discovery asynchronously and handles time tracking for the previous biome.
     *
     * @param player       the player who changed biomes
     * @param newBiome     the new biome entered
     * @param oldBiome     the previous biome (may be null)
     * @param location     the location where the biome change occurred
     */
    private void recordBiomeDiscovery(@NotNull Player player, @NotNull Biome newBiome,
                                       Biome oldBiome, @NotNull Location location) {
        UUID playerUuid = player.getUniqueId();
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        // Get biome key and dimension
        String biomeKey = getBiomeKey(newBiome);
        String dimension = world.getKey().toString();
        int coordX = location.getBlockX();
        int coordZ = location.getBlockZ();
        Instant discoveredAt = Instant.now();

        // Run async to record the biome in the database
        SchedulerUtil.runAsync(plugin, () -> {
            try {
                // Record the new biome discovery
                journalManager.registerBiome(playerUuid, player.getName(), biomeKey, dimension,
                        coordX, coordZ, discoveredAt);

                // If there was a previous biome, update time spent
                if (oldBiome != null) {
                    String oldBiomeKey = getBiomeKey(oldBiome);
                    journalManager.updateBiomeTime(playerUuid, oldBiomeKey);
                }

                // Return to region thread to update time tracking cache
                SchedulerUtil.runOnEntity(plugin, player, () -> {
                    // Update biome entry time for the new biome
                    journalManager.startBiomeTimer(playerUuid, newBiome, System.currentTimeMillis());
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to record biome discovery for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Gets the namespaced key for a biome using the Keyed interface.
     * In Paper 1.21+ Biome implements Keyed, so getKey() is available.
     *
     * @param biome the biome
     * @return the biome's namespaced key (e.g., "minecraft:plains")
     */
    @NotNull
    private String getBiomeKey(@NotNull Biome biome) {
        return biome.getKey().toString();
    }

    /**
     * Clears all cached data for a player (used on logout or data reset).
     *
     * @param playerUuid the player's UUID
     */
    public void clearPlayerCache(@NotNull UUID playerUuid) {
        biomeCache.remove(playerUuid);
        lastCheckCache.remove(playerUuid);
    }

    /**
     * Gets the current cached biome for a player.
     *
     * @param playerUuid the player's UUID
     * @return the cached biome, or null if not cached
     */
    public Biome getCachedBiome(@NotNull UUID playerUuid) {
        return biomeCache.get(playerUuid);
    }

    /**
     * Gets the timestamp of the last biome check for a player.
     *
     * @param playerUuid the player's UUID
     * @return the last check timestamp in milliseconds, or null if never checked
     */
    public Long getLastCheckTime(@NotNull UUID playerUuid) {
        return lastCheckCache.get(playerUuid);
    }
}
