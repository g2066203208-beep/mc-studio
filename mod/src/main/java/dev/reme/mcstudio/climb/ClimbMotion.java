package dev.reme.mcstudio.climb;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Initial non-teleport grip controller. The server is authoritative; the local
 * client runs the same bounded movement step for responsive camera prediction.
 *
 * This is an experimental constraint controller, NOT a fully simulated Rapier body.
 */
public final class ClimbMotion {
    private static final double ARM_LENGTH = GripRules.MAX_EXTENSION;
    private static final double MAX_SPEED = 0.33;
    private static final double MAX_ANCHOR_TRAVEL_PER_TICK = 1.5;
    private ClimbMotion() {}

    public static void tick(Player player, ClimbState s) {
        if (!s.enabled || !s.gripping() || !player.isAlive() || player.isPassenger()
                || player.getAbilities().flying || player.isSpectator()) return;
        if (!player.level().isClientSide() && player.level().getGameTime() - s.lastInputTick > 20) {
            s.release(); s.enabled = false; return;
        }

        GripAnchor.Contact a = s.left == null ? null : s.left.resolve(player.level());
        GripAnchor.Contact b = s.right == null ? null : s.right.resolve(player.level());
        if (a == null) { s.left = null; s.lastLeft = Vec3.ZERO; }
        if (b == null) { s.right = null; s.lastRight = Vec3.ZERO; }
        // A teleport / structure desync must not leave an infinite-range tether.
        if (a != null && player.getEyePosition().distanceToSqr(a.point()) > 36) {
            s.left = null; a = null;
        }
        if (b != null && player.getEyePosition().distanceToSqr(b.point()) > 36) {
            s.right = null; b = null;
        }
        if (a == null && b == null) return;

        Vec3 movedByStructure = Vec3.ZERO;
        int movingAnchors = 0;
        if (a != null) {
            if (s.lastLeft != Vec3.ZERO) {
                Vec3 motion = a.point().subtract(s.lastLeft);
                if (GripRules.snapTooFar(motion.length())) {
                    s.left = null; a = null;
                } else { movedByStructure = movedByStructure.add(motion); movingAnchors++; }
            }
            if (a != null) s.lastLeft = a.point();
        }
        if (b != null) {
            if (s.lastRight != Vec3.ZERO) {
                Vec3 motion = b.point().subtract(s.lastRight);
                if (motion.length() > MAX_ANCHOR_TRAVEL_PER_TICK) {
                    s.right = null; b = null;
                } else { movedByStructure = movedByStructure.add(motion); movingAnchors++; }
            }
            if (b != null) s.lastRight = b.point();
        }
        if (a == null && b == null) return;
        if (movingAnchors > 0) movedByStructure = movedByStructure.scale(1.0 / movingAnchors);

        Vec3 normal = Vec3.ZERO;
        if (a != null) normal = normal.add(a.normal());
        if (b != null) normal = normal.add(b.normal());
        normal = normal.lengthSqr() < 0.1 ? new Vec3(0, 1, 0) : normal.normalize();

        Vec3 wish = desiredTangent(normal, player.getYRot(), s.forward, s.strafe);

        Vec3 old = player.getDeltaMovement();
        // Retain a little gravity while the hand constraints carry the player's weight.
        Vec3 velocity = old.scale(0.65).add(wish.scale(0.12))
                .add(0, -0.046, 0).add(movedByStructure);

        // A grip acts as a unilateral maximum-length constraint, not free flight.
        Vec3 shoulder = player.getEyePosition().add(0, -0.22, 0);
        if (a != null) velocity = constrain(velocity, a.point().subtract(shoulder));
        if (b != null) velocity = constrain(velocity, b.point().subtract(shoulder));
        double speed = velocity.length();
        if (speed > GripRules.allowedSpeed(movedByStructure.length())) {
            velocity = velocity.scale(GripRules.allowedSpeed(movedByStructure.length()) / speed);
        }
        player.setDeltaMovement(velocity);
        // A held grip prevents accumulated landing fall damage, not gravity itself.
        player.fallDistance = 0;
        player.hurtMarked = true;
    }

    /**
     * Maps controls onto a physical surface, not the forward-looking ray.
     * On a vertical wall W means ascend (even looking straight at the wall);
     * on the floor and ceiling W means travel toward the horizontal view heading.
     */
    public static Vec3 desiredTangent(Vec3 surfaceNormal, float yawDegrees,
                                       float forwardAmount, float strafeAmount) {
        Vec3 n = surfaceNormal.normalize();
        double yaw = Math.toRadians(yawDegrees);
        Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 horizontalForward = projectTangent(facing, n);
        Vec3 verticalForward = projectTangent(new Vec3(0, 1, 0), n);
        Vec3 along = Math.abs(n.y) < 0.70 && verticalForward.lengthSqr() > 0.01
                ? verticalForward.normalize() : horizontalForward.normalize();
        Vec3 across = projectTangent(right, n);
        if (across.lengthSqr() < 0.0001) across = n.cross(along);
        if (across.lengthSqr() > 0.0001) across = across.normalize();
        Vec3 wish = along.scale(forwardAmount).add(across.scale(strafeAmount));
        return wish.lengthSqr() > 1 ? wish.normalize() : wish;
    }

    private static Vec3 projectTangent(Vec3 movement, Vec3 normal) {
        return movement.subtract(normal.scale(movement.dot(normal)));
    }

    private static Vec3 constrain(Vec3 velocity, Vec3 toAnchor) {
        double distance = toAnchor.length();
        if (distance < 1.0e-5) return velocity;
        if (distance > ARM_LENGTH) {
            Vec3 inward = toAnchor.scale(1.0 / distance);
            // Subtract outward velocity to stop a hand from extending forever.
            double outward = -velocity.dot(inward);
            if (outward > 0) velocity = velocity.add(inward.scale(outward));
            return velocity.add(inward.scale(GripRules.inwardCorrection(distance)));
        }
        return velocity;
    }
}
