package dev.reme.worldseam;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(WorldSeamMod.MOD_ID)
public final class WorldSeamMod {
    public static final String MOD_ID = "worldseam";
    public WorldSeamMod(IEventBus modBus) {
        modBus.addListener(SeamNetwork::register);
        NeoForge.EVENT_BUS.addListener(SeamCommands::register);
        NeoForge.EVENT_BUS.addListener(SeamServer::tick);
        NeoForge.EVENT_BUS.addListener(SeamServer::entityTick);
        NeoForge.EVENT_BUS.addListener(SeamTerrain::onChunkLoad);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            SeamPreviewClient.install();
            SeamSectionClient.install();
            SeamClientWorldPool.install();
            SeamNativeGeometryClient.install();
            SeamClientDiagnostic.install();
            SeamVisualE2E.install();
        }
        LogUtils.getLogger().info("WorldSeam standalone mod loaded; no climbing integration");
    }
}
