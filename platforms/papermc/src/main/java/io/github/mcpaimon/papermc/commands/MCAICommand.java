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

/**
 * Command and TabCompleter for the /ai command.
 */
public class MCAICommand implements TabExecutor {

    private final MCAIPlugin plugin;

    public MCAICommand(MCAIPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be executed by players.", NamedTextColor.RED));
            return true;
        }

        // Handle Chat Toggle
        if (args.length == 0) {
            if (this.plugin.getAiChatSessions().contains(player.getUniqueId())) {
                this.plugin.getAiChatSessions().remove(player.getUniqueId());
                player.sendMessage(Component.text("[MCAI] AI Chat Mode: OFF", NamedTextColor.RED));
            } else {
                // Check if they have an active model set before enabling
                String activeModel = this.plugin.getActiveModel(player.getUniqueId());
                if (activeModel == null || activeModel.isEmpty()) {
                    player.sendMessage(Component.text("Please set your active AI model first using '/ai set active <platform> <model>' before enabling the session.", NamedTextColor.RED));
                    return true;
                }
                
                this.plugin.getAiChatSessions().add(player.getUniqueId());
                player.sendMessage(Component.text("[MCAI] AI Chat Mode: ON. Your chat will now be sent directly to the AI.", NamedTextColor.GREEN));
            }
            return true;
        }

        // Handle Subcommands
        if (args[0].equalsIgnoreCase("set")) {
            
            // Handle: /ai set active <platform> <model>
            if (args.length >= 4 && args[1].equalsIgnoreCase("active")) {
                String platformName = args[2];
                String modelName = args[3];

                this.plugin.getProvider().getPlatforms().thenAccept(platforms -> {
                    Optional<AIPlatform> platformOpt = platforms.stream().filter(p -> p.displayName().equalsIgnoreCase(platformName)).findFirst();
                    if (platformOpt.isEmpty()) {
                        player.sendMessage(Component.text("Error: Platform '" + platformName + "' not found.", NamedTextColor.RED));
                        return;
                    }
                    AIPlatform platform = platformOpt.get();

                    this.plugin.getProvider().getModels(platform.id()).thenAccept(models -> {
                        boolean modelExists = models.stream().anyMatch(m -> m.modelId().equalsIgnoreCase(modelName));
                        if (!modelExists) {
                            player.sendMessage(Component.text("Error: Model '" + modelName + "' not found for platform " + platformName + ".", NamedTextColor.RED));
                            return;
                        }

                        // Maintain the existing token if present
                        this.plugin.getProvider().getToken("player", player.getUniqueId().toString()).thenAccept(tokenOpt -> {
                            String token = tokenOpt.orElse("");
                            this.plugin.getManager().setupAccount("player", player.getUniqueId().toString(), platform.id(), token).thenRun(() -> {
                                this.plugin.setActiveModel(player.getUniqueId(), modelName);
                                player.sendMessage(Component.text("[MCAI] Active AI set to Platform: " + platformName + " | Model: " + modelName, NamedTextColor.GREEN));
                            }).exceptionally(e -> {
                                player.sendMessage(Component.text("Error updating account: " + e.getMessage(), NamedTextColor.RED));
                                return null;
                            });
                        });
                    });
                });
                return true;
            }

            // Handle: /ai set token <platform> <token>
            if (args.length >= 4 && args[1].equalsIgnoreCase("token")) {
                String platformName = args[2];
                String tokenText = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

                this.plugin.getProvider().getPlatforms().thenAccept(platforms -> {
                    Optional<AIPlatform> platformOpt = platforms.stream().filter(p -> p.displayName().equalsIgnoreCase(platformName)).findFirst();
                    if (platformOpt.isEmpty()) {
                        player.sendMessage(Component.text("Error: Platform '" + platformName + "' not found.", NamedTextColor.RED));
                        return;
                    }

                    this.plugin.getManager().setupAccount("player", player.getUniqueId().toString(), platformOpt.get().id(), tokenText).thenRun(() -> {
                        player.sendMessage(Component.text("[MCAI] Token successfully updated for platform: " + platformName, NamedTextColor.GREEN));
                    }).exceptionally(e -> {
                        player.sendMessage(Component.text("Error saving token: " + e.getMessage(), NamedTextColor.RED));
                        return null;
                    });
                });
                return true;
            }
        }

