package io.github.mcpaimon.papermc.tools;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.model.AIPlatform;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.bukkit.event.SetTokenEvent;
import io.github.mcpaimon.common.MCAIManager;
import io.github.mcpaimon.papermc.MCAIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to set tokens.
 * Logic: Fully dynamic. Safely sets the token exclusively for the target AIAccount.
 */
public class SetTokenTool implements AITool {

    @Override
    public String getName() { return "set_token"; }

    @Override
    public String getDescription() { return "Sets the AI token for the account. Provide 'platformName' (e.g., openai) to set it for a specific platform. Can optionally specify 'targetAccountType' and 'targetAccountUuid' to set for another entity like a clan."; }

    @Override
    public String getParametersJsonSchema() {
        return "{"
                + "  \"type\": \"object\","
                + "  \"properties\": {"
                + "    \"platformName\": { \"type\": \"string\", \"description\": \"The name of the platform\" },"
                + "    \"token\": { \"type\": \"string\" },"
                + "    \"targetAccountType\": { \"type\": \"string\", \"description\": \"Optional. The account type to modify (e.g., player, console, clan). Defaults to current account if empty.\" },"
                + "    \"targetAccountUuid\": { \"type\": \"string\", \"description\": \"Optional. The UUID of the target account. Defaults to current account if empty.\" }"
                + "  },"
                + "  \"required\": [\"token\"]"
                + "}";
    }

    @Override
    public List<String> getCategories() {
        return List.of("account_management");
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        String token = (String) arguments.get("token");

        if (token == null || token.isBlank()) {
            return CompletableFuture.completedFuture("Error: 'token' parameter is missing.");
        }

        String platformName = (String) arguments.get("platformName");
        String targetType = arguments.containsKey("targetAccountType") && arguments.get("targetAccountType") != null ? (String) arguments.get("targetAccountType") : account.accountType();
        String targetUuid = arguments.containsKey("targetAccountUuid") && arguments.get("targetAccountUuid") != null ? (String) arguments.get("targetAccountUuid") : account.accountUuid();

        SetTokenEvent event = new SetTokenEvent(account, targetType, targetUuid, platformName, token);
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

                return manager.setupAccount(targetType, targetUuid, pId, token)
                        .thenApply(updatedAccount -> "Success. The token for '" + platformName + "' has been set for account type [" + targetType + "].");
            });
        }

        return manager.setupAccount(targetType, targetUuid, account.platformId(), token)
                .thenApply(updatedAccount -> "Success. The token for the active session has been set for account type [" + targetType + "].");
    }
}
