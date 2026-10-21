# REME 活世界 NPC 0.5.9-dev — 自涌道路、居民聚落、跨仓商队、盗匪与旅行路线

**Minecraft Java 1.21.1 / NeoForge 21.1.248 / Java 21；开发分支 `feature/npc-emergent-roads-hamlets-v059`；不是生产版。**

## 玩法北极星

不是“村民周围随机生成一栋房子”的假文明，也不是 200 个 NPC 疯狂刷新。玩家能够长期观察一个真实因果链：

1. 居民的真实生活路线往返农田、家与聚落中心，穿草而过。每块位置被不同居民实际踩过，地面通行热度缓慢增长。
2. 居民选择走熟悉、安全、物理可行的路线，越常走越形成共同交通。**道路即行为留下的后果，反过来影响行为**。自然地块在管理员授权后可真正形成 Minecraft 草径；其余只保留非破坏性路线记忆。
3. 三个不同家庭的实际住所聚集、同阵营互相信任，获得当地“聚落”的社会识别；记录首次成立时间。居民会在适当时段尝试走到共同中心会面，不强制生成世界方块或额外人口。
4. 运输员有管理员批准的两座原版箱子：从源仓库获取真实小麦、装进其 36 格实际背包、利用道路前往目的仓库、存入小麦。缺货/塞满/区块未加载都不会创生物资；运输任务可以跨 NBT 存档继续。
5. 敌对 RAIDERS 阵营会局部追踪载有真小麦的 COURIER，在实际近战距离抢走最多两份真小麦。盗匪的战利品进入自己背包，运输员受惊、记住对方，目击者独立记下 THEFT，守卫可按原有敌对阵营机制防卫；事件冷却跨存档。

**绝不声明以下已经完成**：独立村庄建筑蓝图、婚育人口增长、自动领土国家外交、警队抓捕审判、自动建桥、独立完整商品经济、任意模组机器运输、玩家口袋抢劫、离屏旅途模拟、万级人流性能、被保护的客户端存档兼容测试。这些都需要下一阶段单独实现。

## 代码系统与安全边界

### A. 行人流量（`FootTrafficData`, `EmergentFootpaths`, `RoadNavigator`）

- 每维度独立 `SavedData`，上限 **4096 个路面格**；保存绝对方块位置、最多 1000 次累计通行、最后通行 NPC UUID/世界 tick、是否实体路面已经成形，禁止无限制世界扫描。定期采样真实站立 NPC 的不同脚部坐标；原地待着与出生不计。短时间同一 NPC 反复踩同一格不重复刷分。
- **7 次有效脚步**才触发物理路径判定。 `RoadNavigator` 即使未开启世界编辑，也会把 3 次以上人流地块作为优先路径节点。
- 默认物理路径 **OFF**。OP 执行 `/npc trails on`；要求 `mobGriefing=true`、服务端同线程、原版世界边界、已加载区块、站立居民、目标附近 3 格、露天两格空气、天然草地/泥土且无方块实体；只通过真实 FakePlayer 铲子右键 `ItemStack.useOn` 进行路径变化，尊重 NeoForge `BlockToolModificationEvent` 的取消。铁轨、农田、农作物、箱子、机器和木石建筑全部不是目标。尝试失败不强行改方块。
- `/npc trails off` 关闭地表修改但保留路网记忆。注意：只有管理员可更改地表选择，仍需在隔离存档中与用户整合包保护 Mod 单独做兼容验收；不要以一项取消事件测试推断所有权限实现都受支持。
- **设计理由**：玩家真正走出的路是不规则的渐成小径，而不是“随机 100 格石砖全铺”。极简现实反馈需要慢、局部、有物理场景原因，不使用全局 A* 重算世界。

### B. 自发聚落（`EmergentSettlements` 和 `SettlementLedger`）

- `/npc settlement`：只调查附近实际加载的 NPC，要求 3 个**有独立家园坐标**、实际存活且拥有同国同派系同区域身份的居民，且家园在 32 格内。3+ 为 hamlet（聚落）、6+ 为 village、12+ 为 town，敌对 RAIDERS 的聚集识别为 band。不会使用不存在的离屏家庭来虚报人口。
- `SettlementLedger` 仅保存实际观测到的 128 个聚居事件：首次创立 tick、最初中心、家庭数量、文化/派系和最近观察时间。为了防止离屏区块人口污染，当前不会假装保存所有离屏实体运行状态或创建新居民。
- 处于 SOCIALIZE 意图、有精力且附近能安全到达中心的普通居民，低频尝试赴聚落会面。当前中心通过已加载实际家庭住所求均值，不会传送 NPC 穿墙。
- `/npc demo` 的六名居民里至少有三个同阵营、不同家庭住所，因此可真实满足第一个小聚落的条件。

