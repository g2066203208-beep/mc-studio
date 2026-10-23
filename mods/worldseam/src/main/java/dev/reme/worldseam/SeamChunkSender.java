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
    // Residency state uses independent enter/release radii. Never pin both
    // Nether connection planes simply because the player is near Y64.
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
        SeamGraph.Link selectedLink;
        ServerLevel ticketLevel;
        ChunkPos ticketCenter;
        long ticketChanges;
        long chunkFetches;
        long waitingForAsyncChunks;
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
            selectedLink=null;
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
        UUID id=player.getUUID();
        Session session=sessions.get(id);
        if(!enabled) {
            if(session!=null){session.reset();sessions.remove(id);}
            return;
        }
        ServerLevel source=player.serverLevel();
        String sourceId=source.dimension().location().toString();
        var routes=SeamStackRuntime.from(server,sourceId);
        var near=new java.util.ArrayList<SeamPreloadSelection.Candidate>();
        var available=new java.util.HashMap<String,SeamGraph.Link>();
        for(SeamGraph.Link link:routes) {
            String key=sourceId+":"+link.target()+":"+link.exit();
            available.put(key,link);
            near.add(new SeamPreloadSelection.Candidate(key,
                Math.abs(player.getY()-
                    SeamStackRuntime.boundary(source,link.exit()))));
        }
        var chosen=SeamPreloadSelection.choose(
            near,session==null?"":session.scope);
        if(chosen==null) {
            if(session!=null){session.reset();sessions.remove(id);}
            return;
        }
        SeamGraph.Link selected=available.get(chosen.scope());
        ServerLevel target=selected==null?null:
            server.getLevel(SeamServer.dimensionKey(selected.target()));
        if(target==null) {
            if(session!=null){session.reset();sessions.remove(id);}
            return;
        }
        if(session==null) {
            session=new Session();
            sessions.put(id,session);
        }
        String scope=chosen.scope();
        if(!scope.equals(session.scope)) {
            session.reset();
            session.scope=scope;
        }
        session.selectedLink=selected;
        int centerX=Math.floorDiv(player.blockPosition().getX(),16);
        int centerZ=Math.floorDiv(player.blockPosition().getZ(),16);
        session.updateTicket(target,new ChunkPos(centerX,centerZ));
        // A second native ClientLevel does not receive vanilla entity
        // tracking packets from the other ServerLevel automatically.
        // Stream bounded real server entity snapshots independently of
        // chunk refresh; no server-side clone or extra physics authority.
        SeamRemoteEntitySender.tick(player,sourceId,selected,target,tick);

        // Warm the crossing-critical 3x3 rapidly, then the 5x5 visual
        // window. Once resident, send only bounded dirty-block updates or
        // infrequent 1200-tick refreshes. No permanent full-chunk stream.
        int cadence=session.lastSent.size()<9?2:
            (session.lastSent.size()<OFFSETS.length?5:INTERVAL);
        if(tick%cadence!=0)return;

        int slot=SeamChunkPriority.chooseSlot(centerX,centerZ,OFFSETS,
            session.lastSent,session.dirty,tick,REFRESH_TICKS,DIRTY_COOLDOWN);
        if(slot<0)return; // ALL targets still resident/fresh
        int cx=centerX+OFFSETS[slot][0],cz=centerZ+OFFSETS[slot][1];
        long key=((long)cx<<32)|(cz&0xffffffffL);

        // A controlled, bounded one-chunk fetch occurs only for unsent/stale
        // data. Temporary residency keeps the remaining chunks loaded.
        if(!target.hasChunk(cx,cz)) {
            // The region ticket has already requested this chunk from the
            // NORMAL server chunk scheduler. Synchronous getChunk() here
            // forces expensive terrain generation or disk I/O on the server
            // main thread. Wait for the ticket to complete asynchronously;
            // leave the item unsent so it is revisited next tick.
            session.waitingForAsyncChunks++;
            return;
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
                              long fetches,long ticketMoves,long packets,long liveUpdates,
                              long pendingAsyncWaits) {}

    public static StreamStats stats(ServerPlayer player) {
        Session session=sessions.get(player.getUUID());
        if(session==null)return new StreamStats(false,0,0,0,0,0,0,0);
        return new StreamStats(session.ticketLevel!=null,
            currentTargetLoaded(player),session.lastSent.size(),
            session.chunkFetches,session.ticketChanges,session.packetsSent,session.changeResends,
            session.waitingForAsyncChunks);
    }

    /**
     * Called by a ServerLevel post-update Mixin. Only the pinned remote
     * 5x5 window is invalidated; changes elsewhere create ZERO packets.
     * Chunk+light snapshot is resent at most once/20 ticks per changed chunk,
     * including dynamic block and block-entity state from native packet.
     */
    public static void markBlockChanged(ServerLevel level,BlockPos pos) {
        // Keep already-projected target blocks in the ONE Sable Rapier solver
        // up-to-date when real gameplay mutates the other ServerLevel.
        SeamRapierRemoteTerrain.markBlockChanged(level,pos);
        int x=Math.floorDiv(pos.getX(),16),z=Math.floorDiv(pos.getZ(),16);
        long key=((long)x<<32)|(z&0xffffffffL);
        for(Session s:sessions.values()) {
            if(s.ticketLevel!=level || s.ticketCenter==null)continue;
            if(Math.abs(s.ticketCenter.x-x)>WINDOW_RADIUS ||
               Math.abs(s.ticketCenter.z-z)>WINDOW_RADIUS)continue;
            if(s.lastSent.containsKey(key))s.dirty.add(key);
        }
    }

    /** The one authoritative selected adjacent dimension for all streams. */
    public static SeamGraph.Link activeLink(ServerPlayer player) {
        Session s=sessions.get(player.getUUID());
        return s==null?null:s.selectedLink;
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
