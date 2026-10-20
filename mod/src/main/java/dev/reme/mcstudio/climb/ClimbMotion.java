package dev.reme.mcstudio.climb;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Surface driven movement. Camera pitch/yaw no longer steer the body when
 * hanging: the surface normal and grab heading supply the entire tangent basis.
 */
public final class ClimbMotion {
    private static final double MAX_GRAB_REACH = 2.90;
    private static final double MAX_REGRIP_JUMP = 1.36;
    private ClimbMotion() {}

    public static void tick(Player player, ClimbState s) {
        if (s.lungeCooldown > 0) s.lungeCooldown--;
        if (player.isPassenger() || player.isSpectator() || !player.isAlive()
                || player.getAbilities().flying) {
            s.peakHeld = false; s.release(); return;
        }
        if (!player.level().isClientSide() && s.peakHeld
                && player.level().getGameTime() - s.lastInputTick > 12) {
            s.peakHeld = false; s.release(); return;
        }
        if (!s.peakHeld) {
            if (s.enabled) s.release();
            return;
        }
        if (s.mantleTicks > 0) {
            PeakLedgeAssist.tick(player, s);
            return;
        }
        if (s.mantleRequiresRelease) return;

        if (!s.enabled || s.left == null) {
            GripAnchor candidate = SurfaceProbe.trace(player);
            if (!PeakGripRules.mayGrab(player, candidate)) return;
            GripAnchor.Contact c = candidate.resolve(player.level());
            if (c == null) return;
            s.left = candidate;
            s.enabled = true;
            s.grabYaw = ClimbSurfaceMath.facingYaw(c.normal(), player.getYRot());
            s.lastLeft = c.point();
            s.hasContact = true;
        }
        GripAnchor oldAnchor = s.left;
        GripAnchor.Contact contact = oldAnchor.resolve(player.level());
        if (contact == null || player.getEyePosition().distanceTo(contact.point()) > MAX_GRAB_REACH) {
            s.release(); return;
        }

        Vec3 normal = contact.normal().normalize();
        if (!Double.isFinite(normal.lengthSqr()) || normal.lengthSqr() < 0.8) {
            s.release(); return;
        }
        // Only an unchanged moving Sable sub-level generates structure velocity.
        // Previous code added changes between unrelated block hit points, causing
        // enormous unexplained speed bursts near ledges and corners.
        Vec3 platformDelta = s.hasContact
                ? ClimbSurfaceMath.platformDelta(s.lastLeft, contact.point(), oldAnchor.dynamic())
                : Vec3.ZERO;
        float heading = s.grabYaw;
        Vec3 input = ClimbSurfaceMath.direction(normal, heading, s.forward, s.strafe);
        boolean moving = input.lengthSqr() > 0.0001;

        if (moving && player.tickCount % 3 == 0) {
            GripAnchor probe = SurfaceProbe.traceOffset(player, input.scale(0.55), normal);
            if (probe == null) probe = SurfaceProbe.trace(player);
            if (PeakGripRules.mayGrab(player, probe)) {
                GripAnchor.Contact next = probe.resolve(player.level());
                if (next != null
                        && next.point().distanceTo(contact.point()) <= MAX_REGRIP_JUMP
                        && player.getEyePosition().distanceTo(next.point()) <= MAX_GRAB_REACH
                        && next.normal().normalize().dot(normal) >= -0.08) {
                    s.left = probe;
                    contact = next;
                    normal = next.normal().normalize();
                    s.grabYaw = ClimbSurfaceMath.facingYaw(normal, heading);
                    // CRITICAL: do NOT turn old->new anchor difference into motion.
                    // Platform drift has already been measured on the old anchor.
                    input = ClimbSurfaceMath.direction(normal, s.grabYaw, s.forward, s.strafe);
                }
            }
        }

        s.contactPoint = contact.point();
        s.contactNormal = normal;
        s.lastLeft = contact.point();
        s.hasContact = true;

        // Mantling is a separate bounded state machine, not an upward impulse
        // that can be restarted every tick during a camera or face transition.
        if (PeakLedgeAssist.start(player, s, contact)) {
            PeakLedgeAssist.tick(player, s);
            return;
        }

        Vec3 velocity = ClimbSurfaceMath.motor(player.getDeltaMovement(),
                input.scale(ClimbSurfaceMath.speed(normal)), normal);
        // The adhesion force counters vanilla gravity on roofs; unlike the old
        // 0.022 upward value, a roof contact should not slowly slide downward.
        double adhesion = ClimbSurfaceMath.ceiling(normal) ? 0.112 : 0.019;
        velocity = velocity.subtract(normal.scale(adhesion)).add(platformDelta);

        // Shift is a single key-down edge; roofs dash *along* the roof, never
        // launch the character vertically into a wall when ceiling-grabbing.
        if (s.lungeRequested && PeakGripRules.canLunge(s.lungeCooldown, s.enabled)) {
            s.lungeCooldown = 17;
            if (ClimbSurfaceMath.ceiling(normal)) velocity = velocity.add(input.scale(0.09));
            else if (!ClimbSurfaceMath.floor(normal))
                velocity = velocity.add(new Vec3(0, 0.22, 0)).add(input.scale(0.05));
            else velocity = velocity.add(input.scale(0.075));
        }
        s.lungeRequested = false;

        // Bound tether correction and each per-tick world displacement. A
        // regrip and surface turn must never amplify player motion.
        Vec3 shoulder = player.getEyePosition().add(0, -0.18, 0);
        Vec3 toPoint = contact.point().subtract(shoulder);
        if (toPoint.length() > 1.72) {
            Vec3 pull = toPoint.normalize();
            double outbound = -velocity.dot(pull);
            if (outbound > 0) velocity = velocity.add(pull.scale(outbound));
            velocity = velocity.add(pull.scale(Math.min(0.065, (toPoint.length()-1.72)*0.16)));
        }
        velocity = ClimbSurfaceMath.clamp(velocity, ClimbSurfaceMath.MAX_RELATIVE_SPEED
                + platformDelta.length());
        player.setDeltaMovement(velocity);
        player.fallDistance = 0;
        player.hurtMarked = true;
        if (ClimbSurfaceMath.ceiling(normal))
            s.poseAction = !moving ? (byte)4 : Math.abs(s.forward) >= Math.abs(s.strafe)
                    ? (byte)5 : (byte)6;
        else
            s.poseAction = !moving ? (byte)0 : Math.abs(s.forward) >= Math.abs(s.strafe)
                    ? (byte)1 : (byte)2;
        if (moving) s.peakPhase += (float)Math.min(0.35, input.length()*0.28);
    }

    /** Backward-compatible pure helper used by the original GameTests. */
    public static Vec3 desiredTangent(Vec3 normal, float yaw, float forward, float strafe) {
        return ClimbSurfaceMath.direction(normal, yaw, forward, strafe);
    }
}
