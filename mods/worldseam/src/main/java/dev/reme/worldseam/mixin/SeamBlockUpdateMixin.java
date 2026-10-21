package dev.reme.worldseam.mixin;

import dev.reme.worldseam.SeamChunkSender;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dimension-scoped native block invalidation. The vanilla ServerLevel still
 * owns all block updates. Only interested players' secondary loaded windows
 * are marked dirty; actual full packets are throttled by SeamChunkSender.
 */
@Mixin(ServerLevel.class)
public abstract class SeamBlockUpdateMixin {
    @Inject(method="sendBlockUpdated",at=@At("TAIL"))
    private void worldseam$nativeRemoteChange(BlockPos pos,BlockState oldState,
                                               BlockState newState,int flags,
                                               CallbackInfo ci) {
        if(oldState!=newState)
            SeamChunkSender.markBlockChanged((ServerLevel)(Object)this,pos);
    }
}
