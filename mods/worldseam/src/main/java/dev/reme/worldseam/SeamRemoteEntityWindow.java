package dev.reme.worldseam;

/** Strictly bounded *server-owned* entity observation near a joined plane.
 * The native entity remains authoritative in its original ServerLevel. */
final class SeamRemoteEntityWindow {
    private SeamRemoteEntityWindow() {}
    static boolean contains(double x,double z,double minY,double maxY,
                            double centerX,double centerZ,double boundary,
                            boolean entryTop) {
        if(!Double.isFinite(x)||!Double.isFinite(z)||
           !Double.isFinite(minY)||!Double.isFinite(maxY)||
           !Double.isFinite(centerX)||!Double.isFinite(centerZ)||
           !Double.isFinite(boundary)||minY>=maxY)return false;
        if(Math.abs(x-centerX)>48||Math.abs(z-centerZ)>48)return false;
        return entryTop ? minY<boundary && maxY>boundary-96
                        : maxY>boundary && minY<boundary+96;
    }
}
