package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Original boundary crossing engine. Server authority only.
 *
 * Preserves player velocity, orientation and x/z. Does not copy the target
 * dimension or use Immersive Portals. No ordinary portal block is placed.
 */
public final class SeamServer {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int COOLDOWN_TICKS = 12;
    private static final Map<UUID, Long> lastCross = new HashMap<>();
    private static final SeamReturnGuard RETURN_GUARD = new SeamReturnGuard();
    private static MinecraftServer lastServer;
    private static volatile boolean seamEnabled;
    private static volatile boolean terrainEnabled;

    private SeamServer() {}

    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server != lastServer) {
            lastServer = server;
            lastCross.clear();
            RETURN_GUARD.clear();
            SeamReadiness.clear();
        }
        boolean enabled = SeamSettings.get(server).enabled();
        long tick = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SeamStackSync.tick(server, player);
            String dim=player.serverLevel().dimension().location().toString();
            boolean ready=false;
            if(enabled) {
                for(SeamGraph.Link link:SeamStackRuntime.from(server,dim)) {
                    if(link.near(player.getY(),SeamStackRuntime.bottom(player.serverLevel()),
                            SeamStackRuntime.top(player.serverLevel()),48) &&
                       SeamReadiness.isReady(player.getUUID(),dim,link.target(),tick)) {
                        ready=true;
                        break;
                    }
                }
            }
            // Native 3x3 ready: DO NOT keep encoding duplicate legacy section
            // and sparse block previews at 4Hz. These consumed server CPU
            // and network even after the second live ClientLevel was built.
            SeamPreviewSender.tick(server, player, tick, enabled && !ready);
            SeamSectionSender.tick(server, player, tick, enabled && !ready);
            SeamChunkSender.tick(server, player, tick, enabled);
        }
        seamEnabled = enabled;
        terrainEnabled = enabled && SeamSettings.get(server).carveNewChunks();
        // Release any ticket held by disconnected players within five seconds,
        // even if world-seam was subsequently disabled.
        if(tick % 100==0) {
            var online=server.getPlayerList().getPlayers().stream()
                .map(Entity::getUUID).collect(java.util.stream.Collectors.toSet());
            SeamChunkSender.prune(online);
            SeamStackSync.prune(online);
        }
        if (!enabled) return;
        // Only one teleport per player per tick. All operations on server thread.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isPassenger() || player.isVehicle()
                || player.isSleeping() || player.isSpectator()) {
                continue;
            }
            if (tick - lastCross.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < COOLDOWN_TICKS) {
                continue;
            }
            ServerLevel source = player.serverLevel();
            String dim = source.dimension().location().toString();
            for (SeamGraph.Link link : SeamStackRuntime.from(server,dim)) {
                int min = SeamStackRuntime.bottom(source);
                int max = SeamStackRuntime.top(source);
                // No permanent "loading chunks" banner. Once the native
                // second world is ready the gameplay view should stay clear.
                if(SeamSettings.get(server).experimentalNoRespawn()
                    && tick%100==0 && link.near(player.getY(),min,max,64)
                    && !SeamReadiness.isReady(player.getUUID(),
                        dim,link.target(),tick)) {
                    int chunks=SeamChunkSender.currentTargetLoaded(player);
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                            "界缝 · 目标维度预载 "+chunks+"/25 个区块"
                        ),true);
                }
                ServerLevel target = server.getLevel(dimensionKey(link.target()));
                if (target == null) {
                    continue;
                }
                double targetBoundaryY = SeamGraph.boundaryY(link.entry(),
                    SeamStackRuntime.bottom(target), SeamStackRuntime.top(target));
                // A freshly arrived player must enter the new world before
                // gravity or position correction can send them back out.
                if (RETURN_GUARD.blocked(player.getUUID(), dim, link.exit(),
                    player.getY(), min, max)) {
                    double holdY=SeamReturnGuard.safeY(link.exit(),min,max);
                    if (link.exit()==SeamGraph.EdgeSide.BOTTOM
                        && player.getY()<holdY && player.getDeltaMovement().y()<0) {
                        player.connection.teleport(player.getX(),holdY,player.getZ(),
                            player.getYRot(),player.getXRot());
                        player.setDeltaMovement(new Vec3(player.getDeltaMovement().x(),
                            0,player.getDeltaMovement().z()));
                        player.fallDistance=0;
                    } else if (link.exit()==SeamGraph.EdgeSide.TOP
                        && player.getY()>holdY && player.getDeltaMovement().y()>0) {
                        player.connection.teleport(player.getX(),holdY,player.getZ(),
                            player.getYRot(),player.getXRot());
                        player.setDeltaMovement(new Vec3(player.getDeltaMovement().x(),
                            0,player.getDeltaMovement().z()));
                        player.fallDistance=0;
                    }
                    continue;
                }
                SeamCrossing.Decision crossing = SeamCrossing.evaluate(
                    link, player.getY(), player.yo,
                    player.getDeltaMovement().y(), min, max, targetBoundaryY);
                if (!crossing.crossing()) continue;
                if (target == null) {
                    LOG.warn("Skipping seam: missing target dimension {}", link.target());
                    break;
                }
                // Vanilla dimension scale (e.g., Nether 8:1) deliberately
                // DOES NOT apply to this 1:1 spatial fold.
                double sourceBoundary = SeamGraph.boundaryY(link.exit(), min, max);
                double targetBoundary = SeamGraph.boundaryY(link.entry(),
                    SeamStackRuntime.bottom(target), SeamStackRuntime.top(target));
                double nextY = crossing.arrivalY();
                Vec3 previousVelocity = player.getDeltaMovement();
                float previousFall = player.fallDistance;
                BlockPos arrival = BlockPos.containing(player.getX(), nextY, player.getZ());
                if (!target.getWorldBorder().isWithinBounds(arrival)) {
                    break;
                }
                boolean useExperimental = SeamSettings.get(server).experimentalNoRespawn();
                boolean ready = SeamReadiness.isReady(player.getUUID(),
                    dim, link.target(), tick);
                // Do not synchronously load the destination on EACH tick
                // before the client has even acknowledged its dual cache.
                // This was a second repeated-loading hotspot.
                if (useExperimental && !ready) {
                    // Never fall back to a loading screen when user explicitly
                    // requested the no-respawn experiment.
                    double holdY = link.exit() == SeamGraph.EdgeSide.TOP
                        ? sourceBoundary - 0.75 : sourceBoundary + 0.75;
                    player.connection.teleport(player.getX(), holdY,
                        player.getZ(),player.getYRot(),player.getXRot());
                    if (tick % 40 == 0) player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                            "WorldSeam: preparing remote native chunks (9 needed)"
                        ), true);
                    break;
                }
                // Now and only now, with the destination ready, load the
                // authoritative landing chunk and validate its collision.
                target.getChunkAt(arrival);
                if (!SeamArrival.hasClearance(target, arrival, player)) break;
                boolean success;
                if (useExperimental) {
                    SeamDirectTransfer.move(player, target,
                        new Vec3(player.getX(),nextY,player.getZ()),
                        player.getYRot(),player.getXRot());
                    success = true;
                } else {
                    success = player.teleportTo(target, player.getX(), nextY,
                        player.getZ(), Set.of(), player.getYRot(), player.getXRot());
                }
                if (success) {
                    player.setDeltaMovement(previousVelocity);
                    player.fallDistance = previousFall;
                    lastCross.put(player.getUUID(), tick);
                    RETURN_GUARD.landed(player.getUUID(), link.target(), link.entry());
                } else {
                    LOG.warn("Seam teleport rejected: {} -> {}", dim, link.target());
                }
                break;
            }
        }

        if (tick % 1200 == 0) {
            var online = server.getPlayerList().getPlayers().stream()
                .map(Entity::getUUID).collect(java.util.stream.Collectors.toSet());
            SeamPreviewSender.prune(online);
            SeamSectionSender.prune(online);
            SeamChunkSender.prune(online);
            // Per-entity cooldowns only need to survive a short period;
            // no global getAllEntities scan is needed for eviction.
            lastCross.entrySet().removeIf(e -> tick - e.getValue() > 2400);
        }
    }


    /**
     * Event-driven transfer, not an O(all-world-entities) global scan.
     * The event runs on the logical server after an entity's own update.
     * Mounted entity trees remain excluded until an atomic migration exists.
     */
    public static void entityTick(EntityTickEvent.Post event) {
        if (!seamEnabled || !(event.getEntity().level() instanceof ServerLevel source)) return;
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer || !entity.isAlive()
            || entity.isPassenger() || entity.isVehicle()
            || entity.getType() == EntityType.ENDER_DRAGON) return;
        MinecraftServer server = source.getServer();
        if (server == null || server != lastServer) return;

        long tick = server.overworld().getGameTime();
        if (tick - lastCross.getOrDefault(entity.getUUID(), Long.MIN_VALUE / 2)
            < COOLDOWN_TICKS) return;
        for (SeamGraph.Link link : SeamStackRuntime.from(server,source.dimension().location().toString())) {
            int sourceMinY = SeamStackRuntime.bottom(source);
            int sourceTopY = SeamStackRuntime.top(source);
            // Target-aware plan is evaluated below once target level exists.
            ServerLevel target = server.getLevel(dimensionKey(link.target()));
            if (target == null || !entity.canChangeDimensions(source, target)) return;
            double sourceBoundary = SeamGraph.boundaryY(link.exit(),
                SeamStackRuntime.bottom(source), SeamStackRuntime.top(source));
            double targetBoundary = SeamGraph.boundaryY(link.entry(),
                SeamStackRuntime.bottom(target), SeamStackRuntime.top(target));
            SeamCrossing.Decision crossing = SeamCrossing.evaluate(
                link, entity.getY(), entity.yo, entity.getDeltaMovement().y(),
                sourceMinY, sourceTopY, targetBoundary);
            if (!crossing.crossing()) continue;
            double y = crossing.arrivalY();
            BlockPos dest = BlockPos.containing(entity.getX(), y, entity.getZ());
            if (!target.getWorldBorder().isWithinBounds(dest)) return;
            target.getChunkAt(dest);
            if (!SeamArrival.hasClearance(target, dest, entity)) return;
            Vec3 motion = entity.getDeltaMovement();
            float fall = entity.fallDistance;
            Entity moved = entity.changeDimension(new DimensionTransition(
                target, new Vec3(entity.getX(), y, entity.getZ()),
                motion, entity.getYRot(), entity.getXRot(),
                false, DimensionTransition.DO_NOTHING
            ));
            if (moved != null) {
                moved.setDeltaMovement(motion);
                moved.fallDistance = fall;
                lastCross.put(moved.getUUID(), tick);
            }
            return;
        }
    }

    public static boolean shouldCarveNewChunk(MinecraftServer server) {
        return server!=null && SeamSettings.get(server).enabled()
            && SeamSettings.get(server).carveNewChunks();
    }

    public static ResourceKey<Level> dimensionKey(String id) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
    }
}
