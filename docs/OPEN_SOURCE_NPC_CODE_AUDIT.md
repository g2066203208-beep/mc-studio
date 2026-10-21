# 从真实开源源码学习活世界 NPC：2026-10-21 源码审查

## 这次看了什么（不是只看 README）

阅读了四个仓库的以下真实 Java 源码；各链接具体定位到文件：

1. **Millénaire / Leviaria，NeoForge 1.21.1**
   - [GoalScheduler.java](https://github.com/Leviaria/Millenaire/blob/main/src/main/java/org/millenaire/goal/GoalScheduler.java)：`VillagerGoal` 和 `VillagerTask` 分离，选择最适目标，非休闲任务可抢占休闲任务；每个任务有 start/tick/stop，正常完成 `StopReason.COMPLETED`，中断 `INTERRUPTED`，无进度 watchdog 6000 ticks `IMPOSSIBLE`，空闲指数退避上限 20；任务结束清理 navigator。
   - [WaypointNavigator.java](https://github.com/Leviaria/Millenaire/blob/main/src/main/java/org/millenaire/goal/WaypointNavigator.java)：48 blocks 以上使用村路 waypoint graph，全局分段、本地 vanilla nav；检测沿路卡住 100 tick 重试、600 tick 触发最后恢复策略；保存导航状态、记录 NAV event。
   - [PerVillagerThrottle.java](https://github.com/Leviaria/Millenaire/blob/main/src/main/java/org/millenaire/goal/PerVillagerThrottle.java)：按居民 UUID 限频，维护 `lastEvalTick`，按过期删除防止 map 无限增长。
2. **Kithkyn / Quzzar，NeoForge 1.21.1**
   - [RoamGoal.java](https://github.com/Quzzar/kithkyn/blob/main/src/main/java/com/quzzar/kithkyn/entities/ai/goals/RoamGoal.java)：每天更新流浪方向，短行程 16 blocks 避免未加载区块，100 ticks 每次检查沿方向净进展；只在 **目标区块加载** 条件下将没进展解释为地形阻塞；生存寻食可以高优先级抢占漫步。明确反对 NPC 在玩家可见时直接脱离世界。
   - [BedtimeWithoutBedGoal.java](https://github.com/Quzzar/kithkyn/blob/main/src/main/java/com/quzzar/kithkyn/entities/ai/goals/BedtimeWithoutBedGoal.java)：无床居民在营火旁休息、守卫继续巡逻；夜间归档物资仍执行，而不是所有 NPC 半夜硬睡。
   - [UnstuckPersonGoal.java](https://github.com/Quzzar/kithkyn/blob/main/src/main/java/com/quzzar/kithkyn/entities/ai/goals/UnstuckPersonGoal.java)：不是“路径卡住就传送”。只有三夜未休息等实际异常条件才恢复，避免把建筑室内卡路径的正常 NPC 错误地传走。
3. **MCA Reborn / Luke100000**
   - [SchedulesMCA.java](https://github.com/Luke100000/minecraft-comes-alive/blob/main/common/src/main/java/net/conczin/mca/entity/ai/SchedulesMCA.java)：用 `ScheduleBuilder` 固定不同活动时段，分别定义 DEFAULT、NIGHT_OWL_DEFAULT、GUARD、GUARD_NIGHT、GUESTS；默认 2000 work, 9000 meet, 12500 rest。
   - [VillagerBrain.java](https://github.com/Luke100000/minecraft-comes-alive/blob/main/common/src/main/java/net/conczin/mca/entity/ai/brain/VillagerBrain.java)：拥有持久化的 active chore、assigned player UUID、memories、personality、mood、movement state；重新登录后应恢复 chore 的 brain activity；适时减少社交疲劳。
4. **MineColonies / ldtteam**
   - [TickRateStateMachine.java](https://github.com/ldtteam/minecolonies/blob/develop/src/main/java/com/minecolonies/api/entity/ai/statemachine/tickratestatemachine/TickRateStateMachine.java)：显式检查 AI_BLOCKING > EVENT > STATE_BLOCKING > state transitions；每个 transition 自己管理重评 tick-rate，而不只是每个居民统一每 80 ticks 更新一次。

## 与 REME v0.5.2 的对照：实话实说

- 我们 `LivingPlanner.choose` 只是判断下一步意图，没有 `VillagerTask` 实例、进展检测、可解释的 stop reason、重试/backoff/任务超时；在恶劣地形可以永久重复尝试。
- `DailyRoaming` 和 `RoadNavigator` 没有“短腿＋沿方向净进展监测/加载区块区分”，故自然生成居民有可能站住不动。
- 固定 48 格半径并不能替代有工作台、床、屋子、道路的真实家庭和 POI；当前生成的 NPC `setHomePos(anchor); setWorkPos(anchor)`，基本不会体验到真正上班通勤。
- `CivicMind` 与 `ResidentMemory` 是有界事件账本，不等于 MCA 的脑部任务状态恢复/亲缘结构/家族生活。
- `/npc demo` 不提供完整环境，一次生成六个人不等于演示完整村庄。
- 用户上传画面最关键的是 `/npc` 根命令未知；这是用户实际 HMCL 实例 **未注册根命令**，根本不能以“AI 通过 GameTest”代替安装成功。尚无用户实例 latest.log 或 Mod 列表，不能确定具体是目录错、模组未加载、冲突还是其他原因。

## 发现并改正的真正 bug

`NaturalResidentSpawn.allowedNaturalReason` 允许 `NATURAL` **和** `CHUNK_GENERATION`，但是 `PlayerNpcEntity.finalizeSpawn` 之前只处理 `NATURAL`，导致区块生成的 NPC 没有被分配个人姓名、地区、初始食物/住所、生活范围与随机性格。本修复统一通过 `allowedNaturalReason` 判断两种出生方式，并对两种生成原因都应用已加载 NPC 的局部人口上限（不加载远方区块）。新增 NeoForge GameTest 用 CHUNK_GENERATION 原因生成实体、断言初始化和 NBT 往返。

## 后续必须按源码结构逐层开发，而不是“继续加几个聊天命令”

1. **Game 环境可玩验收**：与用户实际 HMCL 一致的游戏目录、运行时模组列表及 `/remecheck` 和 `/npc` 成功；客户端真实世界可见、可点击、可交互。没有这一步不发“可用版”。
2. **真正的村民身份与家庭住房**：从世界原版村庄 POI（床/工作站）认领家庭，所有权与关系存档；无工作台/住所时有可观察的“闲置/失业”而不假装上班。
3. **任务生命周期**：独立 `Task`、start/tick/stop、状态转移优先级、任务目标/失败条件、无进展 Watchdog 和指数退避；支持危险抢占劳动；持久化任务并恢复。
4. **路线与实际目的**：世界加载约束、朝目标的净进展、建筑门互动、道路图/本地路径的清晰分层、用户可见路由调试状态，不能无限重算 A*。
5. **真实需求与社会网络**：工作需要物料与工作站，结果改背包/方块；会交易的对象与物资守恒；地区关系、声誉/法律记录持续影响生活。
6. **性能与规模**：在稳定场景中记录各职业每秒任务选择次数、path updates、实体数量、20/100/200 NPC 的服务端 MSPT、区块卸载加载内存，避免空话“性能提升”。

## 许可证与做法

这些项目许可证各不相同；只研究设计结构与测试原则，**不直接粘贴 GPL 等项目的代码到 REME**，若未来重用需进行明确许可兼容性审查与署名。我们的 bug 修复是独立编写的小补丁。

## 验证状态

v0.5.3 需要实际 GitHub Actions Build 和 NeoForge GameTest 成功后才能标记；GameTest 不等于用户的 HMCL 实机。

## v0.5.3 真实完成验证（2026-10-21）
- 源码提交：`ddef80b99663c5fdd4ef9f996aa385877e4c59f3`。Build [39542185445](https://github.com/g2066203208-beep/mc-studio/actions/runs/39542185445) SUCCESS。
- 真 NeoForge GameTest [39542185523](https://github.com/g2066203208-beep/mc-studio/actions/runs/39542185523) SUCCESS；日志 `All 12 required tests passed :)`。新回归针对 CHUNK_GENERATION 自然居民出生及身份/NBT。
- 产物 GitHub artifact ID `12319922214`；解包确认 JAR `reme_studio-0.5.3-dev.jar` 大小 127188 bytes，SHA-256 `ee837d2b02f9d7baf65d8e13b646323ad04d97904e3a39fbfc17916a6b012ab9`。核验 `META-INF/neoforge.mods.toml` (modid reme_studio; v0.5.3-dev; NeoForge min 21.1.248) 和 `javap` 显示 finalizeSpawn 实际调用 `NaturalResidentSpawn.allowedNaturalReason`。
- 仍 **不是** 用户 HMCL 启动成功；用户截图 /npc 根命令未知，尚需 Mod 加载日志或选中版本的 mods 列表才能准确定位。
