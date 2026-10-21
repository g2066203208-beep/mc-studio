package dev.reme.mcstudio.climb;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GripRulesTest {
    @Test void intentSequenceRejectsReplay() {
        assertTrue(GripRules.validIntent(9, 8, 1, -1));
        assertFalse(GripRules.validIntent(8, 8, 0, 0));
        assertFalse(GripRules.validIntent(3, 8, 0, 0));
    }

    @Test void intentNeverAcceptsNaNsOrMovementOutsideControls() {
        assertFalse(GripRules.validIntent(2, 1, Float.NaN, 0));
        assertFalse(GripRules.validIntent(2, 1, Float.POSITIVE_INFINITY, 0));
        assertFalse(GripRules.validIntent(2, 1, 0, 20));
        assertTrue(GripRules.validIntent(2, 1, 0, 1));
    }

    @Test void ropeIsUnilateralAndPullsOnlyWhenExtended() {
        assertEquals(0, GripRules.inwardCorrection(0), 1e-10);
        assertEquals(0, GripRules.inwardCorrection(1.85), 1e-10);
        assertTrue(GripRules.inwardCorrection(2.0) > 0);
        assertEquals(0.2, GripRules.inwardCorrection(10), 1e-10);
    }

    @Test void extremeStructureMovementMustRelease() {
        assertFalse(GripRules.snapTooFar(1.5));
        assertTrue(GripRules.snapTooFar(1.5001));
        assertTrue(GripRules.snapTooFar(Double.NaN));
        assertTrue(GripRules.snapTooFar(Double.POSITIVE_INFINITY));
    }

    @Test void speedCapAccountsForCarrierMotionButIsFinite() {
        assertEquals(0.33, GripRules.allowedSpeed(0), 1e-10);
        assertEquals(0.73, GripRules.allowedSpeed(0.4), 1e-10);
        assertEquals(1.83, GripRules.allowedSpeed(100), 1e-10);
    }
}
