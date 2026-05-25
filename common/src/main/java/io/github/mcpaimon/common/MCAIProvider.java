package io.github.mcpaimon.common;

import io.github.mcpaimon.api.database.IAIDatabase;
import io.github.mcpaimon.api.model.*;
import io.github.mcpaimon.api.provider.IAIAccountProvider;
import io.github.mcpaimon.api.tools.AITool;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Provides the main workflow integration for the AI system, handling prompt sending and tool execution loops.
 */
public class MCAIProvider {
    /**
     * The default account type used if none is specified.
     */
    private static final String DEFAULT_ACCOUNT_TYPE = "player";
    
    /**
     * The central manager for accounts, tools, and sessions.
     */
    private final MCAIManager manager;
    
    /**
     * The database interface for data retrieval and storage.
     */
    private final IAIDatabase database;

    /**
     * Constructs a new MCAIProvider instance.
     *
     * @param manager  The manager handling core AI features.
     * @param database The database implementation.
     */
    public MCAIProvider(MCAIManager manager, IAIDatabase database) {
        this.manager = manager;
        this.database = database;
    }

    /**
     * Sends a prompt to the AI using a dynamic account provider.
     * This is highly recommended for cross-plugin compatibility (e.g., Clan plugins, Discord bots).
     *
     * @param provider   The dynamic account provider instance.
     * @param platformId The ID of the AI platform.
     * @param modelId    The ID of the AI model.
     * @param prompt     The user's input prompt.
     * @param aiClient   The client used to communicate with the AI API.
     * @return A CompletableFuture containing the final AIResponse.
     */
    public CompletableFuture<AIResponse> sendPrompt(IAIAccountProvider provider, int platformId, String modelId, String prompt, IAIWorkflowClient aiClient) {
        return sendPrompt(provider.getAccountType(), provider.getAccountUuid(), platformId, modelId, prompt, aiClient);
    }

