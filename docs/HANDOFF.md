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
