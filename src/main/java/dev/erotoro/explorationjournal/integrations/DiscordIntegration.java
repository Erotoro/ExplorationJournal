package dev.erotoro.explorationjournal.integrations;

import dev.erotoro.explorationjournal.ExplorationJournal;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Optional DiscordSRV integration that sends first-discovery broadcasts
 * to a configured Discord channel.
 *
 * <p>Uses reflection to access the DiscordSRV API so the plugin compiles
 * and runs without DiscordSRV on the classpath. All calls are guarded by
 * an {@code enabled} flag set to {@code false} when DiscordSRV is absent
 * or any reflection step fails.</p>
 *
 * <p>The target channel name is read from
 * {@code config.yml → integrations.discordsrv.channel} (default:
 * {@code "exploration-log"}).</p>
 */
public class DiscordIntegration {

    private final ExplorationJournal plugin;
    private String channelName;
    private boolean enabled;

    /** Cached DiscordSRV plugin instance. */
    private Object discordSrvInstance;

    /**
     * Creates a new DiscordIntegration.
     *
     * @param plugin the plugin instance
     */
    public DiscordIntegration(@NotNull ExplorationJournal plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.channelName = plugin.getConfig().getString(
                "integrations.discordsrv.channel", "exploration-log");
        this.enabled = false;
    }

    /**
     * Reloads config values. Call when plugin config is reloaded.
     */
    public void reload() {
        this.channelName = plugin.getConfig().getString(
                "integrations.discordsrv.channel", "exploration-log");
    }

    /**
     * Attempts to hook into DiscordSRV. Call during onEnable after
     * checking that DiscordSRV is present.
     *
     * @return {@code true} if the hook succeeded
     */
    public boolean enable() {
        if (!plugin.getConfig().getBoolean("integrations.discordsrv.enabled", true)) {
            return false;
        }

        Plugin discordSrv = Bukkit.getPluginManager().getPlugin("DiscordSRV");
        if (discordSrv == null) {
            return false;
        }

        try {
            // Verify the expected class exists.
            Class.forName("github.scarsz.discordsrv.DiscordSRV");
            this.discordSrvInstance = discordSrv;
            this.enabled = true;
            plugin.getLogger().info("DiscordSRV integration enabled (channel: " + channelName + ").");
            return true;

        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.WARNING,
                    "DiscordSRV found but API class missing — integration disabled.", e);
            return false;
        }
    }

    /**
     * Sends a plain-text message to the configured Discord channel.
     *
     * <p>The message is sent asynchronously by DiscordSRV's internal
     * threading. Safe to call from any thread.</p>
     *
     * @param message the message to send (plain text, no Minecraft formatting)
     */
    public void sendMessage(@NotNull String message) {
        if (!enabled || discordSrvInstance == null) {
            return;
        }

        try {
            // DiscordSRV.getPlugin() → DiscordSRV instance
            Class<?> discordSrvClass = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            var getPluginMethod = discordSrvClass.getMethod("getPlugin");
            Object dsrv = getPluginMethod.invoke(null);

            // DiscordSRV.getDestination(String) → TextChannel (nullable)
            var getDestinationMethod = discordSrvClass.getMethod("getDestination", String.class);
            Object textChannel = getDestinationMethod.invoke(dsrv, channelName);

            if (textChannel == null) {
                // Fallback: try getMainTextChannel()
                var getMainMethod = discordSrvClass.getMethod("getMainTextChannel");
                textChannel = getMainMethod.invoke(dsrv);
            }

            if (textChannel == null) {
                plugin.getLogger().warning("DiscordSRV channel '" + channelName
                        + "' not found and no main channel configured.");
                return;
            }

            // TextChannel.sendMessage(String).queue()
            var sendMessageMethod = textChannel.getClass().getMethod("sendMessage", String.class);
            Object restAction = sendMessageMethod.invoke(textChannel, message);

            var queueMethod = restAction.getClass().getMethod("queue");
            queueMethod.invoke(restAction);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to send DiscordSRV message", e);
        }
    }

    /**
     * Sends a first-discovery broadcast to Discord.
     *
     * @param playerName   the discovering player's name
     * @param discoveryType the type of discovery ("биом", "структуру", "открытие")
     * @param displayName  the human-readable name of the discovery
     */
    public void broadcastFirstDiscovery(@NotNull String playerName,
                                        @NotNull String discoveryType,
                                        @NotNull String displayName) {
        if (!enabled) {
            return;
        }

        String message = "\u2B50 **" + playerName + "** первым на сервере обнаружил "
                + discoveryType + ": **" + displayName + "**!";
        sendMessage(message);
    }

    /**
     * Returns whether DiscordSRV integration is active.
     *
     * @return true if hooked and enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
