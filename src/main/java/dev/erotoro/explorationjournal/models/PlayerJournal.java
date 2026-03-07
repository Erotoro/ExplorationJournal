package dev.erotoro.explorationjournal.models;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a player's journal data loaded from the database.
 */
public record PlayerJournal(
        UUID playerUuid,
        String playerName,
        String currentTitle,
        int journalLevel,
        Instant firstJoin
) {

    public PlayerJournal(UUID playerUuid, String playerName, String currentTitle, int journalLevel) {
        this(playerUuid, playerName, currentTitle, journalLevel, Instant.now());
    }

    public PlayerJournal {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        Objects.requireNonNull(playerName, "playerName cannot be null");
        Objects.requireNonNull(currentTitle, "currentTitle cannot be null");
        if (journalLevel < 0 || journalLevel > 4) throw new IllegalArgumentException();
    }

    public boolean hasJournal() { return journalLevel >= 1; }
    
    public String getJournalTierName() {
        return switch (journalLevel) {
            case 1 -> "Походный";
            case 2 -> "Картографа";
            case 3 -> "Исследователя";
            case 4 -> "Легенды";
            default -> "None";
        };
    }
}
