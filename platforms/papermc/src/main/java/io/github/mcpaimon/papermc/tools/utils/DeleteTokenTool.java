package io.github.mcpaimon.papermc.tools.utils;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.common.MCAIManager;
import io.github.mcpaimon.papermc.tools.PlayerTools;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to delete tokens (sets to empty string).
 * Logic: Can ONLY delete own token.
 */
public class DeleteTokenTool implements AITool {
    private final MCAIManager manager;

    public DeleteTokenTool(MCAIManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() { return "delete_token"; }

    @Override
    public String getDescription() { return "Deletes (resets) the AI token for the account."; }

    @Override
    public String getParametersJsonSchema() {
        return "{ \"type\": \"object\", \"properties\": { \"targetName\": { \"type\": \"string\" } } }";
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        Player sender = PlayerTools.getBukkitPlayer(account);
        if (sender == null) return CompletableFuture.completedFuture("Error: Cannot find sender in game.");

        String targetName = arguments.containsKey("targetName") ? (String) arguments.get("targetName") : sender.getName();

        // Permission Check: STRICTLY self only
        if (!sender.getName().equalsIgnoreCase(targetName)) {
            return CompletableFuture.completedFuture("Error: Access Denied. You cannot delete another player's token.");
        }

        // Reset token to empty string to effectively "delete" it
        return this.manager.setupAccount(account.accountType(), account.accountUuid(), account.platformId(), "")
                .thenApply(updatedAccount -> "Success. Your token has been deleted.");
    }
}
