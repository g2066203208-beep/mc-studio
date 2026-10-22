# HMCL 安装及指令/生成蛋真实客户端验收 (v0.6.6)

本阶段目标是**解决用户截图中 `/npc` 根命令未知**。此前所有 GameTest 都是 GitHub 的服务器环境，不能证明 JAR 已经安装进用户 HMCL 当前实例。真实用户尚未确认已成功运行。


## 0.6.6-dev：不用命令也能生成 NPC（当前版本）
- **这是 NPC Mod，不是世界自带的指令**：对应 Minecraft **1.21.1 / NeoForge 21.1.248 / Java 21**，必须确认在游戏 Mods 列表看见 `REME MC Studio`、版本 `0.6.6-dev`。
- **创造模式**：背包「刷怪蛋」分类查找 `NPC 生成蛋（居民）`，或物品搜索 `NPC`；手持右键地面即可放出有本地家庭位置、背包、身份与 LIVE 作息的 NPC。**不需要作弊、OP、/npc 命令或管理员权限**。
- **生存模式**：工作台按 `EBE / BPB / EBE` 3×3 合成生成蛋（E=绿宝石、B=面包、P=纸），右键地面生成居民；真实消耗材料及生成蛋，不凭空刷人口。配方见 `data/reme_studio/recipe/npc_spawn_egg.json`。
- **可选命令**：聊天输入 `/remecheck` 或 `/npc` 可检查已注册，无需 OP；`/npc spawn`、`/npc demo` 以及建造/世界修改指令需要作弊或管理员权限。没有 OP 时不显示受限子命令是 Minecraft 原生权限过滤，属于预期行为。
- **如果连 `/remecheck` 和 `/npc` 都提示未知**：这一症状不能由「没开作弊」解释。先查看 Mods 列表是否有 `REME MC Studio`；如果没有，打开 HMCL 你正在启动的 **那个 1.21.1 NeoForge 实例** 的「游戏文件夹」，确认该文件夹的 `mods/reme_studio-0.6.6-dev.jar`，**不要放进相邻或未启用版本** 的 mods 文件夹。完整关闭游戏后用本 ZIP 的 `Install-REME-NPC.cmd` 重新安装，原旧 JAR 自动备份，不碰存档。
- **如果 Mods 列表有但还是未知**：打开**同一实例** 的 `logs/latest.log`，搜索 `REME NPC 0.6.6-dev`、`REME NPC: /npc present on dispatcher`、`COMMAND REGISTRATION MISSING` 和 Java 异常。把日志提供给开发者定位，不要用凭空推测代替证据。
- **测试范围**：NeoForge GameTest 可验证真正的 ItemStack.useOn 生物蛋生成、权限 0 的根指令存在，Linux Xvfb 图形客户端验证玩家登录、实际指令和物品注册；Windows PowerShell 安装 CI 只使用隔离的假游戏目录，**不能代替用户 HMCL 整合包环境**。

## 安装包（历史流程，以下 0.5.4 证据仅供追溯）
成功构建后，将 `mod/build/libs/reme_studio-0.6.6-dev.jar` 与 `packaging/Install-REME-NPC.cmd` / `packaging/Install-REME-NPC.ps1` 放在同一 ZIP 根目录。**完整解压**，关闭 Minecraft，双击 `Install-REME-NPC.cmd`，选择 HMCL 当前选中版本的“游戏目录”，明确输入 `INSTALL` 确认。

脚本：
- 自动解压/读取 JAR 的 `META-INF/neoforge.mods.toml`，验证 modId 必须是 `reme_studio`；
- 仅在用户明确确认所选目录后，扫描该目录 `mods` 下 JAR 的 **真实 modId**，将过去的 REME JAR 移到 `mods/REME-backup-时间`，不会删除个人存档；
- 安装新 JAR，验证 SHA-256 一致，并检查仅有一份 REME 安装；
- 提示重启当前实例，看 Mod 列表与登录消息；任何报错优先打开当前实例 `logs/latest.log`。

风险：HMCL 会有版本隔离，不能从桌面路径猜测正确 `mods` 文件夹。脚本**不会**擅自自动猜选版本，也不会修改任何世界存档。PowerShell 静态检查不是 Windows GUI 安装验收，用户必须确认目录。

