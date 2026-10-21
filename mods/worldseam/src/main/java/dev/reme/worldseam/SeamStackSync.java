package dev.reme.worldseam;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread snapshot transmission; no stale stack across saved worlds. */
public final class SeamStackSync {
    private static MinecraftServer active;
    private static final Map<UUID,Long> lastRevision=new HashMap<>();
    private SeamStackSync() {}
    public static void tick(MinecraftServer server, ServerPlayer player) {
        if(active!=server){active=server;lastRevision.clear();}
        SeamSettings settings=SeamSettings.get(server);
        long version=settings.stackRevision();
        if(lastRevision.getOrDefault(player.getUUID(),-1L)==version)return;
        var list=new ArrayList<SeamStackSyncPayload.DimensionTypeRef>();
        for(SeamStack.Entry e:settings.stack().entries()) {
            var level=server.getAllLevels().stream()
                .filter(l->l.dimension().location().toString().equals(e.id()))
                .findFirst().orElse(null);
            if(level!=null) {
                // DimensionType registry key can differ from Level ID.
                var key=level.dimensionTypeRegistration().unwrapKey();
                list.add(new SeamStackSyncPayload.DimensionTypeRef(e.id(),
                    key.map(k->k.location().toString()).orElse(e.id())));
            }
        }
        PacketDistributor.sendToPlayer(player,
            new SeamStackSyncPayload(settings.stack(),version,List.copyOf(list)));
        lastRevision.put(player.getUUID(),version);
    }
}
