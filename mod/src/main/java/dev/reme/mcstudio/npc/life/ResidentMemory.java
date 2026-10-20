package dev.reme.mcstudio.npc.life;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded individual memory + per-person social trust.
 * No omniscient sharing: each resident only records events explicitly observed.
 * Save/load adapters belong to the Minecraft integration layer.
 */
public final class ResidentMemory {
    public enum Kind { GREETING, GIFT, HELPED, THEFT_WITNESSED, ATTACK_WITNESSED, RUMOR }

    public record Event(long worldTick, UUID actor, Kind kind, String where) {}

    private static final int MAX_EVENTS = 48;
    private final Deque<Event> events = new ArrayDeque<>();
    private final Map<UUID, Integer> trust = new HashMap<>();

    public void witness(long tick, UUID actor, Kind type, String where) {
        if (actor == null || type == null) {
            throw new IllegalArgumentException("Memory needs an actor and event kind");
        }
        if (events.size() >= MAX_EVENTS) events.removeFirst();
        events.addLast(new Event(tick, actor, type, where == null ? "" : where));
        int change = switch (type) {
            case GREETING -> 1;
            case GIFT -> 5;
            case HELPED -> 12;
            case THEFT_WITNESSED -> -25;
            case ATTACK_WITNESSED -> -40;
            case RUMOR -> -2;
        };
        trust.put(actor, Math.max(-100, Math.min(100, trust(actor) + change)));
    }

    public int trust(UUID actor) { return trust.getOrDefault(actor, 0); }
    public List<Event> recentEvents() { return List.copyOf(new ArrayList<>(events)); }
    public Map<UUID, Integer> relationshipSnapshot() { return Map.copyOf(trust); }
}
