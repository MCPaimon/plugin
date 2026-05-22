package io.github.mcpaimon.papermc.tools.utils;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.papermc.tools.PlayerTools;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to get the name of the player currently talking to the AI.
 */
public class GetSelfNameTool implements AITool {
    @Override
    public String getName() { return "get_self_name"; }

    @Override
    public String getDescription() { return "Gets the in-game name of the player who is currently talking to you."; }

    @Override
    public String getParametersJsonSchema() {
        return "{ \"type\": \"object\", \"properties\": {} }";
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        Player sender = PlayerTools.getBukkitPlayer(account);
        if (sender == null) return CompletableFuture.completedFuture("Error: Cannot find sender in game.");

        return CompletableFuture.completedFuture("The player currently talking to you is: " + sender.getName());
    }
}
