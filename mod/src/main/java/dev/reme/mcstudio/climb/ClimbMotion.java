package dev.reme.mcstudio.climb;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Surface driven movement. Camera pitch/yaw no longer steer the body when
 * hanging: the surface normal and grab heading supply the entire tangent basis.
 */
public final class ClimbMotion {
    private static final double MAX_GRAB_REACH = 2.90;
    private static final int ROOF_TRANSITION_TIMEOUT = 23;
    private ClimbMotion() {}

    public static void tick(Player player, ClimbState s) {
        if (s.lungeCooldown > 0) s.lungeCooldown--;
        if (s.faceSwitchCooldown > 0) s.faceSwitchCooldown--;
        if (s.wallJumpCooldown > 0) s.wallJumpCooldown--;
        if (s.airborneRegrabTicks > 0) s.airborneRegrabTicks--;
        if (s.roofEntryTicks > 0) s.roofEntryTicks--;
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
        if (!s.enabled && s.airborneRegrabTicks > 0) return;

        if (!s.enabled || s.left == null) {
            GripAnchor candidate = SurfaceProbe.trace(player);
            if (!PeakGripRules.mayGrab(player, candidate)) return;
            GripAnchor.Contact c = candidate.resolve(player.level());
            if (c == null) return;
            s.left = candidate;
            s.enabled = true;
            s.grabYaw = ClimbSurfaceMath.facingYaw(c.normal(), player.getYRot());
            s.lastLeft = c.point();
            s.lastResolvedAnchor = candidate;
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
        // Jump while holding the wall: use the SERVER'S player look heading,
        // release the current anchor and fly toward another aimable hold.  Space
        // does not silently replace the existing SHIFT short lunge.
        if (s.jumpRequested) {
            s.jumpRequested = false;
            if (s.wallJumpCooldown == 0 && !ClimbSurfaceMath.ceiling(normal)
                    && !ClimbSurfaceMath.floor(normal)) {
                Vec3 impulse = ClimbSurfaceMath.wallJumpVelocity(player.getLookAngle(), normal);
                s.release();                 // keep peakHeld so a new wall may be caught
                s.airborneRegrabTicks = 7;
                s.wallJumpCooldown = 22;
                player.setDeltaMovement(impulse);
                player.fallDistance = 0;
                player.hurtMarked = true;
                return;
            }
        }
        // Only an unchanged moving Sable sub-level generates structure velocity.
        // Previous code added changes between unrelated block hit points, causing
        // enormous unexplained speed bursts near ledges and corners.
        Vec3 platformDelta = s.hasContact && oldAnchor.dynamic()
                && oldAnchor.equals(s.lastResolvedAnchor)
                ? ClimbSurfaceMath.platformDelta(s.lastLeft, contact.point(), true)
                : Vec3.ZERO;
        float heading = s.grabYaw;
        Vec3 input = ClimbSurfaceMath.ceiling(normal)
                ? ClimbSurfaceMath.cameraRelativeRoof(normal, player.getYRot(), s.forward, s.strafe)
                : ClimbSurfaceMath.direction(normal, heading, s.forward, s.strafe);
        boolean moving = input.lengthSqr() > 0.0001;

        // Two-stage transfer: DON'T lie about being on a roof just because a
        // long ray from the old wall happened to intersect its underside.
        // Keep a real wall anchor while moving the eyes/torso under that roof.
        if (s.pendingRoof != null) {
            GripAnchor.Contact roof = s.pendingRoof.resolve(player.level());
            if (roof == null || !ClimbSurfaceMath.ceiling(roof.normal())
                    || ++s.roofTransitionTicks > ROOF_TRANSITION_TIMEOUT
                    || player.getEyePosition().distanceTo(roof.point()) > MAX_GRAB_REACH) {
                s.pendingRoof = null;
                s.roofTransitionTicks = 0;
            } else if (ClimbSurfaceMath.roofEntryReady(
                    player.getEyePosition(), roof.point(), roof.normal())) {
                s.left = s.pendingRoof;
                s.pendingRoof = null;
                s.roofTransitionTicks = 0;
                s.roofEntryTicks = 11;
                s.roofEntryOutward = new Vec3(normal.x, 0, normal.z).normalize();
                s.faceSwitchCooldown = 8;
                contact = roof;
                normal = roof.normal().normalize();
                // Body does not snap; camera-relative motion becomes available
                // on this same tick. The entry assist only handles facing *into*
                // the just-departed wall until the player turns toward free roof.
                input = roofEntryControl(player, s, normal);
            } else {
                Vec3 approach = ClimbSurfaceMath.roofEntryVelocity(
                        player.getEyePosition(), roof.point(), roof.normal());
                // Retain only the SAME Sable anchor's bounded platform motion.
                approach = ClimbSurfaceMath.clamp(approach.add(platformDelta), .24);
                if (!player.level().noCollision(player, player.getBoundingBox().move(approach)))
                    approach = Vec3.ZERO;
                player.setDeltaMovement(approach);
                player.fallDistance = 0;
                player.hurtMarked = true;
                s.commandedVertical = approach.y;
                s.poseAction = 8; // visible transitional wall -> ceiling hand reach
                s.peakPhase += .08f;
                s.contactPoint = contact.point();
                s.contactNormal = normal;
                s.lastLeft = contact.point();
                s.lastResolvedAnchor = oldAnchor;
                s.hasContact = true;
                return;
            }
        }
        if (ClimbSurfaceMath.ceiling(normal)) {
            input = roofEntryControl(player, s, normal);
            s.grabYaw = ClimbSurfaceMath.approachYaw(s.grabYaw,
                    roofBodyTargetYaw(player, s), 19f);
        }
        moving = input.lengthSqr() > 0.0001;

        if (moving && s.pendingRoof == null && s.roofEntryTicks != 11) {
            // Switching between surface families requires a ray against the
            // NEW face. A ray pointing only toward the old wall never detects
            // the ceiling underside or a side wall.
            GripAnchor probe = s.faceSwitchCooldown == 0
                    && SurfaceProbe.nearFaceEdge(oldAnchor)
                    ? SurfaceProbe.traceTransition(player, contact, input) : null;
            boolean faceTransition = probe != null;
            if (probe == null) probe = SurfaceProbe.traceOffset(player, input.scale(.55), normal);
            if (probe == null) probe = SurfaceProbe.trace(player);
            if (PeakGripRules.mayGrab(player, probe)) {
                GripAnchor.Contact next = probe.resolve(player.level());
                if (next != null
                        && next.point().distanceTo(contact.point()) <= 1.74
                        && player.getEyePosition().distanceTo(next.point()) <= MAX_GRAB_REACH
                        && next.normal().normalize().dot(normal) >= -0.14) {
                    Vec3 candidateNormal = next.normal().normalize();
                    // The ceiling animation and movement must not switch until
                    // the player physically reaches the new handhold.
                    if (!ClimbSurfaceMath.ceiling(normal) && ClimbSurfaceMath.ceiling(candidateNormal)) {
                        if (SurfaceProbe.nearFaceEdge(oldAnchor)) {
                            s.pendingRoof = probe;
                            s.roofTransitionTicks = 0;
                            s.poseAction = 8;
                        }
                    } else if (!ClimbSurfaceMath.ceiling(normal)
                            || ClimbSurfaceMath.ceiling(candidateNormal)
                            || SurfaceProbe.nearFaceEdge(oldAnchor)) {
                        // Roof -> wall is possible ONLY near an actual roof
                        // edge, not because a ray happened to see an old wall.
                        // Ordinary same-face regrips remain
                        // bounded by the contact-distance, no new acceleration.
                        s.left = probe;
                        contact = next;
                        normal = candidateNormal;
                        if (faceTransition) s.faceSwitchCooldown = 4;
                        // Already on a roof? Preserve the continuously
                        // interpolated camera yaw, never restore old wall yaw.
                        if (!ClimbSurfaceMath.ceiling(normal))
                            s.grabYaw = ClimbSurfaceMath.facingYaw(normal, heading);
                        input = ClimbSurfaceMath.ceiling(normal)
                                ? roofEntryControl(player, s, normal)
                                : ClimbSurfaceMath.direction(normal, s.grabYaw, s.forward, s.strafe);
                    }
                }
            }
        }

        s.contactPoint = contact.point();
        s.contactNormal = normal;
        s.lastLeft = contact.point();
        s.lastResolvedAnchor = s.left;
        s.hasContact = true;

        // Mantling is a separate bounded state machine, not an upward impulse
        // that can be restarted every tick during a camera or face transition.
        if (s.pendingRoof == null && PeakLedgeAssist.start(player, s, contact)) {
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
        if (s.pendingRoof != null)
            s.poseAction = 8;
        else if (ClimbSurfaceMath.ceiling(normal))
            s.poseAction = !moving ? (byte)4 : Math.abs(s.forward) >= Math.abs(s.strafe)
                    ? (byte)5 : (byte)6;
        else
            s.poseAction = !moving ? (byte)0 : Math.abs(s.forward) >= Math.abs(s.strafe)
                    ? (byte)1 : (byte)2;
        if (moving) s.peakPhase += (float)Math.min(0.35,
                ClimbSurfaceMath.tangent(velocity, normal).length()*1.55);
    }

    /** After first entering a roof, W must move AWAY from the departure wall,
     * not into it when the camera still faces the wall. Once the camera turns
     * into free space, normal first-person camera-relative controls take over.
     */
    private static Vec3 roofEntryControl(Player player, ClimbState state, Vec3 normal) {
        Vec3 intent = ClimbSurfaceMath.cameraRelativeRoof(
                normal, player.getYRot(), state.forward, state.strafe);
        if (state.roofEntryTicks > 0 && state.forward > 0 && state.roofEntryOutward.lengthSqr() > .25) {
            Vec3 look = player.getLookAngle();
            Vec3 flatLook = new Vec3(look.x, 0, look.z);
            if (flatLook.lengthSqr() > .01
                    && flatLook.normalize().dot(state.roofEntryOutward) < -.3) {
                Vec3 escape = ClimbSurfaceMath.tangent(state.roofEntryOutward, normal).normalize();
                Vec3 sideways = ClimbSurfaceMath.cameraRelativeRoof(normal,
                        player.getYRot(), 0, state.strafe);
                return ClimbSurfaceMath.clamp(
                        escape.scale(state.forward).add(sideways), 1);
            }
        }
        return intent;
    }

    private static float roofBodyTargetYaw(Player player, ClimbState state) {
        if (state.roofEntryTicks > 0 && state.forward > 0
                && state.roofEntryOutward.lengthSqr() > .25) {
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x,0,look.z);
            if (flat.lengthSqr() > .01
                    && flat.normalize().dot(state.roofEntryOutward) < -.3)
                // atan2(-0,-1) produces -180 while +180 is identical
                // physically; pick a stable positive tie-break to avoid an
                // immediate -19 -> 0 -> +19 reversal when the camera turns.
                double angle = Math.toDegrees(
                        Math.atan2(-state.roofEntryOutward.x, state.roofEntryOutward.z));
                return (float)(Math.abs(Math.abs(angle)-180) < 0.0001 ? 180 : angle);
        }
        return player.getYRot();
    }

    /** Backward-compatible pure helper used by the original GameTests. */
    public static Vec3 desiredTangent(Vec3 normal, float yaw, float forward, float strafe) {
        return ClimbSurfaceMath.direction(normal, yaw, forward, strafe);
    }
}
