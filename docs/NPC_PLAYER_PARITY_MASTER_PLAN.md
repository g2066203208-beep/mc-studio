# REME NPC｜玩家等价能力与活世界工程总规划（2026-10-21）

> **单一事实来源**：Minecraft Java 1.21.1；NeoForge 21.1.248；Java 21；真实 GitHub 独立分支开发；本仓库代码为产品，本文只是规范。不要把计划功能标记为已实现。

## 产品北极星／可玩验收

一个自然生成、有名字和记忆的 NPC，从远方走回住宅，领工具、从合法矿点按原版玩家规则挖出方块，掉落真实物品，搬运到真实箱子；然后用原版配方合成／进食／战斗；日暮睡觉，被攻击打断工作，危险消退恢复；即使玩家不输入命令也能连续执行；重启后个人身份、物品、长期任务依然正确。另一个玩家在旁边能够像看玩家一样看到皮肤、服装外层、装备、挥臂、粒子与世界资源变化。

**不等于** 将 `PathfinderMob` 继承类名改为 `ServerPlayer`。真正的 ServerPlayer 依赖玩家登录、连接、游戏模式、同步及实体派发；不要创造上百个伪在线玩家作为全能替代。保留可导航/自然生成的 NPC 实体，独立 `PlayerCapabilityAdapter`：使用 NeoForge FakePlayer 作为受限服务器动作执行身份，世界修改尽量走原版玩家交互流水线；绝不把 FakePlayer 误称为真实在线玩家。

## 模块分层（松耦合，因果可测）

1. **Identity / Persistence**：UUID、性格、住所、工作台、技能、动机、双层自绘皮肤编号、人口与社会关系。NBT 用于实体；SavedData 用于村落经济/离屏事件；任何加载恢复不能复制资源。
2. **Perception / Local Memory**：仅感知可见、已加载区块；危险、天气、相邻实体、实物库存、事件来源，采用限额与过期清理。
3. **Planner / Task Lifecycle**：日程规则+生存需求+高优先级危险打断，单一活动定义 start/tick/stop、原因、失败回退、指数退避、进度看门狗；路径永不无限加载区块。
4. **Player Action Adapter**：`look / walk / equip / use / break / place / attack / interact / transfer / craft / eat` 小接口，返回真实 receipt（物品/方块/目标变化、取消原因）；服务端同线程、短距离、世界边界、领地权限事件与事务化消费。各项使用原版行为但不可承诺对任意第三方模组全兼容。
5. **Presentation**：原版 PlayerModel 比例、可同步模型状态、绑定每位 NPC 自绘 64×64 皮肤、二层纹理（至少原版膨胀 outer mesh；逐像素真实 3D mesh 另外验收）、真实渲染截图与多帧动作。
6. **Validation / Performance**：静态编译＋纯 Java 决策测试＋实际 GameTestServer 方块/库存/事件/存档测试＋真实客户端进入世界/截图（逐步画面，不是只抓主菜单）＋ Windows 安装器模拟＋ 222 Mod 整合包隔离验证＋ 20/50/100/200 NPC TPS/内存（实测才能出数字）。

## 优先级、可执行交付切片和出闸标准

| 版本切片 | 核心能力 | 客观完成条件 |
|---|---|---|
| P0 0.5.6（已有） | 玩家物品放置；守卫巡逻 | 17/17 GameTests；不能称所有玩家功能完成 |
| **P1 0.5.7（本轮）** | 玩家规则挖掘（原版工具、耐久、实际掉落、破坏事件、禁止未授权/超距/未加载区块、采矿耗时）、独立动作执行层 | 场景内石头变空气，正确工具耐久变化，真掉落，无工具/禁止方块不变；取消事件能拦截；全过程不直接 `level.destroyBlock` 旁路权限；新 GameTest 全部通过 |
| P2 0.5.8 | 真实 36 格背包+装备副手/盔甲／死亡物品转移、拾取、工作台原版配方、箱子权限 | 存档、跨服务端重启、物品守恒；未经证明不能称模组方块通用 |
| P3 0.6 | 智能职业：自主矿工、农民、木匠/运输；POI 就业；任务超时/重试/危险打断恢复 | 不输入命令让 NPC 完成连续生产闭环、两日行为、存档、资源不复制 |
| P4 0.7 | 关系法律经济、家庭建筑、居住容量、离屏低频世界台账 | 真实交互与记忆影响交易/迁移/法律且可持久恢复 |
| P5 0.8 | 自画原创 64×64 双层皮肤、NPC 逐像素立体外层/眼部帧、关节、盔甲兼容 | 真人游戏客户端六人多视角截图+动画多帧，不能仅靠安装 3D Skin Layers 宣称 NPC 已适配 |
| P6 1.0 gate | 真实多人/222-Mod 兼容、TPS 与内存、安装/回滚、异常存档安全 | 多小时隔离存档和实际 TPS 基准、持续构建、无已知灾难性世界修改/复制漏洞 |

