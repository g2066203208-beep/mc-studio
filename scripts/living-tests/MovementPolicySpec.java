package dev.reme.mcstudio.npc.life;

public final class MovementPolicySpec {
    private static int checked;
    private static void ok(boolean yes, String why) {
        if (!yes) throw new AssertionError(why);
        checked++;
    }
    public static void main(String[] args) {
        ok(MovementPolicy.radius(-100) == 16, "min");
        ok(MovementPolicy.radius(900) == 128, "max");
        ok(MovementPolicy.radius(64) == 64, "normal");
        ok(MovementPolicy.region(-1, -1).equals("sector_-1_-1"), "negative regions");
        ok(MovementPolicy.region(511, 511).equals("sector_0_0"), "last cell position");
        ok(MovementPolicy.region(512, 0).equals("sector_1_0"), "regional boundary");
        ok(MovementPolicy.within(0, 0, 30, 40, 50), "boundary included");
        ok(!MovementPolicy.within(0, 0, 30, 40, 49), "boundary rejects");
        ok(MovementPolicy.within(-25000000, 20000000, -25000020, 20000010, 24), "large world coords");
        ok(!MovementPolicy.within(-20000000, 20000000, 20000000, -20000000, 128), "overflow safety");
        for (int i = -100; i < 100; i++) {
            int count = 0;
            for (int t = 0; t < 80; t++) if (MovementPolicy.due(t, i, 80)) count++;
            ok(count == 1, "exactly one decision in eighty ticks");
            ok(Math.abs(MovementPolicy.dailyShift(i)) <= 400, "bounded personalized shift");
        }
        System.out.println("MovementPolicySpec: " + checked + " assertions passed");
    }
}
