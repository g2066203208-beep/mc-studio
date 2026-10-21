package dev.reme.mcstudio.seam;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SeamSectionCodecTest {
    @Test void paletteRoundTripForEveryWordWidthAndNegativeSectionY() {
        var random = new Random(821);
        for (int distinct : new int[]{1, 2, 3, 7, 16, 17, 31, 255, 256, 257, 512, 4096}) {
            int[] blocks = new int[SeamSectionCodec.VOLUME];
            byte[] light = new byte[SeamSectionCodec.VOLUME];
            for (int i=0; i<blocks.length; i++) {
                blocks[i] = i < distinct ? i + 900 : 900 + random.nextInt(distinct);
                light[i] = (byte)random.nextInt(256);
            }
            var packed = SeamSectionCodec.encode(blocks, light);
            for (int i=0; i<blocks.length; i++) {
                assertEquals(blocks[i], packed.stateAt(i));
                assertEquals(light[i] & 0xff, packed.lightAt(i));
            }
            assertEquals(packed.words().length, SeamSectionCodec.wordCount(packed.bits()));
        }
        assertEquals(4095, SeamSectionCodec.index(15, 15, 15));
        assertEquals(0, SeamSectionCodec.index(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> SeamSectionCodec.index(16, 0, 0));
    }

    @Test void uniformSectionsAreVerySmallAndCopiesAreImmutable() {
        int[] states=new int[4096]; Arrays.fill(states, 33);
        byte[] light = new byte[4096];
        var packed=SeamSectionCodec.encode(states, light);
        assertTrue(packed.encodedBytes() < 5000);
        packed.palette()[0] = 900;
        packed.words()[0] = -1L;
        packed.light()[0] = (byte)0xff;
        assertEquals(33, packed.stateAt(0));
        assertEquals(0, packed.lightAt(0));
        assertTrue(SeamSectionCodec.same(packed, SeamSectionCodec.encode(states, light)));
        light[0] = 1;
        assertFalse(SeamSectionCodec.same(packed, SeamSectionCodec.encode(states, light)));
    }

    @Test void rejectsMalformedEncodedPackets() {
        assertThrows(IllegalArgumentException.class,
            () -> new SeamSectionCodec.Encoded(new int[]{1}, new long[0], new byte[4096], 1));
        assertThrows(IllegalArgumentException.class,
            () -> new SeamSectionCodec.Encoded(new int[0], new long[64], new byte[4096], 1));
        assertThrows(IllegalArgumentException.class,
            () -> SeamSectionCodec.encode(new int[5000], new byte[4096]));
    }
}
