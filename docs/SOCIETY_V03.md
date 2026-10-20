# 活世界 NPC 0.3 — 社会结构与行动测试

**基线：** Minecraft Java 1.21.1 / NeoForge 21.1.248 / Java 21。独立分支 `feature/living-world-society-v03`，基于 `feature/player-npc-v1`；本阶段不要直接合入 main。

## 有代码实现的系统（编译通过也不代表游戏运行通过）

- **社会身份：** `SocietyRules.Identity` 记录 Nation（COMMONWEALTH / FRONTIER / OUTLANDS）、Faction（SETTLERS / MERCHANTS / RANGERS / RAIDERS）、region（小写 ASCII 标识，最多 32 字符）。现有关系是**写在代码中的初始外交规则**：袭击者与普通居民敌对，同地区和同国更容易友好。尚无真正国家政府、领土边界与动态外交存档。
- **人生目标：** `LifeJourney` 支持 FEED_COMMUNITY / ESTABLISH_TRADE / EXPLORE_LANDS / DEFEND_SETTLEMENT / BUILD_A_HOME / LIVE_PEACEFULLY。只有满足事件条件的成功行动才增加进度，上限 100。目标影响普通居民是否自发探索或执勤，但尚无完整目标规划树与人生阶段。
- **道路：** `RoadNavigator` 仅在局部已加载范围扫描原版 dirt path、gravel、cobblestone、stone bricks、mossy cobblestone，挑选朝目标前进的道路方块，再使用原版导航。不是跨地图道路图搜索、也不是保证 NPC 永远走道路。
- **守卫、护送：** GUARD 检测附近有视线的敌对派系 NPC / Monster，使用原有近战 AI；实际命中才增加保护目标进度。没有见到敌人时回工作锚点巡逻；可命令护送某个玩家。防御玩家攻击时沿用原有恐惧逃避，**不会默认攻击玩家**。
- **探索、收集：** SCOUT 只探索已经加载的区域，探索记录最多 64 个 Chunk 坐标，收集近距离没有绑定所有者的真实掉落物。目标位置不会强制加载新区块。不是自动生成冒险副本、采矿或探查未加载世界。
- **物流：** 仓储对象仅允许管理员用 /npc setstore 对着 6 格内的**已加载原版箱子**明确授权；NPC 搬运真实食物/农作物与取出小麦。仅对已指派 NPC 生效，不自动连接全世界箱子；物资转移使用临时背包快照检查空间。
- **实际贸易：** 面包师可用 1 个实际面包和玩家的 1 绿宝石交换（玩家持有绿宝石右键面包师，要求背包空间）；既有 NPC 面包师—农民换麦子逻辑仍保留。
- **建筑：** 管理员主动执行 /npc buildstart 后，BUILDER 在已清空、已加载、无实体占用的 5×5×3 位置按固定小屋蓝图逐块放置实物圆石墙与木板屋顶，留入口；材料必须来自其背包。/npc buildstop 可暂停。**不进行任何自动拆除、挖掘或建筑迁移**。没有领地保护 Mod 适配，只在隔离测试世界操作。
- **持久化：** NPC 的国家、派系、地区、人生目标/进度、探索访问记录、箱子锚点、护送对象 UUID、施工许可和进度存入实体 NBT；不强制加载远处区块。

## 新增指令（所有作用于附近 16 格最近的属于你的 NPC）

| 指令 | 功能 |
|---|---|
| `/npc demo` | 6 个不同职业演示居民：2 农民、1 面包师、1 守卫、1 侦察员、1 运输员 |
| `/npc info` | 显示国家、派系、地区、目标、目标进度、库存与当前行动 |
| `/npc nation COMMONWEALTH` | 选择国家；FRONTIER、OUTLANDS 可选（需要权限 2） |
| `/npc faction RANGERS` | 选择阵营；SETTLERS、MERCHANTS、RAIDERS 可选（需要权限 2） |
| `/npc region river` | 地区标签（需要权限 2，不自动主张领地所有权） |
| `/npc ambition EXPLORE_LANDS` | 改变 NPC 长期目标 |
| `/npc guardjob`、`/npc scout`、`/npc courier`、`/npc builder` | 指派职业 |
| `/npc escort`、`/npc unescort` | 给附近自己守卫指派护送任务或取消 |
| `/npc setstore` | 管理员看着原版箱子给附近自己 NPC 指定仓库 |
| `/npc setwork` | 把自己的当前位置设成附近 NPC 的工作锚点 |
| `/npc buildstart`、`/npc buildstop` | 管理员授权/暂停固定蓝图小屋施工 |
| `/npc live` | 返回生活模式 |

## 推荐测试方法

