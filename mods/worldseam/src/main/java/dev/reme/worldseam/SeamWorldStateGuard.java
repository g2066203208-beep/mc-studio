package dev.reme.worldseam;

/**
 * Reject foreign-dimension atmosphere updates that belong to an old handoff
 * or contain nonphysical/corrupt weather values. Dimension identity matters:
 * the active and secondary ClientLevels can exchange roles without respawn.
 */
final class SeamWorldStateGuard {
    private SeamWorldStateGuard() {}

    static boolean accepts(String expectedSource, String expectedTarget,
                           String packetSource, String packetTarget,
                           float rain, float thunder) {
        return expectedSource != null && expectedTarget != null &&
            expectedSource.equals(packetSource) &&
            expectedTarget.equals(packetTarget) &&
            Float.isFinite(rain) && Float.isFinite(thunder) &&
            rain >= 0f && rain <= 1f &&
            thunder >= 0f && thunder <= 1f;
    }
}
