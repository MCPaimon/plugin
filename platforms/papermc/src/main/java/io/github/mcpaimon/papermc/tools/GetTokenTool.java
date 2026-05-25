package io.github.mcpaimon.papermc.tools;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.model.AIPlatform;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.bukkit.event.GetTokenEvent;
import io.github.mcpaimon.common.MCAIManager;
import io.github.mcpaimon.papermc.MCAIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to view tokens.
 * Logic: Fully dynamic. Safely retrieves the token exclusively for the AIAccount that initiated the workflow.
 */
public class GetTokenTool implements AITool {

    @Override
    public String getName() { return "get_token"; }

    @Override
    public String getDescription() { return "Retrieves the current interacting account's API token. Provide 'platformName' (e.g., openai, deepseek) to get the token for a specific platform."; }

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

        GetTokenEvent event = new GetTokenEvent(account, platformName);
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

                return manager.fetchAccount(account.accountType(), account.accountUuid(), pId)
                        .thenApply(targetAccountOpt -> {
                            if (targetAccountOpt.isEmpty() || targetAccountOpt.get().token().isBlank()) {
                                return "Notice: No API token registered for '" + platformName + "' on account type [" + account.accountType() + "].";
                            }
                            return "The current token for '" + platformName + "' on account type [" + account.accountType() + "] is: `" + targetAccountOpt.get().token() + "`";
                        });
            });
        }

        return CompletableFuture.completedFuture("The current token for the active session on account type [" + account.accountType() + "] is: `" + account.token() + "`");
    }
}
