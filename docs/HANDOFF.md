# 桌面端 / 网页端交接

## 用户目标

GitHub 是整个 Minecraft 工作室的开发中心。代码、需求、研究结论、任务状态、测试证据与产物版本需要在仓库中留档，不能仅保存在某一个聊天里。用户主要游玩并提出需求，助手负责工程工作。

## 接手入口

1. 阅读 `AGENTS.md`、`README.md`、`docs/STATUS.md` 和本文件。
2. 检查当前分支、HEAD、未提交更改及相关 PR，避免覆盖其他会话的工作。
3. Minecraft 1.21.1 / NeoForge 21.1.248 / Java 21 为当前暂定基线。
4. Linux/云端在仓库根目录执行 `bash scripts/build.sh`。Windows 本机可执行 `scripts/Build.ps1`。
5. 云端无需 `local/environment.json`、Windows 游戏目录或用户存档。
6. 完成修改后提交代码并记录结果。具备远程写权限时推送任务分支、创建 PR，核对对应提交的 Actions 结果。

## 当前工作

- 最小 Mod 工程已经在 Windows + Java 21 下构建成功。
- 尚无玩法功能；没有测试源码，不能把 `test NO-SOURCE` 写成自动化测试通过。
- GitHub 专用私有仓库：https://github.com/g2066203208-beep/mc-studio。初始化 PR #1 已合并到 main，合并提交 facfadeeafe4fcfe055d33754259885012196e4d。
- 仓库应包含全部工程文件及 Gradle wrapper JAR；不要上传 local、build、存档或第三方整合包。
- 已验证云端构建：https://github.com/g2066203208-beep/mc-studio/actions/runs/39402002769。对应 PR head 为 11c6feca3e25ca2f5be8e9a86a04a94b756ec036；build 和上传产物步骤均成功。
- 下一任务：根据用户玩法需求实现首个功能，然后独立分支、PR、Actions，再由本机会话进行游戏验证。
- 网页聊天只有实际提供 GitHub 写工具或可写执行环境时才能修改和提交。若只有搜索/阅读工具，明确说明能力缺口，不声称已提交。

## 交付记录要求

每个任务记录：需求、实现、分支/提交/PR、构建结果、游戏实测结果、已知限制、下一步。
游戏启动、完整整合包兼容和本机部署由具备本机访问能力的会话验证；编译成功不能替代这些验收。

## 可复制给另一个聊天

请接手这个 GitHub 仓库的 Minecraft Mod 开发。先读取 AGENTS.md、README.md、docs/STATUS.md 和 docs/HANDOFF.md，检查最新提交与相关 PR，再继续任务。默认 MC 1.21.1、NeoForge 21.1.248、Java 21。使用独立任务分支，构建后提交代码并同步交接记录；明确区分编译结果和游戏内测试结果。


## 2026-10-20 新任务：玩家型 NPC Mod

- 开发分支 `feature/player-npc-v1`，初始实现提交 `9215da0d53c03d0d9435b633fcceca5306e1703f`。
- 已编写实体、玩家模型渲染、服务端控制指令、36 格物品存储/装备、基础 AI 和 NBT 保存代码，详情见 `docs/NPC_V1.md`。
- 已通过 GitHub Actions `39453494702`（push）和 `39453501071`（PR）编译；JAR 产物 `12279378797`，验证提交 `b51773ecebda40705fc54009baa40aee80f34e0e`。**未运行客户端与整合包，不得写成玩家全部能力复刻完成**。
- 待办：修复 CI 编译、验证头身比例与右键交互，添加 PlayerAction（真正物品使用/挖掘/建造）、盔甲副手、存档死亡掉落和工作 AI。


## 2026-10-21 用户纠偏：NPC ≠ 玩家外形傀儡

新验收目标明确是「活世界 NPC」：借鉴星露谷日程/情谊、RDR2 环境情境反应/社会后果、Skyrim AI Package 的活动包。此前 /npc follow/stay/guard 只是技术骨架，并非用户想要的内容。**禁止继续以更多命令或对话句子冒充真实生活模拟**。参见 `docs/LIVING_WORLD_DESIGN.md`。

最小垂直切片验收：铁匠/农民有住处、工作地点、时间表、饥饿和疲劳、至少一种生产行为、有跨天记忆与人际关系、遭遇威胁会重排计划、存档持续。系统运行不需要玩家跟随或输入命令；客户端能观察行为与原因。向他人交接时优先阅读新设计。


## 活世界基础内核开发交接（2026-10-21）

- PR #10 保持 draft；Issue #11 为真正生活 NPC 可玩验收。研究设计文档 `docs/LIVING_WORLD_DESIGN.md`。
- 已提交 `LivingPlanner`（需求日程、天气、威胁、性格、决策回滞）和 `ResidentMemory`（有界事件账本、逐人好感），并在 GitHub Actions 39455827090 的独立 Java 单测 + 完整 Gradle Build 均通过，代码提交为 `915c9ec561469f91e81c5dd18dfd5814b291668b`。
- 这些是**未接入原型实体**的纯 Java 核心。下一位开发者必须先把决策结果连接到世界行为执行器、NBT/SavedData、POI，再实现真正的食物消耗/生产、相互交谈、声望后果；不能宣称已可用的“活世界”。


## 2026-10-21 活世界 0.2 实际开发交接

