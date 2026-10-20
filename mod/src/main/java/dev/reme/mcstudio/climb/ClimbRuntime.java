package dev.reme.mcstudio.climb;

import net.minecraft.world.entity.player.Player;

/** Enforces mode, sequence, hand state, and reach/geometry on BOTH logical sides. */
public final class ClimbRuntime {
    private ClimbRuntime() {}

    public static void accept(Player player, int sequence, int hand, boolean down, boolean mode,
                              float forward, float strafe) {
        ClimbState s = ClimbState.of(player);
        if (!GripRules.validIntent(sequence, s.sequence, forward, strafe)) return;
        s.sequence = sequence;
        s.lastInputTick = player.level().getGameTime();

        if (!mode || player.isSpectator() || !player.isAlive() || player.isPassenger()) {
            s.enabled = false; s.release(); return;
        }
        s.enabled = true;
        s.forward = forward;
        s.strafe = strafe;

        if (hand == 0) {
            if (down) {
                if (s.left == null) s.left = SurfaceProbe.trace(player);
                if (s.left != null) s.lastLeft = s.left.resolve(player.level()) == null
                        ? net.minecraft.world.phys.Vec3.ZERO : s.left.resolve(player.level()).point();
            } else { s.left = null; s.lastLeft = net.minecraft.world.phys.Vec3.ZERO; }
        } else if (hand == 1) {
            if (down) {
                if (s.right == null) s.right = SurfaceProbe.trace(player);
                if (s.right != null) s.lastRight = s.right.resolve(player.level()) == null
                        ? net.minecraft.world.phys.Vec3.ZERO : s.right.resolve(player.level()).point();
            } else { s.right = null; s.lastRight = net.minecraft.world.phys.Vec3.ZERO; }
        }
    }
}
