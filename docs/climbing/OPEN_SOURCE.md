# 攀爬 Mod 开源研究索引与依赖边界

更新：2026-10-20。此文件是源码研究清单，不是依赖已安装或兼容性已验证声明。目标 Minecraft 1.21.1 / NeoForge 21.1.248 / JDK 21。

## A. 核心依赖（优先）

### 1. Sable — 动态物理结构

- 官方仓库：https://github.com/ryanhcode/sable
- 查阅分支：main；查阅时 SHA：227077f7f528c9beab1336a9fad2e27b754e4e70
- 配套 MC 版本：1.21.1；源码 gradle.properties 版本：2.0.6（发布页可核查 NeoForge 2.0.6）；源码中 NeoForge 开发版本 21.1.228 不代表玩家必须降到此版本。
- 许可证：PolyForm Shield License 1.0.0，**非 MIT / Apache 等宽松许可证**。调用公开 API 与直接复制/分发源码是不同的许可行为；不直接拷贝上游源文件、资源或打包 Sable JAR。
- 源码重点：wiki/Home.md、wiki/Working with Entities.md、common/src/main/java/dev/ryanhcode/sable/sublevel/SubLevel.java、api/entity/EntitySubLevelUtil.java、ActiveSableCompanion.java。
- SubLevel 包含隔离方块网格与 pose；逻辑 pose、last pose 用于物理计算。Wiki 说明跟踪动态结构的实体相对移动及同步；不能将 tracking 误当成任意位置的手部固定约束。
- 官方仓库明确提醒 Sable 大量 Mixin 与其他 Mod 可能存在兼容问题。

### 2. Sable Companion — 坐标与结构兼容 API

- 官方仓库：https://github.com/ryanhcode/sable-companion
- 查阅分支：main；查阅时 SHA：652ecf0e051846f3a433dd20126dd4d64ec3793a；gradle.properties 标记 1.6.0。
- 许可证：MIT，Copyright 2026 RyanHCode，引用源码必须保留声明。
- README：https://github.com/ryanhcode/sable-companion/blob/main/README.md
- 官方 NeoForge 推荐 Jar-in-Jar 配置，但对于计划强制依赖 Sable 的 Mod，先研究何时选择 compileOnly / runtime依赖 / jarJar，避免重复打包造成版本冲突。
- API 研究：SableCompanion.INSTANCE.getAllIntersecting、getContaining、projectOutOfSubLevel、distanceSquaredWithSubLevels；SubLevelAccess.logicalPose / lastPose / renderPose；位置/法线正反变换和结构 UUID。
- 重要：Sub-level plot 内坐标可能极大，不能简单与玩家世界坐标直接计算距离。必须按官方推荐转换到同一空间。
- 首先验证 getAllIntersecting 在玩家视线射线扫过动态结构时是否覆盖全部候选，验证其和 VoxelShape 窄相位的时序；不能假设此 API 已提供直接抓握能力。

## B. 同版本重点参考（只研究所需代码，不依赖它们运行）

### 3. ParCool–Sable 兼容附属

- 仓库：https://github.com/semillakan6/ParCool-CompatibilityAddon-NeoForge
- 固定分支：1.21.1-NeoForge；查阅时 SHA：4eb6d8b153c7c4ef7b757ec9f4c557b5d6f0df4d
- 许可证：LGPL-3.0。不要复制其中的整段算法/资源到闭源工程；若以后确实重用代码，先审查许可证义务。
- 版本记录：NeoForge 21.1.248、ParCool 3.4.3.3、Sable 2.0.5、Companion 1.6.0。作为参考样本，不证明 Sable 2.0.6 已在本项目兼容。
- 必读源码：
  - src/main/java/com/alrexu/parcool/compat/extern/sable/SableCollision.java
  - src/main/java/com/alrexu/parcool/compat/mixin/sable/WorldUtilSableCollisionMixin.java
