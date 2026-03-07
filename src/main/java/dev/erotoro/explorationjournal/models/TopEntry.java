package dev.erotoro.explorationjournal.models;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents an entry in the leaderboard (top players by various metrics).
 * Used for the /journal top command to display rankings.
 *
 * @param playerUuid The player's unique identifier
 * @param playerName The player's display name
 * @param rank       The player's rank position (1 = first place)
 * @param value      The metric value (biomes count, structures count, or discoveries count)
 * @param metricType The type of metric being ranked
 */
public record TopEntry(
        UUID playerUuid,
        String playerName,
        int rank,
        int value,
        MetricType metricType
) {

    /**
     * Creates a new TopEntry with the given parameters.
     *
     * @param playerUuid the player's UUID (must not be null)
     * @param playerName the player's name (must not be null)
     * @param rank       the rank position (must be >= 1)
     * @param value      the metric value (must be >= 0)
     * @param metricType the metric type (must not be null)
     * @throws IllegalArgumentException if playerUuid or metricType is null
     * @throws IllegalArgumentException if rank < 1 or value < 0
     */
    public TopEntry {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(playerName, "playerName cannot be null");
        Objects.requireNonNull(metricType, "metricType cannot be null");

        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1, got: " + rank);
        }

        if (value < 0) {
            throw new IllegalArgumentException("value must be >= 0, got: " + value);
        }
    }

    /**
     * Creates a TopEntry for biome count ranking.
     *
     * @param playerUuid the player's UUID
     * @param playerName the player's name
     * @param rank       the rank position
     * @param biomeCount the number of biomes discovered
     * @return a new TopEntry for biomes metric
     */
    public static TopEntry ofBiomes(UUID playerUuid, String playerName, int rank, int biomeCount) {
        return new TopEntry(playerUuid, playerName, rank, biomeCount, MetricType.BIOMES);
    }

    /**
     * Creates a TopEntry for structure count ranking.
     *
     * @param playerUuid the player's UUID
     * @param playerName the player's name
     * @param rank       the rank position
     * @param structureCount the number of structures discovered
     * @return a new TopEntry for structures metric
     */
    public static TopEntry ofStructures(UUID playerUuid, String playerName, int rank, int structureCount) {
        return new TopEntry(playerUuid, playerName, rank, structureCount, MetricType.STRUCTURES);
    }

    /**
     * Creates a TopEntry for discoveries count ranking.
     *
     * @param playerUuid the player's UUID
     * @param playerName the player's name
     * @param rank       the rank position
     * @param discoveryCount the number of discoveries made
     * @return a new TopEntry for discoveries metric
     */
    public static TopEntry ofDiscoveries(UUID playerUuid, String playerName, int rank, int discoveryCount) {
        return new TopEntry(playerUuid, playerName, rank, discoveryCount, MetricType.DISCOVERIES);
    }

    /**
     * Gets the formatted rank string with ordinal suffix.
     *
     * @return the rank with suffix (e.g., "1st", "2nd", "3rd", "4th")
     */
    public String getFormattedRank() {
        int mod100 = rank % 100;
        int mod10 = rank % 10;

        String suffix = switch (mod10) {
            case 1 -> mod100 == 11 ? "th" : "st";
            case 2 -> mod100 == 12 ? "th" : "nd";
            case 3 -> mod100 == 13 ? "th" : "rd";
            default -> "th";
        };

        return rank + suffix;
    }

    /**
     * Gets the metric name in the appropriate grammatical case for display.
     *
     * @return the metric name (e.g., "biomes", "structures", "discoveries")
     */
    public String getMetricName() {
        return switch (metricType) {
            case BIOMES -> "biomes";
            case STRUCTURES -> "structures";
            case DISCOVERIES -> "discoveries";
        };
    }

    /**
     * Enum representing the type of metric being ranked.
     */
    public enum MetricType {
        BIOMES,
        STRUCTURES,
        DISCOVERIES
    }
}
