package dev.reme.mcstudio.climb;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PeakGripRulesTest {
    @Test void holdingDrainsAndStaminaNeverGoesNegative() {
        assertTrue(PeakGripRules.staminaNext(100,true,false,false)<100);
        assertTrue(PeakGripRules.staminaNext(100,true,true,false)
                <PeakGripRules.staminaNext(100,true,false,false));
        assertEquals(0,PeakGripRules.staminaNext(0.01f,true,true,false),0.0001);
    }
    @Test void regeneratesOnSolidGroundOnly() {
        assertEquals(51.3f,PeakGripRules.staminaNext(50,false,false,true),0.0001);
        assertEquals(50f,PeakGripRules.staminaNext(50,false,false,false),0.0001);
        assertEquals(100,PeakGripRules.staminaNext(200,false,false,true),0.0001);
    }
    @Test void lungeRequiresStaminaNoCooldownAndGrip() {
        assertTrue(PeakGripRules.canLunge(18f,0,true));
        assertFalse(PeakGripRules.canLunge(17.9f,0,true));
        assertFalse(PeakGripRules.canLunge(100,1,true));
        assertFalse(PeakGripRules.canLunge(100,0,false));
    }
}
