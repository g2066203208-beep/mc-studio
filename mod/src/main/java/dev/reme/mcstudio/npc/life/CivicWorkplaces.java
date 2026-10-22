package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * No imaginary "city buildings": a tiny, explicitly bounded loaded-world
 * workstation survey, based only on physically present vanilla blocks.
 *
 * A worksite is not a complete house. Roof+door are independent indicators;
 * they are not silently converted into a new building or new residents.
 * No remote chunk access or territory mutation, and no world-wide scans.
 */
public final class CivicWorkplaces {
    private CivicWorkplaces() {}
    public enum Type {
        SHOP, BAKERY, SMITHY, FARM, INN, LIBRARY, GUARD_POST, UNASSIGNED
    }
    public record Survey(Type type, BlockPos center, int requiredBlocks,
                         boolean roof, boolean door, int sampledBlocks) {
        public boolean indoorBuilding() { return roof && door; }
        public boolean operational() { return type != Type.UNASSIGNED; }
    }

    public static Survey inspect(ServerLevel level, BlockPos anchor) {
        if (!level.isInWorldBounds(anchor)
            || !level.getWorldBorder().isWithinBounds(anchor)
            || !level.hasChunkAt(anchor)) {
            return new Survey(Type.UNASSIGNED, anchor.immutable(), 0, false, false, 0);
        }
        int chest=0, barrel=0, crafting=0, furnace=0, blast=0, anvil=0,
            farmland=0, composter=0, bed=0, bookshelf=0, lectern=0, bars=0,
            bell=0, doors=0, visited=0;
        // Keep independent coverage masks for y+2 and y+3. A few pillars,
        // furnishings or scattered solids do not make a real ceiling.
        int[] roofCoverage = new int[2];
        // 5x5x4 = 100 block checks maximum, only on loaded chunks.
        for (int x=-2;x<=2;x++) for(int z=-2;z<=2;z++)
            for(int y=0;y<=3;y++) {
                BlockPos pos=anchor.offset(x,y,z);
                if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) continue;
                BlockState state=level.getBlockState(pos);
                visited++;
                if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) chest++;
                if (state.is(Blocks.BARREL)) barrel++;
                if (state.is(Blocks.CRAFTING_TABLE)) crafting++;
                if (state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER)) furnace++;
                if (state.is(Blocks.BLAST_FURNACE)) blast++;
                if (state.is(Blocks.ANVIL) || state.is(Blocks.CHIPPED_ANVIL)
                    || state.is(Blocks.DAMAGED_ANVIL)) anvil++;
                if (state.is(Blocks.FARMLAND)) farmland++;
                if (state.is(Blocks.COMPOSTER)) composter++;
                if (state.is(Blocks.RED_BED) || state.is(Blocks.WHITE_BED)
                    || state.is(Blocks.BLUE_BED) || state.is(Blocks.BROWN_BED)
                    || state.is(Blocks.YELLOW_BED)) bed++;
                if (state.is(Blocks.BOOKSHELF) || state.is(Blocks.CHISELED_BOOKSHELF))
                    bookshelf++;
                if (state.is(Blocks.LECTERN)) lectern++;
                if (state.is(Blocks.IRON_BARS)) bars++;
                if (state.is(Blocks.BELL)) bell++;
                if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                    && state.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                    && level.hasChunkAt(pos.above())
                    && level.getBlockState(pos.above()).is(state.getBlock())
                    && level.getBlockState(pos.above()).getValue(
                        net.minecraft.world.level.block.DoorBlock.HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                    doors++;
                if ((y == 2 || y == 3) && Math.abs(x) <= 1
                    && Math.abs(z) <= 1 && state.isSolid())
                    roofCoverage[y - 2]++;
            }
        Type type=Type.UNASSIGNED;
        if (chest > 0 && barrel > 0) type=Type.SHOP;
        else if (crafting > 0 && furnace > 0) type=Type.BAKERY;
        else if (anvil > 0 && blast > 0) type=Type.SMITHY;
        else if (composter > 0 && farmland > 0) type=Type.FARM;
        else if (bed > 0 && barrel > 0) type=Type.INN;
        else if (bookshelf > 0 && lectern > 0) type=Type.LIBRARY;
        else if (bell > 0 && bars > 0) type=Type.GUARD_POST;
        // A full contiguous 3x3 ceiling at ONE height and a real door are
        // separate indicators; unrelated blocks must not fake a building.
        // Do not report a
        // full building merely because a two-block shop counter exists.
        return new Survey(type, anchor.immutable(),
            type == Type.UNASSIGNED ? 0 : 2,
            roofCoverage[0] == 9 || roofCoverage[1] == 9, doors > 0, visited);
    }

    public static boolean matchesProfession(Type type, PlayerNpcEntity.Profession role) {
        return switch (type) {
            case SHOP -> role == PlayerNpcEntity.Profession.SHOPKEEPER;
            case BAKERY -> role == PlayerNpcEntity.Profession.BAKER;
            case FARM -> role == PlayerNpcEntity.Profession.FARMER;
            case GUARD_POST -> role == PlayerNpcEntity.Profession.GUARD;
            case INN, LIBRARY, SMITHY -> role == PlayerNpcEntity.Profession.RESIDENT;
            case UNASSIGNED -> false;
        };
    }
}
