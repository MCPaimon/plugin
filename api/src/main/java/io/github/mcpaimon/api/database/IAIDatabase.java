package io.github.mcpaimon.api.database;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.model.AILog;
import io.github.mcpaimon.api.model.AIModel;
import io.github.mcpaimon.api.model.AIPlatform;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Core interface for database operations.
 * Must be implemented by platform-specific database providers (e.g., PostgreSQL, MongoDB).
 * Absolutely NO java.sql.* classes should be exposed here.
 */
public interface IAIDatabase {

    /**
     * Initializes the database connection and creates schemas if necessary.
     */
    CompletableFuture<Void> initialize();

    /**
     * Closes the database connection cleanly.
     */
    CompletableFuture<Void> close();

    /* --- Platform Operations --- */
    
    CompletableFuture<AIPlatform> createPlatform(String displayName, String url);
    CompletableFuture<Optional<AIPlatform>> getPlatform(int id);
    CompletableFuture<List<AIPlatform>> getAllPlatforms();

    /* --- Model Operations --- */
    
    CompletableFuture<AIModel> createModel(int platformId, String modelId);
    CompletableFuture<List<AIModel>> getModelsByPlatform(int platformId);
    CompletableFuture<Optional<AIModel>> getModel(int platformId, String modelId);

    /* --- Account Operations --- */
    
    CompletableFuture<AIAccount> createOrUpdateAccount(String accountType, String accountUuid, int platformId, String token);
    CompletableFuture<Optional<AIAccount>> getAccount(String accountType, String accountUuid);
    CompletableFuture<Void> deleteAccount(String accountType, String accountUuid);

    /* --- Log Operations --- */
    
    CompletableFuture<AILog> insertLog(String accountType, String accountUuid, int platformId, String modelId, String chatHistory, int tokenTotalUsage);
    CompletableFuture<List<AILog>> getLogsByAccount(String accountType, String accountUuid, int limit);
}
