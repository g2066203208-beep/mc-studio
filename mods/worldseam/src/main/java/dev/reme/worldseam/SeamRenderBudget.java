package dev.reme.worldseam;

/**
 * Feedback controller for the TEMPORARY renderSingleBlock fallback.
 * The longer term solution is cached native section meshes, but until then
 * never blindly submit 3500 block models during every frame.
 *
 * Controls CPU-side submission work, not FPS, GPU cost or actual frame time.
 * No OpenGL or MC dependencies: JUnit exercises the safety caps.
 */
public final class SeamRenderBudget {
    public static final int MIN_MODELS = 160;
    public static final int MAX_MODELS = 3500;
    public static final long TARGET_CPU_NANOS = 8_000_000L;
    private int models = 900;

    public int limit() { return models; }

    /** Feedback only when the candidate list filled the previous budget. */
    public void observe(int rendered, long cpuNanos) {
        if (rendered <= 0 || cpuNanos <= 0) return;
        if (cpuNanos > TARGET_CPU_NANOS) {
            // An isolated pathological frame should reduce load quickly;
            // never assume GPU time equals CPU submission time.
            int target = clamp((int)Math.round(
                (double)rendered * TARGET_CPU_NANOS / cpuNanos));
            int next=clamp((int)Math.round(models * 0.60 + target * 0.40));
            // Integer rounding otherwise sticks at MIN+1 indefinitely
            // under a pathological slow GPU/CPU frame.
            models = target==MIN_MODELS && next<=MIN_MODELS+1
                ? MIN_MODELS : next;
        } else if (rendered >= models * 0.90 && cpuNanos < TARGET_CPU_NANOS * 0.70) {
            // Gradual recovery after the expensive environment is gone.
            models = clamp(models + Math.max(8,models / 18));
        }
    }

    private static int clamp(int value) {
        return Math.max(MIN_MODELS,Math.min(MAX_MODELS,value));
    }

    public void reset() { models=900; }
}
