package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamRemoteEntityVisibilityTest {
    @Test void topEntryIncludesOnlyBodiesReachingBelowLogicalRoof() {
        assertTrue(SeamRemoteEntityVisibility.intersectsNativeSlice(126,127.8,128,true));
        assertTrue(SeamRemoteEntityVisibility.intersectsNativeSlice(127.5,128.5,128,true));
        assertFalse(SeamRemoteEntityVisibility.intersectsNativeSlice(128,129,128,true));
    }
    @Test void bottomEntryIncludesOnlyBodiesReachingAboveLogicalFloor() {
        assertTrue(SeamRemoteEntityVisibility.intersectsNativeSlice(-64,-62,-64,false));
        assertTrue(SeamRemoteEntityVisibility.intersectsNativeSlice(-65,-63,-64,false));
        assertFalse(SeamRemoteEntityVisibility.intersectsNativeSlice(-66,-64,-64,false));
    }
    @Test void rejectsInvalidBoundsAndNaN() {
        assertFalse(SeamRemoteEntityVisibility.intersectsNativeSlice(2,2,0,true));
        assertFalse(SeamRemoteEntityVisibility.intersectsNativeSlice(
            Double.NaN,1,0,true));
        assertFalse(SeamRemoteEntityVisibility.intersectsNativeSlice(
            0,Double.POSITIVE_INFINITY,1,false));
    }
}
