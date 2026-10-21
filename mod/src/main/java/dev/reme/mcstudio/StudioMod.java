package dev.reme.mcstudio;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** Minimal development baseline: does not register world content. */
@Mod(StudioMod.MOD_ID)
public final class StudioMod {
    public static final String MOD_ID = "reme_studio";

    public StudioMod() {
        // Native observer/export; never substitutes for the source Mod's physics.
        NeoForge.EVENT_BUS.addListener(NativeSnapshotCommand::register);
        LogUtils.getLogger().info("REME MC Studio native snapshot command registered");
    }
}
