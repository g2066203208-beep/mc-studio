# 新版提示：v0.6.9-dev 市集与居民经济

- **当前 NPC 功能开发 PR：#93**（`feat/npc-economy-market-fair-v069`）；继承 PR #92 的原生床位系统，新增真物资的自动 NPC 交易、市集、警钟与演奏，玩家可输入 `/npc market`。
- 打包版本必须是 `0.6.9-dev`，Minecraft 1.21.1 / NeoForge 21.1.262+（符合 1.21.1 元数据）/ Java21，实际具体 JAR 以最新成功 Build Actions Artifacts 为准；主分支的 REME 骨架不是此完整 NPC Mod。
- 选择 HMCL **实际启动实例** 的 mods 文件夹，移除旧 `reme_studio` 同 ID JAR，先备份世界再测试。不同工作室中 PEAK 和 NPC 当前都用 `reme_studio`，不能同时加载，不要假装已经解决全部六工作室模块隔离。
- GitHub GameTest 和虚拟 Xvfb 图形客户端不能替代用户自己的大型整合包，运行失败需附 `logs/latest.log` 与 Mod 菜单，特别是 `/remecheck` 和 `/npc market` 是否真的存在。
- 版本 0.6.8 / 0.6.7 的旧诊断保留下方，帮助旧档排障。

---

# 最新 v0.6.8（真实床位与 NPC 指令，2026-10-22）

- 工作分支：`feat/npc-real-bed-lifecycle-v068`，PR #92。正式主分支尚未整合。
- 游戏目标：Minecraft 1.21.1、NeoForge 21.1.262（严格依元数据的 NeoForge 下限）、Java 21。
- 增加真实原版床位认领、原生入睡/起床、`/npc bed`、`/npc bed assign`、`/npc bed clear`、床位 NBT、真正睡床恢复精力。新增测试详见 `docs/NPC_BED_LIFECYCLE_V068.md`。
- 请选择 PR #92 对应的 **成功 Build Action Artifacts** 中 `installer-0.6.8-dev`，不要拿 main 中的通用 `reme_studio` 骨架、PEAK 攀爬包或旧 0.6.7 安装包替代。
- HMCL 必须打开正在运行的那一个实例对应 mods 文件夹，不能凭路径猜。不要让同一目录有两个同 ID 的 REME 版本，也不要同时装 PEAK 当前尚未隔离的 `reme_studio` 包。
- 真实 GUI 测试仅能证明 GitHub 的干净 NeoForge 客户端可运行；用户 HMCL 的 222 Mod 整合包仍必须验证，不能把用户实际看不到指令归因为没开作弊。
- 老版本历史与诊断记录保留如下，避免误以为已经完全修复全部整合包兼容。

---

# HMCL 安装及指令/生成蛋真实客户端验收 (v0.6.7)

本阶段目标是**解决用户截图中 `/npc` 根命令未知**。此前所有 GameTest 都是 GitHub 的服务器环境，不能证明 JAR 已经安装进用户 HMCL 当前实例。真实用户尚未确认已成功运行。


## 最新 NeoForge 与安装环境的验收门槛

- 2026-10-22 从 **官方 NeoForge Maven 完整列表** 查实 Minecraft 1.21.1 现有最高 21.1.262；之前 CI 构建用 21.1.248，已经升级为 **21.1.262**。源文件 `mod/gradle.properties` 声明 `neo_version=21.1.262`，成品 `META-INF/neoforge.mods.toml` 要求 `neoforge [21.1.262,)`。这是两个不同的 Mod 加载器版本，不能把 1.21.11 或 26.1 当作 1.21.1。**如果你的 HMCL 仍是 21.1.248，要升级至 21.1.262 后才能加载 0.6.7**；若整合包中的其他 Mod 限制了 NeoForge 版本，先做独立测试实例，不要贸然升级原存档。
- 本 Mod **没有**通过客户端伪命令代替游戏指令。实际服务器事件注册：`NeoForge.EVENT_BUS.addListener(NpcCommands::register)`，`NpcCommands.register(RegisterCommandsEvent)` 将 `remecheck` 和 `npc` 注册到 Brigadier `event.getDispatcher()`。事件回调内会记录 `COMMANDS REGISTERED: /npc=true, /remecheck=true`；玩家登录时还会检查服务端 dispatcher 是否包含 `npc`。客户端必须加入真正的集成服务器后才能看到此测试证据。
- 以前 0.6.6 CI 中的**真实客户端日志**含 `/npc registered=true`、`[Dev: Created player NPC: NPC]`，说明测试机可执行，**不说明用户 HMCL 选中的另一实例加载了相同 JAR**。如果你在自己的游戏里看不到命令，绝不能用 CI 绿灯来否认用户的问题。首先检查你当前实例 Mods 界面是否出现版本 `0.6.7-dev`。
- 已给安装脚本加 `-CheckOnly` 无修改诊断：能打印当前选定的 `mods` 文件夹、找到的每一份 REME JAR 文件名与 SHA256、最近日志修改时间、是否出现真实模组入口和指令注册证据。不猜 HMCL 的隔离目录。用户只需提供该实例的 `logs/latest.log` 或诊断结果即可进一步定位。
- 新独立 GitHub 工作流 `npc-command-client-smoke.yml`：使用 **NeoForge 21.1.262** 启动真实图形客户端，进入创造测试世界，以真实键盘输入 `/remecheck` 与 `/npc spawn`，并在真正的集成服务器日志强制断言两条注册、物品 `reme_studio:npc_spawn_egg` 和 NPC 生成确认。把这个通过与原来包含复杂挖矿的 GUI 故障分开，不让无关失败掩盖指令是否可用。**CI 是否通过必须读取该提交的最新日志；未通过前不能认定 0.6.7 可用。**

