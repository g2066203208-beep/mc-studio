package dev.reme.shiprepair;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sable plot actor for blueprint recording + staggered restoration.
 * All block locations are RELATIVE to the station's PLOT coordinates, not the
 * moving/rotating global ship position. Ship UUID must match on EVERY tick.
 *
 * v0.1 records/restores blockstates (including orientation) but NEVER clones
 * inventories or modded block-entity NBT: old inventories cannot be duped.
 */
public final class RepairStationBlockEntity extends BlockEntity implements BlockEntitySubLevelActor {
    private static final int SAVE_VERSION=1;
    private UUID owner,shipUUID;
    private Map<Long,BlockState> saved=Map.of();
    private long capturedAt;
    private Job job;

    private enum Phase { CAPTURE, SCAN, APPLY }
    private static final class Job {
        final UUID shipId,requester;
        final List<LevelChunk> chunks;
        final Map<Long,BlockState> seen=new HashMap<>();
        List<SnapshotDiff.Edit<BlockState>> edits=List.of();
        Phase phase;
        int scanned,applied,protectedSkipped;
        Job(UUID shipId,UUID requester,List<LevelChunk> chunks,Phase phase) {
            this.shipId=shipId;
            this.requester=requester;
            this.chunks=chunks;
            this.phase=phase;
        }
    }

    public RepairStationBlockEntity(BlockPos pos,BlockState state) {
        super(ShipRepairMod.STATION_ENTITY.get(),pos,state);
    }

    /** Read-only test diagnostics; only a COMPLETE scan becomes a saved blueprint. */
    int savedBlockCount() { return saved.size(); }
    boolean busy() { return job != null; }


    /** First click saves. Next click restores. Shift-click overwrites blueprint. */
    public void click(Player player,boolean overwrite) {
        if(!(level instanceof ServerLevel server)) return;
        if(player.isSpectator() || !player.getAbilities().mayBuild) {
            tell(player,"No build permission");
            return;
        }
        if(owner!=null && !owner.equals(player.getUUID())
                && !(player instanceof net.minecraft.server.level.ServerPlayer admin
                     && admin.hasPermissions(2))) {
            tell(player,"Only the ship blueprint owner or an operator may repair");
            return;
        }
        if(job!=null) { tell(player,"Station is already working"); return; }
        ShipPlot.Attached sub=ShipPlot.locate(server,worldPosition);
        if(sub==null) {
            tell(player,"This station must be mounted on a loaded Sable physical ship");
            return;
        }
        if(!saved.isEmpty() && !overwrite && !sub.id().equals(shipUUID)) {
            tell(player,"Ship UUID changed: refused to modify a different structure");
            return;
        }
        Phase phase=saved.isEmpty() || overwrite ? Phase.CAPTURE:Phase.SCAN;
        job=new Job(sub.id(),player.getUUID(),sub.chunks(),phase);
        tell(player,phase==Phase.CAPTURE
                ? "Capturing entire Sable plot over several ticks; do not edit ship yet"
                : "Scanning current ship blocks to calculate repair differences");
    }

    @Override
    public void sable$tick(ServerSubLevel active) {
        if(job==null || !(level instanceof ServerLevel server)) return;
        ShipPlot.Attached ship=ShipPlot.locate(server,worldPosition);
        if(ship==null || !ship.id().equals(job.shipId)
                || !active.getUniqueId().equals(job.shipId)
                || server.getBlockEntity(worldPosition)!=this) {
            finish(server,false,"Sublevel identity or load changed; stopping repair");
            return;
        }
        if(job.phase==Phase.CAPTURE || job.phase==Phase.SCAN) {
            int budget=ShipPlot.SCAN_CHUNKS_PER_TICK;
            while(budget-->0 && job.scanned<job.chunks.size()) {
                LevelChunk chunk=job.chunks.get(job.scanned++);
                if(!scan(chunk,job.seen)) {
                    finish(server,false,"Ship exceeds "+ShipPlot.MAX_BLOCKS+" non-air blocks; blueprint unchanged");
                    return;
                }
            }
            if(job.scanned<job.chunks.size()) return;
            if(job.phase==Phase.CAPTURE) {
                if(job.seen.isEmpty() || !job.seen.containsKey(BlockPos.ZERO.asLong())) {
                    finish(server,false,"Repair station was not present in ship plot");
                    return;
                }
                saved=Map.copyOf(job.seen);
                shipUUID=job.shipId;
                capturedAt=server.getGameTime();
                if(owner==null) owner=job.requester;
                setChanged();
                finish(server,true,"Saved "+saved.size()+" blocks from "+job.chunks.size()+" ship chunks");
                return;
            }
            // Do not create chunks in arbitrary world terrain or another ship.
            var loadedChunks=new HashSet<Long>();
            for(LevelChunk c:ship.chunks()) loadedChunks.add(c.getPos().toLong());
            for(long key:saved.keySet()) {
                BlockPos pos=absolute(key);
                if(!loadedChunks.contains(new ChunkPos(pos).toLong())) {
                    finish(server,false,"Blueprint chunk unloaded or split away; repair refused");
                    return;
                }
            }
            job.edits=SnapshotDiff.plan(saved,job.seen);
            if(job.edits.isEmpty()) {
                finish(server,true,"Ship already matches saved state");
                return;
            }
            job.phase=Phase.APPLY;
            message(server,"Found "+job.edits.size()+" changes; repairing at most "
                    +ShipPlot.REPAIR_BLOCKS_PER_TICK+" blocks/tick");
        }
        if(job==null || job.phase!=Phase.APPLY) return;
        int budget=ShipPlot.REPAIR_BLOCKS_PER_TICK;
        while(budget-->0 && job.applied<job.edits.size()) {
            var edit=job.edits.get(job.applied++);
            BlockPos pos=absolute(edit.offset());
            if(!server.hasChunk(pos.getX()>>4,pos.getZ()>>4)) {
                finish(server,false,"Ship chunk unloaded during repair; stopped");
                return;
            }
            if(!server.getBlockState(worldPosition).is(ShipRepairMod.STATION.get())) {
                finish(server,false,"Repair station missing; stopped");
                return;
            }
            BlockState actual=server.getBlockState(pos);
            BlockState expected=edit.after();
            if(expected==null) {
                // User-created cargo/machines must not silently lose items.
                if(actual.isAir()) continue;
                if(actual.is(ShipRepairMod.STATION.get())
                        || server.getBlockEntity(pos)!=null) {
                    job.protectedSkipped++;
                    continue;
                }
                server.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            } else if(!actual.equals(expected)) {
                if(server.getBlockEntity(pos)!=null && actual.getBlock()!=expected.getBlock()) {
                    job.protectedSkipped++;
                    continue;
                }
                server.setBlock(pos,expected,3);
            }
        }
        if(job.applied>=job.edits.size()) {
            finish(server,true,"Repair finished: "+job.edits.size()
                    +" planned edits, "+job.protectedSkipped+" protected locations skipped");
        }
    }

