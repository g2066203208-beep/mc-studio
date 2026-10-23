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

    public static boolean matchesWitness(int x,int y,int z,
                                         int witnessX,int witnessY,int witnessZ) {
        return x==witnessX && y==witnessY && z==witnessZ;
    }
}