- 2026-10-21 在分支 `feature/player-npc-v1` 添加游戏内 LivingWorldActions、LIVE 默认状态与实体 Tick、内置需求与社会记忆 NBT、玩家袭击反应、真实作物收获/重种、面包师麦子换面包并工作台制作面包、六居民测试生成、状态检查。
- 验证提交 `b27de8cff1f4b7f222255d5adea94038ea4d0634`：push Actions 39457871574 SUCCESS、PR Actions 39457879491 SUCCESS，ZIP artifact `12281113336`。
- 下一步：仅在隔离存档下检查实体实际路径、农田交易重种、加载/卸载与记忆恢复。**未通过真实游戏客户端/服务端/整合包测试**，PR #10 仍应作为草稿，不可合入 main。
- 演示：`/npc demo`（管理员）→ 自主 6 居民；`/npc info` 检查附近 NPC；`/npc sethome` 和 `/npc setwork` 为指定居民定位，空手右键问候；`/npc live`、`/npc stay` 切换生活模式。
- 不要将旧 NPC v0.1 的跟随命令当成最终产品；读 `docs/LIVING_WORLD_PLAYTEST.md`。


## 0.2.0-dev 最新交接（2026-10-21）
- 分支 feature/player-npc-v1 HEAD 实现与版本提交 4a8b87d25c56ce7f01639265eaea6afd196a82ea。
- push CI 39458311637 SUCCESS，PR CI 39458319744 SUCCESS；PR 成功产物 ZIP 12281915935。
- 要继续：先获取 ZIP 内 reme_studio-0.2.0-dev.jar，启用隔离测试世界，执行 /npc demo 与 /npc info，测试农田、面包师、NPC 受攻击反应、卸载/加载区块与 NBT 存储。不能把构建 success 等同游戏实测。


## 2026-10-21 社会系统 0.3 开发交接

新分支 `feature/living-world-society-v03` 基于 `feature/player-npc-v1` HEAD aff1ad4a4d0baeb9bbd9641185c154791fbff5d8。参见 `docs/SOCIETY_V03.md`。实现 Nation / Faction / Region、LifeJourney、局部道路偏好、守卫护送、侦察员探索/拾取、绑定箱子物流、面包师对玩家实际售货、需管理员确认并使用材料的小屋建造。只读后才能接手，注意原型未做实机验证；不可宣称采矿/钓鱼已完成。


## 2026-10-21 v0.3 后续接手

- 独立开发 PR #12（base `feature/player-npc-v1`），任务分支 `feature/living-world-society-v03`。功能增加三列 `/npc squad` 小队编队、`/npc disband`、运输员农田→箱子真实货物、敌对阵营禁交易、负面记忆触发面包师拒售。
- 未完成采矿/钓鱼/真正领土外交/离屏模拟已创建 Issue #13，生产级世界编辑要按 NeoForge 的 player block event + protection 检查执行。
- **无真正客户端/服务器运行证据**，只有 CI 源码编译和纯 Java 测试。下一会话先核对 PR #12 的 Actions 与 artifact，再做独立世界实机测试。


## 2026-10-21 v0.3 测试已完成（仅 CI）

独立任务分支 `feature/living-world-society-v03`；完整实现代码提交 `e05f765e65de7234e49ac83b3b0abc999c284310`，后续仅更改文档。
Push CI `39518033958` SUCCESS，PR CI `39518039426` SUCCESS。Artifact `12310085514` 包含 `reme_studio-0.3.0-dev.jar`（SHA-256 5f480e86d8f2808c52b41662de1a5cf362c3226569099a0f7eead88d2e5eb3f3）。已经从 ZIP 检查上述类存在。**仍无客户端游戏实测。**
后续先读 `docs/SOCIETY_V03.md` 执行隔离世界验收，重点观察国家阵营敌对关系、组队跟随、指定原版箱子资源守恒与 5×5 房屋施工。PR #12 保持 draft。


## 2026-10-21 civic v0.4 交接

- 新分支 `feature/living-world-civic-v04` 从 v0.3 最终验证提交 `c9dcaed19fe2f2b69e6c9bc0b5d8d853eded3815` 分出，全部开发独立于 main。
- 新增 `CivicMind`（目击事件 UUID、来源、二手传播/去重/边界）、`CharacterProfile`（诚实/正义/好奇/同理）、`CivicSocial`（受攻击见证、本地传播、守卫警示）、`Commission`+ `CivicContracts`（真实绿宝石保管资金、单人承接、不可重复领奖的交付）、`CivicPersistence`（NBT）。
- 玩家指令：`/npc law` / `/npc news` / `/npc commission WHEAT 3 2` / `/npc job` / `/npc accept` / `/npc deliver` / `/npc canceljob`。
- 自动测试：`scripts/living-tests/CivicSpec.java` 独立断言加入 `scripts/test-living-planner.sh`；Minecraft 真机型 `CivicGameTests.java` + `data/reme_studio/structure/empty.nbt`，.github/workflows/civic-gametest.yml 云端运行 GameTestServer。
- 看 `docs/CIVIC_V04.md` 验收清单；真实 GameTest、客户端和整合包的结果请以 Actions 证据判定，严禁擅自标记成功。


## 2026-10-21 Civic v0.4 交付

- 单独 draft PR #14：`feature/living-world-civic-v04`，base=`feature/living-world-society-v03`。源提交 `4c1f6d3503a79f86e1820f2fe334e33292af1d3e`，后续文档提交不改变 JAR Java 类。
- 最终 Mod JAR 111428 bytes，SHA-256 9fffdd8a99db74671f64bcc518d306ab64244a5dc4e475e9c16b33716200529e。Actions 39520744762 push 和 39520751645 PR BUILD SUCCESS。
- 真 NeoForge 游戏测试服务器：39520461688 的 4/4 required GameTests 均通过；最新 4c1 的相同测试运行结果需查 39520744874，不能在其完成之前声称最终版本四项均通过。
- 特性：NPC 自主求粮并以自己绿宝石悬赏、当地事件证据/见证人/弱可信传闻、商店拒售、守卫法律警示、性格、可实际完成且资金守恒的委托和对话播报（不会真正说语音）。仍需手工实机世界体验、客户端与整合包测试。