    private boolean scan(LevelChunk chunk,Map<Long,BlockState> output) {
        LevelChunkSection[] sections=chunk.getSections();
        for(int i=0;i<sections.length;i++) {
            LevelChunkSection section=sections[i];
            if(section==null || section.hasOnlyAir()) continue;
            int sy=chunk.getSectionYFromSectionIndex(i)<<4;
            int sx=chunk.getPos().getMinBlockX();
            int sz=chunk.getPos().getMinBlockZ();
            for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
                BlockState state=section.getBlockState(x,y,z);
                if(state.isAir()) continue;
                long offset=BlockPos.asLong(sx+x-worldPosition.getX(),
                        sy+y-worldPosition.getY(),sz+z-worldPosition.getZ());
                output.put(offset,state);
                if(output.size()>ShipPlot.MAX_BLOCKS) return false;
            }
        }
        return true;
    }

    private BlockPos absolute(long relative) {
        return BlockPos.of(relative).offset(worldPosition);
    }

    private void message(ServerLevel server,String text) {
        if(job==null) return;
        for(var p:server.players()) if(p.getUUID().equals(job.requester)) {
            tell(p,text);
            return;
        }
    }
    private void finish(ServerLevel server,boolean ok,String text) {
        message(server,(ok?"§a":"§c")+text);
        job=null;
    }
    private static void tell(Player p,String text) {
        p.displayClientMessage(Component.literal("[ShipRepair] "+text),false);
    }

    @Override
    protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        tag.putInt("BlueprintVersion",SAVE_VERSION);
        if(owner!=null) tag.putUUID("Owner",owner);
        if(shipUUID!=null) tag.putUUID("ShipId",shipUUID);
        tag.putLong("CapturedAt",capturedAt);
        if(saved.isEmpty()) return;
        var palette=new ArrayList<BlockState>();
        var indexes=new LinkedHashMap<BlockState,Integer>();
        var list=saved.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        long[] coords=new long[list.size()];
        int[] states=new int[list.size()];
        for(int i=0;i<list.size();i++) {
            var e=list.get(i);
            coords[i]=e.getKey();
            states[i]=indexes.computeIfAbsent(e.getValue(),key->{
                palette.add(key);
                return palette.size()-1;
            });
        }
        ListTag tags=new ListTag();
        for(BlockState s:palette) tags.add(NbtUtils.writeBlockState(s));
        tag.put("Palette",tags);
        tag.putLongArray("Offsets",coords);
        tag.putIntArray("PaletteIndices",states);
    }

    @Override
    protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;
        shipUUID=tag.hasUUID("ShipId")?tag.getUUID("ShipId"):null;
        capturedAt=tag.getLong("CapturedAt");
        saved=Map.of();
        job=null; // interrupted jobs must never continue after restart
        if(tag.getInt("BlueprintVersion")!=SAVE_VERSION
                || !tag.contains("Palette",Tag.TAG_LIST)) return;
        long[] coords=tag.getLongArray("Offsets");
        int[] ids=tag.getIntArray("PaletteIndices");
        if(coords.length!=ids.length || coords.length>ShipPlot.MAX_BLOCKS) return;
        ListTag tags=tag.getList("Palette",Tag.TAG_COMPOUND);
        var palette=new ArrayList<BlockState>();
        for(int i=0;i<tags.size();i++) {
            CompoundTag state=tags.getCompound(i);
            ResourceLocation name=ResourceLocation.tryParse(state.getString("Name"));
            if(name==null || !BuiltInRegistries.BLOCK.containsKey(name)) return;
            palette.add(NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK),state));
        }
        var reconstructed=new HashMap<Long,BlockState>();
        for(int i=0;i<coords.length;i++) {
            if(ids[i]<0 || ids[i]>=palette.size()) return;
            reconstructed.put(coords[i],palette.get(ids[i]));
        }
        saved=Map.copyOf(reconstructed);
    }
}
