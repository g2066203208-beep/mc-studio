package dev.reme.worldseam;

/** Pure target-chart vertical math. This is not a fused Rapier solver. */
final class SeamRapierChartWindow {
    record Vertical(int bottom,int top) {}
    private SeamRapierChartWindow() {}

    static boolean intersects(double minY,double maxY,double sourceBottom,
                              double targetBottom,double targetTop,double margin) {
        if(!Double.isFinite(minY)||!Double.isFinite(maxY)||
           !Double.isFinite(sourceBottom)||!Double.isFinite(targetBottom)||
           !Double.isFinite(targetTop)||!Double.isFinite(margin)||
           minY>=maxY||targetBottom>=targetTop||margin<0)return false;
        double mappedBottom=sourceBottom+(targetBottom-targetTop);
        return minY<sourceBottom+margin && maxY>mappedBottom;
    }

    /** One section of real target terrain ahead of the lowest hull point. */
    static Vertical select(double hullMinY,int shift,int targetMinSection,
                           int targetTopSection,int depth) {
        if(depth<1||targetMinSection>targetTopSection)
            throw new IllegalArgumentException("Invalid native chart window");
        int initialBottom=(int)Math.max((long)targetMinSection,
            (long)targetTopSection-depth+1L);
        int bottom=initialBottom;
        if(Double.isFinite(hullMinY)) {
            long hullSection=(long)Math.floor((hullMinY-shift)/16.0);
            bottom=(int)Math.max((long)targetMinSection,
                Math.min((long)initialBottom,hullSection-1L));
        }
        int top=(int)Math.min((long)targetTopSection,(long)bottom+depth-1L);
        return new Vertical(bottom,top);
    }
}
