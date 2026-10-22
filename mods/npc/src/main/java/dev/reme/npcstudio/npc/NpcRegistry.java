package dev.reme.npcstudio.npc;

import dev.reme.npcstudio.NpcMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class NpcRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, NpcMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<PlayerNpcEntity>> PLAYER_NPC =
        ENTITIES.register("player_npc", () ->
            EntityType.Builder.of(PlayerNpcEntity::new, MobCategory.CREATURE)
                .sized(0.6F, 1.8F)
                .clientTrackingRange(10)
                .updateInterval(2)
                .build("player_npc"));

    private NpcRegistry() {}

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }

    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(PLAYER_NPC.get(), Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.29)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.FOLLOW_RANGE, 24.0)
            .add(Attributes.ARMOR, 0.0)
            .build());
    }
}
