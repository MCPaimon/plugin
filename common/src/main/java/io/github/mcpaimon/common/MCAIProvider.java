package io.github.mcpaimon.common;

import io.github.mcpaimon.api.database.IAIDatabase;
import io.github.mcpaimon.api.model.*;
import io.github.mcpaimon.api.provider.IAIAccountProvider;
import io.github.mcpaimon.api.tools.AITool;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Provides the main workflow integration for the AI system, handling prompt sending and tool execution loops.
 */
public class MCAIProvider {
    /**
     * The default account type used if none is specified.
     */
    private static final String DEFAULT_ACCOUNT_TYPE = "player";

    /**
     * The maximum number of tool execution iterations allowed per prompt to prevent infinite loops.
     */
    private final int maxWorkflowIterations;
    
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
     * @param manager               The manager handling core AI features.
     * @param database              The database implementation.
     * @param maxWorkflowIterations The maximum number of tool iterations to prevent infinite loops.
     */
    public MCAIProvider(MCAIManager manager, IAIDatabase database, int maxWorkflowIterations) {
        this.manager = manager;
        this.database = database;
        this.maxWorkflowIterations = maxWorkflowIterations;
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
        return sendPrompt(provider.getAccountType(), provider.getAccountUuid(), platformId, modelId, prompt, aiClient, null);
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
        return sendPrompt(accountType, accountUuid, platformId, modelId, prompt, aiClient, null);
    }

    /**
     * Sends a prompt to the AI, processes any tool calls required, and returns the final response.
     * Allows an interceptor to modify or append context to the tool results before final summary.
     *
     * @param accountType           The type of account.
     * @param accountUuid           The UUID of the account.
     * @param platformId            The ID of the AI platform.
     * @param modelId               The ID of the AI model.
     * @param prompt                The user's input prompt.
     * @param aiClient              The client used to communicate with the AI API.
     * @param preSummaryInterceptor A function to intercept and modify tool results before sending to the AI.
     * @return A CompletableFuture containing the final AIResponse.
     */
    public CompletableFuture<AIResponse> sendPrompt(String accountType, String accountUuid, int platformId, String modelId, String prompt, IAIWorkflowClient aiClient, BiFunction<AIAccount, String, String> preSummaryInterceptor) {
        String type = (accountType == null || accountType.isBlank()) ? DEFAULT_ACCOUNT_TYPE : accountType;

        return this.manager.fetchAccount(type, accountUuid, platformId).thenCompose(accountOpt -> {
            if (accountOpt.isEmpty() || accountOpt.get().token().isBlank()) {
                return CompletableFuture.completedFuture(new AIResponse("Error: No API token found. Please register your token first.", 0, 0, 0));
            }
            AIAccount account = accountOpt.get();

            return this.database.getPlatform(platformId).thenCompose(platformOpt -> {
                if (platformOpt.isEmpty()) return CompletableFuture.completedFuture(new AIResponse("Error: Platform not found.", 0, 0, 0));
                
                AIPlatform platform = platformOpt.get();
                
                // Start the multi-step execution loop
                return executeWorkflowLoop(platform, modelId, account, prompt, "", 0, aiClient, preSummaryInterceptor, 0, 0, 0);
            });
        });
    }

