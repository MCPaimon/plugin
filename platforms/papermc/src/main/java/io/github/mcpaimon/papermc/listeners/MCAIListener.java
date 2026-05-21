package io.github.mcpaimon.papermc.listeners;

import io.github.mcpaimon.common.MCAIProvider;
import io.github.mcpaimon.papermc.MCAIPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens to player chat and intercepts it if they are in AI chat mode.
 * Dispatches the message to the central AI provider dynamically based on player's model.
 */
public class MCAIListener implements Listener {

    private final MCAIPlugin plugin;
    private final MCAIProvider provider;
    private final MCAIProvider.IAIWorkflowClient aiClient;

    public MCAIListener(MCAIPlugin plugin, MCAIProvider provider, MCAIProvider.IAIWorkflowClient aiClient) {
        this.plugin = plugin;
        this.provider = provider;
        this.aiClient = aiClient;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (this.plugin.getAiChatSessions().contains(player.getUniqueId())) {
            
            event.setCancelled(true);

            String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

            if (message.equalsIgnoreCase("quit") || message.equalsIgnoreCase("exit")) {
                this.plugin.getAiChatSessions().remove(player.getUniqueId());
                player.sendMessage(Component.text("[MCAI] AI Chat Mode: OFF. Session ended.", NamedTextColor.RED));
                return;
            }
            
            player.sendMessage(Component.text("[You -> AI]: ", NamedTextColor.YELLOW).append(Component.text(message, NamedTextColor.WHITE)));

            if (this.aiClient == null) {
                player.sendMessage(Component.text("[System Error]: AI Client is not configured. Cannot process request.", NamedTextColor.RED));
                return;
            }

            player.sendMessage(Component.text("[System]: Processing your request...", NamedTextColor.GRAY));

            // Fetch the dynamically configured model for this specific player
            String activeModel = this.plugin.getActiveModel(player.getUniqueId());

            this.provider.sendPrompt("player", player.getUniqueId().toString(), activeModel, message, this.aiClient)
                .thenAccept(response -> {
                    player.sendMessage(Component.text("[AI]: ", NamedTextColor.LIGHT_PURPLE).append(Component.text(response, NamedTextColor.WHITE)));
                })
                .exceptionally(throwable -> {
                    player.sendMessage(Component.text("[System Error]: Failed to get response from AI. " + throwable.getMessage(), NamedTextColor.RED));
                    return null;
                });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.plugin.getAiChatSessions().remove(event.getPlayer().getUniqueId());
    }
}
