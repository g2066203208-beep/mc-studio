package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SeamStackTest {
    @Test void vanillaDefaultIsEndOverworldNether() {
        SeamStack s=SeamStack.defaults();
        assertEquals(List.of("minecraft:the_end","minecraft:overworld","minecraft:the_nether"),
            s.entries().stream().map(SeamStack.Entry::id).toList());
        assertEquals(4,s.links().size());
        assertEquals(128,s.boundary("minecraft:the_nether",SeamGraph.EdgeSide.TOP,0,256));
        assertEquals(-64,s.boundary("minecraft:overworld",SeamGraph.EdgeSide.BOTTOM,-64,320));
    }
    @Test void customModdedOrderAndHeights() {
        SeamStack stack=new SeamStack(List.of(
            new SeamStack.Entry("modded:skylands",20,450,true,true),
            new SeamStack.Entry("minecraft:overworld",-64,300,true,true),
            new SeamStack.Entry("modded:deep_caves",-100,90,true,true)
        ),false);
        assertEquals(4,stack.links().size());
        assertEquals("modded:deep_caves",stack.from("minecraft:overworld").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.BOTTOM).findFirst().orElseThrow().target());
        assertEquals(300,stack.boundary("minecraft:overworld",SeamGraph.EdgeSide.TOP,-64,320));
        assertEquals(90,stack.boundary("modded:deep_caves",SeamGraph.EdgeSide.TOP,-100,128));
    }
    @Test void addRemoveAndReorderAreListDriven() {
        var stack=new SeamStack(List.of(
            new SeamStack.Entry("minecraft:the_nether",null,128,true,true),
            new SeamStack.Entry("minecraft:overworld",null,null,true,true)
        ),false);
        assertEquals("minecraft:overworld",stack.from("minecraft:the_nether").get(0).target());
        assertEquals(2,stack.links().size());
    }
    @Test void rejectsDuplicatesAndBadIds() {
        var e=new SeamStack.Entry("mod:realm",0,100,true,true);
        assertThrows(IllegalArgumentException.class,()->new SeamStack(List.of(e,e),false));
        assertThrows(IllegalArgumentException.class,()->new SeamStack.Entry("bad id",null,null,true,true));
        assertThrows(IllegalArgumentException.class,()->new SeamStack.Entry("mod:realm",100,10,true,true));
    }
}
