package dev.reme.mcstudio.climb;

/**
 * Pure double-precision server wall-jump impulse: no Minecraft runtime dependency.
 * Used by authoritative ClimbSurfaceMath AND ordinary Java unit tests.
 */
public final class WallJumpMath {
    private static final double MAX_SPEED = .54;
    private WallJumpMath() {}

    public static double[] velocity(double lookX, double lookY, double lookZ,
                                    double normalX, double normalZ) {
        double nl = Math.hypot(normalX, normalZ);
        if (!Double.isFinite(nl) || nl < .5) return new double[]{0, 0, 0};
        double nx = normalX / nl, nz = normalZ / nl;
        double ll = Math.sqrt(lookX*lookX + lookY*lookY + lookZ*lookZ);
        if (!Double.isFinite(ll) || ll < 1.0e-7) {
            lookX=0; lookY=1; lookZ=0; ll=1;
        }
        double lx=lookX/ll, ly=lookY/ll, lz=lookZ/ll;
        double normalProjection=lx*nx+lz*nz;
        double tx=lx-normalProjection*nx, tz=lz-normalProjection*nz;
        double tl=Math.hypot(tx,tz);
        if (tl>.32) { tx*=.32/tl; tz*=.32/tl; }
        double rise=Math.max(.12,Math.min(.39,.22+ly*.24));
        double x=nx*.24+tx, y=rise, z=nz*.24+tz;
        double total=Math.sqrt(x*x+y*y+z*z);
        if (total>MAX_SPEED) { x*=MAX_SPEED/total; y*=MAX_SPEED/total; z*=MAX_SPEED/total; }
        return new double[]{x,y,z};
    }
}
