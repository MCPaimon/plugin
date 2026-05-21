package io.github.mcpaimon.common;

import io.github.mcpaimon.api.database.IAIDatabase;
import io.github.mcpaimon.api.model.*;
import io.github.mcpaimon.api.tools.AITool;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class MCAIManager {
    private final IAIDatabase database;
    private final Logger logger;
    private final Map<String, AITool> registeredTools = new ConcurrentHashMap<>();

    public MCAIManager(IAIDatabase database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public CompletableFuture<Void> initialize() { return this.database.initialize(); }
    public CompletableFuture<Void> shutdown() { return this.database.close(); }

    public CompletableFuture<AIPlatform> registerPlatform(String displayName, String url) { return this.database.createPlatform(displayName, url); }
    public CompletableFuture<AIModel> registerModel(int platformId, String modelId) { return this.database.createModel(platformId, modelId); }
    
    public CompletableFuture<AIAccount> setupAccount(String accountType, String accountUuid, int platformId, String token) { 
        return this.database.createOrUpdateAccount(accountType, accountUuid, platformId, token); 
    }
    
    public CompletableFuture<Optional<AIAccount>> fetchAccount(String accountType, String accountUuid, int platformId) { 
        return this.database.getAccount(accountType, accountUuid, platformId); 
    }

    public CompletableFuture<Void> setActiveSession(String accountType, String accountUuid, int platformId, String modelId) {
        return this.database.setActiveSession(accountType, accountUuid, platformId, modelId);
    }

    public CompletableFuture<Optional<AIActiveSession>> getActiveSession(String accountType, String accountUuid) {
        return this.database.getActiveSession(accountType, accountUuid);
    }

    public void registerTool(AITool tool) { this.registeredTools.put(tool.getName(), tool); }
    public List<AITool> getAllRegisteredTools() { return new ArrayList<>(this.registeredTools.values()); }
    
    public CompletableFuture<String> executeToolCall(String toolName, Map<String, Object> arguments, AIAccount account) {
        return registeredTools.getOrDefault(toolName, (args, acc) -> CompletableFuture.completedFuture("Unknown Tool")).execute(arguments, account);
    }
}
