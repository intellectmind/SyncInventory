# 📦 SyncInventory 背包同步插件

## 🎯 功能概述
实现多玩家背包内容实时同步，支持：
- 创建/管理同步组
- 即时背包同步
- 多语言界面
- 数据自动备份
- Folia服务端兼容

---

## 📥 安装指南
1. 下载 `SyncInventory.jar`
2. 放入 `plugins` 文件夹
3. 重启服务器
4. 自动生成配置目录：

---

## 🔐 权限说明

| 权限节点 | 默认 | 描述 |
|---------|------|------|
| `syncinv.use` | OP | 基础命令权限 |
| `syncinv.join` | OP | 加入组权限 | 
| `syncinv.leave` | OP | 离开组权限 |
| `syncinv.manage` | OP | 组管理权限 |
| `syncinv.admin` | OP | 所有管理权限 |

---

## ⌨️ 命令手册

### 基础命令
| 命令 | 参数 | 描述 |
|------|------|------|
| `/syncinv help` | - | 查看帮助 |
| `/syncinv list` | - | 列出所有组 |

### 组管理
| 命令 | 参数 | 示例 |
|------|------|------|
| `/syncinv create` | `<组名>` | `/syncinv create A组` |
| `/syncinv delete` | `<组名>` | `/syncinv delete A组` |

### 成员操作
| 命令 | 参数 | 注意事项 |
|------|------|----------|
| `/syncinv join` | `<组名>` | 需二次确认 |
| `/syncinv confirm` | - | 确认加入 |
| `/syncinv restore` | - | 恢复个人背包 |

---

## ⚙️ 配置详解
`config.yml`
```yaml
settings:
auto-save-interval: 5  # 自动保存时间(分钟)
language: zh           # 界面语言(zh/en)
