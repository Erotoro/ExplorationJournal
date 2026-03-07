package dev.erotoro.explorationjournal.integrations;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.SchedulerUtil;
import dev.erotoro.explorationjournal.managers.JournalManager;
import dev.erotoro.explorationjournal.models.PlayerJournal;
import dev.erotoro.explorationjournal.models.TopEntry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * PlaceholderAPI expansion for Exploration Journal.
 *
 * <p>Registered placeholders:</p>
 * <ul>
 *   <li>{@code %ej_biomes_found%} — number of biomes discovered by the player</li>
 *   <li>{@code %ej_biomes_total%} — total vanilla biome count (67)</li>
 *   <li>{@code %ej_structures_found%} — number of structures discovered</li>
 *   <li>{@code %ej_discoveries_found%} — number of unique discoveries</li>
 *   <li>{@code %ej_title%} — the player's current exploration title</li>
 *   <li>{@code %ej_journal_level%} — the player's journal tier (0–4)</li>
 *   <li>{@code %ej_top_biomes_1%} through {@code %ej_top_biomes_10%} — top biome leaderboard</li>
 *   <li>{@code %ej_rank_biomes%} — the player's own rank in the biome leaderboard</li>
 * </ul>
 *
 * <p>All database reads are cached in-memory per player and refreshed every
 * 30 seconds by a global repeating task. Placeholder resolution itself is
 * always non-blocking.</p>
 */
public class PAPIExpansion extends PlaceholderExpansion {

    private static final int TOTAL_BIOMES = 67;
    private static final long CACHE_REFRESH_TICKS = 600L; // 30 seconds

    private final ExplorationJournal plugin;
    private final JournalManager journalManager;

    /** Per-player snapshot cache: UUID → cached counts. */
    private final Map<UUID, PlayerSnapshot> snapshotCache = new ConcurrentHashMap<>();

    /** Top-biome leaderboard cache (refreshed periodically). */
    private volatile List<TopEntry> topBiomesCache = List.of();

    /**
     * Creates and starts the PAPI expansion.
     *
     * @param plugin the plugin instance
     */
    public PAPIExpansion(@NotNull ExplorationJournal plugin) {
        this.plugin = plugin;
        this.journalManager = plugin.getJournalManager();

        // Periodic cache refresh so placeholder reads never block.
        SchedulerUtil.runGlobalRepeating(plugin, this::refreshAllCaches,
                20L, CACHE_REFRESH_TICKS);
    }

    // ------------------------------------------------------------------
    // PlaceholderExpansion contract
    // ------------------------------------------------------------------

    @Override
    public @NotNull String getIdentifier() {
        return "ej";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Erotoro";
    }

    @Override
    public @NotNull String getVersion() {
        var meta = plugin.getPluginMeta();
        return meta != null ? meta.getVersion() : "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // Survive PAPI reloads.
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer offlinePlayer,
                                       @NotNull String params) {
        if (offlinePlayer == null) {
            return "";
        }

        UUID uuid = offlinePlayer.getUniqueId();
        PlayerSnapshot snap = snapshotCache.get(uuid);

        return switch (params.toLowerCase()) {
            case "biomes_found" -> snap != null ? String.valueOf(snap.biomes) : "0";
            case "biomes_total" -> String.valueOf(TOTAL_BIOMES);
            case "structures_found" -> snap != null ? String.valueOf(snap.structures) : "0";
            case "discoveries_found" -> snap != null ? String.valueOf(snap.discoveries) : "0";
            case "title" -> snap != null ? snap.title : "Новичок";
            case "journal_level" -> snap != null ? String.valueOf(snap.journalLevel) : "0";
            case "rank_biomes" -> resolvePlayerRank(uuid);
            default -> resolveTopPlaceholder(params);
        };
    }

    // ------------------------------------------------------------------
    // Top placeholders: %ej_top_biomes_N%
    // ------------------------------------------------------------------

    /**
     * Resolves {@code top_biomes_1} … {@code top_biomes_10}.
     */
    @Nullable
    private String resolveTopPlaceholder(@NotNull String params) {
        if (!params.startsWith("top_biomes_")) {
            return null; // Unknown placeholder.
        }
        String indexStr = params.substring("top_biomes_".length());
        try {
            int index = Integer.parseInt(indexStr);
            if (index < 1 || index > topBiomesCache.size()) {
                return "-";
            }
            TopEntry entry = topBiomesCache.get(index - 1);
            return entry.playerName() + " - " + entry.value();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Resolves the requesting player's rank in the biome leaderboard.
     */
    @NotNull
    private String resolvePlayerRank(@NotNull UUID uuid) {
        for (TopEntry entry : topBiomesCache) {
            if (entry.playerUuid().equals(uuid)) {
                return String.valueOf(entry.rank());
            }
        }
        return "-";
    }

    // ------------------------------------------------------------------
    // Cache refresh (runs on global/async thread)
    // ------------------------------------------------------------------

    /**
     * Refreshes all snapshot caches by reading from the database.
     * Launched as a repeating task — runs asynchronously so the
     * main/global thread is never blocked.
     */
    private void refreshAllCaches() {
        SchedulerUtil.runAsync(plugin, () -> {
            try {
                // Refresh top leaderboard.
                topBiomesCache = journalManager.getTopBiomes(10);

                // Refresh per-player snapshots for all cached players.
                for (UUID uuid : snapshotCache.keySet()) {
                    refreshPlayer(uuid);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "PAPI cache refresh failed", e);
            }
        });
    }

    /**
     * Populates or refreshes the snapshot for a single player.
     * Must be called from an async thread.
     */
    private void refreshPlayer(@NotNull UUID uuid) {
        try {
            PlayerJournal journal = journalManager.getJournal(uuid);
            int biomes = journalManager.getBiomeCount(uuid);
            int structures = journalManager.getStructureCount(uuid);
            int discoveries = journalManager.getDiscoveryCount(uuid);

            String title = journal != null ? journal.currentTitle() : "Новичок";
            int level = journal != null ? journal.journalLevel() : 0;

            snapshotCache.put(uuid, new PlayerSnapshot(biomes, structures, discoveries, title, level));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to refresh PAPI snapshot for " + uuid, e);
        }
    }

    /**
     * Ensures a player's snapshot exists in the cache (call on join).
     *
     * @param uuid the player's UUID
     */
    public void cachePlayer(@NotNull UUID uuid) {
        // Seed with a default so placeholders don't return null
        // before the first async refresh.
        snapshotCache.putIfAbsent(uuid, new PlayerSnapshot(0, 0, 0, "Новичок", 0));
        SchedulerUtil.runAsync(plugin, () -> refreshPlayer(uuid));
    }

    /**
     * Removes a player from the snapshot cache (call on quit).
     *
     * @param uuid the player's UUID
     */
    public void uncachePlayer(@NotNull UUID uuid) {
        snapshotCache.remove(uuid);
    }

    // ------------------------------------------------------------------
    // Snapshot record
    // ------------------------------------------------------------------

    /**
     * Immutable snapshot of a player's exploration counts for fast reads.
     */
    private record PlayerSnapshot(int biomes, int structures, int discoveries,
                                  String title, int journalLevel) {}
}
