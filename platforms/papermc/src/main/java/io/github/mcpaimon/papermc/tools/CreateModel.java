package io.github.mcpaimon.papermc.tools;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.model.AIPlatform;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.common.MCAIManager;
import io.github.mcpaimon.papermc.MCAIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to create and register a new AI model for a specific platform.
 * Logic: Requires OP status (if executed by player) to execute. Prevents creating duplicate models on the same platform.
 */
public class CreateModel implements AITool {

    @Override
    public String getName() {
        return "create_model";
    }

    @Override
    public String getDescription() {
        return "Registers a model name into the local server database for a specific platform. This tool DOES NOT train or create actual AI models externally; it only saves the string identifier (e.g., 'gpt-4o') locally for the Minecraft plugin to use. Requires OP status. Provide 'platformName' and 'modelId'.";
    }

    @Override
    public String getParametersJsonSchema() {
        return "{"
                + "  \"type\": \"object\","
                + "  \"properties\": {"
                + "    \"platformName\": { \"type\": \"string\", \"description\": \"The display name of the existing platform\" },"
                + "    \"modelId\": { \"type\": \"string\", \"description\": \"The model identifier string to save locally (e.g., gpt-4o, deepseek-chat)\" }"
                + "  },"
                + "  \"required\": [\"platformName\", \"modelId\"]"
                + "}";
    }

    @Override
    public List<String> getCategories() {
        return List.of("system_management", "admin");
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        boolean isConsole = account.accountType().equalsIgnoreCase("console");

        if (!isConsole) {
            Player sender = Bukkit.getPlayer(UUID.fromString(account.accountUuid()));
            if (sender == null) {
                return CompletableFuture.completedFuture("Error: Cannot find sender in game.");
            }

            // Permission Check: Strictly OP only for players
            if (!sender.isOp()) {
                return CompletableFuture.completedFuture("Error: Access Denied. Only server operators (OP) can register new models.");
            }
        }

        String platformName = (String) arguments.get("platformName");
        String modelId = (String) arguments.get("modelId");

        if (platformName == null || platformName.isBlank() || modelId == null || modelId.isBlank()) {
            return CompletableFuture.completedFuture("Error: Both 'platformName' and 'modelId' must be provided.");
        }

        MCAIPlugin plugin = JavaPlugin.getPlugin(MCAIPlugin.class);
        MCAIManager manager = plugin.getManager();

        return manager.getAllPlatforms().thenCompose(platforms -> {
            Optional<AIPlatform> targetOpt = platforms.stream()
                    .filter(p -> p.displayName().equalsIgnoreCase(platformName))
                    .findFirst();

            if (targetOpt.isEmpty()) {
                return CompletableFuture.completedFuture("Error: Unknown platform '" + platformName + "'. Create the platform first.");
            }

            AIPlatform platform = targetOpt.get();

            // Check if model already exists on this platform
            return plugin.getProvider().getModels(platform.id()).thenCompose(existingModels -> {
                boolean modelExists = existingModels.stream()
                        .anyMatch(m -> m.modelId().equalsIgnoreCase(modelId));

                if (modelExists) {
                    return CompletableFuture.completedFuture("Error: Model '" + modelId + "' already exists under platform '" + platformName + "'.");
                }

                // If not exists, proceed to register
                return manager.registerModel(platform.id(), modelId)
                        .thenApply(model -> "Success: Registered model '" + model.modelId() + "' under platform '" + platform.displayName() + "'.")
                        .exceptionally(throwable -> "Error: Failed to register model. " + throwable.getMessage());
            });
        });
    }
}
