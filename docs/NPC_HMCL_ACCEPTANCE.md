# HMCL 真正安装/客户端验收门禁 (v0.5.4)

本阶段目标是**解决用户截图中 `/npc` 根命令未知**。此前所有 GameTest 都是 GitHub 的服务器环境，不能证明 JAR 已经安装进用户 HMCL 当前实例。真实用户尚未确认已成功运行。

## 安装包
成功构建后，将 `mod/build/libs/reme_studio-0.5.4-dev.jar` 与 `packaging/Install-REME-NPC.cmd` / `packaging/Install-REME-NPC.ps1` 放在同一 ZIP 根目录。**完整解压**，关闭 Minecraft，双击 `Install-REME-NPC.cmd`，选择 HMCL 当前选中版本的“游戏目录”，明确输入 `INSTALL` 确认。

脚本：
- 自动解压/读取 JAR 的 `META-INF/neoforge.mods.toml`，验证 modId 必须是 `reme_studio`；
- 仅在用户明确确认所选目录后，扫描该目录 `mods` 下 JAR 的 **真实 modId**，将过去的 REME JAR 移到 `mods/REME-backup-时间`，不会删除个人存档；
- 安装新 JAR，验证 SHA-256 一致，并检查仅有一份 REME 安装；
- 提示重启当前实例，看 Mod 列表与登录消息；任何报错优先打开当前实例 `logs/latest.log`。

风险：HMCL 会有版本隔离，不能从桌面路径猜测正确 `mods` 文件夹。脚本**不会**擅自自动猜选版本，也不会修改任何世界存档。PowerShell 静态检查不是 Windows GUI 安装验收，用户必须确认目录。

## 游戏验收
1. 进入 HMCL 当前选中 NeoForge 1.21.1，Mods 页面能找到 REME MC Studio，日志有 `REME NPC 0.5.2: MOD CONSTRUCTED`（基于 v0.5.2 源码的日志文案）。
2. 新建可作弊创造模式测试世界，进入时应该有 REME NPC 系统消息。
3. 不需要作弊的 `/remecheck` 必须报告 `/npc registered=true`。
4. `/npc spawn` 必须生成可见的 NPC，空手右键对话，Shift 空手右键打开 36 槽背包。
5. `/npc demo` 必须真实创建六人。挑一个农民，给它成熟农田/食物，再观察 `/npc info`。
6. 停止加载、存档重启后 `/npc info` 和人格/NBT 正常。必须实际核对，不以单纯服务器 GameTest 代替。
7. 再测自然生成、20/50/100 NPC TPS、其他模组兼容。未完成测试前保持 PR 草稿。

## 开源参考
研究具体源码见 `docs/OPEN_SOURCE_NPC_CODE_AUDIT.md`，GoalScheduler、Kithkyn RoamGoal、MCA Reborn VillagerBrain、MineColonies TickRateStateMachine，不抄代码，仅取任务生命周期/导航状态/失败恢复的设计。