## 0.6.7-dev：不用命令也能生成 NPC（当前版本）
- **这是 NPC Mod，不是世界自带的指令**：对应 Minecraft **1.21.1 / NeoForge 21.1.262 / Java 21**，必须确认在游戏 Mods 列表看见 `REME MC Studio`、版本 `0.6.7-dev`。
- **创造模式**：背包「刷怪蛋」分类查找 `NPC 生成蛋（居民）`，或物品搜索 `NPC`；手持右键地面即可放出有本地家庭位置、背包、身份与 LIVE 作息的 NPC。**不需要作弊、OP、/npc 命令或管理员权限**。
- **生存模式**：工作台按 `EBE / BPB / EBE` 3×3 合成生成蛋（E=绿宝石、B=面包、P=纸），右键地面生成居民；真实消耗材料及生成蛋，不凭空刷人口。配方见 `data/reme_studio/recipe/npc_spawn_egg.json`。
- **可选命令**：聊天输入 `/remecheck` 或 `/npc` 可检查已注册，无需 OP；`/npc spawn`、`/npc demo` 以及建造/世界修改指令需要作弊或管理员权限。没有 OP 时不显示受限子命令是 Minecraft 原生权限过滤，属于预期行为。
- **如果连 `/remecheck` 和 `/npc` 都提示未知**：这一症状不能由「没开作弊」解释。先查看 Mods 列表是否有 `REME MC Studio`；如果没有，打开 HMCL 你正在启动的 **那个 1.21.1 NeoForge 实例** 的「游戏文件夹」，确认该文件夹的 `mods/reme_studio-0.6.7-dev.jar`，**不要放进相邻或未启用版本** 的 mods 文件夹。完整关闭游戏后用本 ZIP 的 `Install-REME-NPC.cmd` 重新安装，原旧 JAR 自动备份，不碰存档。
- **如果 Mods 列表有但还是未知**：打开**同一实例** 的 `logs/latest.log`，搜索 `REME NPC 0.6.7-dev`、`REME NPC: /npc present on dispatcher`、`COMMAND REGISTRATION MISSING` 和 Java 异常。把日志提供给开发者定位，不要用凭空推测代替证据。
- **测试范围**：NeoForge GameTest 可验证真正的 ItemStack.useOn 生物蛋生成、权限 0 的根指令存在，Linux Xvfb 图形客户端验证玩家登录、实际指令和物品注册；Windows PowerShell 安装 CI 只使用隔离的假游戏目录，**不能代替用户 HMCL 整合包环境**。

## 安装包（历史流程，以下 0.5.4 证据仅供追溯）
成功构建后，将 `mod/build/libs/reme_studio-0.6.7-dev.jar` 与 `packaging/Install-REME-NPC.cmd` / `packaging/Install-REME-NPC.ps1` 放在同一 ZIP 根目录。**完整解压**，关闭 Minecraft，双击 `Install-REME-NPC.cmd`，选择 HMCL 当前选中版本的“游戏目录”，明确输入 `INSTALL` 确认。

脚本：
- 自动解压/读取 JAR 的 `META-INF/neoforge.mods.toml`，验证 modId 必须是 `reme_studio`；
- 仅在用户明确确认所选目录后，扫描该目录 `mods` 下 JAR 的 **真实 modId**，将过去的 REME JAR 移到 `mods/REME-backup-时间`，不会删除个人存档；
- 安装新 JAR，验证 SHA-256 一致，并检查仅有一份 REME 安装；
- 提示重启当前实例，看 Mod 列表与登录消息；任何报错优先打开当前实例 `logs/latest.log`。

风险：HMCL 会有版本隔离，不能从桌面路径猜测正确 `mods` 文件夹。脚本**不会**擅自自动猜选版本，也不会修改任何世界存档。PowerShell 静态检查不是 Windows GUI 安装验收，用户必须确认目录。

## 游戏验收（最新 0.6.7 需要按上节额外验收）
1. 进入 HMCL 当前选中 NeoForge 1.21.1，Mods 页面能找到 REME MC Studio，日志应出现 `REME NPC 0.6.7-dev CONSTRUCTED`；进入单人世界后应出现 `REME NPC 0.6.7-dev COMMANDS REGISTERED`。注意两个日志含义不同。
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
