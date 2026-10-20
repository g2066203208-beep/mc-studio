package dev.reme.mcstudio.climb;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exhaustion has been completely removed; only grip and lunge cooldown matter. */
class PeakGripRulesTest {
    @Test void lungeCanAlwaysTriggerWithGripAndNoCooldown() {
        assertTrue(PeakGripRules.canLunge(0,true));
        assertTrue(PeakGripRules.canLunge(0,true));
        assertFalse(PeakGripRules.canLunge(12,true));
        assertFalse(PeakGripRules.canLunge(0,false));
    }
    @Test void noStaminaOrExhaustionAPI() {
        assertEquals(0, PeakGripRules.class.getDeclaredFields().length);
    }
}
