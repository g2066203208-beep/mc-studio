# WorldSeam 1.21.1 world-height audit — actual integration evidence

## Vanilla heights (unmodified, verified on real NeoForge integrated server)
| Level | min inclusive | max exclusive | natural bedrock |
|---|---:|---:|---|
| Overworld | -64 | 320 | bottom: -64 floor plus scattered -63..-60 |
| Nether | 0 | 256 | bottom 0..4; roof approximately 123..127 |
| End | 0 | 256 | no native solid bedrock floor across the End void |

**Nether roof Y=128 is a CUSTOM CONNECTING PLANE, NOT Nether maximum build height.** Vanilla Nether blocks are still buildable above the roof up to Y=255; current WorldSeam does **not** fully reconcile this above-roof space with its compressed lower-realm topology.

## WorldSeam seams — actual current implementation
- Overworld BOTTOM boundary Y=-64 connects to Nether TOP **logical plane** Y=128. X/Z are unchanged (no vanilla Nether 8:1 position scaling).
- One virtual unit below the Overworld floor is Overworld-continuous Y=-65 = Nether local Y=127. Another unit is Overworld-continuous Y=-66 = Nether local Y=126.
- Nether local Y127 maps back to virtual Overworld Y=-65. Vanilla blocks above Nether Y128..255 remain in separate Nether internal build space and are **not** currently stitched into this virtual vertical interval.
- Overworld TOP Y320 connects to End BOTTOM Y0. The large End void and End TOP have not been separately fused.

## Why the user previously saw nothing
1. Old E2E positioned the camera around Overworld Y=-61, **inside ordinary solid deepslate**, and merely removed bedrock. Deepslate prevented line of sight through Y=-64.
2. WorldSeam previously rendered sparse block previews; the current implementation renders limited genuine remote native Chunk models, but does not yet implement a full portal framebuffer, depth mask, fluid/particle/entity composition.
3. The previous HUD showed **only the target dimension**, misleading the reader about which world the player was actually in.

## Measured world and visible witness — real GUI Minecraft, not mockups
Actual CI screenshot: [4 views, in game](worldseam-world-height-view-20261021.jpg), from [NeoForge GUI E2E 39565322628](https://github.com/g2066203208-beep/mc-studio/actions/runs/39565322628). First frame **before any dimension switch**: `WORLDSEAM_E2E_PREVIEW_WINDOW` confirms `client dim=minecraft:overworld`, player feet Y=-61.0, client cached 9/9 native Nether chunks, 60,961 potential remote surface blocks, 3,500 submitted per frame. CI-only harness excavated 958 deepslate blocks above the floor and 598 Nether roof blocks and placed 80 distinct magenta concrete/glowstone target-only markers at Nether Y126/127. The first screenshot shows these distinct Nether-only marker blocks **while client remains in the Overworld**. The second screenshot shows the reverse view in the Nether toward the Overworld underside.

Real integrated client log checks also confirm:
`WORLDSEAM_E2E_BOUNDS: overworld=[-64,320), nether=[0,256), end=[0,256), joined overworld floor Y=-64 to nether roof Y=128`.
Both direct player transitions were acknowledged on the client and server; no vanilla respawn was invoked for the tested handoff.

**What this does NOT prove:** continuous full-world renderer, correct natural untouched bedrock collision and automatic traversal, complete 3D scene beyond nine chunks, light/fluids/entities/block-entity continuity, performance under real load, End-to-Overworld visuals, and Nether upper-roof region treatment. **Do not claim full seamless-world implementation is finished.** CI excavation and colored witness blocks are test-only and **are not placed in user worlds**.

## Source
`SeamBounds.java`, `SeamGraph.java`, `SeamTerrain.java`, `SeamNativeGeometryClient.java`, `SeamVisualE2E.java`, `SeamRuntimeGameTests.java`, `SeamClientDiagnostic.java`.
