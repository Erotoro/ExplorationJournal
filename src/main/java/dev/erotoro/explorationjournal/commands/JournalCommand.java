package dev.erotoro.explorationjournal.commands;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.SchedulerUtil;
import dev.erotoro.explorationjournal.managers.JournalManager;
import dev.erotoro.explorationjournal.models.JournalEntry;
import dev.erotoro.explorationjournal.models.PlayerJournal;
import dev.erotoro.explorationjournal.models.TopEntry;
import dev.erotoro.explorationjournal.utils.BookBuilder;
import dev.erotoro.explorationjournal.utils.JournalItemBuilder;
import dev.erotoro.explorationjournal.utils.MessageFormatter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Main /journal command handler supporting all subcommands.
 */
public class JournalCommand implements CommandExecutor, TabCompleter {

    private final ExplorationJournal plugin;
    private final JournalManager journalManager;

    public JournalCommand(@NotNull ExplorationJournal plugin) {
        this.plugin = plugin;
        this.journalManager = plugin.getJournalManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            handleOpenJournal(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "biomes" -> handleBiomes(sender);
            case "structures" -> handleStructures(sender);
            case "discoveries" -> handleDiscoveries(sender);
            case "stats" -> handleStats(sender, args);
            case "map" -> handleMap(sender);
            case "top" -> handleTop(sender, args);
            case "export" -> handleExport(sender);
            case "give" -> handleGive(sender, args);
            default -> {
                sender.sendMessage(MessageFormatter.parse(
                        MessageFormatter.get("journal-cmd-unknown-subcommand").replace("<sub>", sub)));
                yield true;
            }
        };
    }

