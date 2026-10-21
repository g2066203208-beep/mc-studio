package dev.reme.mcstudio;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import dev.reme.mcstudio.seam.SeamCommands;
import dev.reme.mcstudio.seam.SeamServer;

/** Minimal development baseline: does not register world content. */
@Mod(StudioMod.MOD_ID)
public final class StudioMod {
    public static final String MOD_ID = "reme_studio";

    public StudioMod() {
        NeoForge.EVENT_BUS.addListener(SeamCommands::register);
        NeoForge.EVENT_BUS.addListener(SeamServer::tick);
        LogUtils.getLogger().info("REME Native Seam server core registered (disabled per world by default)");
    }
}
