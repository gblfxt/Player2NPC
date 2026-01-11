package com.gblfxt.llmoblings.network;

import com.gblfxt.llmoblings.LLMoblings;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    public static void register() {
        // Network registration happens via event in NeoForge
        // See registerPayloads method
    }

    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        // Use optional() so clients without the mod can still connect
        final PayloadRegistrar registrar = event.registrar(LLMoblings.MOD_ID).optional();

        // Register spawn companion packet (C2S)
        registrar.playToServer(
                SpawnCompanionPacket.TYPE,
                SpawnCompanionPacket.STREAM_CODEC,
                SpawnCompanionPacket::handle
        );

        // Register despawn companion packet (C2S)
        registrar.playToServer(
                DespawnCompanionPacket.TYPE,
                DespawnCompanionPacket.STREAM_CODEC,
                DespawnCompanionPacket::handle
        );

        // Register chat to companion packet (C2S)
        registrar.playToServer(
                CompanionChatPacket.TYPE,
                CompanionChatPacket.STREAM_CODEC,
                CompanionChatPacket::handle
        );
    }
}
