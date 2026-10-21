package dev.reme.worldseam;

import net.minecraft.server.level.ServerLevel;

/**
 * Logical topology boundaries differ from vanilla build-height boundaries.
 * Nether ceiling is Y=128 (the actual roof), not build height Y=256.
 */
public final class SeamBounds {
    public static final int NETHER_ROOF_SEAM_Y = 128;
    private SeamBounds() {}

    public static int top(String dimensionId, int vanillaMaxY) {
        return "minecraft:the_nether".equals(dimensionId)
            ? Math.min(NETHER_ROOF_SEAM_Y, vanillaMaxY) : vanillaMaxY;
    }

    public static int top(ServerLevel level) {
        return top(level.dimension().location().toString(), level.getMaxBuildHeight());
    }

    /** Inclusive range, only on *new* chunks and separately opted in. */
    public record CarveRange(int minY, int maxY) {
        public CarveRange {
            if (maxY < minY) throw new IllegalArgumentException("Invalid carve range");
        }
    }

    public static CarveRange newChunkCarveRange(
        String dimension, int vanillaMinY
    ) {
        return switch (dimension) {
            case "minecraft:overworld" -> new CarveRange(vanillaMinY, vanillaMinY + 4);
            case "minecraft:the_nether" -> new CarveRange(120, 127);
            default -> null;
        };
    }
}
