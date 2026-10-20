package dev.reme.mcstudio.climb;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Server-authoritative offhand item pipeline while right hand is gripping.
 * Never creates a weapon or ammo; bow uses the ordinary vanilla inventory,
 * charge duration and releaseUsingItem mechanics.
 */
public final class OffhandActions {
    private OffhandActions() {}

    public static void accept(ServerPlayer player, int sequence, boolean down) {
        ClimbState state = ClimbState.of(player);
        if (sequence <= state.sequence) return;
        state.sequence = sequence;
        state.lastInputTick = player.level().getGameTime();

        if (!down) {
            if (state.usingLeftItem && player.isUsingItem()
                    && player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                player.releaseUsingItem();
            }
            state.usingLeftItem = false;
            return;
        }
        if (!state.enabled || state.right == null || state.left != null
                || state.usingLeftItem || player.getOffhandItem().isEmpty()
                || !player.isAlive() || player.isSpectator()) return;

        // This invokes the existing vanilla use pipeline (bow/food/throwable/etc),
        // including the normal arrow requirement and item durability handling.
        player.gameMode.useItem(player, player.level(), player.getOffhandItem(), InteractionHand.OFF_HAND);
        state.usingLeftItem = player.isUsingItem()
                && player.getUsedItemHand() == InteractionHand.OFF_HAND;
    }
}
