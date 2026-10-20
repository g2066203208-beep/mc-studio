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
