# 目标修正：NPC 必须尽可能成为真正的玩家，而不只是“像玩家的生物”

**正式技术决策（2026-10-21）**：用户要求包括全部玩家动画、3D 皮肤、装备、交互、状态、玩家受击与 Mod 效果，并具有 NPC 自主生活能力。采用 **Player-backed NPC（真实服务端玩家实体 + 真实客户端玩家呈现 + 独立 NPCBrain）** 作为最终架构方向。过去的 `PlayerNpcEntity extends PathfinderMob` 和 `LivingNpcPlayerModel` 仅为现阶段兼容/回退，不应被称为“完整玩家等价”。不要再投资独立仿造玩家的渲染功能，优先迁移至标准玩家渲染流水线。

## 先证明目前差距，不再混淆 “FakePlayer” 和真正在线玩家

现有 `NpcPlayerActor.forNpc(npc, level)` 用 NeoForge `FakePlayerFactory` 返回 `FakePlayer extends ServerPlayer`，临时用于 **BlockItem.useOn、ServerPlayerGameMode.destroyBlock** 等玩家行为。此对象与 NPC 共享 UUID，动作结束恢复装备，但 **未作为标准网络玩家经 PlayerList 登录/注册，也未成为该 UUID 的实体**；真正的 `level.getEntity(UUID)` 依旧是 `PlayerNpcEntity extends PathfinderMob`。因此：
- **服务端**：一些原版/NeoForge 的 BlockPlace/Break 模组事件对 FakePlayer 生效；但 `instanceof Player` 判断 NPC 自身仍是假，在线玩家枚举 / player tick、完整玩家属性/饥饿/成就/背包/副手/技能等不完全一致。
- **客户端**：真正画出来的是 `MobRenderer<...PlayerModel>`，**不是** `PlayerRenderer<RemotePlayer>`。第三方只针对 `AbstractClientPlayer`、`PlayerRenderer`、玩家本体的 PAL / playerAnimator / 3D Skin Layers mixin 不会因为用了 `PlayerModel` 就自动应用。
- **UUID 安全**：不能直接把相同 UUID 的 `FakePlayer` 再 `addFreshEntity` 到世界里与已有 Mob 同时存在，会产生实体索引/网络/存档身份冲突。
- **检测**：`full_player_mod_parity_requires_real_networked_player_entity` GameTest 显示在现阶段世界里真实实体仍是 Mob，临时动作委托是未登录网络的 ServerPlayer 子类；绿灯**并不表示玩家等价**。该测试在完成迁移时应改写为断言真的玩家实体，而不是永久把错误设计锁死。

## 三种方案的判定

| 方案 | 玩家专属 ServerPlayer 事件 | PlayerRenderer / 3D Skin Layers / PAL | NPC 寻路/生活 | 结论 |
|---|---|---|---|---|
| 旧 Mob + PlayerModel + 少量 FakePlayer 动作 | 仅部分动作 | 不自动适配 | 现有系统成熟 | **回退/存档迁移输入** |
| 旧 Mob + 外观显示为假 RemotePlayer、服务器仍 Mob | 不自动适配 | 可能视觉层部分生效，网络身份易分裂 | 现有系统 | **不接受：单独变脸不能达到目标** |
| **真正 ServerPlayer NPC** + 非真人连接 + 原生玩家列表/客户端玩家实体 + NPCBrain | 大部分玩家事件和能力自然进入原版管线，前提是 Mod 不主动排除假玩家 | 真人玩家渲染的第三人称功能有望自动复用，具体仍须逐 Mod 验证 | 必须新增无客户端输入的服务端运动/任务控制器 | **采用：分阶段原型后迁移** |

**不能承诺“100% 所有玩家效果”**：有些第三方 Mod 明确检查是否真人连接、UUID、认证账户、真正在线客户端、本地第一人称输入、玩家个人配置/键位；这些即使 `ServerPlayer` 继承成功也可能不触发。用户要的是尽可能通用而不是宣称绝对 100% 覆盖。**不可绕过用户鉴权/正版会话验证，不冒用真实玩家账号 UUID**。

