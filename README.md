# REME MC Studio

这是本聊天的 Minecraft 开发工作台。目标是由助手负责需求拆解、源码研究、开发、升级、性能诊断、Git 提交和测试，用户主要负责游玩与体验反馈。

GitHub 是共享开发中心，桌面端与网页端通过同一仓库交接。新会话先读 [交接说明](docs/HANDOFF.md)。远程仓库：[g2066203208-beep/mc-studio](https://github.com/g2066203208-beep/mc-studio)。初始化 PR 已合入 main，[首次云端构建成功](https://github.com/g2066203208-beep/mc-studio/actions/runs/39402002769)，可在该运行页面的 Artifacts 下载 JAR 压缩包。

## 当前基线

2026-10-20 实机核实：

| 项目 | 状态 |
|---|---|
| 游戏 | Java 版，Minecraft 1.21.1 |
| 加载器 | NeoForge 21.1.248 |
| 发现的整合包 | TerraFirma Rebirth 2，1.21.1-1.0.0-release |
| 已发现实例 Mod | 222 个，文件大小及 SHA-256 见 local/mods-baseline.csv |
| 编译 JDK | 本机 JDK 21.0.9，64 位；另有 JDK 25 |
| Git | 2.54.0.windows.1 |
| GitHub | 插件账号 g2066203208-beep 已连通；gh CLI 未登录 |
| 远程工作室仓库 | g2066203208-beep/mc-studio（私有） |

发现的实例是暂定兼容基线，不代表用户已选定今后的唯一整合包。游戏根目录同时存在 mods、config 和 saves；正式部署前必须核实 HMCL 的实际游戏隔离设置及启动日志，不能只凭目录名确定生效路径。

## 工作台内容

- `mod/`：依据官方 NeoForge 1.21.1 MDK 建立的最小 Mod 工程，锁定当前加载器。仅打印加载标记，不新增方块或修改世界。
- `scripts/Build.ps1`：使用本机配置的 Java 21 构建并检查退出码。
- `scripts/Inventory.ps1`：重新生成已发现实例的 Mod 文件校验清单。
- `docs/WORKFLOW.md`：开发、测试、升级、回滚和 GitHub 管理流程。
- `docs/RESEARCH.md`：开源研究入口、研究方法和来源记录。
- `.github/workflows/build.yml`：已在 GitHub Actions 验证成功，推送和 PR 会触发 Java 21 构建并保存 JAR。
- `local/`：本机路径、构建日志与清单，不进入 Git。

## 构建

Linux / 网页开发环境只需 Java 21 和网络访问，在仓库根目录执行：

```bash
bash scripts/build.sh
```

不需要本机游戏安装或被忽略的 `local/` 配置。

在工作台目录执行：

```powershell
.\scripts\Build.ps1
```

产物目录为 `mod/build/libs/`。开发客户端可在 `mod/` 下使用相同 Java 环境运行 `gradlew.bat runClient`。首次构建需要下载依赖。

本工作台的“构建成功”“客户端能启动”“进入世界成功”“完整整合包兼容”和“性能改善”是不同验收项，必须分别有证据。

## 使用方式

后续直接在本聊天描述玩法、报错或想修改的 Mod。助手先读取本工作台与本机基线，尽量独立完成技术工作；只有账号授权、目标不明确或确实需要玩家体验判断时才让用户介入。

当前尚未部署到正式游戏，也尚未完成真实客户端与整合包运行测试。最新构建结果见 `docs/STATUS.md`。


## 玩家型 NPC 原型（开发中）

功能与约束见 [NPC v0.1 文档](docs/NPC_V1.md)。实验分支 `feature/player-npc-v1`。有作弊权限的玩家执行 `/npc spawn 小雷` 创建 NPC；`/npc follow`、`/npc stay`、`/npc wander`、`/npc guard`、`/npc info` 控制附近自己的 NPC。使用现有 Java 21/NeoForge 1.21.1 工程构建。
