package dev.erotoro.explorationjournal.managers;

import dev.erotoro.explorationjournal.database.DatabaseManager;
import dev.erotoro.explorationjournal.models.TopEntry;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides leaderboard queries for the /journal top command.
 */
public class TopManager {

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public TopManager(@NotNull DatabaseManager databaseManager, @NotNull Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    @NotNull
    public List<TopEntry> getTopBiomes(int limit) {
        String sql = """
                SELECT p.player_uuid, p.player_name, COUNT(*) AS cnt
                FROM biome_discoveries bd
                JOIN players p ON p.player_uuid = bd.player_uuid
                GROUP BY bd.player_uuid
                ORDER BY cnt DESC
                LIMIT ?
                """;
        return queryTop(sql, limit, TopEntry.MetricType.BIOMES);
    }

    @NotNull
    public List<TopEntry> getTopStructures(int limit) {
        String sql = """
                SELECT p.player_uuid, p.player_name, COUNT(*) AS cnt
                FROM structure_discoveries sd
                JOIN players p ON p.player_uuid = sd.player_uuid
                GROUP BY sd.player_uuid
                ORDER BY cnt DESC
                LIMIT ?
                """;
        return queryTop(sql, limit, TopEntry.MetricType.STRUCTURES);
    }

    @NotNull
    public List<TopEntry> getTopDiscoveries(int limit) {
        String sql = """
                SELECT p.player_uuid, p.player_name, COUNT(*) AS cnt
                FROM discoveries d
                JOIN players p ON p.player_uuid = d.player_uuid
                GROUP BY d.player_uuid
                ORDER BY cnt DESC
                LIMIT ?
                """;
        return queryTop(sql, limit, TopEntry.MetricType.DISCOVERIES);
    }

    @NotNull
    private List<TopEntry> queryTop(@NotNull String sql, int limit, @NotNull TopEntry.MetricType metricType) {
        List<TopEntry> entries = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    String name = rs.getString("player_name");
                    int value = rs.getInt("cnt");
                    entries.add(new TopEntry(uuid, name, rank++, value, metricType));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to query top " + metricType, e);
        }
        return Collections.unmodifiableList(entries);
    }
}
