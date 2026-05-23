package io.github.mcpaimon.api.tools;

import io.github.mcpaimon.api.model.AIAccount;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a tool or function that the AI can call.
 * Extensions can implement this interface to provide custom actions.
 */
public interface AITool {

    /**
     * Gets the unique name of the tool (e.g., "get_player_balance").
     * @return The tool name.
     */
    String getName();

    /**
     * Gets the description of what the tool does.
     * This is sent to the AI model so it knows when to use it.
     * @return The tool description.
     */
    String getDescription();

    /**
     * Gets the JSON schema representation of the parameters this tool requires.
     * @return A valid JSON schema string.
     */
    String getParametersJsonSchema();

    /**
     * Gets a list of category IDs this tool belongs to.
     * This supports multiple categories per tool.
     * @return A list of category strings.
     */
    List<String> getCategories();

    /**
     * Executes the tool with the provided arguments from the AI.
     * @param arguments The arguments parsed from the AI's function call.
     * @param account   The AI account initiating the request.
     * @return A CompletableFuture containing the string result to send back to the AI.
     */
    CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account);
}
