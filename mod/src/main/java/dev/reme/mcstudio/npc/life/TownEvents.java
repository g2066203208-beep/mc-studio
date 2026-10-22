package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Physical audiovisual town events, not chat-only or fictional effects.
 * A real nearby Minecraft bell rings when local guards actually accept an
 * eyewitness complaint. A real tuned note block plays at a weekly town fair.
 * Both honor existing blocks / NeoForge events and never create new terrain.
 */
public final class TownEvents {
    private TownEvents() {}

    /** Real block entity swing/sound, NOT a synthetic siren or global alarm. */
    public static boolean ringAlarm(ServerLevel level, BlockPos reportedAt) {
        if (!level.hasChunkAt(reportedAt)) return false;
        BlockPos best = null;
        int nearest = Integer.MAX_VALUE;
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -6; dx <= 6; dx++)
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos candidate = reportedAt.offset(dx, dy, dz);
                    if (!level.hasChunkAt(candidate) ||
                        !(level.getBlockState(candidate).getBlock() instanceof BellBlock))
                        continue;
                    int dist = dx*dx + dz*dz + dy*dy;
                    if (dist < nearest) { best = candidate.immutable(); nearest = dist; }
                }
        if (best == null) return false;
        BellBlock bell = (BellBlock) level.getBlockState(best).getBlock();
        // This is the vanilla attemptToRing API: updates real BellBlockEntity,
        // broadcasts genuine bell animation and audible village bell sound.
        return bell.attemptToRing(level, best, null);
    }

    /**
     * Calls native Level.blockEvent for a PLAYER-PLACED note block near a
     * physical stall, keeping its current instrument/pitch and NeoForge's
     * cancellation hooks; no fake inventory or sound-only illusion.
     */
    public static boolean playFairNote(PlayerNpcEntity visitor,
                                       PlayerNpcEntity host, ServerLevel level) {
        if (visitor == host || !visitor.isAlive() || !host.isAlive()
            || visitor.distanceToSqr(host) > 16.0
            || !visitor.identity().region().equals(host.identity().region()))
            return false;
        BlockPos center = host.workPos();
        if (!level.hasChunkAt(center)) return false;
        for (int dy = -1; dy <= 2; dy++)
            for (int dx = -4; dx <= 4; dx++)
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()))
                        continue;
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(Blocks.NOTE_BLOCK) ||
                        !level.getBlockState(pos.above()).isAir())
                        continue;
                    level.blockEvent(pos, Blocks.NOTE_BLOCK, 0, 0);
                    return true;
                }
        return false;
    }
}
