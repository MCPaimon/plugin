package io.github.mcpaimon.papermc.tools;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.common.MCAIManager;
import io.github.mcpaimon.papermc.tools.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Collection of AI tools related to Player data and permissions.
 * Registers tools that are separated into the utils package.
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
     * @param account The AI Account.
     * @return The Bukkit Player, or null if not found.
     */
    public static Player getBukkitPlayer(AIAccount account) {
        try {
            return Bukkit.getPlayer(UUID.fromString(account.accountUuid()));
        } catch (Exception e) {
            return null;
        }
    }
}
