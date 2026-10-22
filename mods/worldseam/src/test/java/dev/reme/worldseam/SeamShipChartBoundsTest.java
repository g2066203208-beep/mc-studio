package dev.reme.worldseam;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamShipChartBoundsTest {
    @Test void largeAirshipRetainsOneWorldSpaceBodyAndClippedTwoChartHulls() {
        var down=SeamStack.defaults().from("minecraft:overworld").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.BOTTOM)
            .findFirst().orElseThrow();
        // A 64-wide aircraft whose physics origin remains in Overworld.
        var oneBody=new AABB(-32,-70,-8,32,-57,8);
        var p=SeamShipChartBounds.project(oneBody,down,-64,128);
        assertTrue(p.straddles());
        assertEquals(-64,p.homePart().minY,1.e-9);
        assertEquals(-57,p.homePart().maxY,1.e-9);
        assertEquals(122,p.neighboringPart().minY,1.e-9);
        assertEquals(128,p.neighboringPart().maxY,1.e-9);
        assertEquals(-32,p.neighboringPart().minX,1.e-9);
        assertEquals(32,p.neighboringPart().maxX,1.e-9);
        assertEquals(192,p.translationY(),1.e-9);
    }

    @Test void crossingIsSymmetricForHomeAndDestinationChart() {
        var down=SeamStack.defaults().from("minecraft:overworld").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.BOTTOM).findFirst().orElseThrow();
        var up=SeamStack.defaults().from("minecraft:the_nether").stream()
            .filter(l->l.exit()==SeamGraph.EdgeSide.TOP).findFirst().orElseThrow();
        var body=new AABB(10,-67,10,18,-59,18);
        var d=SeamShipChartBounds.project(body,down,-64,128);
        var reverse=SeamShipChartBounds.project(body.move(0,192,0),up,128,-64);
        assertTrue(d.straddles());
        assertTrue(reverse.straddles());
        assertEquals(d.homePart(),reverse.neighboringPart());
        assertEquals(d.neighboringPart(),reverse.homePart());
    }

    @Test void wholeShipCannotBeFalselyReportedAsStraddling() {
        var link=SeamStack.defaults().from("minecraft:overworld").get(0);
        var near=new AABB(0,-60,0,2,-56,2);
        var p=SeamShipChartBounds.project(near,link,-64,128);
        assertFalse(p.straddles());
        assertNotNull(p.homePart());
        assertNull(p.neighboringPart());
    }

    @Test void customHeightSplitAndOverflowValidation() {
        var link=SeamStack.defaults().from("minecraft:overworld").get(0);
        var p=SeamShipChartBounds.project(
            new AABB(-1,32,-1,1,43,1),link,37,209);
        assertTrue(p.straddles());
        assertEquals(32,p.homePart().minY);
        assertEquals(37,p.homePart().maxY);
        assertEquals(209,p.neighboringPart().minY);
        assertEquals(215,p.neighboringPart().maxY);
        assertThrows(IllegalArgumentException.class,()->
            SeamShipChartBounds.project(new AABB(0,0,0,1,1,1),
                link,Double.NaN,209));
    }
}
