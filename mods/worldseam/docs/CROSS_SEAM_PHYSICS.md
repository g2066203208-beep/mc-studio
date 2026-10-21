# WorldSeam — Cross-seam gameplay and physics acceptance contract

Date: 2026-10-21. WorldSeam alone; no modifications to the Create/Aeronautics studio, NPC, or PEAK climbing code.

## Current verified boundaries (at v0.9.2)

- Players have an experimental no-respawn handoff (both server and real GUI client validated).
- Vanilla non-player entities use event-driven `EntityTickEvent.Post` and `changeDimension`, **excluding vehicles, passengers, riders, and ender dragons**. This is not fully visually or physically seamless.
- WorldSeam previously had **no cross-seam explosions or forces**, no cross-seam AABB collision response, no distant entity rendering, and no Sable sublevel migration integration.
- The two loaded 5x5 native block caches do **not** imply the second world runs all entity ticks / has a physical solver.

## v0.9.3: intentionally scoped first physical effect — explosions

- A native NeoForge `ExplosionEvent.Detonate` near an open seam maps its real 3D spherical blast center onto the destination plane using `targetPlane + sourceY - sourcePlane`.
- The destination calls its own vanilla `ServerLevel.explode`: real remote block damage and real remote entity knockback/damage (no fake block copy). Two-way adjacency derives from the saved dimension stack.
- Unlike mirroring a second TNT *inside* the target dimension, the projected center can be outside target build bounds, yielding only the actual overlapping cap of the blast sphere.
- Server opt-in: `/worldseam enable`, then `/worldseam explosions enable`. Cross explosions default **OFF** per save and stay disabled until explicitly enabled. `/worldseam explosions disable` rolls back the feature switch (not damage already caused).
- Safety: maximum supported explosive radius **8 blocks**, center aperture in source must be air, destination 3×3 neighboring chunk ring must already be loaded, world border checked, nested forwarding forbidden. Never force-load/generate distant target chunks from explosions.
- The source's block-destruction policy and TNT/Mob handling inform the target rule. Permissions and custom Mod explosion calculators may need a specific adapter for protection mods and attribution.
- For vanilla bedrock roof/floor, a sealed contact cell blocks propagation; open the seam only on a disposable test world first. Existing worlds will not be modified by simply enabling cross blasts.

## Tests, and what they do NOT establish

- Pure `SeamBlastGeometryTest` proves boundary transform, symmetric mapping, near/far gating, and bounded effect.
- NeoForge `SeamRuntimeGameTests` now contains a test that explodes a real Overworld block near Y=-64 and checks native Nether Y127 glass destroyed (worlds are independent `ServerLevel` instances). It also tests default opt-out.
- CI build + GameTest must pass before v0.9.3 can be called tested. Need a separate **real graphical** TNT test, Nether->Overworld, real TNT ignited at roof, particles/sound, nearby entities and multiplayer. A server-side API explosion test alone is not a proof of complete vanilla TNT animation/flight.

## Sable / Create Aeronautics: distinct, much harder milestone

Observed engineering references (learn only, **no copied source code**, respect original licenses):
- Immersive Portals NeoForge CE: https://github.com/UpperMoon0/ImmersivePortals . It documents Sable sublevel portal traversal and clip-plane render/collision integration, but its exact verified versions differ; do not treat compatibility claims as WorldSeam results.
- Dimensional Sable (MIT): https://github.com/hollow-egg/Dimensional-Sable . Exposes concepts of `WarpSubLevel(subLevel, dimension, position, warpConnected?)`, attached links (rope, spring, docking) and block-entity UUID / position fixups. Its own README acknowledges Create moving piston / moving contraption limitations.
- Original Immersive Portals design: https://github.com/qouteall/imm_ptl_wiki_copy/blob/master/Implementation-Details.md . Multiworld rendering and AABB split collision are crucial.

Required architecture **before** claims that a full Create Aeronautics aircraft is physics-continuous across the seam:

1. **Identify sublevels from the live Sable 2.0.x runtime.** Query actual Sable parent transforms, mass and inertia, angular and linear velocity, collider AABB and connected-sublevel graph. Do not cast a ship to a vanilla `Entity`.
2. **Broadphase crossing event** for Sable shapes intersecting the configured horizontal connection plane; do not scan every sublevel of every server dimension each tick.
3. **Single authoritative rigid-body identity** across both worlds; when straddling, generate transient collision proxies in the neighbor physics space (rather than duplicate independent physics bodies). Transform each contact point and contact normal through the Y-offset while preserving X/Z and rotation.
4. **Impulse and constraint bridge**: apply collision impulses to the authoritative rigid body once, maintain wake/sleep states, gravity, motor torques, elastic joints, bearings, springs, ropes and seat riders. Without this the ship can tear apart or rubber-band at the seam.
5. **Atomic migration** of sublevel plus connected sublevels, block entity world references, Create contraption controllers, passengers, network and persistence when the solver frame of reference changes. If official API is absent, use a gated version-pinned adapter—NEVER forge live Sable internal data by reflection on an unverified version.
6. **Native client synchronized ghost projection and two-sided clip rendering** of straddling rotating bodies, entities and smoke/explosion visuals, with correct remote LevelRenderer/lightmap/shader state.
7. **Integration test matrix**: 1-block rigid body, assembled airship, wind-powered propeller, shafts/stress network, moving contraption, multi-sublevel linkage, rider, explosion on one side during half-cross, unload/relog, remote spectator, constraints near contact plane, CPU frame-time and no perpetual chunk tickets. Every test must run with **actual released** Sable and Aeronautics JARs and record versions, source and CI artifacts.

### Non-goals of v0.9.3

Do not describe the explosion patch as completed Sable crossing, continuous two-world rigid-body collisions, fully redirected projectiles, physics particles, or cross-border mechanical power transmission. These remain blockers.
