package io.github.mcpaimon.papermc.tools.utils;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.papermc.tools.PlayerTools;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to get the current in-game time of the player's world.
 */
public class GetInGameTimeTool implements AITool {
    @Override
    public String getName() { return "get_in_game_time"; }

    @Override
    public String getDescription() { return "Gets the current in-game time (Minecraft world time) of the world the player is currently in."; }

    @Override
    public String getParametersJsonSchema() {
        return "{ \"type\": \"object\", \"properties\": {} }";
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        Player sender = PlayerTools.getBukkitPlayer(account);
        if (sender == null) return CompletableFuture.completedFuture("Error: Cannot find sender in game.");
        
        long time = sender.getWorld().getTime();
        return CompletableFuture.completedFuture(
            "The current in-game time in world '" + sender.getWorld().getName() + "' is " + time + " ticks. " +
            "(Note: 0 = 6:00 AM, 6000 = 12:00 PM (Noon), 12000 = 6:00 PM, 18000 = 12:00 AM (Midnight))"
        );
    }
}
