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
