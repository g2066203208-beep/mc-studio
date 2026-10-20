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
import net.neoforged.neoforge.network.PacketDistributor;

/** LMB hold intents to server, validated state back to player, poses to observers. */
@EventBusSubscriber(modid = StudioMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ClimbNetwork {
    private ClimbNetwork() {}

    /** Legacy record layout retained for straightforward migration: hand ignored, mode = Shift lunge. */
    public record Intent(int sequence, int hand, boolean down, boolean mode, float forward, float strafe)
            implements CustomPacketPayload {
        public static final Type<Intent> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(StudioMod.MOD_ID, "grip_intent"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Intent> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.sequence());
                    buf.writeVarInt(p.hand());
                    buf.writeBoolean(p.down());
                    buf.writeBoolean(p.mode());
                    buf.writeFloat(p.forward());
                    buf.writeFloat(p.strafe());
                },
                buf -> new Intent(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                        buf.readBoolean(), buf.readFloat(), buf.readFloat()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Other clients need to see the same hand-over-hand animation as the controlling player. */
    public record Pose(int entityId, boolean climbing, float phase, float stamina, byte action)
            implements CustomPacketPayload {
        public static final Type<Pose> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(StudioMod.MOD_ID, "peak_pose"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pose> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entityId());
                    buf.writeBoolean(p.climbing());
                    buf.writeFloat(p.phase());
                    buf.writeFloat(p.stamina());
                    buf.writeByte(p.action());
                },
                buf -> new Pose(buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(Intent.TYPE, Intent.CODEC, (packet, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ClimbRuntime.accept(player, packet.sequence(), packet.hand(), packet.down(),
                    packet.mode(), packet.forward(), packet.strafe());
            if (!packet.down() || packet.sequence() % 5 == 0) {
                ClimbState state = ClimbState.of(player);
                PacketDistributor.sendToPlayer(player,
                        new ClimbSnapshot(packet.sequence(), state.enabled, state.left, null));
            }
        });
        registrar.playToClient(ClimbSnapshot.TYPE, ClimbSnapshot.CODEC, (packet, context) -> {
            var player = context.player();
            if (player == null) return;
            ClimbState state = ClimbState.of(player);
            if (packet.sequence() <= state.lastAckSequence) return;
            state.lastAckSequence = packet.sequence();
            // Don't let an older server reply resurrect grip after button release.
            if (!state.peakHeld) return;
            state.enabled = packet.enabled();
            state.left = packet.left();
            state.right = null;
            GripAnchor.Contact hit = state.left == null ? null : state.left.resolve(player.level());
            state.lastLeft = hit == null ? net.minecraft.world.phys.Vec3.ZERO : hit.point();
        });
        registrar.playToClient(Pose.TYPE, Pose.CODEC, (packet, context) -> {
            var clientPlayer = context.player();
            if (clientPlayer != null) {
                PeakVisuals.observe(packet.entityId(), packet.climbing(),
                        packet.phase(), packet.stamina(), clientPlayer.level().getGameTime(), packet.action());
            }
        });
    }
}
