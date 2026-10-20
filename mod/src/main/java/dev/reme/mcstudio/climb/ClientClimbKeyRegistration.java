package dev.reme.mcstudio.climb;

import dev.reme.mcstudio.StudioMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Mod lifecycle events must NOT live on the game event bus. Keeps the regular
 * client gameplay input handler subscribed to the GAME bus independently.
 */
@EventBusSubscriber(modid = StudioMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientClimbKeyRegistration {
    private ClientClimbKeyRegistration() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        ClientClimbControls.registerKeys(event);
    }
}
