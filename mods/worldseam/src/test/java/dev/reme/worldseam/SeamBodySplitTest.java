package dev.reme.worldseam;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamBodySplitTest {
    @Test void fallingPlayerPartlyInTwoRealWorlds() {
        // Feet stand 1/8 block below the Nether logical roof Y=128.
        // Head is still in the Overworld above its floor Y=-64.
        var body=new AABB(-0.3,127.875,-0.3,0.3,129.675,0.3);
        var s=SeamBodySplit.atTargetBoundary(body,
            SeamGraph.EdgeSide.TOP,128,-64);
        assertNotNull(s.targetPart());
        assertNotNull(s.sourcePart());
        assertEquals(127.875,s.targetPart().minY,1e-8);
        assertEquals(128,s.targetPart().maxY,1e-8);
        assertEquals(-64,s.sourcePart().minY,1e-8);
        assertEquals(-62.325,s.sourcePart().maxY,1e-8);
        assertEquals(body.maxX,s.sourcePart().maxX,1e-8);
    }

    @Test void risingPlayerClipIsTheExactInverseHalfspace() {
        // OW has bottom -64. Player's lower leg is still in Nether roof.
        var body=new AABB(-.3,-64.5,-.3,.3,-62.7,.3);
        var s=SeamBodySplit.atTargetBoundary(body,
            SeamGraph.EdgeSide.BOTTOM,-64,128);
        assertEquals(-64,s.targetPart().minY,1e-8);
        assertEquals(-62.7,s.targetPart().maxY,1e-8);
        assertEquals(127.5,s.sourcePart().minY,1e-8);
        assertEquals(128,s.sourcePart().maxY,1e-8);
    }

    @Test void noPhantomSourcePartWhenFullyInTarget() {
        var s=SeamBodySplit.atTargetBoundary(
            new AABB(0,-63.8,0,1,-62,1),
            SeamGraph.EdgeSide.BOTTOM,-64,128);
        assertNotNull(s.targetPart());
        assertNull(s.sourcePart());
    }

    @Test void oneBodyCanBeEntirelyAcrossTheContactPlane() {
        var s=SeamBodySplit.atTargetBoundary(
            new AABB(0,128.5,0,1,129.5,1),
            SeamGraph.EdgeSide.TOP,128,-64);
        assertNull(s.targetPart());
        assertEquals(-63.5,s.sourcePart().minY,1e-8);
        assertEquals(-62.5,s.sourcePart().maxY,1e-8);
    }

    @Test void customWorldHeightAndPlanePreserveExactDistances() {
        var body=new AABB(-11,204,-16,-10,211,-15);
        var s=SeamBodySplit.atTargetBoundary(
            body,SeamGraph.EdgeSide.TOP,209,37);
        assertEquals(204,s.targetPart().minY);
        assertEquals(209,s.targetPart().maxY);
        assertEquals(37,s.sourcePart().minY);
        assertEquals(39,s.sourcePart().maxY);
    }

    @Test void invalidPlanesNeverReturnFakeCollisionFreeVolumes() {
        var box=new AABB(0,0,0,1,1,1);
        assertThrows(IllegalArgumentException.class,()->
            SeamBodySplit.atTargetBoundary(box,SeamGraph.EdgeSide.TOP,Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->
            SeamBodySplit.atTargetBoundary(box,SeamGraph.EdgeSide.TOP,0,Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->
            SeamBodySplit.atTargetBoundary(box,null,0,0));
    }
}
