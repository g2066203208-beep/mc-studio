# NPC 原生床位生活系统 | Bed Lifecycle

基础：PR #91 夜间返家改造；Minecraft 1.21.1 / NeoForge 21.1.262 / Java 21。

## 实际代码行为

1. LIVE 居民在 SLEEP 决策后，从已加载的 homePos 周围水平 8 格、垂直 3 格范围查找原生双格床头（BedBlock.PART=HEAD）。每 200 ticks 分散扫描，不全局请求区块。
2. 居民认领无玩家/村民占用的床头；保存 `NpcAssignedBedHead` 到现有 REME NPC NBT。附近加载中的其他居民不能重复认领相同床位。
3. 居民经现有 RoadNavigator 走到床旁，再以原生 `LivingEntity.startSleeping(BlockPos)` 真正入睡；反向以 `stopSleeping()` 起床，使用 Minecraft 实际床占用属性，而非网页假动画。
4. 白天工作、新危险、拘留、离开 LIVE 模式或床被拆除会唤醒/重新规划；无床时可以在住处缓慢恢复精力，不会在赶路途中快速回复。
5. 每 200 tick 只有原生在床状态 +12 精力，无床在家休息 +2，其他情况 -3；删掉以前每 40 tick 额外 +1 的非物理恢复漏洞。
6. `/npc info` 显示选定床位和真实 `isSleeping()` 状态。
7. NeoForge 原生 GameTest 新增真实双格床、两人认领排他、原生床占用、床位 NBT、起床后床重用；旧夜间优先级测试同步校正新恢复值。

## 清晰边界

- 此 NPC 目前仍是 `PathfinderMob`，使用原生 LivingEntity 睡觉，不是 ServerPlayer 的玩家计数，不会替真实玩家跳过夜晚。
- 床位冲突目前只在相邻已加载的 NPC 里判定。离线或跨区块未加载居民的预约不受全局 POI 锁保证；但是床实际 OCCUPIED 时始终不能被重用。
- 本期不自动生成/拆除床或建筑；不支持所有第三方自定义床，暂只测试原生 BedBlock。
- 旧实体和刷怪蛋的 `reme_studio` 注册 ID 不变，避免丢失旧存档。之后必须设计数据迁移才能和 PEAK 同时安装独立 Mod。
- 编译成功不代表用户自己的 HMCL 实例已经安装正确 JAR，真实客户端和 222-Mod 整合包兼容仍需分别核对。

## 玩家体验步骤（v0.6.8-dev）

- 在已启用作弊的测试世界使用 `/npc spawn`，或创造背包里取 `NPC 生成蛋`。
- 确保 NPC 属于你，将它的居住点放到自己搭建的含原版床的建筑附近：`/npc sethome`（设置为执行者所在坐标）。
- `/npc bed` 查看身边最近居民已分配床位与睡眠状态。
- **看向六格内一张未占用原版床**，输入 `/npc bed assign` 把床分给你拥有的最近 NPC；如超出其住处附近的 8 格搜索圈，会明确拒绝。
- `/npc bed clear` 解除当前自己拥有 NPC 的床位，正在睡觉会起床；自然搜索仍可在之后重新选择合适床位。
- 夜间观察 NPC 实际走到床旁、水平躺下，`/npc info` 应显示 `sleeping true`，床的 `OCCUPIED` 由 Minecraft 原生睡眠更新；白天自然起床。
- 若看不到 `/npc`，首先确认 HMCL 当前实例 Mods 列表中真的载入 **0.6.8-dev**、Java 21 和 NeoForge **21.1.262 或符合元数据的更高 21.1.x**，不要把 CI 测试机通过当作玩家本机已经加载成功。
