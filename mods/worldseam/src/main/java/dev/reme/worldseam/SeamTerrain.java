package dev.reme.worldseam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Bedrock is a world-generation issue, not a client teleport issue.
 *
 * Dimension Stack clears bedrock at chunk-generation stages. WorldSeam
 * independently does the same for its exact connection bands, if and
 * only if the test-save owner opted into terrain modification.
 *
 * The generation hook runs after vanilla feature placement. The existing
 * new-chunk event remains a compatibility fallback. Existing chunks are
 * never automatically modified on load.
 */
public final class SeamTerrain {
    private SeamTerrain() {}

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level))
            return;
        replaceGeneratedBedrock(level, event.getChunk());
    }

    public static void replaceGeneratedBedrock(ServerLevel level, ChunkAccess chunk) {
        if (!SeamServer.shouldCarveNewChunk(level.getServer())) return;
        var range = SeamBounds.newChunkCarveRange(
            level.dimension().location().toString(), level.getMinBuildHeight()
        );
        if (range == null) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int ox = chunk.getPos().getMinBlockX();
        int oz = chunk.getPos().getMinBlockZ();
        for (int y = range.minY(); y <= range.maxY(); y++) {
            if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) continue;
            for (int z=0; z<16; z++) for (int x=0; x<16; x++) {
                pos.set(ox+x,y,oz+z);
                if (chunk.getBlockState(pos).is(Blocks.BEDROCK)) {
                    chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                }
            }
        }
    }
}
