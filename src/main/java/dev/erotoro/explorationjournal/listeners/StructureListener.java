package dev.erotoro.explorationjournal.listeners;

import dev.erotoro.explorationjournal.ExplorationJournal;
import dev.erotoro.explorationjournal.SchedulerUtil;
import dev.erotoro.explorationjournal.managers.JournalManager;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks structure discoveries. Uses throttled checks to ensure server performance.
 * Compatible with Paper 1.21.4 and Folia.
 */
public class StructureListener implements Listener {

    private final ExplorationJournal plugin;
    private final JournalManager journalManager;
    private static final long THROTTLE_MS = 5000L;

    private final Map<UUID, String> currentStructure = new ConcurrentHashMap<>();
    private final Map<UUID, Long> entryTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> recordedInstances = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCheck = new ConcurrentHashMap<>();

    public StructureListener(@NotNull ExplorationJournal plugin) {
        this.plugin = plugin;
        this.journalManager = plugin.getJournalManager();
    }

    private long getExploredThresholdSeconds() {
        return plugin.getConfig().getLong("exploration.structure-explore-time", 300L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        if (now - lastCheck.getOrDefault(uuid, 0L) < THROTTLE_MS) return;
        lastCheck.put(uuid, now);

        Location to = event.getTo();
        Chunk chunk = to.getChunk();
        String structureKey = findStructureAt(to);

        String previous = currentStructure.get(uuid);

        if (structureKey == null && previous != null) {
            handleExit(player, previous);
            currentStructure.remove(uuid);
            entryTimes.remove(uuid);
        } else if (structureKey != null && previous == null) {
            handleEntry(player, structureKey, chunk);
        } else if (structureKey != null && structureKey.equals(previous)) {
            checkExplored(player, structureKey);
        }
    }

    /**
     * Returns the structure key only when the player is actually near/inside
     * the generated structure bounding box.
     *
     * <p>This prevents "cheaty" discoveries like triggering a mineshaft far
     * below the player just because it exists in the same chunk.</p>
     */
    private String findStructureAt(@NotNull Location location) {
        Chunk chunk = location.getChunk();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        // Tunables: small margins so players must be near the structure.
        final double horizontalMargin = 6.0;
        final double verticalMargin = 4.0;

        Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        for (Structure s : registry) {
            Collection<GeneratedStructure> generated = chunk.getStructures(s);
            if (generated.isEmpty()) continue;

            for (GeneratedStructure gs : generated) {
                BoundingBox bb = gs.getBoundingBox();
                if (isNearBoundingBox(bb, x, y, z, horizontalMargin, verticalMargin)) {
                    @SuppressWarnings("removal")
                    String keyStr = s.key().asString();
                    return keyStr;
                }
            }
        }
        return null;
    }

    private boolean isNearBoundingBox(@NotNull BoundingBox bb,
                                      double x, double y, double z,
                                      double horizontalMargin, double verticalMargin) {
        double minX = bb.getMinX() - horizontalMargin;
        double maxX = bb.getMaxX() + horizontalMargin;
        double minZ = bb.getMinZ() - horizontalMargin;
        double maxZ = bb.getMaxZ() + horizontalMargin;
        double minY = bb.getMinY() - verticalMargin;
        double maxY = bb.getMaxY() + verticalMargin;

        return x >= minX && x <= maxX
                && z >= minZ && z <= maxZ
                && y >= minY && y <= maxY;
    }

    private void handleEntry(Player player, String key, Chunk chunk) {
        UUID uuid = player.getUniqueId();
        currentStructure.put(uuid, key);
        entryTimes.put(uuid, System.currentTimeMillis());

        String instanceId = key + "_" + chunk.getX() + "_" + chunk.getZ();
        Set<String> recorded = recordedInstances.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());

        if (recorded.add(instanceId)) {
            SchedulerUtil.runAsync(plugin, () -> {
                journalManager.registerStructure(uuid, player.getName(), key, 
                    player.getWorld().getName(), chunk.getX() << 4, chunk.getZ() << 4, Instant.now());
            });
        }
    }

    private void handleExit(Player player, String key) {
        checkExplored(player, key);
    }

    private void checkExplored(Player player, String key) {
        UUID uuid = player.getUniqueId();
        Long start = entryTimes.get(uuid);
        if (start == null) return;

        if ((System.currentTimeMillis() - start) / 1000 >= getExploredThresholdSeconds()) {
            SchedulerUtil.runAsync(plugin, () -> journalManager.markStructureExplored(uuid, key));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        currentStructure.remove(uuid);
        entryTimes.remove(uuid);
        recordedInstances.remove(uuid);
        lastCheck.remove(uuid);
    }
}
