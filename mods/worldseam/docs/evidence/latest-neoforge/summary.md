# WorldSeam actual NeoForge client evidence

GitHub Actions: https://github.com/g2066203208-beep/mc-studio/actions/runs/39621710725

Source commit: e9747632e1a926ddba1445ad106be31485961bb3

Captured on: 2026-10-21T15:03:40Z UTC

Screenshots: raw real Minecraft via Xvfb/XWD; **not** composited artist mockups.

The E2E checks native handoff and client state. It does **not** certify pixel-perfect seamless frame composition or FPS.

Minecraft E2E log markers:
[15:02:36] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PREVIEW_WINDOW: client dim=minecraft:overworld, received=42, cached3x3=9, surface geometry=76025, drawn=3500, camera near floor Y=-61.0
[15:02:36] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_PREWARM: ticket=true, resident5x5=25, alreadySent=25, fetches=0, ticketsMoved=1, packets=42
[15:02:42] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_SECOND_WORLD_LIVE_BLOCK: mirrored=true at BlockPos{x=257, y=126, z=26}, client dimension=minecraft:overworld, remote client resident=25/25
[15:02:49] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_STABLE: ticket=true, resident5x5=25, transmitted=25, forcedFetches=0 (was 0), unchangedPacketSends=25 (was 25), legitimateLiveUpdates=30 (was 27), totalPacketsDelta=3
[15:02:58] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_CLIENT: real client now in Nether without vanilla respawn
[15:02:58] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_SERVER: real ServerPlayer moved into Nether
[15:03:15] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_RETURN_CLIENT: live Nether -> Overworld direct handoff
[15:03:15] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_RETURN_SERVER: live Nether -> Overworld server migration
[15:03:21] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_END_NATURAL_STONE: 20925 native End-stone candidates, 3500 actually submitted in latest pre-crossing frame
[15:03:33] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_END_CLIENT: Overworld TOP -> End BOTTOM without vanilla respawn
[15:03:33] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_END_SERVER: Overworld TOP -> End BOTTOM
[15:03:38] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_DISABLE_CLEARED: authoritative OFF synced, remote ClientLevel and models released

