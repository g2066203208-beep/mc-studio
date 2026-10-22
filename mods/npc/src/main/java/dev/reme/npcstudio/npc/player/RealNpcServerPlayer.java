package dev.reme.npcstudio.npc.player;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Isolated proof of a REAL vanilla Player superclass, rather than a mob
 * wearing a player texture. Future implementation must register a virtual
 * network connection through PlayerList BEFORE becoming a live resident.
 *
 * IMPORTANT: constructing a ServerPlayer alone does NOT register a real
 * player, send player-info packets, provide a client RemotePlayer avatar or
 * prove mod compatibility. detachedForGameTest is NOT a spawn method.
 */
public final class RealNpcServerPlayer extends ServerPlayer {
    private final UUID originalResidentId;

    private RealNpcServerPlayer(MinecraftServer server, ServerLevel level,
                                GameProfile profile, UUID originalResidentId) {
        super(server, level, profile, ClientInformation.createDefault());
        this.originalResidentId = originalResidentId;
    }

    public UUID originalResidentId() {
        return originalResidentId;
    }

    /**
     * Strictly for isolated server GameTests. Never call addFreshEntity() or
     * treat this as a logged-in player: it has no connected network listener.
     * A separate UUID avoids colliding with a still-running legacy Mob.
     */
    public static RealNpcServerPlayer detachedForGameTest(ServerLevel level, UUID legacyNpcId) {
        String name = "RNPC_" + legacyNpcId.toString().substring(0, 8);
        UUID profileId = UUID.nameUUIDFromBytes(
            ("reme:real-player-npc:" + legacyNpcId).getBytes(StandardCharsets.UTF_8));
        if (profileId.equals(legacyNpcId))
            throw new IllegalStateException("Player migration identity collision");
        RealNpcServerPlayer player = new RealNpcServerPlayer(level.getServer(), level,
            new GameProfile(profileId, name), legacyNpcId);
        // A disposable headless test actor must not leak advancement criteria.
        player.getAdvancements().stopListening();
        return player;
    }
}
