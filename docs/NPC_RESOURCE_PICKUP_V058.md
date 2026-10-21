# NPC v0.5.8-dev｜玩家式真实挖掘掉落与背包闭环

**项目版本**：Minecraft 1.21.1、NeoForge 21.1.248、Java 21。分支 `feature/npc-resource-pickup-v058`，草稿 PR #47，基于玩家挖掘 PR #39。此版本仍属实验，**不可用于正式存档**。

## 为什么不是简单“拾取附近物品”

- 玩家打碎方块生成真正的 ItemEntity，物品可能排队到实体 Tick 结束时才进入世界索引；不能用破坏后立即 `getEntitiesOfClass` 扫描判定掉落不存在。
- 其它玩家地面物品可能同类合并，只有 UUID 没有**原始数量**也不够：新的矿物与旧物体合并会导致 NPC 多领其它玩家的财产。
- 不能用模拟背包 `addItem(new ItemStack(...))` 生成战利品，更不能掉落没进背包就消失。

## 已提交的实现（是否通过以对应 Actions 为准）

1. `PlayerMiningActions` 在 `FakePlayer.gameMode.destroyBlock` 调用的服务器线程内设置一次性作用域，在 NeoForge **`BlockDropsEvent`** 里只记录本 NPC 在本坐标真实产生的 `ItemEntity UUID → ItemStack count`，然后立即清理作用域。玩家旁边原有的掉落不产生认领记录。事件文档：https://lexxie.dev/neoforge/1.21.1/net/neoforged/neoforge/event/level/BlockDropsEvent.html（或者 https://aldak.netlify.app/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/event/level/blockdropsevent）。
2. `PlayerNpcEntity` 有至多 24 个认领条目、有效期 300 游戏 tick、开始 tick（保护延迟注册）、NBT 的 UUID + Remaining；这不是新库存，纯粹是对地面原物品的认领令牌。
3. `PlayerLootPickup` 通过 `ServerLevel.getEntity(UUID)` 查找已加载原物品；只对有认领 UUID+数量的掉落走近（内层使用 vanilla navigation 精确接近 1.5 方块，不用会提前在两格停止的 `RoadNavigator`）；遵守真实拾取延迟，使用背包 `SimpleContainer.addItem` 真正搬移。只按允许数量扣除物品实体原物，部分插入保留地面余量；背包满时原物保留。危险/区块卸载/掉落消失不会重新生成物品。
4. 插入 `LivingWorldActions.tick` 在矿工完成挖掘后优先去拾自己的真掉落，普通 AI 不能跳过这一阶段的收尾工作。
5. `CivicGameTests` 验证完整玩家挖掘→地面掉落→背包守恒、捡不走无关钻石、物品合并无越权、背包半满只拾合法部分、NTB 保存读档、真实工具磨损、以前的社会生活基础回归。额外 GUI 截图脚本在隔离 MC 单人世界跑 `/data get entity ... NpcBackpack` 并执行 `/execute if data entity ... NpcBackpack[{id:"minecraft:cobblestone"}] run say REME_NPC_LOOT_IN_BACKPACK`，需要原始日志具备标签方视为通过。

## 已知失败与纠正记录

最初的自动完整闭环 GameTest [39600581647](https://github.com/g2066203208-beep/mc-studio/actions/runs/39600581647)：**25 项里 1 项失败**，说明物品真实落地、被识别（claims=1），但 NPC 停在“道路导航停止半径”之外未进入真正物品拾取范围。修订 `PlayerLootPickup` 在近处直接用原版精确导航后，提交 `9c0e140fd19011fe7312c4098740eccfce71c7a2` 对应 GameTest [39600949806](https://github.com/g2066203208-beep/mc-studio/actions/runs/39600949806) **25/25 required passed**。这之后又加上防止**同类物品合并导致偷领**的数量限制，必须等待最终源码的 26/26 与 GUI 结果，不得拿旧 25/25 代替新功能。

## 阶段出闸清单（每项有实据才勾）

- [x] 第一版完整采矿→物品自动拾取，独立真实 NeoForge GameTest 25/25 验证
- [ ] 进一步按 UUID+数量防合并越权后的最新版 GameTest
- [ ] 最新 NeoForge GUI 真正检查石头→空气并且 `NpcBackpack` 中有 cobblestone（不可只看 /npc mine 命令成功）
- [ ] 0.5.8-dev 的实际 Build/Windows HMCL 测试与经校验的压缩包
- [ ] 用户实际 HMCL＋222 模组完整实例兼容、200 人性能、长期存档与成对交互
- [ ] 完整玩家背包/热栏/盔甲/副手/工作台合成/职业矿工自己寻矿（本 PR **没有** 完成）

## 手工测试简易操作

仅在独立测试世界执行 `/npc spawn MinerGUI`，手持铁镐潜行右键自己的 NPC 交付主手装备；在家园 16 格内准备石头，`/npc mine ~2 ~ ~`；原石消失后 NPC 应靠近物品，圆石进入 36 格背包。潜行空手右键自有 NPC 查看背包，或 `/data get entity @e[type=reme_studio:player_npc,name=MinerGUI,limit=1] NpcBackpack` 观察。物品不会凭空新增；其他玩家的落地钻石不应被本任务拾取。
