# WorldSeam · 界缝

**独立的 Minecraft 跨维度世界底层 Mod**（仍处实验阶段）。

- Minecraft：1.21.1
- 加载器：NeoForge 21.1.248；Java 21
- **Mod ID：`worldseam`**
- Java 包：`dev.reme.worldseam`
- 独立 Gradle 工程：`mods/worldseam/`
- 构建产物：`mods/worldseam/build/libs/worldseam-0.9.3-dev.jar`
- 依赖：Minecraft 与 NeoForge；**不依赖 PEAK 攀爬，也不依赖 Immersive Portals**。

## 为什么和 PEAK 完全独立

WorldSeam 管维度边界与世界区块/实体流。PEAK 管抓握、攀爬和运动。两个 Mod 不复用入口类、包命名、资源命名空间、Mod ID、项目构建或 JAR。这个目录的源码、资源和 CI 只用于 WorldSeam。攀爬工程仍在 `feat/peak-*` 功能分支，本次没有修改。

## 自己构建

```sh
cd mods/worldseam
./gradlew test build
./gradlew runGameTestServer
```

CI：`.github/workflows/worldseam.yml` 单独运行这些任务，同时检查 JAR 不含 `dev/reme/mcstudio` 或任何 `climb`/`peak` 包。工作室通用 `mod/` 的构建不会打包 WorldSeam。

## 测试世界专用指令

```mcfunction
/worldseam status
/worldseam enable
/worldseam disable
/worldseam probe
/worldseam terrain enable
/worldseam terrain disable
```

默认关闭。新地形接缝额外开关也默认关闭，若开启只处理未来新生成的区块。正式存档请勿直接测试。

## 当前能力边界

自主空间拓扑/服务端跨维实体迁移、原生 16³ 区块段调色板编码、远端区块段网络协议与客户端实验性区块代理渲染已迁移。**还没实现**完整多 ClientLevel、真正独立远端 GPU Chunk Mesh、流体/实体/光照全要素以及无黑屏镜头跨界。GameTest 服务端通过也不能代替客户端实测。这是自研引擎的独立开发版，不是完成的无缝世界。

