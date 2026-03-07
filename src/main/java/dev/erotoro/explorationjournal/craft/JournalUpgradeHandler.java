package dev.erotoro.explorationjournal.craft;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.SchedulerUtil;
import dev.erotoro.explorationjournal.managers.JournalManager;
import dev.erotoro.explorationjournal.utils.JournalItemBuilder;
import dev.erotoro.explorationjournal.utils.MessageFormatter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Handles journal upgrade logic. Ensures only owned journals can be upgraded.
 */
public class JournalUpgradeHandler implements Listener {

    private final ExplorationJournal plugin;
    private final JournalManager journalManager;

    public JournalUpgradeHandler(@NotNull ExplorationJournal plugin) {
        this.plugin = plugin;
        this.journalManager = plugin.getJournalManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(@NotNull CraftItemEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || !JournalItemBuilder.isJournal(result)) return;

        int resultTier = JournalItemBuilder.getJournalLevel(result);
        if (resultTier <= 1) return; // Tier 1 is base craft

        ItemStack oldJournal = findJournalInMatrix(event.getInventory().getMatrix());
        
        // If we're crafting a Tier > 1, we MUST have an old journal
        if (oldJournal == null) {
            event.setCancelled(true);
            return;
        }

        Player player = (Player) event.getWhoClicked();
        UUID owner = JournalItemBuilder.getJournalOwner(oldJournal);

        if (owner != null && !owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(MessageFormatter.error("Вы можете улучшать только свой журнал!"));
            return;
        }

        // Update DB
        SchedulerUtil.runAsync(plugin, () -> {
            try {
                journalManager.updateJournalLevel(player.getUniqueId(), resultTier);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(@NotNull PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || !JournalItemBuilder.isJournal(result)) return;

        int resultTier = JournalItemBuilder.getJournalLevel(result);
        if (resultTier <= 1) {
            // Tier 1 craft - just ensure it has owner
            if (event.getView().getPlayer() instanceof Player p) {
                event.getInventory().setResult(JournalItemBuilder.createJournal(1, p.getUniqueId()));
            }
            return;
        }

        ItemStack oldJournal = findJournalInMatrix(event.getInventory().getMatrix());
        
        // Block crafting if no valid journal or wrong tier
        if (oldJournal == null || JournalItemBuilder.getJournalLevel(oldJournal) != resultTier - 1) {
            event.getInventory().setResult(null);
            return;
        }

        // Set owner for preview
        if (event.getView().getPlayer() instanceof Player p) {
            event.getInventory().setResult(JournalItemBuilder.createJournal(resultTier, p.getUniqueId()));
        }
    }

    private ItemStack findJournalInMatrix(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (item != null && JournalItemBuilder.isJournal(item)) return item;
        }
        return null;
    }
}
