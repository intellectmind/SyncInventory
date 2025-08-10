# 📦 SyncInventory - Inventory Synchronization Plugin  

**Read this in other languages: [English](README.md)，[中文](README_zh.md)。**

This plugin enables real-time synchronization of player inventory items, supporting shared inventory contents among players within the same custom group.

Compatible with Folia, Paper, Bukkit, Purpur, Spigot, and other server cores.

## 📌 Features  

- **Multi-player Inventory Sync**  
  Create sync groups where all members' inventories stay automatically synchronized  
- **Multi-language Support**  
  Built-in English (`en`) and Chinese (`zh`) language support  

---

## 🛠 Commands  

All commands use `/syncinv` prefix  

| Command | Description | Permission |  
|---------|-------------|------------|  
| `create <group>` | Create new sync group | `syncinv.manage` |  
| `delete <group>` | Delete existing group | `syncinv.manage` |  
| `join <group>` | Join a sync group | `syncinv.join` |  
| `leave` | Leave current group | `syncinv.leave` |  
| `list` | List all groups | `syncinv.use` |  
| `members <group>` | Show group members | `syncinv.use` |  
| `reload` | Reload configuration | `syncinv.admin` |  
| `language <en/zh>` | Change display language | `syncinv.admin` |  
| `help` | Show command help | `syncinv.use` |  
| `confirm` | Confirm sensitive actions | `syncinv.use` |  

---

## 🔑 Permissions  

| Permission | Description | Default |  
|------------|-------------|---------|  
| `syncinv.use` | Basic commands | `true` |  
| `syncinv.join` | Join groups | `op` |  
| `syncinv.leave` | Leave groups | `op` |  
| `syncinv.manage` | Manage groups | `op` |  
| `syncinv.admin` | Admin access | `op` |  

---

## ⚙ Configuration (config.yml)  

```yaml
settings:
  # Auto-save interval in minutes
  auto-save-interval: 5
  
  # Interface language (en/zh)
  language: en
```

---

### bStats
![bStats](https://bstats.org/signatures/bukkit/SyncInventory.svg)
