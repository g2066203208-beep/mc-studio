package dev.reme.worldseam;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client explicitly acknowledges native remote chunk availability. */
public record SeamReadyPayload(String source, String target, int loadedChunks)
    implements CustomPacketPayload {
    public static final Type<SeamReadyPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("worldseam","remote_ready")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf,SeamReadyPayload> CODEC =
        StreamCodec.of((buf,p) -> {
            buf.writeUtf(p.source(),128);
            buf.writeUtf(p.target(),128);
            buf.writeVarInt(p.loadedChunks());
        },buf -> new SeamReadyPayload(buf.readUtf(128),buf.readUtf(128),buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
}
