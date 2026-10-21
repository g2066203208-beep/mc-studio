# WorldSeam — native renderer / mod compatibility research & performance contract

2026-10-21, MC 1.21.1 NeoForge. This is an engineering design/acceptance contract, **not** a claim that the compatibility lanes already pass.

## Goals and non-goals

- The renderer may see both `ClientLevel`s concurrently and should use native chunk meshes, block entities, entities, light, weather and sky instead of thousands of `renderSingleBlock` proxy draws per frame.
- Keep the original vanilla dimension ownership: separate `ServerLevel`s and dimension-specific registries, tick state and sky effects. **Do not globally concatenate** dimension logical heights or rewrite vanilla `DimensionType` / registries; mod compatibility depends on them.
- The horizontal connection plane is a view transform and gameplay routing layer, not a dimension global Y offset. The End as a bottom layer retains its original void/ocean/physics configuration; WorldSeam cannot override Sable's End buoyancy or sea behavior by changing this stack.
- Save migration: change `SeamStack.defaults()` for **new** worlds only. Old `SavedData` stacks are serialized explicitly and must load unchanged. CI's earlier End-above-Overworld regression intentionally pins a historical stack, separate from the new default GUI screenshot.

## Verified architectural source references

1. Immersive Portals implementation details (Apache 2.0): https://github.com/iPortalTeam/ImmersivePortalsMod/wiki/Implementation-Details — uses rendering-context stack, secondary-world view, front clipping, stencil or FBO render, frustum pruning, split-entity plane clipping; snapshot renderer state restored on return.
2. Immersive Portals 1.21 implementation: https://github.com/iPortalTeam/ImmersivePortalsMod/blob/1.21/src/main/java/qouteall/imm_ptl/core/render/MyGameRenderer.java — dedicated render buffers plus secondary view renderer; source was archived/read-only in 2026. Ideas, **not copied code**.
3. NeoForge Immersive Portals CE target matrix: https://github.com/UpperMoon0/ImmersivePortals — its author reports pinned Sodium, Iris, Embeddium, NeOculus and Sable combinations, negative controls, GPU phase screenshots and shaders. This does **not** imply WorldSeam inherits the compatibility.
4. Voxy-specific, unofficial MC 1.21.1 NeoForge fork: https://github.com/Gm537/neo-voxy — custom LOD chunk database and Sodium/Iris pipelines. Another independent version: https://github.com/BookerCatch/voxy-neoforge. **No renderer public API is assumed**, and the real selected fork/build must be known.
5. Sable: https://github.com/STRELENA1/sable — sublevels contain chunks, entities and block entities, and the project explicitly warns about intrusive mixin conflicts. Do not migrate a Sable sublevel via vanilla `Entity.changeDimension()`.

Review upstream licenses before adapting implementation; licensing/port compatibility cannot be inferred from the research links.

## Rendering architecture (staged, not yet built)

### Native view context

For each visible seam, maintain `WorldViewContext(source, destination, sourcePlane, targetPlane, cameraTransform, depth, renderBackend)`. The destination `ClientLevel`, dedicated `LevelRenderer`, entity dispatcher context, lightmap, particle engine and weather context must be scoped and **always** restored with a stack/try-finally, including on shader reload or crash. Never mutate `mc.level` simply to draw a distant snapshot.

### Actual seam compositing

The destination is drawn through a finite/plane-clipped region with stencil mask or isolated FBO and destination depth restored into the source depth semantics. Real terrain uses native section meshes; transparent water/particles and cutout layers must render with correct pipeline sorting. Sable crossed sublevels require a single identity with a clipped render on both sides. No duplicate ship physics.

### Renderer backends

| Lane | Planned implementation | Acceptance |
|---|---|---|
| Vanilla | Two `LevelRenderer`s and independent `SectionRenderDispatcher`; projection/stencil/FBO, correct native sky and section meshes | Nether roof, End bottom, water, night, weather, entity, block entity |
| Sodium | Isolated per-dimension chunk mesh context and Sodium API integration (version pinned) | source and target unchanged, no empty chunks, correct cutout/translucent, render-distance changes |
| Iris + Sodium | Dimension-specific shader pipelines; separate framebuffer and restored program/uniform/light/depth state; no shader transform by guessing uniform names | active verified shader pack, hot reload, switching pack OFF/ON, resize, shader sky and shadows |
| Voxy | Independent dimension-keyed LOD cache, camera transform, view frustum, visibility occlusion and correct far-layer composite | truly distant terrain in **each** world, no source-target cache pollution or retained chunk leaks |
| Sable/Aeronautics | version-pinned sublevel transform, authoritative body + contact proxies, stable passenger/constraint network | two sides collide correctly throughout ship crossing, no physics duplication |

A renderer adapter must *fail safely* (warn and fall back to the known near-field renderer) if its exact tested version is absent. Do not force-load arbitrary JARs, assume Fabric compatibility or claim generic mod support from one optional library check.

## Performance acceptance

- The current fallback is 25 nearby destination chunks, *not* unbounded recursive world rendering; game field logs and screenshot artifacts carry cache/ticket counts separately from visual quality.
- This iteration introduces hysteresis for selecting destination views: enter within 64 blocks, keep until 80, and switch to a new target only when it is >12 blocks closer. Stops thrashing when Nether Y0 and Y128 connections are near the player. Stale ticket/session is removed when leaving range or disabling.
- Future native renderer's target is **bounded per-frame CPU and GPU budgets**, not a fixed arbitrary count of 3,500 `renderSingleBlock` models. Draw calls should be batched native meshes with frustum, horizon and seam-plane clipping.
- Profiles for each lane: world load/unload time, GPU render P50/P95/P99, CPU chunk rebuild P95, FPS, retained GPU/CPU memory, network packets/second, chunk tickets, Voxy dimension LOD resident pages.
- Test with 1 / 2 / 4 concurrent players near different seams and 4/8/12/16 vanilla render distances. Verify no unbounded tickets or repeated full 5x5 retransmit at rest, after dimension crossings or after shader reload.
- Test source and destination height mismatches and worlds containing modded dimensions, daylight and night, explosion, Sable physics and Nether-End bottom adjacency separately.

## Gameplay improvement ideas gated by actual physics

- Natural layer exploration: descend from Overworld caves to Nether, then through the Nether floor into the End while keeping the End's existing float/sea behavior. No fake end ocean or physics override.
- Build suspended cross-border bases, drops, item transport, explosives and two-way redstone/machines only after cross-world collisions / tick bridges are implemented and GameTest/GUI validated.
- Keep old saves' layer ordering: **no invisible migrations**, no auto breaking existing bedrock outside explicitly enabled future terrain generation, no destructive default.

## Known limits at 0.9.4-dev

Full native two-`LevelRenderer` same-frame compositing, Iris/Sodium/Voxy integration, cross-world fluid/entity drawing, Aeronautics/Sable rigid-body contact and complete mechanical stress transfer are **not implemented** in this patch. It changes new-world topology and improves remote prewarm stability. Existing WorldSeam v0.9.3 cross-world explosion prototype is separate and opt-in. Do not promote any mod compatibility status without version-pinned graphical reproducibility.
