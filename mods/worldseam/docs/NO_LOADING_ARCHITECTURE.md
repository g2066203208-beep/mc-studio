# WorldSeam — no-loading architecture

Root cause of the reported loading transition: SeamServer still invokes vanilla ServerPlayer.teleportTo, which causes the standard respawn and client world rebuild.

Research: https://github.com/UpperMoon0/ImmersivePortals — its ClientWorldLoader owns separate ClientLevel and LevelRenderer contexts, ClientTeleportationManager performs camera-frame switching, and ServerTeleportationManager avoids the vanilla respawn pathway. This project studies this design without importing that mod or copying its source.

This revision adds SeamClientWorldPool. On receiving a real, dimension-scoped remote section stream, it constructs a distinct ClientLevel and LevelRenderer for the destination. Ownership is limited to one secondary world, resets on logout/source-dimension change, fails closed, and never swaps the active Minecraft.level.

Important: a newly constructed ClientLevel is empty until chunks, biomes, light, block entities, and entities are redirected into it. Server still uses vanilla teleport. Preconstruction is a foundation, not yet no-loading functionality.

Next gates: deliver real native chunk packet data to the secondary world; dimension-tagged entity and chunk packets; remove vanilla respawn through an acknowledged server/player migration; camera-frame atomic switch for player, listener, LevelRenderer, particles, lightmap, and effects; validate with an actual GUI client and recorded crossing.

Do not report no-loading success until the vanilla respawn pathway has been replaced and tested.