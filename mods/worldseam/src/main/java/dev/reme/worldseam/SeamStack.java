package dev.reme.worldseam;

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
            // Pure JVM validation: JUnit cannot load Minecraft ResourceLocation
            // without the NeoForge launch environment.
            if(id==null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
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
            new Entry("minecraft:overworld",null,null,true,true),
            new Entry("minecraft:the_nether",null,128,true,true),
            new Entry("minecraft:the_end",null,null,true,true)
        ),false);
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
        if(loop && entries.size()>1)
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

}
