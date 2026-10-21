# WorldSeam · 界缝

**独立的 Minecraft 跨维度世界底层 Mod**（仍处实验阶段）。

- Minecraft：1.21.1
- 加载器：NeoForge 21.1.248；Java 21
- **Mod ID：`worldseam`**
- Java 包：`dev.reme.worldseam`
- 独立 Gradle 工程：`mods/worldseam/`
- 构建产物：`mods/worldseam/build/libs/worldseam-0.8.0-dev.jar`
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

默认堆叠为 **末地（顶部）→ 主世界（中间）→ 下界（底部）**，默认无环。未来新生成区块才会可选地处理接缝的基岩，已有地形不会自动回填或剪裁。**暂未实现**跨维完整视频遮挡合成、原生真实全维度地形拼接、直接编辑 DimensionType 原生高度、Mod 维度生成器重建。


## v0.9.0-dev：双维度 Chunk 驻留与动态更新

详细说明和验证状态请阅读 [docs/DUAL_WORLD_LOADING.md](docs/DUAL_WORLD_LOADING.md)。这版重点修复下界区块反复加载、客户端丢失区块和动态 Chunk 更新调度饿死。**它并不是完成版视觉无缝。**
