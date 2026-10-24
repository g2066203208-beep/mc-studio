package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.reme.worldseam.mixin.SeamMinecraftAccess;
import dev.reme.worldseam.mixin.SeamGameRendererAccess;
import net.minecraft.client.renderer.RenderBuffers;
import dev.reme.worldseam.mixin.SeamPacketListenerAccess;
import dev.reme.worldseam.mixin.SeamEntityAccess;
import dev.reme.worldseam.mixin.SeamClientPlayerAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.Set;

/**
 * Own secondary client-world owner. The active Minecraft.level is never
 * replaced here. This is a prerequisite for camera-time seamless crossing,
 * NOT YET the actual no-respawn transfer.
 */
public final class SeamClientWorldPool {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Set<String> VALID = Set.of(
        "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"
    );
    private static ClientLevel remote;
    private static LevelRenderer remoteRenderer;
    // Ownership is attached to a renderer identity, not source/target:
    // direct handoff exchanges the active and remote LevelRenderers.
    private static final Map<LevelRenderer,SeamOwnedLevelRenderBuffers> OWNED_RENDERERS=
        new IdentityHashMap<>();
    // Vanilla's original renderer does not belong to OWNED_RENDERERS. After
    // a no-respawn handoff it becomes the *foreign* renderer; preserve its
    // existing native buffer identity without taking ownership or closing it.
    private static final Map<LevelRenderer,RenderBuffers> BORROWED_RENDERERS=
        new IdentityHashMap<>();
    private static int dedicatedCreated;
    private static int dedicatedReleased;
    private static int peakDedicatedActive;
    private static String source = "";
    private static String target = "";
    private static boolean failed;
    private static int constructedCount;
    private static long lastReadySent = Long.MIN_VALUE / 2;
    private static int receivedChunkCount;
    private static int remoteViewDistance;
    private static int lastActiveViewDistance;
    private static int nativeHandoffCount;

    private SeamClientWorldPool() {}

    public static void install() {
        NeoForge.EVENT_BUS.addListener(SeamClientWorldPool::tick);
        NeoForge.EVENT_BUS.addListener(
            (ClientPlayerNetworkEvent.LoggingOut e) -> disconnect()
        );
    }

    public static void hint(String from, String to) {
        if (!SeamStackClient.enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getConnection() == null ||
            !mc.level.dimension().location().toString().equals(from) ||
            net.minecraft.resources.ResourceLocation.tryParse(to)==null || to.equals(from)) return;
        if (source.equals(from) && target.equals(to) &&
            (remote != null || failed)) return;
        clear();
        source = from;
        target = to;
        try {
            ResourceKey<Level> dimension =
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(to));
            ResourceKey<DimensionType> typeKey =
                ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.parse(SeamStackClient.dimensionType(to)));
            Holder<DimensionType> type = mc.getConnection().registryAccess()
                .registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(typeKey);

