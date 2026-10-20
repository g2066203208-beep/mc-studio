package dev.reme.mcstudio.climb;

import dev.reme.mcstudio.StudioMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = StudioMod.MOD_ID)
public final class ClimbServerEvents {
    private ClimbServerEvents() {}

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        var player = e.getEntity();
        ClimbState state = ClimbState.of(player);
        ClimbMotion.tick(player, state);
        if (player instanceof ServerPlayer serverPlayer) {
            // Send close-by clients a compact animation state, also send once on release.
            if (serverPlayer.tickCount % 3 == 0 || (state.previouslySynced && !state.enabled)) {
                if (state.enabled || state.previouslySynced) {
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer,
                            new ClimbNetwork.Pose(serverPlayer.getId(), state.enabled,
                                    state.peakPhase, state.stamina, state.poseAction));
                }
                state.previouslySynced = state.enabled;
            }
        }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        ClimbState.remove(e.getEntity());
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        ClimbState.remove(e.getEntity());
    }
    @SubscribeEvent public static void respawn(PlayerEvent.Clone e) {
        ClimbState.remove(e.getOriginal());
    }
}
