# WorldSeam actual NeoForge client evidence

GitHub Actions: https://github.com/g2066203208-beep/mc-studio/actions/runs/39618151475

Source commit: 79210eceb3dd7b969b89dcac4ac7752360f3bfba

Captured on: 2026-10-21T14:39:24Z UTC

Screenshots: raw real Minecraft via Xvfb/XWD; **not** composited artist mockups.

The E2E checks native handoff and client state. It does **not** certify pixel-perfect seamless frame composition or FPS.

Minecraft E2E log markers:
[14:38:19] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PREVIEW_WINDOW: client dim=minecraft:overworld, received=35, cached3x3=9, surface geometry=53765, drawn=3500, camera near floor Y=-61.0
[14:38:19] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_PREWARM: ticket=true, resident5x5=25, alreadySent=25, fetches=0, ticketsMoved=1, packets=35
[14:38:26] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_SECOND_WORLD_LIVE_BLOCK: mirrored=true at BlockPos{x=13, y=126, z=-47}, client dimension=minecraft:overworld, remote client resident=25/25
[14:38:32] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_STABLE: ticket=true, resident5x5=25, transmitted=25, forcedFetches=0 (was 0), unchangedPacketSends=25 (was 25), legitimateLiveUpdates=24 (was 20), totalPacketsDelta=4
[14:38:41] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_CLIENT: real client now in Nether without vanilla respawn
[14:38:41] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_SERVER: real ServerPlayer moved into Nether
[14:38:59] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_RETURN_CLIENT: live Nether -> Overworld direct handoff
[14:38:59] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_RETURN_SERVER: live Nether -> Overworld server migration
[14:39:05] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_END_NATURAL_STONE: 22474 native End-stone candidates, 3500 actually submitted in latest pre-crossing frame
[14:39:17] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_END_CLIENT: Overworld TOP -> End BOTTOM without vanilla respawn
[14:39:17] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_END_SERVER: Overworld TOP -> End BOTTOM
[14:39:22] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_DISABLE_CLEARED: authoritative OFF synced, remote ClientLevel and models released