            // NeoForge 1.21.1 constructs a SectionRenderDispatcher from
            // the RenderBuffers passed here. Sharing mc.renderBuffers()
            // means remote chunk compilation can contend with active chunks.
            // Give each newly constructed secondary LevelRenderer its OWN
            // limited, releasable native buffers (only one build pack).
            SeamOwnedLevelRenderBuffers storage=new SeamOwnedLevelRenderBuffers();
            LevelRenderer renderer=null;
            ClientLevel secondary;
            try {
                renderer=new LevelRenderer(
                    mc,mc.getEntityRenderDispatcher(),
                    mc.getBlockEntityRenderDispatcher(),storage.buffers()
                );
                ClientLevel.ClientLevelData data =
                    new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false);
                int viewDistance=SeamNativeViewDistance.normalize(
                    mc.options.renderDistance().get());
                secondary = new ClientLevel(
                    mc.getConnection(), data, dimension, type, viewDistance,
                    mc.level.getServerSimulationDistance(), mc::getProfiler,
                    renderer, mc.level.isDebug(),
                    0L /* biome zoom seed must be supplied by server later */
                );
                renderer.setLevel(secondary);
                renderer.onResourceManagerReload(mc.getResourceManager());
            } catch (Throwable error) {
                try { if(renderer!=null)renderer.close(); }
                finally { storage.release(); }
                throw error;
            }
            OWNED_RENDERERS.put(renderer,storage);
            dedicatedCreated++;
            peakDedicatedActive=Math.max(peakDedicatedActive,OWNED_RENDERERS.size());
            remoteRenderer = renderer;
            remote = secondary;
            remoteViewDistance=SeamNativeViewDistance.normalize(
                mc.options.renderDistance().get());
            failed = false;
            lastReadySent = Long.MIN_VALUE / 2;
            constructedCount++;
            LOG.info("WorldSeam prewarmed real secondary ClientLevel {} from {} with native client viewDistance={} (seam prefetch remains 5x5)",to,from,remoteViewDistance);
        } catch (Throwable error) {
            // Do not change active world or disable the vanilla fallback.
            failed = true;
            LOG.warn("WorldSeam secondary ClientLevel not ready for {}", to, error);
        }
    }

    /**
     * Feed canonical vanilla-format chunk packet content into the *remote*
     * ClientChunkCache, never into the active player's chunk cache.
     * This is real target dimension biome and block data, not placeholder art.
     */
    public static void acceptChunk(SeamChunkPayload message) {
        if (!SeamStackClient.enabled()) return;
        hint(message.source(), message.target());
        Minecraft mc = Minecraft.getInstance();
        if (remote == null || mc.level == null ||
            !mc.level.dimension().location().toString().equals(message.source()) ||
            !target.equals(message.target())) return;
        try {
            var cache = remote.getChunkSource();
            // Keep the whole 5x5 ring resident. Re-centering the cache
            // around EACH arriving edge packet would evict opposite chunks.
            cache.updateViewCenter(
                Math.floorDiv(mc.player.blockPosition().getX(),16),
                Math.floorDiv(mc.player.blockPosition().getZ(),16)
            );
            var data = message.data();
            cache.replaceWithPacketData(message.chunkX(), message.chunkZ(),
                data.getReadBuffer(), data.getHeightmaps(),
                data.getBlockEntitiesTagsConsumer(
                    message.chunkX(), message.chunkZ()
                )
            );
            applyLight(message.chunkX(), message.chunkZ(), message.lighting());
            // The alternate world has a DIFFERENT renderer. Updating its
            // ClientChunkCache alone never queues a GPU mesh rebuild.
            // Notify it for every vertical native chunk section.
            if (remoteRenderer != null) {
                int first = Math.floorDiv(remote.getMinBuildHeight(), 16);
                int last = Math.floorDiv(remote.getMaxBuildHeight() - 1, 16);
                for (int sy = first; sy <= last; sy++)
                    remoteRenderer.setSectionDirtyWithNeighbors(
                        message.chunkX(), sy, message.chunkZ()
                    );
            }
            receivedChunkCount++;
            SeamNativeGeometryClient.onChunk(message,remote);
            maybeReportReady(mc);
        } catch (RuntimeException problem) {
            LOG.warn("WorldSeam rejected redirected remote chunk {},{} in {}",
                message.chunkX(), message.chunkZ(), message.target(), problem);
        }
    }

    private static void applyLight(int cx, int cz,
                                   ClientboundLightUpdatePacketData lighting) {
        var engine = remote.getChunkSource().getLightEngine();
        queueLayers(cx,cz, LightLayer.SKY,
            lighting.getSkyYMask(), lighting.getEmptySkyYMask(),
            lighting.getSkyUpdates());
        queueLayers(cx,cz, LightLayer.BLOCK,
            lighting.getBlockYMask(), lighting.getEmptyBlockYMask(),
            lighting.getBlockUpdates());
        engine.setLightEnabled(new ChunkPos(cx,cz), true);
        engine.runLightUpdates();
    }

    private static void queueLayers(int cx, int cz, LightLayer layer,
                                    BitSet provided, BitSet empty,
                                    List<byte[]> updates) {
        var engine = remote.getChunkSource().getLightEngine();
        int next = 0;
        for (int i=0; i < engine.getLightSectionCount(); i++) {
            boolean has = provided.get(i);
            if (!has && !empty.get(i)) continue;
            DataLayer data;
            if (has) {
                if (next >= updates.size())
                    throw new IllegalArgumentException("Incomplete remote light data");
                byte[] bytes = updates.get(next++);
                if (bytes.length != 2048)
                    throw new IllegalArgumentException("Invalid 2048-byte light nibble array");
                data = new DataLayer(bytes.clone());
            } else {
                data = new DataLayer();
            }
            engine.queueSectionData(layer,
                SectionPos.of(cx, engine.getMinLightSection()+i, cz), data);
        }
        if (next != updates.size())
            throw new IllegalArgumentException("Unused remote light updates");
    }

    private static void maybeReportReady(Minecraft mc) {
        if (!SeamStackClient.enabled()) return;
        if (remote == null || mc.level == null || mc.player == null) return;
        if (remote.getChunkSource().getLoadedChunksCount()
            < SeamReadiness.REQUIRED_CHUNKS) return;
        int cx = Math.floorDiv(mc.player.blockPosition().getX(), 16);
        int cz = Math.floorDiv(mc.player.blockPosition().getZ(), 16);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (!remote.getChunkSource().hasChunk(cx+dx,cz+dz)) return;
        long tick = mc.level.getGameTime();
        if (tick - lastReadySent < 40) return;
        PacketDistributor.sendToServer(new SeamReadyPayload(
            source, target, remote.getChunkSource().getLoadedChunksCount()
        ));
        lastReadySent = tick;
    }

    /**
     * Experimental direct handoff. Only called after a server-side ready
     * handshake and authoritative ServerLevel migration. Keeps both worlds
     * and renderers alive instead of calling Minecraft#setLevel/respawn.
     */
    public static boolean handoff(SeamHandoffPayload instruction) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getConnection() == null ||
            remote == null || remoteRenderer == null ||
            !mc.level.dimension().location().toString().equals(instruction.from()) ||
            !target.equals(instruction.to()) ||
            remote.getChunkSource().getLoadedChunksCount()
                < SeamReadiness.REQUIRED_CHUNKS) {
            LOG.error("WorldSeam cannot hand off to {}: destination native chunks not ready",
                instruction.to());
            return false;
        }
        // Promote the prewarmed ClientLevel into a FULL native Minecraft
        // world. A fixed viewDistance=3 truncates the vanilla 8/12/16+
        // render-distance chunk stream after teleport, causing the
        // user's "every few chunks load again" symptom.
        int nativeDistance=SeamNativeViewDistance.normalize(
            mc.options.renderDistance().get());
        remote.getChunkSource().updateViewRadius(nativeDistance);
        remote.getChunkSource().updateViewCenter(
            Math.floorDiv((int)Math.floor(instruction.x()),16),
            Math.floorDiv((int)Math.floor(instruction.z()),16)
        );
        ClientLevel oldWorld = mc.level;
        LevelRenderer oldRenderer = mc.levelRenderer;
        ClientLevel newWorld = remote;
        LevelRenderer newRenderer = remoteRenderer;
        RenderBuffers oldBuffers=mc.renderBuffers();
        RenderBuffers nextBuffers=buffersForRenderer(newRenderer);
        if(nextBuffers==null) {
            LOG.error("WorldSeam cannot hand off: destination LevelRenderer has no live native buffer owner");
            return false;
        }
        // The original vanilla renderer has no WorldSeam-owned allocation.
        // Preserve its buffers by identity so the *reverse* handoff and
        // foreign actor draws can reuse them without an extra allocation.
        if(!OWNED_RENDERERS.containsKey(oldRenderer))
            BORROWED_RENDERERS.putIfAbsent(oldRenderer,oldBuffers);
        LocalPlayer player = mc.player;

        // A promoted secondary world must not retain a second lightmap
        // once the active GameRenderer takes over its normal lightmap.
        SeamRemoteLightmaps.release();
        oldWorld.removeEntity(player.getId(), Entity.RemovalReason.CHANGED_DIMENSION);
        ((SeamEntityAccess) player).worldseam$unsetRemoved();
        ((SeamEntityAccess) player).worldseam$setLevel(newWorld);
        player.setPos(instruction.x(),instruction.y(),instruction.z());
        player.xo = instruction.x();
        player.yo = instruction.y();
        player.zo = instruction.z();
        player.setYRot(instruction.yaw());
        player.setXRot(instruction.pitch());
        ((SeamClientPlayerAccess) player).worldseam$setClientLevel(newWorld);
        newWorld.addEntity(player);

        ((SeamPacketListenerAccess) mc.getConnection()).worldseam$setLevel(newWorld);
        mc.level = newWorld;
        ((SeamMinecraftAccess) mc).worldseam$setLevelRenderer(newRenderer);
        // The active renderer's CPU/GPU draw queues must follow its world.
        // Previously both fields still referenced the OLD renderer's buffers
        // after the ClientLevel swap, contaminating cross-dimension batches.
        ((SeamMinecraftAccess) mc).worldseam$setRenderBuffers(nextBuffers);
        ((SeamGameRendererAccess) mc.gameRenderer).worldseam$setRenderBuffers(nextBuffers);
        mc.particleEngine.setLevel(newWorld);
        mc.getBlockEntityRenderDispatcher().setLevel(newWorld);

        // Retain the source world as the secondary world for reverse crossings.
        remote = oldWorld;
        remoteRenderer = oldRenderer;
        // Keep the old world cache intact for reverse handoff, but do not
        // redirect vanilla active-world packets into a 5x5-only storage map.
        remoteViewDistance=nativeDistance;
        lastActiveViewDistance=nativeDistance;
        nativeHandoffCount++;
        if(!activeBuffersMatchRenderer())
            throw new IllegalStateException("WorldSeam active LevelRenderer native buffers diverged after handoff");
        LOG.info("WORLDSEAM_NATIVE_HANDOFF_BUFFERS: handoffs={}, activeRendererBuffersMatch=true, reverseRendererBuffersKnown={}",
            nativeHandoffCount,buffersForRenderer(oldRenderer)!=null);
        source = instruction.to();
        target = instruction.from();
        failed = false;
        lastReadySent = Long.MIN_VALUE / 2;
        LOG.info("WORLDSEAM_NATIVE_HANDOFF: activeClientViewRadius={}, nativeChunks={}, previousViewChunks={}, handoffs={}",
            nativeDistance,newWorld.getChunkSource().getLoadedChunksCount(),
            oldWorld.getChunkSource().getLoadedChunksCount(),nativeHandoffCount);
        LOG.info("WorldSeam direct local ClientLevel switch {} -> {} at {} {} {}",
            instruction.from(), instruction.to(),
            instruction.x(),instruction.y(),instruction.z());
        return true;
    }

    /** Read the remote live ClientLevel, never the active player's world. */
    public static boolean remoteBlockIs(String dimension,
                                        net.minecraft.core.BlockPos position,
                                        net.minecraft.world.level.block.state.BlockState expected) {
        if(remote==null || !dimension.equals(target) ||
            !remote.getChunkSource().hasChunk(
                Math.floorDiv(position.getX(),16),
                Math.floorDiv(position.getZ(),16)))return false;
        return remote.getBlockState(position).equals(expected);
    }

    public static String remoteStatus() {
        if (failed) return "PREWARM FAILED (check latest.log)";
        if (remote==null) return "NO TARGET ClientLevel";
        return "TARGET READY CONTEXT "+target;
    }
    public static int receivedChunks() { return receivedChunkCount; }
    public static int nativeViewDistance() { return remoteViewDistance; }
    public static int lastActiveViewDistance() { return lastActiveViewDistance; }
    public static int nativeHandoffCount() { return nativeHandoffCount; }
    public static int remoteWindowChunks() {
        Minecraft mc=Minecraft.getInstance();
        if(remote==null || mc.player==null || mc.level==null)return 0;
        int cx=Math.floorDiv(mc.player.blockPosition().getX(),16);
        int cz=Math.floorDiv(mc.player.blockPosition().getZ(),16);
        int loaded=0;
        for(int dx=-2;dx<=2;dx++)
            for(int dz=-2;dz<=2;dz++)
                if(remote.getChunkSource().hasChunk(cx+dx,cz+dz))loaded++;
        return loaded;
    }

    public static int nearbyChunks() {
        Minecraft mc=Minecraft.getInstance();
        if (remote==null || mc.player==null || mc.level==null) return 0;
        int cx=Math.floorDiv(mc.player.blockPosition().getX(),16);
        int cz=Math.floorDiv(mc.player.blockPosition().getZ(),16);
        int found=0;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
            if(remote.getChunkSource().hasChunk(cx+dx,cz+dz))found++;
        return found;
    }

    /** Only expose the actual secondary ClientLevel for dimension-scoped
     * native entity tracking; never confuse it with Minecraft.level. */
    static ClientLevel remoteFor(String from,String to) {
        Minecraft mc=Minecraft.getInstance();
        return remote!=null && mc.level!=null &&
            mc.level.dimension().location().toString().equals(from) &&
            source.equals(from) && target.equals(to) ? remote : null;
    }

    /** Renderer-specific native buffers, including the borrowed vanilla owner. */
    static RenderBuffers renderBuffersFor(ClientLevel level) {
        return buffersForRenderer(rendererFor(level));
    }

    private static RenderBuffers buffersForRenderer(LevelRenderer renderer) {
        if(renderer==null)return null;
        SeamOwnedLevelRenderBuffers owner=OWNED_RENDERERS.get(renderer);
        if(owner!=null)return owner.released()?null:owner.buffers();
        return BORROWED_RENDERERS.get(renderer);
    }

    /** Actual Minecraft/GameRenderer buffer identity after a direct handoff. */
    public static boolean activeBuffersMatchRenderer() {
        Minecraft mc=Minecraft.getInstance();
        RenderBuffers expected=buffersForRenderer(mc.levelRenderer);
        return expected!=null && mc.renderBuffers()==expected &&
            ((SeamGameRendererAccess)mc.gameRenderer).worldseam$getRenderBuffers()==expected;
    }

    /** Renderer owned by exactly this secondary ClientLevel; never the active one. */
    static LevelRenderer rendererFor(ClientLevel level) {
        return level!=null && level==remote ? remoteRenderer : null;
    }

    public static boolean hasPrewarmed(String destination) {
        return remote != null && target.equals(destination);
    }

    public static int constructedCount() {
        return constructedCount;
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (!source.isEmpty() &&
            !mc.level.dimension().location().toString().equals(source))) clear();
        else {
            // Vanilla's radius setting may change without a dimension
            // respawn. Keep the SECOND ClientChunkCache's storage allocation
            // aligned, so the next no-respawn handoff has sufficient slots.
            int desired=SeamNativeViewDistance.normalize(
                mc.options.renderDistance().get());
            if(remote!=null && desired!=remoteViewDistance) {
                remote.getChunkSource().updateViewRadius(desired);
                remoteViewDistance=desired;
            }
            maybeReportReady(mc);
            if (remoteRenderer != null && remoteRenderer != mc.levelRenderer)
                remoteRenderer.tick();
        }
    }

    public static int dedicatedRendererCreated() { return dedicatedCreated; }
    public static int dedicatedRendererReleased() { return dedicatedReleased; }
    public static int dedicatedRendererActive() { return OWNED_RENDERERS.size(); }
    public static int peakDedicatedRendererActive() { return peakDedicatedActive; }
    public static boolean remoteHasDedicatedBuffers() {
        return remoteRenderer!=null && OWNED_RENDERERS.containsKey(remoteRenderer);
    }
    public static boolean mainHasDedicatedBuffers() {
        return OWNED_RENDERERS.containsKey(Minecraft.getInstance().levelRenderer);
    }

    private static void releaseDedicated(LevelRenderer renderer) {
        SeamOwnedLevelRenderBuffers owner=OWNED_RENDERERS.remove(renderer);
        if(owner!=null) {
            owner.release();
            dedicatedReleased++;
        }
    }

    /** No world will be rendered after logout; reclaim even the active pool. */
    private static void disconnect() {
        clear();
        // clear() must not release the active renderer during normal world
        // operation. Once disconnected, its buffers can be reclaimed too.
        for(var renderer:java.util.List.copyOf(OWNED_RENDERERS.keySet()))
            releaseDedicated(renderer);
        BORROWED_RENDERERS.clear();
    }

    public static void clear() {
        SeamRemoteLightmaps.release();
        SeamRemoteEntityClient.reset();
        if (remoteRenderer != null) {
            LevelRenderer detached=remoteRenderer;
            // During a handoff, a formerly remote renderer can become the
            // ACTIVE Minecraft.levelRenderer. Never tear down its buffers
            // while the player is still viewing that dimension.
            if(detached!=Minecraft.getInstance().levelRenderer) {
                try {
                    detached.setLevel(null);
                    detached.close();
                } finally {
                    releaseDedicated(detached);
                    BORROWED_RENDERERS.remove(detached);
                }
            }
        }
        remoteRenderer = null;
        remote = null;
        source = "";
        target = "";
        failed = false;
        receivedChunkCount=0;
        remoteViewDistance=0;
        lastReadySent = Long.MIN_VALUE / 2;
        SeamNativeGeometryClient.reset();
    }
}
