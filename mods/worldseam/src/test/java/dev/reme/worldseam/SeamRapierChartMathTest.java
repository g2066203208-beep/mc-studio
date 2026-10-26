package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamRapierChartMathTest {
    @Test void netherTopIsDirectlyBelowOverworldBottom() {
        assertEquals(-12,SeamRapierChartMath.sectionShift(-64,128));
        assertEquals(-5,SeamRapierChartMath.mapSection(7,-64,128));
        assertEquals(-6,SeamRapierChartMath.mapSection(6,-64,128));
        assertEquals(7,SeamRapierChartMath.topSection(128));
        assertTrue(SeamRapierChartMath.entirelyBelowSource(-5,-64));
        assertFalse(SeamRapierChartMath.entirelyBelowSource(-4,-64));
    }

    @Test void reverseCoordinateTranslationPreservesNativeBlocks() {
        int reverse=SeamRapierChartMath.sectionShift(128,-64);
        assertEquals(12,reverse);
        assertEquals(7,SeamRapierChartMath.mapSection(-5,128,-64));
        assertEquals(-5,SeamRapierChartMath.mapSection(7,-64,128));
    }

    @Test void rejectUnsupportedChartRoundingAndOverflow() {
        assertThrows(IllegalArgumentException.class,
            ()->SeamRapierChartMath.sectionShift(-63,128));
        assertThrows(ArithmeticException.class,
            ()->SeamRapierChartMath.mapSection(Integer.MAX_VALUE,320,0));
        assertThrows(ArithmeticException.class,
            ()->SeamRapierChartMath.topSection(Integer.MIN_VALUE));
    }

    @Test void supportsOtherSixteenAlignedDimensionHeights() {
        assertEquals(-4,SeamRapierChartMath.sectionShift(0,64));
        assertEquals(-1,SeamRapierChartMath.mapSection(3,0,64));
        assertTrue(SeamRapierChartMath.entirelyBelowSource(-1,0));
    }
}
