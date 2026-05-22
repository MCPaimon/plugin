package io.github.mcpaimon.papermc.tools.utils;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.papermc.tools.PlayerTools;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to get a player's health.
 * Logic: Player can check themselves. OP can check anyone.
 */
public class GetPlayerHealthTool implements AITool {
    @Override
    public String getName() { return "get_player_health"; }

    @Override
    public String getDescription() { 
        return "Gets the current health and maximum health of a player. Omit 'targetName' to check the player currently talking to you."; 
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
            return CompletableFuture.completedFuture("Error: Access Denied. You do not have OP permission to view other players' health.");
        }

        double currentHealth = target.getHealth();
        double maxHealth = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null 
            ? target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() 
            : 20.0;

        return CompletableFuture.completedFuture(
            "Success. Name: " + target.getName() + " | Health: " + String.format("%.1f", currentHealth) + " / " + String.format("%.1f", maxHealth)
        );
    }
}
