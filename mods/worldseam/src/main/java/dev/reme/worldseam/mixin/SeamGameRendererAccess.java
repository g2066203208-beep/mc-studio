package dev.reme.worldseam.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Scoped secondary-world lightmap binding; never a permanent renderer swap. */
@Mixin(GameRenderer.class)
public interface SeamGameRendererAccess {
    @Mutable @Accessor("lightTexture")
    void worldseam$setLightTexture(LightTexture lightTexture);
}