### v0.4.0-dev 最终源提交 GameTest 复核
- `4c1f6d35` 的 [39520744874](https://github.com/g2066203208-beep/mc-studio/actions/runs/39520744874) **SUCCESS**；日志明确 `All 4 required tests passed :)`。此前“需查最新 GameTest”的事项关闭；客户端/222-mod 整合包仍未测试。


## 2026-10-21 v0.5 自然居民与性能预算交接

- 独立分支 `feature/living-world-natural-v05`，基于 v0.4 `577d66d582193ac3ae3e7df0b51f54ab36cd7bbd`。
- 新增 `NaturalResidentSpawn` + NeoForge datapack biome modifier，在平原/草甸等主世界地表稀有刷出自然居民，周围 72 格最多 4 人；`PlayerNpcEntity.finalizeSpawn` 初始化天然居民的居住点、个人范围、性格、职业与食物。
- 新增 `MovementPolicy` `DailyRoaming` 及纯 Java 测试，`RoadNavigator` 重算冷却；AI 决策与专业职业执行降低触发频率，农民缓存成熟作物目标。
- /npc radius 16..128；新增 2 个 NeoForge GameTest 实体生成初始化/NBT 居住范围恢复。老四项测试必须一起通过。
- 详读 `docs/NATURAL_V05.md`；不能把原版自然刷新已随机观察、TPS 已测或 222-mod 整合包兼容当成已验收。


## 0.5 后续代码提交说明
- 对 `CivicGameTests.local_spawn_cap_prevents_overpopulation` 删除“整个测试世界开始必须空无居民”的不可靠并行前提，因其他 GameTest 可能同时创建实体。自然 NPC 限制仍在。
- `/npc info` 无需所有者身份即可只读查看最近 16 格 NPC；`/npc adminradius 16..128` 可由管理员调整自然居民活动范围，普通 `/npc radius` 只对自己的 NPC 生效。
- 2026-10-21 GitHub GameTest 39525005713 对自然生成+范围+人口限制等 7 项 **成功**。最终的 HEAD 构建与 GameTest 应以更新后版本为准。


## 2026-10-21 v0.5 最终交付（编译 + 游戏服务器自动化测试）

- 代码源 `6393acd5dcd74966b36e5d7072c5033574ada040`。PR #19 draft，base=`feature/living-world-civic-v04`；该功能还未合入 main。
- GitHub PR Build `39525522849` SUCCESS，NeoForge 真实 GameTest `39525522800` SUCCESS，日志 `All 7 required tests passed :)`。
- JAR `reme_studio-0.5.0-dev.jar` 121888 bytes，SHA-256 `c1ee90cf5529430fbd7a322f2c968b0b3fbc43bb90e0a02bde25d60d767356ca`，artifact 12313600974，已经打开 ZIP 核查类和 biome modifier。
- 代码中 NPC 在选定主世界地表稀有生成，附近 72 格 cap4，自动 512×512 地区标签、居家范围与白天漫步；缺少真正家庭居住分配与长期性能测试。
- 下一阶段优先实机测试新自然世界实际刷新概率/路径与数天日常动作，再做 20/100/200 NPC 稳定负载 profiling、持久人口台账和完整整合包兼容；不要说已经完成这些。


## 2026-10-21 用户运行反馈与 v0.5.1 hotfix
- Hotfix 分支 `fix/npc-runtime-v051`，PR #23，基于 v0.5 draft #19；不能仅依赖旧 v0.5 GameTest 宣称客户端可用。
- 真正修复低概率 spawn 与拒绝 CHUNK_GENERATION、假导航冷却；真实 Minecraft server GameTests 10/10 [39537399622](https://github.com/g2066203208-beep/mc-studio/actions/runs/39537399622) 成功（包括通过 FakePlayer 命令执行真实 /npc demo 生成六 NPC、实体走动、plains spawn 配置加载）。
- 首次尝试用 Xvfb 启动 NeoForge 客户端：Render thread 确认已加载 `mod/reme_studio`，超时 180s 主动结束 [39537393170](https://github.com/g2066203208-beep/mc-studio/actions/runs/39537393170)。不是手工 HMCL、不是实机视觉验收。
- 最新 JAR 0.5.1-dev: 124714 bytes, SHA-256 aa61ce2e079b08e395cddbc4e54fc7213a7a51c37483e03dbc8b3f518dd1ac88, artifact 12317652189, Build 39537399591 successful。
- 玩家反馈未解决前勿合入 main；可索取实际 HMCL `logs/latest.log` 以定位差异，同时继续补客户端交互与进世界测试。


## 2026-10-21 NPC 开源源码审查与 v0.5.3 区块出生修复
- 阅读实际 Java 源码而非 README：Millénaire `GoalScheduler` / `WaypointNavigator` / `PerVillagerThrottle`，Kithkyn `RoamGoal` / `BedtimeWithoutBedGoal` / `UnstuckPersonGoal`，MCA Reborn `SchedulesMCA` / `VillagerBrain`，MineColonies `TickRateStateMachine`。
- 精确源码链接、调用逻辑、和 REME 的差距见 `docs/OPEN_SOURCE_NPC_CODE_AUDIT.md`。**不能说已移植这些项目；只是完成源码学习，并发现并修复一个具体 bug。**
- `fix/npc-born-lifecycle-v053` PR #27 独立于 main；统一 NATURAL / CHUNK_GENERATION 初始化身份，两个原因均应用局部人数限制。NeoForge GameTest **12/12 PASS** [39542185523](https://github.com/g2066203208-beep/mc-studio/actions/runs/39542185523)；Build SUCCESS [39542185445](https://github.com/g2066203208-beep/mc-studio/actions/runs/39542185445)。
- JAR artifact 12319922214，v0.5.3-dev 127188 bytes SHA256 ee837d2b02f9d7baf65d8e13b646323ad04d97904e3a39fbfc17916a6b012ab9。
- **重要**：玩家 HMCL 截图中 `/npc` 根命令不存在尚未获得玩家实际运行环境日志，客户端对其真实可用性没有验收。禁止以 CI 代替。


## v0.5.4 最终运行与交接

## 2026-10-21 v0.5.4 客户端真正进世界验收（首次完成）

- JAR 源提交 `e469663d8c648e22cdd26d339c5f3ca0f14665c9`；`226220f3d52c015c859bc39662881532674d3274` 只改图形客户端测试脚本。
- **Minecraft 1.21.1 + NeoForge 21.1.248 原生图形客户端**：GitHub Linux Xvfb/Openbox 用真实鼠标键盘 `xdotool` 完成首次辅助功能引导、点击 Singleplayer、打开新世界设置、开启 Allow Commands、点击 Create New World、启动集成游戏服务器；实际世界进入成功，非仅启动菜单。
- [实际图形客户端运行 39545260822](https://github.com/g2066203208-beep/mc-studio/actions/runs/39545260822) SUCCESS；artifact `12321243417` 含 11 张真实截图和 `client-gui.log`, `run/logs/latest.log`。日志 `Dev ... logged in with entity id`、`REME NPC: /npc present on dispatcher`。
- 在该真实世界，模拟键盘输入 `/remecheck`，聊天栏显示 `/npc registered=true`；`/npc spawn` 成功，在客户端渲染出 Steve 模型 NPC；`/npc info` 真正回显饥饿、精力、社交、意图与区域；`/npc demo` 生成 6 名各有姓名和职业的居民，画面可见多个实体；`/time set night` 真实切夜且画面中的居民仍在。**这次截图由实际游戏窗口截取，非 AI 图像或服务端模拟。**
- NeoForge GameTest [39545266331](https://github.com/g2066203208-beep/mc-studio/actions/runs/39545266331) SUCCESS，**13/13 required tests**，含服务端 FakePlayer 空手右键属于自己的 NPC，确认问候记忆实际增加。**没有用 Xvfb 图形鼠标手工右键并打开背包界面**。
- **真实 Windows PowerShell 安装测试** [39545266342](https://github.com/g2066203208-beep/mc-studio/actions/runs/39545266342) SUCCESS：非交互干运行不改 mods、备份重命名旧版 REME、不误删其他 Mod、新版安装 hash 校验通过。
- Java21 + NeoForge 编译及双 ZIP artifact [39545266219](https://github.com/g2066203208-beep/mc-studio/actions/runs/39545266219) SUCCESS，installer artifact `12321820441`，ZIP `REME-NPC-HMCL-v0.5.4-installer.zip`，完整解压得到 `Install-REME-NPC.cmd`、`Install-REME-NPC.ps1`、`README.txt`、`reme_studio-0.5.4-dev.jar`。JAR 128021 bytes, SHA256 `c31c085d490d3a60353ae4ffd098c1d9736a6dd6b027d40b09b9cf2a5c277568`，已解包并确认元数据 ModID=reme_studio，version 0.5.4-dev。
- **尚未完成**：用户自己的 HMCL 游戏实例是否已经载入正确版本、222 Mod 整合包兼容性、真实 GUI 右键与背包窗口、长时间自然生成/数日行为、床/工作台 POI、充分的生活生产、20/50/100/200 NPC TPS。不得以已进入 CI 测试世界推论“所有人环境可用”。
- 已发现轻微文案问题：v0.5.4 Mod 元数据正确，但部分自检/登录提示仍显示旧 `v0.5.2`，只是字符串未更新，不应误当实际安装的 JAR 版本；以模组界面的 Mod 版本和文件 SHA 为准。


## 2026-10-21 NPC 守卫巡逻 GameTest 修复 v0.5.6

- 分支 `fix/npc-guard-patrol-v056`，草稿 PR #36（base `feature/npc-character-skins-v055`）。**不要误合并到 main**：该分支是 v0.5.5 修复，和 PR #34 的视觉依赖安装分支是并行的，尚未整合。
- 观察到 v0.5.5 源提交 `2d166478` 的真 NeoForge GameTest [39565701732](https://github.com/g2066203208-beep/mc-studio/actions/runs/39565701732) 为 **15/16**，失败项为 `guard_actually_walks_patrol_in_live_mode`（守卫未实际离开工作台）。
- 修复源提交 `12e42ccab2f874f9ae8c41373614535202242fc8`：守卫检查 40 tick，保留正在运行的导航，原巡逻点不可走时按至多 4 方位×3 距离的固定预算寻找安全地点，不加载未加载的区块；测试舞台与其他并行 GameTest 垂直分离，并要求实际**水平移动**。
- **已核验**：NeoForge GameTest [39572991788](https://github.com/g2066203208-beep/mc-studio/actions/runs/39572991788) **SUCCESS、16/16 required passed**，日志 `All 16 required tests passed :)`；[Build 39572991773](https://github.com/g2066203208-beep/mc-studio/actions/runs/39572991773) **SUCCESS**；Windows 安装器测试 [39572991713](https://github.com/g2066203208-beep/mc-studio/actions/runs/39572991713) **SUCCESS**。
- 未完成：用户 HMCL 所在实例验收、多人实际场景卡路恢复、长时间 TPS、PR #34 3D Skin Layers/EyeMod 自定义 NPC 渲染器适配，以及用户指定的自绘像素 64×64 基础+外套双层皮肤替换旧下载贴图。PR 保留草稿，不能声称这些已完成。


## 2026-10-21 玩家等价行动方向：PlayerActionBridge 原型

- 用户纠偏：NPC 应复用玩家本身能力（真实物品、库存、挖掘、战斗、搭建、交互），而不是继续手写 Mob 直接更改方块的行为。仍可保留 `PathfinderMob` 作为导航与客户端实体，因为 `FakePlayer` 是服务端交互代理，不是可直接替代的玩家客户端实体。
- 研究 https://github.com/Goodbird-git/PlayerEngine (LGPL-3.0，1.21.1 NeoForge 有发布版本；阅读 `LivingEntityInteractionManager` / `LivingEntityInventory`、接口架构) 和 NeoForge `FakePlayerFactory` / `ItemStack.useOn`。**只参考能力分离架构，未复制源代码也未将第三方库添加为运行时依赖**。NeoForge issue #1487 提醒 per-NPC FakePlayer 会有 PlayerAdvancements 监听生命周期问题，因此调用 `stopListening()`，且在本实验只允许管理员授权的建造事务；后续需要真正长跑/卸载内存监测。
- PR #37、代码源提交 `9a6fd5003e1f71b2224a9ad658a7b0292fe18b7a`，新增 `PlayerActionBridge`，基于 ServerLevel 的假玩家进行实际玩家物品使用。旧 `SafeBuilder` 的直接 `setBlock` 替换为 `ItemStack.useOn(UseOnContext)`，要求已加载区块、合法支持面、NPC 在交互距离内、拥有对应真实材料、物品已真正减少，放置目标实际匹配。失败时保留 NPC 背包。
- 游戏测试新增一项，确保未授权不能放置、正确材料真实放置并消耗一次、重复/错误材料拒绝。新 `runGameTestServer` [39574791216](https://github.com/g2066203208-beep/mc-studio/actions/runs/39574791216) **17/17 required passed**；[Build 39574791189](https://github.com/g2066203208-beep/mc-studio/actions/runs/39574791189) **success**；Windows 安装器 [39574791312](https://github.com/g2066203208-beep/mc-studio/actions/runs/39574791312) **success**。客户端截图 [39574786212](https://github.com/g2066203208-beep/mc-studio/actions/runs/39574786212) 仅为独立 Xvfb 功能拍照回归，必须查看图像才可声称实际展示新建造步骤（脚本不会直接展示建造）。
- **未实现/验证**：玩家完整挖掘进度、工具耐久消耗、多种 mod 工作方块、合成界面、玩家背包等价、网络玩家身份、区域保护模组适配、长跑 FakePlayer 内存、真实动态双层 NPC 皮肤。没有完整玩家等价能力，不要标记整个项目完成。
- 本分支基于 `fix/npc-guard-patrol-v056`，与 PR #34 视觉依赖测试是并行开发，仍需安全合流和游戏内完整组合测试。


## 2026-10-21 v0.5.7 玩家式实际挖掘 P1：最终客户端和 GameTest 验收

- 专业实施 Prompt 与 P0–P6 路线图：`docs/NPC_PLAYER_PARITY_MASTER_PLAN.md`；实测总结和复盘：`docs/NPC_PLAYER_PARITY_V057_PLAYTEST.md`；总控 Issue #41；独立 draft PR #39（based on `feature/npc-player-action-bridge-v056`，**绝不直接合入 main**）。
- 编码源码验收提交 `351536ac5c6f4d62daff4045658061af37b9131c`；后续仅文档更改，JAR Java/资源不变。实际功能是带真实工具磨损和原版生存速度的授权单目标挖掘；NeoForge FakePlayer player gameMode.destroyBlock 负责破坏事件与掉落，不是绕过 API 直接修改方块。防错误镐子、超距、未加载区块、越过家园边界；危机打断、NBT 保留目标但重置进度；FakePlayer 复用一份身份。
- [NeoForge GameTest 39585377144](https://github.com/g2066203208-beep/mc-studio/actions/runs/39585377144) **22/22 SUCCESS**；[Build 39585377197](https://github.com/g2066203208-beep/mc-studio/actions/runs/39585377197) **SUCCESS**；[Windows HMCL 安装模拟 39585377100](https://github.com/g2066203208-beep/mc-studio/actions/runs/39585377100) **SUCCESS**。JAR `reme_studio-0.5.7-dev.jar` 165572 bytes，SHA256 `cd95c3089bad7524fd0fc3f46e692e242b148a5f06b34f5bde1fcdefc5b9694b`，真实打包 artifact 12338173240。
- **NeoForge 真实客户端进世界采矿验收**：[39585369776](https://github.com/g2066203208-beep/mc-studio/actions/runs/39585369776) **SUCCESS**，artifact 12338619819（19 张真实窗口截图与日志）。模拟键鼠召唤 MinerGUI，给予真实铁镐并放置石头，执行 `/npc mine` 后原版世界状态条件断言 `REME_MINING_CONFIRMED_AIR` 成功，石头消失且世界出现圆石掉落；`/data get entity` NBT 显示镐子 `minecraft:damage: 1`。不是 AI 生成的图片，也不是单看命令成功。
- 首轮图形断言曾在 [39584228885](https://github.com/g2066203208-beep/mc-studio/actions/runs/39584228885) **失败**：GUI 测试有六个旧 NPC，铁镐按最近实体选错；脚本给 `/tp` 使用错误语法。修复为仅隔离测试世界清理其他 NPC、铺实地测试平台、按 MinerGUI 实体名装备、使用 `/tp @s ...`，并保留原石→空气和工具 NBT 硬断言，最终 39585369776 绿。
- **严格范围声明**：P1 完成不等于“与玩家所有功能完全相同”。还未做完整容器/副手/物品自动收集/工作台合成/采矿职业自主寻矿/POI/长期生命循环/自绘双层 3D 皮肤/动态眼部帧/多人口长测，未在用户实际 HMCL＋222-Mod 整合包验证。后续优先 P2 行为适配器+真实客户端截图验证，不要将 BUILD SUCCESS 冒充完美兼容。


## 2026-10-21 P2 0.5.8-dev NPC 采矿→地面掉落→背包（等待客户端最终确认）

- 独立草稿 PR #47 `feature/npc-resource-pickup-v058` 以已验证 P1 采矿 `feature/npc-player-parity-mining-v057` 为基底，未合并主线。版本 `0.5.8-dev`、MC 1.21.1 NeoForge 21.1.248 Java 21。
- **实际实现**：`PlayerMiningActions` 用 NeoForge `BlockDropsEvent` 捕获本 NPC 采矿时实际产生的 ItemEntity UUID 与原始数量；`PlayerLootPickup` 通过原版移动接近物品，按拾取延迟拾取，回填有 NBT 保存的 36 格背包。部分背包插入留地面余量，背包满不删除物品。同类实体合并后最多只领**本次原始数量**，不能顺手把玩家已有地面物品拿走；认领 UUID/剩余数量/超时有限度保存，不加载新区块、不伪造物品。另修旧 RoadNavigator 的停止半径不能满足真实 1.5 格拾取距离的问题。
- 服务器实测：先 [39600581647](https://github.com/g2066203208-beep/mc-studio/actions/runs/39600581647) 失败 24/25（识别了掉落但寻路停在两格外）。修正后 [39600949806](https://github.com/g2066203208-beep/mc-studio/actions/runs/39600949806) 25/25 成功。加入物品合并数量防盗后，代码最终提交 `ce40daea972f11f7836ae15dc63a1c99021418a5` 对应 **[39601264725](https://github.com/g2066203208-beep/mc-studio/actions/runs/39601264725) 26/26 SUCCESS**；[Build 39601264727](https://github.com/g2066203208-beep/mc-studio/actions/runs/39601264727) SUCCESS；[HMCL Windows Installer 39601264719](https://github.com/g2066203208-beep/mc-studio/actions/runs/39601264719) SUCCESS。CI JAR `reme_studio-0.5.8-dev.jar` SHA-256 `a729f099476a76601426c5cdf8c338a8f27303290a483c20394eedcc72a2115d`、installer ZIP SHA-256 `c0597e59adaa8d42008bef018ce636f0e240650f87616235ebcd64c617b826ee`。产物 artifact `12345084715`。
- **当前 GUI 已通过**：[39601256708](https://github.com/g2066203208-beep/mc-studio/actions/runs/39601256708) **SUCCESS**；日志明确 `PASS: integrated Minecraft confirmed mined stone -> air and cobblestone inside NPC persistent backpack`，退出码 0；游戏内 `NpcBackpack` NBT 为 `{count:1,id:"minecraft:cobblestone"}`，并由 `/execute if data entity ...` 成功验证 `REME_NPC_LOOT_IN_BACKPACK`。原始 21 张真实 MC 截图+日志 artifact [12345944972](https://github.com/g2066203208-beep/mc-studio/actions/runs/39601256708/artifacts/12345944972)；已下载目视确认物品存在，属于与服务器 26/26、Build 一致的真实源码。
- 文档 `docs/NPC_RESOURCE_PICKUP_V058.md` 解释认领协议、失败复盘、用户测试与缺失项。后续仍需真正背包/热栏/盔甲/副手、配方合成、授权职业 POI 自主矿工、3D 自绘 NPC 皮肤、HMCL 222-mod 实机、20/50/100/200 压力测试。**没有**把这些缺失标记为完成。

## 2026-10-21 活世界 0.5.9 真正道路聚落商队（本轮）

- 独立草稿 PR [#51](https://github.com/g2066203208-beep/mc-studio/pull/51)；分支 `feature/npc-emergent-roads-hamlets-v059`，based on 已验证 v058 的 `feature/npc-resource-pickup-v058`，**没有合并 main**。
- 核心代码：`FootTrafficData.java`（维度独立 SavedData 4096 格脚步历史）、`EmergentFootpaths.java`（初始默认 OFF，OP /npc trails on，7 次真实踩踏并 NeoForge shovel/rightclick 权限事件后自然草地→草径）、`RoadNavigator.java`（热度路径偏好）、`EmergentSettlements.java`+`SettlementLedger.java`（三户不同真实家园/盟友聚落 + 128 条持久化事件）、`BanditActions.java`（敌对 Raider 抢劫实体运输员实际小麦，犯罪记忆、目击者与冷却）、`CaravanRoutes.java`（管理员看两箱子设置 /npc route pickup 和 /npc route dropoff，小麦背包实物转移、箱子满不停造货）、`LivingWorldActions.java`（商队任务拥有导航优先权）。
- 真 Minecraft 服务端 GameTest [39614384093](https://github.com/g2066203208-beep/mc-studio/actions/runs/39614384093) **34/34 SUCCESS**，功能源码 `6d91d96dc2c64eee4d2cd2a4d923198c55303cd8`；同源码 [Build 39614385088](https://github.com/g2066203208-beep/mc-studio/actions/runs/39614385088) **SUCCESS**；[Windows PowerShell HMCL 隔离安装器 39614384222](https://github.com/g2066203208-beep/mc-studio/actions/runs/39614384222) **SUCCESS**；[真客户端 39614376551](https://github.com/g2066203208-beep/mc-studio/actions/runs/39614376551) **SUCCESS**，截图日志 artifact [12352925193](https://github.com/g2066203208-beep/mc-studio/actions/runs/39614376551/artifacts/12352925193)。实际画面确认 3 real households 聚落、74 recorded foot tiles 初始 OFF、管理员开启 ON、真实矿工圆石进入 NpcBackpack。Grass→dirt_path 和工具取消事件在 NeoForge GameTest 中验证，**不是** GUI 显示已经有成熟路面。
- 整套可安装 v0.5.9-dev ZIP 包含真实 JAR/Install-REME-NPC.cmd/ps1/README，JAR SHA256 `bd4fcad99d9937a4c95f279964101fcfbb4db38920764ab9ee90b79a122147bc`，完整 ZIP SHA256 `ae553a076de4e8f1525923811ad7ae645183710dc48d4453369faac986a8dc6e`。这两个哈希属于 **6d91d96**，后续改动的版本不得不经核验直接沿用。
- 失败纪录不能隐瞒：初版自动商队实际卡在箱子碰撞块/受普通 AI 导航覆盖；旧 GameTest 错把多趟运输当失败。修复后 34/34 成功。一轮沿海随机世界图形测试 [39614036870](https://github.com/g2066203208-beep/mc-studio/actions/runs/39614036870) 未捡到圆石失败；后续同源码 39614376551 成功，客户端环境随机拾物竞速仍可能导致偶发失败。
- **之后另添、当前需排队验收**：`EmergentSettlements.observe` 所有 LIVE 农民、面包师、运输员无需玩家命令自动记录“聚落成立”到 `SettlementLedger`，275-tick NeoForge `actual_different_professions_found_hamlet_without_player_command` 直接等待3位实体生活建史；另在真实客户端脚本加聚落/路网 OFF/ON 系统聊天硬断言。对应 [最新 GameTest 39616286901](https://github.com/g2066203208-beep/mc-studio/actions/runs/39616286901) / [GUI 39616279123](https://github.com/g2066203208-beep/mc-studio/actions/runs/39616279123) 在交接时间 **QUEUED**，不能将旧的 34/34 写成新代码 35/35 通过。
- 下一阶段：解决 GUI 测试的玩家与 NPC 争抢圆石导致随机失败（独立角色挖矿测试建议命令目标相对坐标独立后，让玩家站远，但保持能够给最近 NPC 下命令）；优化多仓商路/真正越过居住半径的旅行、被抢物品回收与护卫、城镇建筑经济政治、中文交互 UI、复杂 GameTests、更长 20/50/100/200 人 TPS/内存和用户实际 222-Mod HMCL 整合包验证。
- 技术参考与 Prompt 文档 `docs/EMERGENT_WORLD_V059.md`，完整主规划 `docs/NPC_PLAYER_PARITY_MASTER_PLAN.md`，Issue #41。


## 2026-10-22 v0.6.4 实体建筑+店员补货交接（独立实验分支）

- 开发分支 `feature/npc-physical-workplaces-v064`，草稿 [PR #72](https://github.com/g2066203208-beep/mc-studio/pull/72)；承接 v0.6.3，没合并 main。主要变更：`CivicWorkplaces.java` 对实际已加载世界扫描 SHOP/BAKERY/SMITHY/FARM/INN/LIBRARY/GUARD_POST，区分配套工作站和真正带屋顶/双格门的完整建筑；`ShopCounter.java` 从 OP 认定的真实箱子最多转移8份食品，原背包存档与交易继续沿用；`SafeBuilder.java` 新增 SHOP 与 BAKERY 真实 5×5×3 建设蓝图（先后消耗方块、实体家具及门），保留旧 SHELTER；`/npc workplace`、`/npc shopstock set|clear`、`/npc buildplan shelter|shop|bakery` 命令。
- 功能提交 [4d8548d5](https://github.com/g2066203208-beep/mc-studio/commit/4d8548d5340438cfc3c089af19cbeab83f58665a) 首次 [真正 NeoForge GameTest 39700140061](https://github.com/g2066203208-beep/mc-studio/actions/runs/39700140061) **49/49 通过**（包括建筑模板存档、第一块实际施工/真实消耗、不能覆盖已有方块、真货物补货）。另有一次 49/49 失败记录，随机生成地图条件影响石头挖矿/路径等原有用例，不能声称稳定0失败。
- 同源码 [Build 39700140120](https://github.com/g2066203208-beep/mc-studio/actions/runs/39700140120) **第一次 Maven Central gson POM HTTP429 限流失败，明确不是源码 Java 编译错误；对失败 job 重新运行后 SUCCESS**。GitHub 构建 package `installer-0.6.4-dev` id `12392812051`、解压两层 zip 后实体 JAR `reme_studio-0.6.4-dev.jar` SHA256 `e2a84e8022c8f37f6faf732d47fc28a5eea2f18db03875f94f0a282ddc1d4168`，内部4文件的 HMCL ZIP SHA256 `de6ac3d34f3dd225eed1c1b1bd96abb5e78b54c3930b5bb34020096a1b4c3326`，已用 `jar tf` 检查 `CivicWorkplaces/SafeBuilder/ShopCounter` 类。
- [实际 MC 客户端 39700140114](https://github.com/g2066203208-beep/mc-studio/actions/runs/39700140114) 在村落/道路成功之后矿工测试失败；[原始截图/日志 12392946959](https://github.com/g2066203208-beep/mc-studio/actions/runs/39700140114/artifacts/12392946959) 中明确可见 **Dev suffocated in a wall**，因为真人玩家测试脚本 `/tp @s ~ ~ ~-6` 被随机地图山体夹死；未执行完矿工验证。修复 `scripts/test-client-gui.sh` 提交 `9b9da0ce6b46d319d3b2aa2d9377867c15abce32` 在隔离临时CI世界传送前铺设实体地面、清理上方安全的空气区域，保留原命令验证方块→air、NPC 背包→圆石、Dev 玩家不能偷物。最新图形验收 [39701206511](https://github.com/g2066203208-beep/mc-studio/actions/runs/39701206511) 发起，还需以其最终 status/logs 为准。
- 玩家指引、研究和专业下轮 Prompt [docs/NPC_PHYSICAL_TOWNS_V064.md](https://github.com/g2066203208-beep/mc-studio/blob/feature/npc-physical-workplaces-v064/docs/NPC_PHYSICAL_TOWNS_V064.md)。未完成项目：商店/面包房从第一块到整屋完成的长周期实机测试（当前只验证真实第一块和完成蓝图结构）、7种职场中只有部分已有真正工作 AI，不能虚报铁匠/旅店/图书馆实际经营；NPC 当前实体仍 PathfinderMob，只构造了 detached ServerPlayer 原型，离玩家 mod 兼容差很远；自动城市/城堡与200 NPC硬性能门槛尚未实现。构建测试不等于全 modpack 测试。


## 2026-10-22 NPC 仓库审计与 0.6.4 回归修复交接

- 仔细核对了 main、draft PR #60（真 ServerPlayer 仍为未联网的隔离原型）、#65（商店/三餐，另有 GameTest 48×24×48 隔离修复）、#72（0.6.4 实体建筑与店员补货）；不可将 PathfinderMob / FakePlayer 短时动作误称原生玩家渲染与通用 Mod 兼容，亦不可把 main 的最小骨架作为最新 NPC 游戏源码。
- 独立子分支 `fix/npc-v064-reliable-workplaces` 从 `feature/npc-physical-workplaces-v064` 创建，不影响 main 或 WorldSeam/航空学项目。修复了 `CivicWorkplaces` 三块悬空板也可能误判 roof 的问题；新增实体世界 GameTest；恢复旧 v0.6.3 已验证的隔离测试 NBT 准备器。这个 GameTest 前置步骤只能在 CI 测试副本修改结构尺寸。
- 预期共 50 个 GameTest，CI 真实状态应由新分支 PR Actions 核查；在完成前不得将它们记为通过。Minecraft 客户端图形、222 Mod 和 200 NPC TPS/MSPT 本轮均没有声称已验收。
- 下一位开发者优先做两项真验收：在 48×24×48 隔离模板上运行全套 50 GameTest 并看日志，随后在真实客户端从 0/N 个材料按序建完 shop 与 bakery，核实每个 BlockItem 使用、家具、上下门、材料减少、日程/交易/补货，以及失效/中断/存档恢复。然后推进真正 ServerPlayer + 网络玩家渲染原型。


## 2026-10-22 v0.6.5 邻里互助 + 真 NeoForge 2400 Tick 压测分支

- 源分支 `feature/npc-mutual-aid-longrun-v065` 继承已验收的 `fix/npc-v064-reliable-workplaces` (#76, Build success, 50/50 server GameTests, Windows success)。仍不可改 `main` 或 WorldSeam。
- 新 `MutualAid.java`：饥饿真实居民从近邻**已有物品**得到 1 份食物；双方背包各自快照后事务提交，捐赠者保留 2 份，安全/派系/距离/视线/职业校验，原 `ResidentMemory.HELPED` 留关系与 600 tick NBT 冷却；接收后通过旧 `LivingWorldActions.eat` 真正消耗物品。
- 性能漏洞：原 `ResidentMemory.witness` 没有 live 时的关系个数上限，新增统一 128 关系上界和 400 独立联系人纯 Java 回归；保留原 48 事件上界。
- `CivicGameTests` 新增 3 项，共 53 个（源码计数）：真实一份食物互助/NBT、禁止跨区域与溢出与连续乞食、24 名 LIVE NPC 在真实 MC 服务端连跑 2400 world ticks。不是 200 NPC 性能结论、不是完整白天黑夜长时游玩或自动建城。
- `mod_version=0.6.5-dev`，同步 Windows HMCL PowerShell 安装测试文件；GameTest/真实 GUI workflow 加新分支触发，GameTest timeout 提升为22分钟。源/研究/人工说明见 `docs/NPC_MUTUAL_AID_LONGRUN_V065.md`。
- 必须回查本 PR 的 Java BUILD、纯 Java 18项、NeoForge 53 GameTests、Xvfb GUI 和 Windows installer 真实状态；发现失败逐个修复，不能冒充成功。仍未验证真正联网 `ServerPlayer`、用户222 Mod HMCL、20-200 NPC MSPT。
