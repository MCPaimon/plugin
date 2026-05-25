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
 * Tool to change tokens.
 * Logic: Can ONLY change own token (or console can change console's). Requires manager access to update DB.
 * Supports updating tokens for specific platforms by name.
 */
public class ChangeTokenTool implements AITool {

    @Override
    public String getName() { return "change_token"; }

    @Override
    public String getDescription() { return "Changes the AI token for the account. Provide 'platformName' (e.g., openai) to change it for a specific platform. Requires 'newToken'."; }

    @Override
    public String getParametersJsonSchema() {
        return "{ \"type\": \"object\", \"properties\": { \"targetName\": { \"type\": \"string\" }, \"platformName\": { \"type\": \"string\", \"description\": \"The name of the platform\" }, \"newToken\": { \"type\": \"string\" } }, \"required\": [\"newToken\"] }";
    }

    @Override
    public List<String> getCategories() {
        return List.of("account_management");
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        boolean isConsole = account.accountType().equalsIgnoreCase("console");
        String senderName = "CONSOLE";
        
        if (!isConsole) {
            Player sender = Bukkit.getPlayer(UUID.fromString(account.accountUuid()));
            if (sender == null) return CompletableFuture.completedFuture("Error: Cannot find sender in game.");
            senderName = sender.getName();
        }

        String targetName = arguments.containsKey("targetName") ? (String) arguments.get("targetName") : senderName;
        String newToken = (String) arguments.get("newToken");

        if (newToken == null || newToken.isBlank()) {
            return CompletableFuture.completedFuture("Error: 'newToken' parameter is missing.");
        }

        // Permission Check: STRICTLY self only (console counts as itself)
        if (!isConsole && !senderName.equalsIgnoreCase(targetName)) {
            return CompletableFuture.completedFuture("Error: Access Denied. You cannot change another player's token.");
        }

        String platformName = (String) arguments.get("platformName");
        MCAIManager manager = JavaPlugin.getPlugin(MCAIPlugin.class).getManager();

        if (platformName != null && !platformName.isBlank()) {
            return manager.getAllPlatforms().thenCompose(platforms -> {
                Optional<AIPlatform> targetOpt = platforms.stream()
                        .filter(p -> p.displayName().equalsIgnoreCase(platformName))
                        .findFirst();

                if (targetOpt.isEmpty()) {
                    return CompletableFuture.completedFuture("Error: Unknown platform '" + platformName + "'.");
                }

                int pId = targetOpt.get().id();

                return manager.setupAccount(account.accountType(), account.accountUuid(), pId, newToken)
                        .thenApply(updatedAccount -> "Success. Your token for " + platformName + " has been updated.");
            });
        }

        return manager.setupAccount(account.accountType(), account.accountUuid(), account.platformId(), newToken)
                .thenApply(updatedAccount -> "Success. Your token for the active session has been updated.");
    }
}