1. **创建独立新世界 + 备份**，使用 `/npc demo`；确认 6 个职业正确显示、日程与需求更新。成熟农田与工作台需要真实摆设。
2. 指派一个空地作为建造中心：站在预定位置输入 `/npc setwork`，走出 5×5×3 的施工空间，确保在附近 NPC 16 格范围内，对应建筑工背包提供不少于 **30 圆石 + 25 橡木木板**，执行 `/npc buildstart`。测试阻挡时禁止覆盖、建材耗用、退出重进后的施工进度。
3. 给农民或运输员指派原版箱子（`/npc setstore`），放进符合规则的真实物资，观察物流与消耗前后物品总量。
4. 在安全测试区域创建不同阵营 NPC（先用 `/npc faction RAIDERS`），观察友军不能互殴、守卫与敌对目标作战、伤害才增加使命目标进度。
5. 给守卫 `/npc escort`，沿泥土小径前进，观察其在目标接近、道路和受攻击时的动作；与附近人互动、完成目标，退出世界再进入核对记忆/目标进度。
6. 测保存/加载、客户端、专用服务器、真实整合包 222 个 Mod 与性能；这些需要单独的游戏内证据。

## 明确尚未实现

- **采矿与采石、野外建造、自动钓鱼、跨区域队伍、国家领土与外交治理、人口增长、家庭婚姻、完整队伍编制、资源请求系统、完整自由商业市场、远处离线模拟、语言 AI。**
- 采矿/任意建造要通过 NeoForge FakePlayer 与 BlockEvent 的权限检查，避免绕开其它 Mod 的领地保护。官方说明：https://docs.neoforged.net/docs/1.21.1/blocks/ 。未经这一步的简单 `setBlock`/破坏动作不能标记为生产级别。
- 游戏内运行、性能与兼容性**尚未实测**。GitHub CI 编译和纯 Java 单测是必要但不充分的证据。

## 开源技术参考（没有复制源代码）

- [MCA Reborn](https://github.com/Luke100000/minecraft-comes-alive)（日程、记忆、心情、关系、VillagerBrain）。
- [MineColonies](https://github.com/ldtteam/minecolonies)（CitizenAI、工种、建筑、运输、巡逻）。
- [VillAIgence](https://github.com/True-Ruslan/villAIgence)（对话记忆、受限动作）。
- 已检查上述三个项目仓库许可证均为 GPL-3.0；当前代码独立编写，并未直接导入其受约束的源码。


## v0.3 后续增量（同一任务分支）
- `/npc squad`：附近最多九名属于你的守卫/侦察员组成护送小队，按队长朝向在其后方分散成三列。成员的阵型编号持久化，`/npc disband` 解散。仅加载区块内的有限编队；不会跨维度强制跟随。
- 运输员设置仓库后，可寻找同地区的非敌对农民，实际领取最多六个多余小麦（保留农民至少三个），背包交易完成后运送至已指派箱子。所有调拨需要真实物资。
- 国家与关系影响实际行为：面包师不会与敌对派系农民交换，小村民更倾向向熟悉、信任的同伴社交；如果玩家曾严重攻击面包师，面包师会拒绝向他出售面包。
- 角色长期目标会驱动未特定分工居民参与侦察/守卫；实际打中敌人，而不是单纯锁定敌人，才可计入保护任务目标。
- 上述逻辑当前仍无真实 Minecraft 客户端/服务端游戏验收；纯编译通过只能证明 Java API 兼容。

### 进一步的研发工单
后续需要严格通过 FakePlayer/权限管线做 **安全挖矿、钓鱼和自由蓝图建造**，以及真正的国家疆域/动态外交/远处离线模拟，见 [Issue #13](https://github.com/g2066203208-beep/mc-studio/issues/13)。不能声称这些已实现。

## 云端测试证据（2026-10-21）
- 推送源代码提交 `e05f765e65de7234e49ac83b3b0abc999c284310` 的 [GitHub Actions #39518033958](https://github.com/g2066203208-beep/mc-studio/actions/runs/39518033958)：**SUCCESS**，包括 Test living-world planner core、Gradle Build 与 artifact 上传。
- [PR Actions #39518039426](https://github.com/g2066203208-beep/mc-studio/actions/runs/39518039426)：**SUCCESS**。
- ZIP artifact ID：`12310085514`，JAR 文件：`reme_studio-0.3.0-dev.jar`；已解压并检查包含 `SocietyActions`、`SocietyRules`、`LifeJourney`、`SupplyLogistics`、`SafeBuilder`、`RoadNavigator`、`NpcCommands`、`PlayerNpcEntity` 和 `neoforge.mods.toml`。
- JAR SHA-256：`5f480e86d8f2808c52b41662de1a5cf362c3226569099a0f7eead88d2e5eb3f3`。
- 验收格：源代码编译 YES；独立规划单测 YES；Minecraft 客户端 NO；专用服 NO；仓库多人模拟 NO；原 222 Mod 整合包 NO；真实性能 NO。**不将本 PR 合入 main。**
