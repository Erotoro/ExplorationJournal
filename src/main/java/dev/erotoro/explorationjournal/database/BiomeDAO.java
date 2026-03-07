package dev.erotoro.explorationjournal.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class BiomeDAO {
    private final DatabaseManager db;

    public BiomeDAO(DatabaseManager db) {
        this.db = db;
    }

    public void addBiomeDiscovery(UUID uuid, String biomeKey, String dimension, int x, int z) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO biome_discoveries 
            (player_uuid, biome_key, dimension, coord_x, coord_z) 
            VALUES (?, ?, ?, ?, ?);
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, biomeKey);
            ps.setString(3, dimension);
            ps.setInt(4, x);
            ps.setInt(5, z);
            ps.executeUpdate();
        }
    }

    public void incrementTimeSpent(UUID uuid, String biomeKey, int seconds) throws SQLException {
        String sql = "UPDATE biome_discoveries SET time_spent_seconds = time_spent_seconds + ? WHERE player_uuid = ? AND biome_key = ?;";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, seconds);
            ps.setString(2, uuid.toString());
            ps.setString(3, biomeKey);
            ps.executeUpdate();
        }
    }

    public int getDiscoveredCount(UUID uuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM biome_discoveries WHERE player_uuid = ?;";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }
}
