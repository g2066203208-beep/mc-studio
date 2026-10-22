package dev.reme.npcstudio.npc.life;

/**
 * Deterministic, dependency-free planning kernel for a simulated resident.
 *
 * <p>Game integration must execute the returned intent through server-authoritative,
 * validated actions and must record the outcome. Planning alone is not work.</p>
 */
public final class LivingPlanner {
    public enum Intent {
        SLEEP, EAT, WORK, SOCIALIZE, SEEK_SHELTER, ESCAPE, IDLE
    }

    /**
     * In Minecraft dayTime 0 means 06:00; 6000 means noon; 12000 means 18:00.
     * Need meters are 0 (critical) to 100 (satisfied).
     */
    public record Context(
        long dayTime, boolean raining, boolean exposedToWeather,
        boolean visibleThreat, int hunger, int energy, int socialNeed,
        boolean hasJobAndWorkplace
    ) {
        public Context {
            if (hunger < 0 || hunger > 100 || energy < 0 || energy > 100 ||
                socialNeed < 0 || socialNeed > 100) {
                throw new IllegalArgumentException("Need meters must be 0..100");
            }
        }
    }

    public record Personality(int diligence, int sociability, int caution) {
        public Personality {
            if (diligence < 0 || diligence > 100 || sociability < 0 ||
                sociability > 100 || caution < 0 || caution > 100) {
                throw new IllegalArgumentException("Traits must be 0..100");
            }
        }
        public static Personality average() { return new Personality(50, 50, 50); }
    }

    public record Decision(Intent intent, int score, String reason) {}

    private final Personality personality;
    private Intent last = Intent.IDLE;

    public LivingPlanner(Personality personality) {
        this.personality = personality;
    }

    public Decision choose(Context c) {
        // Safety always preempts a daily routine. Re-plan after the threat vanishes.
        if (c.visibleThreat()) {
            last = Intent.ESCAPE;
            return new Decision(Intent.ESCAPE, 1000, "visible threat interrupts routine");
        }

        long clock = Math.floorMod(c.dayTime(), 24000L);
        boolean asleepHours = clock >= 12500L;
        boolean workHours = clock >= 2000L && clock < 9000L;
        boolean leisureHours = clock >= 9000L && clock < 12500L;

        int rest = (asleepHours ? 68 : 5) + (100 - c.energy()) / 2;
        // Minecraft time 0 = 06:00, 6000 = midday, 12000 = dusk.
        // Breakfast/lunch/supper are meaningful routine windows but NEVER
        // compel a full NPC to eat (and never override imminent danger).
        boolean mealtime = (clock >= 900 && clock < 2100)
            || (clock >= 5200 && clock < 6500)
            || (clock >= 11000 && clock < 12200);
        int food = 6 + (100 - c.hunger()) * 4 / 5
            + (mealtime && c.hunger() < 85 ? 65 : 0);
        int work = c.hasJobAndWorkplace() && workHours
            ? 48 + personality.diligence() / 6 : -100;
        int talk = (leisureHours ? 36 : 2) + (100 - c.socialNeed()) / 2
            + personality.sociability() / 10;
        int shelter = c.raining() && c.exposedToWeather()
            ? 75 + personality.caution() / 6 : -100;

        Intent[] options = {
            Intent.SLEEP, Intent.EAT, Intent.WORK,
            Intent.SOCIALIZE, Intent.SEEK_SHELTER, Intent.IDLE
        };
        int[] values = {rest, food, work, talk, shelter, 3};
        String[] reasons = {
            asleepHours ? "night routine or fatigue" : "fatigue",
            mealtime ? "breakfast/lunch/supper or hunger" : "hungry",
            "work shift", "social need or leisure time",
            "rain and exposure", "no urgent need"
        };

        // Hysteresis prevents rapidly toggling between nearly equivalent tasks.
        // Critical survival choices ignore inertia.
        int bestIdx = 0;
        int best = Integer.MIN_VALUE;
        for (int i = 0; i < options.length; i++) {
            int bonus = options[i] == last && c.hunger() > 20 && c.energy() > 15
                ? 8 : 0;
            int value = values[i] + bonus;
            if (value > best) {
                best = value;
                bestIdx = i;
            }
        }
        last = options[bestIdx];
        return new Decision(last, best, reasons[bestIdx]);
    }

    public Intent previousIntent() { return last; }
}
