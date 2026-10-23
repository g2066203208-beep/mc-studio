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
