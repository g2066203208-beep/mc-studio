package dev.reme.mcstudio.npc.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Exactly one verified actor emote, sent by the real server to visible clients.
 * Validated action index/duration on both sides. No location or movement data.
 */
public record ActorEmotePacket(String actorUuid, int poseId, int duration)
    implements CustomPacketPayload {
    public static final Type<ActorEmotePacket> TYPE=new Type<>(
        ResourceLocation.fromNamespaceAndPath("reme_studio","actor_emote_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActorEmotePacket> STREAM_CODEC=
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,ActorEmotePacket::actorUuid,
            ByteBufCodecs.VAR_INT,ActorEmotePacket::poseId,
            ByteBufCodecs.VAR_INT,ActorEmotePacket::duration,
            ActorEmotePacket::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
