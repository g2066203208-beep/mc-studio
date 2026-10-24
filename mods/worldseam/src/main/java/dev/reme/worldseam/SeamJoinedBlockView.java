package dev.reme.worldseam;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.Blocks;

/**
 * Rendering-only, read-only *native* BlockAndTintGetter. Minecraft's Level
 * instances remain separate, but tessellation looks up the real neighbor
 * BlockState/FluidState across the single configured horizontal seam plane.
 *
 * This prevents the renderer treating the target world ceiling/floor as
 * bordering AIR when it is actually touching source blocks/water.
 *
 * Never fetch a missing chunk synchronously, write blocks, tick logic, nor
 * swap Minecraft.level; this is NOT an authoritative physics collision world.
 */
public final class SeamJoinedBlockView implements BlockAndTintGetter {
    private final ClientLevel target;
    private final ClientLevel source;
    private final int sourceBoundary,targetBoundary;
    private final SeamGraph.EdgeSide entry;

    public SeamJoinedBlockView(ClientLevel target,ClientLevel source,
                               int sourceBoundary,int targetBoundary,
                               SeamGraph.EdgeSide entry) {
        this.target=target;
        this.source=source;
        this.sourceBoundary=sourceBoundary;
        this.targetBoundary=targetBoundary;
        this.entry=entry;
    }

    private BlockPos remoteSourcePos(BlockPos pos) {
        Integer y=SeamNeighborMapping.loadedSourceY(
            pos.getY(),sourceBoundary,targetBoundary,
            source.getMinBuildHeight(),source.getMaxBuildHeight(),entry);
        if(y==null || !source.getChunkSource().hasChunk(
            Math.floorDiv(pos.getX(),16),Math.floorDiv(pos.getZ(),16)))
            return null;
        return new BlockPos(pos.getX(),y,pos.getZ());
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        if(!SeamNeighborMapping.outsideTarget(pos.getY(),targetBoundary,entry))
            return target.getBlockState(pos);
        BlockPos mapped=remoteSourcePos(pos);
        return mapped==null?Blocks.AIR.defaultBlockState():source.getBlockState(mapped);
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override public BlockEntity getBlockEntity(BlockPos pos) {
        if(!SeamNeighborMapping.outsideTarget(pos.getY(),targetBoundary,entry))
            return target.getBlockEntity(pos);
        BlockPos mapped=remoteSourcePos(pos);
        return mapped==null?null:source.getBlockEntity(mapped);
    }

    @Override public int getBlockTint(BlockPos pos,ColorResolver resolver) {
        BlockPos mapped=SeamNeighborMapping.outsideTarget(
            pos.getY(),targetBoundary,entry)?remoteSourcePos(pos):null;
        return mapped==null?target.getBlockTint(pos,resolver):
            source.getBlockTint(mapped,resolver);
    }

    @Override public int getBrightness(LightLayer light,BlockPos pos) {
        BlockPos mapped=SeamNeighborMapping.outsideTarget(
            pos.getY(),targetBoundary,entry)?remoteSourcePos(pos):null;
        return mapped==null?target.getBrightness(light,pos):
            source.getBrightness(light,mapped);
    }

    @Override public int getRawBrightness(BlockPos pos,int subtract) {
        BlockPos mapped=SeamNeighborMapping.outsideTarget(
            pos.getY(),targetBoundary,entry)?remoteSourcePos(pos):null;
        return mapped==null?target.getRawBrightness(pos,subtract):
            source.getRawBrightness(mapped,subtract);
    }

    // Target shaders still resolve the target's biome and native light
    // engine. Queries beyond its native height are individually redirected
    // above, avoiding a shared, mutable fake LevelLightEngine.
    @Override public LevelLightEngine getLightEngine() {
        return target.getLightEngine();
    }

    @Override public float getShade(Direction face,boolean shade) {
        return target.getShade(face,shade);
    }

    @Override public int getHeight() {
        return target.getHeight();
    }

    @Override public int getMinBuildHeight() {
        return target.getMinBuildHeight();
    }
}
