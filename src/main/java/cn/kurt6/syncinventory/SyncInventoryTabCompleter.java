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
            return filterSuggestions(getMainCommandSuggestions(sender), args[0]);
        } else if (args.length == 2) {
            // 子命令参数补全
            return filterSuggestions(getSecondArgumentSuggestions(sender, args[0]), args[1]);
        } else if (args.length == 3) {
            // 三级参数补全
            return filterSuggestions(getThirdArgumentSuggestions(sender, args[0], args[1]), args[2]);
        }

        return new ArrayList<>();
    }

    private List<String> getMainCommandSuggestions(CommandSender sender) {
        List<String> suggestions = new ArrayList<>();

        // 基础命令
        suggestions.add("help");
        suggestions.add("list");
        suggestions.add("restore");

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
        List<String> suggestions = new ArrayList<>();

        switch (subCommand.toLowerCase()) {
            case "join":
                if (sender.hasPermission("syncinv.admin")) {
                    // 管理员可以指定玩家名或直接输入组名
                    suggestions.addAll(getOnlinePlayerNames());
                    suggestions.addAll(plugin.groupInventories.keySet());
                } else {
                    // 普通玩家只能看到组名
                    suggestions.addAll(plugin.groupInventories.keySet());
                }
                break;
            case "leave":
                if (sender.hasPermission("syncinv.admin")) {
                    // 管理员可以指定玩家名
                    suggestions.addAll(getOnlinePlayerNames());
                }
                break;
            case "delete":
            case "members":
                if (sender.hasPermission("syncinv.admin")) {
                    // 返回所有组名
                    suggestions.addAll(plugin.groupInventories.keySet());
                }
                break;
            case "language":
                if (sender.hasPermission("syncinv.admin")) {
                    suggestions.addAll(Arrays.asList("en", "zh"));
                }
                break;
            case "create":
                if (sender.hasPermission("syncinv.admin")) {
                    suggestions.add("<group_name>");
                }
                break;
        }

        // 如果玩家在组中，为leave命令提供更智能的补全
        if (sender instanceof Player && subCommand.equalsIgnoreCase("leave") &&
                !sender.hasPermission("syncinv.admin")) {
            Player player = (Player) sender;
            String group = plugin.getPlayerGroup(player);
            if (group != null) {
                suggestions.add(group);
            }
        }

        return suggestions;
    }

    private List<String> getThirdArgumentSuggestions(CommandSender sender, String subCommand, String secondArg) {
        List<String> suggestions = new ArrayList<>();

        if (subCommand.equalsIgnoreCase("join") && sender.hasPermission("syncinv.admin")) {
            // 管理员使用 /syncinv join <player> <group> 格式
            // 检查第二个参数是否是有效的玩家名
            Player target = Bukkit.getPlayer(secondArg);
            if (target != null) {
                suggestions.addAll(plugin.groupInventories.keySet());
            }
        } else if (subCommand.equalsIgnoreCase("members") && sender.hasPermission("syncinv.admin")) {
            // /syncinv members <group> <page>
            suggestions.add("1");
            suggestions.add("2");
            suggestions.add("3");
        }

        return suggestions;
    }

    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> filterSuggestions(List<String> suggestions, String input) {
        String inputLower = input.toLowerCase();
        return suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(inputLower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}