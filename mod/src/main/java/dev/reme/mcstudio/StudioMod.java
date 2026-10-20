package dev.reme.mcstudio;

import com.mojang.logging.LogUtils;
import dev.reme.mcstudio.npc.NpcCommands;
import dev.reme.mcstudio.npc.NpcRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(StudioMod.MOD_ID)
public final class StudioMod {
    public static final String MOD_ID = "reme_studio";

    public StudioMod(IEventBus modBus) {
        NpcRegistry.register(modBus);
        modBus.addListener(NpcRegistry::onEntityAttributes);
        NeoForge.EVENT_BUS.addListener(NpcCommands::register);
        LogUtils.getLogger().info("REME MC Studio player NPC prototype loaded");
    }
}
