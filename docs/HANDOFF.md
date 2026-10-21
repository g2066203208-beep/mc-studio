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
