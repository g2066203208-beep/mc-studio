package dev.reme.mcstudio.climb;

import net.minecraft.world.entity.player.Player;

/** Handles the authoritative hold-input protocol. 'mode' carries a SHIFT lunge key now. */
public final class ClimbRuntime {
    private ClimbRuntime() {}

    /** Prior test and call-site compatibility; newer network includes Jump. */
    public static void accept(Player player, int sequence, int hand, boolean down,
                              boolean lunge, float forward, float strafe) {
        accept(player, sequence, hand, down, lunge, false, forward, strafe);
    }

    public static void accept(Player player, int sequence, int hand, boolean down,
                              boolean lunge, boolean jump, float forward, float strafe) {
        ClimbState s = ClimbState.of(player);
        if (!GripRules.validIntent(sequence, s.sequence, forward, strafe)) return;
        s.sequence = sequence;
        s.lastInputTick = player.level().getGameTime();
        if (!player.isAlive() || player.isPassenger() || player.isSpectator()
                || player.getAbilities().flying) {
            s.peakHeld = false;
            s.release();
            return;
        }
        // One right mouse button (RMB) drives the whole climbing interaction.
        s.peakHeld = down;
        s.forward = forward;
        s.strafe = strafe;
        if (lunge && !s.lastLungeKey) s.lungeRequested = true;
        s.lastLungeKey = lunge;
        if (down && jump && !s.lastJumpKey && s.gripping())
            s.jumpRequested = true;
        s.lastJumpKey = jump;
        if (!down) {
            s.lastJumpKey = false;
            s.mantleRequiresRelease = false;
            s.release();
            return;
        }
        if (!s.enabled && !s.mantleRequiresRelease && s.airborneRegrabTicks == 0) {
            GripAnchor hit = SurfaceProbe.trace(player);
            if (PeakGripRules.mayGrab(player, hit)) {
                s.left = hit;
                s.right = null;
                s.enabled = true;
                GripAnchor.Contact contact = hit.resolve(player.level());
                s.lastLeft = contact == null ? net.minecraft.world.phys.Vec3.ZERO : contact.point();
                s.lastResolvedAnchor = contact == null ? null : hit;
                if (contact != null) {
                    s.grabYaw = ClimbSurfaceMath.facingYaw(contact.normal(), player.getYRot());
                    s.contactNormal = contact.normal();
                    s.contactPoint = contact.point();
                    s.hasContact = true;
                }
            }
        }
    }
}
