package dev.reme.npcstudio.npc.life;

import java.util.UUID;

/**
 * A single finite commission owned by a NPC. The Minecraft executor must verify
 * true inventory stock and mutate items before calling finish().
 */
public final class Commission {
    public enum Goods { WHEAT, BREAD, CARROT, POTATO }
    public enum State { OPEN, ACCEPTED, COMPLETED, CANCELLED }
    private final Goods goods;
    private final int quantity;
    private final int emeraldReward;
    private State state = State.OPEN;
    private UUID contractor;

    public Commission(Goods goods, int quantity, int emeraldReward) {
        if (goods == null || quantity < 1 || quantity > 16 ||
            emeraldReward < 1 || emeraldReward > 16)
            throw new IllegalArgumentException("bounded goods and rewards required");
        this.goods = goods;
        this.quantity = quantity;
        this.emeraldReward = emeraldReward;
    }
    public Goods goods() { return goods; }
    public int quantity() { return quantity; }
    public int emeraldReward() { return emeraldReward; }
    public State state() { return state; }
    public UUID contractor() { return contractor; }

    public boolean accept(UUID player) {
        if (state != State.OPEN || player == null) return false;
        contractor = player;
        state = State.ACCEPTED;
        return true;
    }
    public boolean canComplete(UUID player) {
        return state == State.ACCEPTED && contractor.equals(player);
    }
    public boolean finish(UUID player) {
        if (!canComplete(player)) return false;
        state = State.COMPLETED;
        return true;
    }
    public boolean cancel() {
        if (state == State.COMPLETED || state == State.CANCELLED) return false;
        state = State.CANCELLED;
        return true;
    }
    public void restore(State saved, UUID acceptedBy) {
        if (saved == State.ACCEPTED && acceptedBy == null)
            throw new IllegalArgumentException("accepted job needs contractor");
        if ((saved == State.OPEN || saved == State.CANCELLED) && acceptedBy != null)
            throw new IllegalArgumentException("job state mismatch");
        state = saved;
        contractor = acceptedBy;
    }
}
