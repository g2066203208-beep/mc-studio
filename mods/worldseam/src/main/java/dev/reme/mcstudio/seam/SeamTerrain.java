package dev.reme.mcstudio.seam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Modifies ONLY newly-generated chunk data, NEVER old loaded chunks.
 * Optional separate admin toggle defaults OFF even if Seam travel is enabled.
 *
 * Runs in ChunkEvent.Load before FULL promotion, therefore touches only the
 * event's ChunkAccess and no neighbor chunks, level chunk access or block events.
 */
public final class SeamTerrain {
    private SeamTerrain() {}

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)
            || !SeamServer.shouldCarveNewChunk(level.getServer())) return;
        ChunkAccess chunk = event.getChunk();
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