- 实现思路：通过 getAllIntersecting 查候选结构，变换世界 AABB 到局部进行广相碰撞查询，再把局部精确方块 AABB 变回世界执行相交检测；对 tick 间变化抽样扫掠。
- **重要经验教训**：不要给 Level / CollisionGetter 的全局 noCollision 乱注入；这个附属仅修改 ParCool 自己的世界探针，文档指出全局篡改会导致姿态抖动、错误游泳姿势。
- 参考的现成代码是“悬崖抓握与碰撞兼容”，不等于双手独立物理锚点。

### 4. ParCool — 玩家跑酷动作

- 仓库：https://github.com/alRex-U/ParCool
- 固定分支：1.21.1-NF；查阅时 SHA：4d8d2643f11e3c7aab28f525b88643e3aca618cf
- 许可证：LGPL-3.0。
- 参考：悬崖抓握、攀上台面、跳离墙面、玩家状态/输入与第三方交互；不将 ParCool 安装列为本 Mod 必需项。
- 已知相关问题：https://github.com/alRex-U/ParCool/issues/475 —— 动态结构可能被另一个攀爬 Mod 看作实体而非方块；因此需要自有的 Sable-aware 表面探测。

### 5. ClimbingClaws — 天花板、墙面移动

- 仓库：https://github.com/IntelligenceModding/ClimbingClaws
- 固定分支：1.21.1-neoforge；查阅时 SHA：2266b9ab5ab269271d224d8ca72ea6f09e367dae
- 许可证：MIT，Copyright 2026 DoomedArtemis；引用代码需保留原版权及许可。
- 说明：包括墙面/天花板攀行、静止悬挂和下降，然而需要专用爪装备和右键操作，**不直接满足“裸手左右独立抓取任何方块”**。
- 值得研究：方块顶/底面方向判定、碰撞约束、服务器 tick 处理、原版姿态兼容。

### 6. NeoForge MDK 与官方文档

- 模板：https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle
- 既有工作室记录的原始模板 SHA：00b49d3f81a257858ca425792d59b9e5ca0f7e56；该模板已有本项目独立工程，不重复创建 Gradle 项目。
- 资料：https://docs.neoforged.net/docs/1.21.1/
- 重点：Key Mapping、InputEvent.InteractionKeyMappingTriggered、网络 Payload、实体 Tick/Movement、客户端 Hand 渲染；需在 1.21.1 实际 API 上验证事件取消是否影响左/右键长按、破坏与放置行为。
- 参考：https://docs.neoforged.net/docs/1.21.1/items/interactionpipeline/

## C. 玩法参考（不是可复制 Minecraft Mod 源码）

### 7. White Knuckle

- 官网：https://store.steampowered.com/app/3195790/
- 核心参考：独立左右手抓握、两手交替、按住牵引、松手继承动量、体力/负重对身体控制的影响。
- 开发者关于原型的回顾：https://changelog.gg/games/white-knuckle-3195790/updates/2026-04-15-white-knuckle-first-year-retrospective-06623cfdedea3180
- 开源 VR 社区控制说明：https://github.com/kyanite-rock/White_Knuckle_VR （仅作为操作语义参考，不引入其代码/资产）。
- 保留 Minecraft 原版手臂、皮肤与资源，不复制 White Knuckle 的模型、音效或纹理。

## D. 依赖实施规则

1. 真实游戏实例中 Sable 版本仍待确认。官方 1.21.1 2.0.6 是研究候选，不直接声称用户已安装。
2. 优先直接接入 Companion 的公开接口；动态碰撞探测使用自有、隔离的 ClimbSurfaceProbe。不直接将 ParCool 或 ClimbingClaws 强制作为运行依赖。
3. 开发分支固定各个依赖版本后，在 Gradle 进行 compileJava 和最小测试，记录具体版本、hash、Gradle 的解析结果。源码查阅 SHA 与发布 JAR 哈希分别记录。
4. 不把第三方完整源码、Gradle 下载缓存、游戏客户端、Mod JAR 和整合包提交到仓库。
5. Sable 为 PolyForm Shield，ParCool 附属及 ParCool 为 LGPL-3.0，ClimbingClaws 和 Sable Companion 为 MIT；各自的许可证单独评估，不等价。
6. 任何依赖兼容问题优先在可复现的小型用例中定位，不为了绕过异常贸然修改全局碰撞或更新整合包。
