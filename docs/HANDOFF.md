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

## 2026-10-21：沉浸车辆模型研究 Skill（新任务）

- 用户要求把持续模型研究变为可复用 GitHub Skill；在独立分支 `docs/immersive-vehicle-model-skill` 增加 `skills/immersive-vehicles-modeling/SKILL.md`、真实 OBJ+JSON 的五种车辆研究矩阵、造型/UV/动画/安装契约、原创摩托车示例、纯标准库 OBJ/PNG/JSONC 检查器与单测。
- Skill 和网格检查不是已经创建原创摩托车 OBJ，也不表示 Minecraft、Sable 或公开网页版已通过真实游戏测试；下一位助手在用户要求做摩托车时按 Skill 创建并提供真正可旋转检视的新模型。
- 已有真实 3D 源码 `viewer/index.html`，主要针对 Mustang；如果要预览自己制作的摩托车需扩展支持任意 OBJ+PNG。
- 操作门槛：审查来源许可后使用原创资产；执行自动化测试，再审阅 CI 和 PR。

## WorldSeam 独立 Mod（2026-10-21）

从 `main` 开始的 `feat/worldseam-standalone` 仅新增 `mods/worldseam/` 及其独立 CI、仓库说明。WorldSeam Mod ID 改为 `worldseam`，入口 `dev.reme.worldseam.WorldSeamMod`，指令 `/worldseam`。旧引擎功能分支 PR #18 应标记被取代、不可合并；PEAK 攀爬分支保持原状。对新 Mod 的验证必须在独立 `mods/worldseam` 下完成，不能用工作室通用 `mod/` 编译替代。

- WorldSeam v0.6.0-dev 新增**默认关闭**的 `/worldseam smooth enable` 原生无 Respawn 协同换维实验（9 个真实远端 Chunk+light 预热、握手、ClientLevel/LevelRenderer 直接切换）。仅可备份测试世界验证，CI 不可替代 GUI 实机；主要源码 `mods/worldseam/`，见 `mods/worldseam/docs/NO_LOADING_ARCHITECTURE.md`。保持 PEAK 攀爬完全独立。

## 2026-10-21 · WorldSeam v0.7.0-dev 维度真正物理边界纠偏

**缺陷原因**：原代码把 Nether 原版建筑高度 Y256 当成跨界坐标，但可通行的屋顶边缘实际是 Y128。之前 `SeamPreviewSender.sampleHorizon`、`SeamPreviewSender.sample` 等仍有错误目标/源边界，致跨维渲染偏移 128 格；另外 `Link.crossed` 要求玩家超出上下界 0.25 格，不是靠近平面触发。原版 teleportTo 默认还是加载换维。之前的“无缝已做好”说法不成立。

**对照的源项目**：`UpperMoon0/ImmersivePortals`：`VerticalConnectingPortal.java` 在自定义 Y 平面建立巨大水平连接面、`DimStackInfo.java` 允许边界 Y 覆盖、`MixinChunkStatusTasks_BedrockReplacement.java` 在 ChunkStatusTasks.generateSpawn 阶段处理基岩。仅学习实现思想，不复制代码、不添加门户 Mod 依赖。

**本分支修复**：统一逻辑边界 Nether Y128；独立新 ChunkStatusTasks Mixin 在世界生成阶段除去接缝基岩（管理员开关后，仅新区块）；`SeamCrossing` 在边界平面内侧 0.5 格、朝向面运动时提前交接，不需要先越出建筑高度；远端 LevelRenderer 收到 Chunk 后通知 section mesh 重建；`/worldseam enable` 默认走 no-respawn（若没有预热完成则阻挡边界，不以加载换维假冒成功）；`/worldseam setup-test` 为明确授权的破坏性测试世界命令：为当前X/Z 16×16区块柱在主世界地板/地狱屋顶删去既有基岩。以上只在 `feat/worldseam-standalone`，与 PEAK 攀爬绝不合并。

**禁止宣称最终无缝**：没有 GUI 实际跨界/双向像素级验收和实体/碰撞/粒子/光照整合；自动构建、GameTest、headless-client 仅部分门槛。后续工作必须实现远端全维 `LevelRenderer` 在统一帧中按平面遮挡渲染、服务器/客户端多实体和光照数据正确按维度同步、镜头跨面交接，无加载录像完成才可称交付。


## 2026-10-23：WorldSeam P2 航空学真实录像故障与修复交接（PR #110）

- 只改 `mods/worldseam/` 及本交接文档；目标 Create: Aeronautics + Sable，**不是** Valkyrien Skies，不与 NPC/PEAK/Boss 合并。
- [WorldSeam P2 PR #98](https://github.com/g2066203208-beep/mc-studio/pull/98) 仍需统一 Rapier 双维原生求解器、Sable 船体跨界裁剪/渲染、原生流体/实体完整连贯，不得宣称完美。
- [本次修复 PR #110](https://github.com/g2066203208-beep/mc-studio/pull/110) 在 P2 head `75cbf87` 上修复 Sable 同艘船双目标图表区块票据互相替换，新增 5 个 JUnit 案例；单船同场最多 2 个目标图表，32 船/64 票据上限。
- **关键真实故障**：[Actions 39882697903](https://github.com/g2066203208-beep/mc-studio/actions/runs/39882697903) 的 83 秒原生 Minecraft MP4 **失败**，有真实 /fill 与构建的 Create+Aero 方块飞船，但是无法组装后往返。对应 `real-ship-jvm-thread-dump.txt` 的服务器栈为 `ChunkStatusTasks.full -> SeamTerrain.onChunkLoad -> LevelChunk.setBlockState -> Sable RapierPhysicsPipeline.handleBlockChange -> LevelAccelerator.getChunk -> ServerChunkCache.getChunk (park)`。因此删除加载时逐方块改基岩的事件，仅在生成阶段 `ProtoChunk` 做可选基岩处理。另将发布版 Aeronautics 默认 X 轴螺旋桨改为与上向 Create 马达同轴的 Y 轴，实际速度/推力还必须在 GameTest 中确认。
- 录像 CI `mods/worldseam/ci/record_real_sable_ship.sh` 追加真正的客户端已可渲染、飞到界缝下方、返回时 3 张截图，并通过 `ci/check_real_ship_pixels.py` 校验船体蓝黄方块是否真实出现在像素中；不允许黑洞/空画面成功，也不把测试外力推动说成 Aero-only 推进。
- 新版 Actions [真实电影](https://github.com/g2066203208-beep/mc-studio/actions/workflows/worldseam-ship-film.yml)、[真实航空学](https://github.com/g2066203208-beep/mc-studio/actions/workflows/worldseam-sable-real-mods.yml)、[独立 Mod](https://github.com/g2066203208-beep/mc-studio/actions/workflows/worldseam.yml)、[240 次压力](https://github.com/g2066203208-beep/mc-studio/actions/workflows/worldseam-stress-e2e.yml) 必须看当前 PR HEAD；上轮成功不替代当前测试。**PR #110 及 #98 都保持 Draft**，未有完整双维动态接触/真正 Aero-only 自主往返视频证据前禁止标记通过。
