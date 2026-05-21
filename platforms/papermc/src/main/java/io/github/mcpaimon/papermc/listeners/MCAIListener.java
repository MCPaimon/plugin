package io.github.mcpaimon.papermc.listeners;

import io.github.mcpaimon.api.model.AIActiveSession;
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

            // Fetch dynamic Active Session from Database
            this.plugin.getManager().getActiveSession("player", player.getUniqueId().toString()).thenAccept(sessionOpt -> {
                if (sessionOpt.isEmpty()) {
                    player.sendMessage(Component.text("[System Error]: No active AI session found. Please use /ai set active.", NamedTextColor.RED));
                    return;
                }
                
                AIActiveSession session = sessionOpt.get();

                this.provider.sendPrompt("player", player.getUniqueId().toString(), session.platformId(), session.modelId(), message, this.aiClient)
                    .thenAccept(response -> {
                        player.sendMessage(Component.text("[AI]: ", NamedTextColor.LIGHT_PURPLE).append(Component.text(response, NamedTextColor.WHITE)));
                    })
                    .exceptionally(throwable -> {
                        player.sendMessage(Component.text("[System Error]: Failed to get response from AI. " + throwable.getMessage(), NamedTextColor.RED));
                        return null;
                    });
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.plugin.getAiChatSessions().remove(event.getPlayer().getUniqueId());
    }
}
