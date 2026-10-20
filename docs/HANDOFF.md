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


## 新增研究任务：Sable Hands（双手物理攀爬，规划阶段）

2026-10-20，用户确认：Sable 指 RyanHCode 的 Minecraft 物理/动态子世界 Mod，不是同名独立游戏；玩法参考 White Knuckle 的双手交替抓握，保留 Minecraft 原版方块手臂，优先做功能。墙壁、平地、天花板、可旋转/移动的 Sable 方块都应能够按有效方块表面抓握。

规划文件位于独立候选分支 docs/sable-hand-climbing-plan：
- docs/climbing/SPEC.md：需求、抓握与身体约束、静态与 Sable 坐标架构；
- docs/climbing/OPEN_SOURCE.md：上游源码/版本/commit/许可证清单；
- docs/climbing/TEST_PLAN.md：六向方块、旋转结构、单/双手、联机测试；
- docs/climbing/DEV_PROMPT.md：可供下一个会话直接执行的完整开发指令。

当前只有研究与文档，**没有攀爬源代码，也没有新攀爬功能构建或实测**。不要把早期工作台骨架的云端 BUILD SUCCESSFUL 当成本功能通过。计划下一任务为 M0：原版方块及 Sable 动态碰撞表面的真实抓点识别，先通过可视化测试和自动化校验；然后按 M1 静态双手攀爬、M2 Sable 动态锚点、M3 联机、M4 整合包兼容逐阶段开发。

新会话接手先检索该候选 PR 和最新 main HEAD；如文档已经合并按 main 读取，不要以旧分支覆盖新版。


## Sable Hands alpha 功能分支交接（2026-10-20）

前面的“只有规划、没有源代码”描述对应旧研究提交；当前功能分支已有 Java 实现。

- 分支 feat/sable-hands-prototype；Draft PR #3：https://github.com/g2066203208-beep/mc-studio/pull/3。
- 代码在 mod/src/main/java/dev/reme/mcstudio/climb/，包含 GripAnchor、SurfaceProbe、ClimbState、GripRules、ClimbRuntime、ClimbMotion、ClimbNetwork、ClimbSnapshot、ClientClimbControls、ClimbServerEvents。JUnit 位于 mod/src/test/java/dev/reme/mcstudio/climb/GripRulesTest.java。
- 用户最终需求：基于 Minecraft 的 Sable 物理 Mod，保留原版方块手臂，参考 White Knuckle，左右手独立抓任意方向的真实方块表面：垂直墙、平地、天花板、Sable 移动船体；功能优先。
- 预设控制、已知风险、实测门槛：docs/climbing/PROTOTYPE_STATUS.md。依赖研究：docs/climbing/OPEN_SOURCE.md。
- 早期 Java 编译成功（Actions 39406601421）仅验证 head 761a7b41...；已经加入 JUnit 测试与 XML 证据保存。请重新确认 PR #3 当前 head 的最新 Actions 状态。
- 尚无真实客户端、专服、Sable 飞船或整合包游戏内验证；不得合入 main 宣称正式完成。下一步优先执行实机测试，修复视觉反馈、动态 tracking 重复继承速度及客户端/服务端预测偏差。
- PR #3 以 PR #2 未合并研究文档分支为起点，合并 PR #3 后不得反向重复合并 PR #2。

## 自动化验收结果（2026-10-20，功能源码 HEAD 58def67a099c7e413a7cae4df1f36f33d031fe48）

GitHub Actions: https://github.com/g2066203208-beep/mc-studio/actions/runs/39409432696

- Java 21 + NeoForge 21.1.248：compileJava、compileTestJava、JUnit 5 test、JAR 打包及 artifact 上传均成功。
- 真正启动无界面 Minecraft GameTestServer：2/2 个测试通过。第一项验证四面墙、地板和天花板的表面抓点/移动方向；第二项验证左/右手独立抓取和释放、被抓方块被移除自动失效。
- 以 -PwithSable 下载并加载官方 Sable NeoForge 2.0.6；Sable 的 Rapier Physics Scene 初始化并完成同样 2/2 GameTests，证明最小运行环境下共同启动成功。
- 归档的证据：
  - JAR: https://github.com/g2066203208-beep/mc-studio/actions/runs/39409432696/artifacts/12259500091
  - JUnit XML: https://github.com/g2066203208-beep/mc-studio/actions/runs/39409432696/artifacts/12259310285
  - 普通 GameTest 日志: https://github.com/g2066203208-beep/mc-studio/actions/runs/39409432696/artifacts/12259200820
  - Sable 物理 Mod 运行日志: https://github.com/g2066203208-beep/mc-studio/actions/runs/39409432696/artifacts/12258772673
