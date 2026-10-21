package dev.reme.mcstudio;

import com.mojang.logging.LogUtils;
import dev.reme.mcstudio.npc.NpcCommands;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.life.NaturalResidentSpawn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;

@Mod(StudioMod.MOD_ID)
public final class StudioMod {
    public static final String MOD_ID = "reme_studio";

    public StudioMod(IEventBus modBus) {
        NpcRegistry.register(modBus);
        modBus.addListener(NpcRegistry::onEntityAttributes);
        modBus.addListener(NaturalResidentSpawn::register);
        NeoForge.EVENT_BUS.addListener(NpcCommands::register);
        NeoForge.EVENT_BUS.addListener(StudioMod::onPlayerLogin);
        LogUtils.getLogger().info("REME NPC 0.5.2: MOD CONSTRUCTED; on player login will check /npc command");
    }
    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var server = player.getServer();
        boolean commandsAvailable = server != null &&
            server.getCommands().getDispatcher().getRoot().getChild("npc") != null;
        String status = commandsAvailable
            ? "REME NPC v0.5.9-dev loaded; /npc registered. Use /npc spawn (cheats enabled)."
            : "REME NPC v0.5.9-dev loaded, but /npc command MISSING. Send logs/latest.log.";
        player.sendSystemMessage(Component.literal(status));
        if (!commandsAvailable)
            LogUtils.getLogger().error("REME NPC: COMMAND REGISTRATION MISSING despite Mod loaded");
        else
            LogUtils.getLogger().info("REME NPC: /npc present on dispatcher for player {}", player.getScoreboardName());
    }
}

