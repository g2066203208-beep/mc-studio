package dev.reme.mcstudio.npc.client;

import dev.reme.mcstudio.npc.net.ActorEmotePacket;
import dev.reme.mcstudio.npc.NpcPose;
import dev.reme.mcstudio.npc.SharedHumanoidMotion;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Remote authority: accepts ONLY the already server-validated action of
 * the real identified player, if visible in loaded ClientLevel.
 */
public final class ClientActorEmoteHandler {
    private ClientActorEmoteHandler() {}
    public static void handle(ActorEmotePacket data,IPayloadContext context) {
        if(!ModList.get().isLoaded("playeranimator")) return;
        if(data.duration()<1||data.duration()>100
            ||data.poseId()<0||data.poseId()>=NpcPose.values().length) return;
        NpcPose action=NpcPose.fromNetworkId(data.poseId());
        if(!SharedHumanoidMotion.authoredClip(action)) return;
        var level=Minecraft.getInstance().level;
        if(level==null) return;
        UUID id;
        try { id=UUID.fromString(data.actorUuid()); }
        catch (IllegalArgumentException badId) { return; }
        var player=level.getPlayerByUUID(id);
        if(!(player instanceof AbstractClientPlayer visible)) return;
        KosmXPlayerAnimation.sync(level);
        KosmXPlayerAnimation.play(visible,action,data.duration());
        System.out.println("REME_NPC_SHARED_PLAYER_EMOTE realPlayer="+id
            +" action="+action+" syncedClient=true");
    }
}
