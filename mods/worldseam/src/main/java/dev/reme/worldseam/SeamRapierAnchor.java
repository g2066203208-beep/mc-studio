package dev.reme.worldseam;

import net.minecraft.world.phys.AABB;

/** Thin runtime adapter: all testable center/age arithmetic is Minecraft-free. */
final class SeamRapierAnchor {
    private SeamRapierAnchor() {}

    static boolean fresh(long sampled,long now) {
        return SeamRapierAnchorMath.fresh(sampled,now);
    }

    static SeamRapierAnchorMath.Center fromHull(AABB hull) {
        return hull==null?null:SeamRapierAnchorMath.fromBounds(
            hull.minX,hull.maxX,hull.minZ,hull.maxZ);
    }
}
