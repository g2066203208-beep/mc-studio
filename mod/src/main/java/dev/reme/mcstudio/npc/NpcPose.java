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
    REST;

    public static NpcPose fromNetworkId(int id) {
        NpcPose[] all = values();
        return id >= 0 && id < all.length ? all[id] : IDLE;
    }
}
