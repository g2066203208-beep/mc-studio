package dev.reme.mcstudio;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;

/** Minimal development baseline: does not register world content. */
@Mod(StudioMod.MOD_ID)
public final class StudioMod {
    public static final String MOD_ID = "reme_studio";

    public StudioMod() {
        LogUtils.getLogger().info("REME MC Studio baseline loaded");
    }
}