## 实际阅读的开源实现/可迁移概念

1. [Fabric Carpet `EntityPlayerMPFake extends ServerPlayer`](https://github.com/gnembon/fabric-carpet/blob/master/src/main/java/carpet/patches/EntityPlayerMPFake.java)：真正的服务端玩家类，模拟 `Connection` + `ServerGamePacketListenerImpl`，通过 `PlayerList.placeNewPlayer` 进真实玩家管理和客户端追踪；为虚拟玩家单独处理同步、位移、视角、物品使用、死亡、退出与重生。该源码为 **MIT**；检查许可证和 Minecraft/映射/NeoForge 21.1.248 API 不同，不能直接复制 Fabric mixin 编译结果。要为 NeoForge 写独立适配。
2. [Fabric Carpet `PlayerList_fakePlayersMixin`](https://github.com/gnembon/fabric-carpet/blob/master/src/main/java/carpet/mixins/PlayerList_fakePlayersMixin.java)：实际用假的连接处理器替换真实接收端、在出生/重生流程创建正确类型。**PlayerList 生命周期绝非可以省略的步骤**。
3. [Duzos Fake Players 1.21.1](https://github.com/Duzos/fakeplayer/tree/ver/1.21.1)：读过 `FakePlayerEntity.java`, `FakePlayerModel.java`, `FakePlayerRenderer.java`，虽然有皮肤、战斗、职业、物流，却同样是 `extends PathfinderMob` 和自定义 `LivingEntityRenderer`，无法让所有目标是 `ServerPlayer`/ `AbstractClientPlayer` 的第三方效果自动生效。可参考其职业互动思路，但不选其实体架构。
4. [NeoForge FakePlayer 内存问题 #1487](https://github.com/neoforged/NeoForge/issues/1487)：重用/释放真实 GameProfile 和 advancement listeners；200 个伪账号长期运行必须特测。
5. [Player Animation Library NeoForge 1.21.1](https://modrinth.com/mod/player-animation-library/version/ePlL3vPb), [Entity Player Compat 1.21.1](https://www.curseforge.com/minecraft/mc-mods/entity-player-compat), [3D Skin Layers](https://github.com/tr7zw/3d-skin-layers)：只有实际远程玩家被 `PlayerRenderer` 渲染后才谈自动兼容；玩家外观、皮肤 provider、装备和渲染动画须逐项以真 MC 客户端截图/关键帧验证。NPC 必须复用用户自己的合法正版皮肤系统或原始自绘 64×64 PNG，遵守原始资源许可。

## 必须分阶段改造，而不是一刀切删掉现有村民

### P1 実体探针（本 branch 的开始）
- 独立 PR 和 GameTest **明确区分** `PlayerNpcEntity`、`FakePlayer`、`ServerPlayer`、`PlayerList` 的真实身份/可用能力；
- 原 35+ 游戏测试保留，确保工作/运输/抢劫/脚步路网/库存都不会因为方案调整失去物品守恒。

### P2 单个测试世界真实玩家 NPC
- 新 `RealNpcServerPlayer extends ServerPlayer`，生成**不冒用真人玩家身份**的唯一 GameProfile，附带唯一 UUID、server-side no-human input Controller、真正玩家背包、玩家装备/状态/食物、世界坐标与真实玩家事件。
- 专有虚拟 `Connection(PacketFlow.SERVERBOUND)` 和替代服务器 `ServerGamePacketListenerImpl`，接入服务器 `PlayerList.placeNewPlayer` 或经测试相当的 NeoForge 生命周期；可安全退出、世界 unload/death/dimension transfer/respawn，不能把 fake client 加入真实玩家登录验证或打爆服务器。
- 兼容真玩家渲染：临近真人能看见**PlayerRenderer<RemotePlayer>** 绘制的 NPC，玩家设备上资源加载得到合法皮肤；逐项验证是否自动有外层、盔甲、头饰、常见玩家动画效果。登录/退出必须正确处理 PlayerInfoUpdate、AddEntity/RemoveEntities、皮肤缓存。
- 观察同一生物身上 `BlockEvent.BreakEvent` 与 NeoForge PlayerTickEvent 等是否正确生效。不能用单独 Mob 在另一处执行动作蒙混过关。

### P3 AI 寄生到真实玩家（不重复运行两个实体）
- 把 `LivingPlanner`、`ResidentMemory`、`CivicMind`、`FootTrafficData`、`CaravanRoutes` 等与 `PlayerNpcEntity` 解耦为 `NpcBrain` 数据模型与 `NpcActionController`；运动、攻击、进食、装备、交易、修路、合成尽可能调用标准玩家交互，使用真实玩家库存，不做影子背包。
- 真 ServerPlayer 的运动不同于 PathfinderMob 导航，采用纯寻路规划器 + 服务端方向/移动控制器驱动物理动作，保留重力、碰撞、水、梯、台阶、下落伤害、护甲/饥饿原版逻辑；**绝对不长期用无碰撞 teleport 假装走路**。
- 加入逻辑路权，异常路径重试，禁止未授权矿点/建筑区块强制加载。

### P4 存档迁移与多玩家
- 按稳定 NPC ID 把旧 `PlayerNpcEntity` 的身份/姓名/属性/皮肤/36 槽背包/装备/路线/关系/经济账簿一次性事务化迁移为真 ServerPlayer NPC，成功后才移除旧 Mob；失败可回滚。**不允许同 UUID 两个世界实体并存**、物品无限复制或原来存档破坏。
- 隔离实例测试 1/2/20/100/200 人玩家列表、网络包长度/广播、MSPT p50/p95、玩家登录/退出与重新读取存档；可能需要**分层模式**（远处不保持完整 ServerPlayer）而不可影响附近活动中兼容性。基准未测不能写性能强悍。

## 本轮工程硬验收

| 门槛 | 证据 |
|---|---|
| 身份检查 | 在 NeoForge GameTestServer 打印并断言 FakePlayer 属于 ServerPlayer，但实际 Mob 并非 Player；GameTest 结果不等于完成目标 |
| 真玩家实体 | 真 ServerPlayer NPC 能进入 PlayerList 与真正世界实体索引，客户端看到 RemotePlayer 而非 MobRenderer，真实玩家专属 Mod 的至少一种效果触发 |
| 一致性 | 没有影子实体/物品、保护插件不被绕过、不同 NPC UUID 独立且真人不能被覆盖 |
| 客户端 | 真 Minecraft 1.21.1 NeoForge 双人客户端，肉眼截图/日志证据普通玩家外观、PAL/3D Skin Layers 有无生效 |
| 卸载重连 | player data/no-net cleanup、世界退出/回载、维度切换、死后复活，保持库存与 GameProfile |
| 性能 | 已加载 20/50/100/200 人时真实 TPS/MSPT、玩家列表和内存，目标长时间不内存泄露 |

## 后续执行 Prompt（直接放到编码代理）

> 停止将 NPC 仅当 PathfinderMob＋玩家模型，当用户需要完整玩家等价时将 **RealNpcServerPlayer** 作为目标。先从 Carpet（真实 ServerPlayer+模拟连接），NeoForge FakePlayerFactory 与 PlayerList、PlayerRenderer 的实体网络通道做 1 人隔离原型。严禁直接复刻 Fabric 映射代码，严禁假称已经100%兼容模组，严禁为图方便删除已有居民存档。创建清晰阶段门槛：一只 NPC 真进入 PlayerList/世界，真实客户端按 RemotePlayer/PlayerRenderer 渲染，原版玩家事件/装备/状态/移动、PAL、3D Skin Layers 至少一项逐项证明。通过后逐步抽出旧居民 NpcBrain/任务与资源守恒，加入卸载/重生/登录事件清理和 200 NPC 性能测量。只有最终玩家系统确实已复用，才能删去临时 NPC 专用动画/3D外层。未验证时一律标记未实现，保留独立草稿 PR。
