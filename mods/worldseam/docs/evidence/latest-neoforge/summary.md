# WorldSeam actual NeoForge client evidence

GitHub Actions: https://github.com/g2066203208-beep/mc-studio/actions/runs/39717461605

Source commit: 4130c441212e530c1d185a320f2c785958786f9f

Captured on: 2026-10-22T03:37:55Z UTC

Screenshots: raw real Minecraft via Xvfb/XWD; **not** composited artist mockups.

New world Create-World editor uses Overworld > Nether > End; the in-world End-above-Overworld journey is an explicitly pinned historical-save regression.

The E2E checks native handoff and client state. It does **not** certify pixel-perfect seamless frame composition or FPS.

Minecraft E2E log markers:
[03:36:38] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_CROSS_TNT_WITNESS: Overworld glass at BlockPos{x=7, y=-64, z=-48} must be destroyed by real Nether TNT
[03:36:50] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PREVIEW_WINDOW: client dim=minecraft:overworld, received=36, cached3x3=9, surface geometry=87014, drawn=386, camera near floor Y=-61.0, nativeModelCpuP95Ms=11.597, modelBudget=407, visibleSlices=178, frustumCulledSlices=15, rebuildCpuP95Ms=8.285, pendingSections=10, lastTickSections=4, ownBufferActive=true, ownBufferCreations=1, ownBufferFlushes=238
[03:36:50] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_PREWARM: ticket=true, resident5x5=25, alreadySent=25, fetches=0, ticketsMoved=1, packets=36
[03:36:56] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_SECOND_WORLD_LIVE_BLOCK: mirrored=true at BlockPos{x=8, y=126, z=-46}, client dimension=minecraft:overworld, remote client resident=25/25
[03:37:03] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_STABLE: ticket=true, resident5x5=25, transmitted=25, forcedFetches=0 (was 0), unchangedPacketSends=25 (was 25), legitimateLiveUpdates=25 (was 21), totalPacketsDelta=4
[03:37:12] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_CLIENT: real client now in Nether without vanilla respawn
[03:37:12] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_SERVER: real ServerPlayer moved into Nether
[03:37:17] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_CROSS_TNT_SPAWN: real vanilla PrimedTnt with fuse 2 in Nether near Y128
[03:37:22] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_CROSS_TNT: native Nether PrimedTnt blast destroyed real Overworld floor glass, no teleport used
[03:37:29] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_RETURN_CLIENT: live Nether -> Overworld direct handoff
[03:37:29] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_RETURN_SERVER: live Nether -> Overworld server migration
[03:37:35] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_END_NATURAL_STONE: 25726 native End-stone candidates, 880 actually submitted in latest pre-crossing frame
[03:37:47] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_END_CLIENT: Overworld TOP -> End BOTTOM without vanilla respawn
[03:37:47] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_END_SERVER: Overworld TOP -> End BOTTOM
[03:37:52] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_DISABLE_CLEARED: OFF synced; remotes freed, active world buffers retained only where necessary. proxyDisposals=4, nativeCreated=2, nativeReleased=1, nativeActive=1

