package io.github.mcpaimon.papermc.tools;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.common.MCAIManager;
import io.github.mcpaimon.papermc.MCAIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to change tokens.
 * Logic: Can ONLY change own token. Requires manager access to update DB.
 */
public class ChangeTokenTool implements AITool {

    @Override
    public String getName() { return "change_token"; }

    @Override
    public String getDescription() { return "Changes the AI token for the account. Requires 'newToken' parameter."; }

    @Override
    public String getParametersJsonSchema() {
        return "{ \"type\": \"object\", \"properties\": { \"targetName\": { \"type\": \"string\" }, \"newToken\": { \"type\": \"string\" } }, \"required\": [\"newToken\"] }";
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        Player sender = Bukkit.getPlayer(UUID.fromString(account.accountUuid()));
        if (sender == null) return CompletableFuture.completedFuture("Error: Cannot find sender in game.");

        String targetName = arguments.containsKey("targetName") ? (String) arguments.get("targetName") : sender.getName();
        String newToken = (String) arguments.get("newToken");

        if (newToken == null || newToken.isBlank()) {
            return CompletableFuture.completedFuture("Error: 'newToken' parameter is missing.");
        }

        // Permission Check: STRICTLY self only
        if (!sender.getName().equalsIgnoreCase(targetName)) {
            return CompletableFuture.completedFuture("Error: Access Denied. You cannot change another player's token.");
        }

        MCAIManager manager = JavaPlugin.getPlugin(MCAIPlugin.class).getManager();
        return manager.setupAccount(account.accountType(), account.accountUuid(), account.platformId(), newToken)
                .thenApply(updatedAccount -> "Success. Your token has been updated.");
    }
}
