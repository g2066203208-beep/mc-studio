package dev.reme.worldseam.mixin;

import dev.reme.worldseam.SeamNativeGeometryClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When a player changes a SOURCE boundary block, native TARGET mesh faces
 * touching that seam must be rebuilt. Otherwise stale faces remain visible
 * until a totally unrelated target-chunk packet forces an invalidation.
 */
@Mixin(ClientLevel.class)
public abstract class SeamClientBlockUpdateMixin {
    @Inject(method="sendBlockUpdated",at=@At("TAIL"))
    private void worldseam$invalidateJoin(BlockPos pos,BlockState oldState,
                                            BlockState newState,int flags,
                                            CallbackInfo ci) {
        if(oldState!=newState)
            SeamNativeGeometryClient.onSourceBoundaryBlockChanged(
                (ClientLevel)(Object)this,pos);
    }
}
