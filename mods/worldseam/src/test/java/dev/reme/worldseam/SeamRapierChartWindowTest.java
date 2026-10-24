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

    @Test void rejectsInvalidWindows() {
        assertFalse(SeamRapierChartWindow.intersects(Double.NaN,1,-64,0,128,32));
        assertFalse(SeamRapierChartWindow.intersects(2,2,-64,0,128,32));
        assertThrows(IllegalArgumentException.class,
            ()->SeamRapierChartWindow.select(0,0,5,4,4));
    }
}
