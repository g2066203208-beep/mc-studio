package dev.reme.mcstudio.npc;

/**
 * Explicit, low-bandwidth visual contract. The server owns each NPC's actual
 * work/social/threat context and only replicates pose changes, not every limb.
 * The client animates standard 6-part Minecraft/player geometry locally.
 */
public enum NpcPose {
    IDLE,
    WORK,
    CARRY,
    SOCIALIZE,
    GUARD,
    AFRAID,
    REST,
    // APPEND ONLY: existing ordinal IDs must remain stable across clients.
    FLEE, CHASE, STRIKE, HIT, STUNNED, CUFFED, ESCORT,
    SHOPPING, EATING, SITTING, LOOK_AROUND, CROUCH,
    LAUGH, SAD, GREET,
    // Shared vanilla-player AND living-NPC rig events. Append, never reorder.
    WALK, RUN, JUMP, LEAN, HAND_HOLD, BACK_CARRY, WAKE, SIT_DOWN;

    public static NpcPose fromNetworkId(int id) {
        NpcPose[] all = values();
        return id >= 0 && id < all.length ? all[id] : IDLE;
    }
}
