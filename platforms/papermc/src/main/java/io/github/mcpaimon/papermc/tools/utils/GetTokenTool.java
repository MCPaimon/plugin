package io.github.mcpaimon.papermc.tools.utils;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.papermc.tools.PlayerTools;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to view tokens.
 * Logic: Can ONLY view own token. OP status does not bypass this.
 */
public class GetTokenTool implements AITool {
    @Override
    public String getName() { return "get_token"; }

    @Override
    public String getDescription() { return "Retrieves the current AI token of the account."; }

    @Override
    public String getParametersJsonSchema() {
        return "{ \"type\": \"object\", \"properties\": { \"targetName\": { \"type\": \"string\" } } }";
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        Player sender = PlayerTools.getBukkitPlayer(account);
        if (sender == null) return CompletableFuture.completedFuture("Error: Cannot find sender in game.");

        String targetName = arguments.containsKey("targetName") ? (String) arguments.get("targetName") : sender.getName();

        // Permission Check: STRICTLY self only, OP means nothing here.
        if (!sender.getName().equalsIgnoreCase(targetName)) {
            return CompletableFuture.completedFuture("Error: Access Denied. You can NEVER view another player's token, even as OP.");
        }

        return CompletableFuture.completedFuture("Your current token is: " + account.token());
    }
}
