package dev.reme.worldseam;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client-only authoritative snapshot, cleared at logout. */
public final class SeamStackClient {
    private static SeamStack stack=SeamStack.defaults();
    private static Map<String,SeamStackSyncPayload.DimensionTypeRef> types=Map.of();
    private static long revision=-1;
    private SeamStackClient() {}

    public static void install() {
        NeoForge.EVENT_BUS.addListener(
            (ClientPlayerNetworkEvent.LoggingOut e)->reset()
        );
    }

    public static void accept(SeamStackSyncPayload payload) {
        if(payload.revision()<revision)return;
        stack=payload.stack();
        revision=payload.revision();
        var refs=new HashMap<String,SeamStackSyncPayload.DimensionTypeRef>();
        for(var t:payload.types())refs.put(t.dimension(),t);
        types=Map.copyOf(refs);
    }

    public static List<SeamGraph.Link> from(String id) {return stack.from(id);}
    public static int boundary(String id,SeamGraph.EdgeSide side,int min,int max) {
        return stack.boundary(id,side,min,max);
    }
    public static String dimensionType(String dimension) {
        var data=types.get(dimension);
        return data==null?dimension:data.type();
    }
    public static int minY(String dim) {
        var found=types.get(dim);
        return found!=null?found.minY():("minecraft:overworld".equals(dim)?-64:0);
    }
    public static int maxY(String dim) {
        var found=types.get(dim);
        return found!=null?found.maxY():("minecraft:overworld".equals(dim)?320:256);
    }
    public static void reset() {
        stack=SeamStack.defaults();
        types=Map.of();
        revision=-1;
    }
}
