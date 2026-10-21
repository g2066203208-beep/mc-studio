package dev.reme.worldseam;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Version 2: ordered full/delta frames with dimension+scope isolation.
 * A null Visual deletes a formerly visible block. Light packs sky/block
 * brightness into four bits each; lighting updates are streamed as deltas.
 */
public record SeamPreviewPayload(SeamFrameState.Frame frame) implements CustomPacketPayload {
    public static final Type<SeamPreviewPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("worldseam", "seam_preview")
    );
    public static final int MAX_CHANGES = 2560;

    public static final StreamCodec<RegistryFriendlyByteBuf, SeamPreviewPayload> CODEC =
        StreamCodec.of((buf, payload) -> {
            var frame = payload.frame();
            buf.writeUtf(frame.dimension(), 128);
            buf.writeUtf(frame.scope(), 192);
            buf.writeVarLong(frame.revision());
            buf.writeBoolean(frame.full());
            if (frame.changes().size() > MAX_CHANGES)
                throw new IllegalArgumentException("Too many seam changes");
            buf.writeVarInt(frame.changes().size());
            for (var change : frame.changes()) {
                var pos = change.position();
                buf.writeInt(pos.x());
                buf.writeInt(pos.y());
                buf.writeInt(pos.z());
                boolean hasVisual = change.visual() != null;
                buf.writeBoolean(hasVisual);
                if (hasVisual) {
                    buf.writeVarInt(change.visual().stateId());
                    buf.writeByte(change.visual().light());
                }
            }
        }, buf -> {
            String dim = buf.readUtf(128);
            String scope = buf.readUtf(192);
            long revision = buf.readVarLong();
            boolean full = buf.readBoolean();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_CHANGES)
                throw new IllegalArgumentException("Invalid seam packet length " + count);
            List<SeamFrameState.Change> changes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                var pos = new SeamFrameState.Position(
                    buf.readInt(), buf.readInt(), buf.readInt()
                );
                SeamFrameState.Visual value = null;
                if (buf.readBoolean()) {
                    value = new SeamFrameState.Visual(
                        buf.readVarInt(), buf.readUnsignedByte()
                    );
                }
                changes.add(new SeamFrameState.Change(pos, value));
            }
            return new SeamPreviewPayload(new SeamFrameState.Frame(
                dim, scope, revision, full, changes
            ));
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
