package dev.reme.mcstudio.npc.life;

/** Persisted long-range purpose. Only successful in-world acts advance it. */
public final class LifeJourney {
    public enum Goal {
        FEED_COMMUNITY, ESTABLISH_TRADE, EXPLORE_LANDS,
        DEFEND_SETTLEMENT, BUILD_A_HOME, LIVE_PEACEFULLY
    }
    public enum Achievement { HARVEST, BARTER, VISIT_NEW_CHUNK, DEFEND, BUILD, SOCIALIZE }

    private Goal goal;
    private int progress;
    public LifeJourney(Goal goal) { this.goal = goal; }
    public Goal goal() { return goal; }
    public int progress() { return progress; }
    public void setGoal(Goal next) { goal = next; progress = 0; }
    public void restore(Goal next, int amount) {
        goal = next;
        progress = Math.clamp(amount, 0, 100);
    }
    public boolean succeeded(Achievement action) {
        int add = switch (goal) {
            case FEED_COMMUNITY -> action == Achievement.HARVEST ? 4 : 0;
            case ESTABLISH_TRADE -> action == Achievement.BARTER ? 10 : 0;
            case EXPLORE_LANDS -> action == Achievement.VISIT_NEW_CHUNK ? 8 : 0;
            case DEFEND_SETTLEMENT -> action == Achievement.DEFEND ? 14 : 0;
            case BUILD_A_HOME -> action == Achievement.BUILD ? 6 : 0;
            case LIVE_PEACEFULLY -> action == Achievement.SOCIALIZE ? 2 : 0;
        };
        if (add == 0 || progress == 100) return false;
        progress = Math.min(100, progress + add);
        return true;
    }
}
