package dev.erotoro.explorationjournal.managers;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.database.DatabaseManager;
import dev.erotoro.explorationjournal.database.PlayerDAO;
import dev.erotoro.explorationjournal.models.Discovery;
import dev.erotoro.explorationjournal.models.JournalEntry;
import dev.erotoro.explorationjournal.models.PlayerJournal;
import dev.erotoro.explorationjournal.models.TopEntry;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager facade for Exploration Journal.
 * Bridges all subsystems and manages the player journal cache.
 */
public class JournalManager {

    private final PlayerDAO playerDAO;
    
    private final BiomeManager biomeManager;
    private final StructureManager structureManager;
    private final DiscoveryManager discoveryManager;
    private final TopManager topManager;

    private final ConcurrentHashMap<UUID, PlayerJournal> journalCache = new ConcurrentHashMap<>();

    public JournalManager(@NotNull ExplorationJournal plugin,
                          @NotNull DatabaseManager databaseManager,
                          @NotNull PlayerDAO playerDAO,
                          @NotNull BiomeManager biomeManager,
                          @NotNull StructureManager structureManager,
                          @NotNull DiscoveryManager discoveryManager,
                          @NotNull TopManager topManager) {
        this.playerDAO = playerDAO;
        this.biomeManager = biomeManager;
        this.structureManager = structureManager;
        this.discoveryManager = discoveryManager;
        this.topManager = topManager;
    }

    @Nullable
    public PlayerJournal getJournal(@NotNull UUID uuid) {
        PlayerJournal cached = journalCache.get(uuid);
        if (cached != null) return cached;

        try {
            String title = playerDAO.getCurrentTitle(uuid);
            // In a real implementation, level would be read from DB
            PlayerJournal journal = new PlayerJournal(uuid, "Неизвестно", title, 1);
            journalCache.put(uuid, journal);
            return journal;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public PlayerJournal getCachedJournal(@NotNull UUID uuid) {
        return journalCache.get(uuid);
    }

    // --- Biomes ---

    public void registerBiome(@NotNull UUID uuid, @NotNull String name, @NotNull String key, 
                              @NotNull String dim, int x, int z, @NotNull Instant time) {
        biomeManager.registerDiscovery(uuid, name, key, dim, x, z, time);
    }

    public void updateBiomeTime(@NotNull UUID uuid, @NotNull String key) {
        biomeManager.updateTimeSpent(uuid, key);
    }

    public void startBiomeTimer(@NotNull UUID uuid, @NotNull Biome biome, long time) {
        biomeManager.startTimer(uuid, biome, time);
    }

    public List<JournalEntry> getPlayerBiomes(@NotNull UUID uuid) {
        return biomeManager.getDiscoveredBiomes(uuid);
    }

    public int getBiomeCount(@NotNull UUID uuid) {
        return biomeManager.getBiomeCount(uuid);
    }

    // --- Structures ---

    public void registerStructure(@NotNull UUID uuid, @NotNull String name, @NotNull String key, 
                                  @NotNull String dim, int x, int z, @NotNull Instant time) {
        structureManager.registerDiscovery(uuid, name, key, dim, x, z, time);
    }

    public void markStructureExplored(@NotNull UUID uuid, @NotNull String key) {
        structureManager.markExplored(uuid, key);
    }

    public List<JournalEntry> getPlayerStructures(@NotNull UUID uuid) {
        return structureManager.getDiscoveredStructures(uuid);
    }

    public int getStructureCount(@NotNull UUID uuid) {
        return structureManager.getStructureCount(uuid);
    }

    // --- Discoveries ---

    public void registerDiscovery(@NotNull UUID uuid, @NotNull String name, @NotNull Discovery discovery) {
        discoveryManager.registerDiscovery(uuid, name, discovery);
    }

    public Set<String> getPlayerDiscoveryKeys(@NotNull UUID uuid) {
        return discoveryManager.getPlayerDiscoveryKeys(uuid);
    }

    @NotNull
    public String getBiomeDisplayName(@NotNull String biomeKey) {
        return biomeManager.getBiomeDisplayName(biomeKey);
    }

    @NotNull
    public String getStructureDisplayName(@NotNull String structureKey) {
        return structureManager.getStructureDisplayName(structureKey);
    }

    @NotNull
    public String getDiscoveryDisplayName(@NotNull String discoveryKey) {
        return discoveryManager.getDiscoveryDisplayName(discoveryKey);
    }

    public int getDiscoveryCount(@NotNull UUID uuid) {
        return discoveryManager.getDiscoveryCount(uuid);
    }

    // --- Data Management ---

    public void updateJournalLevel(@NotNull UUID uuid, int level) throws Exception {
        playerDAO.updateJournalLevel(uuid, level);
        PlayerJournal cached = journalCache.get(uuid);
        if (cached != null) {
            journalCache.put(uuid, new PlayerJournal(uuid, cached.playerName(), cached.currentTitle(), level));
        }
    }

    public void clearCache(@NotNull UUID uuid) {
        journalCache.remove(uuid);
        biomeManager.clearCache(uuid);
        structureManager.clearCache(uuid);
        discoveryManager.clearCache(uuid);
    }

    public void resetPlayerData(@NotNull UUID uuid) {
        clearCache(uuid);
    }

    // --- Leaderboards ---

    public List<TopEntry> getTopBiomes(int limit) { return topManager.getTopBiomes(limit); }
    public List<TopEntry> getTopStructures(int limit) { return topManager.getTopStructures(limit); }
    public List<TopEntry> getTopDiscoveries(int limit) { return topManager.getTopDiscoveries(limit); }

    public void reload() {
        biomeManager.reload();
        structureManager.reload();
        discoveryManager.reload();
        journalCache.clear();
    }
}
