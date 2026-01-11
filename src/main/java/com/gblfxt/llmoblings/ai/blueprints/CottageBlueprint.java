package com.gblfxt.llmoblings.ai.blueprints;

import com.gblfxt.llmoblings.ai.Blueprint;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small cottage blueprint: 7x5x7 with peaked roof.
 *
 * Layout (looking from front/south):
 * - Foundation: cobblestone
 * - Corners: oak logs
 * - Walls: oak planks
 * - Windows: glass panes on sides
 * - Door: front center
 * - Roof: oak stairs (peaked)
 * - Interior: bed, torches
 */
public class CottageBlueprint extends Blueprint {

    private static final List<BlockPlacement> PLACEMENTS = new ArrayList<>();
    private static final Map<Item, Integer> MATERIALS = new HashMap<>();

    static {
        buildPlacements();
        calculateMaterials();
    }

    private static void buildPlacements() {
        // Foundation (Y=0) - 7x7 cobblestone floor
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                PLACEMENTS.add(new BlockPlacement(x, 0, z,
                    Blocks.COBBLESTONE.defaultBlockState(), PHASE_FOUNDATION));
            }
        }

        // Corner pillars (Y=1-3) - oak logs at corners
        int[][] corners = {{0, 0}, {6, 0}, {0, 6}, {6, 6}};
        for (int[] corner : corners) {
            for (int y = 1; y <= 3; y++) {
                PLACEMENTS.add(new BlockPlacement(corner[0], y, corner[1],
                    Blocks.OAK_LOG.defaultBlockState(), PHASE_CORNERS));
            }
        }

        // Walls (Y=1-2) - oak planks between corners
        // Front wall (Z=0) - leave door space at X=3
        for (int x = 1; x <= 5; x++) {
            if (x != 3) { // Door position
                PLACEMENTS.add(new BlockPlacement(x, 1, 0,
                    Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
                PLACEMENTS.add(new BlockPlacement(x, 2, 0,
                    Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
            }
        }

        // Back wall (Z=6)
        for (int x = 1; x <= 5; x++) {
            PLACEMENTS.add(new BlockPlacement(x, 1, 6,
                Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
            PLACEMENTS.add(new BlockPlacement(x, 2, 6,
                Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
        }

        // Left wall (X=0) - window at Z=3
        for (int z = 1; z <= 5; z++) {
            if (z == 3) {
                // Window position - glass pane
                PLACEMENTS.add(new BlockPlacement(0, 1, z,
                    Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
                PLACEMENTS.add(new BlockPlacement(0, 2, z,
                    Blocks.GLASS_PANE.defaultBlockState(), PHASE_WINDOWS));
            } else {
                PLACEMENTS.add(new BlockPlacement(0, 1, z,
                    Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
                PLACEMENTS.add(new BlockPlacement(0, 2, z,
                    Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
            }
        }

        // Right wall (X=6) - window at Z=3
        for (int z = 1; z <= 5; z++) {
            if (z == 3) {
                // Window position - glass pane
                PLACEMENTS.add(new BlockPlacement(6, 1, z,
                    Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
                PLACEMENTS.add(new BlockPlacement(6, 2, z,
                    Blocks.GLASS_PANE.defaultBlockState(), PHASE_WINDOWS));
            } else {
                PLACEMENTS.add(new BlockPlacement(6, 1, z,
                    Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
                PLACEMENTS.add(new BlockPlacement(6, 2, z,
                    Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
            }
        }

        // Door (Y=1-2, X=3, Z=0)
        PLACEMENTS.add(new BlockPlacement(3, 1, 0,
            Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER),
            PHASE_DOOR));
        PLACEMENTS.add(new BlockPlacement(3, 2, 0,
            Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER),
            PHASE_DOOR));

        // Wall top / roof base (Y=3) - oak planks around edge
        for (int x = 1; x <= 5; x++) {
            PLACEMENTS.add(new BlockPlacement(x, 3, 0,
                Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
            PLACEMENTS.add(new BlockPlacement(x, 3, 6,
                Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
        }
        for (int z = 1; z <= 5; z++) {
            PLACEMENTS.add(new BlockPlacement(0, 3, z,
                Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
            PLACEMENTS.add(new BlockPlacement(6, 3, z,
                Blocks.OAK_PLANKS.defaultBlockState(), PHASE_WALLS));
        }

        // Roof - sloped with stairs
        // First layer (Y=4) - stairs facing outward
        for (int z = 0; z <= 6; z++) {
            // Left side stairs (facing west/out)
            PLACEMENTS.add(new BlockPlacement(0, 4, z,
                Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST)
                    .setValue(StairBlock.HALF, Half.BOTTOM),
                PHASE_ROOF));
            // Right side stairs (facing east/out)
            PLACEMENTS.add(new BlockPlacement(6, 4, z,
                Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST)
                    .setValue(StairBlock.HALF, Half.BOTTOM),
                PHASE_ROOF));
        }

        // Second layer (Y=4) - planks in middle
        for (int x = 1; x <= 5; x++) {
            for (int z = 0; z <= 6; z++) {
                PLACEMENTS.add(new BlockPlacement(x, 4, z,
                    Blocks.OAK_PLANKS.defaultBlockState(), PHASE_ROOF));
            }
        }

        // Peak layer (Y=5) - stairs and ridge
        for (int z = 0; z <= 6; z++) {
            // Left side stairs
            PLACEMENTS.add(new BlockPlacement(1, 5, z,
                Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST)
                    .setValue(StairBlock.HALF, Half.BOTTOM),
                PHASE_ROOF));
            // Right side stairs
            PLACEMENTS.add(new BlockPlacement(5, 5, z,
                Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST)
                    .setValue(StairBlock.HALF, Half.BOTTOM),
                PHASE_ROOF));
            // Ridge in middle
            for (int x = 2; x <= 4; x++) {
                PLACEMENTS.add(new BlockPlacement(x, 5, z,
                    Blocks.OAK_PLANKS.defaultBlockState(), PHASE_ROOF));
            }
        }

        // Top ridge (Y=6)
        for (int z = 0; z <= 6; z++) {
            PLACEMENTS.add(new BlockPlacement(2, 6, z,
                Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST)
                    .setValue(StairBlock.HALF, Half.BOTTOM),
                PHASE_ROOF));
            PLACEMENTS.add(new BlockPlacement(3, 6, z,
                Blocks.OAK_SLAB.defaultBlockState(), PHASE_ROOF));
            PLACEMENTS.add(new BlockPlacement(4, 6, z,
                Blocks.OAK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST)
                    .setValue(StairBlock.HALF, Half.BOTTOM),
                PHASE_ROOF));
        }

        // Interior - bed in back corner
        PLACEMENTS.add(new BlockPlacement(5, 1, 5,
            Blocks.RED_BED.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.BED_PART, BedPart.FOOT),
            PHASE_INTERIOR));
        PLACEMENTS.add(new BlockPlacement(5, 1, 4,
            Blocks.RED_BED.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.BED_PART, BedPart.HEAD),
            PHASE_INTERIOR));

        // Torches on walls
        PLACEMENTS.add(new BlockPlacement(1, 2, 1,
            Blocks.WALL_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH),
            PHASE_INTERIOR));
        PLACEMENTS.add(new BlockPlacement(5, 2, 1,
            Blocks.WALL_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH),
            PHASE_INTERIOR));
        PLACEMENTS.add(new BlockPlacement(1, 2, 5,
            Blocks.WALL_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
            PHASE_INTERIOR));
    }

    private static void calculateMaterials() {
        // Count blocks by type
        Map<BlockState, Integer> blockCounts = new HashMap<>();
        for (BlockPlacement p : PLACEMENTS) {
            blockCounts.merge(p.state(), 1, Integer::sum);
        }

        // Convert to item requirements
        MATERIALS.put(Items.COBBLESTONE, 49);  // 7x7 foundation
        MATERIALS.put(Items.OAK_LOG, 12);      // 4 corners x 3 high
        MATERIALS.put(Items.OAK_PLANKS, 80);   // Walls + roof
        MATERIALS.put(Items.GLASS_PANE, 2);    // Windows
        MATERIALS.put(Items.OAK_DOOR, 1);      // Door
        MATERIALS.put(Items.OAK_STAIRS, 42);   // Roof slopes
        MATERIALS.put(Items.OAK_SLAB, 7);      // Ridge
        MATERIALS.put(Items.RED_BED, 1);       // Bed
        MATERIALS.put(Items.TORCH, 3);         // Torches
    }

    @Override
    public String getName() {
        return "cottage";
    }

    @Override
    public List<BlockPlacement> getPlacements() {
        return PLACEMENTS;
    }

    @Override
    public Map<Item, Integer> getRequiredMaterials() {
        return MATERIALS;
    }

    @Override
    public int getWidth() {
        return 7;
    }

    @Override
    public int getHeight() {
        return 7; // Including roof peak
    }

    @Override
    public int getDepth() {
        return 7;
    }
}
