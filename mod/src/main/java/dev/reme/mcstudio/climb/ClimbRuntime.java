package dev.reme.mcstudio.climb;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * V2 click-latched independent anchors. down=true is a click edge, not a hold.
 * Heartbeats use hand=-1. All candidate geometry is recomputed by the server.
 */
public final class ClimbRuntime {
    private ClimbRuntime() {}

    public static void accept(Player player, int sequence, int hand, boolean down,
                              boolean active, float forward, float strafe) {
        ClimbState s = ClimbState.of(player);
        if (!GripRules.validIntent(sequence, s.sequence, forward, strafe)) return;
        s.sequence = sequence;
        s.lastInputTick = player.level().getGameTime();

        if (!active || player.isSpectator() || !player.isAlive() || player.isPassenger()) {
            if (s.usingLeftItem && player.isUsingItem()) player.releaseUsingItem();
            s.usingLeftItem = false;
            s.enabled = false;
            s.release();
            return;
        }
        s.forward = forward;
        s.strafe = strafe;

        if (hand == 0 && down) {
            if (s.left != null) {
                s.left = null;
                s.lastLeft = Vec3.ZERO;
            } else if (player.getOffhandItem().isEmpty()) {
                s.left = SurfaceProbe.trace(player);
                GripAnchor.Contact c = s.left == null ? null : s.left.resolve(player.level());
                s.lastLeft = c == null ? Vec3.ZERO : c.point();
                if (c != null && c.normal().y >= 0.65 && player.onGround()
                        && !player.isShiftKeyDown()) {
                    s.left = null;
                    s.lastLeft = Vec3.ZERO;
                }
            }
        } else if (hand == 1 && down) {
            if (s.right != null) {
                s.right = null;
                s.lastRight = Vec3.ZERO;
                if (s.usingLeftItem) {
                    if (player.isUsingItem()) player.releaseUsingItem();
                    s.usingLeftItem = false;
                }
            } else if (player.getMainHandItem().isEmpty()) {
                s.right = SurfaceProbe.trace(player);
                GripAnchor.Contact c = s.right == null ? null : s.right.resolve(player.level());
                s.lastRight = c == null ? Vec3.ZERO : c.point();
                if (c != null && c.normal().y >= 0.65 && player.onGround()
                        && !player.isShiftKeyDown()) {
                    s.right = null;
                    s.lastRight = Vec3.ZERO;
                }
            }
        }
        // Anchors, not a permanently enabled G mode, determine ability to climb.
        s.enabled = s.gripping();
    }
}
