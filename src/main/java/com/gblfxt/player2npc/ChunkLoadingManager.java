package com.gblfxt.player2npc;

import com.gblfxt.player2npc.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages chunk loading for companions so they stay active when players are offline.
 */
@EventBusSubscriber(modid = Player2NPC.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ChunkLoadingManager {

    private static TicketController ticketController;
    private static final Map<UUID, ChunkPos> loadedChunks = new HashMap<>();

    @SubscribeEvent
    public static void registerTicketControllers(RegisterTicketControllersEvent event) {
        ticketController = new TicketController(
                Player2NPC.id("companion_loader"),
                (level, helper) -> {
                    // Called when world loads - re-validate loaded chunks
                    // The helper contains all previously registered tickets
                }
        );
        event.register(ticketController);
        Player2NPC.LOGGER.info("Registered companion chunk loading controller");
    }

    /**
     * Start loading chunks around a companion.
     */
    public static void startLoadingChunks(CompanionEntity companion) {
        if (!Config.COMPANIONS_LOAD_CHUNKS.get()) {
            return;
        }

        if (!(companion.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        UUID companionId = companion.getUUID();
        ChunkPos chunkPos = new ChunkPos(companion.blockPosition());

        // Don't re-register if already loading this chunk
        if (loadedChunks.containsKey(companionId) && loadedChunks.get(companionId).equals(chunkPos)) {
            return;
        }

        // Unload old chunk if companion moved
        stopLoadingChunks(companion);

        // Force load the companion's chunk
        if (ticketController != null) {
            ticketController.forceChunk(
                    serverLevel,
                    companion.blockPosition(),
                    chunkPos.x,
                    chunkPos.z,
                    true,  // add ticket
                    true   // ticking (entities tick)
            );

            loadedChunks.put(companionId, chunkPos);
            Player2NPC.LOGGER.debug("Started chunk loading for companion {} at {}",
                    companion.getCompanionName(), chunkPos);
        }
    }

    /**
     * Stop loading chunks for a companion (when dismissed or despawned).
     */
    public static void stopLoadingChunks(CompanionEntity companion) {
        if (!(companion.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        UUID companionId = companion.getUUID();
        ChunkPos oldChunk = loadedChunks.remove(companionId);

        if (oldChunk != null && ticketController != null) {
            ticketController.forceChunk(
                    serverLevel,
                    companion.blockPosition(),
                    oldChunk.x,
                    oldChunk.z,
                    false,  // remove ticket
                    true
            );

            Player2NPC.LOGGER.debug("Stopped chunk loading for companion {} at {}",
                    companion.getCompanionName(), oldChunk);
        }
    }

    /**
     * Update chunk loading when companion moves to a new chunk.
     */
    public static void updateChunkLoading(CompanionEntity companion) {
        if (!Config.COMPANIONS_LOAD_CHUNKS.get()) {
            return;
        }

        UUID companionId = companion.getUUID();
        ChunkPos currentChunk = new ChunkPos(companion.blockPosition());
        ChunkPos loadedChunk = loadedChunks.get(companionId);

        // If companion moved to a different chunk, update the loaded chunk
        if (loadedChunk == null || !loadedChunk.equals(currentChunk)) {
            startLoadingChunks(companion);
        }
    }

    /**
     * Check if a companion has chunk loading enabled.
     */
    public static boolean isChunkLoadingEnabled(CompanionEntity companion) {
        return Config.COMPANIONS_LOAD_CHUNKS.get() && loadedChunks.containsKey(companion.getUUID());
    }
}
