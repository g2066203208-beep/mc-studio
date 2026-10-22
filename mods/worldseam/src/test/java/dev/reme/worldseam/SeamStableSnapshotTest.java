package dev.reme.worldseam;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeamStableSnapshotTest {
    @Test void repeatedPacketsPreserveImmutableGpuIdentity() {
        var old=List.of("stone:15","stone:15","air:0");
        assertTrue(SeamStableSnapshot.unchanged(old,
            List.of("stone:15","stone:15","air:0")));
        assertFalse(SeamStableSnapshot.unchanged(old,
            List.of("stone:15","stone:13","air:0")));
    }
    @Test void sequenceAndLightingChangesForceUpload() {
        assertFalse(SeamStableSnapshot.unchanged(
            List.of("glass:15","dirt:15"),List.of("dirt:15","glass:15")));
        assertFalse(SeamStableSnapshot.unchanged(
            List.of("stone:14"),List.of("stone:15")));
        assertTrue(SeamStableSnapshot.unchanged(List.of(),List.of()));
    }
}
