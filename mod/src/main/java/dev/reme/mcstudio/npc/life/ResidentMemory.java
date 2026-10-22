package dev.reme.mcstudio.npc.life;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
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
    private static final int MAX_RELATIONSHIPS = 128;
    private final Deque<Event> events = new ArrayDeque<>();
    private final Map<UUID, Integer> trust = new LinkedHashMap<>();

    public void clear() { events.clear(); trust.clear(); }
    public void restoreEvent(long tick, UUID actor, Kind kind, String where) {
        if (events.size() == MAX_EVENTS) events.removeFirst();
        events.addLast(new Event(tick, actor, kind, where == null ? "" : where));
    }
    public void restoreTrust(UUID actor, int value) {
        storeTrust(actor, value);
    }

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
        storeTrust(actor, trust(actor) + change);
    }

    private void storeTrust(UUID actor, int value) {
        if (actor == null) return;
        if (!trust.containsKey(actor) && trust.size() >= MAX_RELATIONSHIPS) {
            // Evict oldest observed identity, never let a busy town or a
            // player swarm grow resident relationship data without bound.
            UUID oldest = trust.keySet().iterator().next();
            trust.remove(oldest);
        }
        trust.put(actor, Math.clamp(value, -100, 100));
    }

    public int trust(UUID actor) { return trust.getOrDefault(actor, 0); }
    public List<Event> recentEvents() { return List.copyOf(new ArrayList<>(events)); }
    public Map<UUID, Integer> relationshipSnapshot() { return Map.copyOf(trust); }
}
