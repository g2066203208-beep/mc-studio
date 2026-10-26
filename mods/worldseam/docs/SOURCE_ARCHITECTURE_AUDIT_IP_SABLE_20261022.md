# WorldSeam / Minecraft 1.21.1 / Sable / Immersive Portals：跨维飞船源码核验（2026-10-22）

**结论：WorldSeam 当前没有实现目标。** 任何“已物理化跨维航空学飞船”的宣称都不成立。这里严格区分编译、原生刚体移动、静态跨界、真实跨界、双维接触、客户端录像等不同验收层级。此文是源码审计 / 实施门禁，不是模拟验收记录。

## 1. 固定的源码与许可边界

| 源码 / 版本 | 固定审核对象 | 关键文件 / 实际核验范围 | 许可和使用限制 |
|---|---|---|---|
| [Sable 2.x source](https://github.com/STRELENA1/sable/tree/bf9713a6430914545113572d1535bfed531a889f) | `bf9713a` | `common/.../sublevel/system/SubLevelPhysicsSystem.java`; `common/.../sublevel/ServerSubLevel.java`; `sable_rapier/.../RapierPhysicsPipeline.java`, `Rapier3D.java`; `SubLevelAssemblyHelper.java` | upstream repository uses restrictive/non-permissive licensing; **do not bundle/copy source or binaries** without sufficient permission. Read API only. |
| [IPSable 0.5 atlas](https://github.com/r2smith141/IPSable/tree/f5e0471d59407a99c4abd0f084d5a4f2e236f2b8) | `f5e0471` | `docs/atlas_portal_physics_spec.md`, `atlas_portal_physics_audit_results.md`; `IplFusedStep`, `IplAtlasStraddleSession`, `IplAtlasBodyImages`, native `scene.rs`, `dispatcher.rs`, `ipl_ext.rs` | repo Apache-2.0 metadata; **vendored Sable/Rapier components may carry different licenses**. Code cannot be wholesale imported; audit origins and NOTICE per source. |
| [Immersive Portals NeoForge CE](https://github.com/UpperMoon0/ImmersivePortals/tree/ad2e7ee5d73e4ff78bd0befade4f797265e65939) | `ad2e7ee` | `SableDimensionStackCompat.java`, physics-substep mixin, start-tracking packet mixin, client renderer ownership, rider handoff | Apache-2.0 repository, review nested attribution and licenses. |
| [IPSable derivative](https://github.com/GaMiR9195/immersiveportals-x-sable/tree/e2ae2be355ac1353b1d4cc94b789a069de7caac3) | `e2ae2be` | same atlas code family, more client-render fixes | license metadata NOASSERTION: inspection only until separately verified. |
| [Immersive Portals: Aeronautics](https://www.curseforge.com/minecraft/mc-mods/immersive-portals-aeronautics) | 1.21.1 NeoForge, 2.4.x and 2.5.x | public release notes discuss joints, seats, cross-portal shafts and dimension stack | **PolyForm Shield** own code; no source transplant, redistribution/fork as competitor without permission. |
| [Vanilla/NeoForge 1.21.1 GameTest API](https://lexxie.dev/neoforge/1.21.1/net/minecraft/gametest/framework/GameTestHelper.html) | MC 1.21.1 / Java 21 | `ServerLevel`, `MinecraftServer.getAllLevels`, `LevelHeightAccessor`, `GameTestHelper.succeedWhen`; mapped public APIs | Minecraft source copyright is Mojang's; **do not commit decompiled Mojang source**. Work from NeoForge mapped APIs. |

Current tested released external jars: Sable 2.0.6, Create 6.0.10, Aeronautics 1.3.2. These are **not** the same as the referenced Immersive Portals: Aeronautics product. Its portal compatibility cannot be presumed installed or present in our native solver.

## 2. What the genuine source actually does

### 2.1 Vanilla Minecraft dimensions are NOT stacked in one Level

- Each `ServerLevel` has independent height range, `ChunkSource`, block states, fluids, entities, world border, storage and game ticks. WorldSeam must map *logical* dimension planes to **native world coordinates**, not replace/destroy worlds.
- Default WorldSeam joining below Overworld uses OW logical bottom `Y=-64` and Nether logical top `Y=128`; continuous body coordinate mapping: `Y_OW=Y_Nether-192`. A ship at `Y_OW=-65` must be represented in Nether collision chart at `Y_Nether=127`. Vanilla Nether may physically store unwanted `Y>=128` blocks; those are **hidden logical territory**, never collision data for the joined Nether top.
- Default logical Nether top **is not necessarily Nether's native build ceiling**. A patched vanilla chunk or `/fill air` cannot solve cross-Level entity ownership, chunk hydration, Sable physics, render owner, sky, entity/seat packets.
- MC dimension tick vs Sable substeps are distinct; an observer invoked once per 10 ticks (`SeamSableObserver.tick`) is much too late for a fast body to cross an entire seam while the solver runs 2+ substeps per tick. A real physics bridge must operate at Sable **physics-substep** granularity.

### 2.2 Sable: one dedicated Rapier scene *per ServerLevel* by default

Upstream `SubLevelPhysicsSystem` constructs `PhysicsPipelineProvider.INSTANCE.createPipeline(level)` for each server level; `tickPipelinePhysics` runs per-level: `prePhysicsTicks -> per-substep prePhysicsTick/applyQueuedForces -> pipeline.physicsTick -> updatePose/postPhase`. See upstream:
- [SubLevelPhysicsSystem](https://github.com/STRELENA1/sable/blob/bf9713a6430914545113572d1535bfed531a889f/common/src/main/java/dev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem.java)
- [RapierPhysicsPipeline](https://github.com/STRELENA1/sable/blob/bf9713a6430914545113572d1535bfed531a889f/sable_rapier/src/main/java/dev/ryanhcode/sable/physics/impl/rapier/RapierPhysicsPipeline.java).

`RapierPhysicsPipeline.physicsTick(timeStep)` directly calls native `Rapier3D.step(this.scene.handle(), timeStep)`. Native `addChunk(scene,x,y,z,int[4096],global,id)` gives each scene *its own* terrain; Java `VoxelShape` queries in another level or `addRegionTicket` do **not** make any foreign terrain participate in that Sable scene's native solver. Neither `SeamShipRemoteContactProbe` nor `SeamJoinedServerBlockView` can turn on physical contacts on their own.

A real Sable `ServerSubLevel` is assembled by `SubLevelAssemblyHelper.assembleBlocks` with a UUID, plot, mass tracker and native runtime ID. `SubLevel.boundingBox()` initially can be all zero until `updateBoundingBox()`; source **does not refresh bounds inside assembleBlocks**. Use readback/pose plus a refreshed real AABB, not `Y=0` as phantom position.

`RapierPhysicsPipeline.applyLinearAndAngularImpulse` ultimately calls `Rapier3D.applyForceAndTorque`: units are **Newtons** (despite method name). Test force must scale by real `getMassTracker().getMass()` (`F=m a`) and needs observed world-space displacement.

### 2.3 Two real architectures in existing Immersive Portals integrations — do not confuse them

**Option A, transactional native body migration**: `UpperMoon0/ImmersivePortals` hooks Sable `SubLevelPhysicsSystem.updateAllPoses` after **every substep**, detects portal plane crossing based on historical/present actual hull/COM, and `SableDimensionStackCompat.migrateSubLevel` stages destination serialized blocks + rider entities + velocity + joints, then retires source after verification and commits player handoff. The source implementation notably invokes destination allocation and eventually `sourceContainer.removeSubLevel`; it does **not** mean there is exactly one continuous native Rapier body handle accepting BOTH world's contacts at mid-straddle. See:
- [Sable physics-substep hook](https://github.com/UpperMoon0/ImmersivePortals/blob/ad2e7ee5d73e4ff78bd0befade4f797265e65939/src/main/java/qouteall/imm_ptl/core/compat/mixin/sable/MixinSubLevelPhysicsSystem_SableDimensionStackCompat.java)
- [Sable dimension-stack transaction](https://github.com/UpperMoon0/ImmersivePortals/blob/ad2e7ee5d73e4ff78bd0befade4f797265e65939/src/main/java/qouteall/imm_ptl/core/compat/sable/SableDimensionStackCompat.java).
This is an excellent gameplay/network/seat continuity reference but **not** the requested strict no-duplicate/no-body-handoff semantics.

**Option B, atlas unified solver (required direction for strict goal)**: IPSable v0.5's `atlas_portal_physics_spec.md` and shipped source aim for one Rapier `RigidBodySet + IslandManager + solver`, **chart-tagged terrain maps** (`ChartId -> ChunkMap`) and **image COLLIDERS (not additional rigid bodies)** projected into other charts on a single authority body. Near/far half-space contact clipping and chart-specific contact manifold correction feed far-side forces into the *same* rigid body **during the same native substep**:
- [Atlas architecture and math](https://github.com/r2smith141/IPSable/blob/f5e0471d59407a99c4abd0f084d5a4f2e236f2b8/docs/atlas_portal_physics_spec.md)
- [native shared WorldCore/chart storage](https://github.com/r2smith141/IPSable/blob/f5e0471d59407a99c4abd0f084d5a4f2e236f2b8/natives/rapier/src/scene.rs)
- [native chart terrain selection](https://github.com/r2smith141/IPSable/blob/f5e0471d59407a99c4abd0f084d5a4f2e236f2b8/natives/rapier/src/dispatcher.rs)
- [real-body image colliders, clipping and lifecycle](https://github.com/r2smith141/IPSable/blob/f5e0471d59407a99c4abd0f084d5a4f2e236f2b8/src/main/java/ipl/sable/transit/IplAtlasStraddleSession.java)
- [fused Sable physics step](https://github.com/r2smith141/IPSable/blob/f5e0471d59407a99c4abd0f084d5a4f2e236f2b8/src/main/java/ipl/sable/atlas/IplFusedStep.java)
- [native JNI extension signatures](https://github.com/r2smith141/IPSable/blob/f5e0471d59407a99c4abd0f084d5a4f2e236f2b8/src/main/java/ipl/sable/natives/IplRapierNatives.java).

**Translation-only stacked dimensions are the simpler Tier-1 physics case**: mapping only translates coordinates, so `R=I` and the image contact lever arm must be built against `p-(COM+t)` instead of `p-COM`. Image collider contact force maps back to the one source rigid body's original center. Correct contact position/torque and no double-counted penetration are mandatory. A merely transformed `AABB` or `VoxelShape` count has zero native solver effect.

**Critical native OS caveat**: inspected IPSable `IplNativesOverrideMixin` loads its bundled **Windows x86_64 patched DLL only**, otherwise falls back to stock Sable natives; while its Java `IplRapierNatives` symbols require that patched binary. Our real GitHub Actions are Linux. Merely copying/executing the Java adapter on Linux will **not** provide working Atlas native symbols or double-world physics; an independently rights-cleared **Linux-native ABI-matched** implementation and verification is required. Link:
[custom native loader](https://github.com/r2smith141/IPSable/blob/f5e0471d59407a99c4abd0f084d5a4f2e236f2b8/src/main/java/ipl/sable/mixin/IplNativesOverrideMixin.java).

### 2.4 Render, tracking and rider continuity are separate from rigid-body physics

- `ClientboundStartTrackingSubLevelPacket` and Sable snapshot interpolation need a destination client sublevel before movement/snapshot packets, stable UUID and dimension-keyed routing; merely teleporting spectator camera in another level causes `non-existent sub-level` packet races.
- [Sable tracking/pose interpolation graft](https://github.com/UpperMoon0/ImmersivePortals/blob/ad2e7ee5d73e4ff78bd0befade4f797265e65939/src/main/java/qouteall/imm_ptl/core/compat/mixin/sable/MixinClientboundStartTrackingSubLevelPacket_SablePortalCompat.java).
- [Sable server-first rider rotation handshake](https://github.com/UpperMoon0/ImmersivePortals/blob/ad2e7ee5d73e4ff78bd0befade4f797265e65939/src/main/java/qouteall/imm_ptl/core/compat/mixin/sable/MixinSableDimensionStackCompat_ServerFirstRotation.java).
- [Sable cross-dimension packet redirect](https://github.com/UpperMoon0/ImmersivePortals/blob/ad2e7ee5d73e4ff78bd0befade4f797265e65939/src/main/java/qouteall/imm_ptl/core/compat/mixin/sable/MixinServerSubLevel_SablePortalCompat.java).
- [Render owner cleanup](https://github.com/UpperMoon0/ImmersivePortals/blob/ad2e7ee5d73e4ff78bd0befade4f797265e65939/src/main/java/qouteall/imm_ptl/core/compat/mixin/sable/MixinClientWorldLoader_SableRenderOwnership.java).

A clean dedicated-server GameTest cannot certify any of these graphical/transit invariants. A GUI screen recorder without real physics witnesses likewise cannot certify a moving ship.

## 3. Precise existing WorldSeam gap matrix (branch PR #98, observed head a52fab6)

| Component | What it genuinely does today | What is missing for strict goal |
|---|---|---|
| `SeamShipChartBounds` | mathematical AABB split into two logical charts | no collider or authoritative contact manifold |
| `SeamShipResidency` | source ship prewarms bounded adjacent native chunks with region tickets | no native terrain inclusion in ONE Rapier solver |
| `SeamShipRemoteContactProbe` | counts real neighbor block `VoxelShape` broadphase candidates | no solver impulse, inertia, contact normal/torque |
| `SeamJoinedServerBlockView` | joined read-only, correctly rejects unloaded native chunks | not wired into Sable native scene chart storage |
| `SeamSableObserver` | reflects real Sable sublevel UUID/hull every 10 ticks; `physicsBridgeActive=false` | no substep integration, body image lifecycle, chart keying |
| `SeamExternalModGameTests` | genuine Sable mass-scaled Rapier downward movement test (3.80 blocks observed), static straddle test | no full native one-UUID downward-and-upward crossing, dual-world contact response |
| `SeamShipFilmE2E` | real disposable world, native `/fill air`, real Sable assembly and force attempts, real ffmpeg MP4 | recorded film has **not** demonstrated crossing; camera alone no proof |
| `SeamNativeGeometryClient` | dedicated GPU terrain mesh of adjacent native chunks | client renderer does not yet own complete sky/fog/particles/entities/sublevel section clipping |

## 4. Implement in this order; NO acceptance shortcut

1. **Physics design gate**: choose chart-native single world core. A single native scene is a *technical requirement* for simultaneous two-chart contacts with one rigid body. Write native-vs-Java ABI contract, one owner body ID and chart map, collision filtering and renderer packet ownership before more unrelated GUI tweaks. Native source licensing MUST be cleared independently; use independent code or permission.
2. **Native proof gate (headless)**: same Sable UUID, mass/inertia and a *single real body handle* through fully submerged lower and upper positions; first in free space, then a Nether ceiling/Overworld floor blocker. Force/torque must change physically in both directions; no position setters, dynamic mirror or clone. A phase-only crossing GameTest must NOT claim dual-world contacts.
3. **Collision gates**: chart-tagged 4096-block terrain sections, native neighbor invalidation, image collider transform/clip at `OW -64↔Nether 128` with non-16-aligned optional boundaries, no mirrored mass, no same-body self-contact, proper friction, dynamic bodies, joints, rope constraints.
4. **Real Minecraft integration gate**: 1.21.1 actual client/ServerLevel GameTest, Create 6, Sable 2.0.6, Aeronautics 1.3.2; client snapshots routed before motion packets; rider/seat and moving component stay aboard; camera follows actual ship. Do not substitute native physics tests with UI screenshots.
5. **OS gate**: Linux x86_64 GitHub Actions must use an ABI-matched custom native bridge, real `ldd`/JNI symbol check and actual roundtrip. **No silently falling back** to stock Sable while reporting physics success. Windows coverage separately if supported.
6. **Movie gate**: CI records *original X11 pixels* with ffmpeg; requires same UUID actual hull positions `maxY<-64` then `minY>-64` after return, and native cross-chart collision telemetry to claim both-world physics. Video must clearly show live ship and destination geometry. Failure MP4 is retained and labeled FAILED.

**Red-line**: No merge / no public feature-complete claim until native atlas contacts + GUI E2E + both directions + Linux/Windows compatibility + resource-release soak PASS. Do not reuse PolyForm Shield-licensed code or binary in WorldSeam without express compatibility rights.


## 5. Newly verified: the ACTUAL Create Aeronautics propulsion source, not just Sable

Official [Creators-of-Aeronautics/Simulated-Project](https://github.com/Creators-of-Aeronautics/Simulated-Project/tree/3705df2062bebba42c3ab3d6b3dd09faa1512850) at commit **`3705df2`** was reviewed (Custom/NOASSERTION license; DO NOT copy sources). The released Aeronautics 1.3.2 is a real NeoForge 1.21.1 vehicle mod with its own Create-kinetic actor classes, not merely a label on a Sable test raft.

- [`BasePropellerBlockEntity`](https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/3705df2062bebba42c3ab3d6b3dd09faa1512850/aeronautics/common/src/main/java/dev/eriksonn/aeronautics/content/blocks/propeller/small/BasePropellerBlockEntity.java): extends **Create `KineticBlockEntity`**; `rotationSpeed` follows `convertToAngular(getSpeed())` each tick (with interpolation); thrust is `getConfigThrust() * getDirectionIndependentSpeed()`. If `getSpeed()==0`, rotor physics is not active. A decorative propeller block without a shaft/working Create motor does not constitute Aeronautics flight.
- [`SmartPropellerBlockEntity`](https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/3705df2062bebba42c3ab3d6b3dd09faa1512850/aeronautics/common/src/main/java/dev/eriksonn/aeronautics/content/blocks/propeller/small/smart_propeller/SmartPropellerBlockEntity.java): during **`sable$physicsTick(ServerSubLevel,RigidBodyHandle,dt)`**, computes oriented thrust, then `super.applyForces(...,dt)`. This uses the actual native Sable body, its orientation, and the native physics actor lifecycle.
- [Sable's propeller actor contract](https://github.com/STRELENA1/sable/blob/bf9713a6430914545113572d1535bfed531a889f/common/src/main/java/dev/ryanhcode/sable/api/block/propeller/BlockEntitySubLevelPropellerActor.java): obtains `prop.getScaledThrust()`, multiplies by **physics substep dt**, queues a **point force** via `ForceGroups.PROPULSION` and `QueuedForceGroup.applyAndRecordPointForce`; position of applied force yields **real torque**, not merely center-of-mass upward acceleration.
- [Sable `BlockEntityPropeller`](https://github.com/STRELENA1/sable/blob/bf9713a6430914545113572d1535bfed531a889f/common/src/main/java/dev/ryanhcode/sable/api/block/propeller/BlockEntityPropeller.java): `getScaledThrust()` depends on current dimension **air pressure** and airflow/ship velocity; cross-dimensional flight is wrong if only the home `Level` air pressure is sampled when an actual propeller lies across the seam. `getAirPressure(level, worldPos)` and `projectOutOfSubLevel` need chart-correct context.
- [PropellerActorBehaviour](https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/3705df2062bebba42c3ab3d6b3dd09faa1512850/aeronautics/common/src/main/java/dev/eriksonn/aeronautics/content/blocks/propeller/behaviour/PropellerActorBehaviour.java): native world pose transforms airflow interaction bounds and particle/physics reactions. An external script pushing the rigid body does **not** prove intact turbine behavior.
- [PropellerBearingBlockEntity](https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/3705df2062bebba42c3ab3d6b3dd09faa1512850/aeronautics/common/src/main/java/dev/eriksonn/aeronautics/content/blocks/propeller/bearing/propeller_bearing/PropellerBearingBlockEntity.java): real Create stress/rotation, contraption attachment and rotor movement; crossing needs these stateful actor connections retained.

**Mandatory correction to WorldSeam E2E:** the current `SeamShipFilmE2E` builds an aircraft-shaped array of concrete/glass/iron/sea-lantern blocks and forces its mass via **test-supplied `applyLinearAndAngularImpulse`**. This is a valuable Sable-only proof but **NOT a powered Aeronautics flight**. Do not name its results as flight driven by Create/Aeronautics. Add a separate actual propulsion fixture assembled with Aero propellers and an operating Create kinetic drivetrain: log Create kinetic speed, `getScaledThrust`, Sable PROPULSION force, actual UUID/pose/velocity, and functioning controls, first in one world and then across world seam. Must be a real published-mod **client** + server run and preserve the propeller/BE state through crossing.

**Additional aerodynamic correctness criterion:** per chart validate dimension-dependent air pressure, lift/drag, buoyancy, projected world propeller force application point, torque, particles/airflow, rotor speed, Create stress, passenger seat ownership and network snapshot continuity. Gravity must also obey chosen chart semantics and not be double-applied by fused Rapier scenes.


### 5.1 Implemented test-fixture amendment (NOT YET VERIFIED)

After the source audit, **the actual CI film fixture has now been changed**, using no copied third-party implementation, to place:
- registered `create:creative_motor` from genuine Create (shaft facing UP, source default configured 16 speed per Create CreativeMotorBlockEntity source);
- registered `aeronautics:smart_propeller` immediately above the motor, a vertical kinetic pair;
- both blocks inside the **same actual Sable `assembleBlocks` ship**, while preserving real aerodynamic/kinetic BlockEntities.

`SeamAeronauticsEvidence` reads **the assembled live Sable plot's** real `dev.eriksonn.aeronautics` BlockEntityPropeller actors, checks actual Create `getSpeed()`, actor active state and measured thrust. Film test reports `WORLDSEAM_FILM_AERONAUTICS_DRIVETRAIN`; it **cannot claim Aeronautics power** unless real motor+propeller RPM and native thrust are nonzero. Absence is **a test FAILURE**, not a fallback to an externally moved decorative boat.

Current CI still uses test-imposed force pulses as an *assist* to test body boundary traversal. The **final unassisted Aeronautics propulsion/controls** gate must replace that assistance and prove actual throttle direction changes (down/return), spinning blades, air-pressure behavior, and seat/control continuity. Until then, even a powered-propeller film is only an intermediate integration milestone.
