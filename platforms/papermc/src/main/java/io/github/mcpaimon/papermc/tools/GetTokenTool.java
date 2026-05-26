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
 * Logic: Fully dynamic. Safely retrieves the token exclusively for the target AIAccount.
 */
public class GetTokenTool implements AITool {

    @Override
    public String getName() { return "get_token"; }

    @Override
    public String getDescription() { return "Retrieves the target account's API token. Provide 'platformName' (e.g., openai, deepseek) to get the token for a specific platform. Can optionally specify 'targetAccountType' and 'targetAccountUuid'."; }

    @Override
    public String getParametersJsonSchema() {
        return "{"
                + "  \"type\": \"object\","
                + "  \"properties\": {"
                + "    \"platformName\": { \"type\": \"string\", \"description\": \"The name of the platform\" },"
                + "    \"targetAccountType\": { \"type\": \"string\", \"description\": \"Optional. The account type to view.\" },"
                + "    \"targetAccountUuid\": { \"type\": \"string\", \"description\": \"Optional. The UUID of the target account.\" }"
                + "  }"
                + "}";
    }

    @Override
    public List<String> getCategories() {
        return List.of("account_management");
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        String platformName = (String) arguments.get("platformName");
        String targetType = arguments.containsKey("targetAccountType") && arguments.get("targetAccountType") != null ? (String) arguments.get("targetAccountType") : account.accountType();
        String targetUuid = arguments.containsKey("targetAccountUuid") && arguments.get("targetAccountUuid") != null ? (String) arguments.get("targetAccountUuid") : account.accountUuid();


        GetTokenEvent event = new GetTokenEvent(account, targetType, targetUuid, platformName);
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

                return manager.fetchAccount(targetType, targetUuid, pId)
                        .thenApply(targetAccountOpt -> {
                            if (targetAccountOpt.isEmpty() || targetAccountOpt.get().token().isBlank()) {
                                return "Notice: No API token registered for '" + platformName + "' on account type [" + targetType + "].";
                            }
                            return "The current token for '" + platformName + "' on account type [" + targetType + "] is: `" + targetAccountOpt.get().token() + "`";
                        });
            });
        }

        if (targetType.equals(account.accountType()) && targetUuid.equals(account.accountUuid())) {
             return CompletableFuture.completedFuture("The current token for the active session on account type [" + targetType + "] is: `" + account.token() + "`");
        } else {
            return manager.fetchAccount(targetType, targetUuid, account.platformId())
                    .thenApply(targetAccountOpt -> {
                        if (targetAccountOpt.isEmpty() || targetAccountOpt.get().token().isBlank()) {
                            return "Notice: No API token registered for active platform on account type [" + targetType + "].";
                        }
                        return "The current token for active platform on account type [" + targetType + "] is: `" + targetAccountOpt.get().token() + "`";
                    });
        }
    }
}
