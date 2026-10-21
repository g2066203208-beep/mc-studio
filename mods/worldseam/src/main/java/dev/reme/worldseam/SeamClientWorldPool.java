package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.reme.worldseam.mixin.SeamMinecraftAccess;
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
    private static String source = "";
    private static String target = "";
    private static boolean failed;
    private static int constructedCount;
    private static long lastReadySent = Long.MIN_VALUE / 2;

    private SeamClientWorldPool() {}

    public static void install() {
        NeoForge.EVENT_BUS.addListener(SeamClientWorldPool::tick);
        NeoForge.EVENT_BUS.addListener(
            (ClientPlayerNetworkEvent.LoggingOut e) -> clear()
        );
    }

    public static void hint(String from, String to) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getConnection() == null ||
            !mc.level.dimension().location().toString().equals(from) ||
            !VALID.contains(to) || to.equals(from)) return;
        if (source.equals(from) && target.equals(to) &&
            (remote != null || failed)) return;
        clear();
        source = from;
        target = to;
        try {
            ResourceKey<Level> dimension =
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(to));
            ResourceKey<DimensionType> typeKey =
                ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.parse(to));
            Holder<DimensionType> type = mc.getConnection().registryAccess()
                .registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(typeKey);

            LevelRenderer renderer = new LevelRenderer(
                mc, mc.getEntityRenderDispatcher(),
                mc.getBlockEntityRenderDispatcher(), mc.renderBuffers()
            );
            ClientLevel secondary;
            try {
                ClientLevel.ClientLevelData data =
                    new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false);
                secondary = new ClientLevel(
                    mc.getConnection(), data, dimension, type, 3,
                    mc.level.getServerSimulationDistance(), mc::getProfiler,
                    renderer, mc.level.isDebug(),
                    0L /* biome zoom seed must be supplied by server later */
                );
                renderer.setLevel(secondary);
                renderer.onResourceManagerReload(mc.getResourceManager());
            } catch (Throwable error) {
                renderer.close();
                throw error;
            }
            remoteRenderer = renderer;
            remote = secondary;
            failed = false;
            lastReadySent = Long.MIN_VALUE / 2;
            constructedCount++;
            LOG.info("WorldSeam prewarmed real secondary ClientLevel {} from {}", to, from);
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
        hint(message.source(), message.target());
        Minecraft mc = Minecraft.getInstance();
        if (remote == null || mc.level == null ||
            !mc.level.dimension().location().toString().equals(message.source()) ||
            !target.equals(message.target())) return;
        try {
            var cache = remote.getChunkSource();
            cache.updateViewCenter(message.chunkX(), message.chunkZ());
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
        ClientLevel oldWorld = mc.level;
        LevelRenderer oldRenderer = mc.levelRenderer;
        ClientLevel newWorld = remote;
        LevelRenderer newRenderer = remoteRenderer;
        LocalPlayer player = mc.player;

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
        mc.particleEngine.setLevel(newWorld);
        mc.getBlockEntityRenderDispatcher().setLevel(newWorld);

        // Retain the source world as the secondary world for reverse crossings.
        remote = oldWorld;
        remoteRenderer = oldRenderer;
        source = instruction.to();
        target = instruction.from();
        failed = false;
        lastReadySent = Long.MIN_VALUE / 2;
        LOG.info("WorldSeam direct local ClientLevel switch {} -> {} at {} {} {}",
            instruction.from(), instruction.to(),
            instruction.x(),instruction.y(),instruction.z());
        return true;
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
            maybeReportReady(mc);
            if (remoteRenderer != null && remoteRenderer != mc.levelRenderer)
                remoteRenderer.tick();
        }
    }

    public static void clear() {
        if (remoteRenderer != null) {
            remoteRenderer.setLevel(null);
            remoteRenderer.close();
        }
        remoteRenderer = null;
        remote = null;
        source = "";
        target = "";
        failed = false;
        lastReadySent = Long.MIN_VALUE / 2;
    }
}
