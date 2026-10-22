# WorldSeam actual NeoForge client evidence

GitHub Actions: https://github.com/g2066203208-beep/mc-studio/actions/runs/39700920844

Source commit: d97168a475df02fc916228e85acb0c75a53f84c6

Captured on: 2026-10-22T00:43:07Z UTC

Screenshots: raw real Minecraft via Xvfb/XWD; **not** composited artist mockups.

New world Create-World editor uses Overworld > Nether > End; the in-world End-above-Overworld journey is an explicitly pinned historical-save regression.

The E2E checks native handoff and client state. It does **not** certify pixel-perfect seamless frame composition or FPS.

Minecraft E2E log markers:
[00:41:51] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_CROSS_TNT_WITNESS: Overworld glass at BlockPos{x=-3, y=-64, z=7} must be destroyed by real Nether TNT
[00:42:03] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PREVIEW_WINDOW: client dim=minecraft:overworld, received=32, cached3x3=9, surface geometry=81860, drawn=584, camera near floor Y=-61.0, nativeModelCpuP95Ms=10.331, modelBudget=616, visibleSlices=184, frustumCulledSlices=10, rebuildCpuP95Ms=9.939, pendingSections=0, lastTickSections=0, ownBufferActive=true, ownBufferCreations=1, ownBufferFlushes=266
[00:42:03] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_PREWARM: ticket=true, resident5x5=25, alreadySent=25, fetches=0, ticketsMoved=1, packets=32
[00:42:09] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_SECOND_WORLD_LIVE_BLOCK: mirrored=true at BlockPos{x=-2, y=126, z=9}, client dimension=minecraft:overworld, remote client resident=25/25
[00:42:16] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_STABLE: ticket=true, resident5x5=25, transmitted=25, forcedFetches=0 (was 0), unchangedPacketSends=25 (was 25), legitimateLiveUpdates=22 (was 18), totalPacketsDelta=4
[00:42:25] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_CLIENT: real client now in Nether without vanilla respawn
[00:42:25] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_SERVER: real ServerPlayer moved into Nether
[00:42:30] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_CROSS_TNT_SPAWN: real vanilla PrimedTnt with fuse 2 in Nether near Y128
[00:42:35] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_CROSS_TNT: native Nether PrimedTnt blast destroyed real Overworld floor glass, no teleport used
[00:42:42] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_RETURN_CLIENT: live Nether -> Overworld direct handoff
[00:42:42] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_RETURN_SERVER: live Nether -> Overworld server migration
[00:42:48] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_END_NATURAL_STONE: 25202 native End-stone candidates, 806 actually submitted in latest pre-crossing frame
[00:43:00] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_END_CLIENT: Overworld TOP -> End BOTTOM without vanilla respawn
[00:43:00] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_END_SERVER: Overworld TOP -> End BOTTOM
[00:43:05] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_DISABLE_CLEARED: authoritative OFF synced, remote ClientLevel, models and render buffers released, bufferDisposals=4

