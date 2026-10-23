package dev.reme.mcstudio.npc;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * ONE actor action contract for both genuine Minecraft players and
 * PlayerNpcEntity humanoids. Every authored clip uses the same vanilla
 * player bones / skin layout and a single resource namespace.
 *
 * Server-side AI or real vanilla player states decide the action. Only the
 * playerAnimator adapter and NPC renderer decide HOW it looks; no gameplay
 * outcome is inferred from an animation. A remote player can use the same
 * entrypoint as the local player when their authoritative state is visible.
 */
public final class SharedHumanoidMotion {
    private SharedHumanoidMotion() {}
    public static final String RESOURCE_NAMESPACE = "reme_studio";

    public static ResourceLocation animationId(NpcPose pose) {
        return ResourceLocation.fromNamespaceAndPath(
            RESOURCE_NAMESPACE, "actor_" + pose.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean authoredClip(NpcPose pose) {
        return switch (pose) {
            case IDLE, WALK, RUN, JUMP, CROUCH, GUARD,
                 STRIKE, HIT, STUNNED, CUFFED, ESCORT, FLEE, CHASE,
                 SOCIALIZE, GREET, HAND_HOLD, LEAN, SITTING,
                 EATING, WORK, CARRY, LOOK_AROUND, AFRAID, REST, SHOPPING,
                 LAUGH, SAD, BACK_CARRY, WAKE, SIT_DOWN -> true;
            default -> false;
        };
    }

    /** Vanilla networked player observations: no fake key presses or extra
     * packets that claim the player has done something they have not done.
     * Other mods can request the authored motions through play(...) later.
     */
    public static NpcPose observePlayer(Player player) {
        if (player.hurtTime > 0) return NpcPose.HIT;
        if (player.swinging) return NpcPose.STRIKE;
        if (player.isPassenger()) return NpcPose.SITTING;
        if (player.isShiftKeyDown()) return NpcPose.CROUCH;
        if (!player.onGround() && !player.isInWater()) return NpcPose.JUMP;
        if (player.isSprinting()) return NpcPose.RUN;
        if (player.getDeltaMovement().horizontalDistanceSqr() > 0.003) return NpcPose.WALK;
        return NpcPose.IDLE;
    }
}
