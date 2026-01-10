package com.gblfxt.player2npc.ai;

import com.gblfxt.player2npc.Player2NPC;
import com.gblfxt.player2npc.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Provides ultimine-style mining capabilities for companions:
 * - Vein mining (connected ores)
 * - Tree felling (logs + leaves)
 * - Crop harvesting with replant
 */
public class UltimineHelper {

    // Maximum blocks to mine in one ultimine operation
    private static final int MAX_VEIN_SIZE = 64;
    private static final int MAX_TREE_SIZE = 128;
    private static final int MAX_CROP_AREA = 9; // 3x3

    /**
     * Find all connected blocks of the same type (vein mining).
     */
    public static List<BlockPos> findConnectedBlocks(Level level, BlockPos start, int maxBlocks) {
        BlockState startState = level.getBlockState(start);
        Block targetBlock = startState.getBlock();

        if (targetBlock == Blocks.AIR) {
            return Collections.emptyList();
        }

        List<BlockPos> connected = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && connected.size() < maxBlocks) {
            BlockPos current = queue.poll();
            BlockState currentState = level.getBlockState(current);

            // Check if this block matches (same block type or similar ore)
            if (blocksMatch(targetBlock, currentState.getBlock())) {
                connected.add(current);

                // Check all 26 neighbors (including diagonals for better vein detection)
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;

                            BlockPos neighbor = current.offset(dx, dy, dz);
                            if (!visited.contains(neighbor)) {
                                visited.add(neighbor);
                                BlockState neighborState = level.getBlockState(neighbor);
                                if (blocksMatch(targetBlock, neighborState.getBlock())) {
                                    queue.add(neighbor);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sort by Y (bottom to top for ores, top to bottom for logs)
        String blockId = BuiltInRegistries.BLOCK.getKey(targetBlock).getPath();
        if (blockId.contains("log")) {
            connected.sort((a, b) -> Integer.compare(b.getY(), a.getY())); // Top first
        } else {
            connected.sort(Comparator.comparingInt(BlockPos::getY)); // Bottom first
        }

        return connected;
    }

    /**
     * Find entire tree from a starting log position.
     * Returns logs first (sorted top to bottom), then leaves.
     */
    public static List<BlockPos> findTree(Level level, BlockPos start) {
        BlockState startState = level.getBlockState(start);
        String startBlockId = BuiltInRegistries.BLOCK.getKey(startState.getBlock()).getPath();

        if (!startBlockId.contains("log") && !startBlockId.contains("wood")) {
            return Collections.emptyList();
        }

        // Determine the wood type prefix (oak, birch, spruce, etc.)
        String woodType = extractWoodType(startBlockId);

        List<BlockPos> logs = new ArrayList<>();
        List<BlockPos> leaves = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && (logs.size() + leaves.size()) < MAX_TREE_SIZE) {
            BlockPos current = queue.poll();
            BlockState currentState = level.getBlockState(current);
            String currentBlockId = BuiltInRegistries.BLOCK.getKey(currentState.getBlock()).getPath();

            boolean isLog = currentBlockId.contains("log") || currentBlockId.contains("wood");
            boolean isLeaf = currentBlockId.contains("leaves");

            // Must match wood type or be generic
            boolean matchesType = woodType.isEmpty() ||
                                  currentBlockId.contains(woodType) ||
                                  isGenericTreeBlock(currentBlockId);

            if ((isLog || isLeaf) && matchesType) {
                if (isLog) {
                    logs.add(current);
                } else {
                    leaves.add(current);
                }

                // Search radius depends on block type
                int searchRadius = isLog ? 1 : 2;

                for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                    for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                        for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;

                            BlockPos neighbor = current.offset(dx, dy, dz);
                            if (!visited.contains(neighbor)) {
                                visited.add(neighbor);
                                BlockState neighborState = level.getBlockState(neighbor);
                                String neighborId = BuiltInRegistries.BLOCK.getKey(neighborState.getBlock()).getPath();

                                if ((neighborId.contains("log") || neighborId.contains("leaves") ||
                                     neighborId.contains("wood")) &&
                                    (woodType.isEmpty() || neighborId.contains(woodType) ||
                                     isGenericTreeBlock(neighborId))) {
                                    queue.add(neighbor);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sort logs top to bottom (so tree falls naturally)
        logs.sort((a, b) -> Integer.compare(b.getY(), a.getY()));

        // Combine: logs first, then leaves
        List<BlockPos> result = new ArrayList<>(logs);
        result.addAll(leaves);

        return result;
    }

    /**
     * Find mature crops in an area around the starting position.
     */
    public static List<BlockPos> findMatureCrops(Level level, BlockPos center, int radius) {
        List<BlockPos> crops = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos pos = center.offset(x, 0, z);
                BlockState state = level.getBlockState(pos);

                if (isMatureCrop(state)) {
                    crops.add(pos);
                }
            }
        }

        return crops;
    }

    /**
     * Check if a crop is fully grown.
     */
    public static boolean isMatureCrop(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }

        // Check for other harvestable plants
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();

        // Pumpkin, melon (always harvestable)
        if (blockId.equals("pumpkin") || blockId.equals("melon")) {
            return true;
        }

        // Sweet berries (check age)
        if (blockId.equals("sweet_berry_bush")) {
            return state.hasProperty(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) &&
                   state.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) >= 2;
        }

        return false;
    }

    /**
     * Harvest a crop and replant if possible.
     */
    public static boolean harvestAndReplant(ServerLevel level, BlockPos pos, CompanionEntity companion) {
        BlockState state = level.getBlockState(pos);

        if (!isMatureCrop(state)) {
            return false;
        }

        Block block = state.getBlock();
        String blockId = BuiltInRegistries.BLOCK.getKey(block).getPath();

        // Get drops
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, companion, ItemStack.EMPTY);

        // Break the block
        level.destroyBlock(pos, false, companion);

        // Spawn drops
        for (ItemStack drop : drops) {
            ItemEntity itemEntity = new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            itemEntity.setDefaultPickUpDelay();
            level.addFreshEntity(itemEntity);
        }

        // Replant if it's a replantable crop
        if (block instanceof CropBlock) {
            // Find seed in drops or companion inventory
            ItemStack seed = findSeedFor(block, drops, companion);
            if (!seed.isEmpty()) {
                // Replant
                level.setBlockAndUpdate(pos, block.defaultBlockState());
                seed.shrink(1);
                return true;
            }
        }

        return true;
    }

    /**
     * Find appropriate seed for replanting.
     */
    private static ItemStack findSeedFor(Block crop, List<ItemStack> drops, CompanionEntity companion) {
        String cropId = BuiltInRegistries.BLOCK.getKey(crop).getPath();

        // Map crops to their seeds
        String seedId = switch (cropId) {
            case "wheat" -> "wheat_seeds";
            case "carrots" -> "carrot";
            case "potatoes" -> "potato";
            case "beetroots" -> "beetroot_seeds";
            case "nether_wart" -> "nether_wart";
            default -> null;
        };

        if (seedId == null) return ItemStack.EMPTY;

        // Check drops first
        for (ItemStack drop : drops) {
            String itemId = BuiltInRegistries.ITEM.getKey(drop.getItem()).getPath();
            if (itemId.equals(seedId) && drop.getCount() > 1) {
                // Keep one for replanting
                return drop;
            }
        }

        // Check companion inventory
        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (itemId.equals(seedId)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Get the best tool for mining a block type.
     */
    public static ItemStack getBestToolFor(CompanionEntity companion, BlockState state) {
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();

        // Determine tool type needed
        boolean needsPickaxe = blockId.contains("ore") || blockId.contains("stone") ||
                               blockId.contains("cobble") || blockId.contains("brick") ||
                               blockId.contains("obsidian") || blockId.contains("netherrack") ||
                               blockId.contains("deepslate") || blockId.contains("basalt");

        boolean needsAxe = blockId.contains("log") || blockId.contains("wood") ||
                          blockId.contains("plank") || blockId.contains("fence") ||
                          blockId.contains("door") || blockId.contains("chest");

        boolean needsShovel = blockId.contains("dirt") || blockId.contains("sand") ||
                              blockId.contains("gravel") || blockId.contains("clay") ||
                              blockId.contains("soul") || blockId.contains("snow");

        boolean needsHoe = blockId.contains("leaves") || blockId.contains("hay") ||
                          blockId.contains("sponge") || blockId.contains("moss");

        // Search inventory for best matching tool
        ItemStack bestTool = ItemStack.EMPTY;
        int bestTier = -1;

        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            int tier = getToolTier(item);

            boolean matches = (needsPickaxe && item instanceof PickaxeItem) ||
                             (needsAxe && item instanceof AxeItem) ||
                             (needsShovel && item instanceof ShovelItem) ||
                             (needsHoe && item instanceof HoeItem);

            if (matches && tier > bestTier) {
                bestTool = stack;
                bestTier = tier;
            }
        }

        // Also check main hand
        ItemStack mainHand = companion.getMainHandItem();
        if (!mainHand.isEmpty()) {
            Item item = mainHand.getItem();
            int tier = getToolTier(item);

            boolean matches = (needsPickaxe && item instanceof PickaxeItem) ||
                             (needsAxe && item instanceof AxeItem) ||
                             (needsShovel && item instanceof ShovelItem) ||
                             (needsHoe && item instanceof HoeItem);

            if (matches && tier > bestTier) {
                bestTool = mainHand;
            }
        }

        return bestTool;
    }

    /**
     * Equip the best tool for a block type.
     */
    public static boolean equipBestTool(CompanionEntity companion, BlockState state) {
        ItemStack bestTool = getBestToolFor(companion, state);

        if (bestTool.isEmpty()) {
            Player2NPC.LOGGER.debug("[{}] No suitable tool found for {}",
                companion.getCompanionName(), state.getBlock());
            return false;
        }

        // Already equipped? Use ItemStack comparison instead of reference
        ItemStack currentHand = companion.getMainHandItem();
        if (ItemStack.isSameItemSameComponents(currentHand, bestTool)) {
            Player2NPC.LOGGER.debug("[{}] Already holding best tool: {}",
                companion.getCompanionName(), bestTool.getHoverName().getString());
            return true;
        }

        // Find tool in inventory and swap
        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack slotItem = companion.getItem(i);
            if (ItemStack.isSameItemSameComponents(slotItem, bestTool)) {
                // Put current hand item into inventory slot
                companion.setItem(i, currentHand.isEmpty() ? ItemStack.EMPTY : currentHand.copy());
                // Equip the tool
                companion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, slotItem.copy());

                Player2NPC.LOGGER.info("[{}] Equipped {} for mining",
                    companion.getCompanionName(), bestTool.getHoverName().getString());
                return true;
            }
        }

        Player2NPC.LOGGER.debug("[{}] Could not find tool {} in inventory to equip",
            companion.getCompanionName(), bestTool.getHoverName().getString());
        return false;
    }

