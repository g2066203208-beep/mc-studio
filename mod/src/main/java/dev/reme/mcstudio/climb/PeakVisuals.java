package dev.reme.mcstudio.climb;

import net.minecraft.world.entity.player.Player;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side observations, updated by server snapshots for other players. */
public final class PeakVisuals {
    public record Pose(boolean climbing, float phase, long gameTick, byte action) {}
    private static final Map<Integer, Pose> SEEN = new ConcurrentHashMap<>();
    private PeakVisuals() {}

    public static void observe(int entityId, boolean active, float phase, long gameTick, byte action) {
        if (!active) SEEN.remove(entityId);
        else SEEN.put(entityId, new Pose(true, phase, gameTick, action));
    }

    public static Pose pose(Player player) {
        ClimbState local = ClimbState.of(player);
        if (local.enabled && local.peakHeld)
            return new Pose(true, local.peakPhase, player.level().getGameTime(), local.poseAction);
        Pose p = SEEN.get(player.getId());
        return p != null && player.level().getGameTime() - p.gameTick() <= 10 ? p : null;
    }
}
