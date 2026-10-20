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


## Sable Hands alpha 开发快照（2026-10-20）

- 功能分支：feat/sable-hands-prototype；PR #3：https://github.com/g2066203208-beep/mc-studio/pull/3 （Draft；尚未合并）。
- 基于先前研究 PR #2 的 SPEC / OPEN_SOURCE / DEV_PROMPT / TEST_PLAN；另有 docs/climbing/PROTOTYPE_STATUS.md。
- 新增 Java 双手抓握原型、普通世界及 Sable Companion 子世界轮廓命中、锚点约束、客户端按键/服务端校验/权威锚点快照；不增加 3D 骨骼或手臂资源。
- 已核验首个功能原型 head 761a7b41fa188e48ad1f9973bce71a4f93695ba0 的 Actions 39406601421：Gradle Java 21 编译与 JAR 成功；当时 test NO-SOURCE。
- 随后新增 JUnit 5 测试与 S2C 状态回执，并升级 alpha 版本号；最新候选请以 PR #3 当前 SHA 的最新 Actions 运行结果及 XML 报告为准。
- 客户端、Sable 旋转/船体、多人专服、整合包兼容、性能均未核验。研发中的速度约束不等于精确 Rapier 玩家物理。不要自动安装到正式存档。

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


## PEAK 攀爬重构状态（2026-10-20）

- 研发主分支候选：`feat/peak-climb-mvp`，Draft PR #5 https://github.com/g2066203208-beep/mc-studio/pull/5 ，base 为旧 V2 功能分支。主线 main 不变，旧 PR #3/#4 不再作为正式攀爬方案。
- 玩法按真实 PEAK：按住 LMB 抓墙、WASD、松开掉落、Shift 冲跃、体力消耗与回复。代码涉及 ClimbRuntime/ClimbMotion/PeakGripRules/ClientClimbControls/PeakVisuals/VanillaGripHands/PeakStaminaHud。
- 原版方块手臂保持原纹理及模型：新增 PeakPlayerModelMixin（第三人称手臂、袖子、腿）以及 PeakFirstPersonHandsMixin（试图绘制空副手）、通过 S2C Pose 告知其他玩家。
- 已确认源代码构建/单测通过；运行 [Actions 39419885573](https://github.com/g2066203208-beep/mc-studio/actions/runs/39419885573) 报告常规 GameTest 3/3 与 Sable 2.0.6 + Rapier 集成 GameTest 3/3 通过。
- 又增加图形客户端 Xvfb + Mesa 软件渲染 startup smoke job，**请以最终分支 HEAD 对应运行的各项状态为准**，不能沿用早期通过报告。
- 缺少：实际第一人称双臂显示截图、第三人称 F5 动作画面、连续爬高墙与爬上平台手感、真实 Sable 动态结构移动/旋转、双人服务器、完整 Create/Aeronautics/TFC 整合包。
- 不进入正式发布、不修改用户游戏存档、不合并 main，直到上面关键实机验收完成。
