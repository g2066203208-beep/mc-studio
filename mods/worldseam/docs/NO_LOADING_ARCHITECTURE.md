# WorldSeam — no-loading architecture

Root cause of the reported loading transition: SeamServer still invokes vanilla ServerPlayer.teleportTo, which causes the standard respawn and client world rebuild.

Research: https://github.com/UpperMoon0/ImmersivePortals — its ClientWorldLoader owns separate ClientLevel and LevelRenderer contexts, ClientTeleportationManager performs camera-frame switching, and ServerTeleportationManager avoids the vanilla respawn pathway. This project studies this design without importing that mod or copying its source.

This revision adds SeamClientWorldPool. On receiving a real, dimension-scoped remote section stream, it constructs a distinct ClientLevel and LevelRenderer for the destination. Ownership is limited to one secondary world, resets on logout/source-dimension change, fails closed, and never swaps the active Minecraft.level.

Important: a newly constructed ClientLevel is empty until chunks, biomes, light, block entities, and entities are redirected into it. Server still uses vanilla teleport. Preconstruction is a foundation, not yet no-loading functionality.

Next gates: deliver real native chunk packet data to the secondary world; dimension-tagged entity and chunk packets; remove vanilla respawn through an acknowledged server/player migration; camera-frame atomic switch for player, listener, LevelRenderer, particles, lightmap, and effects; validate with an actual GUI client and recorded crossing.

Do not report no-loading success until the vanilla respawn pathway has been replaced and tested.
## 2026-10-21 · 自研 no-respawn 实验实现

源码新模块：SeamChunkSender 与 SeamChunkPayload 向远端第二个 ClientLevel 提前送真实 Minecraft 完整 Chunk Data（含生物群系、方块实体、光照），SeamReadiness/SeamReadyPayload 维护受作用域和 TTL 限制的客户端就绪握手，SeamDirectTransfer/SeamHandoffPayload 与 Mixin accessors 提供直接服务端玩家维度迁移及客户端世界/渲染器切换（仅 /worldseam smooth enable）。默认仍走 vanilla teleportTo。

阶段性验收：单元测试握手校验、编译、GameTest 服务端运行。**这些都不能证明客户端无加载。** 未完成的关键点包括：实机 Mixin 客户端加载；相机在同一帧切换及视觉过渡；周围所有远端实体和材质/块实体更新；按维度隔离全部后续服务器同步；服务端多玩家跟踪及传送事件兼容；安全回滚与防止客户端世界/服务端不同步；Sable 兼容性。没有这些验证不能合入稳定分支。
