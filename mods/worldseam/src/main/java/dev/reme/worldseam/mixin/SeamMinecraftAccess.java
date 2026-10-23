package dev.reme.worldseam.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface SeamMinecraftAccess {
    @Mutable @Accessor("levelRenderer")
    void worldseam$setLevelRenderer(LevelRenderer renderer);

    /** The foreign renderer must not reuse active-world pending buffers. */
    @Mutable @Accessor("renderBuffers")
    void worldseam$setRenderBuffers(RenderBuffers buffers);
}
