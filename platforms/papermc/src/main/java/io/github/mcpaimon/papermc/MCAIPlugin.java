package io.github.mcpaimon.papermc;

import io.github.mcengine.mcextension.common.MCExtensionManager;
import io.github.mcpaimon.api.database.IAIDatabase;
import io.github.mcpaimon.api.model.AIPlatform;
import io.github.mcpaimon.common.MCAIManager;
import io.github.mcpaimon.common.MCAIProvider;
import io.github.mcpaimon.common.client.MCAIAPIClient;
import io.github.mcpaimon.common.database.postgresql.MCAIPostgreSQL;
import io.github.mcpaimon.common.database.sqlite.MCAISQLite;
import io.github.mcpaimon.papermc.commands.MCAICommand;
import io.github.mcpaimon.papermc.listeners.MCAIListener;
import io.github.mcpaimon.papermc.tools.PlayerTools;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public class MCAIPlugin extends JavaPlugin {

    private MCAIManager manager;
    private MCAIProvider provider;
    private MCExtensionManager extensionManager;
    private final Set<UUID> aiChatSessions = new HashSet<>();

    @Override
    public void onEnable() {
        Logger logger = getLogger();
        
        saveDefaultConfig();
        if (!getConfig().getBoolean("enable", false)) {
            logger.warning("Plugin is set to 'enable: false' in config.yml. Shutting down MCAI...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        IAIDatabase database;
        String dbType = getConfig().getString("database.type", "sqlite");
        
        if (dbType.equalsIgnoreCase("postgresql")) {
            logger.info("Using PostgreSQL Database...");
            database = new MCAIPostgreSQL(
                getConfig().getString("database.postgresql.host"),
                getConfig().getInt("database.postgresql.port"),
                getConfig().getString("database.postgresql.database"),
                getConfig().getString("database.postgresql.username"),
                getConfig().getString("database.postgresql.password")
            );
        } else {
            logger.info("Using SQLite Database...");
            database = new MCAISQLite(new File(getDataFolder(), getConfig().getString("database.sqlite.file", "mcai.db")));
        }

        this.manager = new MCAIManager(database, logger);
        this.provider = new MCAIProvider(this.manager, database);
        
        // Wait for database tables to be created
        this.manager.initialize().join();

        // Auto-create platforms and models from config safely
        List<String> platformsConfig = getConfig().getStringList("platforms");
        if (platformsConfig != null && !platformsConfig.isEmpty()) {
            try {
                // Fetch existing platforms to avoid duplicates
                List<AIPlatform> existingPlatforms = this.provider.getPlatforms().join();
                
                for (String entry : platformsConfig) {
                    String[] parts = entry.split(",");
                    if (parts.length >= 3) {
                        String pName = parts[0].trim();
                        String pUrl = parts[1].trim();
                        String pModel = parts[2].trim();
                        
                        // Check if platform already exists
                        AIPlatform targetPlatform = existingPlatforms.stream()
                                .filter(p -> p.displayName().equalsIgnoreCase(pName))
                                .findFirst()
                                .orElse(null);
                                
                        if (targetPlatform == null) {
                            // Create new platform and block until finished (.join)
                            targetPlatform = this.manager.registerPlatform(pName, pUrl).join();
                            existingPlatforms.add(targetPlatform); // Update local cache
                            logger.info("Auto-registered new platform: " + pName);
                        }
                        
                        // Register the model for this platform and block until finished
                        this.manager.registerModel(targetPlatform.id(), pModel).join();
                        logger.info("Auto-registered model: " + pModel + " for platform: " + pName);
                    }
                }
            } catch (Exception e) {
                logger.severe("Error during auto-creating platforms/models: " + e.getMessage());
            }
        }

        PlayerTools.registerAll(this.manager);
        MCAIAPIClient aiClient = new MCAIAPIClient();

        logger.info("Loading extensions...");
        this.extensionManager = new MCExtensionManager(-1);
        Executor mainThreadExecutor = command -> Bukkit.getScheduler().runTask(this, command);
        this.extensionManager.loadAllExtensions(this, mainThreadExecutor);

        MCAICommand aiCommand = new MCAICommand(this);
        getCommand("ai").setExecutor(aiCommand);
        getCommand("ai").setTabCompleter(aiCommand);
        
        getServer().getPluginManager().registerEvents(new MCAIListener(this, this.provider, aiClient), this);
        logger.info("MCAI Plugin has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        if (this.extensionManager != null) {
            Executor disableExecutor = command -> {
                if (this.isEnabled()) Bukkit.getScheduler().runTask(this, command);
                else command.run(); 
            };
            this.extensionManager.disableAllExtensions(this, disableExecutor);
        }
        if (this.manager != null) this.manager.shutdown().join();
        getLogger().info("MCAI Plugin has been disabled!");
    }

    public MCAIManager getManager() { return this.manager; }
    public MCAIProvider getProvider() { return this.provider; }
    public Set<UUID> getAiChatSessions() { return this.aiChatSessions; }
}
