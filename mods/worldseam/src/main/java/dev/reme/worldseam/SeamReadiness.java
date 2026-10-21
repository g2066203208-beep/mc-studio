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
    public static final int REQUIRED_CHUNKS = 9;
    public static final int EXPIRY_TICKS = 100;
    private record State(String source, String target, long tick) {}
    private static final Map<UUID, State> sessions = new HashMap<>();

    private SeamReadiness() {}

    public static void accept(UUID playerId, String source, String target,
                              int reportedChunks, long tick) {
        if (reportedChunks < REQUIRED_CHUNKS || !SeamGraph.from(source).stream()
            .anyMatch(link -> link.target().equals(target))) return;
        sessions.put(playerId, new State(source,target,tick));
    }

    public static boolean isReady(UUID id, String source, String target, long tick) {
        State s=sessions.get(id);
        return s!=null && s.source().equals(source) && s.target().equals(target)
            && tick>=s.tick() && tick-s.tick()<=EXPIRY_TICKS;
    }

    public static void invalidate(UUID id) { sessions.remove(id); }
    public static void clear() { sessions.clear(); }

    public static void acceptNetwork(ServerPlayer player, SeamReadyPayload message) {
        if (player.serverLevel().dimension().location().toString().equals(message.source())) {
            accept(player.getUUID(),message.source(),message.target(),
                message.loadedChunks(),player.serverLevel().getGameTime());
        }
    }
}
