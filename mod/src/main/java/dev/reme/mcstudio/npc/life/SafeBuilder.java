package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

/**
 * Explicit operator-armed tiny shelter construction, one actual block per step.
 * 5x5 footprint, two wall layers, door opening, flat roof.
 *
 * Not general schematics or a protection-mod bypass: never overwrite or dig.
 * No auto construction unless an operator explicitly starts it in test world.
 */
public final class SafeBuilder {
    private SafeBuilder() {}

    /** Three actual block-for-block construction types. Default SHELTER
     * preserves previous NBT and player worlds without any migration. No
     * fake /structure load; every material is consumed from real NPC bag.
     */
    public enum Template { SHELTER, SHOP, BAKERY }

    private record Step(BlockPos pos, Block block, Item item) {}

    private static Step planned(BlockPos anchor, int index, Template blueprint) {
        int cursor = 0;
        for (int y = 0; y <= 2; y++) for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) {
                boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
                if (y < 2 && !wall) continue;
                if (y < 2 && x == 0 && z == -2) continue; // entrance arch
                if (cursor++ != index) continue;
                boolean roof = y == 2;
                Block block;
                Item item;
                if (blueprint == Template.SHOP) {
                    boolean timberCorner = !roof && Math.abs(x) == 2 && Math.abs(z) == 2;
                    block = roof ? Blocks.SPRUCE_PLANKS :
                        timberCorner ? Blocks.OAK_LOG : Blocks.OAK_PLANKS;
                    item = roof ? Items.SPRUCE_PLANKS :
                        timberCorner ? Items.OAK_LOG : Items.OAK_PLANKS;
                } else if (blueprint == Template.BAKERY) {
                    block = roof ? Blocks.OAK_PLANKS : Blocks.BRICKS;
                    item = roof ? Items.OAK_PLANKS : Items.BRICKS;
                } else {
                    block = roof ? Blocks.OAK_PLANKS : Blocks.COBBLESTONE;
                    item = roof ? Items.OAK_PLANKS : Items.COBBLESTONE;
                }
                return new Step(anchor.offset(x, y, z), block, item);
            }
        if (blueprint == Template.SHELTER) return null;
        // Place furniture inside the real building through actual vanilla
        // ItemStack.useOn. Chest and barrel are concrete inventories; a
        // bakery has functional furnace and crafting table.
        if (index == cursor)
            return new Step(anchor.offset(-1, 0, 0),
                blueprint == Template.SHOP ? Blocks.CHEST : Blocks.FURNACE,
                blueprint == Template.SHOP ? Items.CHEST : Items.FURNACE);
        if (index == cursor + 1)
            return new Step(anchor.offset(1, 0, 0),
                blueprint == Template.SHOP ? Blocks.BARREL : Blocks.CRAFTING_TABLE,
                blueprint == Template.SHOP ? Items.BARREL : Items.CRAFTING_TABLE);
        if (index == cursor + 2)
            return new Step(anchor.offset(0, 0, -2),
                Blocks.OAK_DOOR, Items.OAK_DOOR);
        return null;
    }

    public static int steps(Template type) {
        int count = 0;
        while (planned(BlockPos.ZERO, count, type) != null) count++;
        return count;
    }

    public static boolean validateSite(ServerLevel level, BlockPos anchor) {
        return validateSite(level, anchor, Template.SHELTER);
    }

    public static boolean validateSite(ServerLevel level, BlockPos anchor, Template type) {
        for (int i = 0; ; i++) {
            Step step = planned(anchor, i, type);
            if (step == null) return true;
            if (!level.hasChunkAt(step.pos()) ||
                !level.getBlockState(step.pos()).isAir()) return false;
            if (step.block() == Blocks.OAK_DOOR &&
                (!level.hasChunkAt(step.pos().above())
                 || !level.getBlockState(step.pos().above()).isAir()))
                return false;
            if (!level.getEntities((net.minecraft.world.entity.Entity) null,
                new AABB(step.pos())).isEmpty()) return false;
        }
    }

    public static void tick(PlayerNpcEntity npc, ServerLevel level, long tick) {
        if (!npc.buildEnabled() || tick % 100 != Math.floorMod(npc.getId(), 100)) return;
        BlockPos anchor = npc.workPos();
        if (npc.blockPosition().distSqr(anchor) > 70) {
            RoadNavigator.moveToward(npc, level, anchor.offset(3, 0, 3), 1.0);
            npc.setAction("travelling to assigned house construction");
            return;
        }
        Step step = planned(anchor, npc.buildCursor(), npc.buildTemplate());
        if (step == null) {
            npc.setBuildEnabled(false);
            npc.setAction("completed " + npc.buildTemplate()
                + " with real block placements and consumed materials");
            return;
        }
        if (!level.hasChunkAt(step.pos())) {
            npc.setAction("construction paused: unloaded chunk");
            return;
        }
        if (!level.getBlockState(step.pos()).isAir() ||
            !level.getEntities((net.minecraft.world.entity.Entity) null,
                new AABB(step.pos())).isEmpty()) {
            npc.setAction("construction blocked; will not overwrite");
            return;
        }
        int slot = -1;
        for (int i = 0; i < npc.backpack().getContainerSize(); i++)
            if (npc.backpack().getItem(i).is(step.item())) { slot = i; break; }
        if (slot < 0) {
            npc.setAction("construction waiting for " + step.item());
            return;
        }
        // Reach and click the site like a player before spending material.
        if (npc.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(step.pos())) > 36.0) {
            BlockPos approach = step.pos().offset(
                step.pos().getX() >= anchor.getX() ? 1 : -1, 0,
                step.pos().getZ() >= anchor.getZ() ? 1 : -1);
            RoadNavigator.moveToward(npc, level, approach, 0.9);
            npc.setAction("walking into player interaction reach of building site");
            return;
        }
        if (!PlayerActionBridge.placeOwnedBlock(npc, level, step.pos(), slot, step.block())) {
            npc.setAction("construction paused: item-use failed, no material spent");
            return;
        }
        npc.advanceBuildCursor();
        npc.journey().succeeded(LifeJourney.Achievement.BUILD);
        npc.setAction("used real player block-placement pipeline for " + step.item()
            + " at " + step.pos().toShortString());
    }
}
