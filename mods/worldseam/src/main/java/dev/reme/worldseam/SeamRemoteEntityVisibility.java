package dev.reme.worldseam;

/** Pure bounds check for real foreign-world entity render eligibility. */
final class SeamRemoteEntityVisibility {
    private SeamRemoteEntityVisibility() {}

    static boolean intersectsNativeSlice(double minY,double maxY,
                                         double boundary,boolean entryTop) {
        if(!Double.isFinite(minY)||!Double.isFinite(maxY)||
           !Double.isFinite(boundary)||minY>=maxY)return false;
        // A straddling entity is eligible but this alone does NOT clip its
        // mesh at the seam; full native compositing remains a separate gate.
        return entryTop?minY<boundary:maxY>boundary;
    }
}
