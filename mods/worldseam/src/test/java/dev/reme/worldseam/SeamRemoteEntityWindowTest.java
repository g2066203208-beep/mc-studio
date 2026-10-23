package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamRemoteEntityWindowTest {
    @Test void netherTopIncludesRealArmorStandBelowLogical128() {
        assertTrue(SeamRemoteEntityWindow.contains(2.5,2.5,126.5,128.0,
            0,0,128,true));
        assertFalse(SeamRemoteEntityWindow.contains(2.5,2.5,128,130,
            0,0,128,true));
        assertFalse(SeamRemoteEntityWindow.contains(49,0,126,127,
            0,0,128,true));
    }
    @Test void overworldBottomIncludesBodyAboveMinus64() {
        assertTrue(SeamRemoteEntityWindow.contains(0,0,-64,-62,
            0,0,-64,false));
        assertFalse(SeamRemoteEntityWindow.contains(0,0,-66,-64,
            0,0,-64,false));
        assertFalse(SeamRemoteEntityWindow.contains(0,0,33,34,
            0,0,-64,false));
    }
    @Test void invalidCoordinatesNeverEnterNativeTracking() {
        assertFalse(SeamRemoteEntityWindow.contains(Double.NaN,0,0,1,
            0,0,128,true));
        assertFalse(SeamRemoteEntityWindow.contains(0,0,0,0,
            0,0,128,true));
        assertFalse(SeamRemoteEntityWindow.contains(0,0,126,127,
            0,0,Double.POSITIVE_INFINITY,true));
    }
}