### C. 真实双仓物流（`CaravanRoutes`）

- 对 16 格内自己的 COURIER，管理员看着真实原版箱子执行 `/npc route pickup`，再看另一个箱子执行 `/npc route dropoff`。两个仓库必须在运输员家园权限范围内、所在区块已加载，且不能同一箱子。 `/npc route` 查看状态；`/npc route clear` 停止已授权物流，但留存运输员背包的所有实际货物。
- 单趟最多 8 小麦；临时复制两个实体库存，以全量插入成功为交易成功条件，然后同时更新两个实际容器；源不足只取存在的部分，目的满不清空货物。只在靠近箱子时装卸，长距离调用已有局部道路偏好+原版导航。箱子与路线本身不会强制加载新区域。
- 一旦装上货，敌人可以袭击；运输员遭劫后只会向目的地运送**剩余的真货**。跨 NBT 目标和物品自动恢复。

### D. 货物抢夺与犯罪后果（`BanditActions`）

- 当前不引入大规模随机强盗刷怪，需先有真实管理员或未来规则生成的 `RAIDERS` NPC。每 40 tick 局部扫描半径 12（不查询全图）；只能追逐敌对、实际携带小麦的运输员，且沿路靠近到约 2 格、有视线且高度差不超过 1.5 格才能执行。
- 每次抢 1–2 实际小麦；先在双方临时背包模拟，无接受空间则拒绝，避免非事务性部分插入造成复制。
- 受害者出现恐惧、低信任与本地司法目击，附近真的看见了事件的人才有直接证据。盗匪冷却 400 游戏 tick（NBT 保存），犯罪是有限资源转移，不是玩家口袋被随意偷。
- 需要后续实现保护联盟的护卫委托、守卫警报、追踪、谈判、赃物流向/追回，以及更复杂的抢劫策略；当前只是最小真实对抗闭环。

## 具体玩家操作（独立测试世界）

```mcfunction
/npc demo
/npc settlement
/npc trails
/npc trails on
/gamerule mobGriefing true
/npc trails off
```

选择一位自己的运输员，先看着有小麦的原版箱子输入 `/npc route pickup`，再看着另一座空原版箱子输入 `/npc route dropoff`，最后用 `/npc route` 查看任务。放任其走路，原版箱子与 NPC 背包里的货物应实际减少/增加。若要观察抢劫，在独立存档用 `/npc spawn Bandit` 后对最近自己的这名 NPC 使用 `/npc faction RAIDERS`，将运输员设成 COURIER 并放入真小麦，让双方在可视的路线上接近；实际代码事件规则以此文和 GameTests 为准。**不推荐在正式生存世界开启物理路径或模拟劫掠。**

## GameTest 验收与后续探索

本阶段 GameTest 应覆盖：计数防原地刷路、阈值/离屏保存、默认关闭、真实草径形成、农田保护、NeoForge 铲子取消事件、三个真实居民聚落、敌对不算村民、聚落 founding NBT、抢劫真实货物守恒/目击与有限 cooldown、目标满载不失物、真实双仓运输寻路与保存、此前采矿背包功能回归。**最终通过数和真实链接只能在 Actions 完成后填入**。

