package io.github.mcpaimon.common;

import io.github.mcpaimon.api.database.IAIDatabase;
import io.github.mcpaimon.api.model.*;
import io.github.mcpaimon.api.tools.AITool;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages the core AI functionalities including platforms, models, accounts, active sessions, tools, and categories.
 */
public class MCAIManager {
    private final IAIDatabase database;
    private final Map<String, AITool> registeredTools = new ConcurrentHashMap<>();
    private final Map<String, String> registeredCategories = new ConcurrentHashMap<>();

    /**
     * Constructs a new MCAIManager instance.
     *
     * @param database The database implementation used for storing AI data.
     */
    public MCAIManager(IAIDatabase database) {
        this.database = database;
    }

    /**
     * Initializes the database connection and schemas.
     *
     * @return A CompletableFuture representing the initialization task.
     */
    public CompletableFuture<Void> initialize() { return this.database.initialize(); }

    /**
     * Shuts down the database connection securely.
     *
     * @return A CompletableFuture representing the shutdown task.
     */
    public CompletableFuture<Void> shutdown() { return this.database.close(); }

    /**
     * Registers a new AI platform in the database.
     *
     * @param displayName The display name of the AI platform.
     * @param url         The base URL of the AI platform API.
     * @return A CompletableFuture containing the newly created AIPlatform object.
     */
    public CompletableFuture<AIPlatform> registerPlatform(String displayName, String url) { return this.database.createPlatform(displayName, url); }

    /**
     * Registers a new AI model under a specific platform.
     *
     * @param platformId The ID of the platform to associate the model with.
     * @param modelId    The identifier of the AI model.
     * @return A CompletableFuture containing the newly created AIModel object.
     */
    public CompletableFuture<AIModel> registerModel(int platformId, String modelId) { return this.database.createModel(platformId, modelId); }
    
    /**
     * Creates or updates an AI account for a specific user and platform.
     *
     * @param accountType The type of the account (e.g., "player").
     * @param accountUuid The unique identifier of the account owner.
     * @param platformId  The ID of the associated platform.
     * @param token       The API token for the platform.
     * @return A CompletableFuture containing the updated or created AIAccount object.
     */
    public CompletableFuture<AIAccount> setupAccount(String accountType, String accountUuid, int platformId, String token) { 
        return this.database.createOrUpdateAccount(accountType, accountUuid, platformId, token); 
    }
    
    /**
     * Fetches an existing AI account from the database.
     *
     * @param accountType The type of the account.
     * @param accountUuid The unique identifier of the account owner.
     * @param platformId  The ID of the associated platform.
     * @return A CompletableFuture containing an Optional AIAccount.
     */
    public CompletableFuture<Optional<AIAccount>> fetchAccount(String accountType, String accountUuid, int platformId) { 
        return this.database.getAccount(accountType, accountUuid, platformId); 
    }

    /**
     * Sets the active AI session for a specific account.
     *
     * @param accountType The type of the account.
     * @param accountUuid The unique identifier of the account owner.
     * @param platformId  The ID of the platform to be used.
     * @param modelId     The identifier of the model to be used.
     * @return A CompletableFuture representing the completion of the operation.
     */
    public CompletableFuture<Void> setActiveSession(String accountType, String accountUuid, int platformId, String modelId) {
        return this.database.setActiveSession(accountType, accountUuid, platformId, modelId);
    }

    /**
     * Retrieves the current active AI session for an account.
     *
     * @param accountType The type of the account.
     * @param accountUuid The unique identifier of the account owner.
     * @return A CompletableFuture containing an Optional AIActiveSession.
     */
    public CompletableFuture<Optional<AIActiveSession>> getActiveSession(String accountType, String accountUuid) {
        return this.database.getActiveSession(accountType, accountUuid);
    }

    /**
     * Retrieves all registered AI platforms from the database.
     *
     * @return A CompletableFuture containing a list of all platforms.
     */
    public CompletableFuture<List<AIPlatform>> getAllPlatforms() {
        return this.database.getAllPlatforms();
    }

    /**
     * Creates and registers a new tool category. 
     * Prevents duplicate registration and prints a warning message if it already exists.
     *
     * @param categoryId  The unique identifier of the category.
     * @param description A brief description of the category.
     */
    public void createCategory(String categoryId, String description) {
        if (this.registeredCategories.containsKey(categoryId)) {
            System.out.println("This catagory " + categoryId + " is registered");
            return;
        }
        this.registeredCategories.put(categoryId, description);
    }

    /**
     * Retrieves all registered tool categories.
     *
     * @return A copy of the map containing all category IDs and their descriptions.
     */
    public Map<String, String> getAllCategories() {
        return new HashMap<>(this.registeredCategories);
    }

    /**
     * Registers a new AI tool.
     * Prevents duplicate registration and prints a warning message if the tool name already exists.
     *
     * @param tool The AITool instance to be registered.
     */
    public void registerTool(AITool tool) {
        String toolName = tool.getName();
        
        if (this.registeredTools.containsKey(toolName)) {
            System.out.println("[MCAI Warning] Registration skipped: tool '" + toolName + "' is already registered.");
            return;
        }
        
        this.registeredTools.put(toolName, tool); 
    }

    /**
     * Retrieves all registered AI tools.
     *
     * @return A list of all registered AITool instances.
     */
    public List<AITool> getAllRegisteredTools() { return new ArrayList<>(this.registeredTools.values()); }
    
    /**
     * Executes a specific AI tool by its name with the provided arguments.
     *
     * @param toolName  The name of the tool to execute.
     * @param arguments The arguments to pass to the tool.
     * @param account   The account initiating the tool execution.
     * @return A CompletableFuture containing the string result of the execution.
     */
    public CompletableFuture<String> executeToolCall(String toolName, Map<String, Object> arguments, AIAccount account) {
        AITool tool = registeredTools.get(toolName);
        if (tool == null) {
            return CompletableFuture.completedFuture("Error: Unknown Tool '" + toolName + "'");
        }
        return tool.execute(arguments, account);
    }
}
