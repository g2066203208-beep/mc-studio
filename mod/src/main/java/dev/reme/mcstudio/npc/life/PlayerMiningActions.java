package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * One operator-authorized mining target; delegates completion to the vanilla
 * ServerPlayerGameMode.destroyBlock pipeline (NeoForge BreakEvent, drops,
 * tool wear). The progress calculation is the vanilla survival break delta.
 *
 * An autonomous miner may only use this after assigning a real permitted work
 * site. Never mine every random block found near players or unloaded chunks.
 */
public final class PlayerMiningActions {
    private PlayerMiningActions() {}

    // Conservative opt-in surface. No block entities, valuables, doors,
    // protected structures, machines or arbitrary modded blocks at this stage.
    public static boolean allowed(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
            || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE)
            || state.is(Blocks.GRANITE) || state.is(Blocks.TUFF)
            || state.is(Blocks.COAL_ORE) || state.is(Blocks.IRON_ORE)
            || state.is(Blocks.COPPER_ORE);
    }

    /**
     * Called on the authoritative server thread every tick while a target
     * exists. Returns true when mining owns the current action slot.
     */
    public static boolean tick(PlayerNpcEntity npc, ServerLevel level) {
        BlockPos target = npc.miningTarget();
        if (target == null) return false;
        if (!level.getServer().isSameThread() || !npc.isAlive()
            || npc.mode() != PlayerNpcEntity.Mode.LIVE) return false;

        if (!level.isInWorldBounds(target)
            || !level.getWorldBorder().isWithinBounds(target)
            || !level.hasChunkAt(target)
            || !DailyRoaming.inRange(npc, target)) {
            abort(npc, level, "mining cancelled: outside loaded authorized territory");
            return true;
        }
        BlockState state = level.getBlockState(target);
        if (!allowed(state) || state.hasBlockEntity() || state.isAir()) {
            abort(npc, level, "mining cancelled: target no longer eligible");
            return true;
        }
        if (npc.intent() == LivingPlanner.Intent.ESCAPE || npc.isAfraid(level.getGameTime())) {
            level.destroyBlockProgress(npc.getId(), target, -1);
            npc.resetMiningProgress();
            return false; // threat response preempts work; target survives
        }

        // Do not mine remotely and do not teleport to the destination.
        double distance = npc.distanceToSqr(Vec3.atCenterOf(target));
        if (distance > 30.25) {
            level.destroyBlockProgress(npc.getId(), target, -1);
            npc.resetMiningProgress();
            RoadNavigator.moveToward(npc, level, target, 0.98);
            npc.setAction("walking into reach of assigned ore");
            return true;
        }

        ItemStack tool = npc.getMainHandItem();
        if (tool.isEmpty() || (state.requiresCorrectToolForDrops()
            && !tool.isCorrectToolForDrops(state))) {
            abort(npc, level, "mining cancelled: requires correct main-hand tool");
            return true;
        }

        FakePlayer player = NpcPlayerActor.forNpc(npc, level);
        ItemStack previous = player.getMainHandItem().copy();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
            float delta = state.getDestroyProgress(player, level, target);
            if (!Float.isFinite(delta) || delta <= 0.0F) {
                abort(npc, level, "mining cancelled: unbreakable with this tool");
                return true;
            }
            float progress = npc.advanceMiningProgress(delta);
            if (progress < 1.0F) {
                level.destroyBlockProgress(npc.getId(), target,
                    Math.min(9, Math.max(0, (int) (progress * 10.0F))));
                npc.setAction("mining " + target.toShortString() +
                    " with real tool (" + Math.round(progress * 100) + "%)");
                return true;
            }

            // This fires the real vanilla/NeoForge player break pipeline.
            // It must NOT be replaced by level.setBlock(AIR) or drops conjured
            // from an independently simulated loot table.
            boolean destroyed = player.gameMode.destroyBlock(target);
            level.destroyBlockProgress(npc.getId(), target, -1);
            npc.resetMiningProgress();
            if (destroyed && level.getBlockState(target).isAir()) {
                // The player action may have damaged or broken the copied tool.
                npc.setItemSlot(EquipmentSlot.MAINHAND,
                    player.getMainHandItem().copy());
                npc.clearMiningTarget();
                npc.setAction("mined " + target.toShortString()
                    + " through vanilla player action; drops in world");
            } else {
                // Canceled by permissions/events: never reattempt bypass.
                npc.clearMiningTarget();
                npc.setAction("mining denied by world or protection event");
            }
            return true;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        }
    }

    private static void abort(PlayerNpcEntity npc, ServerLevel level, String reason) {
        BlockPos target = npc.miningTarget();
        if (target != null) level.destroyBlockProgress(npc.getId(), target, -1);
        npc.clearMiningTarget();
        npc.setAction(reason);
    }
}
