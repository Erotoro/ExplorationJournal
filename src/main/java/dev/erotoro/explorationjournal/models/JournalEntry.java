package dev.erotoro.explorationjournal.models;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single journal entry.
 */
public record JournalEntry(
        EntryType entryType,
        String entryKey,
        String dimension,
        int coordX,
        int coordZ,
        int timeSpent,
        Instant discoveredAt
) {

    public JournalEntry {
        Objects.requireNonNull(entryType);
        Objects.requireNonNull(entryKey);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(discoveredAt);
    }

    public static JournalEntry ofBiome(String biomeKey, String dimension, int coordX, int coordZ,
                                        int timeSpent, Instant discoveredAt) {
        return new JournalEntry(EntryType.BIOME, biomeKey, dimension, coordX, coordZ, timeSpent, discoveredAt);
    }

    public static JournalEntry ofStructure(String structureKey, String dimension, int coordX, int coordZ,
                                            int timeSpent, Instant discoveredAt) {
        return new JournalEntry(EntryType.STRUCTURE, structureKey, dimension, coordX, coordZ, timeSpent, discoveredAt);
    }

    public static JournalEntry ofStructure(String structureKey, String dimension, int coordX, int coordZ,
                                            Instant discoveredAt) {
        return new JournalEntry(EntryType.STRUCTURE, structureKey, dimension, coordX, coordZ, 0, discoveredAt);
    }

    public String getFormattedCoordinates() {
        return coordX + ", " + coordZ;
    }

    public String getSimpleName() {
        int colonIndex = entryKey.indexOf(':');
        return colonIndex >= 0 ? entryKey.substring(colonIndex + 1) : entryKey;
    }

    public enum EntryType {
        BIOME,
        STRUCTURE
    }
}
