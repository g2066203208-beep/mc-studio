package dev.reme.worldseam;

import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SECOND-WORLD RESIDENCY, not a periodic getChunk() poll.
 *
 * Every player near a vertical seam temporarily pins a 5x5 destination
 * window with an ordinary (non-force-ticking) chunk region ticket.
 * The actual current dimension remains loaded via vanilla player tickets.
 * Tickets follow movement and are RELEASED on leaving the seam, dimension
 * changes, toggling the mod, disconnect, or server shutdown/replacement.
 *
 * Arrival chunks are streamed center-first; only changed/new windows are
 * fetched, avoiding generating the same unloaded chunk each scheduler tick.
 */
public final class SeamChunkSender {
    private static final org.slf4j.Logger LOG=LogUtils.getLogger();
    private static final int INTERVAL=10;
    private static final int REFRESH_TICKS=1200;
    private static final int DIRTY_COOLDOWN=20;
    private static final int WINDOW_RADIUS=2;
    private static final int TICKET_DISTANCE=3;
    private static final double RADIUS_FROM_EDGE=96;
    private static final TicketType<ChunkPos> VIEW_TICKET=TicketType.create(
        "worldseam_dual_view", Comparator.comparingLong(ChunkPos::toLong)
    );
    private static final int[][] OFFSETS=offsets();
    private static MinecraftServer lastServer;
    private static final Map<UUID,Session> sessions=new HashMap<>();

    private static int[][] offsets() {
        var list=new java.util.ArrayList<int[]>();
        for(int z=-WINDOW_RADIUS;z<=WINDOW_RADIUS;z++)
            for(int x=-WINDOW_RADIUS;x<=WINDOW_RADIUS;x++)
                list.add(new int[]{x,z});
        list.sort(Comparator.comparingInt((int[] a) ->
            Math.max(Math.abs(a[0]),Math.abs(a[1]))*100+a[0]*a[0]+a[1]*a[1]));
        return list.toArray(new int[0][]);
    }

    private static final class Session {
        String scope="";
        ServerLevel ticketLevel;
        ChunkPos ticketCenter;
        long ticketChanges;
        long chunkFetches;
        long packetsSent;
        final Map<Long,Long> lastSent=new HashMap<>();
        final Set<Long> dirty=new HashSet<>();
        long changeResends;

        void release() {
            if(ticketLevel!=null && ticketCenter!=null)
                ticketLevel.getChunkSource().removeRegionTicket(
                    VIEW_TICKET,ticketCenter,TICKET_DISTANCE,ticketCenter);
            ticketLevel=null;
            ticketCenter=null;
        }

        void updateTicket(ServerLevel destination,ChunkPos center) {
            if(ticketLevel==destination && center.equals(ticketCenter)) return;
            release();
            ticketLevel=destination;
            ticketCenter=center;
            destination.getChunkSource().addRegionTicket(
                VIEW_TICKET,center,TICKET_DISTANCE,center);
            ticketChanges++;
            // A new nearby view must stream its own 25 chunks first.
            // Retain only chunks still in the current window.
            lastSent.keySet().removeIf(key -> {
                int cx=(int)(key>>32),cz=(int)(long)key;
                return Math.abs(cx-center.x)>WINDOW_RADIUS ||
                    Math.abs(cz-center.z)>WINDOW_RADIUS;
            });
            dirty.retainAll(lastSent.keySet());
        }

        void reset() {
            release();
            scope="";
            lastSent.clear();
            dirty.clear();
        }
    }

    private SeamChunkSender() {}

