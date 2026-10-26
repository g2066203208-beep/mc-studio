package dev.reme.worldseam;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Authoritative atmosphere/clock for the REAL secondary ClientLevel.
 * Chunk packets contain blocks and lighting, not the destination's sky time
 * or weather. Never borrow the source dimension's sky state.
 */
public record SeamWorldStatePayload(
    String source, String target, long gameTime, long dayTime,
    float rainLevel, float thunderLevel
) implements CustomPacketPayload {
    public static final Type<SeamWorldStatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("worldseam", "remote_world_state")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SeamWorldStatePayload> CODEC =
        StreamCodec.of((buf, packet) -> {
            buf.writeUtf(packet.source(), 128);
            buf.writeUtf(packet.target(), 128);
            buf.writeLong(packet.gameTime());
            buf.writeLong(packet.dayTime());
            buf.writeFloat(packet.rainLevel());
            buf.writeFloat(packet.thunderLevel());
        }, buf -> new SeamWorldStatePayload(
            buf.readUtf(128), buf.readUtf(128),
            buf.readLong(), buf.readLong(), buf.readFloat(), buf.readFloat()
        ));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
