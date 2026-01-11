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

public record DespawnCompanionPacket(String name) implements CustomPacketPayload {

    public static final Type<DespawnCompanionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LLMoblings.MOD_ID, "despawn_companion"));

    public static final StreamCodec<ByteBuf, DespawnCompanionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            DespawnCompanionPacket::name,
            DespawnCompanionPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DespawnCompanionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // Find and remove companion by name
                List<CompanionEntity> companions = player.level().getEntitiesOfClass(
                        CompanionEntity.class,
                        player.getBoundingBox().inflate(64),
                        c -> c.isOwner(player) && c.getCompanionName().equals(packet.name())
                );

                for (CompanionEntity companion : companions) {
                    companion.discard();
                    LLMoblings.LOGGER.info("Despawned companion '{}' for player {}", packet.name(), player.getName().getString());
                }
            }
        });
    }
}