    private void handleOpenJournal(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageFormatter.parse(MessageFormatter.get("player-only")));
            return;
        }

        SchedulerUtil.runAsync(plugin, () -> {
            UUID playerUuid = player.getUniqueId();
            PlayerJournal journal = journalManager.getJournal(playerUuid);
            
            if (journal == null || journal.journalLevel() < 1) {
                SchedulerUtil.runOnEntity(plugin, player, () -> 
                    player.sendMessage(MessageFormatter.parse(MessageFormatter.get("journal-no-journal"))));
                return;
            }
            
            List<JournalEntry> biomes = journalManager.getPlayerBiomes(playerUuid);
            List<JournalEntry> structures = journalManager.getPlayerStructures(playerUuid);
            Set<String> discoveries = journalManager.getPlayerDiscoveryKeys(playerUuid);
            
            SchedulerUtil.runOnEntity(plugin, player, () -> {
                ItemStack book = BookBuilder.createExportBook(player, biomes, structures, discoveries);
                player.openBook(book);
            });
        });
    }

    private boolean handleBiomes(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;

        SchedulerUtil.runAsync(plugin, () -> {
            List<JournalEntry> biomes = journalManager.getPlayerBiomes(player.getUniqueId());
            SchedulerUtil.runOnEntity(plugin, player, () -> {
                player.sendMessage(MessageFormatter.parse(
                        MessageFormatter.get("journal-list-biomes-header").replace("<count>", String.valueOf(biomes.size()))));
                biomes.stream().limit(10).forEach(e -> 
                    player.sendMessage(MessageFormatter.parse("<yellow>• <white>" + journalManager.getBiomeDisplayName(e.entryKey()))));
            });
        });
        return true;
    }

    private boolean handleStructures(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;

        SchedulerUtil.runAsync(plugin, () -> {
            List<JournalEntry> structures = journalManager.getPlayerStructures(player.getUniqueId());
            SchedulerUtil.runOnEntity(plugin, player, () -> {
                player.sendMessage(MessageFormatter.parse(
                        MessageFormatter.get("journal-list-structures-header").replace("<count>", String.valueOf(structures.size()))));
                structures.stream().limit(10).forEach(e -> 
                    player.sendMessage(MessageFormatter.parse("<aqua>• <white>" + journalManager.getStructureDisplayName(e.entryKey()))));
            });
        });
        return true;
    }

    private boolean handleDiscoveries(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;

        SchedulerUtil.runAsync(plugin, () -> {
            Set<String> discoveries = journalManager.getPlayerDiscoveryKeys(player.getUniqueId());
            SchedulerUtil.runOnEntity(plugin, player, () -> {
                player.sendMessage(MessageFormatter.parse(
                        MessageFormatter.get("journal-list-discoveries-header").replace("<count>", String.valueOf(discoveries.size()))));
                discoveries.stream().limit(10).forEach(key -> 
                    player.sendMessage(MessageFormatter.parse("<yellow>• <white>" + journalManager.getDiscoveryDisplayName(key))));
            });
        });
        return true;
    }

    private boolean handleMap(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;

        SchedulerUtil.runAsync(plugin, () -> {
            PlayerJournal journal = journalManager.getJournal(player.getUniqueId());
            if (journal == null || journal.journalLevel() < 2) {
                SchedulerUtil.runOnEntity(plugin, player, () ->
                    player.sendMessage(MessageFormatter.parse(MessageFormatter.get("journal-tier-required-map"))));
                return;
            }

            List<JournalEntry> biomes = journalManager.getPlayerBiomes(player.getUniqueId());
            SchedulerUtil.runOnEntity(plugin, player, () -> {
                player.sendMessage(MessageFormatter.parse(MessageFormatter.get("journal-map-header")));
                biomes.forEach(e -> {
                    String coords = String.format("x: %d, z: %d", e.coordX(), e.coordZ());
                    player.sendMessage(MessageFormatter.parse("<gold>" + journalManager.getBiomeDisplayName(e.entryKey()) + " <gray>" + coords));
                });
            });
        });
        return true;
    }

    private boolean handleTop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;

        SchedulerUtil.runAsync(plugin, () -> {
            if (journalManager.getJournal(player.getUniqueId()) == null ||
                journalManager.getJournal(player.getUniqueId()).journalLevel() < 4) {
                SchedulerUtil.runOnEntity(plugin, player, () ->
                    player.sendMessage(MessageFormatter.parse(MessageFormatter.get("journal-tier-required-top"))));
                return;
            }

            String category = args.length > 1 ? args[1].toLowerCase() : "biomes";
            List<TopEntry> top;
            String title;

            switch (category) {
                case "structures" -> {
                    top = journalManager.getTopStructures(10);
                    title = MessageFormatter.get("journal-top-title-structures");
                }
                case "discoveries", "disc" -> {
                    top = journalManager.getTopDiscoveries(10);
                    title = MessageFormatter.get("journal-top-title-discoveries");
                }
                default -> {
                    top = journalManager.getTopBiomes(10);
                    title = MessageFormatter.get("journal-top-title-biomes");
                }
            }

            SchedulerUtil.runOnEntity(plugin, player, () -> {
                player.sendMessage(MessageFormatter.info("--- " + title + " ---"));
                int rank = 1;
                for (TopEntry entry : top) {
                    player.sendMessage(MessageFormatter.parse(
                        "<gold>#" + rank + " <yellow>" + entry.playerName() + 
                        " <gray>(" + entry.value() + ")"));
                    rank++;
                }
            });
        });
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length < 2) {
            player.sendMessage(MessageFormatter.parse(MessageFormatter.get("journal-give-usage")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(MessageFormatter.parse(
                    MessageFormatter.get("player-not-found").replace("<player>", args[1])));
            return true;
        }

        if (!player.hasPermission("explorationjournal.give")) {
            player.sendMessage(MessageFormatter.parse(MessageFormatter.get("journal-give-no-permission")));
            return true;
        }

        SchedulerUtil.runOnEntity(plugin, player, () -> {
            ItemStack journal = JournalItemBuilder.createTier1Journal(target.getUniqueId());
            target.getInventory().addItem(journal);
            player.sendMessage(MessageFormatter.parse(
                    MessageFormatter.get("journal-give-sent").replace("<player>", target.getName())));
            target.sendMessage(MessageFormatter.parse(
                    MessageFormatter.get("journal-give-received").replace("<player>", player.getName())));
        });
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        Player target = (args.length > 1 && sender.hasPermission("explorationjournal.admin"))
                ? Bukkit.getPlayer(args[1])
                : (sender instanceof Player p ? p : null);

        if (target == null) {
            sender.sendMessage(MessageFormatter.parse(
                    MessageFormatter.get("player-not-found").replace("<player>", args.length > 1 ? args[1] : "")));
            return true;
        }

        SchedulerUtil.runAsync(plugin, () -> {
            PlayerJournal journal = journalManager.getJournal(target.getUniqueId());
            int biomes = journalManager.getBiomeCount(target.getUniqueId());

            Runnable respond = () -> {
                sender.sendMessage(MessageFormatter.parse(
                        MessageFormatter.get("journal-stats-header").replace("<player>", target.getName())));
                sender.sendMessage(MessageFormatter.parse(
                        MessageFormatter.get("journal-stats-level")
                                .replace("<level>", String.valueOf(journal != null ? journal.journalLevel() : 0))));
                sender.sendMessage(MessageFormatter.parse(
                        MessageFormatter.get("journal-stats-biomes")
                                .replace("<found>", String.valueOf(biomes))));
            };

            if (sender instanceof Player p) SchedulerUtil.runOnEntity(plugin, p, respond);
            else respond.run();
        });
        return true;
    }

    private boolean handleExport(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;

        SchedulerUtil.runAsync(plugin, () -> {
            PlayerJournal journal = journalManager.getJournal(player.getUniqueId());
            if (journal == null || journal.journalLevel() < 3) {
                SchedulerUtil.runOnEntity(plugin, player, () -> 
                    player.sendMessage(MessageFormatter.parse(MessageFormatter.get("journal-tier-required-export"))));
                return;
            }

            List<JournalEntry> biomes = journalManager.getPlayerBiomes(player.getUniqueId());
            List<JournalEntry> structures = journalManager.getPlayerStructures(player.getUniqueId());
            Set<String> discoveries = journalManager.getPlayerDiscoveryKeys(player.getUniqueId());

            SchedulerUtil.runOnEntity(plugin, player, () -> {
                ItemStack book = BookBuilder.createExportBook(player, biomes, structures, discoveries);
                player.getInventory().addItem(book);
                player.sendMessage(MessageFormatter.parse(MessageFormatter.get("journal-export-success")));
            });
        });
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("biomes", "structures", "discoveries", "stats", "map", "top", "export", "give");
        }
        if (args.length == 2) {
            if (args[0].equals("top")) return List.of("biomes", "structures", "discoveries");
            if (args[0].equals("stats") || args[0].equals("give")) return null;
        }
        return Collections.emptyList();
    }
}
