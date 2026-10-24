package dev.reme.worldseam.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Direct field swap: ParticleEngine.setLevel() would clear live particles. */
@Mixin(ParticleEngine.class)
public interface SeamParticleEngineAccess {
    @Accessor("level") ClientLevel worldseam$getLevel();
    @Accessor("level") void worldseam$setLevel(ClientLevel level);
}
