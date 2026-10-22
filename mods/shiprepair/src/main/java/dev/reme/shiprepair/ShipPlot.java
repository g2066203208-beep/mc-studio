package dev.reme.shiprepair;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** UUID-validated access to the Sable plot, never ordinary-world blocks. */
public final class ShipPlot {
    public static final int MAX_CHUNKS=512,MAX_BLOCKS=250000;
    public static final int SCAN_CHUNKS_PER_TICK=2,REPAIR_BLOCKS_PER_TICK=96;
    private ShipPlot() {}
    public record Attached(ServerSubLevel ship,List<LevelChunk> chunks) {
        public UUID id() { return ship.getUniqueId(); }
    }
    @Nullable public static Attached locate(ServerLevel level,BlockPos at) {
        var c=SubLevelContainer.getContainer(level);
        if(c==null) return null;
        var plot=c.getPlot(new ChunkPos(at));
        if(plot==null || !(plot.getSubLevel() instanceof ServerSubLevel ship)) return null;
        var chunks=new ArrayList<LevelChunk>();
        for(PlotChunkHolder h:plot.getLoadedChunks()) {
            chunks.add(h.getChunk());
            if(chunks.size()>MAX_CHUNKS) return null;
        }
        chunks.sort(Comparator.comparingLong(e->e.getPos().toLong()));
        return new Attached(ship,List.copyOf(chunks));
    }
}
