package cn.kurt6.syncinventory;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class InventoryListener implements Listener {

    private final SyncInventory plugin;

    // 防止重复同步的玩家集合
    private final Set<UUID> syncingPlayers = new HashSet<>();

    public InventoryListener(SyncInventory plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isInGroup(player)) {
            return;
        }

        // 延迟同步，确保玩家完全加载
        if (plugin.isFolia()) {
            player.getScheduler().runDelayed(plugin, task -> {
                if (player.isOnline() && plugin.isInGroup(player)) {
                    plugin.syncInventoryToGroup(player, plugin.getPlayerGroup(player));
                }
            }, null, 5L);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && plugin.isInGroup(player)) {
                        plugin.syncInventoryToGroup(player, plugin.getPlayerGroup(player));
                    }
                }
            }.runTaskLater(plugin, 5L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (plugin.isInGroup(player)) {
            // 玩家退出时立即同步背包状态
            plugin.syncInventoryFromPlayer(player);
        }

        // 清理同步标记和待确认加入状态
        syncingPlayers.remove(playerId);

        // 清理超时的待确认加入（如果存在）
        plugin.cleanupPendingJoin(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        if (!plugin.isInGroup(player)) return;

        // 只处理玩家背包的点击事件
        if (event.getInventory().getType() != InventoryType.PLAYER &&
                event.getClickedInventory() != null &&
                event.getClickedInventory().getType() != InventoryType.PLAYER) {
            return;
        }

        // 防止在同步过程中再次触发同步
        if (syncingPlayers.contains(player.getUniqueId())) {
            return;
        }

        // 延迟同步，确保点击操作完成
        scheduleSync(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        if (!plugin.isInGroup(player)) return;

        // 关闭背包时同步
        scheduleSync(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        if (!plugin.isInGroup(player)) return;

        // 打开背包时确保数据是最新的
        if (plugin.isFolia()) {
            player.getScheduler().run(plugin, task -> {
                if (player.isOnline() && plugin.isInGroup(player)) {
                    plugin.syncInventoryToGroup(player, plugin.getPlayerGroup(player));
                }
            }, null);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && plugin.isInGroup(player)) {
                        plugin.syncInventoryToGroup(player, plugin.getPlayerGroup(player));
                    }
                }
            }.runTaskLater(plugin, 0L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.isInGroup(player)) {
            scheduleSync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.isInGroup(player)) {
            scheduleSync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isInGroup(player)) return;

        // 重生后恢复组背包
        if (plugin.isFolia()) {
            player.getScheduler().runDelayed(plugin, task -> {
                if (player.isOnline() && plugin.isInGroup(player)) {
                    plugin.syncInventoryToGroup(player, plugin.getPlayerGroup(player));
                }
            }, null, 1L);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && plugin.isInGroup(player)) {
                        plugin.syncInventoryToGroup(player, plugin.getPlayerGroup(player));
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    /**
     * 调度延迟同步任务
     */
    private void scheduleSync(Player player) {
        UUID playerId = player.getUniqueId();

        if (syncingPlayers.contains(playerId)) {
            return;
        }

        syncingPlayers.add(playerId);

        long delay = 0L; // 默认立即执行

        // 对于特定事件保持1 tick延迟
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String methodName = element.getMethodName();
            if (methodName.equals("onInventoryClick") ||
                    methodName.equals("onPlayerDropItem") ||
                    methodName.equals("onPlayerPickupItem") ||
                    methodName.equals("onPlayerRespawn")) {
                delay = 1L;
                break;
            }
        }

        if (plugin.isFolia()) {
            player.getScheduler().runDelayed(plugin, task -> {
                syncingPlayers.remove(playerId);
                if (player.isOnline() && plugin.isInGroup(player)) {
                    plugin.syncInventoryFromPlayer(player);
                }
            }, null, delay);
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    syncingPlayers.remove(playerId);
                    if (player.isOnline() && plugin.isInGroup(player)) {
                        plugin.syncInventoryFromPlayer(player);
                    }
                }
            }.runTaskLater(plugin, delay);
        }
    }
}