package dev.reme.mcstudio.npc;

import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.RestPolicy;
import net.minecraft.world.item.Items;

/**
 * Cheap server presentation classification (not a second AI planner).
 * Select from facts already held by actual life, inventory and combat systems.
 * Only the enum ordinal is synchronized; keyframe interpolation is client-only.
 */
public final class NpcPoseSelection {
    private NpcPoseSelection() {}

    public static NpcPose select(PlayerNpcEntity npc, long tick) {
        var intent = npc.intent();
        // These take precedence over generic moods. Each is backed by a
        // real entity event/case, not a decorative "city simulation" string.
        NpcPose urgent = classifyPhysical(
            npc.isPoliceStunned(tick),
            npc.isDetained(tick),
            npc.escortedPrisoner() != null,
            npc.policeSuspect() != null,
            npc.hasUnpaidShopItem(tick),
            npc.hurtTime > 0);
        if (urgent != null) return urgent;
        if (npc.isShiftKeyDown()) return NpcPose.CROUCH;
        if (npc.isPassenger()) return NpcPose.SITTING;
        if (npc.isUsingItem()) return NpcPose.EATING;
        if (intent == LivingPlanner.Intent.SOCIALIZE
            && npc.getNavigation().isDone()) return NpcPose.GREET;
        return classify(
            npc.isAfraid(tick) || intent == LivingPlanner.Intent.ESCAPE,
            npc.getTarget() != null && npc.getTarget().isAlive(),
            npc.miningTarget() != null,
            npc.profession() == PlayerNpcEntity.Profession.COURIER
                && npc.backpack().countItem(Items.WHEAT) > 0,
            intent == LivingPlanner.Intent.SOCIALIZE,
            (npc.isSleeping() || (
                RestPolicy.shouldRest(intent,
                    npc.profession() == PlayerNpcEntity.Profession.GUARD,
                    npc.energy())
                && RestPolicy.atHome(npc.blockPosition(), npc.homePos())))
                && !npc.isAfraid(tick)
        );
    }

    /** Strict, side-effect-free hierarchy of physically verifiable moments.
     * A struck NPC may escape; a mere pursuit must never look handcuffed.
     * This is independent of pathfinder timing and test fixtures.
     */
    public static NpcPose classifyPhysical(boolean unconscious, boolean detained,
                                           boolean escorting, boolean pursuing,
                                           boolean fleeing, boolean hurt) {
        if (unconscious) return NpcPose.STUNNED;
        if (hurt) return NpcPose.HIT;
        if (detained) return NpcPose.CUFFED;
        if (escorting) return NpcPose.ESCORT;
        if (pursuing) return NpcPose.CHASE;
        if (fleeing) return NpcPose.FLEE;
        return null;
    }

    /** Public pure function for deterministic test of precedence. Threat
     * beats work; combat beats mining; actual cargo beats generic greeting.
     */
    public static NpcPose classify(boolean afraid, boolean defending,
                                   boolean working, boolean carrying,
                                   boolean socializing, boolean resting) {
        if (afraid) return NpcPose.AFRAID;
        if (defending) return NpcPose.GUARD;
        if (working) return NpcPose.WORK;
        if (carrying) return NpcPose.CARRY;
        if (resting) return NpcPose.REST;
        if (socializing) return NpcPose.SOCIALIZE;
        return NpcPose.IDLE;
    }
}
