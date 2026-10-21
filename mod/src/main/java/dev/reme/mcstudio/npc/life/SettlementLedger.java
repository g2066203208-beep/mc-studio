package dev.reme.mcstudio.npc.life;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-dimension settlement chronicle. Holds no entity references, never
 * teleports/spawns NPCs. An entry is earned only by observing actual residents
 * with distinct homes; reloading the world does not mint a new tribe.
 */
public final class SettlementLedger extends SavedData {
    public static final int MAX_ENTRIES = 128;
    private static final SavedData.Factory<SettlementLedger> FACTORY =
        new SavedData.Factory<>(SettlementLedger::new, SettlementLedger::load);
    public record Chronicle(UUID founder, BlockPos hub, int households, long foundedTick,
                            long lastObservedTick, String culture) {}
    private final LinkedHashMap<UUID, Chronicle> entries = new LinkedHashMap<>();

    public static SettlementLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "reme_npc_hamlets_v1");
    }

    public int knownSettlements() { return entries.size(); }
    public Chronicle get(UUID founder) { return entries.get(founder); }

    /** Stable founder UUID plus immutable first-found time; bounded archive. */
    public void record(UUID founder, BlockPos hub, int households, long tick, String culture) {
        if (households < 3 || founder == null || culture == null
            || culture.length() > 48) return;
        Chronicle old = entries.get(founder);
        if (old != null) {
            if (old.households() != households || tick - old.lastObservedTick() >= 1200) {
                entries.put(founder, new Chronicle(founder, old.hub(),
                    households, old.foundedTick(), tick, old.culture()));
                setDirty();
            }
            return;
        }
        if (entries.size() >= MAX_ENTRIES) {
            Iterator<UUID> iter = entries.keySet().iterator();
            iter.next();
            iter.remove();
        }
        entries.put(founder, new Chronicle(founder, hub.immutable(),
            households, tick, tick, culture));
        setDirty();
    }

    public static SettlementLedger load(CompoundTag tag, HolderLookup.Provider lookup) {
        SettlementLedger result = new SettlementLedger();
        ListTag list = tag.getList("Hamlets", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), MAX_ENTRIES); i++) {
            CompoundTag e = list.getCompound(i);
            if (!e.hasUUID("Founder")) continue;
            UUID founder = e.getUUID("Founder");
            result.entries.put(founder,
                new Chronicle(founder, BlockPos.of(e.getLong("Hub")),
                    Math.clamp(e.getInt("Households"), 0, 100),
                    Math.max(0L, e.getLong("Founded")),
                    Math.max(0L, e.getLong("Seen")), e.getString("Culture")));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (Chronicle c : entries.values()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Founder", c.founder());
            e.putLong("Hub", c.hub().asLong());
            e.putInt("Households", c.households());
            e.putLong("Founded", c.foundedTick());
            e.putLong("Seen", c.lastObservedTick());
            e.putString("Culture", c.culture());
            list.add(e);
        }
        tag.put("Hamlets", list);
        return tag;
    }
}
