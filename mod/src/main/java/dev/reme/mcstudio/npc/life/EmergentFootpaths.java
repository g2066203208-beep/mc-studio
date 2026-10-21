package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Desire paths:
 *  - All actual moving residents (not standing NPCs) contribute local passage
 *    to bounded per-dimension SavedData.
 *  - Route scores prefer commonly visited corridors even with no terrain edits.
 *  - Physical footpath shaping is OFF by default, requires OP opt-in,
 *    mobGriefing and a natural exposed grass/dirt tile.
 *  - Uses the real vanilla shovel-on-block interaction and protection events,
 *    rather than directly calling level.setBlock(DIRT_PATH).
 */
public final class EmergentFootpaths {
    private EmergentFootpaths() {}

    public static boolean naturalTile(ServerLevel level, BlockPos foot) {
        if (!level.isInWorldBounds(foot) || !level.getWorldBorder().isWithinBounds(foot)
            || !level.hasChunkAt(foot) || !level.hasChunkAt(foot.below()))
            return false;
        BlockState ground = level.getBlockState(foot.below());
        // Don't alter crops, sand, logs, redstone, farmland, player-built
        // stone, snow, decorative plants or block-entity-bearing terrain.
        return (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT))
            && !ground.hasBlockEntity()
            && level.getBlockState(foot).isAir()
            && level.getBlockState(foot.above()).isAir()
            && level.canSeeSky(foot);
    }

    /** Actual moving NPC only; observe no more often than once per 12 ticks. */
    public static void observe(PlayerNpcEntity npc, ServerLevel level, long tick) {
        if (npc.mode() != PlayerNpcEntity.Mode.LIVE || !npc.isAlive()
            || !npc.onGround() || !MovementPolicy.due(tick, npc.getId(), 12))
            return;
        BlockPos foot = npc.blockPosition();
        if (!npc.hasNewFootTrafficPosition(foot)) return;
        if (!naturalTile(level, foot)) return;
        FootTrafficData data = FootTrafficData.get(level);
        if (!data.countPassage(npc.getUUID(), foot, tick)) return;
        if (data.physicalPathsEnabled() && data.visits(foot) >= FootTrafficData.PATH_THRESHOLD
            && !data.alreadyShaped(foot) && tryWearPath(npc, level, foot)) {
            data.markShaped(foot);
        }
    }

    /** One physical natural footpath wear step; reversible with vanilla dirt.
     * Same player-use protection/mod event path as a human right-click shovel.
     * Server operator must enable shaping; mobGriefing OFF always vetoes it.
     */
    public static boolean tryWearPath(PlayerNpcEntity npc, ServerLevel level, BlockPos foot) {
        if (!level.getServer().isSameThread()
            || !FootTrafficData.get(level).physicalPathsEnabled()
            || FootTrafficData.get(level).visits(foot) < FootTrafficData.PATH_THRESHOLD
            || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
            || !npc.isAlive() || !npc.onGround()
            || npc.distanceToSqr(Vec3.atCenterOf(foot)) > 9.0
            || !naturalTile(level, foot)) return false;

        BlockPos ground = foot.below();
        FakePlayer actor = NpcPlayerActor.forNpc(npc, level);
        ItemStack old = actor.getItemInHand(InteractionHand.MAIN_HAND).copy();
        try {
            // Shovel item-use is the *actual* game behavior that turns dirt /
            // grass into dirt_path. The temporary shovel represents natural
            // environmental tread, not an NPC inventory item, and it is never
            // copied into the resident's bag.
            ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
            actor.setItemInHand(InteractionHand.MAIN_HAND, shovel);
            BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0),
                Direction.UP, ground, false);
            InteractionResult result = shovel.useOn(
                new UseOnContext(actor, InteractionHand.MAIN_HAND, hit));
            return result.consumesAction()
                && level.getBlockState(ground).is(Blocks.DIRT_PATH);
        } finally {
            actor.setItemInHand(InteractionHand.MAIN_HAND, old);
        }
    }
}
