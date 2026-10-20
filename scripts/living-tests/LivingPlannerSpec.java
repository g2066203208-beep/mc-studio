package dev.reme.mcstudio.npc.life;

import java.util.UUID;

/** Plain Java executable assertions; run without loading Minecraft or JUnit. */
public final class LivingPlannerSpec {
    private static void expect(boolean correct, String message) {
        if (!correct) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        LivingPlanner brain = new LivingPlanner(LivingPlanner.Personality.average());
        expect(brain.choose(new LivingPlanner.Context(3000, false, false, false, 90, 90, 85, true))
            .intent() == LivingPlanner.Intent.WORK, "day shift must choose work");
        expect(brain.choose(new LivingPlanner.Context(3000, false, false, true, 90, 90, 85, true))
            .intent() == LivingPlanner.Intent.ESCAPE, "danger must interrupt work");
        expect(brain.choose(new LivingPlanner.Context(3000, false, false, false, 90, 90, 85, true))
            .intent() == LivingPlanner.Intent.WORK, "resume work after threat");
        expect(brain.choose(new LivingPlanner.Context(3000, false, false, false, 5, 90, 85, true))
            .intent() == LivingPlanner.Intent.EAT, "hunger must override scheduled work");
        expect(brain.choose(new LivingPlanner.Context(3000, true, true, false, 95, 95, 95, true))
            .intent() == LivingPlanner.Intent.SEEK_SHELTER, "rain must interrupt outdoor work");
        expect(brain.choose(new LivingPlanner.Context(17000, false, false, false, 90, 60, 90, true))
            .intent() == LivingPlanner.Intent.SLEEP, "night calls for rest");
        expect(brain.choose(new LivingPlanner.Context(10000, false, false, false, 90, 90, 35, true))
            .intent() == LivingPlanner.Intent.SOCIALIZE, "evening social opportunity");

        ResidentMemory memory = new ResidentMemory();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        memory.witness(100, alice, ResidentMemory.Kind.HELPED, "market");
        memory.witness(200, bob, ResidentMemory.Kind.THEFT_WITNESSED, "smithy");
        expect(memory.trust(alice) == 12 && memory.trust(bob) == -25,
            "different people must have separate reputations");
        for (int i = 0; i < 100; i++) {
            memory.witness(300 + i, alice, ResidentMemory.Kind.GREETING, "square");
        }
        expect(memory.recentEvents().size() == 48, "memory must have a hard cap");
        expect(memory.trust(alice) <= 100, "trust must stay bounded");
        expect(memory.recentEvents().get(0).worldTick() == 352,
            "oldest event must be evicted deterministically");
        System.out.println("LivingPlannerSpec: 11 assertions passed");
    }
}
