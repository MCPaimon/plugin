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
 * Logic: Fully dynamic. It applies changes strictly to the target AIAccount,
 * making it perfectly safe for players, consoles, clans, or other custom account types.
 */
public class ChangeTokenTool implements AITool {

    @Override
    public String getName() { return "change_token"; }

    @Override
    public String getDescription() { return "Changes the AI token for the account. Provide 'platformName' (e.g., openai) to change it for a specific platform. Can optionally specify 'targetAccountType' and 'targetAccountUuid' to change for another entity like a clan."; }

    @Override
    public String getParametersJsonSchema() {
        return "{"
                + "  \"type\": \"object\","
                + "  \"properties\": {"
                + "    \"platformName\": { \"type\": \"string\", \"description\": \"The name of the platform\" },"
                + "    \"newToken\": { \"type\": \"string\" },"
                + "    \"targetAccountType\": { \"type\": \"string\", \"description\": \"Optional. The account type to modify (e.g., player, console, clan). Defaults to current account if empty.\" },"
                + "    \"targetAccountUuid\": { \"type\": \"string\", \"description\": \"Optional. The UUID of the target account. Defaults to current account if empty.\" }"
                + "  },"
                + "  \"required\": [\"newToken\"]"
                + "}";
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
        String targetType = arguments.containsKey("targetAccountType") && arguments.get("targetAccountType") != null ? (String) arguments.get("targetAccountType") : account.accountType();
        String targetUuid = arguments.containsKey("targetAccountUuid") && arguments.get("targetAccountUuid") != null ? (String) arguments.get("targetAccountUuid") : account.accountUuid();

        ChangeTokenEvent event = new ChangeTokenEvent(account, targetType, targetUuid, platformName, newToken);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture("Error: " + event.getCancelReason());
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

                return manager.setupAccount(targetType, targetUuid, pId, newToken)
                        .thenApply(updatedAccount -> "Success. The token for '" + platformName + "' has been updated for account type [" + targetType + "].");
            });
        }

        return manager.setupAccount(targetType, targetUuid, account.platformId(), newToken)
                .thenApply(updatedAccount -> "Success. The token for the active session has been updated for account type [" + targetType + "].");
    }
}
