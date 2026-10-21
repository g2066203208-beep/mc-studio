package dev.reme.mcstudio.climb;

import dev.reme.mcstudio.StudioMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Server acknowledgement, carrying validated anchors instead of trusting local
 * raycast prediction. Null anchors are explicit releases.
 */
public record ClimbSnapshot(int sequence, boolean enabled, @Nullable GripAnchor left,
                             @Nullable GripAnchor right) implements CustomPacketPayload {
    public static final Type<ClimbSnapshot> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StudioMod.MOD_ID, "grip_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClimbSnapshot> CODEC = StreamCodec.of(
            (buf, snapshot) -> {
                buf.writeVarInt(snapshot.sequence);
                buf.writeBoolean(snapshot.enabled);
                writeAnchor(buf, snapshot.left);
                writeAnchor(buf, snapshot.right);
            },
            buf -> new ClimbSnapshot(buf.readVarInt(), buf.readBoolean(), readAnchor(buf), readAnchor(buf))
    );

    private static void writeAnchor(RegistryFriendlyByteBuf buf, @Nullable GripAnchor anchor) {
        buf.writeBoolean(anchor != null);
        if (anchor == null) return;
        buf.writeBoolean(anchor.dynamic());
        if (anchor.dynamic()) buf.writeUUID(anchor.structureId());
        buf.writeBlockPos(anchor.block());
        writeVec(buf, anchor.localPoint());
        writeVec(buf, anchor.localNormal());
    }

    @Nullable
    private static GripAnchor readAnchor(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        UUID id = buf.readBoolean() ? buf.readUUID() : null;
        BlockPos block = buf.readBlockPos();
        return new GripAnchor(id, block, readVec(buf), readVec(buf));
    }

    private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 point) {
        buf.writeDouble(point.x);
        buf.writeDouble(point.y);
        buf.writeDouble(point.z);
    }

    private static Vec3 readVec(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
