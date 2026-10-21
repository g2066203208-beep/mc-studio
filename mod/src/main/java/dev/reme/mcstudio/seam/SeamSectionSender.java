package dev.reme.mcstudio.seam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Back-end for an independent multi-dimension section stream.
 *
 * Only existing loaded target chunks are sampled (NO synchronous chunk
 * generation here). Each active player sees up to 3x3 columns and two
 * sections deep. One full 16^3 section may be encoded per 5 server ticks.
 */
public final class SeamSectionSender {
    private static final int INTERVAL = 5;
    private static final int SECTIONS_PER_SCOPE = 3 * 3 * 2;
    private static final int MAX_SENT_SIGNATURES = 72;
    private static final double ACTIVATION_DISTANCE = 48;
    private static MinecraftServer lastServer;

    private static final class Session {
        String scope = "";
        long revision = 0;
        int cursor = 0;
        boolean active = false;
        final Map<SeamSectionCache.Key, SeamSectionCodec.Encoded> sent =
            new LinkedHashMap<>();
    }
    private static final Map<UUID, Session> sessions = new HashMap<>();

    private SeamSectionSender() {}

    public static void tick(MinecraftServer server, ServerPlayer player,
                            long gameTick, boolean enabled) {
        if (server != lastServer) {
            lastServer = server;
            sessions.clear();
        }
        UUID id = player.getUUID();
        Session s = sessions.computeIfAbsent(id, ignored -> new Session());
        ServerLevel source = player.serverLevel();
        String sourceName = source.dimension().location().toString();
        SeamGraph.Link selected = null;
        if (enabled) {
            for (SeamGraph.Link link : SeamGraph.from(sourceName)) {
                if (link.near(player.getY(), source.getMinBuildHeight(),
                    source.getMaxBuildHeight(), ACTIVATION_DISTANCE)) {
                    selected = link;
                    break;
                }
            }
        }
        if (selected == null) {
            if (s.active) {
                s.active = false;
                s.sent.clear();
                s.scope = "";
                send(player, new SeamSectionCache.Frame(
                    sourceName, "", "", ++s.revision, true,
                    null, 0, 0, null
                ));
            }
            // no permanent per-player session for players who are far away
            return;
        }
        ServerLevel target = server.getLevel(SeamServer.dimensionKey(selected.target()));
        if (target == null || gameTick % INTERVAL != 0) return;
        String scope = sourceName + ">" + selected.target() + ":" + selected.exit();
        if (!scope.equals(s.scope)) {
            s.scope = scope;
            s.sent.clear();
            s.cursor = 0;
        }
        s.active = true;

        int sourceBoundary = (int) SeamGraph.boundaryY(selected.exit(),
            source.getMinBuildHeight(), source.getMaxBuildHeight());
        int targetBoundary = (int) SeamGraph.boundaryY(selected.entry(),
            target.getMinBuildHeight(), target.getMaxBuildHeight());

        // Deterministically rotate the 18 (3x3x2) near-boundary sections.
        int slot = s.cursor++ % SECTIONS_PER_SCOPE;
        int depth = slot / 9;
        int xOffset = (slot % 9) % 3 - 1;
        int zOffset = (slot % 9) / 3 - 1;
        int cx = Math.floorDiv(player.blockPosition().getX(), 16) + xOffset;
        int cz = Math.floorDiv(player.blockPosition().getZ(), 16) + zOffset;
        int sectionY = Math.floorDiv(
            selected.entry() == SeamGraph.EdgeSide.TOP
                ? targetBoundary - 1 : targetBoundary, 16
        ) + (selected.entry() == SeamGraph.EdgeSide.TOP ? -depth : depth);
        int minSection = Math.floorDiv(target.getMinBuildHeight(), 16);
        int maxSection = Math.floorDiv(target.getMaxBuildHeight() - 1, 16);
        if (sectionY < minSection || sectionY > maxSection
            || !target.hasChunk(cx, cz)) return;

        var key = new SeamSectionCache.Key(cx, sectionY, cz);
        var encoded = encode(target, cx, sectionY, cz);
        var previous = s.sent.get(key);
        if (previous != null && SeamSectionCodec.same(previous, encoded)) return;
        s.sent.put(key, encoded);
        while (s.sent.size() > MAX_SENT_SIGNATURES)
            s.sent.remove(s.sent.keySet().iterator().next());
        send(player, new SeamSectionCache.Frame(
            sourceName, selected.target(), scope,
            ++s.revision, false, key, sourceBoundary, targetBoundary, encoded
        ));
    }

    private static SeamSectionCodec.Encoded encode(ServerLevel target,
                int chunkX, int sectionY, int chunkZ) {
        LevelChunk chunk = target.getChunk(chunkX, chunkZ);
        LevelChunkSection section = chunk.getSection(
            chunk.getSectionIndexFromSectionY(sectionY)
        );
        int[] states = new int[SeamSectionCodec.VOLUME];
        byte[] light = new byte[SeamSectionCodec.VOLUME];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = SeamSectionCodec.index(x, y, z);
                    var block = section.getBlockState(x, y, z);
                    states[index] = Block.getId(block);
                    if (block.isAir()) continue;
                    pos.set((chunkX << 4) + x, (sectionY << 4) + y,
                        (chunkZ << 4) + z);
                    light[index] = (byte) (target.getBrightness(LightLayer.BLOCK, pos)
                        | (target.getBrightness(LightLayer.SKY, pos) << 4));
                }
            }
        }
        return SeamSectionCodec.encode(states, light);
    }

    private static void send(ServerPlayer player, SeamSectionCache.Frame frame) {
        PacketDistributor.sendToPlayer(player, new SeamSectionPayload(frame));
    }

    public static void prune(Set<UUID> online) {
        sessions.keySet().retainAll(online);
    }
}
