package dev.reme.worldseam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Own bounded streaming layer: preview only near a vertical seam.
 * - 13 x 13 x 12 near-boundary samples (2028 maximum)
 * - a full frame on first view / scope change
 * - block + light deltas at 4 Hz, idle heartbeat every 2 seconds
 * - one synchronous *new* destination chunk load per preview pass
 * - all player caches disposed when world/server changes
 */
public final class SeamPreviewSender {
    private static final int RADIUS = 6;
    private static final int DEPTH = 12;
    private static final int HORIZON_RADIUS = 9;
    private static final int HORIZON_DEPTH = 320;
    private static final int UPDATE_TICKS = 5;
    private static final int HEARTBEAT_TICKS = 40;
    private static final double VIEW_DISTANCE_TO_SEAM = 32.0;

    private record Session(String dimension, String scope,
                           Map<SeamFrameState.Position, SeamFrameState.Visual> blocks,
                           Map<SeamFrameState.Position, SeamFrameState.Visual> horizon) {}
    private static final Map<UUID, Session> sessions = new HashMap<>();
    private static final Map<UUID, Long> sequences = new HashMap<>();
    private static MinecraftServer lastServer;

    private SeamPreviewSender() {}

    public static void tick(MinecraftServer server, ServerPlayer player, long tick, boolean enabled) {
        if (server != lastServer) {
            lastServer = server;
            sessions.clear();
            sequences.clear();
        }
        if (tick % UPDATE_TICKS != 0 && enabled) return;

        UUID id = player.getUUID();
        if (!enabled) {
            // A disabled world must actively clear any previously rendered view.
            if (sessions.remove(id) != null) {
                transmit(player, id, new SeamFrameState.Frame(
                    player.serverLevel().dimension().location().toString(),
                    "", nextRevision(id), true, java.util.List.of()
                ));
            }
            return;
        }
        ServerLevel source = player.serverLevel();
        String dimension = source.dimension().location().toString();
        SeamGraph.Link selected = null;
        for (SeamGraph.Link link : SeamGraph.from(dimension)) {
            if (link.near(player.getY(), source.getMinBuildHeight(),
                SeamBounds.top(source), VIEW_DISTANCE_TO_SEAM)) {
                selected = link;
                break;
            }
        }
        if (selected == null) {
            if (sessions.remove(id) != null) {
                transmit(player, id, new SeamFrameState.Frame(
                    dimension, "", nextRevision(id), true, java.util.List.of()
                ));
            }
            return;
        }
        ServerLevel target = server.getLevel(SeamServer.dimensionKey(selected.target()));
        if (target == null) return;
        // Quantization prevents re-sending the whole cube every time the
        // player moves by just one block.
        int cx = Math.floorDiv(player.blockPosition().getX(), 8) * 8 + 4;
        int cz = Math.floorDiv(player.blockPosition().getZ(), 8) * 8 + 4;
        String scope = selected.target() + ":" + selected.exit() + ":" + cx + ":" + cz;
        Session previous = sessions.get(id);
        boolean full = previous == null
            || !previous.scope().equals(scope)
            || !previous.dimension().equals(dimension);
        Map<SeamFrameState.Position, SeamFrameState.Visual> now =
            sample(source, target, selected, cx, cz);
        // Far-field foveation: one genuine terrain surface per 19x19
        // column, up to 320 blocks past the seam, streamed at 1 Hz.
        // This complements near-field volume rather than generating fake land.
        Map<SeamFrameState.Position, SeamFrameState.Visual> horizon =
            (full || tick % 20 == 0)
                ? sampleHorizon(source, target, selected, cx, cz)
                : previous.horizon();
        horizon.forEach(now::putIfAbsent);
        var changes = full
            ? SeamFrameState.complete(now)
            : SeamFrameState.difference(previous.blocks(), now);

        sessions.put(id, new Session(dimension, scope,
            Map.copyOf(now), Map.copyOf(horizon)));
        if (changes.isEmpty() && !full && tick % HEARTBEAT_TICKS != 0) return;
        transmit(player, id, new SeamFrameState.Frame(
            dimension, scope, nextRevision(id), full, changes
        ));
    }

