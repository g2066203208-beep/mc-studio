package dev.reme.npcstudio;

import com.mojang.logging.LogUtils;
import dev.reme.npcstudio.npc.NpcCommands;
import dev.reme.npcstudio.npc.NpcRegistry;
import dev.reme.npcstudio.npc.NpcItems;
import dev.reme.npcstudio.npc.life.NaturalResidentSpawn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;

@Mod(NpcMod.MOD_ID)
public final class NpcMod {
    public static final String MOD_ID = "reme_npc";
    public static final String VERSION = "0.6.7-dev";

    public NpcMod(IEventBus modBus) {
        NpcRegistry.register(modBus);
        NpcItems.register(modBus);
        modBus.addListener(NpcRegistry::onEntityAttributes);
        modBus.addListener(NaturalResidentSpawn::register);
        NeoForge.EVENT_BUS.addListener(NpcCommands::register);
        NeoForge.EVENT_BUS.addListener(NpcMod::onPlayerLogin);
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

