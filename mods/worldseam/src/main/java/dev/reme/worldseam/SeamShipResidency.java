package dev.reme.worldseam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded, asynchronous native DESTINATION terrain residency around an
 * ACTUAL Sable sublevel approaching a logical dimension-stack seam.
 *
 * Ship-driven region residency does not depend on a nearby player. Every
 * destination chart has its own ticket, so two simultaneous seam approaches
 * cannot repeatedly evict each other's native chunks. The two ServerLevels
 * remain authoritative; there are NO duplicated ships, teleports, or
 * synchronous getChunk calls.
 *
 * IMPORTANT: a ticket is NOT a shared Rapier physics solver or proof that
 * the entire hull's collider footprint has been loaded.
 */
public final class SeamShipResidency {
    private static final int TICKET_DISTANCE = 3;
    private static final long GRACE_TICKS = 20;
    private static final double APPROACH_MARGIN = 16.0;
    private static final TicketType<UUID> SHIP_TICKET = TicketType.create(
        "worldseam_sable_adjacent_terrain", Comparator.<UUID>naturalOrder());

    private static MinecraftServer lastServer;
    private static final Map<SeamShipTicketPolicy.Key, Resident> sessions =
        new HashMap<>();
    private static long added, removed, rejected;

    private SeamShipResidency() {}

    private static final class Resident {
        final ServerLevel level;
        final ChunkPos center;
        long lastSeen;

        Resident(ServerLevel level, ChunkPos center, long tick) {
            this.level = level;
            this.center = center;
            this.lastSeen = tick;
        }
    }

    public static void beginTick(MinecraftServer server) {
        if (lastServer != server) {
            // Never dereference an already disposed ServerChunkCache here.
            // The old server has stopped and its tickets no longer exist.
            sessions.clear();
            lastServer = server;
            added = removed = rejected = 0;
        }
    }

    public static boolean nearing(AABB hull, double seamY) {
        if (hull == null || !Double.isFinite(seamY) ||
            !Double.isFinite(hull.minY) || !Double.isFinite(hull.maxY) ||
            hull.minY >= hull.maxY) return false;
        return hull.minY < seamY + APPROACH_MARGIN &&
               hull.maxY > seamY - APPROACH_MARGIN;
    }

    public static void touch(UUID ship, ServerLevel other, AABB hull,
                             double homePlane, long tick) {
        if (ship == null || other == null || !nearing(hull, homePlane)) return;

        // Avoid saturating double->int casts for corrupted / giant modded
        // bounds. Coordinates must be finite and in the BlockPos domain.
        if (!Double.isFinite(hull.minX) || !Double.isFinite(hull.maxX) ||
            !Double.isFinite(hull.minZ) || !Double.isFinite(hull.maxZ) ||
            hull.minX >= hull.maxX || hull.minZ >= hull.maxZ) {
            rejected++;
            return;
        }
        double x = Math.floor(hull.minX + (hull.maxX - hull.minX) * 0.5);
        double z = Math.floor(hull.minZ + (hull.maxZ - hull.minZ) * 0.5);
        if (!Double.isFinite(x) || !Double.isFinite(z) ||
            x < Integer.MIN_VALUE || x > Integer.MAX_VALUE ||
            z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
            rejected++;
            return;
        }
        int centerX = (int) x;
        int centerZ = (int) z;
        if (!other.getWorldBorder().isWithinBounds(
            new BlockPos(centerX, other.getMinBuildHeight(), centerZ))) {
            rejected++;
            return;
        }

        var key = new SeamShipTicketPolicy.Key(
            ship, other.dimension().location().toString());
        var center = new ChunkPos(Math.floorDiv(centerX, 16),
                                  Math.floorDiv(centerZ, 16));
        Resident old = sessions.get(key);
        if (old != null && old.level == other && old.center.equals(center)) {
            old.lastSeen = tick;
            return;
        }

        if (old == null && !SeamShipTicketPolicy.mayOpen(sessions.keySet(), key)) {
            rejected++;
            return;
        }

        // Add before removing the old center: travelling across a chunk
        // boundary should not briefly drop its destination world residency.
        other.getChunkSource().addRegionTicket(
            SHIP_TICKET, center, TICKET_DISTANCE, ship);
        sessions.put(key, new Resident(other, center, tick));
        added++;
        if (old != null) release(key, old);
    }

    private static void release(SeamShipTicketPolicy.Key key, Resident resident) {
        resident.level.getChunkSource().removeRegionTicket(
            SHIP_TICKET, resident.center, TICKET_DISTANCE, key.ship());
        removed++;
    }

    /** Called at the end of every real Sable observer pass. */
    public static void prune(long tick) {
        sessions.entrySet().removeIf(entry -> {
            if (tick - entry.getValue().lastSeen < GRACE_TICKS) return false;
            release(entry.getKey(), entry.getValue());
            return true;
        });
    }

    /** Reclaim ALL destination-chart tickets when WorldSeam switches off. */
    public static void clear() {
        for (var entry : sessions.entrySet())
            release(entry.getKey(), entry.getValue());
        sessions.clear();
    }

    /** Active destination tickets, not the number of unique ships. */
    public static int active() { return sessions.size(); }
    public static long added() { return added; }
    public static long removed() { return removed; }
    public static long rejected() { return rejected; }
}
