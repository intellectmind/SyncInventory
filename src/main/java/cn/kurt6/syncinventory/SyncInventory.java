package cn.kurt6.syncinventory;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SyncInventory extends JavaPlugin {

    final Map<String, Inventory> groupInventories = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerGroups = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> groupMembers = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> playerBackups = new ConcurrentHashMap<>();

    // 待确认加入的玩家映射
    private final Map<UUID, String> pendingJoins = new ConcurrentHashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;
    private FileConfiguration langConfig;
    private String currentLanguage;
    private Object scheduledTask;

    // 数据修改标志，避免频繁保存
    private volatile boolean dataModified = false;

    // 同步锁，防止并发修改
    private final Object syncLock = new Object();

    @Override
    public void onEnable() {
        try {
            initializePlugin();
            this.getCommand("syncinv").setExecutor(this);
            getLogger().info(getMessage("plugin-enabled"));
        } catch (Exception e) {
            getLogger().severe("Failed to enable SyncInventory: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializePlugin() throws Exception {
        // 创建插件文件夹
        if (!getDataFolder().exists()) {
            if (!getDataFolder().mkdirs()) {
                throw new IllegalStateException(getMessage("error-create-plugin-folder"));
            }
        }

        // 加载配置
        saveDefaultConfig();
        reloadConfig();
        currentLanguage = getConfig().getString("settings.language", "zh");

        // 加载语言文件
        loadLanguageFile();

        // 初始化数据文件
        setupDataFile();

        // 加载数据
        loadData();

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);

        // 启动自动保存任务
        startAutoSaveTask();

        // 初始化时清理所有待确认状态
        pendingJoins.clear();

        // 注册命令补全
        this.getCommand("syncinv").setTabCompleter(new SyncInventoryTabCompleter(this));
    }

    private void startAutoSaveTask() {
        int interval = getConfig().getInt("settings.auto-save-interval", 5) * 60 * 20; // 转换为tick

        if (isFolia()) {
            scheduledTask = getServer().getGlobalRegionScheduler()
                    .runAtFixedRate(this, task -> {
                        if (dataModified) {
                            saveData();
                            dataModified = false;
                        }
                    }, interval, interval);
        } else {
            scheduledTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (dataModified) {
                        saveData();
                        dataModified = false;
                    }
                }
            }.runTaskTimer(this, interval, interval);
        }
    }

    boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onDisable() {
        // 取消定时任务
        if (scheduledTask != null) {
            try {
                if (isFolia()) {
                    ((ScheduledTask) scheduledTask).cancel();
                } else {
                    ((BukkitTask) scheduledTask).cancel();
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error cancelling scheduled task", e);
            }
        }

        // 强制保存数据
        saveData();
        getLogger().info(getMessage("plugin-disabled"));
    }

    private void loadLanguageFile() {
        try {
            File langFile = new File(getDataFolder(), "lang_" + currentLanguage + ".yml");
            if (!langFile.exists()) {
                saveResource("lang_" + currentLanguage + ".yml", false);
            }
            langConfig = YamlConfiguration.loadConfiguration(langFile);
        } catch (Exception e) {
            getLogger().warning(getMessage("error-load-language-file"));
            currentLanguage = "zh";
            File langFile = new File(getDataFolder(), "lang_zh.yml");
            if (!langFile.exists()) {
                saveResource("lang_zh.yml", false);
            }
            langConfig = YamlConfiguration.loadConfiguration(langFile);
        }
    }

    public String getMessage(String key) {
        if (langConfig == null) {
            return "Missing message: " + key;
        }
        return langConfig.getString(key, "Missing message: " + key);
    }

    private void setupDataFile() throws IOException {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            if (!dataFile.createNewFile()) {
                throw new IOException("Cannot create data file");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadData() {
        synchronized (syncLock) {
            try {
                // 清空现有数据
                groupInventories.clear();
                playerGroups.clear();
                groupMembers.clear();

                // 加载组数据
                if (dataConfig.contains("groups")) {
                    ConfigurationSection groupsSection = dataConfig.getConfigurationSection("groups");
                    if (groupsSection != null) {
                        for (String groupName : groupsSection.getKeys(false)) {
                            loadGroupData(groupName, groupsSection);
                        }
                    }
                }

                // 加载玩家数据
                if (dataConfig.contains("players")) {
                    ConfigurationSection playersSection = dataConfig.getConfigurationSection("players");
                    if (playersSection != null) {
                        loadPlayerData(playersSection);
                    }
                }

                getLogger().info(getMessage("data-loaded")
                        .replace("%groups%", String.valueOf(groupInventories.size()))
                        .replace("%players%", String.valueOf(playerGroups.size())));
            } catch (Exception e) {
                getLogger().severe(getMessage("error-load-data") + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void loadGroupData(String groupName, ConfigurationSection groupsSection) {
        try {
            Inventory groupInv = Bukkit.createInventory(null, 36,
                    getMessage("group-inventory-title").replace("%group%", groupName));

            if (groupsSection.contains(groupName + ".contents")) {
                List<?> contents = groupsSection.getList(groupName + ".contents");
                if (contents != null) {
                    ItemStack[] items = new ItemStack[36];
                    for (int i = 0; i < Math.min(contents.size(), 36); i++) {
                        if (contents.get(i) instanceof ItemStack) {
                            items[i] = (ItemStack) contents.get(i);
                        }
                    }
                    groupInv.setContents(items);
                }
            }

            groupInventories.put(groupName, groupInv);
            groupMembers.put(groupName, ConcurrentHashMap.newKeySet());
        } catch (Exception e) {
            getLogger().warning("Failed to load group " + groupName + ": " + e.getMessage());
        }
    }

    private void loadPlayerData(ConfigurationSection playersSection) {
        for (String uuidStr : playersSection.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(uuidStr);
                String groupName = playersSection.getString(uuidStr);

                if (groupName != null && groupInventories.containsKey(groupName)) {
                    playerGroups.put(playerId, groupName);
                    groupMembers.get(groupName).add(playerId);
                }
            } catch (IllegalArgumentException e) {
                getLogger().warning(getMessage("error-invalid-uuid") + ": " + uuidStr);
            }
        }
    }

    private void saveData() {
        synchronized (syncLock) {
            try {
                File tempFile = new File(getDataFolder(), "data.tmp");
                YamlConfiguration tempConfig = new YamlConfiguration();

                // 保存组数据
                for (Map.Entry<String, Inventory> entry : groupInventories.entrySet()) {
                    tempConfig.set("groups." + entry.getKey() + ".contents",
                            Arrays.asList(entry.getValue().getContents()));
                }

                // 保存玩家数据
                for (Map.Entry<UUID, String> entry : playerGroups.entrySet()) {
                    tempConfig.set("players." + entry.getKey().toString(), entry.getValue());
                }

                // 原子写入
                tempConfig.save(tempFile);

                if (dataFile.exists() && !dataFile.delete()) {
                    throw new IOException(getMessage("error-delete-old-data-file"));
                }
                if (!tempFile.renameTo(dataFile)) {
                    throw new IOException(getMessage("error-rename-temp-file"));
                }

                getLogger().info(getMessage("data-saved"));
            } catch (IOException e) {
                getLogger().severe(getMessage("error-save-data") + ": " + e.getMessage());
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("syncinv")) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage(getMessage("help-default"));
            return true;
        }

        // 处理确认命令
        if (args[0].equalsIgnoreCase("confirm") && sender instanceof Player) {
            return handleConfirm((Player) sender);
        }

        // 恢复背包命令
        if (args[0].equalsIgnoreCase("restore")) {
            return handleRestore(sender);
        }

        Player player = sender instanceof Player ? (Player) sender : null;

        switch (args[0].toLowerCase()) {
            case "create":
                return handleCreateGroup(sender, args);
            case "delete":
                return handleDeleteGroup(sender, args);
            case "join":
                return handleJoinGroup(sender, args);
            case "leave":
                return handleLeaveGroup(sender, args);
            case "list":
                return handleListGroups(sender);
            case "members":
                return handleListMembers(sender, args);
            case "reload":
                return handleReload(sender);
            case "language":
                return handleLanguage(sender, args);
            case "help":
            default:
                return handleHelp(sender);
        }
    }

    private boolean handleConfirm(Player player) {
        String groupName = pendingJoins.get(player.getUniqueId());
        if (groupName == null) {
            player.sendMessage(getMessage("no-pending-join"));
            return true;
        }

        pendingJoins.remove(player.getUniqueId());
        return actuallyJoinGroup(player, player, groupName);
    }

    private boolean handleRestore(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("error-player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (playerGroups.containsKey(player.getUniqueId())) {
            player.sendMessage(getMessage("restore-in-group"));
            return true;
        }

        ItemStack[] backup = playerBackups.get(player.getUniqueId());
        if (backup == null) {
            player.sendMessage(getMessage("no-backup-found"));
            return true;
        }

        player.getInventory().setContents(backup);
        player.updateInventory();
        playerBackups.remove(player.getUniqueId());
        player.sendMessage(getMessage("inventory-restored"));
        markDataModified();
        return true;
    }

    private boolean handleCreateGroup(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("error-player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("syncinv.admin")) {
            player.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(getMessage("usage-create"));
            return true;
        }

        return createGroup(player, args[1]);
    }

    private boolean handleDeleteGroup(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("error-player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("syncinv.admin")) {
            player.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(getMessage("usage-delete"));
            return true;
        }

        return deleteGroup(player, args[1]);
    }

    private boolean handleJoinGroup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("syncinv.join") && !sender.hasPermission("syncinv.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(getMessage("usage-join"));
            return true;
        }

        return joinGroup(sender, args);
    }

    private boolean handleLeaveGroup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("syncinv.leave") && !sender.hasPermission("syncinv.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        return leaveGroup(sender, args);
    }

    private boolean handleListGroups(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("error-player-only"));
            return true;
        }

        return listGroups((Player) sender);
    }

    private boolean handleListMembers(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("error-player-only"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(getMessage("usage-members"));
            return true;
        }

        return listMembers((Player) sender, args[1]);
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("syncinv.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        reloadConfig();
        currentLanguage = getConfig().getString("settings.language", "zh");
        loadLanguageFile();
        sender.sendMessage(getMessage("config-reloaded"));
        return true;
    }

    private boolean handleLanguage(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("error-player-only"));
            return true;
        }

        Player player = (Player) sender;
        if (args.length < 2) {
            player.sendMessage(getMessage("current-language").replace("%language%", currentLanguage));
            player.sendMessage(getMessage("usage-language"));
            return true;
        }

        return setLanguage(player, args[1]);
    }

    private boolean handleHelp(CommandSender sender) {
        if (sender instanceof Player) {
            sendHelp((Player) sender);
        } else {
            sender.sendMessage(getMessage("help-console"));
        }
        return true;
    }

    private boolean createGroup(Player player, String groupName) {
        if (!isValidGroupName(groupName)) {
            player.sendMessage(getMessage("invalid-group-name"));
            return true;
        }

        synchronized (syncLock) {
            if (groupInventories.containsKey(groupName)) {
                player.sendMessage(getMessage("group-exists").replace("%group%", groupName));
                return true;
            }

            Inventory groupInv = Bukkit.createInventory(null, 36,
                    getMessage("group-inventory-title").replace("%group%", groupName));
            groupInventories.put(groupName, groupInv);
            groupMembers.put(groupName, ConcurrentHashMap.newKeySet());
        }

        player.sendMessage(getMessage("group-created").replace("%group%", groupName));
        markDataModified();
        return true;
    }

    private boolean isValidGroupName(String groupName) {
        if (groupName == null || groupName.trim().isEmpty()) {
            return false;
        }
        // 检查长度和字符限制
        return groupName.matches("^[a-zA-Z0-9_]{1,16}$") &&
                !groupName.trim().isEmpty() &&
                groupName.length() <= 16;
    }

    private boolean deleteGroup(Player player, String groupName) {
        synchronized (syncLock) {
            if (!groupInventories.containsKey(groupName)) {
                player.sendMessage(getMessage("group-not-exists").replace("%group%", groupName));
                return true;
            }

            // 通知并移除所有组成员
            Set<UUID> members = new HashSet<>(groupMembers.get(groupName));
            for (UUID memberId : members) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    member.sendMessage(getMessage("group-deleted-notify").replace("%group%", groupName));
                    // 恢复备份背包（如果有）
                    restorePlayerInventory(member);
                }
                playerGroups.remove(memberId);
            }

            groupInventories.remove(groupName);
            groupMembers.remove(groupName);
        }

        player.sendMessage(getMessage("group-deleted").replace("%group%", groupName));
        markDataModified();
        return true;
    }

    private void restorePlayerInventory(Player player) {
        ItemStack[] backup = playerBackups.get(player.getUniqueId());
        if (backup != null) {
            player.getInventory().setContents(backup);
            playerBackups.remove(player.getUniqueId());
            player.sendMessage(getMessage("inventory-restored"));
        } else {
            player.getInventory().clear();
        }
        player.updateInventory();
    }

    private boolean joinGroup(CommandSender sender, String[] args) {
        String groupName;
        Player targetPlayer;

        // 解析参数
        if (args.length == 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(getMessage("error-player-only"));
                return true;
            }
            targetPlayer = (Player) sender;
            groupName = args[1];
        } else if (args.length == 3) {
            if (!sender.hasPermission("syncinv.admin")) {
                sender.sendMessage(getMessage("no-permission"));
                return true;
            }
            targetPlayer = Bukkit.getPlayer(args[1]);
            groupName = args[2];
            if (targetPlayer == null) {
                sender.sendMessage(getMessage("error-player-not-found").replace("%player%", args[1]));
                return true;
            }
        } else {
            sender.sendMessage(getMessage("usage-join"));
            return true;
        }

        // 检查组是否存在
        if (!groupInventories.containsKey(groupName)) {
            sender.sendMessage(getMessage("group-not-exists").replace("%group%", groupName));
            return true;
        }

        // 如果已经在组中，先退出
        if (playerGroups.containsKey(targetPlayer.getUniqueId())) {
            String currentGroup = playerGroups.get(targetPlayer.getUniqueId());
            if (currentGroup.equals(groupName)) {
                sender.sendMessage("§cPlayer is already in group " + groupName);
                return true;
            }
            // 自动退出当前组
            forceLeaveGroup(targetPlayer);
        }

        // 备份当前背包并设置待确认
        if (args.length == 2) { // 只有玩家操作自己时才需要确认
            createBackupAndRequestConfirm(targetPlayer, groupName);
            return true;
        } else {
            // 管理员操作直接加入
            createBackup(targetPlayer);
            return actuallyJoinGroup(sender, targetPlayer, groupName);
        }
    }

    private void createBackupAndRequestConfirm(Player player, String groupName) {
        createBackup(player);
        pendingJoins.put(player.getUniqueId(), groupName);

        String confirmMessage = getMessage("join-confirm-message").replace("%group%", groupName);
        confirmMessage += "\n" + getMessage("join-confirm-timeout");
        player.sendMessage(confirmMessage);

        // 10秒后自动取消
        if (isFolia()) {
            getServer().getGlobalRegionScheduler().runDelayed(this, task -> {
                if (pendingJoins.remove(player.getUniqueId()) != null) {
                    playerBackups.remove(player.getUniqueId());
                    player.sendMessage(getMessage("join-timeout"));
                }
            }, 200L);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (pendingJoins.remove(player.getUniqueId()) != null) {
                        playerBackups.remove(player.getUniqueId());
                        player.sendMessage(getMessage("join-timeout"));
                    }
                }
            }.runTaskLater(this, 200L);
        }
    }

    private void createBackup(Player player) {
        ItemStack[] backup = new ItemStack[36];
        ItemStack[] contents = player.getInventory().getContents();
        System.arraycopy(contents, 0, backup, 0, Math.min(contents.length, 36));
        playerBackups.put(player.getUniqueId(), backup);
        player.sendMessage(getMessage("backup-created"));
    }

    private void forceLeaveGroup(Player player) {
        synchronized (syncLock) {
            String currentGroup = playerGroups.get(player.getUniqueId());
            if (currentGroup != null) {
                groupMembers.get(currentGroup).remove(player.getUniqueId());
                playerGroups.remove(player.getUniqueId());
            }
        }
    }

    private boolean actuallyJoinGroup(CommandSender sender, Player targetPlayer, String groupName) {
        synchronized (syncLock) {
            playerGroups.put(targetPlayer.getUniqueId(), groupName);
            groupMembers.get(groupName).add(targetPlayer.getUniqueId());
        }

        // 同步背包
        syncInventoryToGroup(targetPlayer, groupName);

        // 发送消息
        if (sender != targetPlayer) {
            sender.sendMessage(getMessage("joined-group-other")
                    .replace("%player%", targetPlayer.getName())
                    .replace("%group%", groupName));
        }
        targetPlayer.sendMessage(getMessage("joined-group").replace("%group%", groupName));

        markDataModified();
        return true;
    }

    private boolean leaveGroup(CommandSender sender, String[] args) {
        Player targetPlayer;

        if (args.length <= 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(getMessage("error-player-only"));
                return true;
            }
            targetPlayer = (Player) sender;
        } else {
            if (!sender.hasPermission("syncinv.admin")) {
                sender.sendMessage(getMessage("no-permission"));
                return true;
            }
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                sender.sendMessage(getMessage("error-player-not-found").replace("%player%", args[1]));
                return true;
            }
        }

        String groupName;
        synchronized (syncLock) {
            groupName = playerGroups.get(targetPlayer.getUniqueId());
            if (groupName == null) {
                sender.sendMessage(getMessage("not-in-group-other").replace("%player%", targetPlayer.getName()));
                return true;
            }

            // 从组中移除
            groupMembers.get(groupName).remove(targetPlayer.getUniqueId());
            playerGroups.remove(targetPlayer.getUniqueId());
        }

        // 恢复背包
        restorePlayerInventory(targetPlayer);

        // 发送消息
        if (sender != targetPlayer) {
            sender.sendMessage(getMessage("left-group-other")
                    .replace("%player%", targetPlayer.getName())
                    .replace("%group%", groupName));
        }
        targetPlayer.sendMessage(getMessage("left-group").replace("%group%", groupName));

        markDataModified();
        return true;
    }

    private boolean listGroups(Player player) {
        if (groupInventories.isEmpty()) {
            player.sendMessage(getMessage("no-groups"));
            return true;
        }

        player.sendMessage(getMessage("group-list-header"));
        for (String group : groupInventories.keySet()) {
            int memberCount = groupMembers.get(group).size();
            player.sendMessage(getMessage("group-list-item")
                    .replace("%group%", group)
                    .replace("%count%", String.valueOf(memberCount)));
        }
        return true;
    }

    private boolean listMembers(Player player, String groupName) {
        if (!groupInventories.containsKey(groupName)) {
            player.sendMessage(getMessage("group-not-exists").replace("%group%", groupName));
            return true;
        }

        Set<UUID> members = groupMembers.get(groupName);
        if (members.isEmpty()) {
            player.sendMessage(getMessage("no-members").replace("%group%", groupName));
            return true;
        }

        player.sendMessage(getMessage("member-list-header").replace("%group%", groupName));
        for (UUID memberId : members) {
            Player member = Bukkit.getPlayer(memberId);
            String playerName = member != null ? member.getName() : "Unknown";
            player.sendMessage(getMessage("member-list-item").replace("%player%", playerName));
        }
        return true;
    }

    private boolean setLanguage(Player player, String language) {
        if (!player.hasPermission("syncinv.admin")) {
            player.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (!Arrays.asList("en", "zh").contains(language.toLowerCase())) {
            player.sendMessage(getMessage("invalid-language"));
            return true;
        }

        getConfig().set("settings.language", language.toLowerCase());
        saveConfig();
        currentLanguage = language.toLowerCase();
        loadLanguageFile();

        player.sendMessage(getMessage("language-changed").replace("%language%", language));
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(getMessage("help-header"));
        if (player.hasPermission("syncinv.admin")) {
            player.sendMessage(getMessage("help-create"));
            player.sendMessage(getMessage("help-delete"));
            player.sendMessage(getMessage("help-members"));
            player.sendMessage(getMessage("help-language"));
            player.sendMessage(getMessage("help-reload"));
        }
        if (player.hasPermission("syncinv.join")) {
            player.sendMessage(getMessage("help-join"));
        }
        if (player.hasPermission("syncinv.leave")) {
            player.sendMessage(getMessage("help-leave"));
        }
        player.sendMessage(getMessage("help-list"));
        player.sendMessage(getMessage("help-restore"));
        player.sendMessage(getMessage("help-help"));
    }

    public void syncInventoryToGroup(Player player, String groupName) {
        Inventory groupInv = groupInventories.get(groupName);
        if (groupInv == null) return;

        ItemStack[] groupContents = Arrays.copyOfRange(groupInv.getContents(), 0, 36);
        player.getInventory().setContents(groupContents);
        player.updateInventory();
    }

    public void cleanupPendingJoin(UUID playerId) {
        // 如果玩家有未确认的加入请求，清理它
        if (pendingJoins.remove(playerId) != null) {
            playerBackups.remove(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(getMessage("join-timeout"));
            }
        }
    }

    public void syncInventoryFromPlayer(Player player) {
        String groupName = playerGroups.get(player.getUniqueId());
        if (groupName == null) return;

        Inventory groupInv = groupInventories.get(groupName);
        if (groupInv == null) return;

        // 同步主背包内容到组背包
        ItemStack[] playerContents = Arrays.copyOfRange(player.getInventory().getContents(), 0, 36);
        groupInv.setContents(playerContents);

        // 异步同步给其他在线成员，避免阻塞主线程
        Set<UUID> members = groupMembers.get(groupName);
        if (members == null || members.size() <= 1) return; // 如果只有当前玩家，无需同步

        for (UUID memberId : members) {
            if (memberId.equals(player.getUniqueId())) continue;

            Player member = Bukkit.getPlayer(memberId);
            // 检查玩家是否仍在组中
            if (member != null && member.isOnline() &&
                    groupName.equals(playerGroups.get(memberId))) {
                // 延迟1tick执行，避免在事件处理过程中修改背包
                if (isFolia()) {
                    member.getScheduler().run(this, task -> {
                        if (groupName.equals(playerGroups.get(memberId))) {
                            member.getInventory().setContents(playerContents);
                            member.updateInventory();
                        }
                    }, null);
                } else {
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (member.isOnline() && groupName.equals(playerGroups.get(memberId))) {
                            member.getInventory().setContents(playerContents);
                            member.updateInventory();
                        }
                    }, 1L);
                }
            }
        }

        markDataModified();
    }

    public boolean isInGroup(Player player) {
        return playerGroups.containsKey(player.getUniqueId());
    }

    public String getPlayerGroup(Player player) {
        return playerGroups.get(player.getUniqueId());
    }

    private void markDataModified() {
        dataModified = true;
    }

    /**
     * 清理离线玩家的数据
     */
    public void cleanupOfflinePlayers() {
        synchronized (syncLock) {
            Set<UUID> toRemove = new HashSet<>();

            for (UUID playerId : playerGroups.keySet()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    // 可以添加更复杂的逻辑，比如检查玩家离线时间
                    // 这里简单地保留所有玩家数据
                }
            }

            // 清理空组
            Iterator<Map.Entry<String, Set<UUID>>> groupIterator = groupMembers.entrySet().iterator();
            while (groupIterator.hasNext()) {
                Map.Entry<String, Set<UUID>> entry = groupIterator.next();
                if (entry.getValue().isEmpty()) {
                    String groupName = entry.getKey();
                    groupInventories.remove(groupName);
                    groupIterator.remove();
                    getLogger().info("Cleaned up empty group: " + groupName);
                    markDataModified();
                }
            }
        }
    }

    /**
     * 获取插件统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("groups", groupInventories.size());
        stats.put("total_players", playerGroups.size());
        stats.put("online_players", playerGroups.keySet().stream()
                .mapToInt(uuid -> {
                    Player p = Bukkit.getPlayer(uuid);
                    return (p != null && p.isOnline()) ? 1 : 0;
                }).sum());
        stats.put("backups", playerBackups.size());
        return stats;
    }
}