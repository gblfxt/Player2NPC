package com.gblfxt.player2npc;

import com.gblfxt.player2npc.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

import java.util.*;

/**
 * Manages chunk loading for companions so they stay active when players are offline.
 * Loads a 5x5 grid of chunks (25 chunks) around each companion for an 80x80 block working area.
 */
@EventBusSubscriber(modid = Player2NPC.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ChunkLoadingManager {

    private static TicketController ticketController;
    private static final Map<UUID, Set<ChunkPos>> loadedChunks = new HashMap<>();

    // Load a 5x5 grid of chunks around the companion (2 chunks in each direction + center)
    private static final int CHUNK_LOAD_RADIUS = 2;
    // This gives an 80x80 block working area (5 chunks * 16 blocks per chunk)

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
     * Start loading chunks around a companion (5x5 grid = 25 chunks).
     */
    public static void startLoadingChunks(CompanionEntity companion) {
        if (!Config.COMPANIONS_LOAD_CHUNKS.get()) {
            return;
        }

        if (!(companion.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        UUID companionId = companion.getUUID();
        ChunkPos centerChunk = new ChunkPos(companion.blockPosition());

        // Check if we need to update (companion moved to different chunk)
        Set<ChunkPos> currentLoaded = loadedChunks.get(companionId);
        if (currentLoaded != null && currentLoaded.contains(centerChunk)) {
            // Still in a loaded chunk, check if center chunk changed significantly
            // Only re-center if we moved more than 1 chunk from previous center
            return;
        }

        // Unload old chunks if companion moved
        stopLoadingChunks(companion);

        // Force load a 5x5 grid of chunks around the companion
        if (ticketController != null) {
            Set<ChunkPos> newLoadedChunks = new HashSet<>();

            for (int dx = -CHUNK_LOAD_RADIUS; dx <= CHUNK_LOAD_RADIUS; dx++) {
                for (int dz = -CHUNK_LOAD_RADIUS; dz <= CHUNK_LOAD_RADIUS; dz++) {
                    int chunkX = centerChunk.x + dx;
                    int chunkZ = centerChunk.z + dz;

                    ticketController.forceChunk(
                            serverLevel,
                            companion.blockPosition(),
                            chunkX,
                            chunkZ,
                            true,  // add ticket
                            true   // ticking (entities tick)
                    );

                    newLoadedChunks.add(new ChunkPos(chunkX, chunkZ));
                }
            }

            loadedChunks.put(companionId, newLoadedChunks);
            Player2NPC.LOGGER.debug("Started chunk loading for companion {} - {} chunks centered at {}",
                    companion.getCompanionName(), newLoadedChunks.size(), centerChunk);
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
        Set<ChunkPos> oldChunks = loadedChunks.remove(companionId);

        if (oldChunks != null && ticketController != null) {
            for (ChunkPos chunk : oldChunks) {
                ticketController.forceChunk(
                        serverLevel,
                        companion.blockPosition(),
                        chunk.x,
                        chunk.z,
                        false,  // remove ticket
                        true
                );
            }

            Player2NPC.LOGGER.debug("Stopped chunk loading for companion {} - {} chunks unloaded",
                    companion.getCompanionName(), oldChunks.size());
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
        Set<ChunkPos> currentLoadedChunks = loadedChunks.get(companionId);

        // If companion moved outside the loaded chunks, re-center the grid
        if (currentLoadedChunks == null || !currentLoadedChunks.contains(currentChunk)) {
            startLoadingChunks(companion);
        }
    }

    /**
     * Check if a companion has chunk loading enabled.
     */
    public static boolean isChunkLoadingEnabled(CompanionEntity companion) {
        return Config.COMPANIONS_LOAD_CHUNKS.get() && loadedChunks.containsKey(companion.getUUID());
    }

    /**
     * Check if a block position is within the companion's loaded chunks.
     * Use this before targeting blocks for mining, building, etc.
     */
    public static boolean isBlockInLoadedChunks(CompanionEntity companion, BlockPos pos) {
        if (!Config.COMPANIONS_LOAD_CHUNKS.get()) {
            // If chunk loading is disabled, fall back to vanilla chunk loaded check
            return companion.level().hasChunkAt(pos);
        }

        UUID companionId = companion.getUUID();
        Set<ChunkPos> companionChunks = loadedChunks.get(companionId);

        if (companionChunks == null) {
            // No chunks loaded for this companion, use vanilla check
            return companion.level().hasChunkAt(pos);
        }

        ChunkPos targetChunk = new ChunkPos(pos);
        return companionChunks.contains(targetChunk);
    }

    /**
     * Check if a position is within safe working range of the companion.
     * Returns true if the block is in a loaded chunk and within reasonable distance.
     * @param companion The companion entity
     * @param pos The block position to check
     * @param maxDistance Maximum distance in blocks (use 0 for unlimited)
     */
    public static boolean isInWorkingRange(CompanionEntity companion, BlockPos pos, int maxDistance) {
        // First check if chunk is loaded
        if (!isBlockInLoadedChunks(companion, pos)) {
            return false;
        }

        // Then check distance if specified
        if (maxDistance > 0) {
            double distance = companion.blockPosition().distSqr(pos);
            return distance <= maxDistance * maxDistance;
        }

        return true;
    }

    /**
     * Get the maximum working radius in blocks based on loaded chunks.
     * This is approximately 32 blocks (2 chunks) from the center.
     */
    public static int getWorkingRadius() {
        return CHUNK_LOAD_RADIUS * 16;  // 2 * 16 = 32 blocks
    }

    /**
     * Get all loaded chunks for a companion (for debugging).
     */
    public static Set<ChunkPos> getLoadedChunks(CompanionEntity companion) {
        Set<ChunkPos> chunks = loadedChunks.get(companion.getUUID());
        return chunks != null ? Collections.unmodifiableSet(chunks) : Collections.emptySet();
    }
}
