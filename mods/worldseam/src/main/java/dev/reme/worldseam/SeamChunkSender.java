package dev.reme.worldseam;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Remote native chunk stream: never generate missing chunks synchronously.
 * One real full chunk per second per near-seam player. Each of the 3x3 chunks
 * is refreshed at most once per 200 ticks, bounding idle bandwidth.
 */
public final class SeamChunkSender {
    private static final int INTERVAL = 20;
    private static final int REFRESH_TICKS = 200;
    private static final double RADIUS_FROM_EDGE = 64;
    private static MinecraftServer lastServer;
    private static final Map<UUID, Session> sessions = new HashMap<>();

    private static final class Session {
        String scope = "";
        int cursor;
        final Map<Long, Long> lastSent = new HashMap<>();
    }
    private SeamChunkSender() {}

    public static void tick(MinecraftServer server, ServerPlayer player,
                            long tick, boolean enabled) {
        if (lastServer != server) {
            lastServer = server;
            sessions.clear();
        }
        Session session = sessions.computeIfAbsent(player.getUUID(), id -> new Session());
        if (!enabled) {
            session.scope = "";
            session.lastSent.clear();
            return;
        }
        ServerLevel source = player.serverLevel();
        String sourceId = source.dimension().location().toString();
        SeamGraph.Link link = null;
        for (SeamGraph.Link current : SeamGraph.from(sourceId)) {
            if (current.near(player.getY(), source.getMinBuildHeight(),
                SeamBounds.top(source), RADIUS_FROM_EDGE)) {
                link = current;
                break;
            }
        }
        if (link == null) {
            session.scope = "";
            session.lastSent.clear();
            return;
        }
        ServerLevel target = server.getLevel(SeamServer.dimensionKey(link.target()));
        if (target == null || tick % INTERVAL != 0) return;
        String scope = sourceId + ":" + link.target() + ":" + link.exit();
        if (!scope.equals(session.scope)) {
            session.scope = scope;
            session.cursor = 0;
            session.lastSent.clear();
        }

        int slot = session.cursor++ % 9;
        int x = Math.floorDiv(player.blockPosition().getX(),16) + (slot % 3) - 1;
        int z = Math.floorDiv(player.blockPosition().getZ(),16) + (slot / 3) - 1;
        // One destination chunk may be prepared per second, never an
        // unbounded synchronous remote load pass.
        if (!target.hasChunk(x,z)) target.getChunk(x,z);
        long key = ((long)x << 32) | (z & 0xffffffffL);
        if (tick - session.lastSent.getOrDefault(key, Long.MIN_VALUE / 2)
            < REFRESH_TICKS) return;

        // These bytes are the same canonical chunk payload MC uses for
        // real chunk ingestion, rather than inventing a fake block palette.
        ClientboundLevelChunkWithLightPacket nativePacket =
            new ClientboundLevelChunkWithLightPacket(
                target.getChunk(x,z), target.getLightEngine(), null, null
            );
        PacketDistributor.sendToPlayer(player,
            new SeamChunkPayload(sourceId, link.target(), x,z,
                nativePacket.getChunkData(), nativePacket.getLightData()));
        session.lastSent.put(key, tick);
    }

    public static void prune(Set<UUID> online) {
        sessions.keySet().retainAll(online);
    }
}
