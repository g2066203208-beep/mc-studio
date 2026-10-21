package dev.reme.worldseam;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.resources.ResourceLocation;

/**
 * Complete, vanilla-format native chunk data, explicitly addressed to a
 * second dimension. Unlike the experimental sparse preview, this includes
 * all block/biome palettes, heightmaps and block-entity data for the chunk.
 * Light data will follow in a separate bounded channel.
 */
public record SeamChunkPayload(
    String source, String target, int chunkX, int chunkZ,
    ClientboundLevelChunkPacketData data,
    ClientboundLightUpdatePacketData lighting
) implements CustomPacketPayload {
    public static final Type<SeamChunkPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("worldseam", "remote_chunk")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SeamChunkPayload> CODEC =
        StreamCodec.of((buf, packet) -> {
            buf.writeUtf(packet.source(), 128);
            buf.writeUtf(packet.target(), 128);
            buf.writeInt(packet.chunkX());
            buf.writeInt(packet.chunkZ());
            packet.data().write(buf);
            packet.lighting().write(buf);
        }, buf -> {
            String source = buf.readUtf(128);
            String target = buf.readUtf(128);
            int x = buf.readInt();
            int z = buf.readInt();
            ClientboundLevelChunkPacketData blocks =
                new ClientboundLevelChunkPacketData(buf, x, z);
            ClientboundLightUpdatePacketData light =
                new ClientboundLightUpdatePacketData(buf, x, z);
            return new SeamChunkPayload(source, target, x, z, blocks, light);
        });
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
