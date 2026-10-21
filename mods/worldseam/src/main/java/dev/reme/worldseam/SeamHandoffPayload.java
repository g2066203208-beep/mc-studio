package dev.reme.worldseam;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom no-respawn client handoff; sent only after the server has migrated
 * a real ServerPlayer into a different ServerLevel.
 */
public record SeamHandoffPayload(String from, String to,
    double x,double y,double z,float yaw,float pitch)
    implements CustomPacketPayload {
    public static final Type<SeamHandoffPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("worldseam","direct_handoff")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf,SeamHandoffPayload> CODEC =
        StreamCodec.of((buf,p)->{
            buf.writeUtf(p.from(),128);
            buf.writeUtf(p.to(),128);
            buf.writeDouble(p.x());
            buf.writeDouble(p.y());
            buf.writeDouble(p.z());
            buf.writeFloat(p.yaw());
            buf.writeFloat(p.pitch());
        },buf -> new SeamHandoffPayload(buf.readUtf(128),buf.readUtf(128),
            buf.readDouble(),buf.readDouble(),buf.readDouble(),
            buf.readFloat(),buf.readFloat()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
