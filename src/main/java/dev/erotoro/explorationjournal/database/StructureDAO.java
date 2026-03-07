package dev.erotoro.explorationjournal.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class StructureDAO {
    private final DatabaseManager db;

    public StructureDAO(DatabaseManager db) {
        this.db = db;
    }

    public void addStructureDiscovery(UUID uuid, String structureKey, int x, int z) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO structure_discoveries 
            (player_uuid, structure_key, coord_x, coord_z) 
            VALUES (?, ?, ?, ?);
            """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, structureKey);
            ps.setInt(3, x);
            ps.setInt(4, z);
            ps.executeUpdate();
        }
    }

    public void markExplored(UUID uuid, String structureKey) throws SQLException {
        String sql = "UPDATE structure_discoveries SET explored = 1 WHERE player_uuid = ? AND structure_key = ?;";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, structureKey);
            ps.executeUpdate();
        }
    }

    public int getExploredCount(UUID uuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM structure_discoveries WHERE player_uuid = ? AND explored = 1;";
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
