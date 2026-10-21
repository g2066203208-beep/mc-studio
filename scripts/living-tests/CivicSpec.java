package dev.reme.mcstudio.npc.life;

import java.util.UUID;

public final class CivicSpec {
    private static int count;
    private static void ok(boolean v, String why) {
        count++;
        if (!v) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        var guard = new CivicMind();
        var baker = new CivicMind();
        UUID attacker = UUID.randomUUID(), witness = UUID.randomUUID();
        UUID other = UUID.randomUUID(), reportId = UUID.randomUUID();
        ok(guard.witness(reportId, attacker, witness, CivicMind.Kind.ASSAULT, 100), "direct event");
        ok(!guard.witness(reportId, attacker, witness, CivicMind.Kind.ASSAULT, 100), "no double witness");
        ok(guard.reputation(attacker) == -60, "direct is serious");
        ok(guard.reputation(other) == 0, "per-person reputation");
        ok(!baker.hear(guard.reports().get(0), 200, false, true), "no cross-region");
        ok(!baker.hear(guard.reports().get(0), 200, true, false), "untrusted rejected");
        ok(baker.hear(guard.reports().get(0), 200, true, true), "local gossip");
        ok(baker.reputation(attacker) == -30, "gossip weaker than observation");
        ok(!guard.hear(baker.reports().get(0), 300, true, true), "no rumor echo");
        ok(!baker.hear(guard.reports().get(0), 200, true, true), "dedupe");
        ok(!new CivicMind().hear(guard.reports().get(0), 26000, true, true), "ttl");
        ok(guard.standing(attacker, 100) == CivicMind.Standing.WANTED, "high justice");
        ok(guard.standing(attacker, 0) == CivicMind.Standing.NEUTRAL, "low justice");
        var job = new Commission(Commission.Goods.WHEAT, 3, 2);
        ok(!job.finish(attacker), "cannot finish unaccepted");
        ok(job.accept(attacker), "accept");
        ok(!job.accept(other), "exclusive contract");
        ok(!job.finish(other), "cannot steal reward");
        ok(job.finish(attacker), "finish once");
        ok(!job.finish(attacker), "no repeat reward");
        ok(!job.cancel(), "no cancelling completed");
        var restored = new Commission(Commission.Goods.BREAD, 1, 1);
        restored.restore(Commission.State.ACCEPTED, other);
        ok(restored.canComplete(other), "contract restore");
        ok(CharacterProfile.fromSeed(11).curiosity() >= 25, "seed traits bounded");
        for (int i = 0; i < 100; i++)
            guard.witness(UUID.randomUUID(), other, witness, CivicMind.Kind.GIFT, 250+i);
        ok(guard.reports().size() == 48, "bounded civic memory");
        System.out.println("CivicSpec: " + count + " assertions passed");
    }
}
