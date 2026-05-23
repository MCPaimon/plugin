package io.github.mcpaimon.papermc.tools;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.model.AIPlatform;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.common.MCAIManager;
import io.github.mcpaimon.papermc.MCAIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to view tokens.
 * Logic: Can ONLY view own token. OP status does not bypass this.
 * Supports dynamically fetching tokens for specific platforms.
 */
public class GetTokenTool implements AITool {

    @Override
    public String getName() { return "get_token"; }

    @Override
    public String getDescription() { return "Retrieves the user's API token. Provide 'platformName' (e.g., openai, deepseek) to get the token for a specific platform."; }

    @Override
    public String getParametersJsonSchema() {
        return "{ \"type\": \"object\", \"properties\": { \"targetName\": { \"type\": \"string\" }, \"platformName\": { \"type\": \"string\", \"description\": \"The name of the platform\" } } }";
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        Player sender = Bukkit.getPlayer(UUID.fromString(account.accountUuid()));
        if (sender == null) return CompletableFuture.completedFuture("Error: Cannot find sender in game.");

        String targetName = arguments.containsKey("targetName") ? (String) arguments.get("targetName") : sender.getName();

        // Permission Check: STRICTLY self only, OP means nothing here.
        if (!sender.getName().equalsIgnoreCase(targetName)) {
            return CompletableFuture.completedFuture("Error: Access Denied. You can NEVER view another player's token, even as OP.");
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

                return manager.fetchAccount(account.accountType(), account.accountUuid(), pId)
                        .thenApply(targetAccountOpt -> {
                            if (targetAccountOpt.isEmpty() || targetAccountOpt.get().token().isBlank()) {
                                return "Notice: You do not have an API token registered for " + platformName + " yet.";
                            }
                            return "Your current token for " + platformName + " is: `" + targetAccountOpt.get().token() + "`";
                        });
            });
        }

        return CompletableFuture.completedFuture("Your current token for the active session is: `" + account.token() + "`");
    }
}
