package dev.reme.worldseam;

import java.util.List;
import java.util.Objects;

/**
 * Vanilla dimensions remain real, separate ServerLevels.
 * Only their outer vertical borders become logically adjacent.
 *
 * This mapping is owned by REME; it does not use portal mods.
 */
public final class SeamGraph {
    public enum EdgeSide { TOP, BOTTOM }

    public record Link(String source, EdgeSide exit, String target, EdgeSide entry) {
        public Link {
            Objects.requireNonNull(source);
            Objects.requireNonNull(exit);
            Objects.requireNonNull(target);
            Objects.requireNonNull(entry);
            if (exit == entry || source.equals(target)) {
                throw new IllegalArgumentException("A seam must connect opposite borders of two dimensions");
            }
        }

        /**
         * Preserve distance to the boundary and absolute X/Z.
         * Source and target seamY are the respective min inclusive or max exclusive Y.
         */
        public double targetY(double sourceY, double sourceBoundaryY, double targetBoundaryY) {
            return targetBoundaryY + (sourceY - sourceBoundaryY);
        }

        public boolean crossed(double y, double minY, double maxY) {
            return exit == EdgeSide.TOP ? y >= maxY + 0.25 : y < minY - 0.25;
        }

        public boolean near(double y, double minY, double maxY, double radius) {
            double boundary = exit == EdgeSide.TOP ? maxY : minY;
            return Math.abs(y - boundary) <= radius;
        }
    }

    // Sequence: upper realm -> lower realm. No torus/loop.
    public static final List<Link> LINKS = List.of(
        new Link("minecraft:overworld", EdgeSide.BOTTOM,
            "minecraft:the_nether", EdgeSide.TOP),
        new Link("minecraft:the_nether", EdgeSide.TOP,
            "minecraft:overworld", EdgeSide.BOTTOM),
        new Link("minecraft:the_nether", EdgeSide.BOTTOM,
            "minecraft:the_end", EdgeSide.TOP),
        new Link("minecraft:the_end", EdgeSide.TOP,
            "minecraft:the_nether", EdgeSide.BOTTOM)
    );

    private SeamGraph() {}

    public static List<Link> from(String dimensionId) {
        return LINKS.stream().filter(link -> link.source().equals(dimensionId)).toList();
    }

    public static double boundaryY(EdgeSide side, int minY, int maxY) {
        if (maxY <= minY) throw new IllegalArgumentException("invalid build height");
        return side == EdgeSide.TOP ? maxY : minY;
    }

    /** Each one-way link must have exactly one opposite-direction twin. */
    public static boolean mutuallyConnected() {
        return LINKS.stream().allMatch(link ->
            LINKS.stream().filter(candidate ->
                candidate.source().equals(link.target())
                    && candidate.target().equals(link.source())
                    && candidate.exit() == link.entry()
                    && candidate.entry() == link.exit()
            ).count() == 1);
    }
}