    /**
     * Get tool tier (higher = better).
     */
    private static int getToolTier(Item item) {
        if (item instanceof TieredItem tiered) {
            return switch (tiered.getTier()) {
                case net.minecraft.world.item.Tiers.WOOD -> 0;
                case net.minecraft.world.item.Tiers.STONE -> 1;
                case net.minecraft.world.item.Tiers.IRON -> 2;
                case net.minecraft.world.item.Tiers.DIAMOND -> 3;
                case net.minecraft.world.item.Tiers.NETHERITE -> 4;
                case net.minecraft.world.item.Tiers.GOLD -> 0; // Fast but weak
                default -> {
                    // Modded tiers - check by name
                    String tierName = tiered.getTier().toString().toLowerCase();
                    if (tierName.contains("netherite") || tierName.contains("allthemodium")) yield 5;
                    if (tierName.contains("vibranium") || tierName.contains("unobtainium")) yield 6;
                    yield 2; // Default to iron-ish
                }
            };
        }
        return -1;
    }

    /**
     * Check if two blocks should be considered the same for vein mining.
     */
    private static boolean blocksMatch(Block target, Block candidate) {
        if (target == candidate) return true;

        String targetId = BuiltInRegistries.BLOCK.getKey(target).getPath();
        String candidateId = BuiltInRegistries.BLOCK.getKey(candidate).getPath();

        // Match deepslate variants with regular ores
        if (targetId.contains("ore") && candidateId.contains("ore")) {
            String targetOre = targetId.replace("deepslate_", "");
            String candidateOre = candidateId.replace("deepslate_", "");
            return targetOre.equals(candidateOre);
        }

        // Match all log variants of same wood type
        if (targetId.contains("log") && candidateId.contains("log")) {
            String targetWood = extractWoodType(targetId);
            String candidateWood = extractWoodType(candidateId);
            return targetWood.equals(candidateWood);
        }

        return false;
    }

