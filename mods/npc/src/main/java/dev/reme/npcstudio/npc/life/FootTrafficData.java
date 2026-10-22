package dev.reme.npcstudio.npc.life;

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
 * Per-dimension, world-persistent footprints. Agent travel creates soft route
 * memory even when physical world edits are OFF. It does not grant resources,
 * claim territory, force chunks to load, or scan the entire world.
 */
public final class FootTrafficData extends SavedData {
    public static final int MAX_CELLS = 4096;
    public static final int PATH_THRESHOLD = 7;
    public static final String SAVE_KEY = "reme_npc_foot_traffic_v1";
    private static final SavedData.Factory<FootTrafficData> FACTORY =
        new SavedData.Factory<>(FootTrafficData::new, FootTrafficData::load);

    private static final class Cell {
        int visits;
        UUID lastWalker;
        long lastVisitTick;
        boolean shaped;
        Cell(int visits, UUID lastWalker, long lastVisitTick, boolean shaped) {
            this.visits = visits;
            this.lastWalker = lastWalker;
            this.lastVisitTick = lastVisitTick;
            this.shaped = shaped;
        }
    }

    // Insertion-order eviction, not an unbounded heatmap; bounded serialization
    // even after months of play and billions of attempted NPC ticks.
    private final LinkedHashMap<Long, Cell> cells = new LinkedHashMap<>();
    private boolean physicalPathsEnabled = false;

    public static FootTrafficData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, SAVE_KEY);
    }

    public boolean physicalPathsEnabled() { return physicalPathsEnabled; }
    public void setPhysicalPathsEnabled(boolean next) {
        if (physicalPathsEnabled != next) {
            physicalPathsEnabled = next;
            setDirty();
        }
    }
    public int trackedCells() { return cells.size(); }
    public int visits(BlockPos footPosition) {
        Cell cell = cells.get(footPosition.asLong());
        return cell == null ? 0 : cell.visits;
    }
    public boolean alreadyShaped(BlockPos footPosition) {
        Cell cell = cells.get(footPosition.asLong());
        return cell != null && cell.shaped;
    }
    public void markShaped(BlockPos footPosition) {
        Cell cell = cells.get(footPosition.asLong());
        if (cell != null && !cell.shaped) {
            cell.shaped = true;
            setDirty();
        }
    }

    /** One counted passage by a distinct walker, or by a returning walker
     * after 200 world ticks. Repeated standing/jitter never builds a road.
     * Caller must establish real movement and a safe loaded natural tile.
     */
    public boolean countPassage(UUID walker, BlockPos footPosition, long tick) {
        long key = footPosition.asLong();
        Cell cell = cells.get(key);
        if (cell != null) {
            if (cell.lastWalker.equals(walker) && tick - cell.lastVisitTick < 200L)
                return false;
            if (tick <= cell.lastVisitTick) return false;
            cell.lastWalker = walker;
            cell.lastVisitTick = tick;
            if (cell.visits < 1000) cell.visits++;
        } else {
            if (cells.size() >= MAX_CELLS) {
                Iterator<Long> iter = cells.keySet().iterator();
                iter.next();
                iter.remove();
            }
            cells.put(key, new Cell(1, walker, tick, false));
        }
        setDirty();
        return true;
    }

    public static FootTrafficData load(CompoundTag tag, HolderLookup.Provider registry) {
        FootTrafficData data = new FootTrafficData();
        data.physicalPathsEnabled = tag.getBoolean("PhysicalPaths");
        ListTag list = tag.getList("Cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), MAX_CELLS); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Walker")) continue;
            data.cells.put(entry.getLong("Pos"), new Cell(
                Math.clamp(entry.getInt("Count"), 0, 1000),
                entry.getUUID("Walker"), Math.max(0L, entry.getLong("Tick")),
                entry.getBoolean("Shaped")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registry) {
        tag.putBoolean("PhysicalPaths", physicalPathsEnabled);
        ListTag list = new ListTag();
        for (Map.Entry<Long, Cell> pair : cells.entrySet()) {
            Cell cell = pair.getValue();
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", pair.getKey());
            entry.putInt("Count", cell.visits);
            entry.putUUID("Walker", cell.lastWalker);
            entry.putLong("Tick", cell.lastVisitTick);
            entry.putBoolean("Shaped", cell.shaped);
            list.add(entry);
        }
        tag.put("Cells", list);
        return tag;
    }
}
