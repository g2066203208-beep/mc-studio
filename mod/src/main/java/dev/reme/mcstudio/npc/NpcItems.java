package dev.reme.mcstudio.npc;

import dev.reme.mcstudio.StudioMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Actual user-facing NPC spawning without commands/cheats (egg or craft). */
public final class NpcItems {
    private static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(StudioMod.MOD_ID);

    public static final DeferredItem<DeferredSpawnEggItem> NPC_SPAWN_EGG =
        ITEMS.registerItem("npc_spawn_egg",
            props -> new DeferredSpawnEggItem(
                NpcRegistry.PLAYER_NPC, 0x436F88, 0xD8B98C, props));

    private NpcItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(NpcItems::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS)
            event.accept(NPC_SPAWN_EGG);
    }
}
