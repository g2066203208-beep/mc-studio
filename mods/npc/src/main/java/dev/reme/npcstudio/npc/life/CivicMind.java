package dev.reme.npcstudio.npc.life;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded social knowledge with provenance: knowing a rumour is NOT the same
 * as witnessing the underlying event. Only nearby trusted listeners receive it.
 * Events carry stable ids so gossip loops cannot duplicate accusations.
 */
public final class CivicMind {
    public enum Kind { ASSAULT, THEFT, AID, GIFT, HONORED_CONTRACT }
    public enum Standing { WANTED, SUSPECTED, NEUTRAL, RESPECTED }
    public record Report(UUID id, UUID actor, UUID firstWitness, Kind kind,
                         long tick, int hops, boolean direct) {
        public Report {
            if (id == null || actor == null || firstWitness == null || kind == null ||
                hops < 0 || hops > 2 || tick < 0)
                throw new IllegalArgumentException("bad civic report");
        }
    }
    private static final int MAX = 48;
    public static final long NEWS_TTL = 24000L; // one MC day
    private final Deque<Report> reports = new ArrayDeque<>();
    private final Set<UUID> ids = new HashSet<>();

    public void clear() { reports.clear(); ids.clear(); }
    public List<Report> reports() { return List.copyOf(new ArrayList<>(reports)); }

    public boolean witness(UUID id, UUID actor, UUID self, Kind kind, long tick) {
        return add(new Report(id, actor, self, kind, tick, 0, true));
    }

    /**
     * Conversation is an actual local transmission, not a global broadcast.
     * Only first-hand evidence may be forwarded; no multi-hop invention.
     * Listener applies weaker weight than first-hand witness.
     */
    public boolean hear(Report report, long now, boolean sameRegion,
                        boolean trustedSpeaker) {
        if (!sameRegion || !trustedSpeaker || report.hops() != 0 ||
            report.tick() > now || now - report.tick() > NEWS_TTL)
            return false;
        return add(new Report(report.id(), report.actor(), report.firstWitness(),
            report.kind(), report.tick(), 1, false));
    }

    public void restore(Report report) { add(report); }

    private boolean add(Report report) {
        if (ids.contains(report.id())) return false;
        if (reports.size() >= MAX) ids.remove(reports.removeFirst().id());
        reports.addLast(report);
        ids.add(report.id());
        return true;
    }

    public int reputation(UUID actor) {
        int value = 0;
        for (Report report : reports) {
            if (!report.actor().equals(actor)) continue;
            int delta = switch (report.kind()) {
                case ASSAULT -> -60;
                case THEFT -> -40;
                case AID -> 22;
                case GIFT -> 8;
                case HONORED_CONTRACT -> 14;
            };
            value += report.direct() ? delta : delta / 2;
        }
        return Math.clamp(value, -100, 100);
    }

    public Standing standing(UUID actor, int justice) {
        int score = reputation(actor);
        int effective = score * Math.clamp(justice, 0, 100) / 100;
        if (effective <= -35) return Standing.WANTED;
        if (effective <= -15) return Standing.SUSPECTED;
        if (score >= 30) return Standing.RESPECTED;
        return Standing.NEUTRAL;
    }
}
