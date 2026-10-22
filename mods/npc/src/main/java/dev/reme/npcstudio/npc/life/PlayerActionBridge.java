package dev.reme.npcstudio.npc.life;

import dev.reme.npcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Adapter for server-authoritative player-like NPC actions.
 *
 * Reuses the vanilla ItemStack / BlockItem placement pipeline with a NeoForge
 * fake player, rather than bypassing item logic by directly setting blocks.
 * This is a bounded FIRST STEP, not an assertion that an NPC is a ServerPlayer:
 * mining, crafting, permissions, player networking and GUI need their own
 * adapters and real integration tests.
 *
 * Explicit operator-armed building only. Do not call from an arbitrary AI
 * prompt. Every target and supporting chunk must already be loaded.
 */
public final class PlayerActionBridge {
    private PlayerActionBridge() {}

    private static final Direction[] FACE_PRIORITY = {
        Direction.UP, Direction.NORTH, Direction.SOUTH,
        Direction.EAST, Direction.WEST, Direction.DOWN
    };

    /**
     * Place ONE actual inventory block into an empty position using the
     * underlying BlockItem.useOn path (and its NeoForge placement events).
     * On failure, the NPC's item is not consumed. Other mods may veto placement.
     */
    public static boolean placeOwnedBlock(PlayerNpcEntity npc, ServerLevel level,
                                           BlockPos target, int slot, Block expected) {
        if (!level.getServer().isSameThread()
            || !npc.isAlive()
            || !npc.buildEnabled()
            || !level.isInWorldBounds(target)
            || !level.getWorldBorder().isWithinBounds(target)
            || !level.hasChunkAt(target)
            || !level.getBlockState(target).isAir()
            || npc.distanceToSqr(Vec3.atCenterOf(target)) > 36.0)
            return false;

        if (slot < 0 || slot >= npc.backpack().getContainerSize()) return false;
        ItemStack source = npc.backpack().getItem(slot);
        if (source.isEmpty() || !(source.getItem() instanceof BlockItem blockItem)
            || blockItem.getBlock() != expected) return false;

        // Identity is stable across actions. FakePlayerFactory scopes players
        // to ServerLevel and disposes its cache on level unload.
        FakePlayer actor = NpcPlayerActor.forNpc(npc, level);
        ItemStack oldHand = actor.getItemInHand(InteractionHand.MAIN_HAND).copy();

        try {
            for (Direction face : FACE_PRIORITY) {
                BlockPos support = target.relative(face.getOpposite());
                if (!level.hasChunkAt(support)) continue;
                BlockState supportState = level.getBlockState(support);
                if (supportState.isAir() || !supportState.isFaceSturdy(level, support, face))
                    continue;

                // Temporary single item: cannot spend another NPC's inventory,
                // and failure never removes anything from the real backpack.
                ItemStack one = source.copy();
                one.setCount(1);
                actor.setItemInHand(InteractionHand.MAIN_HAND, one);
                Vec3 hitPoint = Vec3.atCenterOf(support).add(
                    face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                BlockHitResult hit = new BlockHitResult(hitPoint, face, support, false);
                InteractionResult result = one.useOn(
                    new UseOnContext(actor, InteractionHand.MAIN_HAND, hit));
                if (!result.consumesAction()) continue;
                // The real block must have appeared. An event reporting
                // success without placement is NOT a successful build.
                if (!level.getBlockState(target).is(expected)) return false;
                // If an event did not consume the temporary item, we refuse to
                // give an unpaid block. This also handles creative bypasses.
                if (!one.isEmpty()) {
                    level.removeBlock(target, false);
                    return false;
                }
                source.shrink(1);
                npc.backpack().setChanged();
                return true;
            }
            return false;
        } finally {
            actor.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
        }
    }
}
