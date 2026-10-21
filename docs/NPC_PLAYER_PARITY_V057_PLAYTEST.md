# REME NPC v0.5.7-dev｜玩家真实挖掘与客户端验收

**2026-10-21；MC 1.21.1 / NeoForge 21.1.248 / Java 21；任务分支 `feature/npc-player-parity-mining-v057`，草稿 PR #39。** 这是阶段性交付，不是所有玩家功能和生活 AI 完成；禁止未经验证合入 main。

## 本次真正做到

- NPC 的真人类玩家动作层：单目标矿石/石块的授权采矿 `PlayerMiningActions`，使用玩家生存进度 `BlockState.getDestroyProgress`，最终调用 `FakePlayer.gameMode.destroyBlock`。不是 `setBlock(AIR)`、不单独伪造战利品。
- 真实主手工具等级与耐久，原版破坏掉落留在世界；验证铁镐 `minecraft:damage=1`。错误镐子拒绝，世界不改。
- NeoForge `BlockEvent.BreakEvent` 权限/保护事件取消；被取消不扣工具、不反复绕过保护。
- 玩家明确授权自己的最近 NPC（管理员）：`/npc mine <x y z>`，`/npc minestop`；无任务时正常生活。任务可以因危险中断并尝试恢复，跨 NBT 只持久目标不保留半截挖掘进度。
- 单一 `NpcPlayerActor` 在挖掘、放置间复用相同 FakePlayer profile；不是实际联网 ServerPlayer，也不强制为每位 NPC 开 Minecraft 客户端。
- 原本 0.5.6 的玩家道具放置桥接、自然居民、基础农民/面包师/守卫和社会功能保留。

## 客户端 / GameTest 真证据（已逐项核对）

- Java/NeoForge Build: [39585377197](https://github.com/g2066203208-beep/mc-studio/actions/runs/39585377197) **SUCCESS**。
- NeoForge 游戏服务器 GameTest: [39585377144](https://github.com/g2066203208-beep/mc-studio/actions/runs/39585377144) **SUCCESS，22/22**：真实方块掉落+铁镐耐久、拒绝错误工具、BreakEvent 拦截、任务跨存档、FakePlayer 身份、历史回归。
- Windows 实际 PowerShell 安装器模拟 HMCL 环境：[39585377100](https://github.com/g2066203208-beep/mc-studio/actions/runs/39585377100) **SUCCESS**；但不是用户本机的 HMCL 或 222-Mod 整合包。
- **真正图形客户端**：[39585369776](https://github.com/g2066203208-beep/mc-studio/actions/runs/39585369776) **SUCCESS**；证据 artifact [12338619819](https://github.com/g2066203208-beep/mc-studio/actions/runs/39585369776/artifacts/12338619819)，含 19 张原始客户端截图和 `client-gui.log`、`run/logs/latest.log`。
  1. `client-gui-mine-prepared.png`：截图可见原石与 MinerGUI NPC 手持铁镐；日志 `Replaced a slot on MinerGUI with [Iron Pickaxe]`，`REME_MINING_PREPARED`。
  2. `client-gui-mining-progress.png`：矿石变空气，真实圆石掉落在世界，NPC 手持铁镐。
  3. `client-gui-mining-block-confirmed.png`：**集成服务器内置命令** `execute if block ... minecraft:air run say REME_MINING_CONFIRMED_AIR`，成功日志确认为 AIR；不是由截图猜测。
  4. `client-gui-mining-tool-after.png`：内置 `data get entity` 显示 `id: minecraft:iron_pickaxe`、`minecraft:damage: 1`，真实工具损耗 1。
- CI 构建的 `reme_studio-0.5.7-dev.jar` 大小 **165572 bytes**，SHA256 `cd95c3089bad7524fd0fc3f46e692e242b148a5f06b34f5b9694b`。完整安装 ZIP 包含 `Install-REME-NPC.cmd`、`Install-REME-NPC.ps1`、`README.txt` 和该 JAR。

## 失败复盘与实际修复（不要删除红色证据）

1. 首轮 20/20 通过后审查发现放置与挖矿 actor 的 GameProfile **名称大小写不一致**，会为单一居民产生多个 FakePlayer。新增 `NpcPlayerActor` 统一身份，GameTest 拓展为 22/22。
2. 从 0.5.5 更新到 0.5.7 后，旧 CI 打包使用硬编码 `reme_studio-0.5.5-dev.jar`，导致 **构建成功而安装器作业失败**；改成从 Gradle 的 `mod_version` 动态选择 JAR、ZIP 和 artifact 名称。
3. 新实际客户端断言的首次尝试 [39584228885](https://github.com/g2066203208-beep/mc-studio/actions/runs/39584228885) **失败**，日志证明 /item replace 给了“NPC”而非 MinerGUI（测试六村民导致 nearest 选错）；且使用无实体参数的 /tp 旋转命令解析错误。不是通过删除断言修复：隔离测试世界清除旧 NPC、铺稳定平台、按名字给 MinerGUI 镐子、`/tp @s ...`、加入游戏内石头→空气及工具 NBT 验证。复测 39585369776 真通过。
4. 测试只覆盖原版/白名单矿石和 FakePlayer 的 NeoForge 事件通路，不能推断全部其他模组的玩家权限兼容性或长时资源压力，仍须独立测量。

## 自己在独立测试世界操作

1. 备份测试实例，关闭游戏，完整解压 `REME-NPC-HMCL-0.5.7-dev-installer.zip`；运行 `Install-REME-NPC.cmd`，选择 **HMCL 正在启动的那个 NeoForge 1.21.1 游戏实例**目录并输入 `INSTALL`。卸载/回滚依赖安装器生成的旧版备份，不动正式存档。
2. 进入 **开启作弊的隔离存档**，输入 `/remecheck` 应显示 v0.5.7-dev 且 `/npc registered=true`。
3. `/npc spawn Miner1`；拿铁镐，**潜行 + 右键** 自己的 NPC 将主手铁镐装备给它，站近且避免附近其他自己的 NPC 抢“最近目标”。
4. 在适合的小型安全试验石块执行 `/setblock ~2 ~ ~ stone`，`/npc mine ~2 ~ ~`，等几秒。看 NPC 的镐子、石头被挖掉、圆石掉落。用 `/execute if block ~2 ~ ~ minecraft:air run say MINED_OK` 验证目标真的变空气。
5. `/npc minestop` 取消等待目标；`/npc info` 查询最近 NPC 状态。不能对正式房屋、箱子、机器方块或未加载区块进行自动采矿。

## 下一阶段尚未完成

- 自动识别**有明确授权的矿区/矿井 POI**，职业矿工取得目标和运输到库存；完整背包/热栏/盔甲/副手、工具类型、NPC 拾取地面掉落、真实工作台合成、任务进度与路线看门狗。
- 长期真实住宅分配、完整 NPC 自主两天作息、多人关系、经济/法令、离屏人口台账。
- 原创自绘 64×64 二层皮肤和在自定义 NPC 渲染器上的真实逐像素立体层、动态眼睛、多帧检验；仅装外观依赖 Mod 不等于适配完成。
- 用户的实际 HMCL、完整 222-Mod 整合包、20/50/100/200 NPC 的可复现 TPS/MSPT/内存长测。暂时 **不合并 main**。

工作规范与完整专业开发 Prompt：[`NPC_PLAYER_PARITY_MASTER_PLAN.md`](NPC_PLAYER_PARITY_MASTER_PLAN.md)。追踪：[Issue #41](https://github.com/g2066203208-beep/mc-studio/issues/41)。
