package dev.erotoro.explorationjournal.managers;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.SchedulerUtil;
import dev.erotoro.explorationjournal.database.BiomeDAO;
import dev.erotoro.explorationjournal.database.DatabaseManager;
import dev.erotoro.explorationjournal.models.JournalEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BiomeManager {

    private final ExplorationJournal plugin;
    private final BiomeDAO biomeDAO;
    private final DatabaseManager databaseManager;

    private final Map<UUID, Long> biomeEntryTimeCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> currentBiomeKeyCache = new ConcurrentHashMap<>();
    private final Map<String, String> biomeDisplayNames = new HashMap<>();

    public BiomeManager(@NotNull ExplorationJournal plugin,
                        @NotNull BiomeDAO biomeDAO,
                        @NotNull DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.biomeDAO = biomeDAO;
        this.databaseManager = databaseManager;
        loadBiomeDisplayNames();
    }

    public void registerDiscovery(@NotNull UUID uuid, @NotNull String name, @NotNull String key, 
                                  @NotNull String dim, int x, int z, @NotNull Instant time) {
        try {
            boolean alreadyKnown = hasBiome(uuid, key);
            biomeDAO.addBiomeDiscovery(uuid, key, dim, x, z);

            if (!alreadyKnown) {
                notifyPlayer(uuid, key, isFirstOnServer(key));
                if (plugin.getTitleManager() != null) {
                    plugin.getTitleManager().checkAndUpdate(uuid);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void updateTimeSpent(@NotNull UUID uuid, @NotNull String key) {
        Long entryTime = biomeEntryTimeCache.remove(uuid);
        currentBiomeKeyCache.remove(uuid);
        if (entryTime != null) {
            int seconds = (int) ((System.currentTimeMillis() - entryTime) / 1000);
            if (seconds > 0) {
                try { biomeDAO.incrementTimeSpent(uuid, key, seconds); }
                catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    public void startTimer(@NotNull UUID uuid, @NotNull Biome biome, long time) {
        biomeEntryTimeCache.put(uuid, time);
        currentBiomeKeyCache.put(uuid, biome.getKey().toString());
    }

    public int getBiomeCount(@NotNull UUID uuid) {
        try { return biomeDAO.getDiscoveredCount(uuid); }
        catch (SQLException e) { return 0; }
    }

    @NotNull
    public List<JournalEntry> getDiscoveredBiomes(@NotNull UUID uuid) {
        List<JournalEntry> biomes = new ArrayList<>();
        String sql = "SELECT * FROM biome_discoveries WHERE player_uuid = ? ORDER BY discovered_at ASC";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    biomes.add(JournalEntry.ofBiome(
                            rs.getString("biome_key"),
                            rs.getString("dimension"),
                            rs.getInt("coord_x"),
                            rs.getInt("coord_z"),
                            rs.getInt("time_spent_seconds"),
                            Instant.now() // placeholder
                    ));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return biomes;
    }

    private void notifyPlayer(UUID uuid, String key, boolean first) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        String display = biomeDisplayNames.getOrDefault(key, key);
        SchedulerUtil.runOnEntity(plugin, player, () -> {
            player.sendMessage(Component.text("Вы открыли новый биом: ", NamedTextColor.GOLD)
                    .append(Component.text(display, NamedTextColor.YELLOW, TextDecoration.BOLD)));
            if (first) {
                Bukkit.broadcast(Component.text("★ ", NamedTextColor.GOLD)
                        .append(Component.text(player.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                        .append(Component.text(" первым открыл биом ", NamedTextColor.GOLD))
                        .append(Component.text(display, NamedTextColor.AQUA, TextDecoration.BOLD)));
            }
        });
    }

    private boolean hasBiome(UUID uuid, String key) throws SQLException {
        String sql = "SELECT 1 FROM biome_discoveries WHERE player_uuid = ? AND biome_key = ? LIMIT 1";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private boolean isFirstOnServer(String key) throws SQLException {
        String sql = "SELECT COUNT(*) FROM biome_discoveries WHERE biome_key = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) <= 1; }
        }
    }

    public void clearCache(UUID uuid) {
        biomeEntryTimeCache.remove(uuid);
        currentBiomeKeyCache.remove(uuid);
    }

    public void reload() {
        biomeDisplayNames.clear();
        loadBiomeDisplayNames();
    }

    @NotNull
    public String getBiomeDisplayName(@NotNull String biomeKey) {
        String direct = biomeDisplayNames.get(biomeKey);
        if (direct != null) return direct;

        if (!biomeKey.contains(":")) {
            String namespaced = biomeDisplayNames.get("minecraft:" + biomeKey);
            if (namespaced != null) return namespaced;
        }

        // Fallback: make key readable (remove namespace, replace underscores).
        String key = biomeKey;
        int colon = key.indexOf(':');
        if (colon >= 0) key = key.substring(colon + 1);
        return key.replace('_', ' ');
    }

    private void loadBiomeDisplayNames() {
        File file = new File(plugin.getDataFolder(), "biomes.yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("biomes");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            biomeDisplayNames.put(key, section.getString(key + ".display", key));
        }
    }
}
