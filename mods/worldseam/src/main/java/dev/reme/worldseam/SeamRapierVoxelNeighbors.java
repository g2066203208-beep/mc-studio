package dev.reme.worldseam;

/**
 * Small, dependency-free classification of six REAL loaded voxel neighbors
 * for the Sable native terrain bridge. Unknown/unloaded neighbors are false.
 * This is not a shared Atlas solver and supplies no forces itself.
 */
public final class SeamRapierVoxelNeighbors {
    private SeamRapierVoxelNeighbors() {}

    public enum Kind { EMPTY, FACE, EDGE, CORNER, INTERIOR }

    public static Kind classify(boolean collidable,boolean fullCube,
                                boolean special,
                                boolean nx,boolean px,boolean ny,boolean py,
                                boolean nz,boolean pz) {
        if(!collidable)return Kind.EMPTY;
        if(!fullCube || special)return Kind.CORNER;
        int paired=(nx&&px?1:0)+(ny&&py?1:0)+(nz&&pz?1:0);
        if(paired==3)return Kind.INTERIOR;
        if(paired==1)return Kind.EDGE;
        if(paired==0)return Kind.CORNER;
        return Kind.FACE;
    }

    /**
     * Only voxels in the SAME uploaded native section may hide one another
     * during neighborhood classification. The foreign ServerLevel can have
     * more loaded chunks than the bounded Rapier projection. Treating a
     * loaded but unprojected neighbor as support can mark a border voxel
     * INTERIOR and remove its collision face entirely. Conservative section
     * edges also remain correct while asynchronous native uploads/removals
     * are in flight, without relying on a stale upload ordering.
     */
    public static boolean sameNativeSection(int x,int y,int z,
                                            int nx,int ny,int nz) {
        return Math.floorDiv(x,16)==Math.floorDiv(nx,16)
            && Math.floorDiv(y,16)==Math.floorDiv(ny,16)
            && Math.floorDiv(z,16)==Math.floorDiv(nz,16);
    }

    public static boolean matchesWitness(int x,int y,int z,
                                         int witnessX,int witnessY,int witnessZ) {
        return x==witnessX && y==witnessY && z==witnessZ;
    }
}
