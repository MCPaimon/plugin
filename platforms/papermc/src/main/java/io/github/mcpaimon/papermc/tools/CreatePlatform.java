package io.github.mcpaimon.papermc.tools;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.common.MCAIManager;
import io.github.mcpaimon.papermc.MCAIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to create and register a new AI platform.
 * Logic: Requires OP status (if executed by player) to execute. Prevents creating duplicates.
 */
public class CreatePlatform implements AITool {

    @Override
    public String getName() {
        return "create_platform";
    }

    @Override
    public String getDescription() {
        return "Creates and registers a new AI platform. Requires OP status. Provide 'displayName' and 'url'.";
    }

    @Override
    public String getParametersJsonSchema() {
        return "{"
                + "  \"type\": \"object\","
                + "  \"properties\": {"
                + "    \"displayName\": { \"type\": \"string\", \"description\": \"The display name of the platform (e.g., openai, deepseek)\" },"
                + "    \"url\": { \"type\": \"string\", \"description\": \"The base API URL for the platform\" }"
                + "  },"
                + "  \"required\": [\"displayName\", \"url\"]"
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
                return CompletableFuture.completedFuture("Error: Access Denied. Only server operators (OP) can create new platforms.");
            }
        }

        String displayName = (String) arguments.get("displayName");
        String url = (String) arguments.get("url");

        if (displayName == null || displayName.isBlank() || url == null || url.isBlank()) {
            return CompletableFuture.completedFuture("Error: Both 'displayName' and 'url' must be provided and cannot be blank.");
        }

        MCAIManager manager = JavaPlugin.getPlugin(MCAIPlugin.class).getManager();

        return manager.getAllPlatforms().thenCompose(platforms -> {
            boolean exists = platforms.stream().anyMatch(p -> p.displayName().equalsIgnoreCase(displayName));
            if (exists) {
                return CompletableFuture.completedFuture("Error: Platform '" + displayName + "' already exists.");
            }

            return manager.registerPlatform(displayName, url)
                    .thenApply(platform -> "Success: Registered new platform '" + platform.displayName() + "' with ID: " + platform.id())
                    .exceptionally(throwable -> "Error: Failed to register platform. " + throwable.getMessage());
        });
    }
}
