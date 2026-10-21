# 验证状态

日期：2026-10-20（Asia/Shanghai）。

## 已完成

- 实机发现 HMCL 与 TerraFirma Rebirth 2 实例。
- 读取整合包 manifest 和版本描述，交叉确认 Minecraft 1.21.1、NeoForge 21.1.248、Java 21。
- 生成 222 个 Mod 的大小与 SHA-256 清单；清单脚本已运行成功。
- GitHub 插件验证账号 `g2066203208-beep`，能够列出授权仓库。
- 建立最小 NeoForge 工程、文档、代理适配构建脚本、CI 配置和独立本地 Git 仓库。
- 首次直连构建失败于 Maven 连接超时；读取现有 Windows 代理后构建成功。
- `Build.ps1`：Gradle `BUILD SUCCESSFUL in 8m 8s`，退出码 0。
- 检查 JAR 内容：包含 StudioMod.class 与 META-INF/neoforge.mods.toml。
- Git 暂存差异空白检查通过，本机配置、日志和产物被排除。
- 已补充云端构建入口 `scripts/build.sh` 与跨会话交接文档 `docs/HANDOFF.md`；云端不依赖本机路径。
- 22 个工程文件通过 GitHub 插件上传，初始化 [PR #1](https://github.com/g2066203208-beep/mc-studio/pull/1) 已合并。
- [GitHub Actions 39402002769](https://github.com/g2066203208-beep/mc-studio/actions/runs/39402002769)：Java 21、构建、上传 JAR 均 success。
- 验证对应 PR head：`11c6feca3e25ca2f5be8e9a86a04a94b756ec036`。PR 工作流使用 GitHub 生成的测试合并提交，因此 artifact 名字中的 SHA 可与 head 不同。
- 产物 ID：`12255270668`，创建时未过期，平台记录过期时间为 2027-01-18。Actions 产物是临时下载，不等于永久 Release。
- 本地已经通过 Git 拉取远程分支；原本地初始化历史保留在 `bootstrap/local-history`。

产物：`mod/build/libs/reme_studio-0.1.0-dev.jar`

SHA-256：
`64310FBC8447A3FC5FFE6D89CC708F425CFD4A083E489277BE6C88E0030DC11C`

完整成功日志：`local/build-proxy.log`。
这是开发骨架，仅记录加载标记，没有新增玩法。
Gradle 报告使用了将来不兼容 Gradle 10 的弃用功能；当前固定 Wrapper 9.2.1 构建成功，不自动升级 Wrapper。

## 尚未完成

- 该骨架没有测试源码，Gradle `test` 为 `NO-SOURCE`；不能称为自动化测试通过。
- 尚未启动开发客户端、专用服务器或完整整合包验证。
- 尚未确认 HMCL 的实际生效隔离目录，未向正式 mods 目录部署。
- 本聊天已验证 GitHub 插件写入及云端构建；尚未在用户的另一个网页版聊天中实际发起开发任务。那个会话仍须具备该私有仓库的访问权和写工具。
- 尚未针对具体玩法展开上游源码分析与功能实现。

真实游玩文件和存档未被修改。后续先根据用户目标确定实例与功能，再开展对应开发和运行测试。


## 玩家型 NPC v0.1 分支（2026-10-20）

- 初始提交：`9215da0d53c03d0d9435b633fcceca5306e1703f`，分支：`feature/player-npc-v1`。
- 添加 `reme_studio:player_npc`，详见 `docs/NPC_V1.md`。
- 编译：首轮 Actions `39453134471` 因错误注册 API 失败；修复后 **Actions `39453494702` push 构建 SUCCESS，Actions `39453501071` PR 构建 SUCCESS**。构建验证提交 `b51773ecebda40705fc54009baa40aee80f34e0e`，产物 `12279378797`，JAR `reme_studio-0.1.0-dev.jar`。编译 != 游戏内实测。
- 运行：客户端、独立服务器、222 Mod 整合包 **均未测试**。正式世界 **未部署**。


## 2026-10-21 NPC 目标调整

- 用户明确否定「听命跟随的人形 NPC」是终点，现阶段 JAR 仅作实体/渲染/背包基础组件。
- 已研究 Stardew NPC schedule、RDR2 contextual interactions、Skyrim AI packages，以及 MCA Reborn、MineColonies、VillAIgence 三个开源代码库；记录在 `docs/LIVING_WORLD_DESIGN.md`。
- 生活模拟功能 **尚未通过游戏内验收**。工作重点转为自主生存/工作、需求/关系/情境、世界事件因果链、离线模拟及性能；不再把跟随功能作为“活 NPC”里程碑。


## 2026-10-21 活世界内核 CI 证据

- `feature/player-npc-v1` 提交 `915c9ec561469f91e81c5dd18dfd5814b291668b`：新增不依赖 Minecraft 的 `LivingPlanner`（时间/天气/需求/危险与个性、惯性）与 `ResidentMemory`（最多 48 条见证事件，分人信任）。
- `scripts/test-living-planner.sh`：独立 Java 21 断言 **通过**，已经纳入 Actions。
- Github Actions [39455827090](https://github.com/g2066203208-beep/mc-studio/actions/runs/39455827090) **Build SUCCESS**。曾有一次测试发现配置引发的编译流水线失败 `39455501797`，通过将不使用 JUnit 的独立断言移至 `scripts/living-tests/` 修复。
- **核心决策/记忆类尚未连入 NPC 实体。不能称为 NPC 已自主生活、已生产、已有跨存档关系，也未做游戏内实测。**


## 活世界 0.2 实体执行层（2026-10-21）

- `feature/player-npc-v1` 新增真正的服务端居民日常操作：需求/天气/威胁决策、避险、带库存吃饭、回家休息、真实农田收割并消费补种种子、面包师工作台制作和与农民面对面交易、双向社会事件、个性和 NBT 恢复。
- 已验证 [push GitHub Actions 39457871574](https://github.com/g2066203208-beep/mc-studio/actions/runs/39457871574) SUCCESS 和 [PR run 39457879491](https://github.com/g2066203208-beep/mc-studio/actions/runs/39457879491) SUCCESS。构建源提交 b27de8cff1f4b7f222255d5adea94038ea4d0634，artifact ID 12281113336。
- 检查 JAR 类与 neoforge.mods.toml 已包含。
- 仍未完成实机游戏内验证、主线建筑/商人等职业、离线 NPC 演算、领地保护 API；真正的日常生活效果和存档重载效果尚未得到实机证明。

 
## 2026-10-21 最终 v0.2-dev 编译和产物

- 变更版本号 mod_version=0.2.0-dev；源提交 4a8b87d25c56ce7f01639265eaea6afd196a82ea。
- GitHub Actions push 39458311637 和 PR 39458319744 均 SUCCESS。
- PR Actions 生成可下载 ZIP artifact ID 12281915935；包内 JAR 预计名为 reme_studio-0.2.0-dev.jar，解包时检查名字。
- 实机客户端/专用服务器/整合包 **仍未测试**，本项是编译成功，不是玩法验收。


## 2026-10-21 v0.3 社会与领地身份特性

- 独立任务分支 `feature/living-world-society-v03`，在活世界 0.2 的实体、决策、农业和面包师基础上继续。
- 新增政治身份、人生目标与进度、道路局部启发式、守卫/侦察员/运输员、已指派的原版箱子物资转移、玩家面包购买、管理员明确许可下的材料消耗建筑。
- 纯 Java 社会规则单测新增并加入原有构建工作流。CI 结果与 JAR ID **需要核验后再填**。
- 钓鱼、任意采矿、真实国家主权/国界、动态政治、无载入区块经济模拟尚未完成。客户端和游戏内测试未知；无证据前保持 draft。


## 0.3 开发分支最终功能范围

在 0.2 基础上新增：国家/派系/地区标签、预置外交关系、人生目标与进度、附近道路优先导航、守卫和玩家护送、侦察和已加载区块发现、区域农田物资调拨给运输员及显式箱子物流、NPC 面包师向玩家出售真实面包且根据过去袭击拒售、管理员授权且消耗真实材料的小屋逐块建造、最多九人三列小队。
- 版本 `0.3.0-dev`，GitHub PR #12；最终编译结果请在 Actions 上核对，不得混用旧版 CI 成功证明新版可运行。
- v0.4 计划 Issue #13 聚焦 FakePlayer 安全采矿/钓鱼/国家实际疆域/离线模拟，均为 TODO。


## 0.3.0-dev 最新 CI 证据（2026-10-21）

- 源代码提交 `e05f765e65de7234e49ac83b3b0abc999c284310`。
- GitHub Actions 39518033958 push SUCCESS；39518039426 PR SUCCESS，Test living-world planner core、Build、JAR 上传全部完成。
- Artifact `12310085514`，JAR `reme_studio-0.3.0-dev.jar`，SHA-256：`5f480e86d8f2808c52b41662de1a5cf362c3226569099a0f7eead88d2e5eb3f3`。
- **仍没有游戏客户端/专用服/整合包 222 mods/TPS 的实测记录，不可误报为运行通过**。


## 2026-10-21 公民社会 v0.4 开发中

- 源代码已经加入目击、有限口口传闻、个人法律声望、性格细分、委托资金托管与交付、NBT 以及 NeoForge GameTest 测试类。
- 原本 v0.3 的纯源码构建成功 **不能**说明新系统实测通过。详见 `docs/CIVIC_V04.md`，跟踪新分支 `feature/living-world-civic-v04`。
- GameTest server 独立 workflow 要求真实服务器级别测试，这一项如失败必须记录失败原因，不得写成成功。


## Civic v0.4 CI 与 JAR 证据

- 源代码提交 `4c1f6d3503a79f86e1820f2fe334e33292af1d3e`，push Actions 39520744762 / PR Actions 39520751645 成功，包含 Java 单测、NeoForge 编译及上传 JAR。
- GameTest 39520461688：**4 required tests PASSED**（见游戏服务端实际日志）。这些 GameTest 是服务端注册生成、NBT 保存往返、区域传闻交换/限跳/去重、委托预扣资金和退款，不是手工游玩全流程。
- artifact 12310983491：`reme_studio-0.4.0-dev.jar` 111428 bytes，SHA-256 9fffdd8a99db74671f64bcc518d306ab64244a5dc4e475e9c16b33716200529e。
- 最近增量：NPC 会自行悬赏求粮，性格控制社交/自发探索，玩家能通过对话听到 NPC 所掌握的消息。
- **没有客户端实机、专用服多玩家手工操作、222-mod 整合包或长时间性能测试结果。**


### 最终源码 GameTest 已成功
- 最新源提交 `4c1f6d3503a79f86e1820f2fe334e33292af1d3e` 的 [NeoForge 游戏服务器 GameTest 39520744874](https://github.com/g2066203208-beep/mc-studio/actions/runs/39520744874) SUCCESS，日志 `All 4 required tests passed :)`。对应同源 Build 39520744762 SUCCESS。


## 2026-10-21 Living World v0.5 自然生成与性能调整

- 从 v0.4 成立独立 `feature/living-world-natural-v05`。编写原版 biome modifier 地表低权重自然 NPC、出生资料、局部人数上限、个体家园半径、白天活动/越界回家，以及路径寻找和农田扫描的性能预算。
- 仅完成 Java 源码/测试编写。CI/Gametest 成果需按最新提交核验；不能将实际 TPS 百分比当成测过。


## 0.5 第一轮实际 NeoForge 测试（2026-10-21）
- [GameTest run 39524861608](https://github.com/g2066203208-beep/mc-studio/actions/runs/39524861608)：游戏服务器真实运行 6 required tests，全部 PASS。
- [GameTest run 39525005713](https://github.com/g2066203208-beep/mc-studio/actions/runs/39525005713)：新增人口上限后，真实运行 7 required tests，全部 PASS。
- 最终客户端真实世界/野外稀有刷新/222-Mod/TPS 还未验证。查看最新 HEAD 的 Actions 才能宣布最终 JAR 已通过。


## Living World v0.5 最终 CI 核验

- Java 源提交 `6393acd5dcd74966b36e5d7072c5033574ada040`。GitHub [PR Build 39525522849](https://github.com/g2066203208-beep/mc-studio/actions/runs/39525522849) **SUCCESS** 与 [PR GameTest 39525522800](https://github.com/g2066203208-beep/mc-studio/actions/runs/39525522800) **SUCCESS**；GameTest 7/7 required passed。
- artifact 12313600974，`reme_studio-0.5.0-dev.jar` (121888 bytes), SHA-256 `c1ee90cf5529430fbd7a322f2c968b0b3fbc43bb90e0a02bde25d60d767356ca`。解包确认生物群系 spawn modifier 和模块类。
- v0.5 所有性能调整为可计数的触发频率/缓存优化，尚无对照 TPS 量测。真实 222-Mod 整合包未验收。


## v0.5.1 实际用户反馈后的纠错（2026-10-21）
- 用户明确反馈“没用”，以前只证明自动服务器 GameTests 通过，未进行真实客户端启动尝试、未实现完整生活行为。不能再声称“用户可正常玩”。
- Hotfix draft PR #23，源提交 `e8daa4ab68e18dce4643cbf75dbc19835040990f`：修复 CHUNK_GENERATION spawn 被拒绝/自然 spawn 权重 1 -> 12/无活动路径时错误冷却。
- NeoForge Java 测试与编译 SUCCESS [39537393137](https://github.com/g2066203208-beep/mc-studio/actions/runs/39537393137)；真实 GameTest 服务器 10/10 通过 [39537399622](https://github.com/g2066203208-beep/mc-studio/actions/runs/39537399622)，包括服务器实体实际移动、真实命令产生六实体、生物群系 spawn 表。
- **客户端启动尝试** [39537393170](https://github.com/g2066203208-beep/mc-studio/actions/runs/39537393170)：Xvfb Render thread 加载 mod/reme_studio 资源无筛查 fatal，180 秒主动退出。不等于 HMCL 和整合包可用，更不代表真正进世界验证所有行动。
- v0.5.1 JAR 124714 字节，sha256 aa61ce2e079b08e395cddbc4e54fc7213a7a51c37483e03dbc8b3f518dd1ac88，GitHub artifact 12317652189；保持 draft。


## Living World v0.5.3 实际开源源码对照和修复
- 2026-10-21 查看 Millénaire、Kithkyn、MCA Reborn、MineColonies 的 Java 实现与 source-level 思路，详见 `docs/OPEN_SOURCE_NPC_CODE_AUDIT.md`。
- 检测到 v0.5.1 和 v0.5.2 出生初始化漏洞：spawn placement 接受 CHUNK_GENERATION，但实体 finalizeSpawn 对该原因不初始化个人信息，已统一初始化；局部已加载人口上限两个自然原因均进行检查。
- 源码编译 [39542185445](https://github.com/g2066203208-beep/mc-studio/actions/runs/39542185445) SUCCESS、12 项 NeoForge GameTests [39542185523](https://github.com/g2066203208-beep/mc-studio/actions/runs/39542185523) SUCCESS。
- SHA256 `ee837d2b02f9d7baf65d8e13b646323ad04d97904e3a39fbfc17916a6b012ab9`、127188 字节、GitHub artifact 12319922214。
- **用户真实 HMCL 模组加载故障未被确认修复。** `/npc` 根命令未知说明当前游戏会话没有此命令；建议基于实际 HMCL Mod 管理列表/日志判断，而不是继续大量增加玩法代码。


## 新里程碑：v0.5.4 首次真实单人世界图形验收

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
