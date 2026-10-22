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
