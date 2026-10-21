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

## Physical dimension bounds fix · v0.7.0-dev

The former implementation mixed MC's Nether build height (max exclusive 256) with its physical vanilla bedrock ceiling at Y128. Consequences: remote near/far proxy placed at Y256, no crossing while standing underneath the bedrock, and `ServerPlayer.teleportTo()` still appearing on the legacy toggle path.

WorldSeam now uses `SeamBounds.top(level)` uniformly for source/target seam Y in server entity, preview and LOD. Pure `SeamCrossing` detects directional motion while the entity is still within 0.5 blocks of the physical seam plane and maps it to a safe 0.125-block inset of the opposite world's corresponding edge; supports large overshoots as fallback. `SeamArrival` continues collision checks (old roof bedrock blocks crossing).

Bedrock world-generation fix: custom `SeamChunkGenerationMixin` at MC 1.21.1 `ChunkStatusTasks.generateSpawn` invokes `SeamTerrain.replaceGeneratedBedrock()` only when the test-save's terrain setting is enabled. Existing chunks are unmodified unless admin expressly invokes `/worldseam setup-test`, which removes only bedrock in one corresponding Overworld and Nether 16x16 column (irreversible).

`/worldseam enable` now enables no-respawn by default because legacy teleport was misleading. Admin can still explicitly use `/worldseam smooth disable` for regression testing of the legacy path, but that is not seamless.

This does NOT yet solve multi-ClientLevel frame rendering, visual depth clipping, synchronization of all entities, collision bridging for physics mods, or verified smooth client handoff. Prior GameTest and headless client startup are insufficient for complete user-visible no-loading claims.

## v0.8 save-specific editable stack

The previous `SeamGraph.LINKS` is retained only as a legacy baseline for pure tests. New server-side runtime world routing takes `SeamSettings.stack()` via `SeamStackRuntime`. The create-world GUI (registered using NeoForge `ScreenEvent.Init.Post`) obtains candidate dimensions from the world-creation `datapackDimensions` registry and opens `SeamStackScreen`. A CreateWorldScreen Mixin arms the preset only when the actual `onCreate` action is invoked. A one-shot `ServerStartingEvent` installs the validated stack into the new world's persistent SavedData. The server transmits ordered entries and actual target dimension type keys via `SeamStackSyncPayload`; clients run their geometry/HUD mappings from that snapshot. The server dynamically routes chunks, previews, player/entity crossings, and bedrock replacement bands using stack endpoints.

**Remaining issues before declaring complete:** verify screen can be opened/saved in real NeoForge GUI and configuration is persisted across reload. Test actual installed third-party dimension mods with different height and dimension-type keys. Vanilla GUI edit config changes the *seam endpoints*, not the registered `DimensionType` properties. No dynamic registry rewrite of min_y/height. No fully unified multiworld stencil or depth compositing.
