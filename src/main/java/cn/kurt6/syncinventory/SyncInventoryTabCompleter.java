package cn.kurt6.syncinventory;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SyncInventoryTabCompleter implements TabCompleter {

    private final SyncInventory plugin;

    public SyncInventoryTabCompleter(SyncInventory plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("syncinv")) {
            return null;
        }

        if (args.length == 1) {
            // 主命令补全
            return getMainCommandSuggestions(sender);
        } else if (args.length == 2) {
            // 子命令参数补全
            return getSecondArgumentSuggestions(sender, args[0]);
        } else if (args.length == 3) {
            // 三级参数补全
            return getThirdArgumentSuggestions(sender, args[0], args[1]);
        }

        return new ArrayList<>();
    }

    private List<String> getMainCommandSuggestions(CommandSender sender) {
        List<String> suggestions = new ArrayList<>(Arrays.asList("help", "list", "restore"));

        // 根据权限添加建议
        if (sender.hasPermission("syncinv.join")) {
            suggestions.add("join");
            suggestions.add("confirm");
        }
        if (sender.hasPermission("syncinv.leave")) {
            suggestions.add("leave");
        }
        if (sender.hasPermission("syncinv.admin")) {
            suggestions.addAll(Arrays.asList("create", "delete", "members", "reload", "language"));
        }

        return suggestions;
    }

    private List<String> getSecondArgumentSuggestions(CommandSender sender, String subCommand) {
        switch (subCommand.toLowerCase()) {
            case "join":
            case "leave":
                if (sender.hasPermission("syncinv.admin")) {
                    // 管理员可以指定玩家名
                    return getOnlinePlayerNames();
                }
                break;
            case "delete":
            case "members":
                if (sender.hasPermission("syncinv.admin")) {
                    // 返回所有组名
                    return new ArrayList<>(plugin.groupInventories.keySet());
                }
                break;
            case "language":
                if (sender.hasPermission("syncinv.admin")) {
                    return Arrays.asList("en", "zh");
                }
                break;
        }
        return new ArrayList<>();
    }

    private List<String> getThirdArgumentSuggestions(CommandSender sender, String subCommand, String secondArg) {
        if (subCommand.equalsIgnoreCase("join") && sender.hasPermission("syncinv.admin")) {
            // 管理员使用 /syncinv join <player> <group> 格式
            return new ArrayList<>(plugin.groupInventories.keySet());
        }
        return new ArrayList<>();
    }

    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }
}