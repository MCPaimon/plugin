package io.github.mcpaimon.papermc.tools.utils;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.papermc.tools.PlayerTools;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to get a player's food level and saturation.
 * Logic: Player can check themselves. OP can check anyone.
 */
public class GetPlayerFoodTool implements AITool {
    @Override
    public String getName() { return "get_player_food"; }

    @Override
    public String getDescription() { 
        return "Gets the current food level (hunger) and saturation of a player. Omit 'targetName' to check the player currently talking to you."; 
    }

    @Override
    public String getParametersJsonSchema() {
        return "{ \"type\": \"object\", \"properties\": { \"targetName\": { \"type\": \"string\" } } }";
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        Player sender = PlayerTools.getBukkitPlayer(account);
        if (sender == null) return CompletableFuture.completedFuture("Error: Cannot find sender in game.");

        String targetName = arguments.containsKey("targetName") ? (String) arguments.get("targetName") : sender.getName();
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null) return CompletableFuture.completedFuture("Error: Target player is offline or does not exist.");

        // Permission Check
        if (!sender.getName().equalsIgnoreCase(targetName) && !sender.isOp()) {
            return CompletableFuture.completedFuture("Error: Access Denied. You do not have OP permission to view other players' food level.");
        }

        int foodLevel = target.getFoodLevel();
        float saturation = target.getSaturation();

        return CompletableFuture.completedFuture(
            "Success. Name: " + target.getName() + " | Food Level: " + foodLevel + " / 20 | Saturation: " + String.format("%.1f", saturation)
        );
    }
}
