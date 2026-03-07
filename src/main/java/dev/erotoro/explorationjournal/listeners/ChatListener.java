package dev.erotoro.explorationjournal.listeners;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.managers.JournalManager;
import dev.erotoro.explorationjournal.models.PlayerJournal;
import dev.erotoro.explorationjournal.utils.MessageFormatter;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modifies chat to prepend title.
 */
public class ChatListener implements Listener {

    private final ConcurrentHashMap<UUID, String> titleCache = new ConcurrentHashMap<>();
    private final JournalManager journalManager;

    public ChatListener(@NotNull ExplorationJournal plugin) {
        this.journalManager = plugin.getJournalManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(@NotNull AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        String title = titleCache.get(uuid);
        if (title == null) {
            PlayerJournal journal = journalManager.getCachedJournal(uuid);
            title = (journal != null) ? journal.currentTitle() : "Новичок";
            titleCache.put(uuid, title);
        }

        final String finalTitle = title;

        event.renderer((source, sourceDisplayName, message, viewer) -> 
            Component.text()
                .append(MessageFormatter.parse("<gold>[" + finalTitle + "] "))
                .append(sourceDisplayName.color(NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(message)
                .build()
        );
    }

    public void updateTitleCache(@NotNull UUID uuid, @NotNull String title) {
        titleCache.put(uuid, title);
    }

    public void removeTitleCache(@NotNull UUID uuid) {
        titleCache.remove(uuid);
    }

    public void initializeTitleCache(@NotNull UUID uuid) {
        PlayerJournal journal = journalManager.getJournal(uuid);
        if (journal != null) {
            titleCache.put(uuid, journal.currentTitle());
        }
    }
}
