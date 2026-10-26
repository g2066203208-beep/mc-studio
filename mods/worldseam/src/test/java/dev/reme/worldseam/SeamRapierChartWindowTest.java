package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamRapierChartWindowTest {
    @Test void preservesHullInsideRealTranslatedNetherChart() {
        assertTrue(SeamRapierChartWindow.intersects(-70,-60,-64,0,128,32));
        assertTrue(SeamRapierChartWindow.intersects(-135,-130,-64,0,128,32));
        assertTrue(SeamRapierChartWindow.intersects(-193,-191,-64,0,128,32));
        assertFalse(SeamRapierChartWindow.intersects(-200,-193,-64,0,128,32));
        assertFalse(SeamRapierChartWindow.intersects(-20,-18,-64,0,128,32));
    }

    @Test void followsShipDownWithoutLeavingTargetBuildHeight() {
        assertEquals(new SeamRapierChartWindow.Vertical(4,7),
            SeamRapierChartWindow.select(Double.NaN,-192,0,7,4));
        assertEquals(new SeamRapierChartWindow.Vertical(4,7),
            SeamRapierChartWindow.select(-84,-192,0,7,4));
        assertEquals(new SeamRapierChartWindow.Vertical(2,5),
            SeamRapierChartWindow.select(-130,-192,0,7,4));
        assertEquals(new SeamRapierChartWindow.Vertical(0,3),
            SeamRapierChartWindow.select(-188,-192,0,7,4));
        assertEquals(new SeamRapierChartWindow.Vertical(3,4),
            SeamRapierChartWindow.select(Double.NaN,0,3,4,4));
    }

    @Test void realNativeTerrainUnderHullUploadsBeforeDistantCorners() {
        var order = SeamRapierChartWindow.prioritize(12,-8,4,7,-82,-192);
        assertEquals(100, order.size());
        assertEquals(100, new java.util.HashSet<>(order).size());
        // Real source hull minY=-82 maps to Nether Y=110, section 6.
        assertEquals(new SeamRapierChartWindow.Section(12,6,-8), order.get(0));
        assertEquals(new SeamRapierChartWindow.Section(12,7,-8), order.get(1));
        assertEquals(new SeamRapierChartWindow.Section(12,5,-8), order.get(2));
        assertEquals(new SeamRapierChartWindow.Section(12,4,-8), order.get(3));
        // No corner section can consume the two-per-tick native JNI budget
        // before the actual central column is scheduled.
        assertTrue(order.subList(0,4).stream().allMatch(
            s -> s.cx()==12 && s.cz()==-8));
        assertTrue(order.indexOf(new SeamRapierChartWindow.Section(12,6,-8))
            < order.indexOf(new SeamRapierChartWindow.Section(10,6,-10)));
    }

    @Test void prioritizesMovingHullAndClampsToActualTargetWindow() {
        var moved = SeamRapierChartWindow.prioritize(-4,3,2,5,-130,-192);
        // -130 - (-192) = Nether Y62 -> section 3.
        assertEquals(new SeamRapierChartWindow.Section(-4,3,3), moved.get(0));
        var noHull = SeamRapierChartWindow.prioritize(0,0,4,7,
            Double.NaN,-192);
        assertEquals(new SeamRapierChartWindow.Section(0,7,0),noHull.get(0));
        var below = SeamRapierChartWindow.prioritize(0,0,4,7,-500,-192);
        assertEquals(new SeamRapierChartWindow.Section(0,4,0),below.get(0));
        assertThrows(IllegalArgumentException.class,
            () -> SeamRapierChartWindow.prioritize(0,0,8,7,0,-192));
    }

    @Test void rejectsInvalidWindows() {
        assertFalse(SeamRapierChartWindow.intersects(Double.NaN,1,-64,0,128,32));
        assertFalse(SeamRapierChartWindow.intersects(2,2,-64,0,128,32));
        assertThrows(IllegalArgumentException.class,
            ()->SeamRapierChartWindow.select(0,0,5,4,4));
    }
}
