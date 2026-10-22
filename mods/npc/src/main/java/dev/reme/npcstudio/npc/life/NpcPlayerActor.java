package dev.reme.npcstudio.npc.life;

import com.mojang.authlib.GameProfile;
import dev.reme.npcstudio.npc.PlayerNpcEntity;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Single shared identity for all player-like actions of the same resident.
 *
 * FakePlayerFactory keys by ServerLevel + GameProfile (both UUID AND name):
 * differing names would produce separate player caches per NPC and could
 * diverge permissions or leak advancement listeners under repeated tasks.
 *
 * Not a networked ServerPlayer. Obtain on demand, restore held tools in
 * finally after every action. Factory cleans up the cache on level unload.
 */
public final class NpcPlayerActor {
    private NpcPlayerActor() {}

    public static FakePlayer forNpc(PlayerNpcEntity npc, ServerLevel level) {
        String shortId = npc.getUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        FakePlayer actor = FakePlayerFactory.get(level,
            new GameProfile(npc.getUUID(), "REME_" + shortId));
        actor.getAdvancements().stopListening();
        actor.moveTo(npc.getX(), npc.getY(), npc.getZ(),
            npc.getYRot(), npc.getXRot());
        actor.setOnGround(npc.onGround());
        return actor;
    }
}
