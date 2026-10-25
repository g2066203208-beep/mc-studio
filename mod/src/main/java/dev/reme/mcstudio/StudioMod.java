package dev.reme.mcstudio;

import com.mojang.logging.LogUtils;
import dev.reme.mcstudio.npc.NpcCommands;
import dev.reme.mcstudio.npc.NpcCameraController;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.NpcItems;
import dev.reme.mcstudio.npc.life.NaturalResidentSpawn;
import dev.reme.mcstudio.npc.life.HomeEntranceRoute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;

@Mod(StudioMod.MOD_ID)
public final class StudioMod {
    public static final String MOD_ID = "reme_studio";
    public static final String VERSION = "0.7.2-dev";

    public StudioMod(IEventBus modBus) {
        NpcRegistry.register(modBus);
        NpcItems.register(modBus);
        modBus.addListener(NpcRegistry::onEntityAttributes);
        modBus.addListener(NaturalResidentSpawn::register);
        NeoForge.EVENT_BUS.addListener(NpcCommands::register);
        NeoForge.EVENT_BUS.addListener(NpcCameraController::onTick);
        NeoForge.EVENT_BUS.addListener(HomeEntranceRoute::onLevelTick);
        NeoForge.EVENT_BUS.addListener(HomeEntranceRoute::onPlayerUseDoor);
        NeoForge.EVENT_BUS.addListener(NpcCameraController::onLogout);
        NeoForge.EVENT_BUS.addListener(StudioMod::onPlayerLogin);
        LogUtils.getLogger().info("REME NPC {} CONSTRUCTED on NeoForge 21.1.262 baseline (Minecraft 1.21.1); commands register when the SERVER dispatcher is created, not at mod construction", VERSION);
    }
    private static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var server = player.getServer();
        boolean commandsAvailable = server != null &&
            server.getCommands().getDispatcher().getRoot().getChild("npc") != null;
        String status = commandsAvailable
            ? "REME NPC " + VERSION + " 已加载：/npc、/remecheck 已注册。"
                + " 生成NPC无需指令：创造模式拿 NPC 生成蛋，生存模式可在工作台合成。"
                + " /npc spawn 需要作弊或OP。"
            : "REME NPC " + VERSION + " 已加载但 /npc 未注册，请提交当前游戏实例 logs/latest.log。";
        player.sendSystemMessage(Component.literal(status));
        if (!commandsAvailable)
            LogUtils.getLogger().error("REME NPC: COMMAND REGISTRATION MISSING despite Mod loaded");
        else
            LogUtils.getLogger().info("REME NPC: /npc present on dispatcher for player {}", player.getScoreboardName());
    }
}

