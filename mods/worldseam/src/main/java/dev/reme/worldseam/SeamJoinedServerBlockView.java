package dev.reme.worldseam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only REAL two-ServerLevel native voxel atlas, looking out from one
 * Sable ship's home physics chart through its configured WorldSeam plane.
 *
 * A Rapier collider baker eventually needs a BLOCK-accurate view across the
 * plane, not fake air, a second ship or vanilla Nether Y128..255 blocks.
 * This view is the safe world-data prerequisite for that integration.
 *
 * Missing native chunks FAIL CLOSED with UnloadedNeighborChunkException.
 * In particular, never generate chunks from a physics substep and never
 * silently substitute AIR as "no collision". The native Rapier image
 * collider producer must defer/disable contacts until this view is complete.
 */
public final class SeamJoinedServerBlockView implements BlockGetter {
    public static final class UnloadedNeighborChunkException
        extends IllegalStateException {
        public UnloadedNeighborChunkException(String message){super(message);}
    }

    private final ServerLevel home;
    private final ServerLevel neighbor;
    private final SeamGraph.EdgeSide exit;
    private final int homePlane,neighborPlane;

    public SeamJoinedServerBlockView(ServerLevel home,ServerLevel neighbor,
                                     SeamGraph.Link link) {
        if(home==null || neighbor==null || link==null ||
           !neighbor.dimension().location().toString().equals(link.target()))
            throw new IllegalArgumentException("Invalid or mismatched native world join");
        this.home=home;
        this.neighbor=neighbor;
        this.exit=link.exit();
        // All configured native seam planes are integral block heights;
        // boundaryY returns double for continuous entity coordinates.
        double hp=SeamGraph.boundaryY(exit,
            SeamStackRuntime.bottom(home),SeamStackRuntime.top(home));
        double np=SeamGraph.boundaryY(link.entry(),
            SeamStackRuntime.bottom(neighbor),SeamStackRuntime.top(neighbor));
        if(!Double.isFinite(hp)||!Double.isFinite(np)||
           hp!=Math.rint(hp)||np!=Math.rint(np)||
           hp<Integer.MIN_VALUE||hp>Integer.MAX_VALUE||
           np<Integer.MIN_VALUE||np>Integer.MAX_VALUE)
            throw new IllegalArgumentException("Non-integral/overflowed seam plane");
        this.homePlane=(int)hp;
        this.neighborPlane=(int)np;
    }

    /** Null means a block belongs to the native home world. */
    private @Nullable BlockPos remotePos(BlockPos pos) {
        if(!SeamNeighborMapping.outsideTarget(pos.getY(),homePlane,exit))
            return null;
        final int otherY;
        try {
            otherY=SeamNeighborMapping.sourceY(
                pos.getY(),neighborPlane,homePlane);
        } catch(ArithmeticException ex) {
            throw new IllegalStateException("WorldSeam mapped block coordinate overflow",ex);
        }
        if(otherY<SeamStackRuntime.bottom(neighbor) ||
           otherY>=SeamStackRuntime.top(neighbor))
            throw new UnloadedNeighborChunkException(
                "WorldSeam image outside configured neighboring world's logical height");
        int cx=Math.floorDiv(pos.getX(),16),cz=Math.floorDiv(pos.getZ(),16);
        if(!neighbor.hasChunk(cx,cz))
            throw new UnloadedNeighborChunkException(
                "Missing real "+neighbor.dimension().location()+" chunk "+cx+","+cz);
        return new BlockPos(pos.getX(),otherY,pos.getZ());
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        var remote=remotePos(pos);
        return remote==null?home.getBlockState(pos):neighbor.getBlockState(remote);
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        var remote=remotePos(pos);
        return remote==null?home.getBlockEntity(pos):neighbor.getBlockEntity(remote);
    }

    @Override public int getHeight(){return home.getHeight();}
    @Override public int getMinBuildHeight(){return home.getMinBuildHeight();}

    public int homePlane(){return homePlane;}
    public int neighborPlane(){return neighborPlane;}
}
