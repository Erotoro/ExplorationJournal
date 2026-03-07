package dev.erotoro.explorationjournal.utils;

import dev.erotoro.explorationjournal.models.JournalEntry;
import dev.erotoro.explorationjournal.ExplorationJournal;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Utility for generating books.
 */
public final class BookBuilder {

    private static final DateTimeFormatter DATE_FORMAT = 
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                    .withZone(ZoneId.systemDefault());

    private BookBuilder() {}

    @NotNull
    public static ItemStack createExportBook(@NotNull Player player,
                                              @NotNull List<JournalEntry> biomes,
                                              @NotNull List<JournalEntry> structures,
                                              @NotNull Set<String> discoveries) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) throw new IllegalStateException();

        meta.title(MessageFormatter.parse(MessageFormatter.get("journal.book-title")));
        meta.author(Component.text(player.getName()));
        meta.setGeneration(BookMeta.Generation.ORIGINAL);

        List<Component> pages = new ArrayList<>();
        pages.add(buildTitlePage(player, biomes.size(), structures.size(), discoveries.size()));
        pages.addAll(buildBiomePages(biomes));
        pages.addAll(buildStructurePages(structures));
        pages.addAll(buildDiscoveryPages(discoveries));

        meta.pages(pages);
        book.setItemMeta(meta);
        return book;
    }

    public static Component buildTitlePage(Player player, int biomes, int structures, int discoveries) {
        String title = MessageFormatter.get("journal.title");
        String sep = MessageFormatter.get("journal.separator");
        String playerLabel = MessageFormatter.get("journal.player-label").replace("<player>", player.getName());
        String dateLabel = MessageFormatter.get("journal.created-label").replace("<date>", DATE_FORMAT.format(Instant.now()));
        String statsHeader = MessageFormatter.get("journal.stats-header");
        String biomesLabel = MessageFormatter.get("journal.biomes-label").replace("<found>", String.valueOf(biomes)).replace("<total>", "67");
        String structuresLabel = MessageFormatter.get("journal.structures-label").replace("<found>", String.valueOf(structures));
        String discoveriesLabel = MessageFormatter.get("journal.discoveries-label").replace("<found>", String.valueOf(discoveries));
        return MessageFormatter.parse(String.join("\n",
                title,
                sep,
                "",
                playerLabel,
                dateLabel,
                "",
                statsHeader,
                biomesLabel,
                structuresLabel,
                discoveriesLabel,
                "",
                sep
        ));
    }

    public static List<Component> buildBiomePages(List<JournalEntry> biomes) {
        List<Component> pages = new ArrayList<>();
        if (biomes.isEmpty()) return pages;
        StringBuilder sb = new StringBuilder();
        sb.append(MessageFormatter.get("journal.section-biomes")).append("\n\n");
        var jm = ExplorationJournal.getInstance().getJournalManager();
        for (JournalEntry entry : biomes) {
            String name = jm != null ? jm.getBiomeDisplayName(entry.entryKey()) : entry.getSimpleName();
            String line = MessageFormatter.get("journal.marker-biome").replace("<name>", name);
            sb.append(line).append("\n");
        }
        pages.add(MessageFormatter.parse(sb.toString()));
        return pages;
    }

    public static List<Component> buildStructurePages(List<JournalEntry> structures) {
        List<Component> pages = new ArrayList<>();
        if (structures.isEmpty()) return pages;
        StringBuilder sb = new StringBuilder();
        sb.append(MessageFormatter.get("journal.section-structures")).append("\n\n");
        var jm = ExplorationJournal.getInstance().getJournalManager();
        for (JournalEntry entry : structures) {
            String name = jm != null ? jm.getStructureDisplayName(entry.entryKey()) : entry.getSimpleName();
            String line = MessageFormatter.get("journal.marker-structure").replace("<name>", name);
            sb.append(line).append("\n");
        }
        pages.add(MessageFormatter.parse(sb.toString()));
        return pages;
    }

    public static List<Component> buildDiscoveryPages(Set<String> discoveries) {
        List<Component> pages = new ArrayList<>();
        if (discoveries.isEmpty()) return pages;
        StringBuilder sb = new StringBuilder();
        sb.append(MessageFormatter.get("journal.section-discoveries")).append("\n\n");
        var jm = ExplorationJournal.getInstance().getJournalManager();
        for (String key : discoveries) {
            String name = jm != null ? jm.getDiscoveryDisplayName(key) : key;
            String line = MessageFormatter.get("journal.marker-discovery").replace("<name>", name);
            sb.append(line).append("\n");
        }
        pages.add(MessageFormatter.parse(sb.toString()));
        return pages;
    }

    @NotNull
    public static ItemStack createInfoBook(@NotNull String title, @NotNull String author, @NotNull String content) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) throw new IllegalStateException();
        meta.title(MessageFormatter.parse(title));
        meta.author(MessageFormatter.parse(author));
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        meta.pages(List.of(MessageFormatter.parse(content)));
        book.setItemMeta(meta);
        return book;
    }
}