## P1 细化：玩家挖掘事务

- 玩家/操作员明确指定已加载目标（仅自有 NPC，限制范围与目标），NPC 走近到 6 方块，必须持有正确实体主手工具；目标属于保守可挖掘白名单且不含方块实体/不可破坏方块。
- 每个服务器 tick 计算 `BlockState.getDestroyProgress(FakePlayer, ServerLevel, BlockPos)`（由原版方块、正确工具及生存条件给出），进度到 1.0 才调用 `FakePlayer.gameMode.destroyBlock(target)`。此调用承载 NeoForge `BlockEvent.BreakEvent`、`HarvestCheck`、原版掉落/物品耐久；不得通过 `setBlock(AIR)` 作弊。
- FakePlayer UUID 来自该 NPC、每维度缓存；在动作期间临时装备 NPC 的**复制**主手工具。动作结束回写成功使用后的工具、恢复 FakePlayer 前一状态，防多 NPC 共享物品或假玩家产生存档污染。关注 FakePlayer advancement listeners 对应长期内存问题。
- 操作失败时保留世界、工具、目标；清除屏幕挖掘进度；事件拒绝须被承认，不允许重试绕过。退出/加载对目标进行重新校验（取消或安全复位）。
- 所有危险/实体失活/离开活动半径/超距/未加载区块不允许继续破坏；难度测试使用原版石头+铁镐（不能白拿圆石），禁止靠创造模式瞬间破坏。
- 正式自动采矿不允许无主 NPC 随机拆玩家房屋；自主行为必须绑定授权工作区域/区域保护规则和矿业资源调查；本轮只作为明确授权的单目标动作。

## 参考源码实查与风险说明

- NeoForge [1.21.1 官方方块破坏流程](https://docs.neoforged.net/docs/1.21.1/blocks/)：有服务端破坏事件、能否收获、掉落与工具/游戏模式条件；本轮使用 FakePlayer 的 `gameMode.destroyBlock` 而非直接修改方块。
- NeoForge [FakePlayerFactory](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/common/util/FakePlayerFactory.java)，库方法以 Level + GameProfile 缓存，世界卸载清理；[1.21.1 内存议题 #1487](https://github.com/neoforged/NeoForge/issues/1487)：不能假设大量假玩家无成本；测试监听生命周期。
- [PlayerEngine](https://github.com/Goodbird-git/PlayerEngine)（代码与 README，LGPL-3.0；参考 `LivingEntityInteractionManager`、`LivingEntityInventory`：把玩家操作、库存、脑分离；不直接复制代码，也不强制引入 1.20/1.21 映射混淆版本）。
- [MCA Reborn](https://github.com/Luke100000/minecraft-comes-alive)（根据 `SchedulesMCA`/`VillagerBrain` 理解持续人格+日程）、[MineColonies](https://github.com/ldtteam/minecolonies)（职业库存与真实世界生产）、[Millénaire](https://github.com/Leviaria/Millenaire)（`GoalScheduler` 任务取消/失败恢复）；**只参考思想，代码版权/许可不混入本仓库**。
- [3D Skin Layers](https://github.com/tr7zw/3d-skin-layers) 的 player-renderer 混入和 MeshHelper 从官方 skin 64×64 第二层构造 mesh，但不自动支持 `MobRenderer<PlayerNpcEntity,...>`；后续必须针对我们的模型集成且尊重其许可证。

## 给后续 GPT/Codex 的专业实现 Prompt（可以整段复制）

> 你是 Minecraft Java 1.21.1／NeoForge 21.1.248／Java 21 资深玩法引擎工程师、AI agent 工程师、技术美术与 QA。仓库为 `g2066203208-beep/mc-studio`，用户期待具有和玩家尽量相同功能的自然 NPC，且独立自主生活。先阅读 `AGENTS.md`、`docs/HANDOFF.md`、本规范，再检查远程真实代码与当前 PR head。不要改 main，也不要覆盖其他会话分支。每项能力必须拆成受限 PlayerAction adapter，服务端真实调用原版/NeoForge API，回传可验证结果（世界方块、物品守恒、耐久、权限/保护事件），不要单纯改 `setBlock`、加假对话或换类名冒充玩家。先从现有通过的 GameTest 复现，再只针对失败案例修改；禁止只改断言绕过验证。新增失效/取消/异常/保存/卸载测试。把开源出处、目标版本与许可证、实际读过的 Java 文件、架构取舍、已知差距记录入文档；不复制 GPL/LGPL 代码。必须独立任务分支提交小批次 commit、创建 draft PR、核对 **每次修改后的提交** 的 GitHub Actions Build、GameTest 日志及客户端启动/进入世界截图。每项报告严格区别编译成功、NeoForge 游戏服务端实测、图形客户端画面、玩家 HMCL/222 mod 包验证与性能。不要把运行中的 CI 当成功。最后给真实 PR/Actions/Artifact 链接和未完成清单，绝不虚构截图、TPS 数值、已合入 main 或兼容性。
