package dev.reme.worldseam.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.SequencedMap;

/** Access only to release memory owned by WorldSeam's secondary RenderBuffers. */
@Mixin(MultiBufferSource.BufferSource.class)
public interface SeamBufferSourceAccess {
    @Accessor("sharedBuffer")
    ByteBufferBuilder worldseam$getSharedBuffer();

    @Accessor("fixedBuffers")
    SequencedMap<RenderType,ByteBufferBuilder> worldseam$getFixedBuffers();
}
