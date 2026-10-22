# REME NPC 0.6.5 — 真实墙钟时间的 50 / 200 NPC NeoForge 压测

## 为什么需要这一层

基础分支 `feature/npc-mutual-aid-longrun-v065` [PR #78](https://github.com/g2066203208-beep/mc-studio/pull/78) 已进行 24 名居民、2400 个真实 Minecraft 世界 tick 的 GameTest。其 GitHub 日志明确显示 `REME_NPC_LONGRUN ... observedWorldTicks=2400 ... wallSeconds=3.53`，因为 **GameTestServer 为加速测试会以远高于 20 TPS 的速度推进世界**。这证明经过 2400 次真正 AI/世界逻辑运行，但绝不能冒充两分钟真实墙钟游玩。

本独立分支 `test/npc-realwall-soak-v065` 补上这个验收漏洞；不重新实现居民，不影响已验证的 #78 和主分支。

## 真长时间自动化

- `.github/workflows/npc-real-time-soak.yml` 在新分支推送时自动以矩阵运行 **50** 和 **200** 名**真实已加载** NPC 的 NeoForge 游戏服务器。两种负载各自在独立的临时 GameTest 世界和 48×24×48 隔离结构里执行，避免并发样本交叉影响。
- 专用 `NpcSoakGameTests.real_wall_clock_loaded_npc_population_soak` 通过 `JAVA_TOOL_OPTIONS` 显式开启，其他普通 PR/GameTests 无额外五分钟开销；关闭时测试立即通过，属于验证门控，不能将其算作性能绿灯。
- 开启后用 `System.nanoTime()` 保证**每组至少 300 秒真实墙钟**，且累计至少 12,000 个世界 tick。没有用 `Thread.sleep` 阻塞游戏服务器或人为循环执行 AI。
- 真实注册 50/200 个不同 UUID 的 `PlayerNpcEntity`，分布在隔离试验场的真实石质地面，LIVE 日程、NPC 社会聚落、寻路与时间轴按原版服务端每 tick 运行。禁止从没加载的地图强制读取或借用户存档测试。
- 每 20 tick 从原生 `MinecraftServer.getTickTimesNanos()` 的环状记录按采样读取，最多保存 8192 项；输出 `REME_NPC_SOAK_PROGRESS`（每 60 秒）与 `REME_NPC_SOAK_RESULT`（人数、墙钟秒数、实际世界 tick、存活数、需值变化、原生 tick 样本 p50/p95，单位毫秒）。这是**所采样的服务器 tick 耗时分位数**，不是完整 222 Mod 整合包、网络玩家模拟或正式 F3 render FPS。
- 在最后通过条件中严格检查：人数全部存活、每人至少一次真实需求变化、每个实体 UUID 唯一、单人关系数据不超128且 tick 样本 >=50。暂不采用某个固定 p95 性能阈值作为自动断言：先收集两个真实负载的环境基线，比较多次运行的波动，再为硬门槛选择公平的性能目标。
- GitHub Actions 工作流内用 `tee` 保留完整服务器输出，`grep` 必须看到每组实际 `REME_NPC_SOAK_RESULT` 和所有游戏测试通过，否则失败；即使失败也上传日志作为故障证据。

**限制**：这仍是无真人连接、无客户端图形渲染的真实 NeoForge **服务端**压力测试。200 个居民集中在约 44×44 的密集试验场，而不是可直接代表城市路网分布、跨区块迁移、玩家视距或 222-mod 整合包；单次样本不足以得出最高端性能结论。五分钟运行成功后再加入卸载/回载、长时间多夜、多人网络/多 Mod 与原生 ServerPlayer 人口测试。

## 资料与实现依据

- [官方 NeoForge 1.21.1 GameTest 文档](https://docs.neoforged.net/docs/1.21.1/misc/gametest/) `GameTestHelper.onEachTick`、超时、游戏环境的区别。
- [Mojmap 1.21.1 MinecraftServer](https://mappings.dev/1.21.1/net/minecraft/server/MinecraftServer.html) `getTickTimesNanos()` 的原版服务端时钟记录。
- [MineColonies 真实开源项目](https://github.com/ldtteam/minecolonies)，参考生产级人口规模与实际供需循环的思路，未复制任何 GPL 源码。

## 完成与否判定

- 提交和 CI 启动不等于 50/200 压测成功。必须打开相应新工作流并核对两组 `REME_NPC_SOAK_RESULT` 行，尤其是 `realWallSeconds >=300`，及 `All ... required tests passed`。若 Maven 或 runner 受限，逐项区分环境失败和世界/功能/性能失败。
