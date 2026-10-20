package dev.reme.mcstudio.climb;

import dev.kosmx.playerAnim.minecraftApi.codec.AnimationCodecs;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the *actual* Player Animator Gecko/JSON decoder in JUnit so a green
 * Minecraft startup cannot hide missing or malformed animation assets.
 */
class PeakAnimationResourcesTest {
    @Test
    void allEightClipsLoadWithRealPlayerAnimatorDecoder() throws Exception {
        String resource = "/assets/reme_studio/player_animations/peak_climb.animation.json";
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertNotNull(stream, "Player Animator animation file is absent from the JAR");
            var animations = AnimationCodecs.deserialize("json", stream);
            var names = animations.stream().map(x -> x.getName().replace("\"", ""))
                    .collect(Collectors.toSet());

            assertEquals(Set.of("peak_hang", "peak_ascend", "peak_traverse",
                    "peak_mantle", "peak_release",
                    "peak_ceiling_hang", "peak_ceiling_crawl", "peak_ceiling_traverse"), names,
                    "Player Animator did not decode all PEAK animation clips");
            animations.forEach(a -> assertNotNull(a.playAnimation(),
                    "Decoding succeeded but animation cannot instantiate: " + a.getName()));
        }
    }
}
