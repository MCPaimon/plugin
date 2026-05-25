package io.github.mcpaimon.papermc.tools;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.model.AIPlatform;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.bukkit.event.ChangeTokenEvent;
import io.github.mcpaimon.common.MCAIManager;
import io.github.mcpaimon.papermc.MCAIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to change tokens.
 * Logic: Fully dynamic. It applies changes strictly to the AIAccount that initiated the workflow,
 * making it perfectly safe for players, consoles, clans, or other custom account types.
 */
public class ChangeTokenTool implements AITool {

    @Override
    public String getName() { return "change_token"; }

    @Override
    public String getDescription() { return "Changes the AI token for the current interacting account. Provide 'platformName' (e.g., openai) to change it for a specific platform. Requires 'newToken'."; }

    @Override
    public String getParametersJsonSchema() {
        return "{ \"type\": \"object\", \"properties\": { \"platformName\": { \"type\": \"string\", \"description\": \"The name of the platform\" }, \"newToken\": { \"type\": \"string\" } }, \"required\": [\"newToken\"] }";
    }

    @Override
    public List<String> getCategories() {
        return List.of("account_management");
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        String newToken = (String) arguments.get("newToken");

        if (newToken == null || newToken.isBlank()) {
            return CompletableFuture.completedFuture("Error: 'newToken' parameter is missing.");
        }

        String platformName = (String) arguments.get("platformName");

        ChangeTokenEvent event = new ChangeTokenEvent(account, platformName, newToken);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture("Error: Action blocked by server policy or another plugin.");
        }

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
                        .thenApply(updatedAccount -> "Success. The token for '" + platformName + "' has been updated for account type [" + account.accountType() + "].");
            });
        }

        return manager.setupAccount(account.accountType(), account.accountUuid(), account.platformId(), newToken)
                .thenApply(updatedAccount -> "Success. The token for the active session has been updated for account type [" + account.accountType() + "].");
    }
}
