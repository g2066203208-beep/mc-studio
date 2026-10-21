package dev.reme.worldseam.mixin;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPacketListener.class)
public interface SeamPacketListenerAccess {
    @Accessor("level")
    void worldseam$setLevel(ClientLevel world);
}
