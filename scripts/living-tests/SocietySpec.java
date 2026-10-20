package dev.reme.mcstudio.npc.life;

public final class SocietySpec {
    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        var settler = SocietyRules.civilian("river");
        var merchant = new SocietyRules.Identity(SocietyRules.Nation.COMMONWEALTH,
            SocietyRules.Faction.MERCHANTS, "river");
        var raider = new SocietyRules.Identity(SocietyRules.Nation.OUTLANDS,
            SocietyRules.Faction.RAIDERS, "wastes");
        check(SocietyRules.affinity(settler, merchant) > 0, "same region trade friendly");
        check(SocietyRules.hostile(settler, raider), "raiders hostile to village");
        check(!SocietyRules.hostile(settler, merchant), "noncombatants friendly");
        check(SocietyRules.affinity(settler, settler) == 100, "same identity");
        var goal = new LifeJourney(LifeJourney.Goal.FEED_COMMUNITY);
        check(!goal.succeeded(LifeJourney.Achievement.BARTER), "wrong event gives zero");
        check(goal.succeeded(LifeJourney.Achievement.HARVEST), "genuine harvest advances goal");
        check(goal.progress() == 4, "increment correct");
        goal.restore(LifeJourney.Goal.DEFEND_SETTLEMENT, 99);
        check(goal.succeeded(LifeJourney.Achievement.DEFEND) && goal.progress() == 100, "goal capped");
        check(!goal.succeeded(LifeJourney.Achievement.DEFEND), "completed goal not re-awarded");
        try {
            SocietyRules.civilian("../../saves");
            throw new AssertionError("invalid region permitted");
        } catch (IllegalArgumentException expected) { /* correct */ }
        System.out.println("SocietySpec 10 assertions passed");
    }
}
