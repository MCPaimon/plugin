package io.github.mcpaimon.papermc.tools;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.model.AIPlatform;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.bukkit.event.DeleteTokenEvent;
import io.github.mcpaimon.common.MCAIManager;
import io.github.mcpaimon.papermc.MCAIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to delete tokens (sets to empty string).
 * Logic: Fully dynamic. Safely deletes the token exclusively for the AIAccount that initiated the workflow.
 */
public class DeleteTokenTool implements AITool {

    @Override
    public String getName() { return "delete_token"; }

    @Override
    public String getDescription() { return "Deletes (resets) the AI token for the current interacting account. Provide 'platformName' (e.g., openai) to delete it for a specific platform."; }

    @Override
    public String getParametersJsonSchema() {
        return "{ \"type\": \"object\", \"properties\": { \"platformName\": { \"type\": \"string\", \"description\": \"The name of the platform\" } } }";
    }

    @Override
    public List<String> getCategories() {
        return List.of("account_management");
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        String platformName = (String) arguments.get("platformName");

        DeleteTokenEvent event = new DeleteTokenEvent(account, platformName);
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

                return manager.setupAccount(account.accountType(), account.accountUuid(), pId, "")
                        .thenApply(updatedAccount -> "Success. The token for '" + platformName + "' has been deleted for account type [" + account.accountType() + "].");
            });
        }

        // Reset token to empty string to effectively "delete" it
        return manager.setupAccount(account.accountType(), account.accountUuid(), account.platformId(), "")
                .thenApply(updatedAccount -> "Success. The token for the active session has been deleted for account type [" + account.accountType() + "].");
    }
}
