package dev.reme.mcstudio.climb;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Collision-checked PEAK style mantle over the edge of a *static* solid wall.
 * This is real movement through Player.setDeltaMovement, not a pose-only
 * animation or a teleport. Dynamic Sable ship mantles are deliberately disabled
 * until moving-platform collision validation is available.
 */
public final class PeakLedgeAssist {
    public static final int DURATION = 12;
    private PeakLedgeAssist() {}

    public static boolean start(Player player, ClimbState state, GripAnchor.Contact contact) {
        if (state.left == null || state.left.dynamic() || state.mantleTicks > 0
                || state.forward < 0.55f || Math.abs(contact.normal().y) > 0.3) return false;
        BlockPos gripBlock = state.left.block();
        BlockPos spaceAbove = gripBlock.above();
        if (!player.level().getBlockState(spaceAbove).getCollisionShape(player.level(), spaceAbove).isEmpty()
                || !player.level().getBlockState(spaceAbove.above())
                        .getCollisionShape(player.level(), spaceAbove.above()).isEmpty()) return false;

        double top = gripBlock.getY() + 1.0;
        double feet = player.getY();
        // Don't skip past the wall or begin pulling up from the bottom of a tower.
        if (feet < top - 1.15 || feet > top + 0.15) return false;

        Vec3 inward = new Vec3(-contact.normal().x, 0, -contact.normal().z);
        if (inward.lengthSqr() < 0.25) return false;
        inward = inward.normalize();
        AABB target = player.getBoundingBox()
                .move(inward.scale(0.65)).move(0, top + 0.06 - feet, 0);
        if (!player.level().noCollision(player, target)) return false;

        state.mantleTicks = DURATION;
        state.mantleTargetY = top + 0.06;
        state.mantleInward = inward;
        state.poseAction = 3;
        return true;
    }

    /** Called once per player tick while the transition is active. */
    public static void tick(Player player, ClimbState state) {
        if (state.mantleTicks <= 0) return;
        // If the supporting block disappears, do not continue rising magically.
        if (state.left == null || state.left.resolve(player.level()) == null) {
            state.release();
            return;
        }
        int elapsed = DURATION - state.mantleTicks;
        double remainingHeight = Math.max(0, state.mantleTargetY - player.getY());
        double up = Math.min(0.27, Math.max(0.0, remainingHeight * 0.65));
        double across = elapsed >= 6 || remainingHeight < 0.16 ? 0.16 : 0.04;
        Vec3 desired = state.mantleInward.scale(across).add(0, up, 0);

        // Still use normal collision on each tick; no noClip or teleport bypass.
        player.setDeltaMovement(desired);
        player.fallDistance = 0;
        player.hurtMarked = true;
        state.poseAction = 3;
        state.peakPhase += 0.15f;
        state.mantleTicks--;
        if (state.mantleTicks <= 0) {
            state.mantleRequiresRelease = true;
            state.release();
            // RMB may still be held. The client continues to send hold intent,
            // but the player will now be standing on the ledge.
        }
    }
}
