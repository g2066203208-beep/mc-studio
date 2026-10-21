package dev.reme.worldseam.mixin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractClientPlayer.class)
public interface SeamClientPlayerAccess {
    @Mutable @Accessor("clientLevel")
    void worldseam$setClientLevel(ClientLevel world);
}
