package dev.reme.mcstudio.seam;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Real 16x16x16 palette sections, not arbitrary block lists.
 * Bounded decoder rejects oversized/invalid sections before allocating.
 */
public record SeamSectionPayload(SeamSectionCache.Frame frame)
    implements CustomPacketPayload {
    public static final Type<SeamSectionPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("reme_studio", "remote_section")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SeamSectionPayload> CODEC =
        StreamCodec.of((buf, payload) -> {
            var f = payload.frame();
            buf.writeUtf(f.source(), 128);
            buf.writeUtf(f.target(), 128);
            buf.writeUtf(f.scope(), 160);
            buf.writeVarLong(f.revision());
            buf.writeBoolean(f.clear());
            if (f.clear()) return;
            buf.writeBoolean(f.key() == null);
            if (f.key() == null) return; // heartbeat only
            buf.writeInt(f.key().chunkX());
            buf.writeInt(f.key().sectionY());
            buf.writeInt(f.key().chunkZ());
            buf.writeInt(f.sourceBoundary());
            buf.writeInt(f.targetBoundary());
            var e = f.content();
            buf.writeByte(e.bits());
            int[] palette = e.palette();
            buf.writeVarInt(palette.length);
            for (int id : palette) buf.writeVarInt(id);
            for (long word : e.words()) buf.writeLong(word);
            buf.writeBytes(e.light());
        }, buf -> {
            String source = buf.readUtf(128);
            String target = buf.readUtf(128);
            String scope = buf.readUtf(160);
            long revision = buf.readVarLong();
            boolean clear = buf.readBoolean();
            if (clear) return new SeamSectionPayload(new SeamSectionCache.Frame(
                source, target, scope, revision, true, null, 0, 0, null
            ));
            if (buf.readBoolean()) return new SeamSectionPayload(
                new SeamSectionCache.Frame(
                    source, target, scope, revision, false, null, 0, 0, null
                )
            );
            var key = new SeamSectionCache.Key(
                buf.readInt(), buf.readInt(), buf.readInt()
            );
            int sourceY = buf.readInt();
            int targetY = buf.readInt();
            int bits = buf.readUnsignedByte();
            if (bits < 1 || bits > 12)
                throw new IllegalArgumentException("Invalid section bit width");
            int n = buf.readVarInt();
            if (n < 1 || n > SeamSectionCodec.MAX_PALETTE || n > (1 << bits))
                throw new IllegalArgumentException("Invalid palette size");
            int[] palette = new int[n];
            for (int i = 0; i < n; i++) {
                palette[i] = buf.readVarInt();
                if (palette[i] < 0) throw new IllegalArgumentException("Negative state");
            }
            long[] words = new long[SeamSectionCodec.wordCount(bits)];
            for (int i = 0; i < words.length; i++) words[i] = buf.readLong();
            byte[] light = new byte[SeamSectionCodec.VOLUME];
            buf.readBytes(light);
            return new SeamSectionPayload(new SeamSectionCache.Frame(
                source, target, scope, revision, false, key,
                sourceY, targetY, new SeamSectionCodec.Encoded(
                    palette, words, light, bits
                )
            ));
        });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