学习来源：
- [MineColonies](https://github.com/ldtteam/minecolonies)：需求驱动职业、仓库/运输员与护卫机制，参考公开代码结构与游戏设计；**未复制源码**。
- [Millénaire NeoForge 1.21.1](https://github.com/Leviaria/Millenaire)：文化村落、商贸、名誉与袭击社会后果；GPL-3.0，不复制其代码。
- [Desire Paths Agent-Based Simulator](https://github.com/AbelVM/desire-paths)：群众足流量塑造环境的思想，不复制实现代码。
- [NeoForge 1.21.1 SavedData](https://docs.neoforged.net/docs/1.21.1/datastorage/saveddata/) 和 [BlockToolModificationEvent](https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/event/level/blockevent.blocktoolmodificationevent)：真实 API。严禁通过裸 `setBlock(DIRT_PATH)` 规避权限检查。

## 下一轮专业工程 Prompt

> 你是资深 Minecraft 1.21.1／NeoForge 21.1.248／Java 21 游戏 AI 和程序化生活模拟工程师。先读取 AGENTS.md、docs/HANDOFF.md、docs/NPC_PLAYER_PARITY_MASTER_PLAN.md、本 v0.5.9 研究和实际 PR/CI。不要合并未验收 main，不复制上游 GPL 代码。以游戏内自主可观察因果关系为优先：居民的常走路线→FootTrafficData→地貌反馈→道路寻路；独立家庭→可见群体与真实物品需求→聚落形成；原版箱子实际货物→商队运输→抢夺/损耗/司法证据→守卫/恢复。每一步必须有真实 Minecraft/NeoForge GameTest 和 Xvfb/Openbox 真客户端单人世界截图，专门测作弊、越权、取消、地形边界、区块卸载、物品守恒、NBT 重启、200 人 MSPT。不要单凭“命令成功”宣称实际玩法成功。后续优先：旅行商人 2+ 定居点交易、过夜旅店、家庭/生育/招募资源制约、季节饥荒/瘟疫事件、桥梁道路施工材料消耗、领地外交与集市治安；UI 用中文，让玩家无需背命令，并明确每个未完成项。


## 2026-10-21 真正的测试结果与失败复盘

- **来源提交** `6d91d96dc2c64eee4d2cd2a4d923198c55303cd8`（道路/聚落/跨仓运输/劫掠），其对应 [NeoForge 34/34 GameTest](https://github.com/g2066203208-beep/mc-studio/actions/runs/39614384093) **SUCCESS**；[Build](https://github.com/g2066203208-beep/mc-studio/actions/runs/39614385088) **SUCCESS**；[Windows 独立 HMCL 安装器模拟](https://github.com/g2066203208-beep/mc-studio/actions/runs/39614384222) **SUCCESS**；[真 Minecraft GUI 运行 39614376551](https://github.com/g2066203208-beep/mc-studio/actions/runs/39614376551) **SUCCESS**，21+ 原始截图、游戏日志 artifact [12352925193](https://github.com/g2066203208-beep/mc-studio/actions/runs/39614376551/artifacts/12352925193)。
- **真实世界画面**：`client-gui-organic-hamlet.png` 证实 `Organic hamlet | 3 real households | COMMONWEALTH/SETTLERS | meeting hub ... | chronicle settlements: 1`；`client-gui-traffic-status.png` 表示 `74 recorded foot tiles` 且 `physical path shaping OFF`；`client-gui-trails-enabled.png` 实际显示管理员选择 ON；`client-gui-loot-verified.png` 验证采矿后 NPC 背包实体 NBT 中含 1 份 `minecraft:cobblestone`，集成服务器硬断言 `REME_NPC_LOOT_IN_BACKPACK`。**GUI 没有证明自然路径在这个画面上已经形成可见草径**；真正用铲子形成草径/阻止模组取消的证据是服务器 34 项 GameTest。
- **失败复盘**：最初双仓库商队 34-test 在 [39612324522](https://github.com/g2066203208-beep/mc-studio/actions/runs/39612324522) 为 32/34（商队路径被通用生活 AI 抢占并尝试站进箱子、另一项随机的采矿拾取回归失败）。通过寻找箱子附近真空地、近距离 vanilla navigation 及商队任务持有调度权修复。另外商队可能在 160 tick 内跑完多次运输，旧测试僵硬要求「恰好卸货 8 且背包为零」误判**真实的额外往返**；新断言仍要求 source+bag+destination 的世界小麦严格为10、至少真实投递8份、parcelsDelivered>=1。此后同源码 34/34 通过。一次真实图形客户端 [39614036870](https://github.com/g2066203208-beep/mc-studio/actions/runs/39614036870) 曾在沿海随机测试世界未收回矿物而失败；后一轮 GUI [39614376551](https://github.com/g2066203208-beep/mc-studio/actions/runs/39614376551) 成功，重复测试可靠性仍需继续复核。
- **新功能补强（这些是以后提交，不能拿此前 34/34 当验证）**：注意到最初记录成立历史仍依赖玩家 `/npc settlement` 或居民 `SOCIALIZE` 才触发，已新增 `EmergentSettlements.observe` 在所有 LIVE 真实家庭（农民、面包师、运输员等）每 240 tick 自主登记聚落，无需玩家命令。额外 GameTest `actual_different_professions_found_hamlet_without_player_command` 真正让三个不同职业 NPC 在测试世界运行 275 ticks，验证 SavedData 创建历史，而非直接调用登记。客户端脚本也增加三个严格硬断言：真实 3 户聚落、默认 OFF 路网、管理员 ON 路网的游戏日志回执。**此功能的最终 35/35 和最新 GUI 需以运行结果为准，不得提前写成功。**
- 构建实测 v0.5.9-dev 产物：JAR `reme_studio-0.5.9-dev.jar` 208883 bytes SHA256 `bd4fcad99d9937a4c95f279964101fcfbb4db38920764ab9ee90b79a122147bc`，单独可解压 HMCL 安装包包含 JAR/Install-REME-NPC.cmd/ps1/README，ZIP SHA256 `ae553a076de4e8f1525923811ad7ae645183710dc48d4453369faac986a8dc6e`；这是 6d91d96 验证源码 JAR，后续源码改动后的构建需另核对。
