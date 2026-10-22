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
 * ACTUAL Sable sublevel approaching the logical dimensional plane.
 *
 * Ship-driven region residency does not depend on a nearby player. It is
 * essential groundwork for image-collider contact generation when the ship
 * approaches a second dimension. The two ServerLevels remain authoritative;
 * there are NO duplicated ships, teleports or synchronous getChunk calls.
 *
 * IMPORTANT: chunk residency != a shared native Rapier physics solver.
 */
public final class SeamShipResidency {
    private static final int TICKET_DISTANCE=3;
    private static final int MAX_SHIPS=32;
    private static final long GRACE_TICKS=20;
    private static final double APPROACH_MARGIN=16.0;
    private static final TicketType<UUID> SHIP_TICKET=TicketType.create(
        "worldseam_sable_adjacent_terrain",Comparator.<UUID>naturalOrder());

    private static MinecraftServer lastServer;
    private static final Map<UUID,Resident> sessions=new HashMap<>();
    private static long added,removed,rejected;
    private SeamShipResidency() {}

    private static final class Resident {
        ServerLevel level;
        ChunkPos center;
        long lastSeen;
        Resident(ServerLevel level,ChunkPos center,long tick) {
            this.level=level;
            this.center=center;
            this.lastSeen=tick;
        }
    }

    public static void beginTick(MinecraftServer server) {
        if(lastServer!=server) {
            // The previous server has already stopped; never dereference
            // old ServerChunkCache native/world lifecycle after disposal.
            sessions.clear();
            lastServer=server;
            added=removed=rejected=0;
        }
    }

    public static boolean nearing(AABB hull,double seamY) {
        if(hull==null||!Double.isFinite(seamY))return false;
        return hull.minY<seamY+APPROACH_MARGIN &&
            hull.maxY>seamY-APPROACH_MARGIN;
    }

    public static void touch(UUID ship,ServerLevel other,AABB hull,
                             double homePlane,long tick) {
        if(ship==null||other==null||!nearing(hull,homePlane))return;
        int centerX=(int)Math.floor(hull.minX+(hull.maxX-hull.minX)*0.5);
        int centerZ=(int)Math.floor(hull.minZ+(hull.maxZ-hull.minZ)*0.5);
        if(!other.getWorldBorder().isWithinBounds(
            new BlockPos(centerX,other.getMinBuildHeight(),centerZ)))return;
        ChunkPos newCenter=new ChunkPos(Math.floorDiv(centerX,16),
                                       Math.floorDiv(centerZ,16));
        Resident old=sessions.get(ship);
        if(old!=null&&old.level==other&&old.center.equals(newCenter)) {
            old.lastSeen=tick;
            return;
        }
        if(old==null&&sessions.size()>=MAX_SHIPS) {
            rejected++;
            return; // never pin unbounded far-away regions
        }
        if(old!=null)release(ship,old);
        other.getChunkSource().addRegionTicket(
            SHIP_TICKET,newCenter,TICKET_DISTANCE,ship);
        sessions.put(ship,new Resident(other,newCenter,tick));
        added++;
    }

    private static void release(UUID ship,Resident resident) {
        resident.level.getChunkSource().removeRegionTicket(
            SHIP_TICKET,resident.center,TICKET_DISTANCE,ship);
        removed++;
    }

    /** Called at the end of every real Sable observer pass. */
    public static void prune(long tick) {
        sessions.entrySet().removeIf(entry -> {
            if(tick-entry.getValue().lastSeen<GRACE_TICKS)return false;
            release(entry.getKey(),entry.getValue());
            return true;
        });
    }

    /** Reclaims all region tickets when WorldSeam switches off. */
    public static void clear() {
        for(var entry:sessions.entrySet())
            release(entry.getKey(),entry.getValue());
        sessions.clear();
    }

    public static int active(){return sessions.size();}
    public static long added(){return added;}
    public static long removed(){return removed;}
    public static long rejected(){return rejected;}
}
