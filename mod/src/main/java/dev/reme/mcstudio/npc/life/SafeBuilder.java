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

    private record Step(BlockPos pos, Block block, Item item) {}

    private static Step planned(BlockPos anchor, int index) {
        int cursor = 0;
        for (int y = 0; y <= 2; y++) for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++) {
                boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
                if (y < 2 && !wall) continue;
                if (y < 2 && x == 0 && z == -2) continue; // walkable door
                if (cursor++ != index) continue;
                boolean roof = y == 2;
                return new Step(anchor.offset(x, y, z),
                    roof ? Blocks.OAK_PLANKS : Blocks.COBBLESTONE,
                    roof ? Items.OAK_PLANKS : Items.COBBLESTONE);
            }
        return null;
    }

    public static boolean validateSite(ServerLevel level, BlockPos anchor) {
        for (int i = 0; ; i++) {
            Step step = planned(anchor, i);
            if (step == null) return true;
            if (!level.hasChunkAt(step.pos()) ||
                !level.getBlockState(step.pos()).isAir()) return false;
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
        Step step = planned(anchor, npc.buildCursor());
        if (step == null) {
            npc.setBuildEnabled(false);
            npc.setAction("completed tiny shelter with real materials");
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
