package dev.reme.worldseam.mixin;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface SeamMinecraftAccess {
    @Mutable @Accessor("mainRenderTarget")
    void worldseam$setMainRenderTarget(RenderTarget target);

    @Mutable @Accessor("levelRenderer")
    void worldseam$setLevelRenderer(LevelRenderer renderer);

    /** The foreign renderer must not reuse active-world pending buffers. */
    @Mutable @Accessor("renderBuffers")
    void worldseam$setRenderBuffers(RenderBuffers buffers);
}
