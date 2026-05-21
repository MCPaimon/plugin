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
 * Command and TabCompleter for the /ai command.
 */
public class MCAICommand implements TabExecutor {

    private final MCAIPlugin plugin;
    // Cache ข้อมูลเพื่อใช้ใน TabComplete โดยไม่บล็อก Thread หลัก
    private final List<AIPlatform> platformCache = new ArrayList<>();
    private final ConcurrentHashMap<Integer, List<AIModel>> modelCache = new ConcurrentHashMap<>();

    public MCAICommand(MCAIPlugin plugin) {
        this.plugin = plugin;
        refreshCache();
    }

    private void refreshCache() {
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
        // ... (Logic onCommand เดิมของคุณที่แก้ไขแล้ว)
        // อย่าลืมเรียก refreshCache() ในกรณีที่มีการสร้าง platform/model ใหม่ เพื่อให้ TabComplete อัปเดตข้อมูล
        if (args.length >= 2 && args[0].equalsIgnoreCase("platform") && args[1].equalsIgnoreCase("create")) {
            // ... หลังจาก registerPlatform สำเร็จ ให้เรียก refreshCache();
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            if ("active".startsWith(args[0].toLowerCase())) completions.add("active");
            if ("token".startsWith(args[0].toLowerCase())) completions.add("token");
            if ("help".startsWith(args[0].toLowerCase())) completions.add("help");
            if (sender.isOp()) {
                if ("platform".startsWith(args[0].toLowerCase())) completions.add("platform");
                if ("model".startsWith(args[0].toLowerCase())) completions.add("model");
            }
        } 
        else if (args.length == 2 && args[1].isEmpty()) {
            if (args[0].equalsIgnoreCase("active") || args[0].equalsIgnoreCase("token")) {
                completions.add("set");
            } else if (sender.isOp() && (args[0].equalsIgnoreCase("platform") || args[0].equalsIgnoreCase("model"))) {
                completions.add("create");
            }
        }
        else if (args.length == 3 && args[1].equalsIgnoreCase("set")) {
            for (AIPlatform p : platformCache) {
                if (p.displayName().toLowerCase().startsWith(args[2].toLowerCase())) completions.add(p.displayName());
            }
        } 
        else if (args.length == 4 && args[0].equalsIgnoreCase("active")) {
            for (AIPlatform p : platformCache) {
                if (p.displayName().equalsIgnoreCase(args[2])) {
                    List<AIModel> models = modelCache.get(p.id());
                    if (models != null) {
                        for (AIModel m : models) {
                            if (m.modelId().toLowerCase().startsWith(args[3].toLowerCase())) completions.add(m.modelId());
                        }
                    }
                }
            }
        }
        return completions;
    }
}
