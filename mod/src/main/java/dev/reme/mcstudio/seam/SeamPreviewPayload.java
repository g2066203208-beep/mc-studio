package dev.reme.mcstudio.seam;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded, server-authoritative visual sample of the *other* ServerLevel.
 * This is not an instance of a client remote world. Later stages will replace
 * it with full chunk/lighting/entity streaming.
 */
public record SeamPreviewPayload(
    String currentDimension, List<BlockSample> blocks
) implements CustomPacketPayload {
    public static final Type<SeamPreviewPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("reme_studio", "seam_preview")
    );
    public static final int MAX_BLOCKS = 1400;

    public record BlockSample(int x, int y, int z, int blockStateId) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, SeamPreviewPayload> CODEC =
        StreamCodec.of((buf, message) -> {
            buf.writeUtf(message.currentDimension(), 128);
            if (message.blocks().size() > MAX_BLOCKS) {
                throw new IllegalArgumentException("Oversized seam preview");
            }
            buf.writeVarInt(message.blocks().size());
            for (BlockSample block : message.blocks()) {
                buf.writeInt(block.x());
                buf.writeInt(block.y());
                buf.writeInt(block.z());
                buf.writeVarInt(block.blockStateId());
            }
        }, buf -> {
            String dimension = buf.readUtf(128);
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_BLOCKS) {
                throw new IllegalArgumentException("Invalid seam preview size " + count);
            }
            List<BlockSample> blocks = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                blocks.add(new BlockSample(
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt()
                ));
            }
            return new SeamPreviewPayload(dimension, List.copyOf(blocks));
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
