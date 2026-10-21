package dev.reme.worldseam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
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

    /**
     * EXPLICIT destructive test-world repair of one existing 16x16 column,
     * synchronously on server thread with vanilla block change/light updates.
     * Called only from the admin-only /worldseam setup-test command.
     */
    public static int openTestColumn(MinecraftServer server, int x, int z) {
        int changed = 0;
        for (var key : java.util.List.of(Level.OVERWORLD, Level.NETHER)) {
            ServerLevel world = server.getLevel(key);
            if (world == null) continue;
            var range = SeamBounds.newChunkCarveRange(
                world.dimension().location().toString(), world.getMinBuildHeight()
            );
            if (range == null) continue;
            int cx = Math.floorDiv(x,16);
            int cz = Math.floorDiv(z,16);
            world.getChunk(cx,cz); // exactly one column per dimension
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int baseX = cx << 4;
            int baseZ = cz << 4;
            for (int y=range.minY(); y<=range.maxY(); y++) {
                for (int dz=0;dz<16;dz++) for(int dx=0;dx<16;dx++) {
                    pos.set(baseX+dx,y,baseZ+dz);
                    if (world.getBlockState(pos).is(Blocks.BEDROCK)) {
                        if (world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3))
                            changed++;
                    }
                }
            }
        }
        return changed;
    }

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
