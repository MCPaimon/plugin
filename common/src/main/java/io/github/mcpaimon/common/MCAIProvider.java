package io.github.mcpaimon.common;

import io.github.mcpaimon.api.database.IAIDatabase;
import io.github.mcpaimon.api.model.*;
import io.github.mcpaimon.api.tools.AITool;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MCAIProvider {
    private static final String DEFAULT_ACCOUNT_TYPE = "player";
    private final MCAIManager manager;
    private final IAIDatabase database;

    public MCAIProvider(MCAIManager manager, IAIDatabase database) {
        this.manager = manager;
        this.database = database;
    }

    public CompletableFuture<String> sendPrompt(String accountType, String accountUuid, int platformId, String modelId, String prompt, IAIWorkflowClient aiClient) {
        String type = (accountType == null || accountType.isBlank()) ? DEFAULT_ACCOUNT_TYPE : accountType;

        return this.manager.fetchAccount(type, accountUuid, platformId).thenCompose(accountOpt -> {
            if (accountOpt.isEmpty() || accountOpt.get().token().isBlank()) {
                return CompletableFuture.completedFuture("Error: No API token found. Please register your token first.");
            }
            AIAccount account = accountOpt.get();

            return this.database.getPlatform(platformId).thenCompose(platformOpt -> {
                if (platformOpt.isEmpty()) return CompletableFuture.completedFuture("Error: Platform not found.");
                
                AIPlatform platform = platformOpt.get();
                Map<String, String> allCategories = this.manager.getAllCategories();

                // Step 1: AI decides which categories to use based on the prompt
                return aiClient.decideCategories(platform, modelId, account, prompt, allCategories).thenCompose(selectedCategories -> {
                    List<AITool> filteredTools = new ArrayList<>();
                    
                    if (selectedCategories != null && !selectedCategories.isEmpty()) {
                        for (AITool tool : this.manager.getAllRegisteredTools()) {
                            boolean hasMatchingCategory = tool.getCategories().stream().anyMatch(selectedCategories::contains);
                            if (hasMatchingCategory) {
                                filteredTools.add(tool);
                            }
                        }
                    }

                    // Step 2: AI decides which specific tools to call within the filtered list
                    return aiClient.decideTools(platform, modelId, account, prompt, filteredTools).thenCompose(toolCalls -> {
                        if (toolCalls == null || toolCalls.isEmpty()) {
                            return aiClient.generateFinalSummary(platform, modelId, account, prompt, "No tools were used.");
                        }

                        // Execute the chosen tools
                        List<CompletableFuture<String>> executionFutures = new ArrayList<>();
                        for (ToolCall call : toolCalls) {
                            CompletableFuture<String> execution = this.manager.executeToolCall(call.toolName(), call.arguments(), account)
                                    .thenApply(result -> "Tool '" + call.toolName() + "' Result:\n" + result + "\n");
                            executionFutures.add(execution);
                        }

                        // Step 3: Send tool results back to AI for final summary
                        return CompletableFuture.allOf(executionFutures.toArray(new CompletableFuture[0])).thenCompose(v -> {
                            StringBuilder sb = new StringBuilder();
                            for (CompletableFuture<String> f : executionFutures) sb.append(f.join());
                            return aiClient.generateFinalSummary(platform, modelId, account, prompt, sb.toString());
                        });
                    });
                });
            });
        });
    }

    public CompletableFuture<List<AIPlatform>> getPlatforms() { return this.database.getAllPlatforms(); }
    public CompletableFuture<List<AIModel>> getModels(int platformId) { return this.database.getModelsByPlatform(platformId); }

    public record ToolCall(String toolName, Map<String, Object> arguments) {}

    public interface IAIWorkflowClient {
        CompletableFuture<List<String>> decideCategories(AIPlatform platform, String modelId, AIAccount account, String prompt, Map<String, String> categories);
        CompletableFuture<List<ToolCall>> decideTools(AIPlatform platform, String modelId, AIAccount account, String prompt, List<AITool> tools);
        CompletableFuture<String> generateFinalSummary(AIPlatform platform, String modelId, AIAccount account, String originalPrompt, String toolResults);
    }
}
