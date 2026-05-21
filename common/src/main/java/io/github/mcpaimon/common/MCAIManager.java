package io.github.mcpaimon.common;

import io.github.mcpaimon.api.database.IAIDatabase;
import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.model.AILog;
import io.github.mcpaimon.api.model.AIModel;
import io.github.mcpaimon.api.model.AIPlatform;
import io.github.mcpaimon.api.tool.AITool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Central manager for MCAI logic.
 * Exclusively uses CompletableFuture for async processing.
 * Acts as a central registry for AI Tools and Extension Classes.
 * Strictly pure Java implementation.
 */
public class MCAIManager {

    private final IAIDatabase database;
    private final Logger logger;

    // Memory registries for Function Calling and Extensions
    private final Map<String, AITool> registeredTools;
    private final Map<String, Object> registeredExtensions;

    public MCAIManager(IAIDatabase database, Logger logger) {
        this.database = database;
        this.logger = logger;
        this.registeredTools = new ConcurrentHashMap<>();
        this.registeredExtensions = new ConcurrentHashMap<>();
    }

    public CompletableFuture<Void> initialize() {
        this.logger.info("Initializing MCAI Database...");
        return this.database.initialize()
            .thenRun(() -> this.logger.info("Database initialized successfully."))
            .exceptionally(throwable -> {
                this.logger.severe("Failed to initialize database: " + throwable.getMessage());
                return null;
            });
    }

    public CompletableFuture<Void> shutdown() {
        this.logger.info("Shutting down MCAI Database and unregistering tools...");
        this.registeredTools.clear();
        this.registeredExtensions.clear();
        return this.database.close();
    }

    /* --- Database Operations --- */

    public CompletableFuture<AIPlatform> registerPlatform(String displayName, String url) {
        return this.database.createPlatform(displayName, url);
    }

    public CompletableFuture<AIModel> registerModel(int platformId, String modelId) {
        return this.database.createModel(platformId, modelId);
    }

    public CompletableFuture<AIAccount> setupAccount(String accountType, String accountUuid, int platformId, String token) {
        return this.database.createOrUpdateAccount(accountType, accountUuid, platformId, token);
    }

    public CompletableFuture<Optional<AIAccount>> fetchAccount(String accountType, String accountUuid) {
        return this.database.getAccount(accountType, accountUuid);
    }

    public CompletableFuture<AILog> logInteraction(AIAccount account, String modelId, String chatHistory, int tokenTotalUsage) {
        return this.database.insertLog(
            account.accountType(),
            account.accountUuid(),
            account.platformId(),
            modelId,
            chatHistory,
            tokenTotalUsage
        );
    }
    
    public CompletableFuture<List<AILog>> fetchRecentHistory(String accountType, String accountUuid, int limit) {
        return this.database.getLogsByAccount(accountType, accountUuid, limit);
    }

    /* --- AI Tool (Function Calling) Registry --- */

    /**
     * Registers a new AI tool for function calling.
     *
     * @param tool The tool implementation to register.
     */
    public void registerTool(AITool tool) {
        if (tool == null || tool.getName() == null) {
            this.logger.warning("Attempted to register an invalid AITool.");
            return;
        }
        this.registeredTools.put(tool.getName(), tool);
        this.logger.info("Registered AI Tool: " + tool.getName());
    }

    /**
     * Unregisters an existing AI tool.
     *
     * @param toolName The name of the tool to remove.
     */
    public void unregisterTool(String toolName) {
        this.registeredTools.remove(toolName);
        this.logger.info("Unregistered AI Tool: " + toolName);
    }

    /**
     * Gets a list of all registered tools to send their schemas to the AI provider.
     *
     * @return A list of registered tools.
     */
    public List<AITool> getAllRegisteredTools() {
        return new ArrayList<>(this.registeredTools.values());
    }

    /**
     * Executes a tool dynamically based on the AI's function call response.
     *
     * @param toolName  The name of the tool requested by the AI.
     * @param arguments The parsed JSON arguments.
     * @param account   The account initiating the execution.
     * @return A CompletableFuture containing the result string.
     */
    public CompletableFuture<String> executeToolCall(String toolName, Map<String, Object> arguments, AIAccount account) {
        AITool tool = this.registeredTools.get(toolName);
        
        if (tool == null) {
            this.logger.warning("AI attempted to call an unknown tool: " + toolName);
            return CompletableFuture.completedFuture("Error: Tool '" + toolName + "' is not registered.");
        }

        try {
            return tool.execute(arguments, account);
        } catch (Exception e) {
            this.logger.severe("Exception while executing tool " + toolName + ": " + e.getMessage());
            return CompletableFuture.completedFuture("Error: Failed to execute tool due to an internal exception.");
        }
    }

    /* --- Extension / Class Registry --- */

    /**
     * Registers an arbitrary extension class instance in memory.
     * Useful for addons that need to share state or APIs through the manager.
     *
     * @param extensionKey A unique key for the extension.
     * @param instance     The instance of the class to store.
     */
    public void registerExtension(String extensionKey, Object instance) {
        if (extensionKey == null || instance == null) return;
        this.registeredExtensions.put(extensionKey, instance);
        this.logger.info("Registered Extension Class: " + extensionKey);
    }

    /**
     * Retrieves a registered extension class instance.
     *
     * @param extensionKey The unique key.
     * @param type         The expected class type.
     * @param <T>          The type parameter.
     * @return An Optional containing the casted instance if found and valid.
     */
    public <T> Optional<T> getExtension(String extensionKey, Class<T> type) {
        Object instance = this.registeredExtensions.get(extensionKey);
        
        if (type.isInstance(instance)) {
            return Optional.of(type.cast(instance));
        }
        
        return Optional.empty();
    }
}
