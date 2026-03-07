package dev.erotoro.explorationjournal.integrations;

import dev.erotoro.explorationjournal.ExplorationJournal;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Optional BlueMap integration that places markers at first-discovery
 * coordinates on the web map.
 *
 * <p>Uses reflection to access the BlueMap API so the plugin compiles
 * and runs without BlueMap on the classpath. All calls are guarded by
 * an {@code enabled} flag that is set to {@code false} if BlueMap is
 * absent or if any reflection step fails.</p>
 *
 * <p>Marker placement follows this flow:</p>
 * <ol>
 *   <li>Obtain the {@code BlueMapAPI} instance via
 *       {@code BlueMapAPI.onEnable(Consumer)}</li>
 *   <li>For each world, get or create a {@code MarkerSet} labelled from
 *       {@code config.yml → integrations.bluemap.marker-set-label}</li>
 *   <li>Add a {@code POIMarker} at the discovery coordinates</li>
 * </ol>
 */
public class BluemapIntegration {

    private final ExplorationJournal plugin;
    private String markerSetLabel;
    private boolean enabled;

    /** Cached BlueMap API instance (set via onEnable callback). */
    private Object blueMapApi;

    /**
     * Creates a new BluemapIntegration.
     *
     * @param plugin the plugin instance
     */
    public BluemapIntegration(@NotNull ExplorationJournal plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.markerSetLabel = plugin.getConfig().getString(
                "integrations.bluemap.marker-set-label", "Открытия игроков");
        this.enabled = false;
    }

    /**
     * Reloads config values. Call when plugin config is reloaded.
     */
    public void reload() {
        this.markerSetLabel = plugin.getConfig().getString(
                "integrations.bluemap.marker-set-label", "Открытия игроков");
    }

    /**
     * Attempts to hook into BlueMap. Call during onEnable after checking
     * that BlueMap is present.
     *
     * @return {@code true} if the hook succeeded
     */
    public boolean enable() {
        if (!plugin.getConfig().getBoolean("integrations.bluemap.enabled", true)) {
            return false;
        }

        Plugin blueMap = Bukkit.getPluginManager().getPlugin("BlueMap");
        if (blueMap == null) {
            return false;
        }

        try {
            // BlueMapAPI.onEnable(Consumer<BlueMapAPI>)
            Class<?> apiClass = Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");

            // Register the onEnable consumer via a lambda-friendly approach.
            // We use a simple Runnable-style hook: BlueMapAPI.onEnable(api -> ...)
            var onEnableMethod = apiClass.getMethod("onEnable",
                    java.util.function.Consumer.class);

            java.util.function.Consumer<Object> consumer = api -> {
                this.blueMapApi = api;
                this.enabled = true;
                plugin.getLogger().info("BlueMap integration enabled.");
            };

            onEnableMethod.invoke(null, consumer);
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to hook into BlueMap API — integration disabled.", e);
            return false;
        }
    }

    /**
     * Places a POI marker at the given world coordinates for a
     * first-discovery event.
     *
     * <p><b>Thread contract:</b> safe to call from any thread; BlueMap's
     * API is thread-safe.</p>
     *
     * @param worldName    the Bukkit world name (e.g. "world")
     * @param x            the X block coordinate
     * @param z            the Z block coordinate
     * @param label        the marker label (discovery display name)
     * @param markerId     a unique marker ID (e.g. "biome_minecraft_plains")
     * @param playerName   the discovering player's name
     */
    public void addMarker(@NotNull String worldName,
                          int x, int z,
                          @NotNull String label,
                          @NotNull String markerId,
                          @NotNull String playerName) {
        if (!enabled || blueMapApi == null) {
            return;
        }

        try {
            // Resolve the world's BlueMap map objects.
            // BlueMapAPI.getWorld(String) → Optional<BlueMapWorld>
            var apiClass = blueMapApi.getClass();

            var getWorldMethod = apiClass.getMethod("getWorld", String.class);
            Object optionalWorld = getWorldMethod.invoke(blueMapApi, worldName);

            // Optional.isPresent() / Optional.get()
            var isPresentMethod = optionalWorld.getClass().getMethod("isPresent");
            if (!(boolean) isPresentMethod.invoke(optionalWorld)) {
                return;
            }
            var getMethod = optionalWorld.getClass().getMethod("get");
            Object blueMapWorld = getMethod.invoke(optionalWorld);

            // BlueMapWorld.getMaps() → Collection<BlueMapMap>
            var getMapsMethod = blueMapWorld.getClass().getMethod("getMaps");
            var maps = (java.util.Collection<?>) getMapsMethod.invoke(blueMapWorld);

            for (Object map : maps) {
                addMarkerToMap(map, x, z, label, markerId, playerName);
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to place BlueMap marker: " + markerId, e);
        }
    }

    /**
     * Adds a POI marker to a single BlueMap map.
     */
    private void addMarkerToMap(@NotNull Object map,
                                int x, int z,
                                @NotNull String label,
                                @NotNull String markerId,
                                @NotNull String playerName) throws Exception {

        // BlueMapMap.getMarkerSets() → Map<String, MarkerSet>
        var getMarkerSetsMethod = map.getClass().getMethod("getMarkerSets");
        @SuppressWarnings("unchecked")
        var markerSets = (java.util.Map<String, Object>) getMarkerSetsMethod.invoke(map);

        // Get or create our marker set.
        Object markerSet = markerSets.get("exploration_journal");
        if (markerSet == null) {
            // MarkerSet.builder().label(label).build()
            Class<?> markerSetClass = Class.forName("de.bluecolored.bluemap.api.markers.MarkerSet");
            var builderMethod = markerSetClass.getMethod("builder");
            Object builder = builderMethod.invoke(null);

            var labelMethod = builder.getClass().getMethod("label", String.class);
            labelMethod.invoke(builder, markerSetLabel);

            var buildMethod = builder.getClass().getMethod("build");
            markerSet = buildMethod.invoke(builder);

            markerSets.put("exploration_journal", markerSet);
        }

        // Create a POIMarker: POIMarker.builder().label(label).position(x, 64, z).build()
        Class<?> poiClass = Class.forName("de.bluecolored.bluemap.api.markers.POIMarker");
        var poiBuilderMethod = poiClass.getMethod("builder");
        Object poiBuilder = poiBuilderMethod.invoke(null);

        var poiLabelMethod = poiBuilder.getClass().getMethod("label", String.class);
        poiLabelMethod.invoke(poiBuilder, label + " (" + playerName + ")");

        var positionMethod = poiBuilder.getClass().getMethod("position", double.class, double.class, double.class);
        positionMethod.invoke(poiBuilder, (double) x, 64.0, (double) z);

        var poiBuildMethod = poiBuilder.getClass().getMethod("build");
        Object poiMarker = poiBuildMethod.invoke(poiBuilder);

        // MarkerSet.getMarkers().put(id, marker)
        var getMarkersMethod = markerSet.getClass().getMethod("getMarkers");
        @SuppressWarnings("unchecked")
        var markers = (java.util.Map<String, Object>) getMarkersMethod.invoke(markerSet);
        markers.put(markerId, poiMarker);
    }

    /**
     * Returns whether BlueMap integration is active.
     *
     * @return true if hooked and enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
}
