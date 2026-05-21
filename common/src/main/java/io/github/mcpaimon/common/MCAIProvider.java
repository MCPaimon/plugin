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

/**
 * Central API Provider for handling AI workflows and data retrieval.
 */
public class MCAIProvider {

    private static final String DEFAULT_ACCOUNT_TYPE = "player";

    private final MCAIManager manager;
    private final IAIDatabase database;

    public MCAIProvider(MCAIManager manager, IAIDatabase database) {
        this.manager = manager;
        this.database = database;
    }

    /* --- Core AI Workflow --- */

    public CompletableFuture<String> sendPrompt(String accountType, String accountUuid, String modelId, String prompt, IAIWorkflowClient aiClient) {
        String type = (accountType == null || accountType.isBlank()) ? DEFAULT_ACCOUNT_TYPE : accountType;

        return this.manager.fetchAccount(type, accountUuid).thenCompose(accountOpt -> {
            if (accountOpt.isEmpty() || accountOpt.get().token().isBlank()) {
                return CompletableFuture.completedFuture("Error: No API token found. Please register your token first.");
            }
            AIAccount account = accountOpt.get();

            // Fetch the AI Platform dynamically from the database to get the URL
            return this.database.getPlatform(account.platformId()).thenCompose(platformOpt -> {
                if (platformOpt.isEmpty()) {
                    return CompletableFuture.completedFuture("Error: The AI platform associated with your account no longer exists.");
                }
                AIPlatform platform = platformOpt.get();

                List<AITool> allTools = this.manager.getAllRegisteredTools();

                // Ask AI to decide which tools to use, passing the dynamic platform URL and model ID
                return aiClient.decideTools(platform, modelId, account, prompt, allTools).thenCompose(toolCalls -> {
                    
                    if (toolCalls == null || toolCalls.isEmpty()) {
                        return aiClient.generateFinalSummary(platform, modelId, account, prompt, "No tools were used.");
                    }

                    List<CompletableFuture<String>> executionFutures = new ArrayList<>();
                    for (ToolCall call : toolCalls) {
                        CompletableFuture<String> execution = this.manager.executeToolCall(call.toolName(), call.arguments(), account)
                                .thenApply(result -> "Tool '" + call.toolName() + "' Result:\n" + result + "\n");
                        executionFutures.add(execution);
                    }

                    return CompletableFuture.allOf(executionFutures.toArray(new CompletableFuture[0]))
                            .thenCompose(v -> {
                                StringBuilder combinedResults = new StringBuilder();
                                for (CompletableFuture<String> future : executionFutures) {
                                    combinedResults.append(future.join());
                                }

                                // Generate the final summary using the tool results
                                return aiClient.generateFinalSummary(platform, modelId, account, prompt, combinedResults.toString());
                            });
                });
            });
        });
    }

    /* --- Data Retrieval API --- */

    public CompletableFuture<List<AIPlatform>> getPlatforms() {
        return this.database.getAllPlatforms();
    }

    public CompletableFuture<List<AIModel>> getModels(int platformId) {
        return this.database.getModelsByPlatform(platformId);
    }

    public CompletableFuture<Optional<String>> getToken(String accountType, String accountUuid) {
        String type = (accountType == null || accountType.isBlank()) ? DEFAULT_ACCOUNT_TYPE : accountType;
        return this.manager.fetchAccount(type, accountUuid)
                .thenApply(accountOpt -> accountOpt.map(AIAccount::token));
    }

    public CompletableFuture<AILog> saveLogs(String accountType, String accountUuid, int platformId, String modelId, String chatHistory, int tokenTotalUsage) {
        String type = (accountType == null || accountType.isBlank()) ? DEFAULT_ACCOUNT_TYPE : accountType;
        return this.database.insertLog(type, accountUuid, platformId, modelId, chatHistory, tokenTotalUsage);
    }

    /* --- Inner Interfaces and Records for AI Workflow --- */

    public record ToolCall(String toolName, Map<String, Object> arguments) {}

    public interface IAIWorkflowClient {
        CompletableFuture<List<ToolCall>> decideTools(AIPlatform platform, String modelId, AIAccount account, String prompt, List<AITool> tools);
        CompletableFuture<String> generateFinalSummary(AIPlatform platform, String modelId, AIAccount account, String originalPrompt, String toolResults);
    }
}
