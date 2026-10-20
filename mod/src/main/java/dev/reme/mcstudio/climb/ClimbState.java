package dev.reme.mcstudio.climb;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Player-identity keyed, not UUID keyed: integrated client and integrated server
 * have separate Player instances and must not share mutable grip state.
 */
public final class ClimbState {
    private static final Map<Player, ClimbState> ALL = new WeakHashMap<>();

    public boolean enabled;
    @Nullable public GripAnchor left;
    @Nullable public GripAnchor right;
    public Vec3 lastLeft = Vec3.ZERO;
    public Vec3 lastRight = Vec3.ZERO;
    public float forward;
    public float strafe;
    public int sequence = -1;
    public long lastInputTick = -1;

    private ClimbState() {}

    public static ClimbState of(Player player) {
        return ALL.computeIfAbsent(player, ignored -> new ClimbState());
    }

    public static void remove(Player player) {
        ALL.remove(player);
    }

    public void release() {
        left = null;
        right = null;
        lastLeft = Vec3.ZERO;
        lastRight = Vec3.ZERO;
    }

    public boolean gripping() {
        return left != null || right != null;
    }
}
