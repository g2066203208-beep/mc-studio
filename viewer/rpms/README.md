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
