package io.github.mcpaimon.papermc;

import io.github.mcengine.mcextension.common.MCExtensionManager;
import io.github.mcpaimon.api.database.IAIDatabase;
import io.github.mcpaimon.api.model.AIPlatform;
import io.github.mcpaimon.common.MCAIManager;
import io.github.mcpaimon.common.MCAIProvider;
import io.github.mcpaimon.common.database.postgresql.MCAIPostgreSQL;
import io.github.mcpaimon.common.database.sqlite.MCAISQLite;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Main plugin class for MCAI on PaperMC.
 */
public class MCAIPlugin extends JavaPlugin {

    /**
     * Manager for handling core AI data and interactions.
     */
    private MCAIManager manager;

    /**
     * Provider serving as the entry point for AI workflows and queries.
     */
    private MCAIProvider provider;

    /**
     * Manager handling the dynamic loading and unloading of extensions.
     */
    private MCExtensionManager extensionManager;

    /**
     * Set of player UUIDs currently in an active AI chat session.
     */
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

        String secretKey = getConfig().getString("token.secret", "secretkey");
        this.manager = new MCAIManager(database, secretKey);

        // Retrieve max_workflow_iterations from config, defaulting to 5
        int maxWorkflowIterations = getConfig().getInt("max_workflow_iterations", 5);
        this.provider = new MCAIProvider(this.manager, database, maxWorkflowIterations);

        // Auto-create platforms and models from config safely
        List<String> platformsConfig = getConfig().getStringList("platforms");
        if (platformsConfig != null && !platformsConfig.isEmpty()) {
            try {
                // Fetch existing platforms to avoid duplicates
                List<AIPlatform> existingPlatforms = this.provider.getPlatforms().join();
                
                for (String entry : platformsConfig) {
                    // Split by comma and support multiple models trailing behind the URL
                    String[] parts = entry.split(",");
                    if (parts.length >= 3) {
                        String pName = parts[0].trim();
                        String pUrl = parts[1].trim();
                        
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
                        } else {
                            logger.info("Found existing platform: " + pName);
                        }
                        
                        // Register ALL models listed for this platform (Loop from index 2 onwards)
                        for (int i = 2; i < parts.length; i++) {
                            String pModel = parts[i].trim();
                            if (!pModel.isEmpty()) {
                                this.manager.registerModel(targetPlatform.id(), pModel).join();
                                logger.info("Auto-registered model: " + pModel + " for platform: " + pName);
                            }
                        }
                    } else {
                        logger.warning("Invalid format in config.yml -> platforms. Expected 'name,url,model1,model2...', but got: " + entry);
                    }
                }
            } catch (Exception e) {
                logger.severe("Error during auto-creating platforms/models: " + e.getMessage());
                e.printStackTrace(); // Print full stack trace for debugging
            }
        } else {
            logger.warning("No platforms defined in config.yml or 'platforms' list is empty.");
        }

        // Initialize Console Account
        if (getConfig().contains("console.platform")) {
            String cPlatform = getConfig().getString("console.platform");
            String cModel = getConfig().getString("console.model");
            String cToken = getConfig().getString("console.token");
            
            this.provider.getPlatforms().thenAccept(platforms -> {
                platforms.stream().filter(p -> p.displayName().equalsIgnoreCase(cPlatform)).findFirst().ifPresent(p -> {
                    this.manager.setupAccount("console", "00000000-0000-0000-0000-000000000000", p.id(), cToken).join();
                    this.manager.setActiveSession("console", "00000000-0000-0000-0000-000000000000", p.id(), cModel).join();
                    logger.info("Console AI Account successfully configured.");
                });
            });
        }

        logger.info("Loading extensions...");
        this.extensionManager = new MCExtensionManager(-1);
        Executor mainThreadExecutor = command -> Bukkit.getScheduler().runTask(this, command);
        this.extensionManager.loadAllExtensions(this, mainThreadExecutor);

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
        if (this.provider != null) this.provider.shutdown().join();
        getLogger().info("MCAI Plugin has been disabled!");
    }

    /**
     * Gets the MCAIManager instance.
     * @return The active MCAIManager.
     */
    public MCAIManager getManager() { return this.manager; }
    
    /**
     * Gets the MCAIProvider instance.
     * @return The active MCAIProvider.
     */
    public MCAIProvider getProvider() { return this.provider; }
    
    /**
     * Gets the set of UUIDs of players currently in AI chat sessions.
     * @return A set of player UUIDs.
     */
    public Set<UUID> getAiChatSessions() { return this.aiChatSessions; }
}
