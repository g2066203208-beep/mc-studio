package dev.reme.worldseam.mixin;

import dev.reme.worldseam.SeamForeignFullLevelPass;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The genuine foreign sky is drawn once using the original projection before
 * the second LevelRenderer draws terrain with the physical oblique seam clip.
 * Vanilla renderLevel also invokes renderSky: drawing it a second time using
 * the terrain clip can erase portions of the real sky at the seam.
 *
 * Only the active foreign renderer with a completed native sky prepass is
 * affected. Vanilla/primary-world sky and non-clipped passes are untouched.
 */
@Mixin(LevelRenderer.class)
public abstract class SeamLevelRendererSkyMixin {
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void worldseam$preserveNativeSky(Matrix4f modelView,
                                              Matrix4f projection,
                                              float partialTick,
                                              Camera camera,
                                              boolean foggy,
                                              Runnable setupFog,
                                              CallbackInfo ci) {
        if (!SeamForeignFullLevelPass.suppressClippedDuplicateSky(
                (LevelRenderer) (Object) this)) return;
        // Preserve the vanilla caller's fog callback even when the duplicate
        // raster draw is skipped. This does not replace sky geometry or color.
        setupFog.run();
        ci.cancel();
    }
}
