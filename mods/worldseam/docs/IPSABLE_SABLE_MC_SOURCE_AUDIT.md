# WorldSeam — Source-level architecture audit: Sable, Immersive Portals, IPSable, Minecraft 1.21.1

**Review date:** 2026-10-23. **Purpose:** prevent repeated claims that a static AABB, a
server-only force test, or an empty Minecraft MP4 proves genuine cross-dimension
Sable/Create Aeronautics physics. **Code state:** native shared-chart physics is
NOT IMPLEMENTED by WorldSeam. This is not an acceptance certificate.

## Sources actually inspected (direct links to code, not guessed APIs)

1. **Sable 2.x** ([repository](https://github.com/STRELENA1/sable)):
   - `common/.../sublevel/system/SubLevelPhysicsSystem.java`, specifically
     `tickPipelinePhysics`: each Level has a PhysicsPipeline; for each physics
     substep, call prePhysicsTick → pipeline.physicsTick → updateAllPoses.
   - `sable_rapier/.../RapierPhysicsPipeline.java`, `physicsTick` and
     `applyLinearAndAngularImpulse`: this JNI bridge actually calls
     `Rapier3D.applyForceAndTorque`. Values are **Newtons**, so the old
     35-N-for-a-ship test could not provide a meaningful propulsion proof.
   - `common/.../sublevel/SubLevel.java`, `updateBoundingBox()`: the initially
     zero AABB is not the real ship; Sable mass and logicalPose come from the
     *actual assembled ServerSubLevel*. Recompute before claiming movement.
   - `common/.../api/sublevel/SubLevelContainer.java` exposes
     `getContainer(ClientLevel)` and `getSubLevel(UUID)`.
     `ClientSubLevel.getRenderData()` establishes client render registration,
     but **not** pixel visibility, occlusion correctness or draw-call counts.
2. **IPSable** (Apache-2.0, [source](https://github.com/r2smith141/IPSable)):
   - `docs/atlas_portal_physics_spec.md` §§2.1–2.3: one native Rapier world,
     *per-chart* terrain + **image colliders referencing the original body**,
     no duplicated dynamic bodies, with contact normal / lever arm transformed
     from foreign chart back to the one body.
   - `src/main/java/ipl/sable/atlas/IplFusedStep.java` and
     `mixin/IplFusedStepMixin.java`: merges Sable substep phases and **gates**
     the shared native Rapier step so it is executed ONCE per substep,
     not once per dimension.
   - `atlas/IplAtlasBodyImages.java`: creates an image collider in a foreign
     chart with the original native body ID; cleanup never dereferences dead
     native scenes. Dynamic *image collision geometry* is not a second body.
   - `dim/IplSceneOwnership.java`: real Sable body / plot / BE and constraint
     ownership stay with one owning level; render/physics images may appear in
     neighboring chart, ownership does not migrate on crossing.
   - `mixin/SableRapierPipelineOwnershipGuardMixin.java`: **native stale
     rigid-body id may hang the server or abort JNI**. Only act on bodies
     owned by an active pipeline. A server UUID alone is not a valid Rapier ID.
   - `atlas/IplHostedTerrainGate.java`: remote parent terrain not ready →
     park native simulation safely, never silently treat unloaded chunks as air.
   - `docs/atlas_portal_physics_audit_results.md`: native Rapier dispatcher
     stores/chooses per-chart chunk maps, *not* simple Java VoxelShape contact
     candidates; solve contact manifolds with transformed center of mass.
3. **ImmersivePortals 1.21.1 NeoForge** ([source](https://github.com/UpperMoon0/ImmersivePortals)):
   - `compat/mixin/sable/MixinClientboundStartTrackingSubLevelPacket_SablePortalCompat.java`
     handles client-owned sublevel UUID, full-sync and interpolation history
     when crossing portals. Fake server-only snapshots are insufficient.
   - `compat/mixin/sable/MixinVanillaChunkedSubLevelRenderData_SableSectionOwnership.java`
     retains Sable section buffer ownership through resize and close.
   - `compat/mixin/sable/MixinClientWorldLoader_SableRenderOwnership.java`
     releases per-dimension Sable GPU section owners on world disposal.
   - `compat/mixin/sable/MixinSubLevelPhysicsSystem_SableDimensionStackCompat.java`
     hooks physics substeps rather than only once every 10 ticks.
4. **Minecraft / NeoForge game itself**: WorldSeam's actual 1.21.1
   `SeamServer`, `SeamChunkSender`, `SeamClientWorldPool`,
   `SeamNativeGeometryClient` must keep both **native** ServerLevels and
   ClientLevels live. A world projection must keep consistent block light,
   chunk lifetime, renderer pool, Sable client start-tracking packets,
   passengers, blocks and block entities.

## What WorldSeam currently does versus what is REQUIRED

| Area | Current WorldSeam source / evidence | IPSable-class requirement |
|---|---|---|
| Real ship identity | `SeamSableObserver` collects Sable ServerSubLevel UUID and real AABB | Single *native Rapier body handle*, stable through complete crossing |
| Neighbor terrain | `SeamShipRemoteContactProbe` samples loaded native VoxelShape broadphase | Atlas per-chart chunk collider mapping queried during each *native* physics substep |
| Joined voxel lookup | `SeamJoinedServerBlockView` reads true OW/Nether states/fluids and fails closed | Feed native solver chart-keyed section storage; restore disabled/hidden native near-plane collider sections |
| Physics step | Original per-level Sable pipelines still step separately | One fused/native Rapier world; one step per substep, correct mass/COM and force transforms |
| Foreign collision | **NONE** — `physicsBridgeActive=false` | Foreign-chart *image colliders* attached to the same rigid body, contact solved in same island |
| Sable client rendering | Real video can show only void/terrain despite server y crossing | Same UUID actually client tracked/rendered in every relevant view; no missing GPU sections |
| Create/Aero | True motor/propeller actor can spin at end of externally forced roundtrip | Motor/propeller RPM and measured thrust must itself accelerate aircraft without injected test force |
| Film acceptance | 2026-10-22 `#39871438051`: 33.9 s MP4, 21 server ticks ship movement; observed frames include no discernible aircraft | Continuous visible aircraft in frame before/during/after seam, actual native movement, no teleport, Aero-only powered trial, true foreign collision witness |

### Translation-only mapping in the *default* dimension stack

When OW logical bottom is Y=-64, Nether logical top is Y=128:
`y_nether = y_overworld + 192`. The home body remains at its OW
coordinates, while the geometric **image** in Nether uses this constant
transform. **Collision impulse and lever arm** from Nether contact must be
mapped back to that ORIGINAL body's center of mass. A Java AABB intersection
is NOT a force/torque applied by native Rapier. For custom/modded dimensions,
seam planes need not be 16-aligned, so naïve section-offset copying is wrong;
clip/rebake partial boundary chunks instead.

## Implementation order and hard acceptance gates

1. **GATE R (real visualization)**: client has matching Sable UUID and live
   Sable render data *before* physical flight; record actual ship pixel coverage
   in front/straddle/far-side frames (checking only GPU object existence is
   insufficient). Extend video capture; 21 ticks/~1s is too fast to judge.
2. **GATE P (unassisted real Create:Aero propulsion)**: record live Create
   RPM, Sable aero thrust, force/velocity and position **with no external
   test pulses**. A separately labeled Rapier-forced movement test is allowed
   only as a *diagnostic*; it cannot pass the Aero-only gate.
3. **GATE A0 (ownership & native scenes)**: inspect active native body IDs,
   scene handles, plot ownership and chunks, attach a fail-closed guard before
   any native operation; prevent native stale-handle JNI hangs.
4. **GATE A1 (chart terrain)**: create one native world with chart-keyed
   native terrain, with a strict ready/unloaded policy for remote chunks and
   contact clipping on both sides of the seam. Do NOT convert unloaded areas
   to AIR or create a second body.
5. **GATE A2 (single rigid body/contact solver)**: image colliders with the
   correct +192/-192 transform for default stack, inertia/COM contributions
   unchanged. Confirm an OW-anchored hull **rests on a real Nether block**
   and Nether contact changes SAME Rapier UUID angular+linear velocities.
6. **GATE A3 (fused substeps)**: one physics step per native substep, correct
   BE/kinematic/constraint phases; repeated crossings, multi-ship, rider,
   unloaded terrain, resource cleanup and robust latency/FPS stress.
7. **GATE F (movie)**: real gameplay MP4 shows uninterrupted full ship
   down/up and player/rider, with overlay showing dimension and the SAME body
   UUID, plus independently verified FOV/object pixels; no cuts or fake ship.
   Keep the PR **draft** until GATES R/P/A0–A3/F all pass.

**Do not vendor/repackage IPSable native libraries or third-party assets without
separately reviewing compatibility, license and binary redistribution terms.
Its Apache-2.0 documents and source are architectural references, not proof
that its patched native ABI exists in stock Sable 2.0.6.**
