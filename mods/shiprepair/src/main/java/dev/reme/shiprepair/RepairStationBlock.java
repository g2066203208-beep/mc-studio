package dev.reme.shiprepair;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class RepairStationBlock extends BaseEntityBlock {
    public static final MapCodec<RepairStationBlock> CODEC=simpleCodec(RepairStationBlock::new);
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    public RepairStationBlock(Properties p) { super(p); }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos,BlockState s) {
        return new RepairStationBlockEntity(pos,s);
    }
    @Override protected InteractionResult useWithoutItem(
            BlockState s,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(level.isClientSide()) return InteractionResult.SUCCESS;
        if(level.getBlockEntity(pos) instanceof RepairStationBlockEntity station)
            station.click(player,player.isShiftKeyDown());
        return InteractionResult.SUCCESS;
    }
}