        // Handle Admin Commands
        if (args.length >= 4 && args[0].equalsIgnoreCase("platform") && args[1].equalsIgnoreCase("create")) {
            if (!sender.isOp()) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            String platformName = args[2];
            String url = args[3];
            this.plugin.getManager().registerPlatform(platformName, url).thenAccept(p -> {
                player.sendMessage(Component.text("Successfully created platform: " + p.displayName(), NamedTextColor.GREEN));
            }).exceptionally(e -> {
                player.sendMessage(Component.text("Error creating platform: " + e.getMessage(), NamedTextColor.RED));
                return null;
            });
            return true;
        }

        if (args.length >= 4 && args[0].equalsIgnoreCase("model") && args[1].equalsIgnoreCase("create")) {
            if (!sender.isOp()) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            String platformName = args[2];
            String modelId = args[3];
            
            this.plugin.getProvider().getPlatforms().thenAccept(platforms -> {
                Optional<AIPlatform> platformOpt = platforms.stream().filter(p -> p.displayName().equalsIgnoreCase(platformName)).findFirst();
                if (platformOpt.isEmpty()) {
                    player.sendMessage(Component.text("Error: Platform '" + platformName + "' not found.", NamedTextColor.RED));
                    return;
                }
                
                this.plugin.getManager().registerModel(platformOpt.get().id(), modelId).thenAccept(m -> {
                    player.sendMessage(Component.text("Successfully created model '" + m.modelId() + "' for platform: " + platformName, NamedTextColor.GREEN));
                }).exceptionally(e -> {
                    player.sendMessage(Component.text("Error creating model: " + e.getMessage(), NamedTextColor.RED));
                    return null;
                });
            });
            return true;
        }

        // Send Help Guide
        player.sendMessage(Component.text("Usage Guide:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/ai", NamedTextColor.GRAY).append(Component.text(" - Toggle AI Chat Mode", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/ai set active <platform> <model>", NamedTextColor.GRAY).append(Component.text(" - Select the AI you want to use", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/ai set token <platform> <token>", NamedTextColor.GRAY).append(Component.text(" - Set your private API token", NamedTextColor.WHITE)));
        if (sender.isOp()) {
            player.sendMessage(Component.text("/ai platform create <name> <url>", NamedTextColor.GRAY).append(Component.text(" - Admin: Add platform", NamedTextColor.WHITE)));
            player.sendMessage(Component.text("/ai model create <platform> <model>", NamedTextColor.GRAY).append(Component.text(" - Admin: Add model", NamedTextColor.WHITE)));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if ("set".startsWith(args[0].toLowerCase())) completions.add("set");
            if (sender.isOp()) {
                if ("platform".startsWith(args[0].toLowerCase())) completions.add("platform");
                if ("model".startsWith(args[0].toLowerCase())) completions.add("model");
            }
        } 
        else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set")) {
                if ("active".startsWith(args[1].toLowerCase())) completions.add("active");
                if ("token".startsWith(args[1].toLowerCase())) completions.add("token");
            } else if (sender.isOp() && (args[0].equalsIgnoreCase("platform") || args[0].equalsIgnoreCase("model"))) {
                if ("create".startsWith(args[1].toLowerCase())) completions.add("create");
            }
        } 
        else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set") && (args[1].equalsIgnoreCase("active") || args[1].equalsIgnoreCase("token"))) {
                // Suggest Platforms
                try {
                    List<AIPlatform> platforms = this.plugin.getProvider().getPlatforms().join();
                    for (AIPlatform p : platforms) {
                        if (p.displayName().toLowerCase().startsWith(args[2].toLowerCase())) {
                            completions.add(p.displayName());
                        }
                    }
                } catch (Exception ignored) {}
            } else if (sender.isOp() && args[0].equalsIgnoreCase("model") && args[1].equalsIgnoreCase("create")) {
                // Suggest Platforms for model create
                try {
                    List<AIPlatform> platforms = this.plugin.getProvider().getPlatforms().join();
                    for (AIPlatform p : platforms) {
                        if (p.displayName().toLowerCase().startsWith(args[2].toLowerCase())) {
                            completions.add(p.displayName());
                        }
                    }
                } catch (Exception ignored) {}
            }
        } 
        else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase("active")) {
                // Suggest Models dynamically based on the selected Platform
                try {
                    String platformName = args[2];
                    List<AIPlatform> platforms = this.plugin.getProvider().getPlatforms().join();
                    Optional<AIPlatform> pOpt = platforms.stream().filter(p -> p.displayName().equalsIgnoreCase(platformName)).findFirst();
                    
                    if (pOpt.isPresent()) {
                        List<AIModel> models = this.plugin.getProvider().getModels(pOpt.get().id()).join();
                        for (AIModel m : models) {
                            if (m.modelId().toLowerCase().startsWith(args[3].toLowerCase())) {
                                completions.add(m.modelId());
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        return completions;
    }
}
