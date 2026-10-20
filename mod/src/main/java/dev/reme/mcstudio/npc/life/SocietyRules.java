package dev.reme.mcstudio.npc.life;

/**
 * Stable, data-oriented social identity. These are meaningful allegiances rather
 * than just tags: hostile nations/factions cannot socialize or share supplies.
 * The preset relations are provisional; a SavedData diplomacy table is future work.
 */
public final class SocietyRules {
    public enum Nation { COMMONWEALTH, FRONTIER, OUTLANDS }
    public enum Faction { SETTLERS, MERCHANTS, RANGERS, RAIDERS }

    public record Identity(Nation nation, Faction faction, String region) {
        public Identity {
            if (nation == null || faction == null || region == null
                    || region.length() > 32 || !region.matches("[a-z0-9_-]+")) {
                throw new IllegalArgumentException("Invalid resident affiliation");
            }
        }
    }

    public static Identity civilian(String region) {
        return new Identity(Nation.COMMONWEALTH, Faction.SETTLERS, region);
    }

    public static int affinity(Identity a, Identity b) {
        if (a.equals(b)) return 100;
        if (a.faction() == Faction.RAIDERS || b.faction() == Faction.RAIDERS) {
            return a.faction() == b.faction() ? 65 : -95;
        }
        int score = a.nation() == b.nation() ? 35 : -10;
        if (a.faction() == b.faction()) score += 30;
        if (a.region().equals(b.region())) score += 15;
        if (a.faction() == Faction.MERCHANTS || b.faction() == Faction.MERCHANTS) score += 12;
        if (a.faction() == Faction.RANGERS || b.faction() == Faction.RANGERS) score += 10;
        return Math.clamp(score, -100, 100);
    }

    public static boolean hostile(Identity a, Identity b) {
        return affinity(a, b) <= -50;
    }

    private SocietyRules() {}
}
