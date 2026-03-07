package dev.erotoro.explorationjournal.utils;

import dev.erotoro.explorationjournal.ExplorationJournal;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Utility class for creating and managing Explorer's Journal items.
 * Handles PDC metadata storage for journal level and owner UUID.
 */
public final class JournalItemBuilder {
    /**
     * Создаёт журнал с актуальными страницами прогресса игрока.
     * @param tier уровень журнала (1-4)
     * @param ownerUuid UUID владельца
     * @param player игрок (для имени и BookBuilder)
     * @param journalManager менеджер журналов
     * @return ItemStack WRITTEN_BOOK с заполненными страницами
     */
    @NotNull
    public static ItemStack createJournalWithPages(int tier, @NotNull UUID ownerUuid, @NotNull Player player, @NotNull dev.erotoro.explorationjournal.managers.JournalManager journalManager) {
        ItemStack journal = createJournal(tier, ownerUuid);
        // Получаем прогресс игрока
        java.util.List<dev.erotoro.explorationjournal.models.JournalEntry> biomes = journalManager.getPlayerBiomes(ownerUuid);
        java.util.List<dev.erotoro.explorationjournal.models.JournalEntry> structures = journalManager.getPlayerStructures(ownerUuid);
        java.util.Set<String> discoveries = journalManager.getPlayerDiscoveryKeys(ownerUuid);

        org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) journal.getItemMeta();
        if (meta != null) {
            java.util.List<net.kyori.adventure.text.Component> pages = new java.util.ArrayList<>();
            pages.add(dev.erotoro.explorationjournal.utils.BookBuilder.buildTitlePage(player, biomes.size(), structures.size(), discoveries.size()));
            pages.addAll(dev.erotoro.explorationjournal.utils.BookBuilder.buildBiomePages(biomes));
            pages.addAll(dev.erotoro.explorationjournal.utils.BookBuilder.buildStructurePages(structures));
            pages.addAll(dev.erotoro.explorationjournal.utils.BookBuilder.buildDiscoveryPages(discoveries));
            meta.pages(pages);
            journal.setItemMeta(meta);
        }
        return journal;
    }

    private static NamespacedKey journalLevelKey;
    private static NamespacedKey journalOwnerKey;

    private static final String[] TIER_NAMES = {
            "", "Походный", "Картографа", "Исследователя", "Легенды"
    };

    private static final NamedTextColor[] TIER_COLORS = {
            null,
            NamedTextColor.GREEN,
            NamedTextColor.AQUA,
            NamedTextColor.LIGHT_PURPLE,
            NamedTextColor.GOLD
    };

    private static final String[][] TIER_LORE = {
            {},
            {"&7Записи о биомах", "&7Записи о структурах", "", "&eЩёлкните ПКМ для просмотра"},
            {"&7Записи о биомах с координатами", "&7Записи о структурах с координатами", "&a+ Команда /journal map", "", "&eЩёлкните ПКМ для просмотра"},
            {"&7Записи о биомах с координатами", "&7Записи о структурах с координатами", "&7Трекер уникальных открытий", "&a+ Команда /journal map", "&a+ Команда /journal export", "", "&eЩёлкните ПКМ для просмотра"},
            {"&7Записи о биомах с координатами", "&7Записи о структурах с координатами", "&7Трекер уникальных открытий", "&6+ Серверный рейтинг", "&6+ Уникальное описание", "&6+ Эффект частиц при открытии", "&a+ Команда /journal map", "&a+ Команда /journal export", "&a+ Команда /journal top", "", "&eЩёлкните ПКМ для просмотра"}
    };

    private JournalItemBuilder() {}

    @NotNull
    public static NamespacedKey getLevelKey() {
        if (journalLevelKey == null) {
            journalLevelKey = new NamespacedKey(ExplorationJournal.getInstance(), "journal_level");
        }
        return journalLevelKey;
    }

    @NotNull
    public static NamespacedKey getOwnerKey() {
        if (journalOwnerKey == null) {
            journalOwnerKey = new NamespacedKey(ExplorationJournal.getInstance(), "journal_owner");
        }
        return journalOwnerKey;
    }

    @NotNull
    public static ItemStack createJournal(int tier, @NotNull UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid cannot be null");
        if (tier < 1 || tier > 4) throw new IllegalArgumentException("Tier 1-4 only");

        ItemStack journal = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = journal.getItemMeta();
        if (meta == null) throw new IllegalStateException();

        meta.displayName(Component.text("Дневник Исследователя: " + TIER_NAMES[tier])
                .color(TIER_COLORS[tier])
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        for (String line : TIER_LORE[tier]) {
            lore.add(MessageFormatter.parse(line));
        }
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(getLevelKey(), PersistentDataType.INTEGER, tier);
        pdc.set(getOwnerKey(), PersistentDataType.STRING, ownerUuid.toString());

        journal.setItemMeta(meta);
        return journal;
    }

    @NotNull
    public static ItemStack createTier1Journal(@NotNull UUID ownerUuid) {
        return createJournal(1, ownerUuid);
    }

    public static int getJournalLevel(@NotNull ItemStack item) {
        if (!item.hasItemMeta()) return 0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer level = pdc.get(getLevelKey(), PersistentDataType.INTEGER);
        return level != null ? level : 0;
    }

    public static UUID getJournalOwner(@NotNull ItemStack item) {
        if (!item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String uuidString = pdc.get(getOwnerKey(), PersistentDataType.STRING);
        if (uuidString == null) return null;
        try { return UUID.fromString(uuidString); } catch (Exception e) { return null; }
    }

    public static boolean isJournal(@NotNull ItemStack item) {
        return getJournalLevel(item) >= 1;
    }

    @NotNull
    public static String getTierName(int level) {
        if (level < 1 || level > 4) return "Неизвестный";
        return TIER_NAMES[level];
    }
}