    /**
     * Extract wood type from a block ID (e.g., "oak_log" -> "oak").
     */
    private static String extractWoodType(String blockId) {
        String[] woodTypes = {"oak", "birch", "spruce", "jungle", "acacia", "dark_oak",
                              "mangrove", "cherry", "bamboo", "crimson", "warped"};

        for (String type : woodTypes) {
            if (blockId.contains(type)) {
                return type;
            }
        }

        return "";
    }

    /**
     * Check if block is a generic tree block (matches any tree).
     */
    private static boolean isGenericTreeBlock(String blockId) {
        return blockId.equals("log") || blockId.equals("leaves") || blockId.equals("wood");
    }

    /**
     * Calculate mining time bonus from tool.
     */
    public static float getMiningSpeedMultiplier(CompanionEntity companion, BlockState state) {
        ItemStack tool = companion.getMainHandItem();
        if (tool.isEmpty()) return 1.0f;

        Item item = tool.getItem();

        // Check if tool is appropriate
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        boolean correctTool = false;

        if (blockId.contains("ore") || blockId.contains("stone")) {
            correctTool = item instanceof PickaxeItem;
        } else if (blockId.contains("log") || blockId.contains("wood")) {
            correctTool = item instanceof AxeItem;
        } else if (blockId.contains("dirt") || blockId.contains("sand")) {
            correctTool = item instanceof ShovelItem;
        }

        if (!correctTool) return 1.0f;

        // Get tier multiplier
        int tier = getToolTier(item);
        return switch (tier) {
            case 0 -> 2.0f;  // Wood
            case 1 -> 4.0f;  // Stone
            case 2 -> 6.0f;  // Iron
            case 3 -> 8.0f;  // Diamond
            case 4 -> 9.0f;  // Netherite
            case 5 -> 10.0f; // Allthemodium
            case 6 -> 12.0f; // Vibranium/Unobtainium
            default -> 1.0f;
        };
    }

    /**
     * Check if companion has silk touch.
     */
    public static boolean hasSilkTouch(CompanionEntity companion) {
        ItemStack tool = companion.getMainHandItem();
        if (tool.isEmpty()) return false;

        // Check for silk touch enchantment
        return tool.getEnchantmentLevel(
            companion.level().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH)
        ) > 0;
    }

    /**
     * Get fortune level of current tool.
     */
    public static int getFortuneLevel(CompanionEntity companion) {
        ItemStack tool = companion.getMainHandItem();
        if (tool.isEmpty()) return 0;

        return tool.getEnchantmentLevel(
            companion.level().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE)
        );
    }
}
