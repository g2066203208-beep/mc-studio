package dev.reme.mcstudio.worldstack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldStackLayoutTest {
    @Test
    void threeLayersYieldExactlyTwoAdjacentConnections() {
        assertEquals(List.of(
            new WorldStackLayout.Connection("minecraft:the_end", "minecraft:overworld"),
            new WorldStackLayout.Connection("minecraft:overworld", "minecraft:the_nether")
        ), WorldStackLayout.connections(WorldStackLayout.DEFAULT));
    }

    @Test
    void defaultHasEqualPositiveScaleAndObsidianReplacement() {
        assertEquals(3, WorldStackLayout.DEFAULT.size());
        assertTrue(WorldStackLayout.DEFAULT.stream().allMatch(x -> x.scale() == 1.0));
        assertTrue(WorldStackLayout.DEFAULT.stream().allMatch(
            x -> x.bedrockReplacement().equals("minecraft:obsidian")));
    }

    @Test
    void invalidTopologyIsRejectedBeforeTouchingWorld() {
        var end = WorldStackLayout.DEFAULT.get(0);
        assertThrows(IllegalArgumentException.class,
            () -> WorldStackLayout.validate(List.of(end, end)));
        assertThrows(IllegalArgumentException.class,
            () -> WorldStackLayout.connections(List.of(end)));
        assertThrows(IllegalArgumentException.class,
            () -> new WorldStackLayout.Layer("minecraft:overworld", 0, "minecraft:obsidian"));
    }
}
