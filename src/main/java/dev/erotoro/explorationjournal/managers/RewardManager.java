package dev.erotoro.explorationjournal.managers;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.utils.MessageFormatter;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses {@code rewards.yml} and delivers vanilla-only rewards to players.
 * Updated to use Adventure Title API.
 */
public class RewardManager {

    private final ExplorationJournal plugin;
    private final Map<String, RewardBundle> titleRewards = new HashMap<>();
    private final Map<Integer, RewardBundle> biomeCountRewards = new HashMap<>();
    private final Map<UUID, Set<String>> grantedTitleRewards = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> grantedBiomeMilestones = new ConcurrentHashMap<>();

    public RewardManager(@NotNull ExplorationJournal plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        loadRewards();
    }

    public void grantTitleReward(@NotNull Player player, @NotNull String newTitle) {
        UUID uuid = player.getUniqueId();
        Set<String> granted = grantedTitleRewards.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        if (!granted.add(newTitle)) return;

        RewardBundle bundle = titleRewards.get(newTitle);
        if (bundle != null) deliver(player, bundle);
    }

    public void grantBiomeCountRewards(@NotNull Player player, int biomeCount) {
        UUID uuid = player.getUniqueId();
        Set<Integer> granted = grantedBiomeMilestones.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());

        for (Map.Entry<Integer, RewardBundle> entry : biomeCountRewards.entrySet()) {
            int threshold = entry.getKey();
            if (biomeCount >= threshold && granted.add(threshold)) {
                deliver(player, entry.getValue());
            }
        }
    }

    public void reload() {
        titleRewards.clear();
        biomeCountRewards.clear();
        loadRewards();
    }

    public void clearPlayerCache(@NotNull UUID playerUuid) {
        grantedTitleRewards.remove(playerUuid);
        grantedBiomeMilestones.remove(playerUuid);
    }

    private void deliver(@NotNull Player player, @NotNull RewardBundle bundle) {
        // Experience
        if (bundle.exp > 0) player.giveExp(bundle.exp);

        // Items
        for (ItemStack item : bundle.items) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            if (!overflow.isEmpty()) {
                overflow.values().forEach(leftover ->
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
        }

        // Title / subtitle splash text (Modern Adventure API)
        if (bundle.titleText != null || bundle.subtitleText != null) {
            Title title = Title.title(
                MessageFormatter.parse(bundle.titleText),
                MessageFormatter.parse(bundle.subtitleText),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000))
            );
            player.showTitle(title);
        }
    }

    private void loadRewards() {
        File file = new File(plugin.getDataFolder(), "rewards.yml");
        if (!file.exists()) plugin.saveResource("rewards.yml", false);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        ConfigurationSection titleSection = config.getConfigurationSection("on-title");
        if (titleSection != null) {
            for (String key : titleSection.getKeys(false)) {
                ConfigurationSection entry = titleSection.getConfigurationSection(key);
                if (entry != null) titleRewards.put(key, parseBundle(entry));
            }
        }

        ConfigurationSection biomeSection = config.getConfigurationSection("on-biome-count");
        if (biomeSection != null) {
            for (String key : biomeSection.getKeys(false)) {
                try {
                    int threshold = Integer.parseInt(key);
                    ConfigurationSection entry = biomeSection.getConfigurationSection(key);
                    if (entry != null) biomeCountRewards.put(threshold, parseBundle(entry));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    @NotNull
    private RewardBundle parseBundle(@NotNull ConfigurationSection section) {
        int exp = section.getInt("exp", 0);
        String title = section.getString("title");
        String subtitle = section.getString("subtitle");
        List<ItemStack> items = new ArrayList<>();
        
        List<?> itemList = section.getList("items");
        if (itemList != null) {
            for (Object obj : itemList) {
                if (obj instanceof Map<?, ?> map) {
                    String mat = (String) map.get("material");
                    int amt = map.containsKey("amount") ? (int) map.get("amount") : 1;
                    if (mat != null) {
                        try {
                            Material m = Material.valueOf(mat.toUpperCase());
                            items.add(new ItemStack(m, amt));
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return new RewardBundle(exp, title, subtitle, List.copyOf(items));
    }

    private record RewardBundle(int exp, String titleText, String subtitleText, List<ItemStack> items) {}
}
