package io.github.mcpaimon.papermc.tools;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.tools.AITool;
import io.github.mcpaimon.common.MCAIManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Collection of AI tools related to Player data and permissions.
 */
public class PlayerTools {

    /**
     * Registers all player-related tools into the MCAIManager.
     * @param manager The central MCAIManager.
     */
    public static void registerAll(MCAIManager manager) {
        manager.registerTool(new GetSelfNameTool());
        manager.registerTool(new GetPlayerInfoTool());
        manager.registerTool(new GetTokenTool());
        manager.registerTool(new ChangeTokenTool(manager));
        manager.registerTool(new DeleteTokenTool(manager));
        manager.registerTool(new GetCurrentDateTool());
        manager.registerTool(new GetInGameTimeTool());
        manager.registerTool(new GetPlayerHealthTool());
        manager.registerTool(new GetPlayerFoodTool());
    }

    /**
     * Helper method to convert AIAccount back to a Bukkit Player.
     */
    private static Player getBukkitPlayer(AIAccount account) {
        try {
            return Bukkit.getPlayer(UUID.fromString(account.accountUuid()));
        } catch (Exception e) {
            return null;
        }
    }

    /* --- Tool Implementations --- */

    /**
     * Tool to get the name of the player currently talking to the AI.
     */
    public static class GetSelfNameTool implements AITool {
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
            Player sender = getBukkitPlayer(account);
            if (sender == null) return CompletableFuture.completedFuture("Error: Cannot find sender in game.");

            return CompletableFuture.completedFuture("The player currently talking to you is: " + sender.getName());
        }
    }

    /**
     * Tool to get a player's UUID.
     * Logic: Player can check themselves. OP can check anyone.
     */
    public static class GetPlayerInfoTool implements AITool {
        @Override
        public String getName() { return "get_player_info"; }

        @Override
        public String getDescription() { 
            return "Gets the UUID of a player. IMPORTANT: To get the UUID of the player currently talking to you, omit the 'targetName' parameter entirely. You do not need to know their name first."; 
        }

        @Override
        public String getParametersJsonSchema() {
            return "{ \"type\": \"object\", \"properties\": { \"targetName\": { \"type\": \"string\" } } }";
        }

        @Override
        public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
            Player sender = getBukkitPlayer(account);
            if (sender == null) return CompletableFuture.completedFuture("Error: Cannot find sender in game.");

            String targetName = arguments.containsKey("targetName") ? (String) arguments.get("targetName") : sender.getName();
            Player target = Bukkit.getPlayer(targetName);
            
            if (target == null) return CompletableFuture.completedFuture("Error: Target player is offline or does not exist.");

            // Permission Check
            if (!sender.getName().equalsIgnoreCase(targetName) && !sender.isOp()) {
                return CompletableFuture.completedFuture("Error: Access Denied. You do not have OP permission to view other players' UUIDs.");
            }

            return CompletableFuture.completedFuture("Success. Name: " + target.getName() + " | UUID: " + target.getUniqueId().toString());
        }
    }

    /**
     * Tool to view tokens.
     * Logic: Can ONLY view own token. OP status does not bypass this.
     */
    public static class GetTokenTool implements AITool {
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
            Player sender = getBukkitPlayer(account);
            if (sender == null) return CompletableFuture.completedFuture("Error: Cannot find sender in game.");

            String targetName = arguments.containsKey("targetName") ? (String) arguments.get("targetName") : sender.getName();

            // Permission Check: STRICTLY self only, OP means nothing here.
            if (!sender.getName().equalsIgnoreCase(targetName)) {
                return CompletableFuture.completedFuture("Error: Access Denied. You can NEVER view another player's token, even as OP.");
            }

            return CompletableFuture.completedFuture("Your current token is: " + account.token());
        }
    }

    /**
     * Tool to change tokens.
     * Logic: Can ONLY change own token. Requires manager access to update DB.
     */
    public static class ChangeTokenTool implements AITool {
        private final MCAIManager manager;

        public ChangeTokenTool(MCAIManager manager) {
            this.manager = manager;
        }

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
            Player sender = getBukkitPlayer(account);
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

            return this.manager.setupAccount(account.accountType(), account.accountUuid(), account.platformId(), newToken)
                    .thenApply(updatedAccount -> "Success. Your token has been updated.");
        }
    }

    /**
     * Tool to delete tokens (sets to empty string).
     * Logic: Can ONLY delete own token.
     */
    public static class DeleteTokenTool implements AITool {
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
            Player sender = getBukkitPlayer(account);
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

    /**
     * Tool to get the current real-world date and time.
     */
    public static class GetCurrentDateTool implements AITool {
        @Override
        public String getName() { return "get_current_date"; }

        @Override
        public String getDescription() { return "Gets the current real-world date and time."; }

        @Override
        public String getParametersJsonSchema() {
            return "{ \"type\": \"object\", \"properties\": {} }";
        }

        @Override
        public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            return CompletableFuture.completedFuture("The current real-world time is: " + currentTime);
        }
    }

    /**
     * Tool to get the current in-game time of the player's world.
     */
    public static class GetInGameTimeTool implements AITool {
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
            Player sender = getBukkitPlayer(account);
            if (sender == null) return CompletableFuture.completedFuture("Error: Cannot find sender in game.");
            
            long time = sender.getWorld().getTime();
            return CompletableFuture.completedFuture(
                "The current in-game time in world '" + sender.getWorld().getName() + "' is " + time + " ticks. " +
                "(Note: 0 = 6:00 AM, 6000 = 12:00 PM (Noon), 12000 = 6:00 PM, 18000 = 12:00 AM (Midnight))"
            );
        }
    }

    /**
     * Tool to get a player's health.
     * Logic: Player can check themselves. OP can check anyone.
     */
    public static class GetPlayerHealthTool implements AITool {
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
            Player sender = getBukkitPlayer(account);
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

    /**
     * Tool to get a player's food level and saturation.
     * Logic: Player can check themselves. OP can check anyone.
     */
    public static class GetPlayerFoodTool implements AITool {
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
            Player sender = getBukkitPlayer(account);
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
}
