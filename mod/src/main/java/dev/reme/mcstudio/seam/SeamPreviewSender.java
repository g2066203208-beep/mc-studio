package dev.reme.mcstudio.seam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Narrow proof-of-concept of viewing blocks that exist in a different
 * dimension without installing the client's full remote level renderer.
 *
 * Bounded by distance-to-edge, 6 block horizontal radius, 8 vertical layers,
 * and one payload every 20 server ticks per player.
 */
public final class SeamPreviewSender {
    private static final int RADIUS = 6;
    private static final int DEPTH = 8;
    private static final double VIEW_DISTANCE_TO_SEAM = 24.0;

    private SeamPreviewSender() {}

    public static void tick(MinecraftServer server, ServerPlayer player, long tick) {
        if (tick % 20 != 0) return;

        ServerLevel source = player.serverLevel();
        String dimension = source.dimension().location().toString();
        List<SeamPreviewPayload.BlockSample> samples = new ArrayList<>();

        for (SeamGraph.Link link : SeamGraph.from(dimension)) {
            if (!link.near(player.getY(), source.getMinBuildHeight(),
                source.getMaxBuildHeight(), VIEW_DISTANCE_TO_SEAM)) {
                continue;
            }
            ServerLevel target = server.getLevel(SeamServer.dimensionKey(link.target()));
            if (target == null) continue;

            int sourceBound = (int) SeamGraph.boundaryY(link.exit(),
                source.getMinBuildHeight(), source.getMaxBuildHeight());
            int targetBound = (int) SeamGraph.boundaryY(link.entry(),
                target.getMinBuildHeight(), target.getMaxBuildHeight());

            int centerX = player.blockPosition().getX();
            int centerZ = player.blockPosition().getZ();

            for (int x = centerX - RADIUS; x <= centerX + RADIUS; x++) {
                for (int z = centerZ - RADIUS; z <= centerZ + RADIUS; z++) {
                    // Snapshots of remote chunks explicitly loaded on the
                    // server. This is bounded; not a permanent chunk ticket.
                    target.getChunkAt(new BlockPos(x, targetBound - 1, z));

                    for (int depth = 0; depth < DEPTH; depth++) {
                        int remoteY = link.entry() == SeamGraph.EdgeSide.TOP
                            ? targetBound - 1 - depth : targetBound + depth;
                        int visibleY = link.exit() == SeamGraph.EdgeSide.TOP
                            ? sourceBound + depth : sourceBound - 1 - depth;
                        BlockPos remotePos = new BlockPos(x, remoteY, z);
                        BlockState state = target.getBlockState(remotePos);
                        if (state.isAir()) continue;

                        samples.add(new SeamPreviewPayload.BlockSample(
                            x, visibleY, z, Block.getId(state)
                        ));
                        if (samples.size() == SeamPreviewPayload.MAX_BLOCKS) break;
                    }
                    if (samples.size() == SeamPreviewPayload.MAX_BLOCKS) break;
                }
                if (samples.size() == SeamPreviewPayload.MAX_BLOCKS) break;
            }
            // At most one boundary per update.
            break;
        }

        // Empty payload clears a previously visible boundary as the player leaves.
        PacketDistributor.sendToPlayer(player, new SeamPreviewPayload(dimension, List.copyOf(samples)));
    }
}
