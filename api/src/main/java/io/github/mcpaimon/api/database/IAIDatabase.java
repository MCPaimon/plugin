package io.github.mcpaimon.api.database;

import io.github.mcpaimon.api.model.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface IAIDatabase {
    CompletableFuture<Void> initialize();
    CompletableFuture<Void> close();

    CompletableFuture<AIPlatform> createPlatform(String displayName, String url);
    CompletableFuture<Optional<AIPlatform>> getPlatform(int id);
    CompletableFuture<List<AIPlatform>> getAllPlatforms();

    CompletableFuture<AIModel> createModel(int platformId, String modelId);
    CompletableFuture<List<AIModel>> getModelsByPlatform(int platformId);
    CompletableFuture<Optional<AIModel>> getModel(int platformId, String modelId);

    CompletableFuture<AIAccount> createOrUpdateAccount(String accountType, String accountUuid, int platformId, String token);
    CompletableFuture<Optional<AIAccount>> getAccount(String accountType, String accountUuid, int platformId);
    CompletableFuture<Void> deleteAccount(String accountType, String accountUuid);

    CompletableFuture<Void> setActiveSession(String accountType, String accountUuid, int platformId, String modelId);
    CompletableFuture<Optional<AIActiveSession>> getActiveSession(String accountType, String accountUuid);

    CompletableFuture<AILog> insertLog(String accountType, String accountUuid, int platformId, String modelId, String chatHistory, int tokenTotalUsage);
    CompletableFuture<List<AILog>> getLogsByAccount(String accountType, String accountUuid, int limit);
}
