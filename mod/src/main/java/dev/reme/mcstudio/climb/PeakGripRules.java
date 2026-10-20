package dev.reme.mcstudio.climb;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Valid grip targets and cooldown checks; no stamina or exhaustion. */
public final class PeakGripRules {
    private PeakGripRules() {}

    public static boolean mayGrab(Player player, @Nullable GripAnchor hit) {
        if (hit == null) return false;
        GripAnchor.Contact contact = hit.resolve(player.level());
        if (contact == null) return false;
        // Do not turn ordinary mining on level ground into accidental gripping.
        return contact.normal().y < 0.65 || !player.onGround();
    }

    public static boolean canLunge(int cooldown, boolean climbing) {
        return climbing && cooldown == 0;
    }
}
