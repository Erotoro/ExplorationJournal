package dev.erotoro.explorationjournal.listeners;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.SchedulerUtil;
import dev.erotoro.explorationjournal.managers.JournalManager;
import dev.erotoro.explorationjournal.utils.JournalItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Handles right-click on the Explorer's Journal to update and open it with actual player progress.
 */
public class JournalInteractListener implements Listener {

    private final ExplorationJournal plugin;
    private final JournalManager journalManager;

    public JournalInteractListener(@NotNull ExplorationJournal plugin, @NotNull JournalManager journalManager) {
        this.plugin = plugin;
        this.journalManager = journalManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        ItemStack item = event.getItem();
        if (item == null || !JournalItemBuilder.isJournal(item)) return;

        // Обновляем содержимое книги перед открытием
        UUID uuid = player.getUniqueId();
        int tier = JournalItemBuilder.getJournalLevel(item);
        event.setCancelled(true);
        SchedulerUtil.runAsync(plugin, () -> {
            ItemStack updated = JournalItemBuilder.createJournalWithPages(tier, uuid, player, journalManager);
            SchedulerUtil.runOnEntity(plugin, player, () -> {
                player.getInventory().setItemInMainHand(updated);
                player.openBook(updated);
            });
        });
    }
}
