package dev.rawrland.constructionsite.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The mod's network messages. Registered on the mod event bus. */
public final class ModNetwork {

    /** Raise when a message changes, so that games with different versions refuse to connect. */
    private static final String VERSION = "3";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(ContraptionToolDataPayload.TYPE, ContraptionToolDataPayload.STREAM_CODEC,
            ContraptionToolDataPayload::handle);
        registrar.playToServer(CylinderSettingsPayload.TYPE, CylinderSettingsPayload.STREAM_CODEC,
            CylinderSettingsPayload::handle);
        registrar.playToServer(ConsoleInputPayload.TYPE, ConsoleInputPayload.STREAM_CODEC,
            ConsoleInputPayload::handle);
        registrar.playToServer(ConsoleScrollPayload.TYPE, ConsoleScrollPayload.STREAM_CODEC,
            ConsoleScrollPayload::handle);
    }
}
