package com.gblfxt.llmoblings.network;

import com.gblfxt.llmoblings.LLMoblings;
import com.gblfxt.llmoblings.entity.CompanionEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record SpawnCompanionPacket(String name, String skinUrl) implements CustomPacketPayload {

    public static final Type<SpawnCompanionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LLMoblings.MOD_ID, "spawn_companion"));

    public static final StreamCodec<ByteBuf, SpawnCompanionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            SpawnCompanionPacket::name,
            ByteBufCodecs.STRING_UTF8,
            SpawnCompanionPacket::skinUrl,
            SpawnCompanionPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpawnCompanionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // Check if player already has max companions
                List<CompanionEntity> existingCompanions = player.level().getEntitiesOfClass(
                        CompanionEntity.class,
                        player.getBoundingBox().inflate(64),
                        c -> c.isOwner(player)
                );

                if (existingCompanions.size() >= 3) { // Max companions
                    return;
                }

                // Spawn new companion
                CompanionEntity companion = new CompanionEntity(LLMoblings.COMPANION.get(), player.level());
                companion.setCompanionName(packet.name());
                companion.setSkinUrl(packet.skinUrl());
                companion.setOwner(player);
                companion.setPos(player.getX(), player.getY(), player.getZ());

                player.level().addFreshEntity(companion);
                LLMoblings.LOGGER.info("Spawned companion '{}' for player {}", packet.name(), player.getName().getString());
            }
        });
    }
}
