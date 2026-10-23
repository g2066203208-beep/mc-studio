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


## 2026-10-23 15:20 CST — native voxel-neighborhood and witness-isolation code (not full acceptance)

**Real implementation commit:** [c4e1700](https://github.com/g2066203208-beep/mc-studio/commit/c4e1700ba726785dcdb97fc7e34a6491369328fd), limited to `mods/worldseam`. PR #98 is still Draft, unmerged. Do not claim the full dual-LevelRenderer renderer or shared two-chart Atlas exists.

### Changed in actual Java

- `SeamRapierRemoteTerrain` now derives the native voxel-neighborhood byte for each **real loaded** foreign-world collider, not a global `CORNER` byte for every block. Sable's actual `VoxelNeighborhoodState` enum is resolved by name and its published `byteRepresentation()` at runtime. Six adjacent blocks are read only via `getChunkNow`; no synchronous chunk generation and no foreign Nether roof outside the configured seam slice. Missing neighbors stay conservative.
- The per-`BlockState` cache holds only the native collider **handle**; `FACE/EDGE/CORNER/INTERIOR` depends on the individual voxel's six loaded neighbors and is never cached per shared state. Full-block, liquid/kelp and Sable collision-callback distinctions are considered. Neighboring native sections are invalidated on a face edit so contact classification can be rebuilt. This improves the **existing experimental translated terrain upload**, not the architecture of Sable's solver.
- The observed real Nether obsidian JNI collider is now installed **only at the exact witness (x,y,z)**; it is never cached as the collider of all obsidian blocks sharing a `BlockState`. Re-registering the witness requeues its native section. The pass-through callback still injects **zero** force and does not move the ship.
- New dependency-free `SeamRapierVoxelNeighbors` and five JUnit tests check the six-direction classifications, conservative special/empty cases and exact witness isolation. They must pass real independent CI before being called verified.

### Upstream code studied (not copied into this repository)

- NeoForge 1.21.1 Immersive Portals `ClientWorldLoader`: each `ClientLevel` has a separate `LevelRenderer`; the scoped world switch replaces `Minecraft.level`, `Minecraft.levelRenderer`, particle engine world and network handler world, **then restores all four in `finally`**. Remote world ticks include entities and light updates. This is materially more than WorldSeam's current late VBO and standalone BE renderer.
- `WorldRenderInfo` stores the destination world/camera/transform, render distance, sky and fog state in a scoped stack; `MixinLevelRenderer` clips the actual native section render and manages translucent/entity phases. These are the required architecture references for a real full second-world renderer, not proof that WorldSeam has implemented it.
- Sable `RapierPhysicsPipeline` has one native scene **per ServerLevel** and calls `Rapier3D.step(scene.handle(),timeStep)` in its own substep. Uploading translated remote chunks to one scene does not establish two chart ownership, same-step contact force/torque attribution, full constraint routing, arbitrary modded dimensions or client sublevel tracking. A permission-cleared ABI-matched Atlas/native interface remains required for the full contract.

### CI evidence and exact gate status

At code commit `c4e1700`, fresh Actions were triggered; **not pre-declared passed**: [Build 39917401438](https://github.com/g2066203208-beep/mc-studio/actions/runs/39917401438), [independent 39917401544](https://github.com/g2066203208-beep/mc-studio/actions/runs/39917401544), [released Sable/Create/Aero 39917401420](https://github.com/g2066203208-beep/mc-studio/actions/runs/39917401420), [real GUI 39917401471](https://github.com/g2066203208-beep/mc-studio/actions/runs/39917401471), [240-long 39917401414](https://github.com/g2066203208-beep/mc-studio/actions/runs/39917401414), [real ship film 39917401618](https://github.com/g2066203208-beep/mc-studio/actions/runs/39917401618).

The previous `c676a1f` ship-film run [39906816126](https://github.com/g2066203208-beep/mc-studio/actions/runs/39906816126) **FAILED** visual acceptance: its original log recorded 1900 JNI callback events near the projected Nether obsidian witness, but the old observer collider was cached per `BlockState`, not strictly per witness block; therefore this revision must be retested with the corrected isolation. The earlier MP4 has obvious visibility holes, and its ship motion used external test force. Do not present it as successful flight.

**Still not implemented/accepted:** two complete native ClientLevel/LevelRenderer views with sky/light/fluid/transparent depth/ordinary entities/Sable sublevels; one fully fused native two-chart Rapier scene with measured same-substep two-world contact impulse and torque; autonomous Create/Aeronautics-only down/up; passenger and machinery continuity; 240 real physics crossings; clean unedited visible MP4 and user approval. No merge.

**Next engineering priority:** inspect the fresh compile and real Sable JNI logs for the new neighbor-state bytes and exact witness callback; fix regressions, then implement the second native LevelRenderer state lifecycle and ABI-permitted shared Rapier chart interface rather than adding video scripts or treating ordinary GameTest green as acceptance.


## 2026-10-23 16:22 CST — real Sable-hull-owned native terrain residency (UNVERIFIED new head)

**Actual code:** [6eb1354](https://github.com/g2066203208-beep/mc-studio/commit/6eb1354f003441d61c7083c0ea8d14cb786374eb), [e0a96a1](https://github.com/g2066203208-beep/mc-studio/commit/e0a96a10e34003746f6fe184a4c8d1c4a3acb179). Scope is ONLY `mods/worldseam`; PR #98 remains Draft/unmerged.

- **Root cause addressed:** `SeamRapierRemoteTerrain.tick` previously **closed all foreign-world native colliders when no player stood within 48 blocks of the Overworld seam**, even when a real Sable-owned ship was still near/through that seam. Thus an unattended authoritative ship could lose the foreign collision surface mid-flight.
- `SeamSableObserver` now identifies a **real, OW-owned Sable hull** approaching the configured OW-bottom → Nether-top link, retaining the nearest eligible hull with **ServerLevel identity** and a bounded sample age (20 ticks). It is not a synthetic player position or a cloned body.
- `SeamRapierRemoteTerrain` now anchors its existing 5×5 native foreign-terrain window on the observed ship's X/Z center **before** considering the old spectator/player fallback. When the last spectator leaves, an eligible ship does not automatically close the native scene. The sample cannot be reused after a server restart or after expiry. Native collision uploads remain one section/tick and two sections deep; this is still only a **bounded translation-only experimental bridge**, not Atlas/fused multi-chart physics.
- `SeamShipResidency` extends the bounded asynchronous region-ticket approach window from 16 to 32 blocks, matching the current two projected native sections. No synchronous `getChunk`, teleport, duplicated ship, artificial impulse, or new native binary. New `SeamRapierAnchorTest` covers negative chunk coordinates, world bounds, missing hull and stale/overflowing timestamps. This unit test **does not prove** no-player real Sable physics; that requires a new real released-mod scenario.
- Upstream **Sable** `RapierPhysicsPipeline.physicsTick` steps its own scene per dimension; the `Rapier3D.clearCollisions` JNI call *consumes* native collision events, so WorldSeam must **not** call it for passive evidence (would steal Sable's effects). Upstream **IPSable** `IplFusedStep` instead coordinates every sublevel system and permits one native step per substep in a shared world. The WorldSeam patch does **not** introduce such a shared world or same-substep force/torque attribution.
- Previous actual disposable flight [39917538603](https://github.com/g2066203208-beep/mc-studio/actions/runs/39917538603) **FAILED visual acceptance**: unedited MP4 loses the ship in below/return frames; its motion is externally forced, and 1750+ exact Nether witness callbacks do not measure force/torque. Its Build/independent/released-mod/GUI/240 stress jobs passed only their limited gates, not the final contract.

**New-head Actions (PENDING at documentation time, never predeclare success):** [Build 39924510249](https://github.com/g2066203208-beep/mc-studio/actions/runs/39924510249), [independent GameTest 39924510221](https://github.com/g2066203208-beep/mc-studio/actions/runs/39924510221), [released Sable/Create/Aeronautics 39924510223](https://github.com/g2066203208-beep/mc-studio/actions/runs/39924510223), [native GUI 39924510245](https://github.com/g2066203208-beep/mc-studio/actions/runs/39924510245), [240 stress 39924510243](https://github.com/g2066203208-beep/mc-studio/actions/runs/39924510243), [actual ship film 39924510262](https://github.com/g2066203208-beep/mc-studio/actions/runs/39924510262).

**Next engineering gates (all OPEN):** (1) released-mod **unattended** ship with native body ID and remote collider contacts, block-edit and ticket-release evidence; (2) full second native ClientLevel/LevelRenderer world pass, sky/light/BE/ordinary entities/Sable sublevel tracking and seam depth/stencil/transparency; (3) ABI/licence-cleared chart-keyed Atlas and **same-substep two-world impulses plus torque on one body**; (4) Create/Aeronautics autonomous powered descent/return, passenger/constraint continuity, uninterrupted raw MP4, 240 real physics crossings and user approval. Do NOT merge.


### 2026-10-23 16:30 CST — released Sable anchor regression added (await CI)

[8146837](https://github.com/g2066203208-beep/mc-studio/commit/81468371a80969948eb3777d4ae065b14fdcee60) extends the existing **real released-Sable assembly GameTest**, not a mock: it requires the actual OW-owned Sable hull to supply the spectator-independent native terrain anchor, validates the derived X/Z chunk center, and rejects an expired 21-tick sample. Emits `WORLDSEAM_REAL_SABLE_UNATTENDED_TERRAIN_ANCHOR` only after the real assembly assertions. **This is not a JNI native-contact/torque pass and cannot replace a genuine no-player flight test.** CI for the latest HEAD must pass before the test can be called verified. Previous intermediate-head runs may be cancelled by GitHub Actions concurrency and must not be counted as successful.


## 2026-10-23 17:18–17:30 CST — native remote entity pass + real film runner unblock (NOT final acceptance)

**All five permanent gates at top remain unchecked. PR #98 stays Draft; no merge.**

### Fresh evidence from previous HEAD `15468b1`
- [Build 39925100089](https://github.com/g2066203208-beep/mc-studio/actions/runs/39925100089): success.
- [Independent JUnit/GameTest 39925100333](https://github.com/g2066203208-beep/mc-studio/actions/runs/39925100333): success.
- [Released Sable 2.0.6 / Create 6.0.10 / Aeronautics 1.3.2 integration 39925099948](https://github.com/g2066203208-beep/mc-studio/actions/runs/39925099948): limited GameTest success.
- [Real GUI 39925100085](https://github.com/g2066203208-beep/mc-studio/actions/runs/39925100085): limited GUI success, artifact 12489732400.
- [240-crossing long run 39925100008](https://github.com/g2066203208-beep/mc-studio/actions/runs/39925100008): limited long-run success, artifact 12489587312.
- **[Real ship film 39925099966](https://github.com/g2066203208-beep/mc-studio/actions/runs/39925099966): FAILED before launching Minecraft**: apt downloaded a 65 MB ffmpeg/ImageMagick dependency chain from a slow Azure Ubuntu mirror and hit exit 124 at the 240-second timeout. No film/log artifact was created by that run. This is a CI infrastructure failure, not physical flight evidence.

### Real code pushed (limited client-native rendering)
- `486d9483`, `647f2200`, `5814bb3b`, `8495e589`, `99f29236`: `SeamRemoteEntities` now attempts a bounded native `EntityRenderDispatcher` pass for **actual entities already present in the foreign ClientLevel**, with correct Y chart translation, logical-plane eligibility, real remote light sampling, independent vertex buffers, source dispatcher restoration even if foreign prepare fails, lifecycle cleanup and pure-Java boundary JUnit tests. This is an actual Java/NeoForge render change, **not** a complete second `LevelRenderer` pass and does not create foreign entity packets or render Sable sublevels.
- `dc05b8e`: disposable GUI E2E places a genuine Nether-owned vanilla armor stand with no gravity and logs `WORLDSEAM_E2E_NATIVE_ENTITY_SCENE`. Zero actual rendered foreign entity frames MUST be treated as **missing remote tracking**, not as a pass; the E2E telemetry is diagnostic until real foreign spawn/move/remove packets are implemented.
- `8cdae4f` and `5f9cfc0`: replace the film job's apt ffmpeg/ImageMagick dependency chain with minimal X11 apt packages and pinned `imageio-ffmpeg==0.6.0`, still recording real X11 MP4 and direct real X11 screenshots, never synthesized or edited gameplay. These are runner repairs, **not** rendering/physics acceptance.

### Specific upstream code and legal boundaries examined
- [Immersive Portals 1.21 `MyGameRenderer.java`](https://github.com/iPortalTeam/ImmersivePortalsMod/blob/1.21/src/main/java/qouteall/imm_ptl/core/render/MyGameRenderer.java): the native second-world render saves and switches `Minecraft.level`, `LevelRenderer`, lightmap, camera, RenderBuffers, fixed section buffers, frustum, projection/modelview, fog/particles and transparency state, and then restores the source. Our new entity pass deliberately changes only the dispatcher and isolated buffers; **the required full context switch is still absent**.
- [Sable native `Rapier3D`](https://github.com/STRELENA1/sable/blob/main/sable_rapier/src/main/java/dev/ryanhcode/sable/physics/impl/rapier/Rapier3D.java) + [`RapierPhysicsPipeline`](https://github.com/STRELENA1/sable/blob/main/sable_rapier/src/main/java/dev/ryanhcode/sable/physics/impl/rapier/RapierPhysicsPipeline.java): stock physics steps its own scene per ServerLevel, and the native callback returns tangent motion/removal, not the complete two-world contact impulses/torques. A witness callback count is not a same-substep dual-contact force measurement.
- [IPSable `IplFusedStep`](https://github.com/r2smith141/IPSable/blob/f5e0471d59407a99c4abd0f084d5a4f2e236f2b8/src/main/java/ipl/sable/atlas/IplFusedStep.java) drives per-dimension pre-phases and one native step per substep. IPSable `LICENSE` at that commit is Apache-2.0, but [Sable `LICENSE.md`](https://github.com/STRELENA1/sable/blob/main/LICENSE.md) is **PolyForm Shield 1.0.0**, not Apache-2.0. No Sable source/native binaries copied or redistributed; ABI/derivative-use permission must be cleared before any native replacement.

### Current-head tests (not silently promoted to pass)
New HEAD after these commits must complete six fresh Actions: Build, independent JUnit/GameTest, released-mod integration, GUI, 240 crossings, real ship MP4. A green remote entity draw counter alone cannot establish entity tracking or continuity; the actual remote armor stand must appear in GUI and survive real packet updates. The film still uses externally imposed test forces; even a visible ship would NOT satisfy Create/Aeronautics autonomous propulsion.

**Next actual architecture work**: (1) native full secondary `ClientLevel/LevelRenderer` compositing with all source state saved/restored, independent lightmap, sky, transparent depth, Sable client sublevel tracking and resources; (2) one ABI-compatible shared Rapier world with chart-keyed terrain, one authoritative body, image colliders and measured simultaneous source+foreign contact impulses/torques in the SAME substep. Only after these can autonomous powered down/up, passengers, mechanics and unedited MP4 be final-validated.


## 2026-10-23 18:19+ CST — actual foreign ServerLevel entity tracking implementation (UNVERIFIED CURRENT HEAD)

**All five permanent acceptance gates at the top remain OPEN. PR #98 remains Draft; never merge without explicit user approval.** Only `mods/worldseam` and its dedicated CI changed.

### Actual source changes, not a mock video

- [d0d8be5](https://github.com/g2066203208-beep/mc-studio/commit/d0d8be54858c42b01b37b864cf11248f4f108d88): bounded native seam-side entity window; rejects invalid coordinates and outside-plane bodies.
- [1c60fed](https://github.com/g2066203208-beep/mc-studio/commit/1c60fed8d46929358f051f2d3ce752de4c1b45c0): bounded 64-entry typed foreign entity packet with original UUID, server entity ID/type, native position and orientation.
- [f7eaaec](https://github.com/g2066203208-beep/mc-studio/commit/f7eaaec6bfe1f872200db22b0c9986e676807174): stream **actual foreign ServerLevel vanilla entity snapshots** within a bounded native seam volume. No server entity or physics body is cloned.
- [6fde10f](https://github.com/g2066203208-beep/mc-studio/commit/6fde10fca2a899f0abd50b8cd0e5fc465a7d6db0): track these snapshots as real client-only `Entity` instances inside the actual second `ClientLevel`, keyed by server ID and UUID, with stale removal and handoff-safe cleanup. These are ordinary client rendering replicas, NOT a second authoritative entity or Rapier body.
- [9027086](https://github.com/g2066203208-beep/mc-studio/commit/9027086a543279cf944cc61a252d9da05b17653f), [9840e1a](https://github.com/g2066203208-beep/mc-studio/commit/9840e1a97436e8baf609ca4ebc46337f434f22ff), [f889a56](https://github.com/g2066203208-beep/mc-studio/commit/f889a56b481348b55e32e74f4bfa128a2bd6d6f1): connect NeoForge payload, independent real-server tracking cadence, client world ownership and release.
- [50fd480](https://github.com/g2066203208-beep/mc-studio/commit/50fd480c15fe1a9b0f75d4177f04163ba2bb5402): 3 new JUnit cases for both logical boundary directions, window edges and nonfinite values.
- [399d0ab](https://github.com/g2066203208-beep/mc-studio/commit/399d0abbe0ac7226c2008366be194889ce8ab5da): repair a **confirmed CI recorder hang**. Raw cancelled [film #39931808628](https://github.com/g2066203208-beep/mc-studio/actions/runs/39931808628) contains real native flight-return telemetry at 09:34:29 UTC but continued until cancellation at 10:03:42 UTC; background FFmpeg inherited ignored SIGINT, so `wait` never finished. Replace with graceful SIGTERM plus 15-second hard cap. Film remains **UNVERIFIED**, even if recorder exits.

### Verified pre-change failure and current-head validation state

- Previous real GUI [#39931808716](https://github.com/g2066203208-beep/mc-studio/actions/runs/39931808716): native Nether armor stand was spawned, but **0** foreign ordinary-entity frames; source world does not automatically stream vanilla entity tracking to the second ClientLevel. This is the specific gap the code addresses, NOT proof it is fixed.
- Downloaded original [cancelled ship-film artifact #12493103755](https://github.com/g2066203208-beep/mc-studio/actions/runs/39931808628/artifacts/12493103755): original real ship UUID `cba54274-a12e-4c5b-999b-491fdfb3e78f`, native body ID 0, actual lower Nether collision callback, return with externally applied test force; **no simultaneous two-chart native contact impulse/torque proof, no Aeronautics-only autonomous flight, no valid continuously visible MP4**.
- Current head [399d0ab](https://github.com/g2066203208-beep/mc-studio/commit/399d0abbe0ac7226c2008366be194889ce8ab5da) CI queued/running at time of this entry: [Build #39938815781](https://github.com/g2066203208-beep/mc-studio/actions/runs/39938815781), [independent JUnit/GameTest #39938815766](https://github.com/g2066203208-beep/mc-studio/actions/runs/39938815766), [released Sable/Create/Aeronautics #39938815744](https://github.com/g2066203208-beep/mc-studio/actions/runs/39938815744), [real GUI #39938815966](https://github.com/g2066203208-beep/mc-studio/actions/runs/39938815966), [240-crossing stress #39938815830](https://github.com/g2066203208-beep/mc-studio/actions/runs/39938815830), [raw ship MP4 #39938815929](https://github.com/g2066203208-beep/mc-studio/actions/runs/39938815929). **None of these is a pass until its actual log/artifact is checked.**
- Limitations: snapshot channel currently covers vanilla entity types only, with ID/UUID/position/rotation/no-gravity; it is not vanilla full metadata/equipment/passenger/interaction replication, Sable client sublevel registration, a complete second LevelRenderer, or an Atlas fused Rapier physics substep. No server-side physics authority is duplicated. Client native render frames must be verified in new GUI logs.

### Next priorities

1. Inspect latest-head compile, JUnit, GUI and raw native logs; fix errors immediately. Confirm foreign native entity UUID and actual >0 rendered frames, plus metadata/lifecycle regression. If GUI still shows zero, inspect native ClientLevel entity storage and tracking order, not pixel heuristics.
2. Replace late GPU chunk/standalone BE/entity draws with full second `LevelRenderer` world-state and GL restoration, sky/light/transparency/depth and Sable sublevel tracking. No complete visual acceptance yet.
3. Rights-cleared Linux JNI Atlas-style single-world physics: chart-keyed terrain, nonmass image colliders, same Rapier body and same substep two-dimension impulse+torque. Stock Sable remote addChunk collision callback is not this architecture.
4. Only then test real Aeronautics powered flight, real passengers/constraints, full uninterrupted unedited GUI MP4 and sustained 240+ performance. User confirmation is mandatory.


## 2026-10-23 19:22 CST — verified foreign entity frames, real chunk-unload lifecycle fix (NOT final acceptance)

**Draft PR #98 stays OPEN and DRAFT. All final visual, physics, propulsion and user-approval gates remain OPEN.** Only `mods/worldseam` changed; no NPC, climbing or Boss edits.

### Actual current-head evidence (pre-fix `679b190`)

- [Real GUI #39938901223](https://github.com/g2066203208-beep/mc-studio/actions/runs/39938901223): original GUI job log recorded a real Nether armor stand UUID `6fbb7a5a-e24e-4ee1-a7ac-46a85baf9ae1`, `WORLDSEAM_E2E_NATIVE_ENTITY_SCENE: realRemoteEntityFrames=158, lastRendered=1, lastEligible=1, rendererErrors=0`. This **does verify real foreign vanilla entity snapshot delivery into the second ClientLevel and a native EntityRenderDispatcher pass**, unlike the previous zero-frame result. It **does not verify full LevelRenderer, equipment/metadata, Sable sublevels, correct visual compositing or continuous aircraft rendering**. Remote BE frames were also 158; chunk draw count alone is not a visual acceptance metric.
- [240 client handoffs #39938901333](https://github.com/g2066203208-beep/mc-studio/actions/runs/39938901333): limited stress passed `crossings=240` and cleanup, but **26 client tick gaps >250 ms**; this is not 240 genuine physics ship crossings or proof of no perceptible stutter.
- [Real released-mod integration #39938901481](https://github.com/g2066203208-beep/mc-studio/actions/runs/39938901481): passed limited GameTest; original log still says `physicsBridgeActive=false` and `NATIVE RAPIER ATLAS BRIDGE NOT IMPLEMENTED`. Sable ship force motion is not Aeronautics-only powered flight.
- [Real aircraft MP4 #39938901331](https://github.com/g2066203208-beep/mc-studio/actions/runs/39938901331): **FAILED**. Raw job says `WORLDSEAM_FILM_FAIL: visible aircraft pixels not verified at all three stages`; `ffprobe: command not found` is a separate recorder diagnostic, not an explanation for missing visible ship. No success video.
- [Build #39938901322](https://github.com/g2066203208-beep/mc-studio/actions/runs/39938901322) and [independent JUnit/GameTest #39938901307](https://github.com/g2066203208-beep/mc-studio/actions/runs/39938901307) passed their limited gates.

### Actual source fix this iteration

- [`b26b4f3`](https://github.com/g2066203208-beep/mc-studio/commit/b26b4f3c5e80ef8a79ba0c8b1e18c2108c46ed73): `SeamRemoteEntitySender.tick` now sends a **real empty snapshot** when the authoritative foreign center chunk is not resident, instead of silently returning and leaving ghost entities in the second ClientLevel. No synchronous chunk load, fake entity, teleport, or new native binary. This fixes one concrete entity-lifecycle path; it does not complete remote entity tracking or dual-world rendering.
- Fresh Actions triggered for this code commit: [Build #39945777595](https://github.com/g2066203208-beep/mc-studio/actions/runs/39945777595), [independent #39945778159](https://github.com/g2066203208-beep/mc-studio/actions/runs/39945778159), [released Sable/Create/Aeronautics #39945777570](https://github.com/g2066203208-beep/mc-studio/actions/runs/39945777570), [GUI #39945777779](https://github.com/g2066203208-beep/mc-studio/actions/runs/39945777779), [240 stress #39945778074](https://github.com/g2066203208-beep/mc-studio/actions/runs/39945778074), [real film #39945777625](https://github.com/g2066203208-beep/mc-studio/actions/runs/39945777625). **PENDING when recorded; no result is predeclared.** A subsequent documentation-only commit may cancel these branch-concurrent runs; verify the newest SHA, not intermediate canceled runs.

### Architecture blockers and next work

- Read upstream Immersive Portals 1.21 `MyGameRenderer.renderWorldNew` and `ClientWorldLoader.withSwitchedWorld`: native second-world render requires scoped `Minecraft.level` and `LevelRenderer`, network listener, independent lightmap, render buffers, camera/frustum, transparency and GL matrices, and strict restoration. Current WorldSeam still draws remote chunk/BE/entity passes in the active renderer. **Full second LevelRenderer composite NOT IMPLEMENTED.**
- Read upstream IPSable `IplFusedStep`: it schedules all dimension pre-phases, then only one armed native Rapier step per substep. Current Sable scenes are dimension-local, and the WorldSeam translation-only foreign terrain uploader does not provide a rights-cleared Atlas shared world, chart-keyed terrain, image colliders, same-body two-chart impulses/torques or native fused step. **Unified dual-dimension physics NOT IMPLEMENTED.**
- Next priority is actual second-world native renderer context/compositing and licence/ABI-cleared shared Rapier physics architecture; then autonomous Aeronautics power, passenger/constraint continuity, actual 240 ship crossings and uninterrupted raw MP4. Do not equate a green GameTest or remote entity frame counter with these final gates.


## 2026-10-23 20:18 CST — native Rapier projected-section collision safety (FINAL GATES OPEN)

**Draft PR #98 remains Draft; NO merge, NO visual/physics acceptance.** Only `mods/worldseam` Java and JUnit changed.

### Fresh baseline at 50fac5b
- [Build #39945895757](https://github.com/g2066203208-beep/mc-studio/actions/runs/39945895757): passed.
- [Independent JUnit/GameTest #39945895714](https://github.com/g2066203208-beep/mc-studio/actions/runs/39945895714): passed limited checks.
- [Released Sable/Create/Aeronautics #39945895838](https://github.com/g2066203208-beep/mc-studio/actions/runs/39945895838): passed limited GameTest; **physicsBridgeActive=false**.
- [Native GUI #39945895814](https://github.com/g2066203208-beep/mc-studio/actions/runs/39945895814): passed limited test, 134 actual foreign entity frames, 165 foreign BE frames; **full second LevelRenderer and visible seamless composite unverified**.
- [240 handoffs #39945895768](https://github.com/g2066203208-beep/mc-studio/actions/runs/39945895768): passed **client** handoffs, **17 >250ms tick gaps, worst 442ms**; NOT 240 physical ship crossings.
- [Actual ship film #39945895730](https://github.com/g2066203208-beep/mc-studio/actions/runs/39945895730): **FAILED** visible ship continuity; ffprobe missing is an additional diagnostic issue, not proof of successful aircraft rendering.

### Actual native collision code fix (not fused Atlas physics)
The Sable `VoxelNeighborhoodState` classification intentionally marks fully supported voxels INTERIOR. The old `fullNativeNeighbor` sampled any *loaded* foreign ServerLevel chunk, including sections never uploaded to the bounded native Rapier scene. At native projection edges this could mark a real collider INTERIOR based on a neighbor that did not exist in Rapier, producing missing native contact faces, especially during asynchronous upload/eviction.

- [f677157](https://github.com/g2066203208-beep/mc-studio/commit/f67715778fb18280a0801ddcd82ba6b5817c9ff4): introduce integer-exact same-native-section support predicate, including negative coordinates.
- [4a46249](https://github.com/g2066203208-beep/mc-studio/commit/4a46249d90aeac10605733d05b5111a4279b1920): foreign native voxel neighborhood culls faces ONLY when neighboring voxel belongs to the same projected native section; cross-section faces remain conservatively exposed regardless of Java chunk residency, eliminating an upload-order/eviction-dependent false INTERIOR.
- [64149d8](https://github.com/g2066203208-beep/mc-studio/commit/64149d89ef5dab2fa58c72d801fce9ee6d843024): JUnit coverage for x/z boundaries, negative section arithmetic and Nether roof Y127/Y128.

This is a **real Java/native terrain correctness change**, but the experimental projection is off in ordinary gameplay, and the native force/torque effect has **NOT** yet been measured. Native Rapier Atlas shared-world, chart-keyed image colliders, fused same-substep dual-dimension impulses/torques, autonomous Aeronautics flight and continuous ship rendering are still **NOT IMPLEMENTED/NOT VERIFIED**. No native Sable source/binary was redistributed.

### Required follow-up
Check fresh Actions on this HEAD (Build, JUnit/GameTest, released-mod integration, real GUI, 240 client handoffs, unedited real aircraft MP4). In addition to the two missing main architectures, instrument actual native body ID and per-chart contact impulse/torque in one substep only through a licensed ABI-safe interface; do not call callback counts or test external forces successful physics. Maintain the Draft PR and all acceptance gates OPEN pending objective real-game results and user approval.

## 2026-10-23 21:25 CST — depth-order repair and latest original CI evidence; final gates OPEN

**PR #98 stays DRAFT and UNMERGED.** This entry does not claim a complete second LevelRenderer or Atlas-style fused Rapier world.

- PR head before this repair: [d23c3aa](https://github.com/g2066203208-beep/mc-studio/commit/d23c3aaa1ed3aca49c039f2a5ecedba335cf2c53). Its complete independent [Build #39952637849](https://github.com/g2066203208-beep/mc-studio/actions/runs/39952637849), [JUnit/GameTest #39952637839](https://github.com/g2066203208-beep/mc-studio/actions/runs/39952637839), [released Sable/Create/Aeronautics #39952637844](https://github.com/g2066203208-beep/mc-studio/actions/runs/39952637844), and [GUI #39952637950](https://github.com/g2066203208-beep/mc-studio/actions/runs/39952637950) succeeded in their limited scopes. Real GUI log: 104 foreign vanilla entity frames, one rendered foreign entity, zero entity renderer errors, 107/107 GPU sections; NOT a seamless-frame pass.
- [240 client handoffs #39952637926](https://github.com/g2066203208-beep/mc-studio/actions/runs/39952637926) succeeded as a *client handoff* stress test, but logged 25 tick gaps >250 ms, worst 508 ms; NOT 240 ship crossings or stutter-free operation.
- [Unedited genuine ship film #39952637833](https://github.com/g2066203208-beep/mc-studio/actions/runs/39952637833) **FAILED** the actual visible-aircraft continuity gate. Downloaded artifact #12501327038 and inspected original real-ship-flight-telemetry.log, real-ship-film-client.log and MP4. Real ship UUID 56bcec75-106b-4427-8165-5200b8e3e7a1, native body ID 0, foreign obsidian JNI callbacks >2000 and actual external-force down/return telemetry. Original log explicitly reports poweredByTestExternalForce=true, aeronauticsOnlyFlightUnverified=true, trueDualChartRapierContactsUnverified=true; these contacts do NOT measure both-chart impulses/torques. ffprobe missing is an additional recorder dependency issue, not the visual failure root cause.
- **Actual Java rendering change:** [cd52cd1](https://github.com/g2066203208-beep/mc-studio/commit/cd52cd1247a2cfadc8712f7000fd3c4afdd26a7b) corrects the *intra-WorldSeam* depth submission order: previously real foreign block entities and ordinary entities were drawn before WorldSeam's own GPU/legacy terrain, allowing subsequent terrain depth writes to obscure the earlier foreign actors. Foreign actor draw now happens after terrain, including no-section and GPU-only early exits. This does **not** solve vanilla translucent passes, separate sky/lightmap, Sable sublevels or the full second LevelRenderer state switch. No new GUI evidence for this fix yet; check CI at the updated HEAD.
- **Blocking architecture:** proper scoped second ClientLevel + LevelRenderer + lightmap/sky/depth/transparent/Sable-sublevel lifecycle, and license/ABI-cleared Atlas-style one Rapier world with chart-keyed terrain, original-body image colliders, and same-substep dual-dimension impulses/torques. Neither is complete. Genuine autonomous Aeronautics down/return and passengers/kinetics continuity remain UNVERIFIED. Never promote PR to ready or merge without objective real-game acceptance and explicit user approval.


## 2026-10-23 22:30 CST — real per-dimension LightTexture scope (NO FINAL ACCEPTANCE)

**Code commit:** [4a9d7ee](https://github.com/g2066203208-beep/mc-studio/commit/4a9d7ee0f97b67aa43fb4b6f35dc006b6352440e). **PR #98 remains DRAFT, unmerged.** Only `mods/worldseam` changed; no NPC, climbing, Boss, third-party source or native binary modified.

### Implemented this round

- Added `SeamRemoteLightmaps` and a narrow GameRenderer lightmap accessor. During the real foreign GPU terrain + target BlockEntity + target ordinary-entity render pass, it creates a genuine `LightTexture` owned by the secondary `ClientLevel`, temporarily switches `Minecraft.level` ONLY for native lightmap update, then restores source `Minecraft.level` before joined-mesh rendering. The remote lightmap is bound to the actual shader texture sampler and the original `GameRenderer.lightTexture`/active sampler is restored by try-with-resources even on early return or renderer failure.
- Releases foreign lightmap native resources on handoff/secondary world disposal, and emits `WORLDSEAM_E2E_REMOTE_LIGHTMAP` bound-frame/error/lifetime counters in genuine GUI logs. This is an actual per-dimension lighting prerequisite, **NOT** the full two-`LevelRenderer` composite.
- **New-code CI:** [Build #39969413822](https://github.com/g2066203208-beep/mc-studio/actions/runs/39969413822) PASSED; [independent JUnit/GameTest #39969413829](https://github.com/g2066203208-beep/mc-studio/actions/runs/39969413829) PASSED; [real released Sable/Create/Aeronautics #39969413859](https://github.com/g2066203208-beep/mc-studio/actions/runs/39969413859) PASSED limited integration. [Real GUI #39969413881](https://github.com/g2066203208-beep/mc-studio/actions/runs/39969413881), [240 client handoffs #39969413981](https://github.com/g2066203208-beep/mc-studio/actions/runs/39969413981), [real unedited aircraft film #39969413841](https://github.com/g2066203208-beep/mc-studio/actions/runs/39969413841) **PENDING** at documentation time; their actual screenshots/logs must be inspected, and none is predeclared a success.

### Pre-change genuine failures verified from original artifacts

- [Real aircraft film #39961485330](https://github.com/g2066203208-beep/mc-studio/actions/runs/39961485330): **FAILED** visible ship at all three stages. Downloaded original artifact #12504628907: UUID `546f5274-6f99-493c-8959-562c1f438c4d`, Rapier native body ID `0`, genuine Nether obsidian callbacks, external-force down/return. Telemetry explicitly `poweredByTestExternalForce=true`, `aeronauticsOnlyFlightUnverified=true`, `trueDualChartRapierContactsUnverified=true`. Callback counts and speed drop are NOT proof of simultaneous two-chart native impulses/torques.
- [GUI #39961485388](https://github.com/g2066203208-beep/mc-studio/actions/runs/39961485388): actual Nether ordinary entity rendered for 82 frames and BE for 87 frames, but visual sky/depth/ship continuity not accepted; inspected raw `visual-near-seam.jpg` and the black/magenta discontinuity remains.
- [240 client handoffs #39961485329](https://github.com/g2066203208-beep/mc-studio/actions/runs/39961485329): 240 **client** handoffs, 21 tick gaps >250ms, worst 937ms. NOT 240 ship crossings and not no-stutter acceptance.

### Upstream implementation and rights/ABI boundary

- Immersive Portals 1.21.1 `MyGameRenderer.switchAndRenderTheWorld` maintains a distinct per-world `LightTexture`, and also swaps/restores `Minecraft.level`, `LevelRenderer`, camera, transparency chain, buffers, frustum, GL model-view/projection, particle world, fog and third-party shader context. WorldSeam now addresses only the **lightmap** part; the rest of this scoped full-render architecture and framebuffer depth/sky composite is still missing. Do not mistake a per-dimension lightmap for a second rendered world.
- IPSable `IplFusedStep` executes all chart pre-substeps, then a single armed Rapier native step; `IplAtlasBodyImages` creates chart-specific image colliders that act on the original body. Its `IplRapierNatives` expressly depends on a custom Windows DLL and is not the released Sable 2.0.6 Linux JNI ABI. IPSable and Immersive Portals repositories contain Apache-2.0 top-level licenses, but stock Sable repository metadata is `NOASSERTION` and the embedded native/third-party provenance must be separately cleared. **No restricted source/binary was copied.** Linux-compatible authorized native extension or upstream API is a hard blocker for complete shared-world same-substep impulse/torque proof.

### Objective acceptance gates (unchanged)

- **Implemented:** secondary real ClientLevel/LevelRenderer ownership and native chunk/BE/ordinary entity pathways; newly scoped foreign lightmap; experimental foreign terrain projection.
- **Tested:** build/JUnit/limited released-mod integration for 4a9d7ee; prior GUI ordinary-entity frames; prior one-body callback/UUID telemetry.
- **Failed:** preceding genuine MP4 visibility, prior >250ms client stutters.
- **Not implemented / not objectively verified:** full simultaneous dual-LevelRenderer sky/lighting/fog/transparency/depth and Sable sublevel tracking; original Rapier body with BOTH charts' native impulse/torque in ONE substep; actual Aeronautics-only powered down/up flight, Create mechanisms and passengers continuous; 240 genuine ship crossings; raw successful unedited MP4; user approval.
- **Next priority:** read current GUI `WORLDSEAM_E2E_REMOTE_LIGHTMAP` and raw screenshots, repair any failure; then implement scoped second full LevelRenderer with correct offscreen compositing; separately obtain a rights-cleared Linux ABI extension for Atlas-style fused native physics. Do not merge or promote Draft PR #98.


### 2026-10-23 22:32 CST — Sable license audit CORRECTION (important)

The earlier GitHub API `license.spdx_id=NOASSERTION` is **NOT** evidence of no license: inspected the actual upstream [Sable LICENSE.md](https://github.com/STRELENA1/sable/blob/main/LICENSE.md), which is **PolyForm Shield License 1.0.0**, with a noncompete restriction on competing products and obligations for distribution/derivatives. The bundled [Rapier third-party LICENSE-RAPIER](https://github.com/STRELENA1/sable/blob/main/sable_rapier/src/main/resources/natives/sable_rapier/LICENSE-RAPIER) is Apache-2.0 but **does not relicense Sable's own Java/native integration**. No Sable source or custom native binary was copied into WorldSeam. An upstream-authorized, ABI-matched Linux integration and license review remain mandatory. IPSable's top-level Apache-2.0 license does not override this Sable restriction.


### 2026-10-23 22:33 CST — actual new-code film result: FAILED

[Film #39969413841](https://github.com/g2066203208-beep/mc-studio/actions/runs/39969413841) **FAILED** `WORLDSEAM_FILM_FAIL: visible aircraft pixels not verified at all three stages`. Downloaded raw artifact #12508686778 (MP4, three JPGs, JVM log and ship telemetry). The native target lightmap was genuinely constructed (`WORLDSEAM_REMOTE_LIGHTMAP_CREATED: dimension=minecraft:the_nether`), but the actual first/below/returned screenshots still have a severe black void and the ship is not continuously visible. Same real Sable UUID `88b8fb78-4eb8-4719-b044-dca10c94b0d4` and native body ID `0` occur in native motion logs, with real foreign Nether obsidian callbacks. The return explicitly still uses **external test force** and has no measured two-chart same-substep contact impulses/torques or Aeronautics-only propulsion. `ffprobe` missing is an additional CI diagnostic, **not** an explanation for the visible failure. The candidate MP4 is not a success deliverable. New-code GUI and 240 client-handoff jobs remain PENDING at this checkpoint.


### 2026-10-23 23:19 CST — real native Rapier terrain unload/reload retry (FINAL GATES OPEN)

**PR #98 remains DRAFT, UNMERGED. This is a native-terrain lifecycle correctness repair, NOT Atlas fused physics or seamless second LevelRenderer.**

**New Java changes, restricted to `mods/worldseam`:**
- [4c2127d](https://github.com/g2066203208-beep/mc-studio/commit/4c2127d6d8ccf5f0a6aa92cf13390c6d59361b4a): previously uploaded Nether native section is removed when its authoritative target chunk unloads; it is now **requeued for asynchronous upload**. Previously it could stay permanently missing until the Sable hull moved to another chunk, even after the real Nether chunk reloaded. No forced loads, fake colliders, cloned rigid bodies or JNI ABI changes.
- [face4926](https://github.com/g2066203208-beep/mc-studio/commit/face4926eb0ce3ef6a306e2e404c8f169feca10c): preserve dirty/reloaded section identities if the 50-slot native upload queue is saturated, rather than silently losing updates.
- [dad01c6](https://github.com/g2066203208-beep/mc-studio/commit/dad01c60ec815073f16e37a20fb31b5a382b2ed9), [1a994ee](https://github.com/g2066203208-beep/mc-studio/commit/1a994ee9a46144e16aef139317ec743c163e77d3), [c6094f2](https://github.com/g2066203208-beep/mc-studio/commit/c6094f258fe2a81efad1c717307d58386960d3d4), [507af74](https://github.com/g2066203208-beep/mc-studio/commit/507af740c7f13205af2e6c86014780ec04c0a88e): extracted actual production queue into bounded `SeamBoundedRetryQueue` and added JUnit cases for unloaded-chunk deferral, queue saturation, duplicate edits, reset, and 240 repeated retries.

**Previous HEAD's newly completed raw CI checks (lightmap change 4a9d7ee):**
- [GUI #39969413881](https://github.com/g2066203208-beep/mc-studio/actions/runs/39969413881) succeeded **limited gate**: raw JVM log `WORLDSEAM_E2E_REMOTE_LIGHTMAP: created=1, boundFrames=150, failures=0`; real remote BE rendered 150 frames and vanilla entity 148 frames. This is **not** a seamless dual-LevelRenderer render acceptance; genuine aircraft film still has a severe black void.
- [240 client handoffs #39969413981](https://github.com/g2066203208-beep/mc-studio/actions/runs/39969413981) succeeded **client-only** test: `crossings=240`, **25 client tick gaps >250 ms**, worst 460 ms; NOT 240 Sable physical ship crossings.
- [Genuine aircraft film #39969413841](https://github.com/g2066203208-beep/mc-studio/actions/runs/39969413841) **FAILED** visible aircraft continuity. The raw film/telemetry uses external test force, with no both-chart same-substep impulse/torque measurement.

**New-code CI at [507af74](https://github.com/g2066203208-beep/mc-studio/commit/507af740c7f13205af2e6c86014780ec04c0a88e)** was still running when checked: [Build #39977824614](https://github.com/g2066203208-beep/mc-studio/actions/runs/39977824614), [independent JUnit/GameTest #39977824411](https://github.com/g2066203208-beep/mc-studio/actions/runs/39977824411), [released Sable/Create/Aeronautics #39977824720](https://github.com/g2066203208-beep/mc-studio/actions/runs/39977824720), [GUI #39977824605](https://github.com/g2066203208-beep/mc-studio/actions/runs/39977824605), [240 client stress #39977824773](https://github.com/g2066203208-beep/mc-studio/actions/runs/39977824773), [genuine aircraft film #39977824426](https://github.com/g2066203208-beep/mc-studio/actions/runs/39977824426). **No pass is predeclared; documentation-only commits may supersede/cancel intermediate runs.**

**Unchanged hard blockers:** scoped second native `ClientLevel/LevelRenderer` full sky, fog, lightmap, transparent/depth, BE/entity/Sable-sublevel tracking and composite; authorized Linux Sable/Rapier ABI for one authoritative body with two chart-specific contact impulses and torques in the same native substep; autonomous Aeronautics/Create powered down/up with passengers and mechanisms; 240 real ship crossings; successful unedited continuous MP4; explicit user acceptance. Sable's PolyForm Shield 1.0.0 and native third-party provenance remain an integration constraint. **NO FINAL ACCEPTANCE.**


### 2026-10-24 00:19 CST — native terrain scheduler starvation correction (NO FINAL ACCEPTANCE)

- Latest verified HEAD before change: `bee016225a061e4390de3d9d6ab567abc6b38864`; PR #98 is still **DRAFT** and unmerged.
- [Build #39977969284](https://github.com/g2066203208-beep/mc-studio/actions/runs/39977969284), [independent GameTest #39977969528](https://github.com/g2066203208-beep/mc-studio/actions/runs/39977969528), [released Sable/Create/Aeronautics #39977969642](https://github.com/g2066203208-beep/mc-studio/actions/runs/39977969642), [GUI #39977969049](https://github.com/g2066203208-beep/mc-studio/actions/runs/39977969049) and [240 client-handoff stress #39977969305](https://github.com/g2066203208-beep/mc-studio/actions/runs/39977969305) concluded SUCCESS as *limited* gates. The [real Sable aircraft MP4 #39977969050](https://github.com/g2066203208-beep/mc-studio/actions/runs/39977969050) **FAILED**; original artifact #12512820764 was inspected. First stage shows a white deck without the full ship; middle shows a partial ship above a large black void; return stage shows no ship. The recorded UUID `1c140292-73d2-440b-b1b5-3e398f50f80a` and native body ID `0` are stable, but movement still uses external test force, and no dual-chart same-substep impulses/torques were measured. The native foreign-block callback count is not proof of fused physics.
- Source audit found an actual collision correctness failure: `SeamBoundedRetryQueue` kept all 50 pending slots occupied by unloaded chunks and only admitted `overflow` after successful uploads. If every pending chunk remained absent, a *loaded, dirty* section in overflow could starve forever, leaving its native Rapier terrain stale. Changed `defer` to fairly rotate the oldest overflow section into the pending slot while retaining the absent chunk for later retry. Added deterministic JUnit coverage for resident edits behind missing chunks and 240 saturated deferrals with no lost section IDs. No synchronous chunk loads, duplicated bodies, forced impulses or restricted native libraries.
- This is a **core native collision-data liveness repair**, not the required Atlas-style shared world/fused native step. New-code Build/GameTest/GUI/film results must be verified from fresh Actions; no pass is assumed.
- **Still FAILED / NOT IMPLEMENTED:** true simultaneous native `ClientLevel/LevelRenderer` sky/fog/transparency/depth/Sable-sublevel composite; one authoritative Rapier body receiving separately attributed Overworld+Nether impulses and torques in the same native substep; authorized compatible Linux JNI ABI; Aeronautics-only autonomous powered down/up flight, mechanical/passenger continuity, 240 real ship crossings, seamless unedited MP4 and user approval. PR remains Draft and MUST NOT be merged.


### 2026-10-24 02:27 CST — real foreign-actor native world/renderer scope (FINAL GATES OPEN)

**Draft PR #98 stays DRAFT, UNMERGED. No user acceptance.** This round implements a real, narrowly scoped foreign-world state switch for **existing target-owned BlockEntity and ordinary entity draws**; it does not render the second sky or solve dual-chart Rapier.

- Actual Java/mixin changes: `SeamForeignActorWorldScope` now temporarily installs the real prewarmed target `ClientLevel` and its OWN `LevelRenderer`, switches the native block-entity dispatcher and the ParticleEngine's world field without calling `ParticleEngine.setLevel()` (which clears particles), prepares the native entity renderer, and restores the original active world, renderer, BE dispatcher, particle level and entity dispatcher in nested `finally` blocks. It retains the source camera/projection intentionally because foreign actors are translated into source coordinates. No synthetic actor, clone body, teleport, native binary, or third-party code added.
- `SeamNativeGeometryClient.renderForeignActors` uses this context around real target BE/entity draws, and `SeamRemoteEntities` accepts an already-active scoped target context. A GUI E2E invariant records `WORLDSEAM_E2E_FOREIGN_ACTOR_CONTEXT` and fails its test if no scopes ran, enter/restore counts differ, a restoration failure occurred or the context remained active. This is **not** a completed independent offscreen LevelRenderer render: sky, fog, translucent depth, source/target framebuffers, Sable sublevels and full shader state still need implementation.
- Previous exact-code [Build #39995131074](https://github.com/g2066203208-beep/mc-studio/actions/runs/39995131074), [independent tests #39995131131](https://github.com/g2066203208-beep/mc-studio/actions/runs/39995131131), [released mods #39995131041](https://github.com/g2066203208-beep/mc-studio/actions/runs/39995131041), and [GUI #39995131114](https://github.com/g2066203208-beep/mc-studio/actions/runs/39995131114) succeeded as LIMITED gates. The [240-client-handoff stress #39995131080](https://github.com/g2066203208-beep/mc-studio/actions/runs/39995131080) succeeded, **34 tick gaps >250 ms, worst 467 ms**, NOT 240 physical ship crossings. The [unedited Sable aircraft film #39995131110](https://github.com/g2066203208-beep/mc-studio/actions/runs/39995131110) **FAILED** real visible-aircraft continuity, despite genuine native hull UUID/body ID and Nether witness callbacks. It used external test force, not autonomous Aeronautics propulsion; no measured two-chart same-substep contact impulse/torque.
- Earlier deferred-chunk-carving commits `bc37f75` and `6dd295a` had **cancelled** integration/GUI/film CI runs (their Build passed); later `d181eda` introduced a genuine film-visibility gate; `4ae973b` had successful limited Build/independent test/released-mod/GUI/client-stress runs, while film was cancelled. Neither historical cancellation nor GameTest success establishes final physics.
- This round's new context code and GUI invariant **have NOT yet been independently verified by completed CI** at this documentation checkpoint. Read fresh Actions logs and actual screenshots before claiming any pass; if a Mixin/accessor or renderer crash appears, repair from its original JVM stack trace.
- Next priority remains a full offscreen second `LevelRenderer` sky/fog/depth/transparency composite with real Sable sublevel tracking, plus a separately licensed Linux Sable JNI/Atlas-compatible **one-body, both-chart, one-substep** Rapier integration. The current scope is a prerequisite, not a substitute. Do not merge or mark ready.


### 2026-10-24 03:26 CST — completed 240-client stress and render-path blocker (NO ARCHITECTURE BREAKTHROUGH)

**Fresh exact-code Actions read:** [long-run #40003993139](https://github.com/g2066203208-beep/mc-studio/actions/runs/40003993139), at `b029f7214da7f11a39cde09863ab9d526c176dd7`, has completed SUCCESS as a **client handoff-only** gate. Raw workflow job #120587274817 ends with `WORLDSEAM_STRESS_PASS: crossings=240, clientHandoffs=240, elapsedWallMs=459937, peakNativeChunks=609, peakDedicatedRendererOwners=2, peakHeapUsedMiB=3517, clientTickGapsOver250ms=27, clientTickGapsOver1000ms=0, worstClientTickGapMs=528`. Cleanup reports `enabled=false remoteBuffers=false proxyBuffers=false completed=240`. The 27 >250ms stalls and 528ms worst gap **fail perceptually seamless acceptance**; these 240 crossings are **not** 240 independently powered Sable aircraft crossings. [Build #40003993079](https://github.com/g2066203208-beep/mc-studio/actions/runs/40003993079), [independent tests #40003993180](https://github.com/g2066203208-beep/mc-studio/actions/runs/40003993180), [released-mod integration #40003993148](https://github.com/g2066203208-beep/mc-studio/actions/runs/40003993148), [GUI #40003993089](https://github.com/g2066203208-beep/mc-studio/actions/runs/40003993089) all concluded SUCCESS only as limited gates. [Real aircraft film #40003993229](https://github.com/g2066203208-beep/mc-studio/actions/runs/40003993229) concluded FAILURE.

**Direct source audit (not a pass):** `SeamNativeGeometryClient.render` hooks exclusively at `RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES`; `renderForeignActors` switches the second real ClientLevel/LevelRenderer only around BE/entity calls, never calls the second renderer's `renderSky` or `renderLevel`, and does not create an independent color/depth target. This explains a concrete architectural absence consistent with the black void in the genuine aircraft screenshot; it does **not** establish that the missing sky pass alone is the only cause. A correct repair must use an independently rendered target world, sky/fog/depth/translucency and actual Sable sublevel state, then composite by a geometrically correct portal/depth mask with strict state restoration. Filling black pixels or drawing an invented colored plane is NOT acceptance.

**Physics audit remains blocked:** WorldSeam's current foreign voxel projection and native collision callbacks do not expose per-chart, per-substep impulses/torques applied to the same authoritative Rapier body; published Sable Linux JNI ABI and third-party licensing must be verified before implementing a fused Atlas-style solver. The film explicitly uses an external test force. No source/native binaries with incompatible permissions may be imported. No new production Java/physics change was made at this checkpoint; the only new verified result is completed stress evidence and this explicit source-level blocker record. PR remains Draft/unmerged, with no user acceptance.


### 2026-10-24 04:20 CST — exact-HEAD CI completed, genuine flight still FAILED (NO PRODUCTION ARCHITECTURE CHANGE)

At PR #98 exact head `e12455d3037fd681d3487abc7aa1d1b101f1bb95`, all six Actions are complete. [Build #40011655929](https://github.com/g2066203208-beep/mc-studio/actions/runs/40011655929), [independent JUnit/GameTest #40011655482](https://github.com/g2066203208-beep/mc-studio/actions/runs/40011655482), [released Sable/Create/Aeronautics #40011655698](https://github.com/g2066203208-beep/mc-studio/actions/runs/40011655698), [GUI #40011655799](https://github.com/g2066203208-beep/mc-studio/actions/runs/40011655799) and [240-client-handoff stress #40011655870](https://github.com/g2066203208-beep/mc-studio/actions/runs/40011655870) concluded SUCCESS as **limited** gates. [Genuine Sable aircraft film #40011655457](https://github.com/g2066203208-beep/mc-studio/actions/runs/40011655457) concluded **FAILURE**.

Original film artifact #12526238424 was downloaded and inspected, including the actual below-seam screenshot and native flight telemetry. The screenshot still has a very large black void, partial/incomplete hull view and missing complete seamless second world; no success MP4 may be advertised. Film telemetry reports genuine UUID `d067175e-4849-4efb-a2fa-92f8e41fdabc`, minY -80.5038 and return -61.2825, but **`poweredByTestExternalForce=true`**, **`aeronauticsOnlyFlightUnverified=true`**, **`trueDualChartRapierContactsUnverified=true`**. Native foreign terrain contact callback counts cannot prove two independently attributed chart contacts/torques in one Rapier substep. The film job also reports `ffprobe: command not found`; that diagnostic is separate from the visible black-void failure.

GUI job raw JVM log #120613281443 confirms foreign actor context entered/restored 194/194, remote lightmap bound 194 frames with zero errors, real remote block entities drawn 187 frames and ordinary entities 161 frames. These **do not** constitute a complete target `LevelRenderer` sky/fog/depth/transparency/Sable-sublevel pass. Real released-mod job #120613280616 explicitly logs `physicsBridgeActive=false` and `NATIVE RAPIER ATLAS BRIDGE NOT IMPLEMENTED`.

240-client-handoff stress raw job #120613281931 reports `crossings=240`, `clientHandoffs=240`, 8 client tick gaps >250ms, worst 434ms, peakHeapUsedMiB 1860, peakDedicatedRendererOwners 2. This is **not 240 actual aircraft crossings** and fails perceptually seamless acceptance. Improvement versus previous 27 gaps / 528ms is observed but is not attributed to a code change at this documentation-only head.

Direct production source audit reconfirms `SeamNativeGeometryClient.render` is gated to `AFTER_BLOCK_ENTITIES`, with the second `LevelRenderer` installed only around BE/entity draws; there is no independent target sky/fog/transparent/depth offscreen render or geometric compositing. Native Atlas-style shared Rapier world / one authoritative body with both chart-specific contacts in one fused step remains unimplemented, and Linux JNI/PolyForm Shield/third-party licensing compatibility remains unresolved. **No production Java, native code, or CI workflow was changed at this checkpoint.** Do not merge or mark ready without actual successful physical/rendering acceptance and explicit user confirmation.


### 2026-10-24 05:22 CST — dedicated native render-buffer state isolation (NEW CODE; CI PENDING)

**Draft PR #98 remains OPEN, DRAFT and UNMERGED. No final acceptance.** Latest exact pre-change HEAD `5410448b5b36cf4bdb798ddde0c5957ce890c328` was checked against the *actual* latest six CI runs:

- [Build #40018613215](https://github.com/g2066203208-beep/mc-studio/actions/runs/40018613215) SUCCESS; [independent tests #40018613208](https://github.com/g2066203208-beep/mc-studio/actions/runs/40018613208) SUCCESS; [released Sable/Create/Aeronautics #40018613329](https://github.com/g2066203208-beep/mc-studio/actions/runs/40018613329) SUCCESS (LIMITED); [real GUI #40018615069](https://github.com/g2066203208-beep/mc-studio/actions/runs/40018615069) SUCCESS (LIMITED).
- [240-client-handoff stress #40018613427](https://github.com/g2066203208-beep/mc-studio/actions/runs/40018613427) SUCCESS only as a client test: original job #120637040973 logs `crossings=240, clientHandoffs=240, clientTickGapsOver250ms=44, worstClientTickGapMs=548`. This is **NOT** 240 genuine powered aircraft crossings, and the 548ms stall fails perceptual continuity.
- [Real aircraft film #40018613219](https://github.com/g2066203208-beep/mc-studio/actions/runs/40018613219) **FAILURE**, original job #120637040403 `WORLDSEAM_FILM_FAIL: visible aircraft pixels not verified at all three stages`. Artifact #12528809477 preserves raw MP4/JVM/telemetry. The separate `ffprobe: command not found` diagnostic does not explain away the actual failure. No success video may be advertised.
- Original released-mod GameTest job #120637040831 explicitly logs `physicsBridgeActive=false` and `NATIVE RAPIER ATLAS BRIDGE NOT IMPLEMENTED`. It verifies a real Sable ship moved with native force; it does **not** verify both-chart impulses/torques in one substep or Aeronautics autonomous powered flight.

**Actual new Java/Mixin implementation, limited to `mods/worldseam`:**
- [16420ab](https://github.com/g2066203208-beep/mc-studio/commit/16420abdff7f26130da9b081dc097d17058d9113) and [1f7d9a0](https://github.com/g2066203208-beep/mc-studio/commit/1f7d9a0a3e81de6998f738356b088329b0d387f1) expose the native Minecraft and GameRenderer RenderBuffers owners through scoped Mixins.
- [328763e](https://github.com/g2066203208-beep/mc-studio/commit/328763ed6454ee10b830ee9a31030eaf9dc35fc4) resolves the *real* secondary LevelRenderer's existing dedicated RenderBuffers rather than constructing or cloning another world/renderer.
- [0422664](https://github.com/g2066203208-beep/mc-studio/commit/0422664d50b64f9972abbb8d2b554973a9f16de6) and [0c818fc](https://github.com/g2066203208-beep/mc-studio/commit/0c818fc1a3d3685f2b6128e8c7303f270fd46cd2) switch BOTH Minecraft.renderBuffers and GameRenderer.renderBuffers to the secondary LevelRenderer's owned native buffers during real remote BE/entity drawing, assert the target identity, and restore both original identities in nested `finally` blocks even on a mod renderer exception. This is a real render-state ownership prerequisite drawn from Immersive Portals' multiworld context handling; it is **not** a full second-world render, sky/fog/depth/transparent composite, or a physics bridge.

**Fresh Build/JUnit/real released-mod/GUI/240-client-stress/film CI on the final document HEAD must complete before any new-code pass is claimed.** If the Mixin accessors or actual GUI render fail, repair from original JVM logs. Do not merge or mark ready. Next priorities: independent full target LevelRenderer offscreen pass with source/target camera/projection, sky/fog/depth/transparency/Sable-sublevel handling and strict GL restoration; legally compatible Linux Sable/Rapier shared-world native API with one authoritative body, two chart-keyed terrain contacts and impulses/torques in one fused step. Both are **UNIMPLEMENTED**.
