package dev.reme.worldseam;

import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative gating of experimental no-respawn crossings.
 *
 * A ready assertion cannot select a destination: the server always checks
 * actual player dimension, configured link, world border and arrival collision.
 */
public final class SeamReadiness {
    public static final int REQUIRED_CHUNKS = 25;
    public static final int EXPIRY_TICKS = 100;
    private record State(String source, String target, long tick, int chunkX, int chunkZ) {}
    private static final Map<UUID, State> sessions = new HashMap<>();

    private SeamReadiness() {}

    public static void accept(UUID playerId, String source, String target,
                              int reportedChunks, long tick) {
        if (reportedChunks < REQUIRED_CHUNKS || !SeamGraph.from(source).stream()
            .anyMatch(link -> link.target().equals(target))) return;
        sessions.put(playerId, new State(source,target,tick,0,0));
    }

    /**
     * An ACK is evidence for the client-observed center, not whichever center
     * the server player happens to occupy when the packet arrives. Reject a
     * delayed ACK after a chunk-boundary move instead of silently rebinding it.
     */
    static boolean acceptAuthorized(UUID playerId, String source, String target,
                              int reportedChunks, long tick, int reportedX, int reportedZ,
                              int serverX, int serverZ) {
        if (reportedChunks < REQUIRED_CHUNKS ||
            reportedX != serverX || reportedZ != serverZ) {
            sessions.remove(playerId);
            return false;
        }
        sessions.put(playerId,new State(source,target,tick,reportedX,reportedZ));
        return true;
    }

    public static boolean isReady(UUID id, String source, String target, long tick) {
        State s=sessions.get(id);
        return s!=null && s.source().equals(source) && s.target().equals(target)
            && tick>=s.tick() && tick-s.tick()<=EXPIRY_TICKS;
    }

    /**
     * A previously complete 5x5 becomes incomplete if the player moves to
     * another chunk after ACK. The server must reject the stale ACK BEFORE
     * moving the authoritative player, otherwise the client can refuse the
     * handoff after the server has already switched dimensions.
     */
    public static boolean isReadyAt(UUID id, String source, String target,
                                    long tick, int chunkX, int chunkZ) {
        State state = sessions.get(id);
        return isReady(id,source,target,tick) && state.chunkX()==chunkX
            && state.chunkZ()==chunkZ;
    }

    public static void invalidate(UUID id) { sessions.remove(id); }
    public static void clear() { sessions.clear(); }

    public static void acceptNetwork(ServerPlayer player, SeamReadyPayload message) {
        if (!player.serverLevel().dimension().location().toString().equals(message.source()))
            return;
        var targetLevel = player.server.getLevel(SeamServer.dimensionKey(message.target()));
        if (targetLevel == null) return;
        // CRITICAL: Do NOT require all 25 target SERVER chunks to remain
        // loaded. They have already been transmitted and decoded into the
        // CLIENT's independent chunk cache. A destination with no players
        // naturally unloads its server chunks between the one-per-second
        // packets, so the old server-side hasChunk gate prevented EVERY crossing.
        // The single arrival chunk is loaded authoritatively during actual
        // SeamServer crossing, and arrival collision/border checks still run.
        if (message.loadedChunks() < REQUIRED_CHUNKS) return;
        boolean nearLinkedBorder = SeamStackRuntime.from(player.server,message.source()).stream()
            .anyMatch(link -> link.target().equals(message.target())
                && link.near(player.getY(),SeamStackRuntime.bottom(player.serverLevel()),
                    SeamStackRuntime.top(player.serverLevel()),64));
        if (!nearLinkedBorder) return;
        long tick=player.server.overworld().getGameTime();
        boolean newAcknowledgement=!isReadyAt(player.getUUID(),
            message.source(),message.target(),tick,
            Math.floorDiv(player.blockPosition().getX(),16),
            Math.floorDiv(player.blockPosition().getZ(),16));
        if (!acceptAuthorized(player.getUUID(),message.source(),message.target(),
            message.loadedChunks(),tick,
            message.centerChunkX(),message.centerChunkZ(),
            Math.floorDiv(player.blockPosition().getX(),16),
            Math.floorDiv(player.blockPosition().getZ(),16))) return;
        if (newAcknowledgement)
            com.mojang.logging.LogUtils.getLogger().info(
                "WorldSeam READY ACK accepted {} -> {} (client {} native chunks)",
                message.source(),message.target(),message.loadedChunks());
    }
}
