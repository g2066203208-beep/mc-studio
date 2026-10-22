package dev.reme.npcstudio.npc.life;

import dev.reme.npcstudio.npc.PlayerNpcEntity;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Witnessing, localized gossip and law-aware reactions without global omniscience. */
public final class CivicSocial {
    private CivicSocial() {}

    public static void witnessedAssault(PlayerNpcEntity victim, Player attacker,
                                        ServerLevel level) {
        long now = level.getGameTime();
        // Direct incidents are stable across all simultaneous eyewitnesses.
        UUID incident = UUID.randomUUID();
        victim.civic().witness(incident, attacker.getUUID(), victim.getUUID(),
            CivicMind.Kind.ASSAULT, now);
        for (PlayerNpcEntity witness : level.getEntitiesOfClass(PlayerNpcEntity.class,
            victim.getBoundingBox().inflate(12.0),
            npc -> npc != victim && npc.isAlive() &&
                npc.identity().region().equals(victim.identity().region()) &&
                !npc.hostileTo(victim) &&
                npc.hasLineOfSight(attacker))) {
            witness.civic().witness(incident, attacker.getUUID(), victim.getUUID(),
                CivicMind.Kind.ASSAULT, now);
            // Direct perception makes the witness distrust the offender.
            witness.memory().witness(now, attacker.getUUID(),
                ResidentMemory.Kind.ATTACK_WITNESSED,
                victim.blockPosition().toShortString());
        }
    }

    public static int exchangeNews(PlayerNpcEntity first, PlayerNpcEntity second,
                                   long now) {
        if (first.hostileTo(second)) return 0;
        boolean sameRegion = first.identity().region().equals(second.identity().region());
        if (!sameRegion) return 0;
        int shared = 0;
        shared += transmit(first, second, now);
        shared += transmit(second, first, now);
        return shared;
    }

    private static int transmit(PlayerNpcEntity speaker, PlayerNpcEntity listener,
                                long now) {
        if (!speaker.character().sharesRumour() ||
            listener.memory().trust(speaker.getUUID()) <= -20) return 0;
        int shared = 0;
        for (CivicMind.Report report : speaker.civic().reports()) {
            // Rumours never become eyewitnesses or recurse indefinitely.
            if (listener.civic().hear(report, now, true, true)) shared++;
            if (shared == 3) break; // bounded message budget per conversation
        }
        return shared;
    }

    public static void giveCredit(PlayerNpcEntity npc, Player player,
                                  CivicMind.Kind kind) {
        npc.civic().witness(UUID.randomUUID(), player.getUUID(), npc.getUUID(),
            kind, npc.level().getGameTime());
    }

    public static void guardLaw(PlayerNpcEntity guard, ServerLevel level, long tick) {
        if (guard.character().justice() < 45 ||
            tick % 100 != Math.floorMod(guard.getId(), 100)) return;
        for (Player nearby : level.getEntitiesOfClass(Player.class,
                guard.getBoundingBox().inflate(9),
                user -> user.isAlive() && !user.isSpectator() &&
                    guard.hasLineOfSight(user))) {
            CivicMind.Standing status = guard.civic().standing(
                nearby.getUUID(), guard.character().justice());
            if (status == CivicMind.Standing.WANTED) {
                // No arbitrary killing, confiscation or police teleports.
                guard.setAction("warning suspected violent offender " +
                    nearby.getName().getString() + "; justice evidence on record");
                break;
            }
        }
    }
}
