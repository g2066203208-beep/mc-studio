package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * Integration harness for a REAL SINGLEPLAYER CLIENT; never active in a
 * normal installed JAR. Enable explicitly with -Dworldseam.e2e=true.
 *
 * Unlike a GameTestServer-only test this reaches integrated server packets,
 * the remote ClientChunkCache, actual GUI, and no-respawn client handoff.
 * Destructively edits bedrock ONLY in the disposable CI test world.
 */
public final class SeamVisualE2E {
    private static final Logger LOG=LogUtils.getLogger();
    private static final String DEST="minecraft:the_nether";
    private static int phase;
    private static long startTick;
    private static UUID playerId;

    private SeamVisualE2E() {}

    public static void install() {
        if (!Boolean.getBoolean("worldseam.e2e")) return;
        LOG.warn("WORLDSEAM_E2E_ACTIVE: disposable singleplayer world ONLY");
        NeoForge.EVENT_BUS.addListener(SeamVisualE2E::tick);
    }

    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc=Minecraft.getInstance();
        MinecraftServer server=mc.getSingleplayerServer();
        if (mc.level==null || mc.player==null || server==null) return;
        long now=mc.player.tickCount;
        if (phase==0) {
            if (now<60) return;
            playerId=mc.player.getUUID();
            startTick=now;
            phase=1;
            LOG.info("WORLDSEAM_E2E_START: actually joined world {}",mc.level.dimension().location());
            server.execute(() -> prepare(server,playerId));
        } else if (phase==1 && now-startTick>390) {
            phase=2;
            LOG.info("WORLDSEAM_E2E_CHECK: client native packets={}, 3x3={}, geometry={}, drawn={}",
                SeamClientWorldPool.receivedChunks(),SeamClientWorldPool.nearbyChunks(),
                SeamNativeGeometryClient.visibleBlockCount(),
                SeamNativeGeometryClient.lastFrameDrawn());
            server.execute(() -> attempt(server,playerId));
        } else if (phase==2 && now-startTick>500) {
            phase=3;
            String clientDimension=mc.level.dimension().location().toString();
            if (clientDimension.equals(DEST)) {
                LOG.info("WORLDSEAM_E2E_PASS_CLIENT: real client now in Nether without vanilla respawn");
            } else {
                LOG.error("WORLDSEAM_E2E_FAIL_CLIENT: still in {}, expected {}, native packets={}",
                    clientDimension,DEST,SeamClientWorldPool.receivedChunks());
            }
            server.execute(() -> {
                ServerPlayer p=server.getPlayerList().getPlayer(playerId);
                if (p!=null && p.serverLevel().dimension()==Level.NETHER)
                    LOG.info("WORLDSEAM_E2E_PASS_SERVER: real ServerPlayer moved into Nether");
                else LOG.error("WORLDSEAM_E2E_FAIL_SERVER: did not migrate player");
            });
        }
    }

    private static void prepare(MinecraftServer server,UUID id) {
        ServerPlayer player=server.getPlayerList().getPlayer(id);
        if (player==null) {LOG.error("WORLDSEAM_E2E_FAIL: player missing");return;}
        if (server.getPlayerList().getPlayerCount()!=1 ||
            player.serverLevel().dimension()!=Level.OVERWORLD) {
            LOG.error("WORLDSEAM_E2E_FAIL: must be sole player in overworld");
            return;
        }
        var cfg=SeamSettings.get(server);
        cfg.setEnabled(true);
        cfg.setExperimentalNoRespawn(true);
        cfg.setCarveNewChunks(true);
        int x=player.blockPosition().getX();
        int z=player.blockPosition().getZ();
        int cleared=SeamTerrain.openTestColumn(server,x,z);
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying=true;
        player.onUpdateAbilities();
        player.connection.teleport(player.getX(),-61.0,player.getZ(),0,90);
        LOG.info("WORLDSEAM_E2E_SETUP: opened {} bedrock blocks, at x={} z={}, Overworld Y=-61",cleared,x,z);
    }

    private static void attempt(MinecraftServer server,UUID id) {
        ServerPlayer player=server.getPlayerList().getPlayer(id);
        if(player==null)return;
        long tick=server.overworld().getGameTime();
        boolean ready=SeamReadiness.isReady(id,
            "minecraft:overworld",DEST,tick);
        LOG.info("WORLDSEAM_E2E_REMOTE_READY={}, server dimension={}, y={}",
            ready,player.serverLevel().dimension().location(),player.getY());
        if(!ready) {
            LOG.error("WORLDSEAM_E2E_FAIL: client did not preload 3x3 native chunks");
            return;
        }
        if(player.serverLevel().dimension()!=Level.OVERWORLD) {
            LOG.error("WORLDSEAM_E2E_FAIL: changed dimension prematurely");
            return;
        }
        player.connection.teleport(player.getX(),-63.75,player.getZ(),0,90);
        player.getAbilities().flying=false;
        player.onUpdateAbilities();
        player.setDeltaMovement(new Vec3(0,-.15,0));
        LOG.info("WORLDSEAM_E2E_TRIGGER: actual integrated ServerPlayer near floor with downwards velocity");
    }
}
