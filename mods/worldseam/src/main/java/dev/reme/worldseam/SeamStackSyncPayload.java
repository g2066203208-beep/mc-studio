package dev.reme.worldseam;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** One authoritative topology snapshot per save, sent to each client. */
public record SeamStackSyncPayload(SeamStack stack,long revision,
                                   List<DimensionTypeRef> types)
    implements CustomPacketPayload {
    public record DimensionTypeRef(String dimension,String type,int minY,int maxY) {}
    public static final Type<SeamStackSyncPayload> TYPE=new Type<>(
        ResourceLocation.fromNamespaceAndPath("worldseam","stack_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SeamStackSyncPayload> CODEC=
        StreamCodec.of((buf,p)->{
            buf.writeVarLong(p.revision());
            buf.writeBoolean(p.stack().loop());
            buf.writeVarInt(p.stack().entries().size());
            for(SeamStack.Entry e:p.stack().entries()){
                buf.writeUtf(e.id(),128);
                buf.writeBoolean(e.bottomY()!=null);
                if(e.bottomY()!=null)buf.writeInt(e.bottomY());
                buf.writeBoolean(e.topY()!=null);
                if(e.topY()!=null)buf.writeInt(e.topY());
                buf.writeBoolean(e.connectsUp());
                buf.writeBoolean(e.connectsDown());
            }
            buf.writeVarInt(p.types().size());
            for(DimensionTypeRef t:p.types()){
                buf.writeUtf(t.dimension(),128);
                buf.writeUtf(t.type(),128);
                buf.writeInt(t.minY());
                buf.writeInt(t.maxY());
            }
        },buf->{
            long revision=buf.readVarLong();
            boolean loop=buf.readBoolean();
            int n=buf.readVarInt();
            if(n<1||n>SeamStack.MAX_ENTRIES)
                throw new IllegalArgumentException("Invalid stack size "+n);
            var entries=new ArrayList<SeamStack.Entry>(n);
            for(int i=0;i<n;i++){
                String id=buf.readUtf(128);
                Integer low=buf.readBoolean()?buf.readInt():null;
                Integer top=buf.readBoolean()?buf.readInt():null;
                entries.add(new SeamStack.Entry(id,low,top,
                    buf.readBoolean(),buf.readBoolean()));
            }
            int size=buf.readVarInt();
            if(size<0||size>SeamStack.MAX_ENTRIES)
                throw new IllegalArgumentException("Invalid dimension types "+size);
            var types=new ArrayList<DimensionTypeRef>();
            for(int i=0;i<size;i++)
                types.add(new DimensionTypeRef(buf.readUtf(128),buf.readUtf(128),buf.readInt(),buf.readInt()));
            return new SeamStackSyncPayload(new SeamStack(entries,loop),
                revision,List.copyOf(types));
        });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
