# REME NPC · 0.6.7 候选测试：盯住同一名 NPC 全天（24 MC 小时）

## 关键目标与原则

用户不需要又一批只证明“活着”的压力测试，要求 **亲眼观察同一名 NPC 整整一个原版日夜：吃饭、工作、行走、夜间作息；发现卡住就查原因**。

之前的 50/200 NPC CI 已真实跑了 >=300秒，且 200/200 活着，但是没有逐人检查“工作、吃饭、睡觉”和真正第三人称画面。这轮单独建试验分支 `test/npc-single-citizen-full-day-v067`，不替换用户存档，也不抢先合并到主分支。

## 一：真实 GameTestServer 精确跟踪 1 个 UUID、24,000 世界 tick

文件：`mod/src/main/java/dev/reme/mcstudio/npc/test/NpcSingleDayGameTests.java`。

- 特地只生成一名叫 **DayWatchFarmer** 的 LIVE 农民，固定其 UUID、家和农田；其周围真实铺设 27×27 石质地板与 3×3 完全成熟的原版小麦，背包给 10 个真实面包和 12 个小麦种子；无需凭空制造收成或调用 AI 方法伪造劳动。
- 重置测试世界为 `/time set 0` 等价的日出。世界原生 Tick 自动推进；采样包括玩家式库存实物数量、位置、实际农作物成熟/生长、意图、最近一次真正工作结果、饥饿精力社交和寻路状态。每**1,000 Minecraft tick** 输出一行 `REME_DAYWATCH_HOUR`，严格 24 行，包含 **同一个 UUID**。
- 日终 `REME_DAYWATCH_RESULT` 必须显示至少 **24,000 个真实服务端 world ticks**，同一实体存活、24 个样本、有不同坐标、实际吃过背包面包、曾进入 WORK 和 SLEEP 意图、至少一次真实收割/补种，否则直接判失败。不得只凭终点 alive 标志通过。
- `.github/workflows/npc-one-day-watch.yml` 分两条独立通道运行：**快照/AI 回归**仅要求24k世界tick（NeoForge GameTestServer 允许加速）；**真实时间**额外要求 `System.nanoTime` 至少 1,200 秒（20 分钟）后才能通过。二者都存24小时逐行日志，并提供所有失败日志。默认普通 PR GameTest 通过不开启标志立即略过重型流程，不混淆 55 项中被跳过的 1 项。
- 引擎仍存在关键欠账：当前 `LivingWorldActions.rest` 只能回家恢复精力，**不是真正的床和睡眠动画**。SLEEP 意图的日志不是“NPC使用原版床”的证据。

## 二：真实 NeoForge 客户端固定摄像机看同一只 NPC

文件：`scripts/test-npc-single-day-gui.sh` + `.github/workflows/npc-one-day-gui.yml`。

- 真正启动 Minecraft 1.21.1 + NeoForge + Mod 的 Linux Xvfb 图形客户端、创建新的作弊测试世界，实际通过 `/npc spawn DayWatchFarmer` 命名生成一个 NPC，切为农民，用 `/fill` 铺真实地板、湿润农田与成熟小麦。
- 真正玩家切为 `/gamemode spectator`，使用 `/execute at @e[name=DayWatchFarmer,...] run tp @s ~5 ~3 ~5 facing entity ... eyes`：视角每次拍摄前从斜上方对准同一个生物，而不是 `/spectate` 看它的第一人称眼睛（那样反而看不到 NPC 造型）。
- 循环 24 次，每次相隔约50秒真实墙钟，命令查看 `/npc info` 和 `/time query daytime`，用真实屏幕截图生成 `daywatch-hour-00.png` 到 `daywatch-hour-23.png`。工作流保存 PNG 和真实服务端 `latest.log`，并检查 24 图片、实体始终存在、`/npc` 实际已被集成服务端注册。
- **注意**：这里每张照片是约50秒墙钟**间隔采样**，而非确保每张刚好跨过1000 game ticks；客户端可能低TPS。与精确 24k tick 的服务端日志对照才能判断完整全天。测试不代表已经把 24 小时完全录成视频或主机玩家正常游玩，也没有验证用户的数百 Mod 整合包。
- 图形脚本默认安装 Mod 的当前 dev environment 生成测试世界，不动 HMCL 存档。前一轮GUI自动矿块检查存在真实失败，故摄像机验收独立工作流，不借用过去失败脚本的“绿灯”。

## 什么才叫真正成功？

- 真实 CI 启动不等于通过：必须查看运行日志中单NPC完整的 24 行、日终结果的工作/吃饭/睡觉计数、实际截图、最后 UUID 一致；两条 GameTest（一条加速、一条20分钟墙钟）与一条 GUI 都必须通过，才能说“这次全天跟踪测试完成”。
- 如果出现时段间昼夜状态不变化、农田从未收割、持续迷路、面包从未减少、能量一直为零、图形客户端死亡、视角对不准主体，要把**失败反映到 GitHub CI 并修复 AI**，不应靠降低门槛假装完成。

## 参考

- [NeoForge 1.21.1 GameTest 官方说明](https://docs.neoforged.net/docs/1.21.1/misc/gametest/) 以及 [1.21.1 GameTestHelper API](https://lexxie.dev/neoforge/1.21.1/net/minecraft/gametest/framework/GameTestHelper.html) 的 `onEachTick`。
- 世界世界Tick/现实墙钟分别用 `ServerLevel.getGameTime` / `System.nanoTime`；Minecraft 24,000 tick 原生一天约 20 分钟（假设持续20TPS，不代表CI固定20TPS）。
