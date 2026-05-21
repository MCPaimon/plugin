package io.github.mcpaimon.common.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.model.AIPlatform;
import io.github.mcpaimon.api.tool.AITool;
import io.github.mcpaimon.common.MCAIProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    @Override
    public CompletableFuture<List<MCAIProvider.ToolCall>> decideTools(AIPlatform platform, String modelId, AIAccount account, String prompt, List<AITool> tools) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", modelId);

                JsonArray messages = new JsonArray();
                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", prompt);
                messages.add(userMsg);
                requestBody.add("messages", messages);

                // Inject tools into the request if available
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
                    requestBody.addProperty("tool_choice", "auto"); // Let AI decide
                }

                // Use dynamic URL from platform.url()
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(platform.url()))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + account.token())
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

                List<MCAIProvider.ToolCall> toolCallsList = new ArrayList<>();
                
                // Parse AI response for tool calls
                if (jsonResponse.has("choices")) {
                    JsonObject messageObj = jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
                    if (messageObj.has("tool_calls")) {
                        JsonArray toolCalls = messageObj.getAsJsonArray("tool_calls");
                        for (JsonElement callElement : toolCalls) {
                            JsonObject functionCall = callElement.getAsJsonObject().getAsJsonObject("function");
                            String name = functionCall.get("name").getAsString();
                            String argumentsStr = functionCall.get("arguments").getAsString();
                            
                            Map<String, Object> args = gson.fromJson(argumentsStr, new TypeToken<Map<String, Object>>(){}.getType());
                            toolCallsList.add(new MCAIProvider.ToolCall(name, args));
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

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(platform.url()))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + account.token())
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();

                if (jsonResponse.has("choices")) {
                    return jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
                }
                
                if (jsonResponse.has("error")) {
                    return "API Error: " + jsonResponse.getAsJsonObject("error").get("message").getAsString();
                }
                
                return "Error parsing AI response.";
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate final summary: " + e.getMessage(), e);
            }
        });
    }
}
