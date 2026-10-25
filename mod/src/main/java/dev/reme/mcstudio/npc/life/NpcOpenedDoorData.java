package dev.reme.mcstudio.npc.life;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

/**
 * Per-dimension, bounded persistence for wooden doors ACTUALLY opened by NPCs.
 * Separate SavedData does not alter old resident NBT or player-opened doors.
 * Normal world saves retain watches across restart; power loss before a save
 * can still lose recent state. Never loads chunks or creates chunk tickets.
 */
public final class NpcOpenedDoorData extends SavedData {
    public static final int MAX_TRACKED = 256;
    public static final int CLOSE_DELAY_TICKS = 50;
    public static final String SAVE_KEY = "reme_npc_opened_doors_v1";
    private static final SavedData.Factory<NpcOpenedDoorData> FACTORY =
        new SavedData.Factory<>(NpcOpenedDoorData::new, NpcOpenedDoorData::load);

    private final LinkedHashMap<Long, Long> openedAt = new LinkedHashMap<>();
    private long lastProcessedTick = Long.MIN_VALUE;

    public static NpcOpenedDoorData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, SAVE_KEY);
    }

    public int pendingCount() { return openedAt.size(); }
    public boolean has(BlockPos door) { return openedAt.containsKey(door.asLong()); }
    public boolean canTrack(BlockPos door) {
        return has(door) || openedAt.size() < MAX_TRACKED;
    }

    public boolean remember(BlockPos door, long tick) {
        if (!canTrack(door) || tick < 0) return false;
        openedAt.put(door.asLong(), tick);
        setDirty();
        return true;
    }

    public void relinquish(BlockPos door) {
        if (openedAt.remove(door.asLong()) != null) setDirty();
    }

    /** Run from server-level post tick even after the opening NPC despawns.
     * Never close into living occupants; unloaded doors stay pending.
     */
    public void tickOpenedDoors(ServerLevel level) {
        if (!level.getServer().isSameThread()) return;
        long now = level.getGameTime();
        if (lastProcessedTick == now) return;
        lastProcessedTick = now;
        boolean changed = false;
        Iterator<Map.Entry<Long, Long>> iterator = openedAt.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> pending = iterator.next();
            long age = now - pending.getValue();
            if (age < 0) {
                // A restored world may have an earlier game clock than this
                // SavedData (for example after recovering a level.dat backup).
                // Dropping the watch here strands an NPC-opened door forever.
                // Restart the grace period instead; the normal occupancy,
                // door-integrity and loaded-chunk checks still apply.
                pending.setValue(now);
                changed = true;
                continue;
            }
            if (age < CLOSE_DELAY_TICKS) continue;
            BlockPos door = BlockPos.of(pending.getKey());
            if (!level.hasChunkAt(door) || !level.hasChunkAt(door.above()))
                continue;
            BlockState lower = level.getBlockState(door);
            BlockState upper = level.getBlockState(door.above());
            if (!(lower.getBlock() instanceof DoorBlock)
                || !DoorBlock.isWoodenDoor(lower)
                || upper.getBlock() != lower.getBlock()
                || lower.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                || upper.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
                || lower.getValue(DoorBlock.FACING) != upper.getValue(DoorBlock.FACING)
                || lower.getValue(DoorBlock.HINGE) != upper.getValue(DoorBlock.HINGE)
                || lower.getValue(DoorBlock.OPEN) != upper.getValue(DoorBlock.OPEN)
                || lower.getValue(DoorBlock.POWERED) != upper.getValue(DoorBlock.POWERED)
                || !lower.getValue(DoorBlock.OPEN)
                || lower.getValue(DoorBlock.POWERED)) {
                iterator.remove();
                changed = true;
                continue;
            }
            if (!level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(door).inflate(1.0D, 1.0D, 1.0D),
                    LivingEntity::isAlive).isEmpty()) continue;
            ((DoorBlock) lower.getBlock()).setOpen(null, level, lower, door, false);
            iterator.remove();
            changed = true;
        }
        if (changed) setDirty();
    }

    public static NpcOpenedDoorData load(CompoundTag tag, HolderLookup.Provider lookup) {
        NpcOpenedDoorData data = new NpcOpenedDoorData();
        ListTag list = tag.getList("Doors", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), MAX_TRACKED); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains("Pos", Tag.TAG_LONG)
                || !entry.contains("OpenedAt", Tag.TAG_LONG)) continue;
            long opened = entry.getLong("OpenedAt");
            if (opened < 0) continue;
            data.openedAt.put(entry.getLong("Pos"), opened);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Long> pending : openedAt.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", pending.getKey());
            entry.putLong("OpenedAt", pending.getValue());
            list.add(entry);
        }
        tag.put("Doors", list);
        return tag;
    }
}