    /**
     * Recursively handles the multi-step workflow allowing the AI to autonomously decide when to stop calling tools.
     */
    private CompletableFuture<AIResponse> executeWorkflowLoop(
            AIPlatform platform, String modelId, AIAccount account, String originalPrompt, 
            String accumulatedToolResults, int currentIteration, IAIWorkflowClient aiClient, 
            BiFunction<AIAccount, String, String> preSummaryInterceptor, 
            int accumulatedPromptTokens, int accumulatedCompletionTokens, int accumulatedTotalTokens) {

        // Force termination if the iteration limit is reached to prevent infinite loops
        if (currentIteration >= this.maxWorkflowIterations) {
            return finalizeWorkflow(platform, modelId, account, originalPrompt, accumulatedToolResults, aiClient, preSummaryInterceptor, accumulatedPromptTokens, accumulatedCompletionTokens, accumulatedTotalTokens);
        }

        // Provide context to the AI so it knows what has already been executed
        String decisionPrompt = originalPrompt;
        if (!accumulatedToolResults.isEmpty()) {
            decisionPrompt = originalPrompt + "\n\n--- Context from previous tool actions ---\n" + accumulatedToolResults + "\n-----------------------------------\nBased on the results above, do you need to execute more tools to fully answer the original prompt? If the information is sufficient, return an empty tool call list.";
        }
        
        final String finalDecisionPrompt = decisionPrompt;
        
        // Step 1: AI decides which categories to use based on current context
        return aiClient.decideCategories(platform, modelId, account, finalDecisionPrompt, this.manager.getAllCategories()).thenCompose(categoryResult -> {
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

            // Step 2: AI decides which specific tools to call
            return aiClient.decideTools(platform, modelId, account, finalDecisionPrompt, filteredTools).thenCompose(toolResult -> {
                List<ToolCall> toolCalls = toolResult.data();
                
                int newPromptTokens = accumulatedPromptTokens + categoryResult.promptTokens() + toolResult.promptTokens();
                int newCompletionTokens = accumulatedCompletionTokens + categoryResult.completionTokens() + toolResult.completionTokens();
                int newTotalTokens = accumulatedTotalTokens + categoryResult.totalTokens() + toolResult.totalTokens();
                
                // Trigger condition: AI decides to stop by not calling any tools
                if (toolCalls == null || toolCalls.isEmpty()) {
                    String finalResults = accumulatedToolResults.isEmpty() ? "No tools were used." : accumulatedToolResults;
                    return finalizeWorkflow(platform, modelId, account, originalPrompt, finalResults, aiClient, preSummaryInterceptor, newPromptTokens, newCompletionTokens, newTotalTokens);
                }

                // Execute the chosen tools
                List<CompletableFuture<String>> executionFutures = new ArrayList<>();
                for (ToolCall call : toolCalls) {
                    CompletableFuture<String> execution = this.manager.executeToolCall(call.toolName(), call.arguments(), account)
                            .thenApply(result -> "Step " + (currentIteration + 1) + " - Tool '" + call.toolName() + "' Result:\n" + result + "\n");
                    executionFutures.add(execution);
                }

                // Step 3: Wait for all tools to finish, accumulate results, and loop
                return CompletableFuture.allOf(executionFutures.toArray(new CompletableFuture[0])).thenCompose(v -> {
                    StringBuilder newResults = new StringBuilder(accumulatedToolResults);
                    for (CompletableFuture<String> f : executionFutures) {
                        newResults.append(f.join());
                    }
                    
                    // Recursive call for the next iteration
                    return executeWorkflowLoop(platform, modelId, account, originalPrompt, newResults.toString(), currentIteration + 1, aiClient, preSummaryInterceptor, newPromptTokens, newCompletionTokens, newTotalTokens);
                });
            });
        });
    }

    /**
     * Handles the final summary generation process once the workflow loop completes.
     */
    private CompletableFuture<AIResponse> finalizeWorkflow(
            AIPlatform platform, String modelId, AIAccount account, String originalPrompt, 
            String toolResults, IAIWorkflowClient aiClient, BiFunction<AIAccount, String, String> preSummaryInterceptor,
            int promptTokens, int completionTokens, int totalTokens) {
        
        String finalToolResults = toolResults;
        if (preSummaryInterceptor != null) {
            finalToolResults = preSummaryInterceptor.apply(account, finalToolResults);
        }
        
        return aiClient.generateFinalSummary(platform, modelId, account, originalPrompt, finalToolResults).thenApply(finalResponse -> 
            new AIResponse(
                finalResponse.content(),
                promptTokens + finalResponse.promptTokens(),
                completionTokens + finalResponse.completionTokens(),
                totalTokens + finalResponse.totalTokens()
            )
        );
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
