package dev.erotoro.explorationjournal.listeners;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.SchedulerUtil;
import dev.erotoro.explorationjournal.managers.JournalManager;
import dev.erotoro.explorationjournal.models.Discovery;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for tracking player discoveries across all categories:
 * NATURE, COMBAT, SECRET, and PROCEDURAL.
 *
 * <p>Discovers are event-driven and multi-condition based.
 * Procedural discoveries hash multiple conditions simultaneously
 * to create unique discovery keys (e.g., "village_snowy_night_thunder").</p>
 *
 * <p>First-on-server discoveries are tracked and can trigger broadcasts.</p>
 */
public class DiscoveryListener implements Listener {

    /**
     * Cache tracking which discoveries each player has already made.
     * Key: player UUID, Value: set of discovery keys
     */
    private final Map<UUID, Set<String>> playerDiscoveriesCache = new ConcurrentHashMap<>();

    /**
     * Cache tracking procedural discovery combinations per player.
     * Key: player UUID, Value: set of procedural discovery keys
     */
    private final Map<UUID, Set<String>> proceduralDiscoveriesCache = new ConcurrentHashMap<>();

    /**
     * Cache tracking if a player has seen their first sunrise.
     */
    private final Map<UUID, Boolean> firstSunriseCache = new ConcurrentHashMap<>();

    /**
     * Cache tracking lava damage survival for players.
     */
    private final Map<UUID, Boolean> lavaSurvivorCache = new ConcurrentHashMap<>();

    /**
     * Cache for last procedural check time per player (throttle).
     */
    private final Map<UUID, Long> lastProceduralCheck = new ConcurrentHashMap<>();

    /**
     * The plugin instance.
     */
    private final ExplorationJournal plugin;

    /**
     * The journal manager for registering discoveries.
     */
    private final JournalManager journalManager;

    /**
     * Creates a new DiscoveryListener with the given plugin instance.
     *
     * @param plugin the plugin instance (must not be null)
     * @throws IllegalArgumentException if plugin is null
     */
    public DiscoveryListener(@NotNull ExplorationJournal plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.journalManager = plugin.getJournalManager();
    }

