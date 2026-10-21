package dev.reme.mcstudio.climb;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side observations, updated by server snapshots for other players. */
public final class PeakVisuals {
    public record Pose(boolean climbing, float phase, long gameTick, byte action,
                       Vec3 normal, Vec3 contactPoint, float bodyYaw) {}
    private static final Map<Integer, Pose> SEEN = new ConcurrentHashMap<>();
    private PeakVisuals() {}

    public static void observe(int entityId, boolean active, float phase, long gameTick,
                               byte action, Vec3 normal, Vec3 contactPoint, float bodyYaw) {
        if (!active) SEEN.remove(entityId);
        else SEEN.put(entityId, new Pose(true, phase, gameTick, action, normal, contactPoint, bodyYaw));
    }

    public static Pose pose(Player player) {
        ClimbState local = ClimbState.of(player);
        if (local.peakHeld && (local.enabled || local.wallJumpAnimationTicks > 0))
            return new Pose(true, local.peakPhase, player.level().getGameTime(), local.poseAction,
                    local.contactNormal, local.contactPoint, local.grabYaw);
        // The local controlling player can still have an older server Pose
        // packet cached. Never let that packet resurrect the grip after RMB up.
        if (player instanceof net.minecraft.client.player.LocalPlayer) return null;
        Pose p = SEEN.get(player.getId());
        return p != null && player.level().getGameTime() - p.gameTick() <= 10 ? p : null;
    }
}
