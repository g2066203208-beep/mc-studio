package dev.reme.mcstudio.npc.client;

import dev.reme.mcstudio.StudioMod;
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
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ModList.get().isLoaded("playeranimator")) return;
        var level=Minecraft.getInstance().level;
        if (level!=null) KosmXPlayerAnimation.sync(level);
    }
}
