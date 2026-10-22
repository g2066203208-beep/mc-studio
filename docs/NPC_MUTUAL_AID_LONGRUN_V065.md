# REME NPC 0.6.5-dev — 邻里互助、自洽物资与长时间真实服务器测试

**目标**：让 NPC 在玩家不输入指令时，基于实际状态互相帮助，而不是用随机台词冒充生活。将每个行为建立在 Minecraft 世界中的实体、背包、事件和存档上。项目为 Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21。

## 新的原创玩法循环：邻里的一份饭

1. 一名 LIVE 居民真实饥饿（`hunger <= 40`）、自己的背包确实没有可吃物品时，每人每 80 tick 分相检查最多 3 格内的在场其他居民，不从无限全局人口表里查询，也不强制加载任何区块。
2. 捐赠方必须是 LIVE、生存状态健康（`hunger >= 65`），同国家/阵营/地区、双方非敌对、真正距离不超过 3 格且相互视线可见。禁止使用被拘留/受惊角色的库存；店员、建造工和商队不挪用其专用货物。敌对劫匪绝不参加互助。
3. 按有限的现有真实食物清单选择捐助，捐赠方至少已有 3 份相同食物，保留 2 份。两端 36 格现有库存先分别复制进行试算，接收方有空间且成功扣减物品后才提交两端。成功只转移**一份真实食物**：世界食物总量不增加，双方都不能在失败时失去物品。
4. 接收方基于现有 `ResidentMemory.Kind.HELPED` 写入社会关系与时间（原先已有的有界 NBT 持久化）；提供帮助的居民记录 `Kind.GIFT`。帮助有 600 tick 冷却，存档回读仍有效，避免同一饥饿者反复榨取邻居。
5. 食物到手后交还给已有真实吞食流程 `LivingWorldActions.eat`，不直接瞬间增加饥饿值或创造食物。危险逃跑、警卫抓捕、原版挖矿及物资运输仍按原优先级保护。

## 本次排查修正：关系内存真正有硬上界

旧 `ResidentMemory.restoreTrust` 最多接受 128 个不同人，但**LIVE 的 `witness` 没有上限**，人口密集的城市或反复靠近大量玩家，可能导致每位居民的 `Map<UUID,Integer>` 无界增长。新版本强制包括实时观察在内的 128 联系人上限，超限先淘汰最久记录的身份，再追加；所有信任值保持 -100..100。纯 Java `LivingPlannerSpec` 新增 400 独立人际对象写入与 400 个恢复验证。

## 真 NeoForge 测试证据的界限

- 基于 v0.6.4 经过 CI 验证的 50 项 GameTest，增加 3 项：邻里一物守恒 + 求助关系 NBT、真实背包满/保留食物/地区隔离/600 tick 冷却反例，以及 **24 个不同 UUID 的活 NPC 在真实 GameTestServer 连续被世界 tick 2400 次**（约 2 分钟，以20TPS估算）的存活、需求变化、内存上界校验。不是 `for` 循环假调用 2400 次 AI。
- 新增游戏长跑用例计量日志 `REME_NPC_LONGRUN` 输出实体数、实际等待 tick 数、存活数、需求改变数和从测试开始的墙钟秒数。墙钟时长**不是 MSPT**；没有采集真实 20/50/100/200 NPC MSPT p95 之前，不能宣称大型城市已优化完成。
- GameTest 全部使用修过的 48×24×48 隔离结构，仅修改 CI 临时 GameTest 世界；原始用户游戏世界绝不被修改。
- CI `build.yml` 的 Gradle 构建与独立 Java 规格测试、`civic-gametest.yml` 的 NeoForge 服务端、`npc-client-gui.yml` 的 Xvfb 真实客户端与 Windows 安装验证都须查看**该分支/该合并 SHA** 的实际结果。脚本跑完≠用户整合包兼容通过。

## 实际阅读和后续底层技术路线

- [NeoForge 1.21.1 官方 Game Test 文档](https://docs.neoforged.net/docs/1.21.1/misc/gametest/)：使用 `@GameTest(timeoutTicks=...)` + `GameTestHelper.runAfterDelay` 驱动真实 tick。
- [MineColonies 的开源 1.21.1 实际项目](https://github.com/ldtteam/minecolonies) 及 2026-10-21 版 [1.21.1 release](https://github.com/ldtteam/minecolonies/releases)：实际居民、建筑工、仓储与请求式物资流的产业链可借鉴**设计思想**。MineColonies 是 GPL-3.0，未复制它的源码、素材或协议。
- [NeoForge FakePlayer listener 泄漏问题 #1487](https://github.com/neoforged/NeoForge/issues/1487)：使用逐 NPC 稳定身份时必须检查 `PlayerAdvancements` 监听注册释放，不能为了所谓玩家兼容直接创建 200 个假玩家并宣称安全。
- 本分支仍使用原 `PathfinderMob` 居民；`RealNpcServerPlayer` 还是未网络注册的隔离原型。下一阶段应独立开发真正一人在线的 `ServerPlayer`、安全虚拟 Connection、PlayerList、客户端 PlayerRenderer / 3D 皮肤等，在测试前**不**以这轮互助功能掩盖目标未完成。

## 下一阶段优先权

1. 长跑中观察并定位寻路卡住、需求断供、商队与施工争抢任务的真日志；修复后再增加图形客户端多夜测试。
2. 独立执行 24 / 50 / 100 / 200 NPC 的真专用服务端采样，收集 TPS、MSPT p50/p95、GC、内存增量和网络包，控制区块分布/世界种子/视距/Mod 组合，不能仅凭计时一两个 tick 编造性能。
3. 真玩家实体原型进入网络、玩家渲染、正常装备/动画，再在隔离存档逐步迁移现有居民数据。先验证一人，绝不直接让同 UUID 的 NPC Mob 和 ServerPlayer 同时进世界。
