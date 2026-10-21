# WorldSeam FAILED graphical client run (NOT a successful screenshot)

Actions: https://github.com/g2066203208-beep/mc-studio/actions/runs/39628367368

Source commit: 7309c7264cdd82ebc6b118ea64dd82efd0ec1dc3

Time: 2026-10-21T15:48:07Z UTC

Successful screenshots and summary.md were deliberately NOT overwritten.

Available WorldSeam client E2E events and last diagnostic errors:

[15:46:04] [modloading-worker-0/WARN] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_ACTIVE: disposable singleplayer world ONLY
[15:47:19] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_START: actually joined world minecraft:overworld
[15:47:19] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_LEGACY_STACK_PINNED: old world ordering End > Overworld > Nether, separate from new-world default
[15:47:19] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_BOUNDS: overworld=[-64,320), nether=[0,256), end=[0,256), joined overworld floor Y=-64 to nether roof Y=128
[15:47:22] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_END_WITNESS: NO synthetic End blocks created
[15:47:22] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_CROSS_TNT_WITNESS: Overworld glass at BlockPos{x=3, y=-64, z=-6} must be destroyed by real Nether TNT
[15:47:22] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_GEOMETRY: opened local deepslate shaft=1051 blocks, Nether roof=597 blocks, target-only magenta/glowstone markers=80, player view Overworld -61 toward mapped Nether Y127
[15:47:22] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_SETUP: opened 789 bedrock blocks, at x=2 z=-6, Overworld Y=-61
[15:47:34] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PREVIEW_WINDOW: client dim=minecraft:overworld, received=35, cached3x3=9, surface geometry=81317, drawn=3500, camera near floor Y=-61.0, nativeModelCpuP95Ms=58.058
[15:47:34] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_PREWARM: ticket=true, resident5x5=25, alreadySent=25, fetches=0, ticketsMoved=1, packets=35
[15:47:36] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_MUTATE_REMOTE_NETHER: changed block at BlockPos{x=4, y=126, z=-4} while player remains in Overworld
[15:47:40] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_SECOND_WORLD_LIVE_BLOCK: mirrored=true at BlockPos{x=4, y=126, z=-4}, client dimension=minecraft:overworld, remote client resident=25/25
[15:47:43] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_LIVE_PACKET: liveResends=20, totalPackets=45, loaded5x5=25
[15:47:47] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_DUAL_STABLE: ticket=true, resident5x5=25, transmitted=25, forcedFetches=0 (was 0), unchangedPacketSends=25 (was 25), legitimateLiveUpdates=28 (was 20), totalPacketsDelta=8
[15:47:50] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_CHECK: client native packets=60, 3x3=9, geometry=81317, drawn=3500
[15:47:50] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_REMOTE_READY=true, server dimension=minecraft:overworld, y=-61.0
[15:47:50] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_TRIGGER: actual integrated ServerPlayer near floor with downwards velocity
[15:47:56] [Render thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_CLIENT: real client now in Nether without vanilla respawn
[15:47:56] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_PASS_SERVER: real ServerPlayer moved into Nether
[15:47:56] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_REVERSE_STAGE: hovering inside Nether below Y128, looking UP
[15:48:01] [Server thread/INFO] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_CROSS_TNT_SPAWN: real vanilla PrimedTnt with fuse 2 in Nether near Y128
[15:48:06] [Server thread/ERROR] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_FAIL_CROSS_TNT: actual Nether TNT did not destroy remote Overworld glass

Last 25 client log lines:

[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: 7, 3
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -7, -3
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -7, -4
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: 4, 6
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: 7, -3
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -7, 2
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: 3, 6
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: 7, -4
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -7, -5
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -7, 3
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: 7, 4
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: 5, 6
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: 6, 6
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -6, -8
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -7, -6
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: 7, 5
[15:48:05] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: 7, 6
[15:48:06] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -7, -7
[15:48:06] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -3, 7
[15:48:06] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -2, 7
[15:48:06] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -4, 7
[15:48:06] [Server thread/ERROR] [de.re.wo.SeamVisualE2E/]: WORLDSEAM_E2E_FAIL_CROSS_TNT: actual Nether TNT did not destroy remote Overworld glass
[15:48:06] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -7, -8
[15:48:06] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -5, -9
[15:48:06] [Render thread/WARN] [minecraft/ClientChunkCache]: Ignoring chunk since it's not in the view range: -6, -9
