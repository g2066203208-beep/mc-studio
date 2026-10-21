package dev.reme.worldseam.mixin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Entity internals used exclusively for experimental no-respawn crossing. */
@Mixin(Entity.class)
public interface SeamEntityAccess {
    @Accessor("level")
    void worldseam$setLevel(Level level);
    @Invoker("unsetRemoved")
    void worldseam$unsetRemoved();
}