    /**
     * Sends a prompt to the AI, processes any tool calls required, and returns the final response.
     *
     * @param accountType The type of account.
     * @param accountUuid The UUID of the account.
     * @param platformId  The ID of the AI platform.
     * @param modelId     The ID of the AI model.
     * @param prompt      The user's input prompt.
     * @param aiClient    The client used to communicate with the AI API.
     * @return A CompletableFuture containing the final AIResponse.
     */
    public CompletableFuture<AIResponse> sendPrompt(String accountType, String accountUuid, int platformId, String modelId, String prompt, IAIWorkflowClient aiClient) {
        String type = (accountType == null || accountType.isBlank()) ? DEFAULT_ACCOUNT_TYPE : accountType;

        return this.manager.fetchAccount(type, accountUuid, platformId).thenCompose(accountOpt -> {
            if (accountOpt.isEmpty() || accountOpt.get().token().isBlank()) {
                return CompletableFuture.completedFuture(new AIResponse("Error: No API token found. Please register your token first.", 0, 0, 0));
            }
            AIAccount account = accountOpt.get();

            return this.database.getPlatform(platformId).thenCompose(platformOpt -> {
                if (platformOpt.isEmpty()) return CompletableFuture.completedFuture(new AIResponse("Error: Platform not found.", 0, 0, 0));
                
                AIPlatform platform = platformOpt.get();
                Map<String, String> allCategories = this.manager.getAllCategories();

                // Step 1: AI decides which categories to use based on the prompt
                return aiClient.decideCategories(platform, modelId, account, prompt, allCategories).thenCompose(categoryResult -> {
                    List<AITool> filteredTools = new ArrayList<>();
                    List<String> selectedCategories = categoryResult.data();
                    
                    if (selectedCategories != null && !selectedCategories.isEmpty()) {
                        for (AITool tool : this.manager.getAllRegisteredTools()) {
                            boolean hasMatchingCategory = tool.getCategories().stream().anyMatch(selectedCategories::contains);
                            if (hasMatchingCategory) {
                                filteredTools.add(tool);
                            }
                        }
                    }

                    // Step 2: AI decides which specific tools to call within the filtered list
                    return aiClient.decideTools(platform, modelId, account, prompt, filteredTools).thenCompose(toolResult -> {
                        List<ToolCall> toolCalls = toolResult.data();
                        
                        if (toolCalls == null || toolCalls.isEmpty()) {
                            return aiClient.generateFinalSummary(platform, modelId, account, prompt, "No tools were used.").thenApply(finalResponse -> 
                                new AIResponse(
                                    finalResponse.content(),
                                    categoryResult.promptTokens() + toolResult.promptTokens() + finalResponse.promptTokens(),
                                    categoryResult.completionTokens() + toolResult.completionTokens() + finalResponse.completionTokens(),
                                    categoryResult.totalTokens() + toolResult.totalTokens() + finalResponse.totalTokens()
                                )
                            );
                        }

                        // Execute the chosen tools
                        List<CompletableFuture<String>> executionFutures = new ArrayList<>();
                        for (ToolCall call : toolCalls) {
                            CompletableFuture<String> execution = this.manager.executeToolCall(call.toolName(), call.arguments(), account)
                                    .thenApply(result -> "Tool '" + call.toolName() + "' Result:\n" + result + "\n");
                            executionFutures.add(execution);
                        }

                        // Step 3: Send tool results back to AI for final summary and accumulate all tokens
                        return CompletableFuture.allOf(executionFutures.toArray(new CompletableFuture[0])).thenCompose(v -> {
                            StringBuilder sb = new StringBuilder();
                            for (CompletableFuture<String> f : executionFutures) sb.append(f.join());
                            return aiClient.generateFinalSummary(platform, modelId, account, prompt, sb.toString()).thenApply(finalResponse -> 
                                new AIResponse(
                                    finalResponse.content(),
                                    categoryResult.promptTokens() + toolResult.promptTokens() + finalResponse.promptTokens(),
                                    categoryResult.completionTokens() + toolResult.completionTokens() + finalResponse.completionTokens(),
                                    categoryResult.totalTokens() + toolResult.totalTokens() + finalResponse.totalTokens()
                                )
                            );
                        });
                    });
                });
            });
        });
    }

    /**
     * Retrieves all available AI platforms.
     *
     * @return A CompletableFuture containing a list of platforms.
     */
    public CompletableFuture<List<AIPlatform>> getPlatforms() { return this.database.getAllPlatforms(); }

    /**
     * Retrieves all available models for a given platform.
     *
     * @param platformId The ID of the platform.
     * @return A CompletableFuture containing a list of models.
     */
    public CompletableFuture<List<AIModel>> getModels(int platformId) { return this.database.getModelsByPlatform(platformId); }

    /**
     * Shuts down the database connection securely.
     *
     * @return A CompletableFuture representing the shutdown task.
     */
    public CompletableFuture<Void> shutdown() { 
        return this.database.close(); 
    }

    /**
     * Record representing a tool call decision from the AI.
     */
    public record ToolCall(String toolName, Map<String, Object> arguments) {}
    
    /**
     * Record representing the final response text and token usages from the AI interaction.
     */
    public record AIResponse(String content, int promptTokens, int completionTokens, int totalTokens) {}

    /**
     * Record representing the result of an AI workflow step, generic over its data type.
     */
    public record AIWorkflowResult<T>(T data, int promptTokens, int completionTokens, int totalTokens) {}

    /**
     * Client interface for managing the AI workflow calls and generating summaries.
     */
    public interface IAIWorkflowClient {
        CompletableFuture<AIWorkflowResult<List<String>>> decideCategories(AIPlatform platform, String modelId, AIAccount account, String prompt, Map<String, String> categories);
        CompletableFuture<AIWorkflowResult<List<ToolCall>>> decideTools(AIPlatform platform, String modelId, AIAccount account, String prompt, List<AITool> tools);
        CompletableFuture<AIResponse> generateFinalSummary(AIPlatform platform, String modelId, AIAccount account, String originalPrompt, String toolResults);
    }
}
