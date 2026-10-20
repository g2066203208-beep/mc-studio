package dev.reme.mcstudio.climb;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Short, collision-checked ledge pull-up. Moves up FIRST, inward only when
 * the feet clear the solid wall. Disallows displacement teleports and retries.
 * Moving Sable structures require a separate future swept-collision solver.
 */
public final class PeakLedgeAssist {
    public static final int DURATION = 14;
    public static final double MAX_MANTLE_STEP = 0.225;
    private PeakLedgeAssist() {}

    public static boolean start(Player player, ClimbState state, GripAnchor.Contact contact) {
        if (state.left == null || state.left.dynamic() || state.mantleTicks > 0
                || state.forward < .55f || Math.abs(contact.normal().y) > .30) return false;
        BlockPos block = state.left.block();
        BlockPos above = block.above();
        if (!player.level().getBlockState(above).getCollisionShape(player.level(), above).isEmpty()
                || !player.level().getBlockState(above.above())
                    .getCollisionShape(player.level(), above.above()).isEmpty()) return false;
        double top = block.getY() + 1.0;
        double feet = player.getY();
        if (feet < top - 1.10 || feet > top + .12) return false;
        Vec3 inward = new Vec3(-contact.normal().x, 0, -contact.normal().z);
        if (inward.lengthSqr() < .25) return false;
        inward = inward.normalize();
        AABB destination = player.getBoundingBox()
                .move(inward.scale(.60)).move(0, top + .08 - feet, 0);
        if (!player.level().noCollision(player, destination)) return false;

        state.mantleOrigin = player.position();
        state.mantleTicks = DURATION;
        state.mantleTargetY = top + .08;
        state.mantleInward = inward;
        state.poseAction = 3;
        return true;
    }

    private static void abort(ClimbState state) {
        state.mantleRequiresRelease = true;
        state.release();
    }

    public static void tick(Player player, ClimbState state) {
        if (state.mantleTicks <= 0) return;
        if (state.left == null || state.left.resolve(player.level()) == null
                || player.position().distanceToSqr(state.mantleOrigin) > 2.45*2.45) {
            abort(state);
            return;
        }
        double heightLeft = Math.max(0, state.mantleTargetY - player.getY());
        double rise = Math.min(.19, heightLeft * .52);
        // The previous code pushed horizontally while the feet were below the
        // ledge, which could launch the player out or into a wall.
        double inward = player.getY() >= state.mantleTargetY - .11 ? .095 : 0.0;
        Vec3 wanted = new Vec3(state.mantleInward.x*inward, rise,
                state.mantleInward.z*inward);
        wanted = ClimbSurfaceMath.clamp(wanted, MAX_MANTLE_STEP);

        if (!player.level().noCollision(player, player.getBoundingBox().move(wanted))) {
            Vec3 verticalOnly = new Vec3(0, rise, 0);
            if (rise > .0001
                    && player.level().noCollision(player, player.getBoundingBox().move(verticalOnly))) {
                wanted = verticalOnly;
            } else {
                abort(state);
                return;
            }
        }
        player.setDeltaMovement(wanted);
        player.fallDistance = 0;
        player.hurtMarked = true;
        state.poseAction = 3;
        state.peakPhase += .12f;
        state.mantleTicks--;
        if (state.mantleTicks <= 0) {
            state.mantleRequiresRelease = true;
            state.release();
        }
    }
}