[历史引擎源代码及研发说明](https://github.com/g2066203208-beep/mc-studio/pull/18)（旧 PR 不应合并）；最新独立 WorldSeam CI 才是新命名、新 Mod ID 的有效构建证据。

## v0.6.0-dev：实验性无 Respawn 跨维度（客户端尚未实机验收）

v0.5 的 teleportTo 仍引发原版 ClientboundRespawnPacket/加载界面。本版本增加第二 ClientLevel + LevelRenderer、真实完整 Chunk/生物群系/方块实体/sky&block 光照预传输、目标区块准备握手，和不调用 vanilla teleportTo 的实验性客户端/服务端协同换维。

新指令：/worldseam smooth enable（管理员专用，**默认关闭**），/worldseam smooth disable。需要先 /worldseam enable。实验模式只有远端 9 个原生区块全部缓存并收到准备确认后才直接切换；如果没准备好会在接缝边缘阻止穿越，而不是自动回退到带加载屏的原版传送。等待预加载请在接缝 64 格内停留一段时间。实际转换仍未在 GUI 客户端录屏验证，可能有帧级渲染、实体/光照等错误，仅限备份测试世界。

**这不是最终已经通过验收的无缝世界。** 目前没有真正验证客户端无黑屏和跨维其他实体、粒子、音效、Sable；实验功能请严格在可丢弃的全新创造世界使用。

研究参考（仅学习架构，未引入依赖）：https://github.com/UpperMoon0/ImmersivePortals 的 ClientWorldLoader、ServerTeleportationManager、ClientTeleportationManager、PacketRedirectionClient。

## v0.7.0-dev：维度高度边界的物理接缝修复（尚未图形化实机验收）

本次定位到并修复了 **Nether 建筑高度 256 与真实基岩屋顶 128 混淆** 的问题：远端近景、远景 LOD、实体传送都必须用逻辑 Y128 接缝，不能把远端地形误画到 Y256。主世界底部是 Y=-64，主世界顶部 Y=320，末地底部 Y=0。各自实际独立 ServerLevel，按跨世界连接平面拼接。

参考 Immersive Portals 的 `VerticalConnectingPortal`（巨型全局水平连接面）和 `MixinChunkStatusTasks_BedrockReplacement`（在 `ChunkStatusTasks.generateSpawn` 世界生成阶段处理基岩）。WorldSeam 添加**自己实现**的生成阶段 Mixin，针对新生成区块在管理员允许时只处理主世界底部/Nether 屋顶基岩，不引入门户 Mod 的运行依赖；旧区块不会被自动修改。

服务器玩家和实体改为 **在边界平面内侧 0.5 格、向接缝方向移动时提前执行换维判断**，保留世界外坐标越界检测兜底，而不要求 Minecraft 先让实体穿过高度边界。原生 9-chunk 预加载及 no-respawn 模式不变。

**全新可丢弃创造模式世界一键准备：** `/worldseam setup-test`（需要管理员权限等级 4）。这一指令启用跨维、默认开启不使用原版 Respawn 的实验模式、开启未来新区块基岩接缝，同时为命令执行位置所在的一个 X/Z 区块柱清理主世界地板与下界天花板的既有基岩。**永久修改最多两个维度各一个 16x16 区块柱中的边界基岩，严禁在正式世界执行，务必备份。** 只在测试地点对应 X/Z 区块尝试穿越。

**警告：** WorldSeam 当前的多 ClientLevel 和 direct handoff 已通过自动化编译/部分启动测试，但还没有实际 GUI 存档穿越录屏验收。渲染仍然是实验性稀疏代理，不能保证完整区块视觉无缝、双向实体同步和流体光照正确；这些是下一个阻断验收。

**本版重要限制**：虽然新增了真实物理接缝提前判断，远端世界仍未建立一个已通过 GUI 穿越验收的完整跨维渲染合成器。不要把测试世界的无 Respawn 实验模式当成完成版。

## v0.8.0-dev — 可编辑维度堆叠（测试中）

进入 **创建世界 → WorldSeam: Stack** 可以配置堆叠。以从上到下的顺序排列，每层可 **Prev/Next 选择、Move UP/DOWN 换位、Add/Remove 增减、设置 Bottom Y/Top Y、选择 UP/DOWN 单向/双向连接以及 Loop**。在 **Save stack** 后返回创建世界界面，**最后点击创建世界**才提交到该新存档。

“可用维度”来自创建世界当前已加载的世界生成 `LevelStem` 注册表（含安装的维度 Mod 与数据包实际提供的维度），而不是写死三个原版维度。服务器还会再次校验每一个所选 ID 是否存在以及上/下连接面是否在真实可建造范围内；不存在就拒绝启用，不能自动生成别的 Mod 的维度。独立运行时从存档读取该堆叠并把配置发给客户端。

**重要区别**：这里的 Top/Bottom Y 是“连接面坐标”，不是修改 Minecraft 的 `DimensionType.min_y`/`height` 本身。配置里的下界 Top Y=128 不会自动将下界原版实际建筑上限 Y=256 修改为128。底层完整远端渲染与世界生物/光照/流体同步仍处实验阶段。**目前尚未用真实第三方维度 Mod 进行 GUI 游戏内验证**，不能保证所有维度 Mod 的兼容性。存档修改高度和堆叠前请备份，尽量只在新建世界设置。

默认堆叠为 **主世界（顶部）→ 下界（中间）→ 末地（底部）**，默认无环。未来新生成区块才会可选地处理接缝的基岩，已有地形不会自动回填或剪裁。**暂未实现**跨维完整视频遮挡合成、原生真实全维度地形拼接、直接编辑 DimensionType 原生高度、Mod 维度生成器重建。


## v0.9.0-dev：双维度 Chunk 驻留与动态更新

详细说明和验证状态请阅读 [docs/DUAL_WORLD_LOADING.md](docs/DUAL_WORLD_LOADING.md)。这版重点修复下界区块反复加载、客户端丢失区块和动态 Chunk 更新调度饿死。**它并不是完成版视觉无缝。**


## v0.9.1-dev — 远端可见模型增量重建性能修复

此版本在 v0.9 双维度原生 Chunk 驻留基础上修复**数据包到达造成的 CPU 尖峰**：原版 `SeamNativeGeometryClient.onChunk` 每包同步扫描 1 个 16×16×160 方块柱及四邻列；如今先按 Chunk 坐标去重并进入 `SeamGeometryQueue`，每客户端 tick **最多重建 2 列**，优先玩家附近列，移动后弃掉已经离开 5×5 视窗的任务，避免边缘远端块吞掉主接缝渲染预算。六组纯 Java JUnit 测试检查合并队列、构建上限、优先级、负数 Chunk 坐标及窗口移动。

以前长期没有新的区块包时，原生几何缓存会在 2400 tick 后清空，即使目标 `ClientLevel` 仍然驻留。本次移除了这种流量驱动的过期逻辑；生命周期跟随真实次级世界、当前源/目标维度及退出/转维清理。

**尚未完成**：独立第二 `LevelRenderer` 在同帧中的帧缓冲合成、遮挡/深度/流体/粒子及远端实体模拟。几何代理的 3500 模型/帧预算仍可能在复杂环境下影响 FPS。这次修复只解决可复现的 CPU 重建尖峰和静止预览错误过期，不代表视觉完全无缝。CI 真实 GUI 截图与重建性能指标必须独立核对。


## v0.9.2-dev — 真客户端禁用清理和协议同步

修复 v0.9.1 长驻留远端渲染缓存在服务器关闭 WorldSeam 后仍可能滞留的问题。之前的堆叠网络同步只依据 `stackRevision`，管理员执行 `/worldseam disable` 不改变维度堆叠版本，客户端收不到禁用信息。现在协议快照加入 `enabled` 状态，使用 `SeamSyncStamp(revision, enabled)` 去重；关闭时立即清空次级 `ClientLevel`、`LevelRenderer`、原生几何重建任务、稀疏代理和远端区块段缓存。延迟抵达的旧区块包会因为客户端状态 OFF 被忽略；离线玩家的同步 ledger 被清理。网络协议由 3 升级为 4，必须客户端与服务端使用同一版本。

真实 NeoForge 单人 GUI 的 E2E 在双向穿越、末地预览后，会从服务端禁用 Mod 并明确要求 `WORLDSEAM_E2E_PASS_DISABLE_CLEARED`，否则截图工作流不得绿灯。纯 Java JUnit 测试验证启停切换即使堆叠修订号不变也会更换同步标记。

**还未完成**：真正双世界同帧透明/深度合成、远端实体/流体/天空、Sable 兼容及持续 FPS 帧时间测量。

## v0.9.3-dev — 世界接缝两侧的原生爆炸（实验功能）

**独立开关，默认关闭：**
```mcfunction
/worldseam enable
/worldseam explosions enable
/worldseam explosions disable
```

针对有真实开放连接面的两个维度，使用 NeoForge `ExplosionEvent.Detonate` 与目标维度的**原生服务端爆炸**，按真实水平接缝映射半径与中心：例如下界屋顶 Y128、主世界地板 Y-64。爆炸可能在源维度与目标维度同时破坏方块并伤害实体，反向映射同理；远端 3×3 区块须已经驻留，不会因爆炸生成遥远新区块。8 格以上的超大型 Mod 炸弹不自动跨界。封闭的基岩边界仍阻止爆炸传递，需在**可丢弃测试存档**打开连接面。

新增纯 Java 数学单测及真实 NeoForge GameTest 中的两个原生维度方块破坏验证。**尚未证明**真实 TNT 点火完整客户端 E2E、多玩家、权限保护 Mod 或 Sable 物理结构受力；本阶段不能称为航空学/Sable 物理化方块完美穿越。

完整研究和技术验收见 [docs/CROSS_SEAM_PHYSICS.md](docs/CROSS_SEAM_PHYSICS.md)。


## v0.9.4-dev — 末地改为最底层 + 双接缝区块驻留防抖

**新建世界默认顺序：主世界 → 下界 → 末地。** 主世界底部接下界配置顶面 `Y=128`，下界底部 `Y=0` 接末地原生顶面 `Y=256`；正反双向转换保持 X/Z 1:1 与速度。**不改变**原版 DimensionType 世界生成高度、不更改末地悬浮/虚空/海面及 Sable 载具已有浮力行为。末地自然地形大多在更低处；“预加载末地块”和“隔着下界地板直接看见远处末地自然岛”是不同目标，后者需要真正的第二 LevelRenderer/LOD。

**旧存档安全：** 已保存的 `SeamStackStorage` 配置原样读取，不擅自把原来的「末地 → 主世界 → 下界」迁移到新顺序。旧顺序的实际 GUI 客户端跨维回归继续显式固定为历史配置；新世界的默认顺序另由 GameTest、纯 Java JUnit 以及创建世界中文 GUI 日志断言验证。已有存档如需重排，先备份并在创建新的测试世界试验；不自动清除既有基岩。

**性能：** 原远端 Chunk 流媒体在下界 Y0 与 Y128 两个接缝附近容易反复更换唯一预载目标、重新派发 5×5 区块票据。新增纯 Java `SeamPreloadSelection`：靠近接缝 64 格内进入预加载、离开超过 80 格才释放；新目标需要比现有接缝近至少 12 格才切换。玩家远离两侧接缝不再在 session 表里持续创建空会话。远端原生区块、稀疏方块预览和区块段同步现在都从同一个服务端活动接缝读取目的维度，不再各自选择，从根源降低预览数据误切换和第二世界重置风险。新增 6 项迟滞/会话选择 JUnit 测试。

**性能证据：** 远端逐方块模型提交逻辑新增 120 帧滚动窗口 P95 **客户端 CPU 提交耗时**（`nativeModelCpuP95Ms`），由实际 NeoForge GUI E2E 写入证据日志。该指标不是 FPS、GPU 帧时间，也不能替代正式基准；它帮助后续用原生 Chunk mesh/跨维渲染替代 `renderSingleBlock` 热点。

关于 Iris、Sodium、Voxy、Immersive Portals、Sable 及真正双世界渲染方案，见 [docs/NATIVE_RENDERER_COMPAT.md](docs/NATIVE_RENDERER_COMPAT.md)。这次只优化现有双维度缓存与坐标/拓扑；**尚未完成**实际双原生 LevelRenderer 同帧合成、Voxy/Iris/光影通用适配及航空学/Sable 物理刚体跨界。功能证据与兼容矩阵必须按版本单独验证。

## v0.9.5-dev — 帧预算闭环 + TNT 区块范围修复（性能优先）

**实测回归输入**：前一轮真实 NeoForge GUI 失败文件 `mods/worldseam/docs/evidence/latest-neoforge/latest-failed.md` 显示在 3,500 个 `renderSingleBlock` 模型/帧时 `nativeModelCpuP95Ms=58.058`，并记录了 `WORLDSEAM_E2E_FAIL_CROSS_TNT`。这两个缺陷是本轮的明确回归目标，而非推测。

**临时远端模型渲染器性能保护**：新增 `SeamRenderBudget` 闭环控制，在 *Minecraft 客户端实际采到的 CPU 模型提交时间* 基础上调整单帧渲染模型上限，目标约 8ms CPU 提交；硬性范围 160–3500，昂贵帧立即降预算，便宜帧缓慢回升，不因场景自身模型不足而错误增加负载。保留真实深度、逐模型片段、正确远端坐标；这是针对当前 `renderSingleBlock` 旧代理管线的**帧率保护**，会临时减少渲染细节，不能当成真正的多维度原生网格方案。移除原来每渲染帧重复清理 25 个模型列/统计总面数的 CPU 逻辑（交给 20TPS tick），并跳过远端完全空气的 16³ 方块 Section；降低末地虚空/下界洞穴的块状态扫描量。F3 与 E2E 输出当前模型预算和 120 帧 CPU P95，后续要实际比较，不可预先宣称 FPS 提升百分比。

**真实 TNT 缺陷修复**：以前跨界爆炸无条件要求目标维度任意 3×3 = 9 个 Chunk 全驻留，导致位于块边缘的普通 TNT 也因不相关区块没加载而静默失效。现在按实际爆炸球心与原版实体受击最大 (2×radius) 计算需驻留的 X/Z Chunk 闭包（一般 1–4 个），**不主动加载或生成**目标维度 Chunk；不满足则明确打印 `WORLDSEAM_CROSS_BLAST_SKIP`。新增此规则的 JUnit，以及真实 NeoForge GameTest **主世界→下界和下界→主世界**原生方块破坏验证。原有真实 TNT `PrimedTnt` GUI 验收继续作为合并后的硬门槛，只有其 `WORLDSEAM_E2E_PASS_CROSS_TNT` 才能发布成功照片。该 patch 尚不保证跨维流体、实体、爆炸护甲/权限 Mod、Sable 飞船跨界碰撞。

**继续的性能工程**：Iris/Sodium/Voxy 专用路径需要真正原生 `LevelRenderer` 和 GPU 深度/透明状态隔离，不能仅通过自适应降低方块数达成完整兼容；详见 `docs/NATIVE_RENDERER_COMPAT.md`。末地保持最底层和现有航空学悬浮行为不变。


## v0.9.6-dev — 远端原生区块段可见性缓存（降低渲染主线程开销）

**实际发现的瓶颈：** v0.9.5 使用 `renderSingleBlock` 逐方块绘制，每帧为每个候选方块新建 `AABB` 并调用一次视锥体检查。即使使用约 565 模型的自适应预算，这些反复分配与裁剪仍会增加 GC/CPU 开销，无法发挥 Minecraft 以 16×16×16 区块段为基础进行渲染剔除的结构优势。

**已修改：** 将真实远端 `ClientLevel` 表面模型按 16³ 原生区块段整理为不可变 `Slice`，在收到区块数据后的有界重建阶段**预计算整个区块段的准确世界坐标 AABB**，绘制时先按段做视锥裁剪，整段被剔除就不遍历任何单格方块。每帧排序时考虑三维相机距离，段间交错绘制避免一个密集的末地石段独占预算；远离接缝超过 80 格暂停代理模型绘制，但仍继续预加载以保障接近后的正常穿越。精准支持负数高度、非 16 整数倍的 Mod 维度接缝位置。

**新增真实可测指标：** F3 和独立 NeoForge GUI E2E 日志输出 `visibleSlices` 与 `frustumCulledSlices`，连同 `nativeModelCpuP95Ms` 和 `modelBudget` 一起记录，逐步区分几何可见性优化与 v0.9.5 降模型数量的影响。新增纯 Java `SeamSectionLayoutTest`，验证末地、下界、负高度与自定义边界的精确切片映射。所有已建模型仍使用原版 `renderSingleBlock` 绘制，**不是** GPU 原生网格批处理，不声明 Iris/Voxy 已兼容或已改善所有资源包模型。

**性能保护原则：** 改动严格限定 WorldSeam 原分支 `mods/worldseam/`；不干涉机械动力、NPC、航空学或末地 Sable 物理浮力。后续仍需专门的第二 `LevelRenderer` 同帧 GPU 合成和 shader/LOD 适配。对比性能只以相同的 NeoForge GUI 场景实测值判断，不能预设提速百分比。


## v0.9.7-dev — 原生 16³ 区块段增量重建（减少客户端卡顿）

**本轮主动发现的瓶颈：** v0.9.6 虽然把每帧视锥裁剪改为区块段级，但 `SeamNativeGeometryClient.tick` 仍在**一个客户端游戏刻**扫描最多两个完整的 16×16×160 方块柱。一个繁忙区块更新就可能造成主线程偶发高延迟；只看平均 FPS 会漏掉这种尖峰。

**底层修复：** 将接缝 160 格预览带按原生 16×16×16 section 精确划分，新增无 OpenGL/无 Minecraft 依赖的 `SeamSectionWorkQueue`，每客户端 tick 最多处理 **4 个 section**（理论待扫描方块上限 16,384），相比原来的两个全柱 81,920 方块上限减少 80% 的单刻基础状态扫描量。工作队列按区块 X/Z 和世界原生 Section Y 三元组去重，优先玩家身边区块与接缝附近段，离开 5×5 窗口立即丢弃尚未执行的重建任务。跨 Chunk 边界的六面遮挡变化会一并使四邻列对应区块段失效。

**画面连续：** 每个完成的 section 原子替换 `Slice` 缓存，不再把尚未构建完成的一整根柱直接清空；远端 ClientLevel、所有原生区块数据仍属于目标维度，其他已完成区块段可以继续绘制。空 section 和卸载 section 对应的老预览会清理，防止悬空的旧地形。支持接缝边界不对齐 16 的 Mod 世界及负 Y 区块段；服务端存档与默认堆叠顺序不修改。

**新增可验证指标：** 真实 NeoForge GUI E2E 输出 `rebuildCpuP95Ms`、`pendingSections`、`lastTickSections`，并有硬性日志检查 `lastTickSections <= 4`。F3 显示“待重建段”和重建P95，与 `nativeModelCpuP95Ms`（绘制提交）区分。新增纯 Java JUnit 测试队列去重、原生负高度 Section、逐 tick 上限、移动窗口清理和禁用清理。

**明确限制：** 这里只优化现有后备渲染器的 CPU 几何重建峰值，**不是**最终 GPU 原生区块网格、双 `LevelRenderer` 帧缓冲合成、Iris/Voxy 光影兼容或 Sable 物理化船体跨维。这些目标需要独立版本锁定的真实图形和多模组验收。先比较 CI 实际的客户端重建P95、绘制P95及真实截图，再谈收益。


## v0.9.8-dev — 独立渲染缓冲池，消除跨 Mod 提交冲突

**源码审计发现的底层风险：** 旧 `SeamNativeGeometryClient.render` 在原版 `RenderLevelStageEvent.AFTER_BLOCK_ENTITIES` 中，获取 `mc.renderBuffers().bufferSource()`，在绘制远端模型后调用该**主世界共享对象的** `endBatch()`。这可能提前提交实体、方块实体、Create/Sable 或光影 Mod 仍在使用的缓冲，形成跨维度图像污染及不可预测的渲染顺序。使用减少模型数并不能修复这个问题。

**这次实际改造：** `SeamRenderBuffers` 通过 NeoForge/Minecraft 1.21.1 原生 `MultiBufferSource.immediateWithBuffers` 与 `ByteBufferBuilder` 创建 **WorldSeam 自己管理**的专用缓冲；针对 solid/cutout/cutoutMipped/translucent 四个常见渲染类型各维护独立构建缓冲，另设 shared fallback。初始堆外缓冲预算总计 **1.75 MiB**，只在首次需要绘制远端地形时懒创建，后续逐帧复用；注意 `ByteBufferBuilder` 运行时可以自动增长，1.75 MiB 是初始容量而非绝对硬内存上限。

远端渲染只调用自己缓冲池的 `flush()`，用 `try/finally` 确保异常时也完成原有资源的正常提交，模型的 PoseStack 也在单次绘制抛异常时恢复。不再对 `Minecraft.renderBuffers()` 做任何远端绘制或 `endBatch()`。退出世界、停用 WorldSeam、更换第二维度后清理所有 `ByteBufferBuilder` 堆外内存；初始化失败时同样释放已创建内存，防止 native buffer 泄漏。

**真正测试的验收条件：** NeoForge GUI E2E 除原有双向穿越、TNT 跨维爆炸、末地岛、区块驻留和关闭清理外，新增 `ownBufferActive`、`ownBufferCreations`、`ownBufferFlushes` 运行日志；远端模型绘制时必须实际使用且提交自有缓冲，关闭后必须释放自有缓冲并增加 disposals 计数。保留独立 CPU P95 和重建 P95，验证修复没有引入性能灾难。

**安全边界：** 这是为双独立 LevelRenderer/FBO 做的资源隔离基础，**不是已实现** Iris 着色器上下文切换、Voxy LOD、Sable 物理子世界自由穿越或全 GPU 多维度渲染。所有这些仍需版本绑定的真实客户端集成测试，不能从一个成功截图推断“完美兼容”。本次未改航空学、NPC 或 Sable 代码，也未改变末地作为新世界最底层的默认安排。


## v0.9.9-dev — 第二个 LevelRenderer 的原生缓冲完全分离（阶段一）

**主动排查的问题：** v0.9.8 已隔离远端逐方块代理视图的 `MultiBufferSource`，但创建真正的次级 `LevelRenderer` 时仍给它传 `mc.renderBuffers()`（主世界同一套 `SectionBufferBuilderPool` 和 `fixedBufferPack`）。这会让远端原生 Chunk 编译与主世界争夺方块网格构建池，若未来启用同帧双世界原生网格渲染更容易产生任务争用和跨 Mod 问题。

**这轮实现：** 使用 NeoForge 1.21.1 的真实 `new RenderBuffers(1)`，每个真实次级 `LevelRenderer` 配一套独立原生 section builder pool 和实体/方块渲染 BufferSource，只预留一个远端编译 pack，不随主世界视距扩大；预览模型仍复用 v0.9.8 的独立小缓冲。渲染器与专用缓冲的拥有关系通过 `IdentityHashMap<LevelRenderer,SeamOwnedLevelRenderBuffers>` 跟踪。跨维直接 handoff 会交换主、副 Renderer，因此资源的释放不能仅根据变量名称 `remote` 判断，而是检查**这个 Renderer 是否还在 `Minecraft.levelRenderer` 活跃**。未活跃且被清理的 Renderer 关闭后，再释放 pool 中所有归还的 SectionBufferBuilderPack、固定 pack，以及通过受限客户端 Mixin 获取的 `ByteBufferBuilder` 共享及固定层缓冲。退出世界时也执行资源释放，初始化失败则立即释放已分配的内存。资源池大小与创建/释放统计加入真实客户端中文调试 HUD。

**严格测试：** 真实 NeoForge GUI 预览时必须发现实际独立次级渲染缓冲（当前只有一个持有者）；玩家下界双向穿越、末地预览和 TNT 后停用 WorldSeam，必须正确保留当时仍活跃 Renderer 的缓冲、同时关闭所有非活跃次级 Renderer 的原生内存。测试记录 `WORLDSEAM_E2E_NATIVE_RENDERER_POOL` 和释放计数，配合既有模型绘制/区块重建 P95、25/25 区块驻留与 TNT 验收。

**边界提醒：** 这只是独立原生 Chunk 编译**资源**，并不代表已经把完整原生 `LevelRenderer.renderLevel` 输出以深度/遮挡/透明/光照正确合成到主世界，也没有证明兼容 Iris、Sodium、Voxy 或 Sable 飞船连续刚体跨维。未来仍要通过带实际第三方 JAR 的版本限定 E2E。新世界默认主世界→下界→末地不变，末地航空学载具原有悬浮行为不变；不修改 NPC 或航空学工程。


## v0.10.0-dev — 原生维度激活后正常区块流修复

针对实际用户反馈“进入下界后仍只有 5×5 预加载、继续走就卡区块”：审计发现 v0.9.9 的第二 `ClientLevel` 创建参数 `viewDistance=3` **写死**，随后被提升为主世界时仍持有只有小范围容量的原版 `ClientChunkCache`。5×5 暖缓存是跨界前的临时视野，**不能替代维度切换后玩家设置的原生视距**。同时不调用目标 `ServerChunkCache.move(player)`，无法强制同步原版玩家区块追踪状态；之前 E2E 仅核实 5×5 和瞬间坐标正确，无法证明正常地形持续加载。

现在第二客户端世界以玩家视距初始化缓存（3–32 范围限制），设置改变时自动扩缩；在原生无 Respawn 切换前明确调用 `updateViewRadius` 和 `updateViewCenter`，转换后继续使用真正的原版 `ClientLevel`、`ClientChunkCache` 和 `LevelRenderer`。服务端完成真实玩家维度迁移后显式调用 `target.getChunkSource().move(player)`，让原版 ChunkMap 追踪、正常光照和实体区块流继续工作。原来单独的 5×5 缓存只负责 **接缝预热**，不是新维度的原版渲染距离。

此外，预热服务器区块不再使用 `getChunk` 在主线程同步触发远端新区块生成；依赖其本来已经注册的原生区块票据异步等待，延迟加载不会冻结整个服务器。真正过界时若目标落点区块暂不在服务器内存中，不强行 `getChunkAt` 生成，而是推迟本次过界。预加载进度不会再反复在游戏动作栏弹出；F3 中文 HUD 留有诊断。修复原来的稀疏备用模型与区块段备用渲染偷偷使用并 `endBatch()` 主世界共享顶点缓冲的问题：两者也复用 WorldSeam 自有专用缓冲池。

**新增真 NeoForge 客户端强断言：** 实际无 respawn 从主世界进入下界后，等待原版区块追踪稳定，要求在初始 5×5 预热窗**以外四个区块**处出现玩家视距范围内的真实下界 Chunk（且客户端总原生区块数 >25）。有 `WORLDSEAM_E2E_FAIL_NATIVE_ACTIVE_STREAM` 则整个 GUI 工作流必须失败，不能再拿「只收到 25 个」冒充原生世界流畅。新单元测试检验正常视距、负 Chunk 坐标和区块预热半径划分。原有真实 TNT、双向过界、末地、关闭资源释放均保留。

**明确尚未完成：** 完整的接缝异维同帧 GPU 地形/天空/透明流体和 Iris/Voxy/其他模组渲染兼容。老 `renderSingleBlock` 代理视图仍非原生场景合成；本次补救用户反馈的**跨界之后原版正常持续加载**与服务器区块卡顿，必须等完整实机测试验收，不能提前声称视觉完全无缝。


## v0.10.1-dev — 长时间真实客户端高压测试与实体卡顿修复

从玩家反馈“开挖上下接缝、反复出入仍偶发卡顿、实体甚至崩溃”出发，不再用一次成功传送或少量截图代表稳定性。新增 **真实 NeoForge 1.21.1 图形客户端**独立 Actions `worldseam-stress-e2e.yml`：从通常的 1,500+ 游戏 tick 旧存档 E2E 开始，在**同一个实际世界和客户端进程**里切换至新世界默认的 **主世界 → 下界 → 末地** 顺序，在一次性的 CI 地图把主世界底部↔下界顶面、下界底部↔末地顶面（包括真实基岩）挖通，之后连续触发 **6×4=24 次**物理接缝过界（末地→下界→主世界→下界→末地）。每次必须通过服务器真实 ready 握手和原有 `SeamServer` 跨界逻辑、验证实际客户端维度已改变，以及目标原版世界客户端能够实际收到并绘制真实实体 `ArmorStand`，并定期检查至少九个目标原生 Chunk、第二渲染器缓冲池最多两个活跃 owner。记录内存、区块和渲染 CPU 时间，并在第 4、12、20、24 次穿越截取真实 Minecraft 图像。崩溃、超时、实体未同步或泄漏均导致 Actions 失败，保留完整日志、crash-reports、连续截图与阶段数据。超时 40 分钟，**跑满并看到 `WORLDSEAM_STRESS_PASS` 和 `WORLDSEAM_STRESS_PASS_CLEANUP` 才算通过**；绝不将工作流存在或「已触发」当成通过。

**实际热点修复：** 旧版 `EntityTickEvent.Post` 在世界里**每个实体每个游戏刻**都可能先解析目标维度、判断是否允许跨维，再决定是否接近接缝；把目标维度和其他 Mod 的 hook 调用提前变成 `2.0` 格接缝内才处理，远处的实体减少额外 CPU 与兼容冲突。实际实体进入接缝前若目标原生 Chunk 未加载，**拒绝主线程 `target.getChunkAt()` 同步生成整个区块**，改成推迟本次实体迁移；所有真实迁移继续检查源和目标方块、边界和碰撞，不伪造实体副本。

**保留限制：** 该真实 GUI 是软件 OpenGL 的自动化压力场景，不是玩家手动连续键鼠玩 30 分钟，也不是 GPU FPS 基准。与 Iris、Voxy、Create、航空学/Sable 的具体版本和多人服务器还需专门 CI 测试；真正的两个 `LevelRenderer` 跨世界完整地形/天空合成仍未完成。用户的世界不会被这套挖通/压力测试改变，它只在 `-PworldseamStress` 指定的一次性世界启动。


**长测 CI 编排修正：** 独立 `worldseam-stress-e2e.yml` 不再先跑 1500+ tick 旧顺序截图 E2E（该工作流自己独立运行，已经通过），而是在新建的真实一次性世界中从主世界立刻开始试验，主动启用 no-respawn、创造模式飞行和默认堆叠，开挖主世界底面、下界顶面及底面、末地顶面的实际方块。24 次循环顺序改为 `主世界→下界→末地→下界→主世界` 共六轮。固定实体见证位置随目标连接面在内部±2格，避免位于接缝**外侧**的测试实体被 WorldSeam 正确地立即传送到相邻维度而产生假阴性。旧截图和新世界性能长测分别留下真实证据，不再使用易超时的串行等待。


## 240 次跨维长时间压力测试（独立 CI）

在首轮 v0.10.1 的真实客户端 **24/24** 次穿越通过后，新增独立长时版：软件 OpenGL NeoForge Xvfb 同一进程中在真实挖通的原生三维度连续执行 **60 轮 × 4 段 = 240 次** 玩家无 Respawn 过界。每一次必须有实际客户端/服务端切换且获得本轮生成的盔甲架 UUID；每轮检测原生 Chunk 驻留、第二渲染器 owner 不超过2，记录堆内存峰值、超过250ms/1秒游戏刻间隔及最坏卡顿，在第40/120/180/240次保存截图。崩溃/日志和图片全部作为 Actions Artifact。仅在 CI 的一次性世界执行，不会挖玩家存档里的基岩。原先逐10tick跨界阻塞日志只在单独的 `-Dworldseam.traceCrossings=true` 下输出，避免长时高压测试本身制造不必要的日志 CPU 开销。总时限40分钟；没有 240/240 通过之前不得宣称长时间稳定性完成。


## v0.11.0-dev — 持久化 GPU 原生区块段模型，解决远端视觉严重残缺

**实测问题**：旧 WorldSeam 在 `AFTER_BLOCK_ENTITIES` 阶段每帧反复调用 `renderSingleBlock`，为了避免 P95 58ms 以上的 CPU 帧开销，动态限制成几百到几千个模型。接缝下明明已收到 25/25 的远端真实 Chunk，画面却只能零散出现几个面，严重不像完整的原版世界。Voxy、Iris 等 Mod 更不能靠这个方块代理绘制方式自动兼容。

**本次真正改变渲染路径：** 新增 `SeamGpuSectionRenderer`，以远端实际 `ClientLevel` 收到的真实 `BlockState` 为输入，把每个 16×16×16 Section 的方块模型根据其原始 `RenderType` 分批生成 `MeshData`，上传到原版 `VertexBuffer`（GPU 缓存）且跨帧保留。未更新的区块**无需每帧重复烘焙模型**；按当前相机透视，对所有已上传且通过 Frustum 测试的区块段直接做 GPU 绘制，**不再用 3500 个方块/帧的上限截断完整地形**。每帧至多新上传一个 16³ 区块段，逐步填满可见世界；尚未上传的部分保留安全、分预算的旧模型绘制。区块实时更新时在 GPU 新模型上传成功前保持旧图像，防止黑帧。远离接缝、世界切换、禁用 WorldSeam 或退出时，立即清理对应 GPU VBO，跟随原有两客户端世界完整生命周期。

**实际视觉验收新标准：** NeoForge 真实 GUI 脚本除了原有玩家穿越、下界真实 TNT、末地自然岛和 25/25 远端 Chunk 检查外，新增 `WORLDSEAM_E2E_GPU_SCENE`：必须上传真正的 GPU 区块段，在渲染事件中至少覆盖 50% 的可见区块段，并显示至少 5000 个来自远端的真实 Block 模型，不能再靠少量散块通过。新增 `WORLDSEAM_E2E_GPU_END_SCENE` 确认末地石是通过持久 GPU 缓存而非截图假图像实际绘制。测试日志继续记录 render CPU P95、重建 P95、GPU uploads/drawCalls/coverage，与之前旧路径的模型提交成本分开。

**失败回退：** 如果第三方动态 block model 导致 GPU 编译阶段抛出异常，会记录异常并回退到之前自有 BufferSource 的有限模型路径；用户也可在启动 JVM 添加 `-Dworldseam.legacyBlockPreview=true` 强制使用旧渲染器方便排查兼容问题。真实图形验收必须验证 GPU 路径没有回退，不能把 fallback 当成功。

**未完成范围**：这是真实 GPU 网格缓冲渲染的重大阶段，并非已达到 Immersive Portals 的完整双 `LevelRenderer` 同帧世界合成；原生不同维度天空/天气/水体/实体/光影阴影、透明排序及 Iris/Sodium/Voxy 插件特有上下文仍需继续集成与实机验收。新世界顺序主世界→下界→末地、Sable 末地物理悬浮行为均不变。严格只修改 `mods/worldseam/**`，不涉及航空学/NPC。
