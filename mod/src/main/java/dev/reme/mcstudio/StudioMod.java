package dev.reme.mcstudio;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import dev.reme.mcstudio.worldstack.WorldStackCommands;

/** Minimal development baseline: does not register world content. */
@Mod(StudioMod.MOD_ID)
public final class StudioMod {
    public static final String MOD_ID = "reme_studio";

    public StudioMod() {
        NeoForge.EVENT_BUS.addListener(WorldStackCommands::register);
        LogUtils.getLogger().info("REME MC Studio loaded (WorldStack command registered)");
    }
}