    /**
     * Handles player join to initialize discovery caches and check for first-join discovery.
     *
     * @param event the player join event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Initialize caches
        playerDiscoveriesCache.putIfAbsent(playerUuid, ConcurrentHashMap.newKeySet());
        proceduralDiscoveriesCache.putIfAbsent(playerUuid, ConcurrentHashMap.newKeySet());
        firstSunriseCache.putIfAbsent(playerUuid, false);

        // Load existing discoveries from database
        SchedulerUtil.runAsync(plugin, () -> {
            try {
                Set<String> existingDiscoveries = journalManager.getPlayerDiscoveryKeys(playerUuid);
                if (existingDiscoveries != null) {
                    playerDiscoveriesCache.put(playerUuid, ConcurrentHashMap.newKeySet());
                    playerDiscoveriesCache.get(playerUuid).addAll(existingDiscoveries);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load discoveries for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Handles player quit to clean up caches.
     *
     * @param event the player quit event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        playerDiscoveriesCache.remove(playerUuid);
        proceduralDiscoveriesCache.remove(playerUuid);
        firstSunriseCache.remove(playerUuid);
        lavaSurvivorCache.remove(playerUuid);
        lastProceduralCheck.remove(playerUuid);
    }

    /**
     * Handles player respawn to reset lava survival tracking.
     *
     * @param event the player respawn event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        lavaSurvivorCache.put(playerUuid, false);
    }

    /**
     * Handles entity damage to detect lava survival discovery.
     *
     * @param event the entity damage event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Check for lava damage
        if (event.getCause() != EntityDamageEvent.DamageCause.LAVA) {
            return;
        }

        UUID playerUuid = player.getUniqueId();
        lavaSurvivorCache.put(playerUuid, true);
    }

    /**
     * Handles entity death to detect boss kill discoveries in structures.
     *
     * @param event the entity death event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player player)) {
            return;
        }

        EntityType entityType = event.getEntityType();

        // Check for boss/wither kills
        if (entityType != EntityType.WITHER && entityType != EntityType.ENDER_DRAGON) {
            return;
        }

        String discoveryKey = entityType == EntityType.WITHER ? "wither-slayer" : "dragon-slayer";

        recordDiscoveryIfNew(player, discoveryKey,
                "Defeated the " + (entityType == EntityType.WITHER ? "Wither" : "Ender Dragon"),
                Discovery.DiscoveryCategory.COMBAT);
    }

    /**
     * Handles player interaction to detect secret discoveries (e.g., first portal activation).
     *
     * @param event the player interact event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Player player = event.getPlayer();

        // Check for end portal activation (eye of ender placement)
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.ENDER_EYE &&
                block.getType() == Material.END_PORTAL_FRAME) {

            recordDiscoveryIfNew(player, "portal-activator",
                    "Activated an End Portal frame",
                    Discovery.DiscoveryCategory.SECRET);
        }

        // Check for first beacon placement
        if (item != null && item.getType() == Material.BEACON) {
            recordDiscoveryIfNew(player, "beacon-builder",
                    "Placed their first beacon",
                    Discovery.DiscoveryCategory.SECRET);
        }
    }

    /**
     * Handles player movement to detect procedural and location-based discoveries.
     *
     * @param event the player move event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Throttle procedural checks to every 5 seconds
        long currentTime = System.currentTimeMillis();
        Long lastCheck = lastProceduralCheck.get(playerUuid);
        if (lastCheck != null && (currentTime - lastCheck) < 5000L) {
            return;
        }
        lastProceduralCheck.put(playerUuid, currentTime);

        // Check for first sunrise (NATURE)
        checkFirstSunrise(player);

        // Check for procedural discoveries (multi-condition combinations)
        checkProceduralDiscoveries(player);

        // Check for three-biome chunk discovery
        checkThreeBiomeChunk(player);
    }

    /**
     * Checks if the player has experienced their first sunrise.
     *
     * @param player the player to check
     */
    private void checkFirstSunrise(@NotNull Player player) {
        UUID playerUuid = player.getUniqueId();
        Boolean hasSeenSunrise = firstSunriseCache.get(playerUuid);

        if (hasSeenSunrise == null || hasSeenSunrise) {
            return;
        }

        World world = player.getWorld();
        long time = world.getTime();

        // Sunrise is around tick 0-1000 in Minecraft
        if (time >= 0 && time <= 1000) {
            firstSunriseCache.put(playerUuid, true);
            recordDiscoveryIfNew(player, "first-sunrise",
                    "Witnessed their first sunrise",
                    Discovery.DiscoveryCategory.NATURE);
        }
    }

    /**
     * Checks for procedural discoveries based on multiple simultaneous conditions.
     *
     * @param player the player
     */
    private void checkProceduralDiscoveries(@NotNull Player player) {
        World world = player.getWorld();
        org.bukkit.Location location = player.getLocation();
        Biome biome = world.getBiome(location);

        if (biome == null) {
            return;
        }

        // Gather conditions
        long time = world.getTime();
        boolean isNight = time >= 13000 || time <= 1000;
        boolean isThunder = world.isThundering();

        // Check biome type using registry key (not name())
        String biomeKey = biome.getKey().getKey();
        boolean isSnowy = biomeKey.contains("snow") || biomeKey.contains("ice");
        boolean isDesert = biomeKey.contains("desert");
        boolean isJungle = biomeKey.contains("jungle");
        boolean isOcean = biomeKey.contains("ocean");

        // Generate procedural keys for various combinations
        if (isSnowy && isNight) {
            recordProceduralDiscovery(player, "snowy_night",
                    "Explored a snowy biome at night",
                    Discovery.DiscoveryCategory.PROCEDURAL);
        }

        if (isThunder && isNight) {
            recordProceduralDiscovery(player, "thunder_night",
                    "Experienced a thunderstorm at night",
                    Discovery.DiscoveryCategory.PROCEDURAL);
        }

        if (isDesert && isThunder) {
            recordProceduralDiscovery(player, "desert_thunder",
                    "Experienced a thunderstorm in the desert",
                    Discovery.DiscoveryCategory.PROCEDURAL);
        }

        if (isJungle && isNight && !world.hasStorm()) {
            recordProceduralDiscovery(player, "jungle_clear_night",
                    "Survived a clear night in the jungle",
                    Discovery.DiscoveryCategory.PROCEDURAL);
        }

        if (isOcean && isThunder && isNight) {
            recordProceduralDiscovery(player, "ocean_storm_night",
                    "Survived a stormy night at sea",
                    Discovery.DiscoveryCategory.PROCEDURAL);
        }
    }

