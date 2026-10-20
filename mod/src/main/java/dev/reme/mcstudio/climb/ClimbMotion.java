package dev.reme.mcstudio.climb;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * PEAK-style held-grab locomotion. Uses the vanilla player collision resolver,
 * not noClip or teleport, and regrips as the player moves along a block face.
 * Sable contacts are still transformed by GripAnchor.resolve each tick.
 */
public final class ClimbMotion {
    private static final double MAX_GRAB_REACH = 2.9;
    private ClimbMotion() {}

    public static void tick(Player player, ClimbState s) {
        if (s.lungeCooldown > 0) s.lungeCooldown--;
        if (player.isPassenger() || player.isSpectator() || !player.isAlive() || player.getAbilities().flying) {
            s.peakHeld = false; s.release(); return;
        }
        if (!player.level().isClientSide() && s.peakHeld
                && player.level().getGameTime() - s.lastInputTick > 12) {
            s.peakHeld = false;
            s.release();
        }
        if (!s.peakHeld) {
            if (s.enabled) s.release();
            s.stamina = PeakGripRules.staminaNext(s.stamina, false, false, player.onGround());
            return;
        }
        if (s.mantleTicks > 0) {
            PeakLedgeAssist.tick(player, s);
            return;
        }
        if (s.stamina <= 0) {
            s.release();
            s.stamina = PeakGripRules.staminaNext(s.stamina,false,false,player.onGround());
            return;
        }

        // Continuous automatic grip acquisition: one input, no manual hand switching.
        if (!s.enabled || s.left == null) {
            GripAnchor candidate = SurfaceProbe.trace(player);
            if (PeakGripRules.mayGrab(player, candidate)) {
                s.left = candidate;
                s.enabled = true;
                GripAnchor.Contact c = candidate.resolve(player.level());
                s.lastLeft = c == null ? Vec3.ZERO : c.point();
            } else {
                s.stamina = PeakGripRules.staminaNext(s.stamina, false, false, player.onGround());
                return;
            }
        }
        GripAnchor.Contact current = s.left.resolve(player.level());
        if (current == null) {
            s.release();
            return;
        }

        double distance = player.getEyePosition().distanceTo(current.point());
        // Re-target the current face while moving; do not tether to a single block forever.
        if (distance > 1.2 || (player.tickCount % 4 == 0 && Math.abs(s.forward) > 0.01f)) {
            GripAnchor next = SurfaceProbe.trace(player);
            if (PeakGripRules.mayGrab(player, next)) {
                s.left = next;
                current = next.resolve(player.level());
            }
        }
        if (current == null || player.getEyePosition().distanceTo(current.point()) > MAX_GRAB_REACH) {
            s.release();
            return;
        }

        if (PeakLedgeAssist.start(player, s, current)) {
            PeakLedgeAssist.tick(player, s);
            return;
        }

        boolean moving = Math.abs(s.forward) > 0.01 || Math.abs(s.strafe) > 0.01;
        s.stamina = PeakGripRules.staminaNext(s.stamina, true, moving, player.onGround());
        if (s.stamina <= 0) { s.release(); return; }

        Vec3 deltaFromStructure = Vec3.ZERO;
        if (!s.lastLeft.equals(Vec3.ZERO)) {
            deltaFromStructure = current.point().subtract(s.lastLeft);
            if (deltaFromStructure.length() > 1.5) { s.release(); return; }
        }
        s.lastLeft = current.point();
        Vec3 normal = current.normal().normalize();
        Vec3 direction = desiredTangent(normal, player.getYRot(), s.forward, s.strafe);
        Vec3 velocity = player.getDeltaMovement().scale(0.35).add(direction.scale(0.16))
                .add(deltaFromStructure);

        // Upward grip force and wall contact are bounded, never infinite flight.
        if (Math.abs(normal.y) < 0.65) {
            velocity = new Vec3(velocity.x, direction.y * 0.17, velocity.z);
            // A small inward normal component holds the body against the surface.
            velocity = velocity.subtract(normal.scale(0.025));
        } else if (normal.y < -0.65) {
            // Ceiling traversal: no gravity-only fall while button is held.
            velocity = velocity.subtract(normal.scale(0.015));
        } else {
            // Ground: retain normal ground contact and friction.
            velocity = new Vec3(velocity.x, Math.min(velocity.y, 0), velocity.z);
        }

        if (s.lungeRequested && PeakGripRules.canLunge(s.stamina, s.lungeCooldown, s.enabled)) {
            s.stamina -= PeakGripRules.LUNGE_COST;
            s.lungeCooldown = 17;
            velocity = velocity.add(new Vec3(0, 0.30, 0)).add(direction.scale(0.12));
        }
        s.lungeRequested = false;

        // Last safety constraint: camera/shoulder cannot travel arbitrarily far from the face.
        Vec3 shoulder = player.getEyePosition().add(0, -0.18, 0);
        Vec3 toAnchor = current.point().subtract(shoulder);
        if (toAnchor.length() > 1.75) {
            Vec3 inward = toAnchor.normalize();
            double outward = -velocity.dot(inward);
            if (outward > 0) velocity = velocity.add(inward.scale(outward));
            velocity = velocity.add(inward.scale(Math.min(0.11, (toAnchor.length() - 1.75) * 0.35)));
        }
        double maxSpeed = 0.35 + deltaFromStructure.length();
        if (velocity.length() > maxSpeed) velocity = velocity.normalize().scale(maxSpeed);
        player.setDeltaMovement(velocity);
        player.fallDistance = 0;
        player.hurtMarked = true;
        s.poseAction = !moving ? (byte) 0 : Math.abs(s.forward) > 0.1f ? (byte) 1 : (byte) 2;
        if (moving) s.peakPhase += (float) Math.min(0.4, direction.length() * 0.36);
    }

    /** Natural surface-local movement; W is up on walls and forward on ceilings/floor. */
    public static Vec3 desiredTangent(Vec3 surfaceNormal, float yawDegrees, float forward, float strafe) {
        Vec3 n = surfaceNormal.normalize();
        double yaw = Math.toRadians(yawDegrees);
        Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 horizontal = projection(facing, n);
        Vec3 vertical = projection(new Vec3(0, 1, 0), n);
        Vec3 along = Math.abs(n.y) < 0.7 && vertical.lengthSqr() > 0.01
                ? vertical.normalize() : horizontal.normalize();
        Vec3 across = projection(right, n);
        if (across.lengthSqr() < 0.001) across = n.cross(along);
        if (across.lengthSqr() > 0.001) across = across.normalize();
        Vec3 v = along.scale(forward).add(across.scale(strafe));
        return v.lengthSqr() > 1 ? v.normalize() : v;
    }
    private static Vec3 projection(Vec3 movement, Vec3 normal) {
        return movement.subtract(normal.scale(movement.dot(normal)));
    }
}
