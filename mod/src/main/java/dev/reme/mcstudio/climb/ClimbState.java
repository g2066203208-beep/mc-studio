package dev.reme.mcstudio.climb;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/** One PEAK-style automatic grip; the two arm poses are a single gait, not two mouse controls. */
public final class ClimbState {
    private static final Map<Player, ClimbState> ALL = new WeakHashMap<>();

    /** True only while the player is attached to a valid surface. */
    public boolean enabled;
    /** Server-validated intention to keep the grab button held. */
    public boolean peakHeld;
    public boolean lastLungeKey;
    public boolean lungeRequested;
    public boolean previouslySynced;
    public int lungeCooldown;
    public float stamina = 100f;
    public float peakPhase;
    public float forward, strafe;
    @Nullable public GripAnchor left;
    @Nullable public GripAnchor right;
    public Vec3 lastLeft = Vec3.ZERO;
    public Vec3 lastRight = Vec3.ZERO;
    public int sequence = -1;
    public int lastAckSequence = -1;
    public long lastInputTick = -1;

    private ClimbState() {}
    public static ClimbState of(Player player) { return ALL.computeIfAbsent(player, ignored -> new ClimbState()); }
    public static void remove(Player player) { ALL.remove(player); }
    public void release() {
        enabled = false;
        left = null;
        right = null;
        lastLeft = Vec3.ZERO;
        lastRight = Vec3.ZERO;
        lungeRequested = false;
    }
    public boolean gripping() { return enabled && left != null; }
}