    public static void tick(MinecraftServer server,ServerPlayer player,
                            long tick,boolean enabled) {
        if(lastServer!=server) {
            for(Session s:sessions.values())s.reset();
            sessions.clear();
            lastServer=server;
        }
        Session session=sessions.computeIfAbsent(player.getUUID(),id->new Session());
        if(!enabled) {session.reset();return;}

        ServerLevel source=player.serverLevel();
        String sourceId=source.dimension().location().toString();
        SeamGraph.Link selected=null;
        double best=Double.POSITIVE_INFINITY;
        for(SeamGraph.Link link:SeamStackRuntime.from(server,sourceId)) {
            double seam=SeamStackRuntime.boundary(source,link.exit());
            double delta=Math.abs(player.getY()-seam);
            if(delta<=RADIUS_FROM_EDGE && delta<best) {
                best=delta;
                selected=link;
            }
        }
        if(selected==null) {session.reset();return;}

        ServerLevel target=server.getLevel(SeamServer.dimensionKey(selected.target()));
        if(target==null) {session.reset();return;}

        String scope=sourceId+":"+selected.target()+":"+selected.exit();
        if(!scope.equals(session.scope)) {
            session.reset();
            session.scope=scope;
        }
        int centerX=Math.floorDiv(player.blockPosition().getX(),16);
        int centerZ=Math.floorDiv(player.blockPosition().getZ(),16);
        session.updateTicket(target,new ChunkPos(centerX,centerZ));

        // Warm the crossing-critical 3x3 rapidly, then the 5x5 visual
        // window. Once resident, send only bounded dirty-block updates or
        // infrequent 1200-tick refreshes. No permanent full-chunk stream.
        int cadence=session.lastSent.size()<9?2:
            (session.lastSent.size()<OFFSETS.length?5:INTERVAL);
        if(tick%cadence!=0)return;

        // Search the entire bounded 5x5 set for the FIRST missing/stale
        // chunk, prioritizing the player's near 3x3. On movement the
        // prior implementation wasted 20 scheduled slots on already-cached
        // chunks before reaching the new ring edge.
        int slot=-1,cx=0,cz=0;
        long key=0L;
        for(int i=0;i<OFFSETS.length;i++) {
            int testX=centerX+OFFSETS[i][0],testZ=centerZ+OFFSETS[i][1];
            long candidate=((long)testX<<32)|(testZ&0xffffffffL);
            long sentAt=session.lastSent.getOrDefault(candidate,Long.MIN_VALUE/2);
            boolean changed=session.dirty.contains(candidate)
                && tick-sentAt>=DIRTY_COOLDOWN;
            if(tick-sentAt<REFRESH_TICKS && !changed)continue;
            slot=i;
            cx=testX;
            cz=testZ;
            key=candidate;
            break;
        }
        if(slot<0)return; // warm and stable: ZERO getChunk(), ZERO packets

        // A controlled, bounded one-chunk fetch occurs only for unsent/stale
        // data. Temporary residency keeps the remaining chunks loaded.
        if(!target.hasChunk(cx,cz)) {
            session.chunkFetches++;
            target.getChunk(cx,cz);
        }
        ClientboundLevelChunkWithLightPacket vanilla =
            new ClientboundLevelChunkWithLightPacket(
                target.getChunk(cx,cz),target.getLightEngine(),null,null);
        PacketDistributor.sendToPlayer(player,
            new SeamChunkPayload(sourceId,selected.target(),cx,cz,
                vanilla.getChunkData(),vanilla.getLightData()));
        session.lastSent.put(key,tick);
        if(session.dirty.remove(key))session.changeResends++;
        session.packetsSent++;
        if(slot==8 || slot==24)
            LOG.info("WorldSeam dual-world target={} center={},{} cached-window={}/25 sent={} forcedFetches={} ticketMoves={}",
                selected.target(),centerX,centerZ,session.lastSent.size(),
                session.packetsSent,session.chunkFetches,session.ticketChanges);
    }

    public record StreamStats(boolean ticketActive,int resident,int transmitted,
                              long fetches,long ticketMoves,long packets,long liveUpdates) {}

    public static StreamStats stats(ServerPlayer player) {
        Session session=sessions.get(player.getUUID());
        if(session==null)return new StreamStats(false,0,0,0,0,0,0);
        return new StreamStats(session.ticketLevel!=null,
            currentTargetLoaded(player),session.lastSent.size(),
            session.chunkFetches,session.ticketChanges,session.packetsSent,session.changeResends);
    }

    /**
     * Called by a ServerLevel post-update Mixin. Only the pinned remote
     * 5x5 window is invalidated; changes elsewhere create ZERO packets.
     * Chunk+light snapshot is resent at most once/20 ticks per changed chunk,
     * including dynamic block and block-entity state from native packet.
     */
    public static void markBlockChanged(ServerLevel level,BlockPos pos) {
        int x=Math.floorDiv(pos.getX(),16),z=Math.floorDiv(pos.getZ(),16);
        long key=((long)x<<32)|(z&0xffffffffL);
        for(Session s:sessions.values()) {
            if(s.ticketLevel!=level || s.ticketCenter==null)continue;
            if(Math.abs(s.ticketCenter.x-x)>WINDOW_RADIUS ||
               Math.abs(s.ticketCenter.z-z)>WINDOW_RADIUS)continue;
            if(s.lastSent.containsKey(key))s.dirty.add(key);
        }
    }

    public static int activeTickets() {
        int count=0;
        for(Session session:sessions.values())
            if(session.ticketLevel!=null)count++;
        return count;
    }

    public static int currentTargetLoaded(ServerPlayer player) {
        Session session=sessions.get(player.getUUID());
        if(session==null||session.ticketLevel==null||session.ticketCenter==null)
            return 0;
        int count=0;
        for(int z=-WINDOW_RADIUS;z<=WINDOW_RADIUS;z++)
            for(int x=-WINDOW_RADIUS;x<=WINDOW_RADIUS;x++)
                if(session.ticketLevel.hasChunk(session.ticketCenter.x+x,
                    session.ticketCenter.z+z))count++;
        return count;
    }

    public static void prune(Set<UUID> online) {
        sessions.entrySet().removeIf(e->{
            if(online.contains(e.getKey()))return false;
            e.getValue().reset();
            return true;
        });
    }
}
