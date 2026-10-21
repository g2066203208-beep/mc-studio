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
    public boolean lastJumpKey;
    public boolean jumpRequested;
    public int wallJumpCooldown;
    /** Prevent instant magnetic regrab of the departure wall after a jump. */
    public int airborneRegrabTicks;
    public boolean previouslySynced;
    public int lungeCooldown;
    public float peakPhase;
    /** Completed a mantle while RMB remained down; don't grab the ledge again. */
    public boolean mantleRequiresRelease;
    public int mantleTicks;
    public double mantleTargetY;
    public Vec3 mantleOrigin = Vec3.ZERO;
    public Vec3 mantleInward = Vec3.ZERO;
    public byte poseAction; // 0 wall hang, 1 climb, 2 traverse, 3 mantle, 4 roof hang, 5 roof crawl
    /** Stable facing established by the actual grabbed surface; not the camera. */
    public float grabYaw;
    public Vec3 contactNormal = Vec3.ZERO;
    public Vec3 contactPoint = Vec3.ZERO;
    /** Track the REAL last world-space contact, only on an unchanged Sable anchor. */
    public boolean hasContact;
    /** Last commanded vertical speed is restored before vanilla travel each tick. */
    public double commandedVertical;
    /** Prevent contact-family flip-flopping at corners. */
    public int faceSwitchCooldown;
    public float forward, strafe;
    @Nullable public GripAnchor left;
    @Nullable public GripAnchor right;
    public Vec3 lastLeft = Vec3.ZERO;
    /** Stable identity prevents regripping a new local point from looking like Sable ship velocity. */
    @Nullable public GripAnchor lastResolvedAnchor;
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
        contactPoint = Vec3.ZERO;
        contactNormal = Vec3.ZERO;
        hasContact = false;
        commandedVertical = 0;
        faceSwitchCooldown = 0;
        lastRight = Vec3.ZERO;
        lungeRequested = false;
        jumpRequested = false;
        lastResolvedAnchor = null;
        mantleTicks = 0;
        poseAction = 0;
    }
    public boolean gripping() { return enabled && left != null; }
}