## 游戏验收（最新 0.6.6 需要按上节额外验收）
1. 进入 HMCL 当前选中 NeoForge 1.21.1，Mods 页面能找到 REME MC Studio，日志有 `REME NPC 0.5.2: MOD CONSTRUCTED`（基于 v0.5.2 源码的日志文案）。
2. 新建可作弊创造模式测试世界，进入时应该有 REME NPC 系统消息。
3. 不需要作弊的 `/remecheck` 必须报告 `/npc registered=true`。
4. `/npc spawn` 必须生成可见的 NPC，空手右键对话，Shift 空手右键打开 36 槽背包。
5. `/npc demo` 必须真实创建六人。挑一个农民，给它成熟农田/食物，再观察 `/npc info`。
6. 停止加载、存档重启后 `/npc info` 和人格/NBT 正常。必须实际核对，不以单纯服务器 GameTest 代替。
7. 再测自然生成、20/50/100 NPC TPS、其他模组兼容。未完成测试前保持 PR 草稿。

## 开源参考
研究具体源码见 `docs/OPEN_SOURCE_NPC_CODE_AUDIT.md`，GoalScheduler、Kithkyn RoamGoal、MCA Reborn VillagerBrain、MineColonies TickRateStateMachine，不抄代码，仅取任务生命周期/导航状态/失败恢复的设计。

## 2026-10-21 v0.5.4 客户端真正进世界验收（首次完成）

- JAR 源提交 `e469663d8c648e22cdd26d339c5f3ca0f14665c9`；`226220f3d52c015c859bc39662881532674d3274` 只改图形客户端测试脚本。
- **Minecraft 1.21.1 + NeoForge 21.1.248 原生图形客户端**：GitHub Linux Xvfb/Openbox 用真实鼠标键盘 `xdotool` 完成首次辅助功能引导、点击 Singleplayer、打开新世界设置、开启 Allow Commands、点击 Create New World、启动集成游戏服务器；实际世界进入成功，非仅启动菜单。
- [实际图形客户端运行 39545260822](https://github.com/g2066203208-beep/mc-studio/actions/runs/39545260822) SUCCESS；artifact `12321243417` 含 11 张真实截图和 `client-gui.log`, `run/logs/latest.log`。日志 `Dev ... logged in with entity id`、`REME NPC: /npc present on dispatcher`。
- 在该真实世界，模拟键盘输入 `/remecheck`，聊天栏显示 `/npc registered=true`；`/npc spawn` 成功，在客户端渲染出 Steve 模型 NPC；`/npc info` 真正回显饥饿、精力、社交、意图与区域；`/npc demo` 生成 6 名各有姓名和职业的居民，画面可见多个实体；`/time set night` 真实切夜且画面中的居民仍在。**这次截图由实际游戏窗口截取，非 AI 图像或服务端模拟。**
- NeoForge GameTest [39545266331](https://github.com/g2066203208-beep/mc-studio/actions/runs/39545266331) SUCCESS，**13/13 required tests**，含服务端 FakePlayer 空手右键属于自己的 NPC，确认问候记忆实际增加。**没有用 Xvfb 图形鼠标手工右键并打开背包界面**。
- **真实 Windows PowerShell 安装测试** [39545266342](https://github.com/g2066203208-beep/mc-studio/actions/runs/39545266342) SUCCESS：非交互干运行不改 mods、备份重命名旧版 REME、不误删其他 Mod、新版安装 hash 校验通过。
- Java21 + NeoForge 编译及双 ZIP artifact [39545266219](https://github.com/g2066203208-beep/mc-studio/actions/runs/39545266219) SUCCESS，installer artifact `12321820441`，ZIP `REME-NPC-HMCL-v0.5.4-installer.zip`，完整解压得到 `Install-REME-NPC.cmd`、`Install-REME-NPC.ps1`、`README.txt`、`reme_studio-0.5.4-dev.jar`。JAR 128021 bytes, SHA256 `c31c085d490d3a60353ae4ffd098c1d9736a6dd6b027d40b09b9cf2a5c277568`，已解包并确认元数据 ModID=reme_studio，version 0.5.4-dev。
- **尚未完成**：用户自己的 HMCL 游戏实例是否已经载入正确版本、222 Mod 整合包兼容性、真实 GUI 右键与背包窗口、长时间自然生成/数日行为、床/工作台 POI、充分的生活生产、20/50/100/200 NPC TPS。不得以已进入 CI 测试世界推论“所有人环境可用”。
- 已发现轻微文案问题：v0.5.4 Mod 元数据正确，但部分自检/登录提示仍显示旧 `v0.5.2`，只是字符串未更新，不应误当实际安装的 JAR 版本；以模组界面的 Mod 版本和文件 SHA 为准。
