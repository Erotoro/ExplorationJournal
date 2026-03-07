package dev.erotoro.explorationjournal.commands;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.SchedulerUtil;
import dev.erotoro.explorationjournal.managers.JournalManager;
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
 * Admin command handler for /journaladmin.
 */
public class JournalAdminCommand implements CommandExecutor, TabCompleter {

    private final ExplorationJournal plugin;
    private final JournalManager journalManager;

    public JournalAdminCommand(@NotNull ExplorationJournal plugin) {
        this.plugin = plugin;
        this.journalManager = plugin.getJournalManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("explorationjournal.admin")) {
            sender.sendMessage(MessageFormatter.parse(MessageFormatter.get("no-permission")));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        if (subCommand.equals("reset")) {
            return handleReset(sender, subArgs);
        } else if (subCommand.equals("give")) {
            return handleGive(sender, subArgs);
        } else if (subCommand.equals("reload")) {
            return handleReload(sender);
        } else {
            sender.sendMessage(MessageFormatter.parse(
                    MessageFormatter.get("journal-cmd-unknown-subcommand").replace("<sub>", subCommand)));
            sendHelp(sender);
            return true;
        }
    }

    private void sendHelp(@NotNull CommandSender sender) {
        sender.sendMessage(MessageFormatter.info("=== Админ: Журнал Исследователя ==="));
        sender.sendMessage(MessageFormatter.parse("<yellow>/journaladmin reset <player> <gray>- Сбросить данные игрока"));
        sender.sendMessage(MessageFormatter.parse("<yellow>/journaladmin give <player> <1-4> <gray>- Выдать журнал"));
        sender.sendMessage(MessageFormatter.parse("<yellow>/journaladmin reload <gray>- Перезагрузить конфиги"));
    }

    private boolean handleReset(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(MessageFormatter.parse(
                    MessageFormatter.get("wrong-usage").replace("<usage>", "/journaladmin reset <player>")));
            return true;
        }

        String playerName = args[0];
        Player target = Bukkit.getPlayer(playerName);
        UUID targetUuid = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(playerName).getUniqueId();

        SchedulerUtil.runAsync(plugin, () -> {
            try {
                journalManager.resetPlayerData(targetUuid);
                sender.sendMessage(MessageFormatter.parse(
                        MessageFormatter.get("data-reset").replace("<player>", playerName)));
                if (target != null) {
                    target.sendMessage(MessageFormatter.parse("<red>Ваши данные исследования были сброшены администратором."));
                }
            } catch (Exception e) {
                sender.sendMessage(MessageFormatter.parse("<red>Не удалось сбросить данные: <gray>" + e.getMessage()));
            }
        });
        return true;
    }

    private boolean handleGive(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageFormatter.parse(
                    MessageFormatter.get("wrong-usage").replace("<usage>", "/journaladmin give <player> <1-4>")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(MessageFormatter.parse(
                    MessageFormatter.get("player-not-found").replace("<player>", args[0])));
            return true;
        }

        int tier;
        try {
            tier = Integer.parseInt(args[1]);
            if (tier < 1 || tier > 4) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageFormatter.parse("<red>Уровень должен быть от 1 до 4."));
            return true;
        }

        SchedulerUtil.runAsync(plugin, () -> {
            try {
                journalManager.updateJournalLevel(target.getUniqueId(), tier);
                SchedulerUtil.runOnEntity(plugin, target, () -> {
                    ItemStack journalItem = JournalItemBuilder.createJournal(tier, target.getUniqueId());
                    target.getInventory().addItem(journalItem);
                    sender.sendMessage(MessageFormatter.parse(
                            MessageFormatter.get("give-success")
                                    .replace("<level>", String.valueOf(tier))
                                    .replace("<player>", target.getName())));
                });
            } catch (Exception e) {
                sender.sendMessage(MessageFormatter.parse("<red>Не удалось выдать журнал: <gray>" + e.getMessage()));
            }
        });
        return true;
    }

    private boolean handleReload(@NotNull CommandSender sender) {
        plugin.reloadConfigs();
        sender.sendMessage(MessageFormatter.parse(MessageFormatter.get("reload-success")));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("reset", "give", "reload");
        if (args.length == 2 && (args[0].equals("reset") || args[0].equals("give"))) return null; // online players
        if (args.length == 3 && args[0].equals("give")) return List.of("1", "2", "3", "4");
        return Collections.emptyList();
    }
}
