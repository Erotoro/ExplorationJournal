package dev.erotoro.explorationjournal.models;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a unique discovery made by a player.
 */
public record Discovery(
        String discoveryKey,
        String description,
        DiscoveryCategory category,
        boolean isFirstOnServer,
        Instant discoveredAt
) {

    public Discovery {
        Objects.requireNonNull(discoveryKey);
        Objects.requireNonNull(description);
        Objects.requireNonNull(category);
        Objects.requireNonNull(discoveredAt);
    }

    public String getDisplayName() {
        String name = discoveryKey.replace('-', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    public enum DiscoveryCategory {
        NATURE,
        COMBAT,
        SECRET,
        PROCEDURAL
    }
}
