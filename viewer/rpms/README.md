# RPMS · Minecraft 方块机械工作台（源码登记 + 严格预检）

主站：\`/rpms/\`。目标是辅助 Minecraft 1.21.1 / Create / Simulated / Sable 实际机械工程，而不是在 Three.js 中虚构一台已能运行的机器人。

## 为什么推翻旧版

旧版 Q12 包含 88 个预设方块和 12 个声称为轴承的方块身份，但排列不等于经过 Create 和航空学物理装配规则验证，不能叫可运行 Boss。

- **新的首页**：空白 MC 整数网格。从左侧目录选取方块，鼠标点击地面或已放置方块的面逐块放置、检查、旋转部分轴件和拆除。无默认 Boss 结构。
- **旧设计**：\`/rpms/assembly.html\`（原拟议方块布局及本地 JAR 模型预览，仍是未验证方案）；\`/rpms/concept.html\`（更早的程序化概念四足机）。
- **目录**：\`registry.json\` 来自官方源码中的 **185 个 Create + 31 个 Simulated 显式 \`REGISTRATE.block(...)\` 字面量**，共 216。**不保证覆盖全部实际注册方块**：染色、数组注册、动态辅助方块及其他模组需进一步提取。用户可以直接在浏览器导入自己安装的 Create/Simulated JAR，追加 \`assets/{create,simulated}/blockstates/\` 目录包含的条目、读取 variants 信息。不上传 JAR。
- **模型边界**：新工作台使用清楚可辨的原创占位形体而不是原版资产；若要查看本地 JAR 模型，请进入 \`assembly.html\` 的导入器。航空学官方模型和材质不在本站托管。

## 已实现的局部规则

\`strict-core.mjs\` 是独立、可单测的**保守规则预检器**，参照 Create 源码 \`RotationPropagator.java\`：
- 整数 MC 方块坐标、禁止重叠；高于地面需相邻方块作为放置参考（只表示常见编辑操作，不覆盖所有 MC 特殊方块支持条件）。
- 平行相邻轴件只能顺轴向传递；相邻同轴平行的小齿轮侧向啮合反向（1:-1）。
- 大齿轮 / 小齿轮符合对角规则时 -2、-0.5 倍转速；相互垂直轴向的大齿轮按源码对角条件连接。
- 创造马达设定用户输入的示意 RPM，沿已支持的规则传播，检查循环冲突和预览转速阈值。
- Simulated 旋转轴承仅记录其朝向的轴线候选；不给它伪造胶水、装配、多刚体或 Sable 运动。

特别说明：**应力容量/消耗不是固定通用常数**。Create 服务器设置和其他 Mod 能改变数值；网页没有接入游戏内配置，因此没有猜测 SU。离合器、变速箱、传动链、流体、配方、蒸汽引擎、红石、实际扳手、物理装配与多刚体负载均为 **原版游戏才有完整功能**，网站不会标识为已验证。这不是全功能 Create/航空学浏览器移植。

## 上游来源（固定于审查时提交）

- Create 1.21.1：\`https://github.com/Creators-of-Create/Create/tree/7ff2760e4924d35d4bc2e22c4b19923f0178cd16\`，\`AllBlocks.java\` 与 \`RotationPropagator.java\`。
- Simulated：\`https://github.com/Creators-of-Aeronautics/Simulated-Project/tree/3705df2062bebba42c3ab3d6b3dd09faa1512850\`，\`SimBlocks.java\`、\`SwivelBearingBlock.java\`、\`SwivelBearingBlockEntity.java\`。
- 源码分支快照并不证明与用户本机 JAR 版本完全一致。以当前实际运行游戏的注册表、配置与行为为最终依据。

## 测试和后续阶段

- \`node viewer/rpms/tests/strict-core.test.mjs\`：离线几何/连接/动力网络逻辑。
- \`viewer/rpms/tests/strict-browser.cjs\`：Playwright Chrome 真实 WebGL、216 项目录、搜索、筛选、合法示例、小型测试 JAR 导入、桌面及手机截图。
- 旧装配回归 \`tests/blocks-smoke.cjs\` 仍作用于 \`assembly.html\`；旧运动学网页回归 \`tests/smoke.cjs\` 仍作用于 \`concept.html\`。
- CI \`.github/workflows/rpms-authentic-blocks-check.yml\` 在分支和 PR 自动执行。

**下一阶段必须转向真正 NeoForge + Create/Simulated 的 MC 测试实例**：
1. 从目标版本游戏进程导出所有注册方块及 BlockState、可用交互、Ponder/配方/BlockEntity 与配置。
2. 在游戏内用逐块已验证、可构建、可启动、可供能的机械小模块做 GameTest；动作来自真实 Mod，而非 Three.js 曲线。
3. 单独验证胶水与轴承组装、动力输入/SU、Sable 约束、刚体质量、负载和运动闭环；最后组装 Boss 与 AI。
4. 只有验收成功的功能，才在网页工具栏标记为“游戏已验证”。

**验收边界**：网页能看到一个方块、能导入 JSON、能计算转速比，并不意味着能在游戏中组装、旋转、行走或战斗。


## 2026-10-21：将官方 JAR 真实静态模型接入主工作台

用户明确要求网页放置的方块与 Minecraft 中原版一致，因此不再把自制方盒/圆柱体伪装为实体外观。本版新增 \`authentic-models.mjs\`：
- 通过浏览器 File API + JSZip 从用户主动选择的 Create、Simulated、Minecraft 1.21.1 客户端 JAR 或资源包中读取 \`assets/<namespace>/blockstates/*.json\`、\`models/**/*.json\` 与 \`textures/**/*.png\`。
- 按 blockstate 变量键选择原始模型，提供变体下拉框，也支持 Multipart 的简单 AND/OR 匹配、JSON 模型继承、Element 面、像素 UV、部分 face rotation 和 model x/y 旋转。
- 使用导入的 PNG 创建 nearest-neighbour WebGL 贴图。对应模型能解析时立即取代占位线框，保留独立、不可见的整数方块点击区域。
- 原始游戏素材只在用户自己的浏览器内处理，不保存至 GitHub、不上传站点，不重分发。Create \`LICENSE.md\` 与 Simulated \`LICENSE.md\` 对 assets 目录均声明 All Rights Reserved，必须尊重。
- 没有资源或不支持时明确显示线框，旁边显示“真实模型 xx / 已放方块 xx”“partial / unsupported / missing”，**不会伪称与游戏一模一样**。
- 静态元素无法完全渲染 Create/航空学自定义几何 Loader、BlockEntityRenderer、程序化机械动态零件、TESR、玩家所装资源包着色、Sable 刚体动力学。复杂混合变体、uvlock 和某些非立方体实现也未必完全等同 MC。
- 进一步做到逐像素一致，应由 **真实 Minecraft 1.21.1 + 用户精确版本 Mod + 一致资源包 + 游戏内 BER** 渲染到可预览的离线截图/实时画面，不能把 Three.js 小渲染器称作客户端。
- CI 使用项目自创的 Simulated bearing / Create shaft & motor 多部分合成 JAR 进行解析器冒烟测试，绝不包含上游原版贴图。只有通过 CI 才能合入 main。


## 官方正式版 JAR 的真实模型端到端验收（2026-10-21）

首次真实测试揭示此前未检查的关键事实：Create Aeronautics `1.3.2+mc1.21.1` 外部 JAR 并不直接包含 Simulated/Aeronautics/Offroad 的 `blockstates`；内容藏在三个 `META-INF/jarjar/*.jar` 子包中。旧解析器只解包最外层，航空学始终显示假方框。因此增加了最多两层嵌套解析、数量/单个 JAR 体积限制和资源覆盖缓存失效处理，改为从实际发布版本的资源里渲染。

真实自动化使用：
- 从 Modrinth 官方发布版 API 临时下载 `Create 6.0.10+mc1.21.1` 与 `Create Aeronautics 1.3.2+mc1.21.1`（SHA-512 校验）。
- 从 Mojang 官方客户端下载 `Minecraft 1.21.1`（SHA-1 校验），补齐原版纹理和父模型。
- GitHub Actions Ubuntu + Chrome/WebGL 导入三套 JAR，覆盖 23 个 Create/Simulated/Aeronautics 代表性方块；保存桌面和手机真实截图、各模型的状态 JSON。Mod JAR 与贴图**只存 CI 临时目录，不提交也不附加到公开 artifact**。
- 最新可复现实测入口：`.github/workflows/rpms-real-release-e2e.yml`，`tests/download-official.py`，`tests/official-release-browser.cjs`。原有小型合成 fixture 也包含模拟 jarjar 子包防回归。
- 一轮针对 23 个代表方块的官方静态模型测试结果：**17 个静态 JSON+PNG 模型可解析、6 个仅部分/近似状态可用、0 个完全缺失**（具体数字以该分支最新 Actions 运行生成的 `official-model-coverage.json` 为准）。
- 有些原版 BlockState 需要页面 UI 尚未输入的额外属性（如 `face`, `axis_along_first`, `powered=0`）。这些方块选用**原本就存在于正式 JAR 的最接近变体**，并明确显示 `partial`，可在 UI 手工选择确切原版状态；不能冒充与游戏默认状态完全一致。
- CI 安装临时的系统 CJK 字体用于测试截图，修复原先 CI 里中文都变成 □ 的问题。

**仍未完成的项目**：需要在 NeoForge 游戏进程内对 Create 和 Sable 动态 BlockEntityRenderer、运动/粒子、Ponder、光照、姿态、贴图资源包与原版截图逐一对比；这无法用浏览器解析 JAR 静态资源证明完成。浏览器的 `authentic` 仅指**静态模型资源解码成功**，不等于游戏内整台 Boss 的真实渲染和物理已验证。

## 2026-10-21 原版服务器实测接入（真正的 NeoForge，不是 Three.js）

本轮新增真正游戏内的 Java GameTest、NeoForge Server 命令和 CI，而非在网站里重新画方块物理。

- **环境：** Minecraft 1.21.1、NeoForge 21.1.248、Create 6.0.10、Aeronautics 1.3.2（含 Simulated/Offroad）和**强制依赖 Sable 2.0.6**。
- **自动化：** .github/workflows/rpms-native-gametest.yml 下载官方发布 JAR 并校验 SHA，把 Create、Aeronautics、Sable 放入临时 mod/runtime-mods 文件夹，在真正的 gameTestServer 运行 Java GameTests。原始 JAR 不提交到仓库，也不作为 CI 工件提供给用户。
- **实测基础：** Create 创造马达朝东 + 传动轴沿 X 放置时，真实 getSpeed() 返回马达 16 RPM、相邻轴 16 RPM；此前错误朝向时轴为 0。此测试已通过。无线和动力中断的后续测试必须等待各自的 CI 真实通过才能声称成功。
- **原版状态快照：** 管理员在游戏里输入 /rpms snapshot 6（半径可填 1–8），从真实游戏里的已加载区块读取附近 Create/Simulated/Aeronautics/Offroad 的 BlockState、BlockEntity 类型和可用的原版转速、无线信号读数等；将 JSON 写入世界存档的 rpms-snapshots 文件夹。不会更改 Minecraft 方块/装配/动力，也不会对外发布服务器。
- **与网页对接：** 在 RPMS 网页先导入当前游戏版本的 Create/Aeronautics/Minecraft JAR，然后通过“导入 JSON”载入该游戏快照。游戏中实际测得的 RPM 在方块信息栏与纯网页预览 RPM 区分显示。快照导入绝不意味着网页已经执行原版物理运算。
- **边界：** 测试套件专门检验明确的原版数据或作用，不据此宣称全部应力、无线覆盖、Sable 刚体、风、升力或碰撞已完成。应以 /rpms/native.html 的系统级验收状态为准。

