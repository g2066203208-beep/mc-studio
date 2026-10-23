package dev.reme.mcstudio.npc.client;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcPose;
import dev.reme.mcstudio.npc.SharedHumanoidMotion;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Hooks all visible REAL players, while the NPC renderer samples the exact
 * same player_animation assets. Not registered on dedicated servers. */
@EventBusSubscriber(modid=StudioMod.MOD_ID,value=Dist.CLIENT)
public final class ClientHumanoidMotionEvents {
    private ClientHumanoidMotionEvents() {}
    private static boolean checkedResources=false;
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ModList.get().isLoaded("playeranimator")) return;
        var level=Minecraft.getInstance().level;
        if (level==null) { checkedResources=false; return; }
        if (!checkedResources) {
            int registered=0;
            for (NpcPose pose:NpcPose.values())
                if (SharedHumanoidMotion.authoredClip(pose)
                    && PlayerAnimationRegistry.getAnimation(
                        SharedHumanoidMotion.animationId(pose))!=null) registered++;
            // Printed by the REAL client only, never by a mocked server test.
            System.out.println("REME_NPC_REAL_CLIENT_SHARED_RIG playeranimator=true"
                +" bothActorKinds=true registeredKeyframeClips="+registered);
            checkedResources=true;
        }
        KosmXPlayerAnimation.sync(level);
    }
}