    /**
     * Checks if the player is in a chunk with 3+ distinct biomes.
     *
     * @param player the player
     */
    private void checkThreeBiomeChunk(@NotNull Player player) {
        UUID playerUuid = player.getUniqueId();
        Set<String> recordedProcedural = proceduralDiscoveriesCache.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());

        if (recordedProcedural.contains("three-biome-chunk")) {
            return;
        }

        org.bukkit.Location loc = player.getLocation();
        org.bukkit.Chunk chunk = loc.getChunk();
        Set<String> biomeTypes = ConcurrentHashMap.newKeySet();

        // Sample biomes at multiple points in the chunk
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                int worldX = chunk.getX() * 16 + x;
                int worldZ = chunk.getZ() * 16 + z;
                int y = loc.getBlockY();

                Biome biome = chunk.getWorld().getBiome(worldX, y, worldZ);
                if (biome != null) {
                    biomeTypes.add(biome.getKey().toString());
                }
            }
        }

        if (biomeTypes.size() >= 3) {
            recordProceduralDiscovery(player, "three-biome-chunk",
                    "Stood in a chunk containing 3+ distinct biomes",
                    Discovery.DiscoveryCategory.PROCEDURAL);
        }
    }

    /**
     * Records a discovery if the player hasn't made it before.
     * The first-on-server check and DB insert run on an async thread.
     *
     * @param player      the player
     * @param key         the discovery key
     * @param description the discovery description
     * @param category    the discovery category
     */
    private void recordDiscoveryIfNew(@NotNull Player player, @NotNull String key,
                                       @NotNull String description,
                                       @NotNull Discovery.DiscoveryCategory category) {
        UUID playerUuid = player.getUniqueId();
        Set<String> knownDiscoveries = playerDiscoveriesCache.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());

        if (knownDiscoveries.contains(key)) {
            return; // Already has this discovery
        }

        // Mark in cache immediately to prevent double-registration
        knownDiscoveries.add(key);

        // All DB operations (including isFirstDiscoveryOnServer) run on async thread
        Instant discoveredAt = Instant.now();
        SchedulerUtil.runAsync(plugin, () -> {
            try {
                Discovery discovery = new Discovery(key, description, category, false, discoveredAt);
                journalManager.registerDiscovery(playerUuid, player.getName(), discovery);
            } catch (Exception e) {
                // Revert cache on failure
                knownDiscoveries.remove(key);
                plugin.getLogger().warning("Failed to record discovery " + key + " for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Records a procedural discovery if not already recorded.
     *
     * @param player      the player
     * @param key         the procedural discovery key
     * @param description the description
     * @param category    the category
     */
    private void recordProceduralDiscovery(@NotNull Player player, @NotNull String key,
                                            @NotNull String description,
                                            @NotNull Discovery.DiscoveryCategory category) {
        UUID playerUuid = player.getUniqueId();
        Set<String> recordedProcedural = proceduralDiscoveriesCache.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());

        if (recordedProcedural.contains(key)) {
            return;
        }

        recordedProcedural.add(key);
        recordDiscoveryIfNew(player, key, description, category);
    }

    /**
     * Clears all cached data for a player.
     *
     * @param playerUuid the player's UUID
     */
    public void clearPlayerCache(@NotNull UUID playerUuid) {
        playerDiscoveriesCache.remove(playerUuid);
        proceduralDiscoveriesCache.remove(playerUuid);
        firstSunriseCache.remove(playerUuid);
        lavaSurvivorCache.remove(playerUuid);
        lastProceduralCheck.remove(playerUuid);
    }

    /**
     * Gets the set of discovery keys for a player.
     *
     * @param playerUuid the player's UUID
     * @return the set of discovery keys
     */
    @NotNull
    public Set<String> getPlayerDiscoveries(@NotNull UUID playerUuid) {
        return playerDiscoveriesCache.getOrDefault(playerUuid, ConcurrentHashMap.newKeySet());
    }
}
