package io.github.mcpaimon.papermc.listeners;

import io.github.mcpaimon.api.model.AIActiveSession;
import io.github.mcpaimon.bukkit.event.PreGenerateSummaryEvent;
import io.github.mcpaimon.common.MCAIProvider;
import io.github.mcpaimon.papermc.MCAIPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Bukkit/Paper events related to the MCAI system, specifically processing chat AI interactions.
 */
public class MCAIListener implements Listener {

    private final MCAIPlugin plugin;
    private final MCAIProvider provider;
    private final MCAIProvider.IAIWorkflowClient aiClient;
    
    /**
     * Temporarily stores the accumulated chat history and token usage for players currently in an AI chat session.
     */
    private final Map<UUID, ChatLogSession> sessionLogMap = new ConcurrentHashMap<>();

    public MCAIListener(MCAIPlugin plugin, MCAIProvider provider, MCAIProvider.IAIWorkflowClient aiClient) {
        this.plugin = plugin;
        this.provider = provider;
        this.aiClient = aiClient;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        if (this.plugin.getAiChatSessions().contains(playerUuid)) {
            event.setCancelled(true);
            String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

            // When the player decides to end the session
            if (message.equalsIgnoreCase("quit") || message.equalsIgnoreCase("exit")) {
                this.plugin.getAiChatSessions().remove(playerUuid);
                player.sendMessage(Component.text("[MCAI] AI Chat Mode: OFF. Session ended.", NamedTextColor.RED));
                saveAndClearSessionLog(playerUuid);
                return;
            }
            
            player.sendMessage(Component.text("[You -> AI]: ", NamedTextColor.YELLOW).append(Component.text(message, NamedTextColor.WHITE)));

            if (this.aiClient == null) {
                player.sendMessage(Component.text("[System Error]: AI Client is not configured. Cannot process request.", NamedTextColor.RED));
                return;
            }

            player.sendMessage(Component.text("[System]: Processing your request...", NamedTextColor.GRAY));

            // Fetch dynamic Active Session from Database
            this.plugin.getManager().getActiveSession("player", playerUuid.toString()).thenAccept(sessionOpt -> {
                if (sessionOpt.isEmpty()) {
                    player.sendMessage(Component.text("[System Error]: No active AI session found. Please use /ai set active.", NamedTextColor.RED));
                    return;
                }
                
                AIActiveSession session = sessionOpt.get();

                this.provider.sendPrompt("player", playerUuid.toString(), session.platformId(), session.modelId(), message, this.aiClient, (acc, results) -> {
                    // Fire Bukkit event to allow modifications to tool results
                    PreGenerateSummaryEvent preEvent = new PreGenerateSummaryEvent(acc, message, results);
                    Bukkit.getPluginManager().callEvent(preEvent);
                    return preEvent.getToolResults();
                }).thenAccept(response -> {
                    player.sendMessage(Component.text("[AI]: ", NamedTextColor.LIGHT_PURPLE).append(Component.text(response.content(), NamedTextColor.WHITE)));
                    
                    if (response.totalTokens() > 0) {
                        String usageText = String.format("[Usage]: %d Tokens (Prompt: %d, Completion: %d)", 
                                response.totalTokens(), response.promptTokens(), response.completionTokens());
                        player.sendMessage(Component.text(usageText, NamedTextColor.GRAY));
                    }

                    // Append the interaction to the player's active session log map
                    ChatLogSession logData = sessionLogMap.computeIfAbsent(playerUuid, k -> new ChatLogSession(session.platformId(), session.modelId()));
                    logData.appendHistory(message, response.content());
                    logData.addTokens(response.totalTokens());

                }).exceptionally(throwable -> {
                    player.sendMessage(Component.text("[System Error]: Failed to get response from AI. " + throwable.getMessage(), NamedTextColor.RED));
                    return null;
                });
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        // Clear chat mode if active and save logs if any exist
        this.plugin.getAiChatSessions().remove(playerUuid);
        saveAndClearSessionLog(playerUuid);
    }

    /**
     * Saves the accumulated session chat log to the database and removes it from memory.
     * * @param playerUuid The UUID of the player.
     */
    private void saveAndClearSessionLog(UUID playerUuid) {
        ChatLogSession logData = sessionLogMap.remove(playerUuid);
        if (logData != null && logData.hasHistory()) {
            this.plugin.getManager().insertLog(
                "player",
                playerUuid.toString(),
                logData.getPlatformId(),
                logData.getModelId(),
                logData.getFormattedHistory(),
                logData.getTotalTokens()
            ).thenAccept(savedLog -> {
                // Successfully saved log to database
            }).exceptionally(throwable -> {
                this.plugin.getLogger().warning("Failed to save AI chat log for player " + playerUuid + ": " + throwable.getMessage());
                return null;
            });
        }
    }

    /**
     * Internal object to hold cumulative chat history and token usage for an ongoing session.
     */
    private static class ChatLogSession {
        private final int platformId;
        private final String modelId;
        private final StringBuilder history;
        private int totalTokens;

        public ChatLogSession(int platformId, String modelId) {
            this.platformId = platformId;
            this.modelId = modelId;
            this.history = new StringBuilder();
            this.totalTokens = 0;
        }

        public void appendHistory(String prompt, String response) {
            this.history.append("User: ").append(prompt).append("\n");
            this.history.append("AI: ").append(response).append("\n\n");
        }

        public void addTokens(int tokens) {
            this.totalTokens += tokens;
        }

        public boolean hasHistory() {
            return this.history.length() > 0;
        }

        public String getFormattedHistory() {
            return this.history.toString().trim();
        }

        public int getPlatformId() {
            return platformId;
        }

        public String getModelId() {
            return modelId;
        }

        public int getTotalTokens() {
            return totalTokens;
        }
    }
}
