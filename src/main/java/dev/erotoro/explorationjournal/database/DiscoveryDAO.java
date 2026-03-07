package dev.erotoro.explorationjournal.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DiscoveryDAO {
    private final DatabaseManager db;

    public DiscoveryDAO(DatabaseManager db) {
        this.db = db;
    }

    public boolean isFirstOnServer(String discoveryKey) throws SQLException {
        String sql = "SELECT COUNT(*) FROM discoveries WHERE discovery_key = ?;";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, discoveryKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) == 0;
            }
        }
        return false;
    }

    public void addDiscovery(UUID uuid, String key, String description, boolean isFirst) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO discoveries 
            (player_uuid, discovery_key, description, is_first_on_server) 
            VALUES (?, ?, ?, ?);
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            ps.setString(3, description);
            ps.setInt(4, isFirst ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public int getPlayerDiscoveryCount(UUID uuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM discoveries WHERE player_uuid = ?;";
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
