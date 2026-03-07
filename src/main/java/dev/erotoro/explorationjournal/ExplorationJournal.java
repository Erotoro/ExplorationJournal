package dev.erotoro.explorationjournal;

import dev.erotoro.explorationjournal.commands.JournalAdminCommand;
import dev.erotoro.explorationjournal.commands.JournalCommand;
import dev.erotoro.explorationjournal.craft.JournalRecipes;
import dev.erotoro.explorationjournal.craft.JournalUpgradeHandler;
import dev.erotoro.explorationjournal.database.BiomeDAO;
import dev.erotoro.explorationjournal.database.DatabaseManager;
import dev.erotoro.explorationjournal.database.DiscoveryDAO;
import dev.erotoro.explorationjournal.database.PlayerDAO;
import dev.erotoro.explorationjournal.database.StructureDAO;
import dev.erotoro.explorationjournal.integrations.BluemapIntegration;
import dev.erotoro.explorationjournal.integrations.DiscordIntegration;
import dev.erotoro.explorationjournal.integrations.PAPIExpansion;
import dev.erotoro.explorationjournal.listeners.*;
import dev.erotoro.explorationjournal.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;

public final class ExplorationJournal extends JavaPlugin {

    private org.bukkit.configuration.file.YamlConfiguration messagesConfig;

    private static ExplorationJournal instance;

    private DatabaseManager databaseManager;
    private PlayerDAO playerDAO;
    private BiomeDAO biomeDAO;
    private StructureDAO structureDAO;
    private DiscoveryDAO discoveryDAO;

    private BiomeManager biomeManager;
    private StructureManager structureManager;
    private DiscoveryManager discoveryManager;
    private RewardManager rewardManager;
    private TopManager topManager;
    private TitleManager titleManager;
    private JournalManager journalManager;

    private ChatListener chatListener;
    private PAPIExpansion papiExpansion;
    private BluemapIntegration bluemapIntegration;
    private DiscordIntegration discordIntegration;

    @NotNull
    public static ExplorationJournal getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfigs();

        loadMessagesConfig();

        // 1. Database
        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.init();
        } catch (SQLException e) {
            getLogger().severe("Failed to initialise database! " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 2. DAOs
        playerDAO = new PlayerDAO(databaseManager);
        biomeDAO = new BiomeDAO(databaseManager);
        structureDAO = new StructureDAO(databaseManager);
        discoveryDAO = new DiscoveryDAO(databaseManager);

        // 3. Managers
        biomeManager = new BiomeManager(this, biomeDAO, databaseManager);
        structureManager = new StructureManager(this, structureDAO, databaseManager);
        discoveryManager = new DiscoveryManager(this, discoveryDAO, databaseManager);
        rewardManager = new RewardManager(this);
        topManager = new TopManager(databaseManager, getLogger());
        titleManager = new TitleManager(this, playerDAO, biomeManager, structureManager, discoveryManager, rewardManager);
        journalManager = new JournalManager(this, databaseManager, playerDAO, biomeManager, structureManager, discoveryManager, topManager);

        // 4. Listeners
        chatListener = new ChatListener(this);
        Bukkit.getPluginManager().registerEvents(new BiomeListener(this), this);
        Bukkit.getPluginManager().registerEvents(new StructureListener(this), this);
        Bukkit.getPluginManager().registerEvents(new DiscoveryListener(this), this);
        Bukkit.getPluginManager().registerEvents(chatListener, this);
        Bukkit.getPluginManager().registerEvents(new JournalUpgradeHandler(this), this);
        Bukkit.getPluginManager().registerEvents(new JoinListener(this, journalManager, playerDAO), this);
        Bukkit.getPluginManager().registerEvents(new JournalInteractListener(this, journalManager), this);

        // 5. Commands
        getCommand("journal").setExecutor(new JournalCommand(this));
        getCommand("journaladmin").setExecutor(new JournalAdminCommand(this));

        // 6. Recipes
        new JournalRecipes(this).registerAllRecipes();

        // 7. Integrations
        hookIntegrations();

        getLogger().info("ExplorationJournal enabled successfully!");
    }

    /**
     * Получить конфиг messages.yml
     */
    public org.bukkit.configuration.file.YamlConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    @Override
    public void onDisable() {
        SchedulerUtil.cancelAll(this);
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("ExplorationJournal disabled.");
    }

    private void hookIntegrations() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiExpansion = new PAPIExpansion(this);
            papiExpansion.register();
            getLogger().info("Hooked into PlaceholderAPI");
        }
        
        if (Bukkit.getPluginManager().getPlugin("BlueMap") != null) {
            bluemapIntegration = new BluemapIntegration(this);
            if (bluemapIntegration.enable()) {
                getLogger().info("Hooked into BlueMap");
            }
        }
        
        if (Bukkit.getPluginManager().getPlugin("DiscordSRV") != null) {
            discordIntegration = new DiscordIntegration(this);
            if (discordIntegration.enable()) {
                getLogger().info("Hooked into DiscordSRV");
            }
        }
    }

    public void reloadConfigs() {
        reloadConfig();
        loadMessagesConfig();
        rewardManager.reload();
        titleManager.reload();
        journalManager.reload();
        if (bluemapIntegration != null) bluemapIntegration.reload();
        if (discordIntegration != null) discordIntegration.reload();
        getLogger().info("Configuration reloaded.");
    }

    private void loadMessagesConfig() {
        java.io.File messagesFile = new java.io.File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(messagesFile);
    }

    public JournalManager getJournalManager() { return journalManager; }
    public TitleManager getTitleManager() { return titleManager; }
    public RewardManager getRewardManager() { return rewardManager; }
    public ChatListener getChatListener() { return chatListener; }
    
    @Nullable
    public PAPIExpansion getPapiExpansion() { return papiExpansion; }
    @Nullable
    public BluemapIntegration getBluemapIntegration() { return bluemapIntegration; }
    @Nullable
    public DiscordIntegration getDiscordIntegration() { return discordIntegration; }

    private void saveDefaultConfigs() {
        saveDefaultConfig();
        String[] resources = {"messages.yml", "biomes.yml", "structures.yml", "discoveries.yml", "rewards.yml"};
        for (String r : resources) {
            if (!new java.io.File(getDataFolder(), r).exists()) {
                saveResource(r, false);
            }
        }
    }
}
