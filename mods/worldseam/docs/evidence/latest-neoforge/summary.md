# WorldSeam actual NeoForge client evidence

GitHub Actions: https://github.com/g2066203208-beep/mc-studio/actions/runs/39719822953

Source commit: 6fc7ff672d366f0ea6afe8524a6bc40b99c1132a

Captured on: 2026-10-22T04:03:47Z UTC

Screenshots: raw real Minecraft via Xvfb/XWD; **not** composited artist mockups.

New world Create-World editor uses Overworld > Nether > End; the in-world End-above-Overworld journey is an explicitly pinned historical-save regression.

The E2E checks native handoff and client state. It does **not** certify pixel-perfect seamless frame composition or FPS.

Minecraft E2E log markers:
[04:02:30] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_CROSS_TNT_WITNESS: Overworld glass at BlockPos{x=99, y=-64, z=-1} must be destroyed by real Nether TNT
[04:02:43] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PREVIEW_WINDOW: client dim=minecraft:overworld, received=40, cached3x3=9, surface geometry=46773, drawn=493, camera near floor Y=-61.0, nativeModelCpuP95Ms=12.257, modelBudget=493, visibleSlices=113, frustumCulledSlices=15, rebuildCpuP95Ms=3.937, pendingSections=84, lastTickSections=4, ownBufferActive=true, ownBufferCreations=1, ownBufferFlushes=279
[04:02:43] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_PREWARM: ticket=true, resident5x5=25, alreadySent=25, fetches=0, ticketsMoved=1, packets=40
[04:02:49] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_SECOND_WORLD_LIVE_BLOCK: mirrored=true at BlockPos{x=100, y=126, z=1}, client dimension=minecraft:overworld, remote client resident=25/25
[04:02:56] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_STABLE: ticket=true, resident5x5=25, transmitted=25, forcedFetches=0 (was 0), unchangedPacketSends=25 (was 25), legitimateLiveUpdates=41 (was 32), totalPacketsDelta=9
[04:03:05] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_CLIENT: real client now in Nether without vanilla respawn
[04:03:05] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_SERVER: real ServerPlayer moved into Nether
[04:03:10] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_CROSS_TNT_SPAWN: real vanilla PrimedTnt with fuse 2 in Nether near Y128
[04:03:15] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_CROSS_TNT: native Nether PrimedTnt blast destroyed real Overworld floor glass, no teleport used
[04:03:22] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_RETURN_CLIENT: live Nether -> Overworld direct handoff
[04:03:22] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_RETURN_SERVER: live Nether -> Overworld server migration
[04:03:28] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_END_NATURAL_STONE: 23850 native End-stone candidates, 1037 actually submitted in latest pre-crossing frame
[04:03:40] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_END_CLIENT: Overworld TOP -> End BOTTOM without vanilla respawn
[04:03:40] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_END_SERVER: Overworld TOP -> End BOTTOM
[04:03:45] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_DISABLE_CLEARED: OFF synced; remotes freed, active world buffers retained only where necessary. proxyDisposals=4, nativeCreated=2, nativeReleased=1, nativeActive=1

