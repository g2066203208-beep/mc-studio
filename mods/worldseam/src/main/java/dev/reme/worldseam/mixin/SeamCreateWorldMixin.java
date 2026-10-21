package dev.reme.worldseam.mixin;

import dev.reme.worldseam.SeamWorldCreation;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tie the configured stack to the ACTUAL create-world action, not to an
 * arbitrary time while browsing the world-creation UI.
 */
@Mixin(CreateWorldScreen.class)
public abstract class SeamCreateWorldMixin {
    @Inject(method="onCreate",at=@At("HEAD"))
    private void worldseam$bindCreationStack(CallbackInfo ci) {
        SeamWorldCreation.onActualCreate((CreateWorldScreen)(Object)this);
    }
}
