package dev.reme.mcstudio;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import dev.reme.mcstudio.seam.SeamNetwork;
import dev.reme.mcstudio.seam.SeamPreviewClient;
import dev.reme.mcstudio.seam.SeamSectionClient;
import net.neoforged.neoforge.common.NeoForge;
import dev.reme.mcstudio.seam.SeamCommands;
import dev.reme.mcstudio.seam.SeamServer;

/** Minimal development baseline: does not register world content. */
@Mod(StudioMod.MOD_ID)
public final class StudioMod {
    public static final String MOD_ID = "reme_studio";

    public StudioMod(IEventBus modBus) {
        modBus.addListener(SeamNetwork::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SeamPreviewClient.install();
            SeamSectionClient.install();
        }
        NeoForge.EVENT_BUS.addListener(SeamCommands::register);
        NeoForge.EVENT_BUS.addListener(SeamServer::tick);
        NeoForge.EVENT_BUS.addListener(SeamServer::entityTick);
        LogUtils.getLogger().info("REME Native Seam server core registered (disabled per world by default)");
    }
}
