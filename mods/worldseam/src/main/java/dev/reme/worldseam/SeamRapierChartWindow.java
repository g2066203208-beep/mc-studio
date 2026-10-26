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

    /**
     * Actual native Rapier terrain upload order for a moving authoritative
     * hull. The former top-down, corner-first queue could upload 20+ distant
     * sections before the center section directly under the ship, leaving a
     * real collider absent during the first seconds of a descent.
     *
     * This schedules only REAL, asynchronously resident target sections;
     * the caller still checks getChunkNow() and never creates a fake collider.
     * It does not supply Atlas image colliders or fused contact impulses.
     */
    record Section(int cx, int cy, int cz) {}

    static java.util.List<Section> prioritize(int centerX, int centerZ,
                                              int bottom, int top,
                                              double hullMinY, int shift) {
        if (bottom > top || (long)top - bottom > 64)
            throw new IllegalArgumentException("Invalid native upload window");
        int nearSection = top;
        if (Double.isFinite(hullMinY)) {
            double mapped = Math.floor((hullMinY - shift) / 16.0);
            nearSection = mapped < bottom ? bottom
                : mapped > top ? top : (int)mapped;
        }
        final int reference = nearSection;
        var result = new java.util.ArrayList<Section>();
        for (int cy = bottom; cy <= top; cy++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dx = -2; dx <= 2; dx++)
                    result.add(new Section(centerX + dx, cy, centerZ + dz));
        // Upload the hull's own column first, then adjacent rings. Within
        // each ring, upload the actual section nearest the hull's lowest
        // point first. A nearby contact cannot wait behind a distant corner.
        result.sort(java.util.Comparator
            .comparingInt((Section s) -> Math.max(
                Math.abs(s.cx() - centerX), Math.abs(s.cz() - centerZ)))
            .thenComparingInt(s -> Math.abs(s.cy() - reference))
            .thenComparingInt(s -> Math.abs(s.cx() - centerX)
                + Math.abs(s.cz() - centerZ))
            .thenComparingInt(s -> -s.cy())
            .thenComparingInt(Section::cz)
            .thenComparingInt(Section::cx));
        return java.util.List.copyOf(result);
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
