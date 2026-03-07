package dev.erotoro.explorationjournal.managers;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.SchedulerUtil;
import dev.erotoro.explorationjournal.database.DatabaseManager;
import dev.erotoro.explorationjournal.database.DiscoveryDAO;
import dev.erotoro.explorationjournal.models.Discovery;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DiscoveryManager {

    private final ExplorationJournal plugin;
    private final DiscoveryDAO discoveryDAO;
    private final DatabaseManager databaseManager;

    private final Map<String, String> discoveryDisplayNames = new ConcurrentHashMap<>();

    public DiscoveryManager(@NotNull ExplorationJournal plugin,
                            @NotNull DiscoveryDAO discoveryDAO,
                            @NotNull DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.discoveryDAO = discoveryDAO;
        this.databaseManager = databaseManager;
        reload();
    }

    public void registerDiscovery(@NotNull UUID uuid, @NotNull String name, @NotNull Discovery discovery) {
        try {
            boolean alreadyKnown = hasDiscovery(uuid, discovery.discoveryKey());
            boolean firstOnServer = discoveryDAO.isFirstOnServer(discovery.discoveryKey());
            
            discoveryDAO.addDiscovery(uuid, discovery.discoveryKey(), discovery.description(), firstOnServer);

            if (!alreadyKnown) {
                notifyPlayer(uuid, discovery, firstOnServer);
                if (plugin.getTitleManager() != null) {
                    plugin.getTitleManager().checkAndUpdate(uuid);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public int getDiscoveryCount(@NotNull UUID uuid) {
        try { return discoveryDAO.getPlayerDiscoveryCount(uuid); }
        catch (SQLException e) { return 0; }
    }

    public Set<String> getPlayerDiscoveryKeys(@NotNull UUID uuid) {
        Set<String> keys = new HashSet<>();
        String sql = "SELECT discovery_key FROM discoveries WHERE player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { keys.add(rs.getString(1)); }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return keys;
    }

    private void notifyPlayer(UUID uuid, Discovery discovery, boolean first) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        SchedulerUtil.runOnEntity(plugin, player, () -> {
            player.sendMessage(Component.text("УНИКАЛЬНОЕ ОТКРЫТИЕ: ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                    .append(Component.text(discovery.getDisplayName(), NamedTextColor.GOLD)));
            if (first) {
                player.sendMessage(Component.text("Вы первый на сервере!", NamedTextColor.AQUA, TextDecoration.ITALIC));
            }
        });
    }

    private boolean hasDiscovery(UUID uuid, String key) throws SQLException {
        String sql = "SELECT 1 FROM discoveries WHERE player_uuid = ? AND discovery_key = ? LIMIT 1";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public void clearCache(UUID uuid) {}

    @NotNull
    public String getDiscoveryDisplayName(@NotNull String key) {
        String v = discoveryDisplayNames.get(key);
        if (v != null) return v;
        // fallback: humanize
        return key.replace('_', ' ').replace('-', ' ');
    }

    public void reload() {
        discoveryDisplayNames.clear();

        File file = new File(plugin.getDataFolder(), "discoveries.yml");
        if (!file.exists()) {
            plugin.saveResource("discoveries.yml", false);
        }
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        loadSectionDisplays(config.getConfigurationSection("discoveries"));
        loadSectionDisplays(config.getConfigurationSection("procedural"));
    }

    private void loadSectionDisplays(ConfigurationSection section) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String display = section.getString(key + ".display");
            if (display != null && !display.isBlank()) {
                discoveryDisplayNames.put(key, display);
            }
        }
    }
}
