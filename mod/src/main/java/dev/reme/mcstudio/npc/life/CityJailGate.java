package dev.reme.mcstudio.npc.life;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Materialized vanilla iron cell gate. Only operates an already placed
 * properly oriented, matching upper/lower iron door beside the actual holding
 * block; never creates a prison, breaks walls or touches arbitrary player doors.
 * City demo uses a DIRECT SOUTH-ENTRY intake corridor: holding + south(2).
 */
public final class CityJailGate {
    private CityJailGate() {}

    public static BlockPos gateFor(BlockPos holding) {
        return holding.south(2);
    }

    public static boolean isGate(ServerLevel level, BlockPos holding) {
        BlockPos at=gateFor(holding);
        if(!level.hasChunkAt(at)||!level.hasChunkAt(at.above()))return false;
        BlockState low=level.getBlockState(at),up=level.getBlockState(at.above());
        return low.is(Blocks.IRON_DOOR) && up.is(Blocks.IRON_DOOR)
            && low.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER
            && up.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER;
    }

    public static boolean isOpen(ServerLevel level, BlockPos holding) {
        return isGate(level,holding)
            && level.getBlockState(gateFor(holding)).getValue(DoorBlock.OPEN);
    }

    public static boolean setOpen(ServerLevel level, BlockPos holding, boolean open) {
        if (!isGate(level, holding)) return false;
        BlockPos at=gateFor(holding);
        BlockState low=level.getBlockState(at),up=level.getBlockState(at.above());
        if(low.getValue(DoorBlock.OPEN)==open&&up.getValue(DoorBlock.OPEN)==open)
            return true;
        // Update both real vanilla halves in one tick on the server thread.
        // The iron door's normal collision/shape updates on subsequent ticks.
        level.setBlock(at,low.setValue(DoorBlock.OPEN,open),3);
        level.setBlock(at.above(),up.setValue(DoorBlock.OPEN,open),3);
        System.out.println("REME_NPC_STORY stage=jail_gate_"+(open?"open":"locked")
            +" tick="+level.getGameTime()+" location="+at.toShortString());
        return true;
    }
}
