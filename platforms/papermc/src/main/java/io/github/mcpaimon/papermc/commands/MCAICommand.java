package io.github.mcpaimon.papermc.commands;

import io.github.mcpaimon.api.model.AIModel;
import io.github.mcpaimon.api.model.AIPlatform;
import io.github.mcpaimon.papermc.MCAIPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all /ai commands, including validation, feedback, and tab completion.
 */
public class MCAICommand implements TabExecutor {

    private final MCAIPlugin plugin;
    private final List<AIPlatform> platformCache = new ArrayList<>();
    private final ConcurrentHashMap<Integer, List<AIModel>> modelCache = new ConcurrentHashMap<>();

    public MCAICommand(MCAIPlugin plugin) {
        this.plugin = plugin;
        refreshCache();
    }

    /**
     * Refreshes the local cache of platforms and models from the database asynchronously.
     */
    public void refreshCache() {
        this.plugin.getProvider().getPlatforms().thenAccept(platforms -> {
            this.platformCache.clear();
            this.platformCache.addAll(platforms);
            for (AIPlatform p : platforms) {
                this.plugin.getProvider().getModels(p.id()).thenAccept(models -> modelCache.put(p.id(), models));
            }
        });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(player, sender.isOp());
            return true;
        }

        // Toggle AI Chat Mode (/ai chat)
        if (args[0].equalsIgnoreCase("chat")) {
            if (plugin.getAiChatSessions().contains(player.getUniqueId())) {
                plugin.getAiChatSessions().remove(player.getUniqueId());
                player.sendMessage(Component.text("[MCAI] AI Chat Mode: OFF", NamedTextColor.RED));
            } else {
                plugin.getAiChatSessions().add(player.getUniqueId());
                player.sendMessage(Component.text("[MCAI] AI Chat Mode: ON. Type 'quit' or 'exit' to stop.", NamedTextColor.GREEN));
            }
            return true;
        }

        // Set Active Model (/ai active set <platform> <model>)
        if (args[0].equalsIgnoreCase("active")) {
            if (args.length >= 4 && args[1].equalsIgnoreCase("set")) {
                handleActiveSet(player, args[2], args[3]);
            } else {
                player.sendMessage(Component.text("[MCAI] Usage: /ai active set <platform> <model>", NamedTextColor.RED));
            }
            return true;
        }

        // Set API Token (/ai token set <platform> <token>)
        if (args[0].equalsIgnoreCase("token")) {
            if (args.length >= 4 && args[1].equalsIgnoreCase("set")) {
                handleTokenSet(player, args[2], String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
            } else {
                player.sendMessage(Component.text("[MCAI] Usage: /ai token set <platform> <token>", NamedTextColor.RED));
            }
            return true;
        }

        // Fallback for unknown subcommands
        sendHelp(player, sender.isOp());
        return true;
    }

    /**
     * Handles setting the active AI session. Includes validation and user feedback.
     */
    private void handleActiveSet(Player player, String pName, String mName) {
        Optional<AIPlatform> platformOpt = platformCache.stream()
                .filter(p -> p.displayName().equalsIgnoreCase(pName))
                .findFirst();
        
        if (platformOpt.isEmpty()) {
            player.sendMessage(Component.text("[MCAI] Error: Platform '" + pName + "' not found.", NamedTextColor.RED));
            return;
        }
        
        AIPlatform p = platformOpt.get();
        List<AIModel> models = modelCache.get(p.id());
        
        if (models == null || models.stream().noneMatch(m -> m.modelId().equalsIgnoreCase(mName))) {
            player.sendMessage(Component.text("[MCAI] Error: Model '" + mName + "' not found for platform '" + pName + "'.", NamedTextColor.RED));
            return;
        }

        this.plugin.getManager().setActiveSession("player", player.getUniqueId().toString(), p.id(), mName).thenRun(() -> {
            player.sendMessage(Component.text("[MCAI] Active AI successfully set to platform: " + pName + ", model: " + mName, NamedTextColor.GREEN));
            refreshCache();
        }).exceptionally(throwable -> {
            player.sendMessage(Component.text("[MCAI] Error: Failed to set active AI. " + throwable.getMessage(), NamedTextColor.RED));
            return null;
        });
    }

    /**
     * Handles setting the API token. Includes validation and user feedback.
     */
    private void handleTokenSet(Player player, String pName, String token) {
        Optional<AIPlatform> platformOpt = platformCache.stream()
                .filter(p -> p.displayName().equalsIgnoreCase(pName))
                .findFirst();
        
        if (platformOpt.isEmpty()) {
            player.sendMessage(Component.text("[MCAI] Error: Platform '" + pName + "' not found.", NamedTextColor.RED));
            return;
        }
        
        AIPlatform p = platformOpt.get();
        
        this.plugin.getManager().setupAccount("player", player.getUniqueId().toString(), p.id(), token).thenRun(() -> {
            player.sendMessage(Component.text("[MCAI] Token successfully set for platform: " + pName, NamedTextColor.GREEN));
        }).exceptionally(throwable -> {
            player.sendMessage(Component.text("[MCAI] Error: Failed to set token. " + throwable.getMessage(), NamedTextColor.RED));
            return null;
        });
    }

    /**
     * Sends the command help menu to the player.
     */
    private void sendHelp(Player player, boolean isOp) {
        player.sendMessage(Component.text("--- MCAI Help ---", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/ai chat - Toggle AI chat mode", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/ai active set <platform> <model> - Set active AI model", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/ai token set <platform> <token> - Set API token for platform", NamedTextColor.WHITE));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(List.of("active", "token", "chat", "help"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("active") || args[0].equalsIgnoreCase("token")) {
                completions.add("set");
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("active") || args[0].equalsIgnoreCase("token"))) {
            // Suggest platform names
            platformCache.forEach(p -> completions.add(p.displayName()));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("active") && args[1].equalsIgnoreCase("set")) {
            // Suggest model names based on the selected platform
            String pName = args[2];
            platformCache.stream().filter(p -> p.displayName().equalsIgnoreCase(pName)).findFirst().ifPresent(p -> {
                List<AIModel> models = modelCache.get(p.id());
                if (models != null) {
                    models.forEach(m -> completions.add(m.modelId()));
                }
            });
        }
        
        // Filter the completions list to match the current argument prefix
        String currentArg = args[args.length - 1].toLowerCase();
        completions.removeIf(s -> !s.toLowerCase().startsWith(currentArg));
        
        return completions;
    }
}
