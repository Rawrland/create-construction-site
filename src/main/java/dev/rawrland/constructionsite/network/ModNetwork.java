package dev.rawrland.constructionsite.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The mod's network messages. Registered on the mod event bus. */
public final class ModNetwork {

    /** Raise when a message changes, so that games with different versions refuse to connect. */
    private static final String VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(ContraptionToolDataPayload.TYPE, ContraptionToolDataPayload.STREAM_CODEC,
            ContraptionToolDataPayload::handle);
    }
}
