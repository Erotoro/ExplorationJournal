package dev.erotoro.explorationjournal.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PlayerDAO {
    private final DatabaseManager db;

    public PlayerDAO(DatabaseManager db) {
        this.db = db;
    }

    public void upsertPlayer(UUID uuid, String name) throws SQLException {
        String sql = """
            INSERT INTO players (player_uuid, player_name) VALUES (?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET player_name = excluded.player_name;
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    public void updateJournalLevel(UUID uuid, int level) throws SQLException {
        String sql = "UPDATE players SET journal_level = ? WHERE player_uuid = ?;";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, level);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    public void updateTitle(UUID uuid, String title) throws SQLException {
        String sql = "UPDATE players SET current_title = ? WHERE player_uuid = ?;";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
    }

    public String getCurrentTitle(UUID uuid) throws SQLException {
        String sql = "SELECT current_title FROM players WHERE player_uuid = ?;";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("current_title");
            }
        }
        return "Новичок";
    }
}
