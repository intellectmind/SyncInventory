# 📦 SyncInventory 背包同步插件

**其他语言版本: [English](README.md)，[中文](README_zh.md)。**

## 📌 功能介绍

- **多玩家共享**  
  可创建同步组，组内所有玩家的背包物品栏自动保持一致。
- **多语言支持**  
  默认支持 `en` / `zh` 语言切换。

---

## 🛠 命令说明

所有命令的前缀为 `/syncinv`

| 子命令 | 说明 | 权限 |
| ------ | ---- | ---- |
| create `<组名>` | 创建一个物品栏同步组 | `syncinv.manage` |
| delete `<组名>` | 删除一个同步组 | `syncinv.manage` |
| join `<组名>` | 加入一个同步组 | `syncinv.join` |
| leave | 离开当前同步组 | `syncinv.leave` |
| list | 列出所有同步组 | `syncinv.use` |
| members `<组名>` | 查看组内成员 | `syncinv.use` |
| reload | 重新加载配置文件 | `syncinv.admin` |
| language `<en/zh>` | 切换语言 | `syncinv.admin` |
| help | 显示帮助信息 | `syncinv.use` |
| confirm | 确认某些需要二次确认的操作 | `syncinv.use` |

---

## 🔑 权限节点

| 节点 | 说明 | 默认值 |
| ---- | ---- | ------ |
| `syncinv.use` | 使用基础命令 | `true` |
| `syncinv.join` | 允许加入同步组 | `op` |
| `syncinv.leave` | 允许离开同步组 | `op` |
| `syncinv.manage` | 允许创建、删除同步组 | `op` |
| `syncinv.admin` | 拥有所有管理权限 | `op` |

## ⚙ 配置文件说明（config.yml）

```yaml
settings:
  # 持久化数据自动保存间隔(分钟)
  auto-save-interval: 5

  # 插件语言 (en / zh)
  language: zh
```

---

### bStats
![bStats](https://bstats.org/signatures/bukkit/SyncInventory.svg)
