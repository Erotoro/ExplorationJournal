package dev.erotoro.explorationjournal.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility for formatting messages using Adventure API.
 * Handles both MiniMessage and Legacy formats.
 */
public final class MessageFormatter {

    /**
     * Получить строку из messages.yml по ключу. Если нет — вернуть ключ с ошибкой.
     */
    @NotNull
    public static String get(@NotNull String key) {
        org.bukkit.configuration.file.YamlConfiguration messages = dev.erotoro.explorationjournal.ExplorationJournal.getInstance().getMessagesConfig();
        String value = messages.getString(key);
        return value != null ? value : "§c[Нет строки: " + key + "]";
    }

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private MessageFormatter() {}

    /**
     * Parses a string into a Component.
     */
    @NotNull
    public static Component parse(@Nullable String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (text.contains("<") && text.contains(">")) {
            return MINI_MESSAGE.deserialize(text);
        }
        return LEGACY_SERIALIZER.deserialize(text);
    }

    /**
     * Alias for parse to support legacy code.
     */
    @NotNull
    public static Component formatComponent(@Nullable String text) {
        return parse(text);
    }

    @NotNull
    public static Component success(@NotNull String msg) {
        return Component.text(msg, NamedTextColor.GREEN);
    }

    @NotNull
    public static Component error(@NotNull String msg) {
        return Component.text(msg, NamedTextColor.RED);
    }

    @NotNull
    public static Component info(@NotNull String msg) {
        return Component.text(msg, NamedTextColor.GOLD);
    }
}
