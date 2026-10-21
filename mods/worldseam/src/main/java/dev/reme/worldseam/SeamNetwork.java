package dev.reme.worldseam;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Versioned protocol. Client cache changes must run on the render/game
 * thread, not on an arbitrary network callback thread.
 */
public final class SeamNetwork {
    private SeamNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3").playToClient(
            SeamPreviewPayload.TYPE,
            SeamPreviewPayload.CODEC,
            (message, context) ->
                context.enqueueWork(() -> SeamPreviewClient.accept(message))
        );
        event.registrar("3").playToClient(
            SeamSectionPayload.TYPE,
            SeamSectionPayload.CODEC,
            (message, context) ->
                context.enqueueWork(() -> SeamSectionClient.accept(message))
        );
        event.registrar("3").playToClient(
            SeamChunkPayload.TYPE,
            SeamChunkPayload.CODEC,
            (message, context) ->
                context.enqueueWork(() -> SeamClientWorldPool.acceptChunk(message))
        );
        event.registrar("3").playToServer(
            SeamReadyPayload.TYPE,
            SeamReadyPayload.CODEC,
            (message, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof net.minecraft.server.level.ServerPlayer p)
                    SeamReadiness.acceptNetwork(p, message);
            })
        );
        event.registrar("3").playToClient(
            SeamHandoffPayload.TYPE,
            SeamHandoffPayload.CODEC,
            (message, context) ->
                context.enqueueWork(() -> SeamClientWorldPool.handoff(message))
        );
    }
}
