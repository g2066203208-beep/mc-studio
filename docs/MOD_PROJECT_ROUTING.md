# Minecraft Studio: 四套独立 Mod 项目路由 (2026-10-22)

用户明确要求：同一 mc-studio GitHub 仓库统一管理**无缝维度、攀爬、机械 Boss、NPC** 四个独立游戏项目。统一仓库并不等于统一 Mod ID 或统一 JAR，不能把这四种玩法塞进单个 reme_studio Mod。

## 源码审核后的真实状态

| 用户项目 | 目标目录 / 独立 Mod ID | 当前代码位置与完成程度 |
|---|---|---|
| WorldSeam 无缝维度 | mods/worldseam / worldseam | 已有独立子工程，源分支 feat/worldseam-v0111-cached-native-gpu-mesh。该项目已独立于 PEAK，含单独 Gradle/命名空间/JAR/CI，但分支尚未安全合入 main |
| PEAK 攀爬 | mods/peak / reme_peak（目标，未实施） | feat/peak-v11-animation-reset-walljump 仍使用历史 mod/ 工程及 reme_studio Mod ID，尚未真正独立 |
| 机械 Boss | mods/mechanicalboss / reme_mechboss（目标，未实施） | 网页原型在 viewer/rpms，原生控制试验 feat/rpmotion-native-aeronautics-g1 中有独立 mods/rpmotion (mod ID rpmotion)。这只是控制基础，不等于物理 Boss 已成为独立可玩的 Mod |
| NPC 智能居民 | mods/npc / reme_npc（目标，未实施） | fix/npc-neoforge-211262-runtime-v067 仍使用历史 mod/ 工程及 reme_studio Mod ID，必须迁移、重新测试 |

## 强制工程规则

1. 每个游戏项目独立 source root、Gradle 配置、Mod ID、入口 @Mod 类、资源/数据命名空间、CI、JAR、版本和 HMCL 安装说明；原则上用户能选择任意一个单独装，不装其他三个。
2. **禁止将 PEAK 和 NPC 的旧 mod/src 直接合并到 main 的历史 mod/src**。两者目前同为 reme_studio；同时安装会出现重复 Mod ID，且合并可能覆盖不同版本的 StudioMod/Gradle 配置。也不应把工作室主分支的 reme_studio-0.1.0-dev.jar 交给想测试 NPC 的用户：此骨架不包含 NpcCommands.class/生成蛋。
3. WorldSeam 是用户指定的唯一维度实现：仅在 mods/worldseam 项目维护。不能把它复制进公共 mod/ 或重新创建平行维度项目。
4. PEAK 专门处理抓握、跑跳、动作和 Sable 物理；NPC 专门处理居民、生活、职业、AI、社会；机械 Boss 专门处理 Create/Aeronautics/Sable 的真实结构、关节、动力及战斗。机械 Boss 可引用独立 RPMotion 技术模块，但不能伪称浏览器里的 Boss 原型为已经在 Minecraft 中运行的多体物理机器人。
5. NPC 0.6.7 NeoForge 21.1.262 在干净的 GitHub 客户端测试能识别 /npc、/remecheck、npc_spawn_egg，不能由此认定用户 HMCL 同时加载了旧 PEAK/Studio 的另一个同 ID JAR。解决用户两个入口都不存在时，优先核对实际 game directory、Mods 列表、JAR 和最新客户端日志；不能只根据源码注册形式断言可用。
6. 拆分 NPC 时要核对所有 reme_studio 命名空间：Mod ID、所有注册实体/物品/资源/配方、语言文件、数据包、日志和存档。不要未经迁移设计就更改实体注册 ID，冒充对旧世界无损兼容；需要保留旧世界时设计独立的注册表映射/数据迁移与回归测试。
7. 合并 GitHub PR 的目标是**四个独立子工程进入同一主仓库，仍各自产出不同的 JAR**，而不是把大量旧的平行实验 PR 无差别合并。合并前检查 diff 是否限定项目目录、是否替换别人的 StudioMod、是否有重复 Mod ID、CI 是否已通过真实 Minecraft 客户端测试。
8. 未来任何会话开始修复一项 Mod，必须先核对当前用户的工程边界、AGENTS.md、此文、相关 PR 分支和独立项目 README；main 未合入不能推断相应源码不存在。禁止私自另起一套重复系统。

## 迁移顺序

首先让 main 建立分离项目路由与可独立部署的产物索引。之后分别：将 PEAK 从旧 mod/ 迁入 mods/peak；将 NPC 0.6.7 从旧 mod/ 迁入 mods/npc（保留 54 GameTest 与真实指令/生成蛋验证）；将 Boss 浏览器原型与 RPMotion 物理控制接入新 mods/mechanicalboss 原生工程，并独立验收；只把 WorldSeam 的 mods/worldseam/ 子目录经审核安全合入 main。最后做 4 个子项目独立构建与多 JAR 并装冲突测试。

## 关联工程证据

- WorldSeam 独立化 PR #21: https://github.com/g2066203208-beep/mc-studio/pull/21
- PEAK 最新动作修复 PR #22: https://github.com/g2066203208-beep/mc-studio/pull/22
- RPMotion 原版航空学物理控制 PR #33: https://github.com/g2066203208-beep/mc-studio/pull/33
- NPC 0.6.7 客户端命令修复 PR #87: https://github.com/g2066203208-beep/mc-studio/pull/87

**本文件是隔离约束与真实状态核查，不表示 PEAK、Boss 和 NPC 的迁移已完成。**
