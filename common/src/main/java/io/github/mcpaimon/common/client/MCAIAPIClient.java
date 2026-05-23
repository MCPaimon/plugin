package io.github.mcpaimon.common.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.model.AIPlatform;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.common.MCAIProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Universal Pure Java HTTP Client.
 * Uses the OpenAI standard JSON schema but fetches the API URL dynamically from the AIPlatform database.
 */
public class MCAIAPIClient implements MCAIProvider.IAIWorkflowClient {

    private final HttpClient httpClient;
    private final Gson gson;

    public MCAIAPIClient() {
        this.httpClient = HttpClient.newBuilder().build();
        this.gson = new Gson();
    }

    /**
     * Helper method to ensure the URL has the correct endpoint path for OpenAI-compatible APIs.
     */
    private String formatUrl(String url) {
        if (url == null || url.isBlank()) return url;
        if (!url.endsWith("/chat/completions") && !url.endsWith("/v1/completions")) {
            if (url.endsWith("/")) {
                return url + "chat/completions";
            } else {
                return url + "/chat/completions";
            }
        }
        return url;
    }

    @Override
    public CompletableFuture<List<String>> decideCategories(AIPlatform platform, String modelId, AIAccount account, String prompt, Map<String, String> categories) {
        if (categories == null || categories.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", modelId);

                JsonArray messages = new JsonArray();
                JsonObject systemMsg = new JsonObject();
                systemMsg.addProperty("role", "system");
                systemMsg.addProperty("content", "You are an AI assistant. Your task is to review the user prompt and the available tool categories, then select the relevant category IDs to handle the user's request. You MUST use the 'select_categories' tool.");
                messages.add(systemMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                StringBuilder contentBuilder = new StringBuilder("Available Categories:\n");
                categories.forEach((id, desc) -> contentBuilder.append("- ").append(id).append(": ").append(desc).append("\n"));
                contentBuilder.append("\nUser Prompt: ").append(prompt);
                userMsg.addProperty("content", contentBuilder.toString());
                messages.add(userMsg);

                requestBody.add("messages", messages);

                // Inject a forced tool call to guarantee strict JSON array output
                JsonArray toolsArray = new JsonArray();
                JsonObject toolObj = new JsonObject();
                toolObj.addProperty("type", "function");
                
                JsonObject functionObj = new JsonObject();
                functionObj.addProperty("name", "select_categories");
                functionObj.addProperty("description", "Selects the appropriate categories needed.");
                functionObj.add("parameters", JsonParser.parseString("{ \"type\": \"object\", \"properties\": { \"categories\": { \"type\": \"array\", \"items\": { \"type\": \"string\" } } }, \"required\": [\"categories\"] }"));
                toolObj.add("function", functionObj);
                toolsArray.add(toolObj);

                requestBody.add("tools", toolsArray);

                // Force the AI to use the specific tool
                JsonObject toolChoice = new JsonObject();
                toolChoice.addProperty("type", "function");
                JsonObject toolChoiceFunc = new JsonObject();
                toolChoiceFunc.addProperty("name", "select_categories");
                toolChoice.add("function", toolChoiceFunc);
                requestBody.add("tool_choice", toolChoice);

                String targetUrl = formatUrl(platform.url());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + account.token())
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    throw new RuntimeException("HTTP Error " + response.statusCode() + " | Raw Response: " + response.body());
                }

                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                List<String> selectedCategories = new ArrayList<>();

                if (jsonResponse.has("choices")) {
                    JsonArray choices = jsonResponse.getAsJsonArray("choices");
                    if (!choices.isEmpty() && choices.get(0).isJsonObject()) {
                        JsonObject messageObj = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                        if (messageObj != null && messageObj.has("tool_calls") && !messageObj.get("tool_calls").isJsonNull()) {
                            JsonArray toolCalls = messageObj.getAsJsonArray("tool_calls");
                            for (JsonElement callElement : toolCalls) {
                                JsonObject functionCall = callElement.getAsJsonObject().getAsJsonObject("function");
                                if ("select_categories".equals(functionCall.get("name").getAsString())) {
                                    String argumentsStr = functionCall.get("arguments").getAsString();
                                    JsonObject argsObj = JsonParser.parseString(argumentsStr).getAsJsonObject();
                                    if (argsObj.has("categories") && argsObj.get("categories").isJsonArray()) {
                                        for (JsonElement catElem : argsObj.getAsJsonArray("categories")) {
                                            selectedCategories.add(catElem.getAsString());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                return selectedCategories;
            } catch (Exception e) {
                throw new RuntimeException("Failed to call AI to decide categories: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<List<MCAIProvider.ToolCall>> decideTools(AIPlatform platform, String modelId, AIAccount account, String prompt, List<AITool> tools) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", modelId);

                JsonArray messages = new JsonArray();
                
                JsonObject systemMsg = new JsonObject();
                systemMsg.addProperty("role", "system");
                systemMsg.addProperty("content", "You are a Minecraft AI assistant. CRITICAL INSTRUCTION: If the user asks for multiple distinct pieces of information (e.g., name AND UUID, or date AND time), you MUST invoke ALL necessary tools simultaneously in parallel. Do not wait for one tool's response to call another.");
                messages.add(systemMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", prompt);
                messages.add(userMsg);
                requestBody.add("messages", messages);

                if (tools != null && !tools.isEmpty()) {
                    JsonArray toolsArray = new JsonArray();
                    for (AITool tool : tools) {
                        JsonObject toolObj = new JsonObject();
                        toolObj.addProperty("type", "function");
                        
                        JsonObject functionObj = new JsonObject();
                        functionObj.addProperty("name", tool.getName());
                        functionObj.addProperty("description", tool.getDescription());
                        functionObj.add("parameters", JsonParser.parseString(tool.getParametersJsonSchema()));
                        
                        toolObj.add("function", functionObj);
                        toolsArray.add(toolObj);
                    }
                    requestBody.add("tools", toolsArray);
                    requestBody.addProperty("tool_choice", "auto");
                }

                String targetUrl = formatUrl(platform.url());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + account.token())
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    throw new RuntimeException("HTTP Error " + response.statusCode() + " | Raw Response: " + response.body());
                }

                JsonElement parsedElement = JsonParser.parseString(response.body());
                if (!parsedElement.isJsonObject()) {
                    throw new RuntimeException("Invalid response format (not JSON object) | Raw Response: " + response.body());
                }

                JsonObject jsonResponse = parsedElement.getAsJsonObject();
                List<MCAIProvider.ToolCall> toolCallsList = new ArrayList<>();
                
                if (jsonResponse.has("choices")) {
                    JsonArray choices = jsonResponse.getAsJsonArray("choices");
                    if (!choices.isEmpty() && choices.get(0).isJsonObject()) {
                        JsonObject messageObj = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                        
                        if (messageObj != null && messageObj.has("tool_calls") && !messageObj.get("tool_calls").isJsonNull()) {
                            JsonArray toolCalls = messageObj.getAsJsonArray("tool_calls");
                            for (JsonElement callElement : toolCalls) {
                                JsonObject functionCall = callElement.getAsJsonObject().getAsJsonObject("function");
                                String name = functionCall.get("name").getAsString();
                                String argumentsStr = functionCall.get("arguments").getAsString();
                                
                                if (argumentsStr == null || argumentsStr.isBlank()) {
                                    argumentsStr = "{}";
                                }
                                
                                Map<String, Object> args = gson.fromJson(argumentsStr, new TypeToken<Map<String, Object>>(){}.getType());
                                toolCallsList.add(new MCAIProvider.ToolCall(name, args));
                            }
                        }
                    }
                }
                return toolCallsList;
            } catch (Exception e) {
                throw new RuntimeException("Failed to call AI to decide tools: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<String> generateFinalSummary(AIPlatform platform, String modelId, AIAccount account, String originalPrompt, String toolResults) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", modelId);

                JsonArray messages = new JsonArray();
                
                JsonObject systemMsg = new JsonObject();
                systemMsg.addProperty("role", "system");
                systemMsg.addProperty("content", "You are a helpful Minecraft AI assistant. Answer the user based on the tool results provided. Keep it concise and natural.\n\n[System Tool Results]:\n" + toolResults);
                messages.add(systemMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", originalPrompt);
                messages.add(userMsg);

                requestBody.add("messages", messages);

                String targetUrl = formatUrl(platform.url());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + account.token())
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    return "API Error (HTTP " + response.statusCode() + "): " + response.body();
                }

                JsonElement parsedElement = JsonParser.parseString(response.body());
                if (!parsedElement.isJsonObject()) {
                    return "Error parsing AI response. Not a JSON object. Raw Response: " + response.body();
                }

                JsonObject jsonResponse = parsedElement.getAsJsonObject();

                if (jsonResponse.has("choices")) {
                    JsonArray choices = jsonResponse.getAsJsonArray("choices");
                    if (!choices.isEmpty() && choices.get(0).isJsonObject()) {
                        JsonObject messageObj = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                        if (messageObj != null && messageObj.has("content") && !messageObj.get("content").isJsonNull()) {
                            return messageObj.get("content").getAsString();
                        }
                    }
                }
                
                if (jsonResponse.has("error")) {
                    return "API Error: " + jsonResponse.getAsJsonObject("error").get("message").getAsString();
                }
                
                return "Error: Unexpected response format from AI. Raw: " + response.body();
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate final summary: " + e.getMessage(), e);
            }
        });
    }
}
