package dev.reme.mcstudio.climb;

import dev.reme.mcstudio.StudioMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = StudioMod.MOD_ID)
public final class ClimbNetwork {
    private ClimbNetwork() {}

    public record Intent(int sequence, int hand, boolean down, boolean mode, float forward, float strafe)
            implements CustomPacketPayload {
        public static final Type<Intent> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(StudioMod.MOD_ID, "grip_intent"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Intent> CODEC =
                StreamCodec.of((buf, p) -> {
                    buf.writeVarInt(p.sequence);
                    buf.writeVarInt(p.hand);
                    buf.writeBoolean(p.down);
                    buf.writeBoolean(p.mode);
                    buf.writeFloat(p.forward);
                    buf.writeFloat(p.strafe);
                }, buf -> new Intent(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                        buf.readBoolean(), buf.readFloat(), buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(Intent.TYPE, Intent.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                // Clients only send intent, never arbitrary world-space anchors.
                ClimbRuntime.accept(player, packet.sequence(), packet.hand(), packet.down(),
                        packet.mode(), packet.forward(), packet.strafe());
            }
        });
    }
}
