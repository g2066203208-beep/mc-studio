package dev.reme.mcstudio.npc.life;

/** Personality traits that affect gossip, justice and NPC priorities. */
public record CharacterProfile(int honesty, int justice, int curiosity, int empathy) {
    public CharacterProfile {
        if (honesty < 0 || honesty > 100 || justice < 0 || justice > 100 ||
            curiosity < 0 || curiosity > 100 || empathy < 0 || empathy > 100)
            throw new IllegalArgumentException("character traits 0..100 only");
    }

    public static CharacterProfile fromSeed(int n) {
        return new CharacterProfile(35 + Math.floorMod(n * 19, 60),
            30 + Math.floorMod(n * 23, 65), 25 + Math.floorMod(n * 29, 70),
            30 + Math.floorMod(n * 31, 60));
    }
    public boolean sharesRumour() { return honesty >= 35 && empathy >= 35; }
    public boolean investigatesThreat() { return justice >= 65; }
    public boolean interestedInExploration() { return curiosity >= 65; }
}
