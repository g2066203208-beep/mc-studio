package dev.reme.worldseam;

/**
 * Conservative guard for JNI scene-handle ownership. This is NOT a native
 * generation counter: pointer reuse requires an upstream generation API.
 */
final class SeamRapierSceneLease {
    private SeamRapierSceneLease() {}

    static boolean isSameLiveScene(long expected, long observed) {
        return expected != 0 && observed != 0 && expected == observed;
    }
}
