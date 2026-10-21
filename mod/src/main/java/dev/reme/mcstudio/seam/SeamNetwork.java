package dev.reme.mcstudio.seam;

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
    }
}
