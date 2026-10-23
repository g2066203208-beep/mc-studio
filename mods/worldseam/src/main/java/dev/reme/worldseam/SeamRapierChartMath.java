package dev.reme.worldseam;

/**
 * Integer-exact chart coordinates for the first, translation-only, 16-aligned
 * remote-native Rapier terrain experiment. Reject unsupported transforms;
 * never silently round a native collider to another physical block.
 */
public final class SeamRapierChartMath {
    private SeamRapierChartMath() {}

    public static int sectionShift(int sourceSeamY,int targetSeamY) {
        long difference=(long)sourceSeamY-targetSeamY;
        if(Math.floorMod(difference,16L)!=0L)
            throw new IllegalArgumentException(
                "Native voxel projection requires 16-block-aligned boundaries");
        return Math.toIntExact(difference/16L);
    }

    public static int mapSection(int targetSectionY,int sourceSeamY,int targetSeamY) {
        return Math.addExact(targetSectionY,sectionShift(sourceSeamY,targetSeamY));
    }

    public static int topSection(int exclusiveTopY) {
        return Math.floorDiv(Math.subtractExact(exclusiveTopY,1),16);
    }

    public static boolean entirelyBelowSource(int mappedSectionY,int sourceBottomY) {
        return ((long)mappedSectionY+1L)*16L <= sourceBottomY;
    }
}
