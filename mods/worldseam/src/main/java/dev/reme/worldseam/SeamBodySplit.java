package dev.reme.worldseam;

import net.minecraft.world.phys.AABB;

/**
 * Exact half-open spatial split of ONE entity/body envelope across the seam.
 *
 * The two ServerLevels keep real native blocks and coordinates. A body whose
 * shape straddles the contact plane is evaluated against BOTH worlds, without
 * ever querying the unrelated blocks hidden beyond a dimension's logical
 * roof (e.g. Nether Y128..255).
 *
 * This geometric split is a conservative voxel collision/clearance primitive.
 * It is NOT a Sable rigid-body constraint or an in-solver Rapier image collider.
 */
public final class SeamBodySplit {
    private SeamBodySplit() {}

    public record Split(AABB targetPart,AABB sourcePart) {
        public Split {
            if(targetPart==null && sourcePart==null)
                throw new IllegalArgumentException("empty split");
        }
    }

    public static Split atTargetBoundary(AABB mappedTargetBody,
                                          SeamGraph.EdgeSide targetEntry,
                                          double targetBoundary,
                                          double sourceBoundary) {
        if(mappedTargetBody==null || targetEntry==null ||
            !Double.isFinite(targetBoundary) ||
            !Double.isFinite(sourceBoundary))
            throw new IllegalArgumentException("invalid plane");
        double shift=sourceBoundary-targetBoundary;
        if(!Double.isFinite(shift))
            throw new IllegalArgumentException("overflowing plane mapping");
        AABB target=null,source=null;
        if(targetEntry==SeamGraph.EdgeSide.TOP) {
            // The logical target exists BELOW its top contact plane.
            double top=Math.min(mappedTargetBody.maxY,targetBoundary);
            if(top>mappedTargetBody.minY)
                target=withY(mappedTargetBody,mappedTargetBody.minY,top);
            double min=Math.max(mappedTargetBody.minY,targetBoundary);
            if(mappedTargetBody.maxY>min)
                source=withY(mappedTargetBody,min+shift,
                    mappedTargetBody.maxY+shift);
        } else {
            // The logical target exists ABOVE its bottom contact plane.
            double min=Math.max(mappedTargetBody.minY,targetBoundary);
            if(mappedTargetBody.maxY>min)
                target=withY(mappedTargetBody,min,mappedTargetBody.maxY);
            double top=Math.min(mappedTargetBody.maxY,targetBoundary);
            if(top>mappedTargetBody.minY)
                source=withY(mappedTargetBody,
                    mappedTargetBody.minY+shift,top+shift);
        }
        return new Split(target,source);
    }

    private static AABB withY(AABB body,double low,double high) {
        return new AABB(body.minX,low,body.minZ,body.maxX,high,body.maxZ);
    }
}
