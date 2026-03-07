package dev.erotoro.explorationjournal.listeners;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.SchedulerUtil;
import dev.erotoro.explorationjournal.database.PlayerDAO;
import dev.erotoro.explorationjournal.managers.JournalManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Handles player join and quit events.
 * Initialises player data in the database and manages caches.
 */
public class JoinListener implements Listener {

    private final ExplorationJournal plugin;
    private final JournalManager journalManager;
    private final PlayerDAO playerDAO;

    public JoinListener(@NotNull ExplorationJournal plugin,
                        @NotNull JournalManager journalManager,
                        @NotNull PlayerDAO playerDAO) {
        this.plugin = plugin;
        this.journalManager = journalManager;
        this.playerDAO = playerDAO;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        // Initialise player in DB and load cache
        SchedulerUtil.runAsync(plugin, () -> {
            try {
                playerDAO.upsertPlayer(uuid, name);
                
                // Pre-load journal into cache
                journalManager.getJournal(uuid);
                
                // Initialise integrations
                if (plugin.getPapiExpansion() != null) {
                    plugin.getPapiExpansion().cachePlayer(uuid);
                }
                
                // Initialise ChatListener cache
                plugin.getChatListener().initializeTitleCache(uuid);
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to initialise player data for " + name);
                e.printStackTrace();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        
        // Clean up caches
        journalManager.clearCache(uuid);
        
        if (plugin.getPapiExpansion() != null) {
            plugin.getPapiExpansion().uncachePlayer(uuid);
        }
        
        plugin.getChatListener().removeTitleCache(uuid);
        plugin.getRewardManager().clearPlayerCache(uuid);
    }
}
