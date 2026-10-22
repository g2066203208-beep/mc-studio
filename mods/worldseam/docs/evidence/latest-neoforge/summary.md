# WorldSeam actual NeoForge client evidence

GitHub Actions: https://github.com/g2066203208-beep/mc-studio/actions/runs/39709446151

Source commit: fc5392e7ecddeb4086838251d1315cf0ca1961f8

Captured on: 2026-10-22T02:10:02Z UTC

Screenshots: raw real Minecraft via Xvfb/XWD; **not** composited artist mockups.

New world Create-World editor uses Overworld > Nether > End; the in-world End-above-Overworld journey is an explicitly pinned historical-save regression.

The E2E checks native handoff and client state. It does **not** certify pixel-perfect seamless frame composition or FPS.

Minecraft E2E log markers:
[02:08:45] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_CROSS_TNT_WITNESS: Overworld glass at BlockPos{x=6, y=-64, z=8} must be destroyed by real Nether TNT
[02:08:58] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PREVIEW_WINDOW: client dim=minecraft:overworld, received=40, cached3x3=9, surface geometry=66588, drawn=969, camera near floor Y=-61.0, nativeModelCpuP95Ms=10.284, modelBudget=914, visibleSlices=157, frustumCulledSlices=10, rebuildCpuP95Ms=4.843, pendingSections=35, lastTickSections=4, ownBufferActive=true, ownBufferCreations=1, ownBufferFlushes=276
[02:08:58] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_PREWARM: ticket=true, resident5x5=25, alreadySent=25, fetches=0, ticketsMoved=1, packets=40
[02:09:04] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_SECOND_WORLD_LIVE_BLOCK: mirrored=true at BlockPos{x=7, y=126, z=10}, client dimension=minecraft:overworld, remote client resident=25/25
[02:09:11] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_STABLE: ticket=true, resident5x5=25, transmitted=25, forcedFetches=0 (was 0), unchangedPacketSends=25 (was 25), legitimateLiveUpdates=40 (was 33), totalPacketsDelta=7
[02:09:20] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_CLIENT: real client now in Nether without vanilla respawn
[02:09:20] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_SERVER: real ServerPlayer moved into Nether
[02:09:25] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_CROSS_TNT_SPAWN: real vanilla PrimedTnt with fuse 2 in Nether near Y128
[02:09:30] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_CROSS_TNT: native Nether PrimedTnt blast destroyed real Overworld floor glass, no teleport used
[02:09:37] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_RETURN_CLIENT: live Nether -> Overworld direct handoff
[02:09:37] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_RETURN_SERVER: live Nether -> Overworld server migration
[02:09:43] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_END_NATURAL_STONE: 25642 native End-stone candidates, 1439 actually submitted in latest pre-crossing frame
[02:09:55] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_END_CLIENT: Overworld TOP -> End BOTTOM without vanilla respawn
[02:09:55] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_END_SERVER: Overworld TOP -> End BOTTOM
[02:10:00] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_DISABLE_CLEARED: OFF synced; remotes freed, active world buffers retained only where necessary. proxyDisposals=4, nativeCreated=2, nativeReleased=1, nativeActive=1

