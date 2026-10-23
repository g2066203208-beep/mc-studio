package dev.reme.worldseam;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded snapshots of REAL entities owned by the foreign ServerLevel.
 * This is client tracking, never a second server authority or Sable body. */
public record SeamRemoteEntityPayload(String source,String target,
                                      List<Snapshot> entities)
        implements CustomPacketPayload {
    public static final int MAX_ENTITIES=64;
    public record Snapshot(int id,UUID uuid,ResourceLocation type,
                           double x,double y,double z,float yaw,float pitch,
                           boolean noGravity) {}
    public SeamRemoteEntityPayload {
        if(entities.size()>MAX_ENTITIES)
            throw new IllegalArgumentException("remote entity cap");
        entities=List.copyOf(entities);
    }
    public static final Type<SeamRemoteEntityPayload> TYPE=new Type<>(
        ResourceLocation.fromNamespaceAndPath("worldseam","remote_entities"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SeamRemoteEntityPayload>
        CODEC=StreamCodec.of((buf,p)->{
            buf.writeUtf(p.source(),128);
            buf.writeUtf(p.target(),128);
            buf.writeVarInt(p.entities().size());
            for(Snapshot e:p.entities()){
                buf.writeVarInt(e.id());
                buf.writeUUID(e.uuid());
                buf.writeResourceLocation(e.type());
                buf.writeDouble(e.x());buf.writeDouble(e.y());buf.writeDouble(e.z());
                buf.writeFloat(e.yaw());buf.writeFloat(e.pitch());
                buf.writeBoolean(e.noGravity());
            }
        },buf->{
            String from=buf.readUtf(128),to=buf.readUtf(128);
            int count=buf.readVarInt();
            if(count<0||count>MAX_ENTITIES)
                throw new IllegalArgumentException("invalid remote entity count "+count);
            var entities=new java.util.ArrayList<Snapshot>(count);
            for(int i=0;i<count;i++)
                entities.add(new Snapshot(buf.readVarInt(),buf.readUUID(),
                    buf.readResourceLocation(),buf.readDouble(),buf.readDouble(),
                    buf.readDouble(),buf.readFloat(),buf.readFloat(),
                    buf.readBoolean()));
            return new SeamRemoteEntityPayload(from,to,entities);
        });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