    private static Map<SeamFrameState.Position, SeamFrameState.Visual> sample(
        ServerLevel source, ServerLevel target, SeamGraph.Link link, int cx, int cz
    ) {
        Map<SeamFrameState.Position, SeamFrameState.Visual> result = new HashMap<>();
        int sourceBound = (int) SeamGraph.boundaryY(link.exit(),
            source.getMinBuildHeight(), SeamBounds.top(source));
        int targetBound = (int) SeamGraph.boundaryY(link.entry(),
            target.getMinBuildHeight(), SeamBounds.top(target));
        int forcedLoads = 0;
        Set<Long> visitedChunks = new HashSet<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = cx - RADIUS; x <= cx + RADIUS; x++) {
            for (int z = cz - RADIUS; z <= cz + RADIUS; z++) {
                int chunkX = Math.floorDiv(x, 16);
                int chunkZ = Math.floorDiv(z, 16);
                long chunkKey = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
                if (visitedChunks.add(chunkKey) && !target.hasChunk(chunkX, chunkZ)
                    && forcedLoads++ < 1) {
                    // Bounded warm-up. Avoid synchronous chunk generation for
                    // every sampled column on the same tick.
                    target.getChunkAt(new BlockPos(x, targetBound - 1, z));
                }
                if (!target.hasChunk(chunkX, chunkZ)) continue;
                for (int depth = 0; depth < DEPTH; depth++) {
                    int remoteY = link.entry() == SeamGraph.EdgeSide.TOP
                        ? targetBound - 1 - depth : targetBound + depth;
                    if (remoteY < target.getMinBuildHeight()
                        || remoteY >= target.getMaxBuildHeight()) continue;
                    int visibleY = link.exit() == SeamGraph.EdgeSide.TOP
                        ? sourceBound + depth : sourceBound - 1 - depth;
                    pos.set(x, remoteY, z);
                    BlockState state = target.getBlockState(pos);
                    if (state.isAir()) continue;
                    // Low nibble = block brightness, high nibble = sky.
                    int light = target.getBrightness(LightLayer.BLOCK, pos)
                        | (target.getBrightness(LightLayer.SKY, pos) << 4);
                    result.put(new SeamFrameState.Position(x, visibleY, z),
                        new SeamFrameState.Visual(Block.getId(state), light));
                }
            }
        }
        return result;
    }

    /**
     * Adaptive distant surface LOD. Above another world's ceiling, use the
     * populated heightmap (e.g. Overworld terrain lies ~250 blocks below its
     * world-height seam). Looking into a world's floor, use a bounded upward
     * search (the End's islands can start dozens of blocks above minY).
     *
     * No invented terrain: every point comes from an actual target block.
     * This does NOT attempt fluid meshes, chunk occlusion, or atmospheric fog.
     */
    private static Map<SeamFrameState.Position, SeamFrameState.Visual> sampleHorizon(
        ServerLevel source, ServerLevel target, SeamGraph.Link link, int cx, int cz
    ) {
        Map<SeamFrameState.Position, SeamFrameState.Visual> surface = new HashMap<>();
        int sourceBound = (int) SeamGraph.boundaryY(link.exit(),
            source.getMinBuildHeight(), SeamBounds.top(source));
        int targetBound = (int) SeamGraph.boundaryY(link.entry(),
            target.getMinBuildHeight(), SeamBounds.top(target));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = cx - HORIZON_RADIUS; x <= cx + HORIZON_RADIUS; x++) {
            for (int z = cz - HORIZON_RADIUS; z <= cz + HORIZON_RADIUS; z++) {
                // Far LOD must never synchronously generate missing chunks.
                if (!target.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) continue;
                int y;
                if (link.entry() == SeamGraph.EdgeSide.TOP) {
                    // One heightmap lookup rather than up to 320 getBlockState calls.
                    y = Math.min(targetBound - 1,
                        target.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                } else {
                    y = target.getMinBuildHeight();
                    int limit = Math.min(target.getMaxBuildHeight() - 1,
                        targetBound + HORIZON_DEPTH - 1);
                    while (y <= limit) {
                        pos.set(x, y, z);
                        if (!target.getBlockState(pos).isAir()) break;
                        y++;
                    }
                    if (y > limit) continue;
                }
                int depth = link.entry() == SeamGraph.EdgeSide.TOP
                    ? targetBound - 1 - y : y - targetBound;
                if (depth < DEPTH || depth >= HORIZON_DEPTH
                    || y < target.getMinBuildHeight()
                    || y >= target.getMaxBuildHeight()) continue;
                pos.set(x, y, z);
                BlockState block = target.getBlockState(pos);
                // Water/lava and empty heightmap cells cannot yet be meshed.
                if (block.isAir() || block.getRenderShape() != RenderShape.MODEL) continue;
                int previewY = link.exit() == SeamGraph.EdgeSide.TOP
                    ? sourceBound + depth : sourceBound - 1 - depth;
                int light = target.getBrightness(LightLayer.BLOCK, pos)
                    | (target.getBrightness(LightLayer.SKY, pos) << 4);
                surface.put(new SeamFrameState.Position(x, previewY, z),
                    new SeamFrameState.Visual(Block.getId(block), light));
            }
        }
        return surface;
    }

    private static long nextRevision(UUID id) {
        return sequences.merge(id, 1L, Long::sum);
    }

    private static void transmit(
        ServerPlayer player, UUID id, SeamFrameState.Frame frame
    ) {
        PacketDistributor.sendToPlayer(player, new SeamPreviewPayload(frame));
    }

    public static void prune(Set<UUID> players) {
        sessions.keySet().retainAll(players);
        sequences.keySet().retainAll(players);
    }
}
