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
        if (s.faceSwitchCooldown > 0) s.faceSwitchCooldown--;
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
            s.contactPoint = c.point();
            s.contactNormal = c.normal().normalize();
            s.commandedVertical = 0;
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

        if (moving && player.tickCount % 2 == 0) {
            // Switching between surface families requires a ray against the
            // NEW face. A ray pointing only toward the old wall never detects
            // the ceiling underside or a side wall.
            GripAnchor probe = s.faceSwitchCooldown == 0
                    ? SurfaceProbe.traceTransition(player, contact, input) : null;
            boolean faceTransition = probe != null;
            if (probe == null) probe = SurfaceProbe.traceOffset(player, input.scale(.55), normal);
            if (probe == null) probe = SurfaceProbe.trace(player);
            if (PeakGripRules.mayGrab(player, probe)) {
                GripAnchor.Contact next = probe.resolve(player.level());
                if (next != null
                        && next.point().distanceTo(contact.point()) <= MAX_REGRIP_JUMP
                        && player.getEyePosition().distanceTo(next.point()) <= MAX_GRAB_REACH
                        && next.normal().normalize().dot(normal) >= -0.14) {
                    s.left = probe;
                    contact = next;
                    normal = next.normal().normalize();
                    if (faceTransition) s.faceSwitchCooldown = 7;
                    s.grabYaw = ClimbSurfaceMath.facingYaw(normal, heading);
                    // CRITICAL: never add the new anchor-point displacement
                    // as Sable structure velocity or accumulated acceleration.
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

        // PlayerTick.Post occurs after vanilla travel: deltaMovement.y contains
        // freshly integrated gravity, not intentional climbing momentum.
        // Use our LAST commanded vertical instead and restore it in Tick.Pre.
        Vec3 previous = player.getDeltaMovement();
        if (!ClimbSurfaceMath.floor(normal))
            previous = new Vec3(previous.x, s.commandedVertical, previous.z);
        Vec3 velocity = ClimbSurfaceMath.motor(previous,
                input.scale(ClimbSurfaceMath.speed(normal)), normal);
        // Explicit clearance from actual face prevents head/camera and torso
        // clipping into blocks (the old permanent inward impulse caused black).
        velocity = velocity.add(ClimbSurfaceMath.standoff(
                player.getEyePosition(), contact.point(), normal)).add(platformDelta);

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
        s.commandedVertical = velocity.y;
        player.fallDistance = 0;
        player.hurtMarked = true;
        if (ClimbSurfaceMath.ceiling(normal))
            s.poseAction = !moving ? (byte)4 : Math.abs(s.forward) >= Math.abs(s.strafe)
                    ? (byte)5 : (byte)6;
        else
            s.poseAction = !moving ? (byte)0 : Math.abs(s.forward) >= Math.abs(s.strafe)
                    ? (byte)1 : (byte)2;
        if (moving) s.peakPhase += (float)Math.min(0.35,
                ClimbSurfaceMath.tangent(velocity, normal).length()*1.55);
    }

    /** Backward-compatible pure helper used by the original GameTests. */
    public static Vec3 desiredTangent(Vec3 normal, float yaw, float forward, float strafe) {
        return ClimbSurfaceMath.direction(normal, yaw, forward, strafe);
    }
}
