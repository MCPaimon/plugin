package io.github.mcpaimon.papermc;

import io.github.mcengine.mcextension.common.MCExtensionManager;
import io.github.mcpaimon.api.database.IAIDatabase;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Main PaperMC Plugin class for MCAI.
 */
public class MCAIPlugin extends JavaPlugin {

    private MCAIManager manager;
    private MCAIProvider provider;
    private MCExtensionManager extensionManager;
    
    private final Set<UUID> aiChatSessions = new HashSet<>();
    // Store the active model per player
    private final Map<UUID, String> activeModels = new ConcurrentHashMap<>();

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
            String host = getConfig().getString("database.postgresql.host");
            int port = getConfig().getInt("database.postgresql.port");
            String dbName = getConfig().getString("database.postgresql.database");
            String user = getConfig().getString("database.postgresql.username");
            String pass = getConfig().getString("database.postgresql.password");
            database = new MCAIPostgreSQL(host, port, dbName, user, pass);
        } else {
            logger.info("Using SQLite Database...");
            File dbFile = new File(getDataFolder(), getConfig().getString("database.sqlite.file", "mcai.db"));
            database = new MCAISQLite(dbFile);
        }

        this.manager = new MCAIManager(database, logger);
        this.provider = new MCAIProvider(this.manager, database);
        
        this.manager.initialize().join();

        // Auto-create platforms and models from config
        List<String> platformsConfig = getConfig().getStringList("platforms");
        if (platformsConfig != null) {
            for (String entry : platformsConfig) {
                String[] parts = entry.split(",");
                if (parts.length >= 3) {
                    this.manager.registerPlatform(parts[0].trim(), parts[1].trim()).thenAccept(p -> {
                        this.manager.registerModel(p.id(), parts[2].trim());
                    });
                }
            }
        }

        PlayerTools.registerAll(this.manager);

        MCAIAPIClient aiClient = new MCAIAPIClient();

        logger.info("Loading extensions...");
        this.extensionManager = new MCExtensionManager(-1);
        Executor mainThreadExecutor = command -> Bukkit.getScheduler().runTask(this, command);
        this.extensionManager.loadAllExtensions(this, mainThreadExecutor);

        // Register Command and TabCompleter
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

        if (this.manager != null) {
            this.manager.shutdown().join();
        }
        getLogger().info("MCAI Plugin has been disabled!");
    }

    public MCAIManager getManager() {
        return this.manager;
    }

    public MCAIProvider getProvider() {
        return this.provider;
    }

    public Set<UUID> getAiChatSessions() {
        return this.aiChatSessions;
    }

    public void setActiveModel(UUID uuid, String modelId) {
        this.activeModels.put(uuid, modelId);
    }

    public String getActiveModel(UUID uuid) {
        return this.activeModels.get(uuid);
    }
}
