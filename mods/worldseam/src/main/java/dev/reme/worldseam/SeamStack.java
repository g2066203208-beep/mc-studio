package dev.reme.worldseam;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Versioned per-save dimension stack: TOP -> BOTTOM.
 * Explicit seam Y overrides select connection planes; they DO NOT alter the
 * actual Minecraft DimensionType min_y, height or logical_height.
 */
public record SeamStack(List<Entry> entries, boolean loop) {
    public static final int MAX_ENTRIES = 24;

    public record Entry(String id, Integer bottomY, Integer topY,
                        boolean connectsUp, boolean connectsDown) {
        public Entry {
            ResourceLocation parsed=ResourceLocation.tryParse(id);
            if(parsed==null || !id.equals(parsed.toString()))
                throw new IllegalArgumentException("Invalid dimension ID: "+id);
            if(bottomY!=null && (bottomY < -4096 || bottomY>4096))
                throw new IllegalArgumentException("bottomY outside safety limit");
            if(topY!=null && (topY < -4096 || topY>4096))
                throw new IllegalArgumentException("topY outside safety limit");
            if(bottomY!=null && topY!=null && topY<=bottomY)
                throw new IllegalArgumentException("topY must be greater than bottomY");
        }
    }

    public SeamStack {
        entries=List.copyOf(entries);
        if(entries.isEmpty() || entries.size()>MAX_ENTRIES)
            throw new IllegalArgumentException("Dimension stack must have 1.."+MAX_ENTRIES+" entries");
        Set<String> seen=new HashSet<>();
        for(Entry entry:entries)
            if(!seen.add(entry.id()))throw new IllegalArgumentException(
                "Duplicate dimension in stack: "+entry.id());
        if(loop && entries.size()<2)
            throw new IllegalArgumentException("Loop needs at least two dimensions");
    }

    public static SeamStack defaults() {
        return new SeamStack(List.of(
            new Entry("minecraft:the_end",null,null,true,true),
            new Entry("minecraft:overworld",null,null,true,true),
            new Entry("minecraft:the_nether",null,128,true,true)
        ),false);
    }

    /** All actual target worlds must exist before enabling the stack. */
    public void validate(MinecraftServer server) {
        for(Entry e:entries) {
            ServerLevel level=server.getLevel(
                net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(e.id())));
            if(level==null)throw new IllegalArgumentException(
                "Missing dimension in this save (check mod/datapack): "+e.id());
            int min=level.getMinBuildHeight(), max=level.getMaxBuildHeight();
            int low=e.bottomY()!=null?e.bottomY():min;
            int top=e.topY()!=null?e.topY():max;
            if(low<min||top>max||low>=top)
                throw new IllegalArgumentException(e.id()+" seam heights "+
                    low+".."+top+" outside build range ["+min+","+max+")");
        }
    }

    public int edge(Entry e,SeamGraph.EdgeSide side,int min,int max) {
        if(side==SeamGraph.EdgeSide.BOTTOM)
            return e.bottomY()==null?min:e.bottomY();
        return e.topY()==null?max:e.topY();
    }

    public List<SeamGraph.Link> links() {
        var links=new ArrayList<SeamGraph.Link>();
        for(int i=0;i<entries.size()-1;i++)
            connect(links,entries.get(i),entries.get(i+1));
        if(loop && entries.size()>2)
            connect(links,entries.get(entries.size()-1),entries.get(0));
        return List.copyOf(links);
    }

    private static void connect(List<SeamGraph.Link> links,Entry upper,Entry lower) {
        if(upper.connectsDown())
            links.add(new SeamGraph.Link(upper.id(),SeamGraph.EdgeSide.BOTTOM,
                lower.id(),SeamGraph.EdgeSide.TOP));
        if(lower.connectsUp())
            links.add(new SeamGraph.Link(lower.id(),SeamGraph.EdgeSide.TOP,
                upper.id(),SeamGraph.EdgeSide.BOTTOM));
    }

    public Entry find(String id) {
        for(Entry e:entries)if(e.id().equals(id))return e;
        return null;
    }
    public int boundary(String id,SeamGraph.EdgeSide side,int min,int max) {
        Entry e=find(id);
        if(e==null)return side==SeamGraph.EdgeSide.BOTTOM?min:max;
        return edge(e,side,min,max);
    }
    public List<SeamGraph.Link> from(String id) {
        return links().stream().filter(link->link.source().equals(id)).toList();
    }

    public CompoundTag save() {
        CompoundTag tag=new CompoundTag();
        tag.putInt("schema",1);
        tag.putBoolean("loop",loop);
        ListTag list=new ListTag();
        for(Entry entry:entries){
            CompoundTag e=new CompoundTag();
            e.putString("id",entry.id());
            e.putBoolean("up",entry.connectsUp());
            e.putBoolean("down",entry.connectsDown());
            if(entry.bottomY()!=null)e.putInt("bottomY",entry.bottomY());
            if(entry.topY()!=null)e.putInt("topY",entry.topY());
            list.add(e);
        }
        tag.put("entries",list);
        return tag;
    }

    public static SeamStack read(CompoundTag tag) {
        List<Entry> entries=new ArrayList<>();
        ListTag list=tag.getList("entries",Tag.TAG_COMPOUND);
        if(list.isEmpty())throw new IllegalArgumentException("Empty saved dimension stack");
        for(int i=0;i<list.size();i++){
            CompoundTag e=list.getCompound(i);
            entries.add(new Entry(e.getString("id"),
                e.contains("bottomY",Tag.TAG_INT)?e.getInt("bottomY"):null,
                e.contains("topY",Tag.TAG_INT)?e.getInt("topY"):null,
                e.getBoolean("up"),e.getBoolean("down")));
        }
        return new SeamStack(entries,tag.getBoolean("loop"));
    }
}
