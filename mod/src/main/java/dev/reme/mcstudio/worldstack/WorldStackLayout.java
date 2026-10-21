package dev.reme.mcstudio.worldstack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ordered from the highest world to the lowest world.
 * Adjacent layers connect their floor to the next layer's ceiling.
 * This class deliberately has no dependency on Minecraft or Immersive Portals,
 * so the topology can be unit tested independently.
 */
public final class WorldStackLayout {
    public record Layer(String dimensionId, double scale, String bedrockReplacement) {
        public Layer {
            if (dimensionId == null || dimensionId.isBlank()) {
                throw new IllegalArgumentException("Missing dimension id");
            }
            if (!Double.isFinite(scale) || scale <= 0) {
                throw new IllegalArgumentException("Scale must be finite and positive");
            }
            if (bedrockReplacement == null || bedrockReplacement.isBlank()) {
                throw new IllegalArgumentException("Missing bedrock replacement");
            }
        }
    }

    public record Connection(String upper, String lower) {}

    // Equal scale makes the vertical transition continuous (1:1 X/Z).
    // The normal Nether portal's 8:1 coordinate rule is separate.
    public static final List<Layer> DEFAULT = List.of(
        new Layer("minecraft:the_end", 1.0, "minecraft:obsidian"),
        new Layer("minecraft:overworld", 1.0, "minecraft:obsidian"),
        new Layer("minecraft:the_nether", 1.0, "minecraft:obsidian")
    );

    private WorldStackLayout() {}

    public static void validate(List<Layer> layers) {
        if (layers == null || layers.size() < 2) {
            throw new IllegalArgumentException("At least two layers are required");
        }
        Set<String> ids = new HashSet<>();
        for (Layer layer : layers) {
            if (layer == null || !ids.add(layer.dimensionId())) {
                throw new IllegalArgumentException("Null or duplicate dimension");
            }
        }
    }

    public static List<Connection> connections(List<Layer> layers) {
        validate(layers);
        List<Connection> result = new ArrayList<>();
        for (int i = 0; i < layers.size() - 1; i++) {
            result.add(new Connection(layers.get(i).dimensionId(), layers.get(i + 1).dimensionId()));
        }
        return List.copyOf(result);
    }
}
