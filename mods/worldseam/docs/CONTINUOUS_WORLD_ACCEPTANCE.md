# WorldSeam — 不可降低的最终验收契约 / Development acceptance ledger

更新时间 / Last review: 2026-10-22. 工作分支：`feat/worldseam-continuous-seam-p2` · [PR #98](https://github.com/g2066203208-beep/mc-studio/pull/98) (draft, must NOT merge yet).

## 永久目标 / Non-negotiable completion gates

- [ ] 主世界、地狱、末地和明确支持的模组维度在接触面如一个连续世界；不能用维度传送、全船原子搬迁、克隆刚体、贴片假渲染冒充无缝。
- [ ] 原生两个 ClientLevel / ServerLevel 的方块、光照、天空、流体、方块实体、普通实体和所有可见几何在接缝同时正确加载、裁剪、同步、渲染。
- [ ] 玩家、Mob、物品、红石、流体、机械、爆炸、实体碰撞都能沿实际空间在接缝实时交互；没有只有脚部的假碰撞，没有重复加载或长时间卡顿。
- [ ] 真正 Create Aeronautics + Sable 船体：**ONE authoritative Rapier rigid body**、两个原生维度的真实接触同时施加法向力、摩擦、转矩；创建器动力图、约束、关节、乘客、力反馈持续一致；跨界船体两半在两边同时实际可见。不能用两个刚体同步或原子传送。
- [ ] NeoForge 1.21.1 真正客户端/服务端 + 240+ 次实玩跨界测试，崩溃、泄漏、画面、FPS/TPS、实体追踪、多模组兼容的记录均通过；得到用户对最终游戏内体验的确认。

## 已实现且有具体实机/CI 证据

- [x] WorldSeam 独立编译、NeoForge GameTest、基础无 respawn 玩家换世界、原生远端 GPU 方块与液体网格、原生预热缓存的**特定场景**：详见 [原版本基线 CI](https://github.com/g2066203208-beep/mc-studio/actions/runs/39771397323)；日志 163/163 visible GPU sections，流体 38,365/38,380 cells 进入绘制流程。
- [x] 240 次真实主世界/地狱/末地往返压力测试，客户端 240 次 handoff，无大于 1 s 的 tick gap： [CI 39771397465](https://github.com/g2066203208-beep/mc-studio/actions/runs/39771397465)。**只证明此基线和环境**。
- [x] 读取 Sable 2.0.6 实际 `ServerSubLevel` / `SubLevelContainerHolder` 接口，真实发布版 Sable + Create 6.0.10 + Aeronautics 1.3.2 的专用服务端 GameTest 中组装了 18 个真实方块并识别一艘 straddling 船体： [CI 39784832595](https://github.com/g2066203208-beep/mc-studio/actions/runs/39784832595)；日志 `WORLDSEAM_REAL_SABLE_STRADDLE_OBSERVED: ... straddling=1, physicsBridgeActive=false`。**没有双世界求解器**。
- [x] 修复真实 CI 缺陷：Minecraft `AABB` 测试迁移 NeoForge GameTest；从 Modrinth 官方 `sha512` 验证发布版 JAR；发布版清单写入项目路径；专用服务器避免反射扫描 ClientLevel overload。

## 本轮新增、未验收 / Changes needing current-head CI

- [ ] `SeamBodySplit` + `SeamArrival.hasJoinedClearance`：玩家/普通实体进入相邻维度前用**真实两个 ServerLevel** 的原生碰撞体检查自己身体的两部分；注意它还不是全帧实体碰撞统一求解。
- [ ] `SeamNativeGeometryClient.isBuried` 按逻辑接缝 Y=128 而非地狱原生最大构建高 Y=256 读取跨维相邻方块；新增真实 GUI 可见性回归检测。
- [ ] `SeamShipChartBounds`：将同一船体的**AABB broad-phase 体积**映射成两个维度的裁剪区，不代表实际 OBB/网格碰撞求解。
- [ ] `SeamShipResidency`：真实 Sable 船体即使**没有玩家在附近**，接近维度接缝时仍异步向相邻 ServerLevel 申请有界区块 region ticket；不调用同步 `getChunk`，32 个唯一 ship UUID 上限，远离、卸载、禁用时移除；新增官方 Aeronautics/GameTest 验收：真实飞船接缝必须持有远端票据。
- [ ] 最新改动全部独立 Build / JUnit / GameTest、真实发布版第三方模组 GameTest、GUI 与 240 次长测的 CURRENT HEAD 通关。注意中间旧提交成功不等于最新版本通过。

## 实现中必须遵守的约束

- 只修改 `mods/worldseam` 及其专门 CI，不影响 NPC、攀爬、机械 Boss 等独立工作室。
- 第三方 JAR 仅在 CI 中下载运行并校验官方 SHA-512，不复制或重新分发 AeroPortals/Sable 等限制许可代码。IPSable 的 Apache-2.0 方案可作技术研究，但实际以 Sable 官方发布版 API 及许可证为准。
- 不能将 source/target 的双份显示缓存视为两份物理刚体；世界真实接触必须通过一个原生 solver 生成 impulse，并且没有原生光照/液体流动时不可说“完全连在一起”。

## 下一轮最高优先级

1. 检查 PR #98 当前最新提交的 [Actions](https://github.com/g2066203208-beep/mc-studio/actions)，修复任意独立 GameTest、真实 Sable 船体/区块票据、真实 GUI、长时间压力测试失败；为新增票据补移出接缝释放和断线/禁用 GameTest。
2. 研究并实际实现 Sable/Rapier 在原生两维地图上的 chart 区隔及 image colliders + single rigid body contact manifold；必须测试真实组装船压住不同维度方块所产生的同体 torque。未通过则 `physicsBridgeActive` 保持 false。
3. 完整实体/船体原生渲染裁剪、跨界动态光照与真实交互的连贯路径，并逐项用真实 MC 客户端视频/截图作验收。

每小时开发工作记录应在实际提交和 CI 证据基础上更新这里，目标不能因为实现困难自行缩减。


## 2026-10-23 07:21 CST — verified development iteration

- **Actual commit:** [63e5150](https://github.com/g2066203208-beep/mc-studio/commit/63e5150c44e97a71974cb6239ee40a1d4d8587d3) fixes the JVM PID matcher in the real Minecraft ship-film CI watchdog and extends native thread-dump timeout. This is a diagnostic repair, **not** a physics or gameplay pass.
- **Reproduced failure:** [ship film run 39807716239](https://github.com/g2066203208-beep/mc-studio/actions/runs/39807716239) recorded actual native clear commands in Overworld and Nether and actual aircraft staging, but server tick handshake never completed; no assembled ship UUID or bidirectional physical flight.
- **New-head checks triggered (PENDING, not passed):** [Build](https://github.com/g2066203208-beep/mc-studio/actions/runs/39871347193), [Independent Mod](https://github.com/g2066203208-beep/mc-studio/actions/runs/39871347295), [Real Aeronautics](https://github.com/g2066203208-beep/mc-studio/actions/runs/39871347143), [GUI](https://github.com/g2066203208-beep/mc-studio/actions/runs/39871347132), [240-crossing stress](https://github.com/g2066203208-beep/mc-studio/actions/runs/39871347325), [genuine ship MP4](https://github.com/g2066203208-beep/mc-studio/actions/runs/39871347136).
- **Still failing/not implemented:** one authoritative Rapier body with simultaneous two-dimension contact forces and torques, real Aeronautics-powered down-and-return footage, full native light/fluid/entity continuity and sustained stutter-free play. Existing GameTests cannot certify those conditions.
- **Next highest priority:** read the new ship-film JVM thread stack on failure, identify and repair server stall before Sable assembly, and rerun genuine film. Enforce fresh-save protection before any destructive film setup. Verify powered propeller shaft orientation on released Aeronautics before treating a test force as actual propulsion.
- PR #98 stays **Draft**. Do not merge until every permanent acceptance gate above is demonstrated and the user confirms satisfaction.


## 2026-10-23 — native load deadlock patch, proper source comparison, film-gate correction (CURRENT HEAD)

**PERMANENT TARGET UNCHANGED: all five non-negotiable acceptance items above remain unchecked.** Do NOT merge PR #98.

### Concrete GitHub commits in this iteration

- [bc37f75](https://github.com/g2066203208-beep/mc-studio/commit/bc37f75b7cea55e585eccff3f4543b81110959fe) and [6dd295a](https://github.com/g2066203208-beep/mc-studio/commit/6dd295a939328239415341fffcdc3171a0695cb8): move new-chunk BEDROCK writes out of `ChunkEvent.Load` into a bounded post-server-tick queue. Neither the target chunk nor its 3×3 neighbors are synchronously loaded; processing is limited to one actual chunk carve per tick. Captures hard queue backpressure rather than rewriting in the unsafe event. Must still verify that chunks whose neighbors remain unloaded eventually get carved, and that vanilla lighting/collider notifications stay consistent. **This is a hypothesis-driven fix for the actual JVM stack from failed flight CI, NOT a game-tested pass yet.**
- [847bbe2](https://github.com/g2066203208-beep/mc-studio/commit/847bbe257c835f2c4523686c372477fba62a09d5): change the published Aeronautics test fixture's rotor from native default axis X to requested vertical Y; no verified aero thrust yet.
- [7bd6649](https://github.com/g2066203208-beep/mc-studio/commit/7bd66493ef241c40f90ac828f98abdd054ff3fe1): install `x11-apps` so `xwd` can capture native Minecraft frames.
- [d181eda](https://github.com/g2066203208-beep/mc-studio/commit/d181eda95b1fd38c3c8548196a1c0a52bf4a1431): enforce three raw frame checks in the film workflow; never label a server-only ship trajectory as a visible GUI MP4. Pixels remain a **necessary but insufficient** criterion, not physical validation.

### Current HEAD = d181eda, new CI (PENDING when first checked)

- [Build](https://github.com/g2066203208-beep/mc-studio/actions/runs/39897413264)
- [Independent GameTest](https://github.com/g2066203208-beep/mc-studio/actions/runs/39897413434)
- [Sable/Create/Aeronautics integration](https://github.com/g2066203208-beep/mc-studio/actions/runs/39897413354)
- [Native GUI](https://github.com/g2066203208-beep/mc-studio/actions/runs/39897413271)
- [240 crossings](https://github.com/g2066203208-beep/mc-studio/actions/runs/39897413343)
- [Unedited ship MP4](https://github.com/g2066203208-beep/mc-studio/actions/runs/39897413331)

Do not mark any as passed before reading the latest checks and real artifacts.

### Upstream source studied, and why current architecture fails the acceptance contract

- [Immersive Portals native world render context](https://github.com/iPortalTeam/ImmersivePortalsMod/blob/1.21/src/main/java/qouteall/imm_ptl/core/render/MyGameRenderer.java) saves/restores the full active/client-side rendering state: `ClientLevel`, `LevelRenderer`, camera, lightmap, render buffers, block entity renderer world, particles, transparent pass and shader context. WorldSeam's current `SeamNativeGeometryClient` only draws extra remote terrain meshes in a late `RenderLevelStageEvent`; this fundamentally omits correct remote sky, live entities, Sable sublevels, block entities, shader transparency, depth/stencil composition. **Render architecture gate: FAILED / NOT IMPLEMENTED**; the current GPU section counter cannot prove seamlessness.
- [Immersive Portals Sable tracking/snapshot integration](https://github.com/UpperMoon0/ImmersivePortals/blob/ad2e7ee5d73e4ff78bd0befade4f797265e65939/src/main/java/qouteall/imm_ptl/core/compat/mixin/sable/MixinClientboundStartTrackingSubLevelPacket_SablePortalCompat.java) saves source sublevel interpolation history before destination tracking. WorldSeam currently can receive Sable movement before client registration. Need a **single authoritative UUID** and reliable client registration/ack before any film motion; no fake full-sync success.
- [IPSable fused substep](https://github.com/r2smith141/IPSable/blob/f5e0471d59407a99c4abd0f084d5a4f2e236f2b8/src/main/java/ipl/sable/atlas/IplFusedStep.java) and [Atlas native contact spec](https://github.com/r2smith141/IPSable/blob/f5e0471d59407a99c4abd0f084d5a4f2e236f2b8/docs/atlas_portal_physics_spec.md): one native world/rigid body, chart-keyed terrain, foreign-chart collider images, ONE native step per substep. Stock Sable's per-dimension Rapier worlds cannot satisfy this using `SeamShipRemoteContactProbe` or a 10-tick observer. Linux native ABI + provenance/license audit required before incorporation. **Unified real Rapier contact gate: FAILED / NOT IMPLEMENTED**.

### Highest priorities next iteration (not lower goals)

1. Read **new-head** CI and download failed native GUI/ship MP4 + JVM dumps; if carve queue blocks, patch based on its exact stack/timing. Add bounded-queue, neighbor-unloaded and reload/cleanup GameTests. NEVER test the destructive `/fill` on non-disposable saves.
2. Replace terrain-only late mesh draw with an actual **second ClientLevel/LevelRenderer full render pass** and seam-plane depth clip, proper sky/lightmap/transparent + entity/BE/Sable client sublevels. This is the main rendering blocker. Keep separated render buffers and restore all GL/client state even on exceptions. Inspect actual upper 1.21.1 NeoForge Immersive Portals implementation; do not transplant Fabric code blindly.
3. Build/verify an ABI-matched, permission-cleared **Linux** Atlas extension (or secure upstream integration) for stock released Sable: chart-keyed terrain, same native body handle and nonmass image colliders, one solver step with simultaneous two-world contact torque. AABB candidate checks do NOT pass.
4. After both foundations work: actual Create/Aeronautics-only thrust, repeat down/up, passenger & mechanical constraints, real frame-following MP4 and 240+ long-run FPS/tick/resource checks. Only actual game/video + native contact evidence count.


## 2026-10-23 additional genuine development — remote BE rendering and notified terrain carving

**Final acceptance gates are unchanged and REMAIN OPEN**: continuous both-ClientLevel rendering / lighting / all entities, truly fused single-body Rapier two-dimension contacts and torque, Create-only powered down/up, passenger continuity, fully clear uninterrupted GUI MP4, sustained performance.

### Actual changes

- [1b3aa0e](https://github.com/g2066203208-beep/mc-studio/commit/1b3aa0ecb41463fb7f684a3555abb03095617b6f) introduces `SeamRemoteBlockEntities`: directly renders real loaded target `ClientLevel` block entities with NeoForge 1.21.1 native `BlockEntityRenderDispatcher`, target-native block entity objects, mapped source-camera coordinates, frustum culling, isolated `SeamRenderBuffers`, and a `finally` restoration of the active world's dispatcher. Does not duplicate BE authority, does not call `getChunk` to generate missing chunks.
- [e852850](https://github.com/g2066203208-beep/mc-studio/commit/e85285083b273de1d34197e17c10cf818d7d8c47) wires this native remote BE pass to actual `SeamNativeGeometryClient.render` and resource cleanup independently of VBO completion.
- [675d6ae](https://github.com/g2066203208-beep/mc-studio/commit/675d6aec41ba456faac54c12a418d8c151dcbbe6) adds actual-native-rendered-frames/error counters, not a fake colored pixel.
- [c6ac16a](https://github.com/g2066203208-beep/mc-studio/commit/c6ac16a485fd62aed2ba0fa5be47fa9adcae8d1c) installs a real Nether-owned CHEST BE in the disposable GUI scene, plus a fail-closed `WORLDSEAM_E2E_FAIL_NATIVE_BE_SCENE` assertion. Other BEs and Sable sublevels still need proper client/world context.
- [690c13a](https://github.com/g2066203208-beep/mc-studio/commit/690c13a1eeea5adf103e5ae99f335154fcbff57e) separates the **published-chunk** carve into 128-cell/one-chunk-per-tick batches with real `ServerLevel#setBlock(...,3)` light/client/physics notification, replacing silent `ChunkAccess#setBlockState` on the loaded chunk. The generation-stage path remains intentionally distinct.
- **Real CI failure surfaced and immediately repaired**: [independent run 39899289755](https://github.com/g2066203208-beep/mc-studio/actions/runs/39899289755) and [released-mod run 39899289724](https://github.com/g2066203208-beep/mc-studio/actions/runs/39899289724) failed to compile because `SeamChunkGenerationMixin` still invokes `SeamTerrain.replaceGeneratedBedrock` during *prepublication* worldgen. [643cb1c](https://github.com/g2066203208-beep/mc-studio/commit/643cb1c0073d20b2659c628f322b42651b6fbcbb) restores that exact worldgen method for safe construction-time `ChunkAccess` mutation; post-load notified changes remain asynchronous/bounded. Must be verified by *new* CI, not assumed successful.

### Current code revision CI (643cb1c; pending at this review)

- [Build](https://github.com/g2066203208-beep/mc-studio/actions/runs/39899530930)
- [independent JUnit/GameTest](https://github.com/g2066203208-beep/mc-studio/actions/runs/39899530931)
- [real Sable/Create/Aeronautics integration](https://github.com/g2066203208-beep/mc-studio/actions/runs/39899530897)
- [real GUI native render](https://github.com/g2066203208-beep/mc-studio/actions/runs/39899530928)
- [240 crossings and resource stress](https://github.com/g2066203208-beep/mc-studio/actions/runs/39899531026)
- [raw real-client ship film](https://github.com/g2066203208-beep/mc-studio/actions/runs/39899530900)

Do not label these passes before reviewing logs/artifacts. The **root architecture** remains insufficient: source-world late-pass VBO terrain and standalone BER are still not a true second `LevelRenderer` full sky/entity/Sable/transparent/depth-composited pass; Java VoxelShape candidates and externally injected forces do not equal foreign real terrain contact inside Sable's one native Rapier solver.

### Priority

1. Fix any regression on the **new final HEAD**. Download raw failed GUI/film artifact and JVM stack, not merely statuses; verify the Nether chest is rendered and bedrock load callback is no longer blocking. Probe 128-cell scheduling and chunk unload/reload cleanup.
2. Implement real dual-ClientLevel full LevelRenderer compositing with seam-plane clip, independent render context/state and Sable tracking; validate native shader/lightmap, entities and resources.
3. Develop a **permission-cleared native physics integration** for the released Linux Sable binary or documented safe release API. Translation-only OW(-64) to Nether(128) permits a 192-block transform, but stock Sable's per-Level scenes and collider-bakery packing must be accounted for. Do not claim `Rapier3D.addChunk` or AABB alone completes chart fusion. Prove same native body ID two-sided forces/torque in the SAME substep, then true unassisted Aeronautics propulsion and riders.
4. Only after that: uninterrupted real MP4 down/up followed by user visual approval, with no PR merge before all gates pass.


## 2026-10-23 13:35 CST — real native chart-collision experiment (NOT final physics/render acceptance)

**Full render / native unified Rapier contacts+torque / autonomous Create flight / passenger continuity / no-stutter gameplay / clearly visible complete MP4: ALL STILL UNVERIFIED.** Draft PR #98 remains unmerged. Do not downgrade these gates.

### Actual implementation, all limited to WorldSeam and related CI

- `a22c32b`: replace the invalid Aeronautics SmartPropeller `AXIS=Y` with the released 1.3.2 `HORIZONTAL_AXIS=X`, `ceiling=false`; reflectively assert the actual kinetic axis Y and valid bottom drive shaft. Confirmed against [Aeronautics upstream SmartPropellerBlock.java](https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/main/aeronautics/common/src/main/java/dev/eriksonn/aeronautics/content/blocks/propeller/small/smart_propeller/SmartPropellerBlock.java).
- `2e510b4`, `b3753a4`, `96ae407`, `d02a6da`, `d0b5378`, `6a41456`: **opt-in experimental Sable JNI remote-terrain path**, disabled in regular worlds, enabled only for disposable ship-film CI. Fetch genuinely resident Nether `LevelChunkSection` blocks, use Sable's published `RapierVoxelColliderBakery`, translate 16-aligned (Nether Y=128 → Overworld Y=-64), call its actual native `Rapier3D.addChunk` on the authoritative Overworld Rapier scene; no second Sable body, no ship teleport. Native block updates invalidate projection, work bounded, chunks cleaned up on unload/exit. This is NOT a full IPSable Atlas: fixed orientation/translation only, conservative CORNER voxel-neighborhood, no full native two-chart ownership, contact-frame transform or gameplay routing.
- `28c4d25`, `9ad3e6a`, `3da9afc`, `0bb93c7`: exact section-aligned chart coordinates with 4 JUnit regression tests, conservative overflow / unsupported mapping rejection.
- `b68ba74`: film refuses to claim native experiment unless actual remote collider cells were uploaded.
- `6fb8779`, `f49baff`, `11b0b239`, `2674805`: film real IRON_BLOCK takeoff platform at Overworld Y=-58 in disposable save, wait for genuine Sable client registration, clear the actual platform with vanilla `/fill minecraft:air replace` before physical takeoff; deny any premature fall as down-crossing; track actual hull every game tick; 35-tick true-under-seam hold, correctly delayed native X11 screenshots.
- `fc165a0`, `5739f68`: log same Sable runtime body ID and genuine JNI Rapier linear/angular velocity over time, not faked ship position changes.
- `72504e3`, `841b493`, `8aeae45`: create one real Nether OBSIDIAN witness below cleared shaft at Nether Y111 (source projected Y=-81); instrument actual Sable JNI block-collision callback with zero injected force/tangent/torque; fail if no native contact is observed. This is only native collision observation, not itself proof of net impulse or complete torque, or autonomous airship propulsion.
- `3a2e347`: real GUI MP4 CI X11 package install bounded to 3+4 minutes, verifies actual `Xvfb`, `xdotool`, `ffmpeg`, `convert`, `xwd`; avoids permanently stuck apt mirror.

### Actual CI and media evidence

- At `a22c32b`, Build [39902746320](https://github.com/g2066203208-beep/mc-studio/actions/runs/39902746320), independent JUnit/GameTest [39902746305](https://github.com/g2066203208-beep/mc-studio/actions/runs/39902746305), and released Sable/Create/Aeronautics GameTest [39902746325](https://github.com/g2066203208-beep/mc-studio/actions/runs/39902746325) all PASSED on actual Actions.
- [Real film on b68ba74, #39903967077](https://github.com/g2066203208-beep/mc-studio/actions/runs/39903967077), [RAW artifact #12480286638](https://github.com/g2066203208-beep/mc-studio/actions/runs/39903967077/artifacts/12480286638): actual UUID `f18b5563-2a70-4c53-babb-3829a3329fc9`; actual source-authority Rapier scene received 56 Nether terrain section uploads and 145,484 cumulative non-air native collider voxels. Genuine body Y motion down/up happened with **externally imposed test forces** and initial 40-tick preflight GRAVITY fall; it was NOT an autonomous two-world flight. Unedited 39.1s X11 MP4 shows recognizable airplane only in first of three stage frames, and no clearly visible airplane after going below seam or returning. Pixel gate properly FAILS. Source log also reports a GLSL 4.60 error under Mesa llvmpipe max GLSL 4.50; whether it contributes to invisible aircraft still unproven. Do NOT link film as successful.
- At `8aeae452`, actual Actions Build [39905461066](https://github.com/g2066203208-beep/mc-studio/actions/runs/39905461066), independent GameTest [39905461022](https://github.com/g2066203208-beep/mc-studio/actions/runs/39905461022), released Sable/Create/Aero [39905461046](https://github.com/g2066203208-beep/mc-studio/actions/runs/39905461046), GUI [39905461048](https://github.com/g2066203208-beep/mc-studio/actions/runs/39905461048), 240-long [39905461032](https://github.com/g2066203208-beep/mc-studio/actions/runs/39905461032) PASS their CURRENT LIMITED TESTS. Real ship film [39905461137](https://github.com/g2066203208-beep/mc-studio/actions/runs/39905461137) still running when last checked; its package-install step had stalled >10m and was not recorded as passed. CI install timeouts committed afterward.
- Latest head after these docs is not a verified success. Re-run/inspect all six Actions for **this HEAD**, download full log/artifact, compare native contact callback count, confirmed same body ID, measured native angular response, unassisted propulsion versus force inputs, and visual ship presence. Do not infer success from green non-ship tests.

### Unchanged next gates (ordered by real physical correctness)

1. **Native two-world contact and torque**: verify JNI callback after true shared Rapier step, independently measure source/target contact impulses and off-axis angular response, handle world edit/unload while straddled, native ABI and third-party license. Above prototype cannot support general arbitrary rotated modded dimensions; need true IPSable-style native shared atlas/charts/image-collider or equivalent.
2. **Full dual-world render**: replace late terrain-only GPU VBO overlay with proper secondary native `ClientLevel/LevelRenderer` render context and seam depth/stencil/transparent compositor; sky and weather, native lightmaps and fluids, live entities, block entities and Sable sublevel render/pose/snapshot across boundary. First/below/return actual MP4 visible not color proxy only. Check GLSL 4.60 vs current runner 4.50 and real GPU/OpenGL limitations.
3. Genuine reversible Create/Aeronautics thrust without test-imposed Sable impulse, mechanical stress, passengers retained, correct overlap of real two-world contact manifolds and original single Rapier body. 240+ continuous crossings with real FPS/tick measurements and user-approved video.
