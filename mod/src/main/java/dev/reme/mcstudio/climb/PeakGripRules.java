package dev.reme.mcstudio.climb;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Common stamina and grab validity shared by client, server and in-game tests. */
public final class PeakGripRules {
    public static final float MAX_STAMINA = 100f;
    public static final float PASSIVE_DRAIN = 0.23f;
    public static final float MOVEMENT_DRAIN = 0.36f;
    public static final float GROUND_RECOVERY = 1.3f;
    public static final float LUNGE_COST = 18f;
    private PeakGripRules() {}

    public static boolean mayGrab(Player player, @Nullable GripAnchor hit) {
        if (hit == null) return false;
        GripAnchor.Contact contact = hit.resolve(player.level());
        if (contact == null) return false;
        // Do not turn ordinary mining on level ground into accidental gripping.
        return contact.normal().y < 0.65 || !player.onGround();
    }

    public static float staminaNext(float current, boolean climbing, boolean moving, boolean grounded) {
        if (climbing) {
            float spent = PASSIVE_DRAIN + (moving ? MOVEMENT_DRAIN : 0f);
            return Math.max(0f, current - spent);
        }
        return grounded ? Math.min(MAX_STAMINA, current + GROUND_RECOVERY) : current;
    }

    public static boolean canLunge(float stamina, int cooldown, boolean climbing) {
        return climbing && cooldown == 0 && stamina >= LUNGE_COST;
    }
}
