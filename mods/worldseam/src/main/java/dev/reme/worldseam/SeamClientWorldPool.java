package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Holder;
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
                    mc.level.getBiomeManager().biomeZoomSeed
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
        } catch (RuntimeException problem) {
            LOG.warn("WorldSeam rejected redirected remote chunk {},{} in {}",
                message.chunkX(), message.chunkZ(), message.target(), problem);
        }
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
    }
}