- Sable 单独运行日志中出现 create:flywheel 方块未知的错误级别日志（该最小测试环境未加载 Create），但物理场景和测试均正常完成；后续整合包环境单独核查。
- **尚未完成真实客户端图形游玩、手臂在第一人称的外观检查、动态 Sable 子世界中的旋转/平移抓取、双人 dedicated server、正式整合包兼容。** CI 绿灯不是这些验收的替代证明。

## 下一会话具体开发任务

1. 从 PR #3 当前 head 及 docs/climbing 目录开始。不要重复初始化工作台；分支上的编译和 Sable 服务器启动已验证。
2. 在独立开发客户端（包括真实 Sable Mod）打开世界，用 G + LMB/RMB 检查墙壁、天花板、平地的手感、原版两只手显示和碰撞，记录错误并修复。
3. 在 Sable 真实 Sub-level 中以 0、匀速、旋转、急停速度测试锚点局部坐标及其随动，检查重复获得船体速度问题。
4. 再跑专服双人/高延迟/整合包 Create Aeronautics TerraFirmaCraft，全部满足才可把 Draft PR 变 Ready 并考虑合入 main。玩家存档仍未动。


## 2026-10-20 用户试玩后的正式方向修订：Sable Hands V2

用户反馈：已装载 Sable 2.0.6 和 REME 0.2.0-alpha.1，但 G 模式繁琐，第一人称只是手臂偏移、没有向前伸；第三人称完全没有攀爬动作。希望点击一次右键锁定右手抓墙，再点击左键锁定左手；同时能够右手抓墙悬挂、左手取出手枪并单手瞄准射击。相较纯 PEAK 或 White Knuckle，确定采用独立双手抓握核心 + 流畅辅助移动 + 点击锁定/按手物品优先。

完整取代旧输入和渲染的**新目标文档**：[docs/climbing/HAND_INTERACTION_V2.md](climbing/HAND_INTERACTION_V2.md)。已追加入口到 SPEC.md 与 DEV_PROMPT.md。上一轮 0.2.0-alpha.1 的 Java/JUnit 和 Sable GameTests 通过，但**没有任何 V2 代码或枪械/第一第三人称效果的验收**，千万不可将这些指标混为一谈。下一开发阶段先修正常控制冲突、左右手状态、第一人称实际伸手和第三人称同步；再对用户指定枪械进行独立适配，旧 PR #3 保持 Draft。


## PEAK Climbing 最新接手指令（2026-10-20，优先于旧双手/弓方案）

用户明确表示 0.2/0.3 alpha 的左右手点按、没有前伸手臂和第三人称动画“反人类”，决定**先完全参考 PEAK 的攀爬操作**，不再实施单手手枪/弓箭作为第一开发优先级。

- 现行开发分支：`feat/peak-climb-mvp`，Draft PR #5 https://github.com/g2066203208-beep/mc-studio/pull/5 。
- 真实操作：主手空手时，墙前**按住左键抓住、WASD 移动、Shift 单次冲跃、松开左键立即放手、右键保留原版使用**。全方向方块探针仍由 SurfaceProbe/Sable Companion 支撑。不要重新恢复 G 切换模式/左右键各控制一只手/弓箭左键开火协议。
- 新实现：ClientClimbControls、ClimbRuntime、ClimbMotion、PeakGripRules（体力）、PeakStaminaHud（HUD）、PeakVisuals + ClimbNetwork.Pose（同步其他人动画）、PeakPlayerModelMixin（F5 玩家模型手臂、腿和袖子）、PeakFirstPersonHandsMixin（空副手原版手臂补绘）、VanillaGripHands（第一人称前伸/交替）。
- 源码参考与许可证及具体手感要求：docs/climbing/PEAK_RESEARCH_AND_ACCEPTANCE.md。
- 版本 0.4.0-peak.alpha.1 的初版测试已过 Java/JUnit、普通 Minecraft GameTest 3/3、Sable 2.0.6 + Rapier GameTest 3/3，记录在 Actions https://github.com/g2066203208-beep/mc-studio/actions/runs/39419885573 。
- 图形客户端 Xvfb + Mesa 的 runClient smoke 测试由后续提交新增；**必须重新检查 PR #5 最新 HEAD 的 CI 结果**。即使启动成功，也无法取代人工第一人称与 F5 可视化测试。
- 下一步：修复所有 client mixin 崩溃，验收原版手臂是否看得到且贴近墙、脚随运动、放手动作；增加连续跨多个方块且可攀上墙顶的实际测试；创建旋转 Sable Sub-level 测试并优化 Sable tracking；最后进行双人 dedicated server。
- 主线 main、用户 mods 与正式存档均未被改动；不要把已有普通 GameTests 的成功当作真实动船攀爬或完全复刻 PEAK 的证据。
