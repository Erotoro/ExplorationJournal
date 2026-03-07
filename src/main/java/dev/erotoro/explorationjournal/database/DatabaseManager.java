package dev.erotoro.explorationjournal.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Менеджер базы данных. 
 * Отвечает за инициализацию пула соединений HikariCP, настройку SQLite 
 * и создание структуры таблиц.
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Инициализирует подключение к базе данных.
     * Создает файл базы данных, если он отсутствует, и настраивает пул соединений.
     * 
     * @throws SQLException если не удалось установить соединение или создать таблицы.
     */
    public void init() throws SQLException {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File dbFile = new File(plugin.getDataFolder(), "database.db");
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setPoolName("ExplorationJournalPool");
        
        config.setMaximumPoolSize(1);
        
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("foreign_keys", "true");
        config.addDataSourceProperty("temp_store", "memory");

        this.dataSource = new HikariDataSource(config);
        
        createTables();
        
        plugin.getLogger().info("База данных успешно инициализирована (режим WAL).");
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource не инициализирован.");
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    player_uuid   TEXT PRIMARY KEY,
                    player_name   TEXT NOT NULL,
                    current_title TEXT DEFAULT 'Новичок',
                    journal_level INTEGER DEFAULT 0,
                    first_join    TEXT DEFAULT (datetime('now'))
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS biome_discoveries (
                    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid        TEXT NOT NULL,
                    biome_key          TEXT NOT NULL,
                    dimension          TEXT NOT NULL,
                    coord_x            INTEGER,
                    coord_z            INTEGER,
                    time_spent_seconds INTEGER DEFAULT 0,
                    discovered_at      TEXT DEFAULT (datetime('now')),
                    UNIQUE(player_uuid, biome_key)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS structure_discoveries (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid   TEXT NOT NULL,
                    structure_key TEXT NOT NULL,
                    coord_x       INTEGER,
                    coord_z       INTEGER,
                    explored      INTEGER DEFAULT 0,
                    discovered_at TEXT DEFAULT (datetime('now'))
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS discoveries (
                    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid        TEXT NOT NULL,
                    discovery_key      TEXT NOT NULL,
                    description        TEXT,
                    is_first_on_server INTEGER DEFAULT 0,
                    discovered_at      TEXT DEFAULT (datetime('now')),
                    UNIQUE(player_uuid, discovery_key)
                );
            """);
        }
    }
}
