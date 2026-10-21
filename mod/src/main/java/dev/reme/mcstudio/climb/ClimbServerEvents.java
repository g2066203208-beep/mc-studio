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
            // Preserve the outward wall-jump animation after the physical grip
            // releases, then send ONE authoritative inactive update at the end.
            boolean active = state.enabled
                    || (state.peakHeld && state.wallJumpAnimationTicks > 0);
            if (serverPlayer.tickCount % 3 == 0 || (state.previouslySynced && !active)) {
                if (active || state.previouslySynced) {
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer,
                            new ClimbNetwork.Pose(serverPlayer.getId(), active,
                                    state.peakPhase, state.poseAction,
                                    state.contactNormal, state.contactPoint, state.grabYaw));
                }
                state.previouslySynced = active;
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
