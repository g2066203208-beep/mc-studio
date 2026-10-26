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
 * touching that seam must be rebuilt. Edits in the secondary ClientLevel
 * also invalidate the target GPU sections and neighboring fluid faces;
 * neither path synchronously loads chunks or compiles VBOs.
 */
@Mixin(ClientLevel.class)
public abstract class SeamClientBlockUpdateMixin {
    @Inject(method="sendBlockUpdated",at=@At("TAIL"))
    private void worldseam$invalidateJoin(BlockPos pos,BlockState oldState,
                                            BlockState newState,int flags,
                                            CallbackInfo ci) {
        if(oldState!=newState) {
            ClientLevel changedLevel=(ClientLevel)(Object)this;
            SeamNativeGeometryClient.onSourceBoundaryBlockChanged(changedLevel,pos);
            SeamNativeGeometryClient.onRemoteBlockChanged(changedLevel,pos);
        }
    }
}
