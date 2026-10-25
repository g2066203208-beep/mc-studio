package dev.reme.worldseam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamRapierSceneLeaseTest {
    @Test void acceptsOnlyTheSameNonzeroNativeScene() {
        assertTrue(SeamRapierSceneLease.isSameLiveScene(0x1A2BL, 0x1A2BL));
        assertFalse(SeamRapierSceneLease.isSameLiveScene(0x1A2BL, 0x2B3CL));
    }

    @Test void rejectsUninitializedOrDisposedScenes() {
        assertFalse(SeamRapierSceneLease.isSameLiveScene(0, 0));
        assertFalse(SeamRapierSceneLease.isSameLiveScene(0, 42));
        assertFalse(SeamRapierSceneLease.isSameLiveScene(42, 0));
    }
}
