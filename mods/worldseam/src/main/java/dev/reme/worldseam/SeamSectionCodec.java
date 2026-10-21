package dev.reme.worldseam;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minecraft-sized 16^3 section, encoded using a local block-state palette.
 * No Minecraft classes are used here so packing is deterministic/testable.
 *
 * The packed indices NEVER cross a 64-bit boundary. Its bits-per-state
 * therefore determine the number of entries per word (vanilla-style).
 */
public final class SeamSectionCodec {
    public static final int VOLUME = 16 * 16 * 16;
    public static final int MAX_PALETTE = VOLUME;
    public static final int MAX_WORDS = VOLUME;
    public static final int MAX_LIGHT_BYTES = VOLUME;

    private SeamSectionCodec() {}

    // Minecraft local indexing is Y-major, Z-middle, X-minor.
    public static int index(int x, int y, int z) {
        if ((x | y | z) < 0 || x > 15 || y > 15 || z > 15)
            throw new IllegalArgumentException("Section-local coordinate out of bounds");
        return (y << 8) | (z << 4) | x;
    }

    public record Encoded(int[] palette, long[] words, byte[] light, int bits) {
        public Encoded {
            if (palette == null || words == null || light == null ||
                palette.length == 0 || palette.length > MAX_PALETTE ||
                bits < 1 || bits > 12 || light.length != VOLUME ||
                words.length != wordCount(bits))
                throw new IllegalArgumentException("Invalid section data bounds");
            if (palette.length > (1 << bits))
                throw new IllegalArgumentException("Palette exceeds declared index bits");
            palette = palette.clone();
            words = words.clone();
            light = light.clone();
        }

        @Override public int[] palette() { return palette.clone(); }
        @Override public long[] words() { return words.clone(); }
        @Override public byte[] light() { return light.clone(); }

        public int stateAt(int offset) {
            if (offset < 0 || offset >= VOLUME)
                throw new IndexOutOfBoundsException(offset);
            int count = 64 / bits;
            long mask = (1L << bits) - 1;
            int index = (int) ((words[offset / count] >>> ((offset % count) * bits)) & mask);
            if (index >= palette.length)
                throw new IllegalArgumentException("Corrupted palette index " + index);
            return palette[index];
        }

        public int lightAt(int offset) { return light[offset] & 0xff; }
        public int encodedBytes() {
            return palette.length * 4 + words.length * 8 + light.length;
        }
    }

    public static int wordCount(int bits) {
        if (bits < 1 || bits > 12) throw new IllegalArgumentException("Invalid bits");
        int perWord = 64 / bits;
        return (VOLUME + perWord - 1) / perWord;
    }

    public static Encoded encode(int[] states, byte[] light) {
        if (states == null || light == null ||
            states.length != VOLUME || light.length != VOLUME)
            throw new IllegalArgumentException("Expected 4096 cells for each array");
        Map<Integer, Integer> indices = new LinkedHashMap<>();
        for (int state : states) {
            if (state < 0) throw new IllegalArgumentException("Negative block state id");
            indices.computeIfAbsent(state, ignored -> indices.size());
        }
        int[] palette = new int[indices.size()];
        indices.forEach((state, ordinal) -> palette[ordinal] = state);
        int bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
        int perWord = 64 / bits;
        long[] words = new long[wordCount(bits)];
        for (int i = 0; i < VOLUME; i++) {
            long ordinal = indices.get(states[i]);
            words[i / perWord] |= ordinal << ((i % perWord) * bits);
        }
        return new Encoded(palette, words, light, bits);
    }

    public static boolean same(Encoded a, Encoded b) {
        return a.bits == b.bits
            && Arrays.equals(a.palette, b.palette)
            && Arrays.equals(a.words, b.words)
            && Arrays.equals(a.light, b.light);
    }
}
