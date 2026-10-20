package dev.reme.mcstudio.climb;

import dev.reme.mcstudio.StudioMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = StudioMod.MOD_ID)
public final class ClimbServerEvents {
    private ClimbServerEvents() {}

    @SubscribeEvent
    public static void beforeMotion(PlayerTickEvent.Pre e) {
        var player = e.getEntity();
        ClimbState state = ClimbState.of(player);
        // Restore the supported velocity before vanilla travel consumes the
        // previous tick's gravity. This preserves ordinary gravity on release.
        if (state.enabled && state.peakHeld && state.mantleTicks == 0
                && state.left != null && !state.mantleRequiresRelease) {
            Vec3 old = player.getDeltaMovement();
            player.setDeltaMovement(old.x,
                    Math.max(-0.28, Math.min(0.34, state.commandedVertical)), old.z);
        }
    }

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
                                    state.peakPhase, state.poseAction,
                                    state.contactNormal, state.contactPoint, state.grabYaw));
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
