package dev.reme.mcstudio.seam;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
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
    private static MinecraftServer lastServer;

    private SeamServer() {}

    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server != lastServer) {
            lastServer = server;
            lastCross.clear();
        }
        if (!SeamSettings.get(server).enabled()) {
            return;
        }
        long tick = server.overworld().getGameTime();
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
            for (SeamGraph.Link link : SeamGraph.from(dim)) {
                int min = source.getMinBuildHeight();
                int max = source.getMaxBuildHeight();
                if (!link.crossed(player.getY(), min, max)) {
                    continue;
                }
                ServerLevel target = server.getLevel(dimensionKey(link.target()));
                if (target == null) {
                    LOG.warn("Skipping seam: missing target dimension {}", link.target());
                    break;
                }
                // Vanilla dimension scale (e.g., Nether 8:1) deliberately
                // DOES NOT apply to this 1:1 spatial fold.
                double sourceBoundary = SeamGraph.boundaryY(link.exit(), min, max);
                double targetBoundary = SeamGraph.boundaryY(link.entry(),
                    target.getMinBuildHeight(), target.getMaxBuildHeight());
                double nextY = link.targetY(player.getY(), sourceBoundary, targetBoundary);
                Vec3 previousVelocity = player.getDeltaMovement();
                float previousFall = player.fallDistance;
                BlockPos arrival = BlockPos.containing(player.getX(), nextY, player.getZ());
                if (!target.getWorldBorder().isWithinBounds(arrival)) {
                    break;
                }
                // Force destination chunk before teleport. Does not edit blocks,
                // portals, chunk generator, or the player's inventory.
                target.getChunkAt(arrival);
                boolean success = player.teleportTo(target, player.getX(), nextY, player.getZ(),
                    Set.of(), player.getYRot(), player.getXRot());
                if (success) {
                    player.setDeltaMovement(previousVelocity);
                    player.fallDistance = previousFall;
                    lastCross.put(player.getUUID(), tick);
                } else {
                    LOG.warn("Seam teleport rejected: {} -> {}", dim, link.target());
                }
                break;
            }
        }
        // Remove entries for offline players. Avoid stale UUID entries.
        if (tick % 1200 == 0) {
            Set<UUID> online = new HashSet<>();
            server.getPlayerList().getPlayers().forEach(p -> online.add(p.getUUID()));
            lastCross.keySet().retainAll(online);
        }
    }

    public static ResourceKey<Level> dimensionKey(String id) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
    }
}
