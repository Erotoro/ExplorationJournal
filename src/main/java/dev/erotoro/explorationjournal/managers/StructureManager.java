package dev.erotoro.explorationjournal.managers;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.SchedulerUtil;
import dev.erotoro.explorationjournal.database.DatabaseManager;
import dev.erotoro.explorationjournal.database.StructureDAO;
import dev.erotoro.explorationjournal.models.JournalEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
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

public class StructureManager {

    private final ExplorationJournal plugin;
    private final StructureDAO structureDAO;
    private final DatabaseManager databaseManager;
    private final Map<String, String> structureDisplayNames = new HashMap<>();

    public StructureManager(@NotNull ExplorationJournal plugin,
                            @NotNull StructureDAO structureDAO,
                            @NotNull DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.structureDAO = structureDAO;
        this.databaseManager = databaseManager;
        loadStructureDisplayNames();
    }

    public void registerDiscovery(@NotNull UUID uuid, @NotNull String name, @NotNull String key, 
                                  @NotNull String dim, int x, int z, @NotNull Instant time) {
        try {
            boolean alreadyKnown = hasStructure(uuid, key, x, z);
            structureDAO.addStructureDiscovery(uuid, key, x, z);

            if (!alreadyKnown) {
                notifyPlayer(uuid, key);
                if (plugin.getTitleManager() != null) {
                    plugin.getTitleManager().checkAndUpdate(uuid);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void markExplored(@NotNull UUID uuid, @NotNull String key) {
        try {
            structureDAO.markExplored(uuid, key);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public int getStructureCount(@NotNull UUID uuid) {
        try { return structureDAO.getExploredCount(uuid); }
        catch (SQLException e) { return 0; }
    }

    @NotNull
    public List<JournalEntry> getDiscoveredStructures(@NotNull UUID uuid) {
        List<JournalEntry> structures = new ArrayList<>();
        String sql = "SELECT * FROM structure_discoveries WHERE player_uuid = ? ORDER BY discovered_at ASC";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    structures.add(JournalEntry.ofStructure(
                            rs.getString("structure_key"),
                            "unknown",
                            rs.getInt("coord_x"),
                            rs.getInt("coord_z"),
                            rs.getInt("explored") == 1 ? 300 : 0,
                            Instant.now() // placeholder
                    ));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return structures;
    }

    private void notifyPlayer(UUID uuid, String key) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        String display = structureDisplayNames.getOrDefault(key, key);
        SchedulerUtil.runOnEntity(plugin, player, () -> {
            player.sendMessage(Component.text("Вы обнаружили структуру: ", NamedTextColor.GOLD)
                    .append(Component.text(display, NamedTextColor.YELLOW, TextDecoration.BOLD)));
        });
    }

    private boolean hasStructure(UUID uuid, String key, int x, int z) throws SQLException {
        String sql = "SELECT 1 FROM structure_discoveries WHERE player_uuid = ? AND structure_key = ? AND coord_x = ? AND coord_z = ? LIMIT 1";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            ps.setInt(3, x);
            ps.setInt(4, z);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public void clearCache(UUID uuid) {}

    public void reload() {
        structureDisplayNames.clear();
        loadStructureDisplayNames();
    }

    @NotNull
    public String getStructureDisplayName(@NotNull String structureKey) {
        String direct = structureDisplayNames.get(structureKey);
        if (direct != null) return direct;

        if (!structureKey.contains(":")) {
            String namespaced = structureDisplayNames.get("minecraft:" + structureKey);
            if (namespaced != null) return namespaced;
        }

        String key = structureKey;
        int colon = key.indexOf(':');
        if (colon >= 0) key = key.substring(colon + 1);
        return key.replace('_', ' ');
    }

    private void loadStructureDisplayNames() {
        File file = new File(plugin.getDataFolder(), "structures.yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("structures");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            structureDisplayNames.put(key, section.getString(key + ".display", key));
        }
    }
}
