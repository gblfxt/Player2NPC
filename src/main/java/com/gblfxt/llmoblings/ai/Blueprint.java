package com.gblfxt.llmoblings.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * Base class for structure blueprints that companions can build.
 */
public abstract class Blueprint {

    /**
     * A single block placement in the blueprint.
     */
    public record BlockPlacement(
        int x, int y, int z,           // Relative position from origin
        BlockState state,               // The block state to place
        int phase                       // Build phase (lower = earlier)
    ) {
        public BlockPos getWorldPos(BlockPos origin, Direction facing) {
            // Rotate the position based on facing direction
            int rx = x, rz = z;
            switch (facing) {
                case EAST -> { rx = -z; rz = x; }
                case SOUTH -> { rx = -x; rz = -z; }
                case WEST -> { rx = z; rz = -x; }
                default -> {} // NORTH is default, no rotation
            }
            return origin.offset(rx, y, rz);
        }
    }

    // Build phases
    public static final int PHASE_FOUNDATION = 0;
    public static final int PHASE_CORNERS = 1;
    public static final int PHASE_WALLS = 2;
    public static final int PHASE_WINDOWS = 3;
    public static final int PHASE_DOOR = 4;
    public static final int PHASE_ROOF = 5;
    public static final int PHASE_INTERIOR = 6;

    /**
     * Get the name of this structure.
     */
    public abstract String getName();

    /**
     * Get all block placements for this blueprint.
     */
    public abstract List<BlockPlacement> getPlacements();

    /**
     * Get required materials for this blueprint.
     */
    public abstract Map<Item, Integer> getRequiredMaterials();

    /**
     * Get the width of the structure (X axis).
     */
    public abstract int getWidth();

    /**
     * Get the height of the structure (Y axis).
     */
    public abstract int getHeight();

    /**
     * Get the depth of the structure (Z axis).
     */
    public abstract int getDepth();

    /**
     * Get placements for a specific phase.
     */
    public List<BlockPlacement> getPlacementsForPhase(int phase) {
        return getPlacements().stream()
            .filter(p -> p.phase() == phase)
            .toList();
    }

    /**
     * Get total block count.
     */
    public int getTotalBlocks() {
        return getPlacements().size();
    }
}
