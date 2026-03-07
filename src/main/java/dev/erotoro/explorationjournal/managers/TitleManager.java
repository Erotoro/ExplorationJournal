package dev.erotoro.explorationjournal.managers;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.SchedulerUtil;
import dev.erotoro.explorationjournal.database.PlayerDAO;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages exploration titles awarded to players based on biome, structure,
 * and discovery thresholds.
 *
 * <p>Title progression (ascending):</p>
 * <ol>
 *   <li><b>Новичок</b> — default title for all players</li>
 *   <li><b>Странник</b> — 15 biomes + 8 structures</li>
 *   <li><b>Картограф</b> — 30 biomes + 20 structures</li>
 *   <li><b>Первопроходец</b> — all 67 biomes</li>
 *   <li><b>Легенда Мира</b> — 100 % of all discoveries</li>
 * </ol>
 *
 * <p>Call {@link #checkAndUpdate(UUID)} after every biome/structure/discovery
 * insert. The method reads counts from DB on the async thread that called it,
 * writes the new title to DB if it changed, then returns to the entity's
 * region thread to update the chat-listener cache and deliver rewards.</p>
 */
public class TitleManager {

    /** Total biome count in vanilla 1.21. */
    private static final int TOTAL_BIOMES = 67;

    /**
     * Ordered map of titles from highest to lowest priority.
     * Checked top-down; the first match wins.
     */
    private static final LinkedHashMap<String, TitleRequirement> TITLES = new LinkedHashMap<>();

    static {
        // Order matters: highest tier first so we short-circuit on the best match.
        TITLES.put("Легенда Мира",  new TitleRequirement(0, 0, true));
        TITLES.put("Первопроходец", new TitleRequirement(TOTAL_BIOMES, 0, false));
        TITLES.put("Картограф",     new TitleRequirement(30, 20, false));
        TITLES.put("Странник",      new TitleRequirement(15, 8, false));
    }

    private static final String DEFAULT_TITLE = "Новичок";

    private final ExplorationJournal plugin;
    private final PlayerDAO playerDAO;
    private final BiomeManager biomeManager;
    private final StructureManager structureManager;
    private final DiscoveryManager discoveryManager;
    private final RewardManager rewardManager;

    /** Total number of defined discoveries loaded from discoveries.yml. */
    private int totalDiscoveries;

    /**
     * Creates a new TitleManager.
     *
     * @param plugin           the plugin instance
     * @param playerDAO        the player DAO for title persistence
     * @param biomeManager     the biome manager for biome counts
     * @param structureManager the structure manager for structure counts
     * @param discoveryManager the discovery manager for discovery counts
     * @param rewardManager    the reward manager for delivering title rewards
     */
    public TitleManager(@NotNull ExplorationJournal plugin,
                        @NotNull PlayerDAO playerDAO,
                        @NotNull BiomeManager biomeManager,
                        @NotNull StructureManager structureManager,
                        @NotNull DiscoveryManager discoveryManager,
                        @NotNull RewardManager rewardManager) {
        this.plugin = Objects.requireNonNull(plugin);
        this.playerDAO = Objects.requireNonNull(playerDAO);
        this.biomeManager = Objects.requireNonNull(biomeManager);
        this.structureManager = Objects.requireNonNull(structureManager);
        this.discoveryManager = Objects.requireNonNull(discoveryManager);
        this.rewardManager = Objects.requireNonNull(rewardManager);

        // Count defined discoveries for the "100 %" check.
        int count = 0;
        org.bukkit.configuration.file.YamlConfiguration discConfig = loadYaml("discoveries.yml");
        if (discConfig != null) {
            var section = discConfig.getConfigurationSection("discoveries");
            if (section != null) {
                count += section.getKeys(false).size();
            }
            var procSection = discConfig.getConfigurationSection("procedural");
            if (procSection != null) {
                count += procSection.getKeys(false).size();
            }
        }
        this.totalDiscoveries = Math.max(count, 1); // avoid divide-by-zero
    }

    /**
     * Reloads discoveries.yml and recalculates total discoveries count.
     * Call this when configs are reloaded.
     */
    public void reload() {
        int count = 0;
        org.bukkit.configuration.file.YamlConfiguration discConfig = loadYaml("discoveries.yml");
        if (discConfig != null) {
            var section = discConfig.getConfigurationSection("discoveries");
            if (section != null) {
                count += section.getKeys(false).size();
            }
            var procSection = discConfig.getConfigurationSection("procedural");
            if (procSection != null) {
                count += procSection.getKeys(false).size();
            }
        }
        this.totalDiscoveries = Math.max(count, 1);
    }

    /**
     * Evaluates the player's current exploration progress and updates their
     * title if a higher-tier title has been earned.
     *
     * <p><b>Thread contract:</b> this method performs blocking DB reads and
     * must be called from an async thread (typically inside
     * {@code SchedulerUtil.runAsync}). It handles returning to the correct
     * region thread internally.</p>
     *
     * @param playerUuid the player's UUID
     */
    public void checkAndUpdate(@NotNull UUID playerUuid) {
        try {
            String currentTitle = playerDAO.getCurrentTitle(playerUuid);
            int biomes = biomeManager.getBiomeCount(playerUuid);
            int structures = structureManager.getStructureCount(playerUuid);
            int discoveries = discoveryManager.getDiscoveryCount(playerUuid);

            String earned = evaluateTitle(biomes, structures, discoveries);

            // Nothing to change — already at this title or higher.
            if (earned.equals(currentTitle)) {
                // Even when the title hasn't changed, still check biome-count rewards
                // (they are independent of titles).
                checkBiomeCountRewards(playerUuid, biomes);
                return;
            }

            // Only upgrade — never downgrade a title.
            if (titleRank(earned) <= titleRank(currentTitle)) {
                checkBiomeCountRewards(playerUuid, biomes);
                return;
            }

            // Persist new title.
            playerDAO.updateTitle(playerUuid, earned);

            // Hop back to the entity's region thread for API calls.
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                String newTitle = earned;
                SchedulerUtil.runOnEntity(plugin, player, () -> {
                    // Update the ChatListener cache so the next chat message uses the new title.
                    plugin.getChatListener().updateTitleCache(playerUuid, newTitle);

                    // Deliver title reward (items, exp, title text).
                    rewardManager.grantTitleReward(player, newTitle);
                });
            }

            // Biome-count rewards (also need region thread).
            checkBiomeCountRewards(playerUuid, biomes);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to check/update title for " + playerUuid, e);
        }
    }

    /**
     * Retrieves the player's current title from the database.
     * Must be called from an async thread.
     *
     * @param playerUuid the player's UUID
     * @return the current title string
     */
    @NotNull
    public String getCurrentTitle(@NotNull UUID playerUuid) {
        try {
            return playerDAO.getCurrentTitle(playerUuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to read title for " + playerUuid, e);
            return DEFAULT_TITLE;
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Determines the best title the player qualifies for based on
     * current counts.
     */
    @NotNull
    private String evaluateTitle(int biomes, int structures, int discoveries) {
        for (Map.Entry<String, TitleRequirement> entry : TITLES.entrySet()) {
            TitleRequirement req = entry.getValue();

            if (req.requireAllDiscoveries) {
                if (discoveries >= totalDiscoveries) {
                    return entry.getKey();
                }
                continue;
            }

            if (biomes >= req.minBiomes && structures >= req.minStructures) {
                return entry.getKey();
            }
        }
        return DEFAULT_TITLE;
    }

    /**
     * Returns a numeric rank for a title (higher = better) to prevent
     * accidental downgrades.
     */
    private int titleRank(@NotNull String title) {
        return switch (title) {
            case "Легенда Мира"  -> 5;
            case "Первопроходец" -> 4;
            case "Картограф"     -> 3;
            case "Странник"      -> 2;
            default              -> 1; // Новичок
        };
    }

    /**
     * Checks and delivers biome-count milestone rewards.
     * Must be called from an async context — hops to region thread for delivery.
     */
    private void checkBiomeCountRewards(@NotNull UUID playerUuid, int biomeCount) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        SchedulerUtil.runOnEntity(plugin, player, () ->
                rewardManager.grantBiomeCountRewards(player, biomeCount));
    }

    /**
     * Loads a YAML file from the plugin's data folder. Returns null on failure.
     */
    @org.jetbrains.annotations.Nullable
    private org.bukkit.configuration.file.YamlConfiguration loadYaml(@NotNull String fileName) {
        java.io.File file = new java.io.File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        if (!file.exists()) {
            return null;
        }
        return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
    }

    // ------------------------------------------------------------------
    // Requirement record
    // ------------------------------------------------------------------

    /**
     * Describes the exploration thresholds for a single title.
     *
     * @param minBiomes             minimum biome count required
     * @param minStructures         minimum structure count required
     * @param requireAllDiscoveries if true, the player must own every defined discovery
     */
    private record TitleRequirement(int minBiomes, int minStructures, boolean requireAllDiscoveries) {}
}
