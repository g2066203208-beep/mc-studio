package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
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
    private static boolean previewLogged;
    private static boolean endPreviewLogged;
    private static boolean dualStableChecked;
    private static boolean liveChangeStarted;
    private static boolean liveChangeVerified;
    private static boolean stableBaselineTaken;
    private static volatile int witnessX, witnessZ;
    private static long stableFetches = -1;
    private static long stablePackets = -1;

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
        } else if (phase==1 && !previewLogged && now-startTick>290) {
            previewLogged=true;
            LOG.info("WORLDSEAM_E2E_PREVIEW_WINDOW: client dim={}, received={}, cached3x3={}, surface geometry={}, drawn={}, camera near floor Y={}",
                mc.level.dimension().location(),SeamClientWorldPool.receivedChunks(),
                SeamClientWorldPool.nearbyChunks(),
                SeamNativeGeometryClient.visibleBlockCount(),
                SeamNativeGeometryClient.lastFrameDrawn(),mc.player.getY());
            server.execute(() -> {
                ServerPlayer player=server.getPlayerList().getPlayer(playerId);
                if(player==null)return;
                var stats=SeamChunkSender.stats(player);
                stableFetches=stats.fetches();
                stablePackets=stats.packets();
                LOG.info("WORLDSEAM_E2E_DUAL_PREWARM: ticket={}, resident5x5={}, alreadySent={}, fetches={}, ticketsMoved={}, packets={}",
                    stats.ticketActive(),stats.resident(),stats.transmitted(),
                    stats.fetches(),stats.ticketMoves(),stats.packets());
                if(!stats.ticketActive() || stats.resident()<25
                    || stats.transmitted()<25)
                    LOG.error("WORLDSEAM_E2E_FAIL_DUAL_PREWARM: second world is not resident in all 5x5 chunks");
            });
        } else if (phase==1 && !liveChangeStarted && now-startTick>330) {
            liveChangeStarted=true;
            server.execute(() -> {
                var nether=server.getLevel(Level.NETHER);
                if(nether==null)return;
                BlockPos marker=new BlockPos(witnessX+2,126,witnessZ+2);
                nether.setBlock(marker,Blocks.DIAMOND_BLOCK.defaultBlockState(),3);
                LOG.info("WORLDSEAM_E2E_MUTATE_REMOTE_NETHER: changed block at {} while player remains in Overworld",marker);
            });
        } else if (phase==1 && !liveChangeVerified && now-startTick>415) {
            liveChangeVerified=true;
            BlockPos marker=new BlockPos(witnessX+2,126,witnessZ+2);
            boolean mirrored=SeamClientWorldPool.remoteBlockIs(
                "minecraft:the_nether",marker,Blocks.DIAMOND_BLOCK.defaultBlockState());
            LOG.info("WORLDSEAM_E2E_SECOND_WORLD_LIVE_BLOCK: mirrored={} at {}, client dimension={}",
                mirrored,marker,mc.level.dimension().location());
            if(!mirrored)
                LOG.error("WORLDSEAM_E2E_FAIL_LIVE_BLOCK: native secondary client chunk did not update when target block changed");
        } else if (phase==1 && !stableBaselineTaken && now-startTick>465) {
            stableBaselineTaken=true;
            server.execute(() -> {
                ServerPlayer p=server.getPlayerList().getPlayer(playerId);
                if(p==null)return;
                var stats=SeamChunkSender.stats(p);
                stableFetches=stats.fetches();
                stablePackets=stats.packets();
                LOG.info("WORLDSEAM_E2E_LIVE_PACKET: liveResends={}, totalPackets={}, loaded5x5={}",
                    stats.liveUpdates(),stats.packets(),stats.resident());
                if(stats.liveUpdates()<1)
                    LOG.error("WORLDSEAM_E2E_FAIL_LIVE_PACKET: no bounded native update sent from secondary world");
            });
        } else if (phase==1 && !dualStableChecked && now-startTick>550) {
            dualStableChecked=true;
            server.execute(() -> {
                ServerPlayer p=server.getPlayerList().getPlayer(playerId);
                if(p==null)return;
                var stats=SeamChunkSender.stats(p);
                LOG.info("WORLDSEAM_E2E_DUAL_STABLE: ticket={}, resident5x5={}, transmitted={}, fetches={} (was {}), packets={} (was {})",
                    stats.ticketActive(),stats.resident(),stats.transmitted(),
                    stats.fetches(),stableFetches,stats.packets(),stablePackets);
                if(!stats.ticketActive() || stats.resident()<25 ||
                    stats.fetches()!=stableFetches ||
                    stats.packets()!=stablePackets)
                    LOG.error("WORLDSEAM_E2E_FAIL_DUAL_STABLE: target dimension is unnecessarily re-fetching or re-sending chunks");
            });
        } else if (phase==1 && now-startTick>620) {
            phase=2;
            LOG.info("WORLDSEAM_E2E_CHECK: client native packets={}, 3x3={}, geometry={}, drawn={}",
                SeamClientWorldPool.receivedChunks(),SeamClientWorldPool.nearbyChunks(),
                SeamNativeGeometryClient.visibleBlockCount(),
                SeamNativeGeometryClient.lastFrameDrawn());
            server.execute(() -> attempt(server,playerId));
        } else if (phase==2 && now-startTick>730) {
            phase=3;
            String clientDimension=mc.level.dimension().location().toString();
            if (clientDimension.equals(DEST)) {
                LOG.info("WORLDSEAM_E2E_PASS_CLIENT: real client now in Nether without vanilla respawn");
                phase=4;
            } else {
                LOG.error("WORLDSEAM_E2E_FAIL_CLIENT: still in {}, expected {}, native packets={}",
                    clientDimension,DEST,SeamClientWorldPool.receivedChunks());
            }
            server.execute(() -> {
                ServerPlayer p=server.getPlayerList().getPlayer(playerId);
                if (p!=null && p.serverLevel().dimension()==Level.NETHER) {
                    LOG.info("WORLDSEAM_E2E_PASS_SERVER: real ServerPlayer moved into Nether");
                    // Show actual Overworld underside FROM NETHER, looking UP.
                    p.getAbilities().flying=true;
                    p.onUpdateAbilities();
                    p.connection.teleport(p.getX(),125,p.getZ(),0,-90);
                    LOG.info("WORLDSEAM_E2E_REVERSE_STAGE: hovering inside Nether below Y128, looking UP");
                } else LOG.error("WORLDSEAM_E2E_FAIL_SERVER: did not migrate player");
            });
        } else if (phase==4 && now-startTick>975) {
            phase=5;
            server.execute(() -> attemptReverse(server,playerId));
        } else if (phase==5 && now-startTick>1080) {
            phase=6;
            String clientDimension=mc.level.dimension().location().toString();
            if (clientDimension.equals("minecraft:overworld"))
                LOG.info("WORLDSEAM_E2E_PASS_RETURN_CLIENT: live Nether -> Overworld direct handoff");
            else LOG.error("WORLDSEAM_E2E_FAIL_RETURN_CLIENT: still in {}",clientDimension);
            server.execute(() -> {
                ServerPlayer p=server.getPlayerList().getPlayer(playerId);
                if (p!=null && p.serverLevel().dimension()==Level.OVERWORLD) {
                    LOG.info("WORLDSEAM_E2E_PASS_RETURN_SERVER: live Nether -> Overworld server migration");
                    endStage(server,playerId);
                } else LOG.error("WORLDSEAM_E2E_FAIL_RETURN_SERVER: wrong dimension");
            });
            phase=7;
        } else if (phase==7 && !endPreviewLogged && now-startTick>1280) {
            endPreviewLogged=true;
            LOG.info("WORLDSEAM_E2E_END_PREVIEW_WINDOW: current dim={}, y={}, target=the_end, native3x3={}, geometry={}, drawn={}",
                mc.level.dimension().location(),mc.player.getY(),
                SeamClientWorldPool.nearbyChunks(),SeamNativeGeometryClient.visibleBlockCount(),
                SeamNativeGeometryClient.lastFrameDrawn());
            int nativeStone=SeamNativeGeometryClient.countEndStone();
            int actuallyRendered=SeamNativeGeometryClient.lastFrameEndStone();
            LOG.info("WORLDSEAM_E2E_END_NATURAL_STONE: {} native End-stone candidates, {} actually submitted in latest pre-crossing frame",
                nativeStone,actuallyRendered);
            if(nativeStone==0 || actuallyRendered==0)
                LOG.error("WORLDSEAM_E2E_FAIL_END_NATURAL: End main island still not actually visible before crossing");
        } else if (phase==7 && now-startTick>1440) {
            phase=8;
            server.execute(() -> attemptEnd(server,playerId));
        } else if (phase==8 && now-startTick>1535) {
            phase=9;
            if(mc.level.dimension()==Level.END)
                LOG.info("WORLDSEAM_E2E_PASS_END_CLIENT: Overworld TOP -> End BOTTOM without vanilla respawn");
            else LOG.error("WORLDSEAM_E2E_FAIL_END_CLIENT: dimension {}",mc.level.dimension().location());
            server.execute(() -> {
                ServerPlayer p=server.getPlayerList().getPlayer(playerId);
                if(p!=null && p.serverLevel().dimension()==Level.END)
                    LOG.info("WORLDSEAM_E2E_PASS_END_SERVER: Overworld TOP -> End BOTTOM");
                else LOG.error("WORLDSEAM_E2E_FAIL_END_SERVER: player not in End");
            });
        }
    }

    private static void endStage(MinecraftServer server,UUID id) {
        ServerPlayer p=server.getPlayerList().getPlayer(id);
        if(p==null || p.serverLevel().dimension()!=Level.OVERWORLD)return;
        p.getAbilities().flying=true;
        p.onUpdateAbilities();
        // A truthful natural-terrain visual test: End's main island is
        // around 0,0, and its surface is much higher than the Y=0 plane.
        // The old screenshots used a tiny artificial marker near random
        // Overworld spawn, proving only proxy models, not End terrain.
        p.connection.teleport(0.5,317.0,0.5,0,-90);
        LOG.info("WORLDSEAM_E2E_END_STAGE: player X0 Z0 Y317, looking UP at actual End main island through Overworld TOP Y320");
    }

    private static void attemptEnd(MinecraftServer server,UUID id) {
        ServerPlayer p=server.getPlayerList().getPlayer(id);
        if(p==null)return;
        boolean ready=SeamReadiness.isReady(id,"minecraft:overworld",
            "minecraft:the_end",server.overworld().getGameTime());
        LOG.info("WORLDSEAM_E2E_END_READY={}, player dim={}, y={}",
            ready,p.serverLevel().dimension().location(),p.getY());
        if(!ready || p.serverLevel().dimension()!=Level.OVERWORLD) {
            LOG.error("WORLDSEAM_E2E_FAIL_END: End nine native chunks not ready");
            return;
        }
        p.connection.teleport(p.getX(),319.75,p.getZ(),0,-90);
        p.getAbilities().flying=false;
        p.onUpdateAbilities();
        p.setDeltaMovement(new Vec3(0,.22,0));
        LOG.info("WORLDSEAM_E2E_END_TRIGGER: crossing Overworld TOP 320 -> End BOTTOM 0");
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
        witnessX=x;
        witnessZ=z;
        var overworld=server.getLevel(Level.OVERWORLD);
        var nether=server.getLevel(Level.NETHER);
        var end=server.getLevel(Level.END);
        if (overworld==null || nether==null || end==null ||
            overworld.getMinBuildHeight()!=-64 || overworld.getMaxBuildHeight()!=320 ||
            nether.getMinBuildHeight()!=0 || nether.getMaxBuildHeight()!=256 ||
            end.getMinBuildHeight()!=0 || end.getMaxBuildHeight()!=256 ||
            SeamBounds.top(nether)!=128) {
            LOG.error("WORLDSEAM_E2E_FAIL_BOUNDS: actual dimension heights disagree with expected vanilla 1.21.1");
            return;
        }
        LOG.info("WORLDSEAM_E2E_BOUNDS: overworld=[{},{}), nether=[{},{}), end=[{},{}), joined overworld floor Y=-64 to nether roof Y=128",
            overworld.getMinBuildHeight(),overworld.getMaxBuildHeight(),
            nether.getMinBuildHeight(),nether.getMaxBuildHeight(),
            end.getMinBuildHeight(),end.getMaxBuildHeight());
        int cleared=SeamTerrain.openTestColumn(server,x,z);
        // A BEDROCK-ONLY opening is not an OBSERVATION WELL: the original
        // Y=-61 player position may be inside solid deepslate.
        // CI-only: excavate an 11x11 window through the local floor so
        // the camera has an unobstructed line of sight across Y=-64.
        // Excavation is NEVER performed in regular installed worlds.
        int excavated=0;
        BlockPos.MutableBlockPos at=new BlockPos.MutableBlockPos();
        for (int dx=-5;dx<=5;dx++) for (int dz=-5;dz<=5;dz++)
            for (int y=-64;y<=-54;y++) {
                at.set(x+dx,y,z+dz);
                if (!overworld.getBlockState(at).isAir()) {
                    if (overworld.setBlock(at,Blocks.AIR.defaultBlockState(),3))
                        excavated++;
                }
            }
        // Also open the target Nether roof; otherwise the photographed
        // target is mostly roof netherrack rather than the lower world.
        int netherOpened=0;
        for (int dx=-5;dx<=5;dx++) for (int dz=-5;dz<=5;dz++)
            for (int y=120;y<=127;y++) {
                at.set(x+dx,y,z+dz);
                if (!nether.getBlockState(at).isAir()) {
                    if (nether.setBlock(at,Blocks.AIR.defaultBlockState(),3))
                        netherOpened++;
                }
            }
        // Bright, distinctive target-only blocks at Nether Y=126/127.
        // WorldSeam maps them below the OVERWORLD boundary, at -66/-65.
        // They must never appear in the Overworld block palette itself.
        int markers=0;
        for(int dz=-3;dz<=3;dz++) for(int dx=-3;dx<=3;dx++) {
            if (Math.abs(dx)<2 && Math.abs(dz)<2) continue; // landing safe
            at.set(x+dx,126,z+dz);
            if(nether.setBlock(at,Blocks.MAGENTA_CONCRETE.defaultBlockState(),3))
                markers++;
            at.set(x+dx,127,z+dz);
            if(nether.setBlock(at,Blocks.GLOWSTONE.defaultBlockState(),3))
                markers++;
        }
        // The End visual benchmark must use NATURALLY GENERATED island
        // terrain, not a synthetic purpur/sea-lantern frame.
        LOG.info("WORLDSEAM_E2E_END_WITNESS: NO synthetic End blocks created");
        if (!overworld.getBlockState(new BlockPos(x,-61,z)).isAir() ||
            !overworld.getBlockState(new BlockPos(x,-64,z)).isAir() ||
            !nether.getBlockState(new BlockPos(x,127,z)).isAir() ||
            nether.getBlockState(new BlockPos(x+3,127,z+3)).getBlock()!=Blocks.GLOWSTONE) {
            LOG.error("WORLDSEAM_E2E_FAIL_GEOMETRY: test shaft or remote-only marker not built");
            return;
        }
        LOG.info("WORLDSEAM_E2E_GEOMETRY: opened local deepslate shaft={} blocks, Nether roof={} blocks, target-only magenta/glowstone markers={}, player view Overworld -61 toward mapped Nether Y127",
            excavated,netherOpened,markers);
        player.setGameMode(GameType.CREATIVE);
        // Real in-game NIGHT_VISION effect for a visible test near dark
        // Nether/Overworld roofs. Only in the opt-in CI-only E2E harness.
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
            20 * 60 * 6, 0, false, false));
        player.getAbilities().flying=true;
        player.onUpdateAbilities();
        player.connection.teleport(player.getX(),-61.0,player.getZ(),0,90);
        LOG.info("WORLDSEAM_E2E_SETUP: opened {} bedrock blocks, at x={} z={}, Overworld Y=-61",cleared,x,z);
    }

    private static void attemptReverse(MinecraftServer server,UUID id) {
        ServerPlayer player=server.getPlayerList().getPlayer(id);
        if(player==null)return;
        long tick=server.overworld().getGameTime();
        boolean ready=SeamReadiness.isReady(id,
            "minecraft:the_nether","minecraft:overworld",tick);
        LOG.info("WORLDSEAM_E2E_REVERSE_READY={}, server dim={}, y={}",
            ready,player.serverLevel().dimension().location(),player.getY());
        if(!ready || player.serverLevel().dimension()!=Level.NETHER) {
            LOG.error("WORLDSEAM_E2E_FAIL_RETURN: reverse native world not ready");
            return;
        }
        player.connection.teleport(player.getX(),127.75,player.getZ(),0,-90);
        player.getAbilities().flying=false;
        player.onUpdateAbilities();
        player.setDeltaMovement(new Vec3(0,.20,0));
        LOG.info("WORLDSEAM_E2E_REVERSE_TRIGGER: upward motion through physical Y128 Nether roof");
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
