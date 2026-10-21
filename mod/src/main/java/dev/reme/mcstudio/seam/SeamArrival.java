package dev.reme.mcstudio.seam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Do not materialize a player or animal inside old roof/floor bedrock. */
public final class SeamArrival {
    private SeamArrival() {}

    public static boolean hasClearance(ServerLevel level, BlockPos feet, Entity entity) {
        int height = Math.max(1, (int) Math.ceil(entity.getBbHeight()));
        for (int i = 0; i < height; i++) {
            BlockPos pos = feet.above(i);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())
                return false;
        }
        return true;
    }
}
