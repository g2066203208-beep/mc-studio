package dev.reme.mcstudio.climb;

import dev.reme.mcstudio.StudioMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-authoritative tick, with a client-side matching step for local prediction. */
@EventBusSubscriber(modid = StudioMod.MOD_ID)
public final class ClimbServerEvents {
    private ClimbServerEvents() {}

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        ClimbMotion.tick(event.getEntity(), ClimbState.of(event.getEntity()));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ClimbState.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void changeWorld(PlayerEvent.PlayerChangedDimensionEvent event) {
        ClimbState.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.Clone event) {
        ClimbState.remove(event.getOriginal());
    }
}
