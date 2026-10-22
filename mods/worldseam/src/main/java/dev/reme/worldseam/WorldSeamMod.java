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
        NeoForge.EVENT_BUS.addListener(SeamPendingWorldPreset::apply);
        NeoForge.EVENT_BUS.addListener(SeamServer::entityTick);
        NeoForge.EVENT_BUS.addListener(SeamExplosionBridge::onDetonate);
        NeoForge.EVENT_BUS.addListener(SeamTerrain::onChunkLoad);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e)->
                e.registerReloadListener(
                    (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                        manager->SeamNativeGeometryClient.invalidateGpuOnReload()
                ));
            SeamPreviewClient.install();
            SeamSectionClient.install();
            SeamClientWorldPool.install();
            SeamNativeGeometryClient.install();
            SeamClientDiagnostic.install();
            if (Boolean.getBoolean("worldseam.stress")) {
                // A dedicated long-run world: do not spend the first 1500+
                // ticks running the unrelated historical-stack visual E2E.
                SeamLongRunE2E.install();
            } else {
                SeamVisualE2E.install();
            }
            SeamStackClient.install();
            SeamWorldCreation.install();
        }
        LogUtils.getLogger().info("WorldSeam standalone mod loaded; no climbing integration");
    }
}
