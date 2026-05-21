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

public class MCAICommand implements TabExecutor {

    private final MCAIPlugin plugin;
    private final List<AIPlatform> platformCache = new ArrayList<>();
    private final ConcurrentHashMap<Integer, List<AIModel>> modelCache = new ConcurrentHashMap<>();

    public MCAICommand(MCAIPlugin plugin) {
        this.plugin = plugin;
        refreshCache();
    }

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
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(player, sender.isOp());
            return true;
        }

        // /ai active set <platform> <model>
        if (args[0].equalsIgnoreCase("active") && args.length >= 4 && args[1].equalsIgnoreCase("set")) {
            handleActiveSet(player, args[2], args[3]);
            return true;
        }

        // /ai token set <platform> <token>
        if (args[0].equalsIgnoreCase("token") && args.length >= 4 && args[1].equalsIgnoreCase("set")) {
            handleTokenSet(player, args[2], String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
            return true;
        }

        // Admin commands... (เพิ่ม logic เดิมของคุณที่นี่)
        return true;
    }

    private void handleActiveSet(Player player, String pName, String mName) {
        platformCache.stream().filter(p -> p.displayName().equalsIgnoreCase(pName)).findFirst().ifPresent(p -> {
            this.plugin.getManager().setActiveSession("player", player.getUniqueId().toString(), p.id(), mName).thenRun(() -> {
                player.sendMessage(Component.text("[MCAI] Active AI set!", NamedTextColor.GREEN));
                refreshCache();
            });
        });
    }

    private void handleTokenSet(Player player, String pName, String token) {
        platformCache.stream().filter(p -> p.displayName().equalsIgnoreCase(pName)).findFirst().ifPresent(p -> {
            this.plugin.getManager().setupAccount("player", player.getUniqueId().toString(), p.id(), token).thenRun(() -> {
                player.sendMessage(Component.text("[MCAI] Token set!", NamedTextColor.GREEN));
            });
        });
    }

    private void sendHelp(Player player, boolean isOp) {
        player.sendMessage(Component.text("--- MCAI Help ---", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/ai active set <platform> <model>"));
        player.sendMessage(Component.text("/ai token set <platform> <token>"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("active", "token", "help"));
            if (sender.isOp()) completions.addAll(List.of("platform", "model"));
        } else if (args.length == 2 && args[1].isEmpty()) {
            if (args[0].equalsIgnoreCase("active") || args[0].equalsIgnoreCase("token")) completions.add("set");
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("active") || args[0].equalsIgnoreCase("token"))) {
            platformCache.forEach(p -> completions.add(p.displayName()));
        }
        return completions;
    }
}
