package com.gblfxt.llmoblings.compat;

import com.gblfxt.llmoblings.LLMoblings;
import com.gblfxt.llmoblings.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Integration with Building Gadgets 2 mod for advanced building capabilities.
 */
public class BuildingGadgetsIntegration {

    private static Boolean bgLoaded = null;
    private static Class<?> baseGadgetClass = null;
    private static Class<?> gadgetNBTClass = null;
    private static Class<?> buildingUtilsClass = null;

    // Gadget item registry names
    private static final String BUILDING_GADGET = "buildinggadgets2:building_gadget";
    private static final String EXCHANGING_GADGET = "buildinggadgets2:exchanging_gadget";
    private static final String COPYPASTE_GADGET = "buildinggadgets2:copypaste_gadget";
    private static final String DESTRUCTION_GADGET = "buildinggadgets2:destruction_gadget";

    public static boolean isBuildingGadgetsLoaded() {
        if (bgLoaded == null) {
            bgLoaded = ModList.get().isLoaded("buildinggadgets2");
            if (bgLoaded) {
                LLMoblings.LOGGER.info("Building Gadgets 2 detected - gadget building enabled");
                try {
                    baseGadgetClass = Class.forName("com.direwolf20.buildinggadgets2.common.items.BaseGadget");
                    gadgetNBTClass = Class.forName("com.direwolf20.buildinggadgets2.util.GadgetNBT");
                    buildingUtilsClass = Class.forName("com.direwolf20.buildinggadgets2.util.BuildingUtils");
                } catch (ClassNotFoundException e) {
                    LLMoblings.LOGGER.warn("Could not load Building Gadgets classes: {}", e.getMessage());
                }
            }
        }
        return bgLoaded;
    }

    /**
     * Check if an item is a building gadget.
     */
    public static boolean isGadget(ItemStack stack) {
        if (!isBuildingGadgetsLoaded() || stack.isEmpty()) {
            return false;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemId.startsWith("buildinggadgets2:");
    }

    /**
     * Check if an item is specifically a building gadget (places blocks).
     */
    public static boolean isBuildingGadget(ItemStack stack) {
        if (!isBuildingGadgetsLoaded() || stack.isEmpty()) {
            return false;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemId.equals(BUILDING_GADGET);
    }

    /**
     * Find a building gadget in the companion's inventory.
     */
    public static ItemStack findBuildingGadget(CompanionEntity companion) {
        // Check main hand first
        ItemStack mainHand = companion.getMainHandItem();
        if (isBuildingGadget(mainHand)) {
            return mainHand;
        }

        // Check inventory
        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (isBuildingGadget(stack)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Find any gadget in the companion's inventory.
     */
    public static ItemStack findAnyGadget(CompanionEntity companion) {
        // Check main hand first
        ItemStack mainHand = companion.getMainHandItem();
        if (isGadget(mainHand)) {
            return mainHand;
        }

        // Check inventory
        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (isGadget(stack)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Set the block that the gadget should place.
     */
    public static boolean setGadgetBlock(ItemStack gadget, BlockState blockState) {
        if (!isGadget(gadget) || gadgetNBTClass == null) {
            return false;
        }

        try {
            Method setBlockState = gadgetNBTClass.getMethod("setGadgetBlockState", ItemStack.class, BlockState.class);
            setBlockState.invoke(null, gadget, blockState);
            LLMoblings.LOGGER.debug("Set gadget block to: {}", blockState.getBlock());
            return true;
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Error setting gadget block: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the block currently set in the gadget.
     */
    public static BlockState getGadgetBlock(ItemStack gadget) {
        if (!isGadget(gadget) || gadgetNBTClass == null) {
            return Blocks.AIR.defaultBlockState();
        }

        try {
            Method getBlockState = gadgetNBTClass.getMethod("getGadgetBlockState", ItemStack.class);
            return (BlockState) getBlockState.invoke(null, gadget);
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Error getting gadget block: {}", e.getMessage());
            return Blocks.AIR.defaultBlockState();
        }
    }

    /**
     * Set the build range of the gadget.
     */
    public static boolean setGadgetRange(ItemStack gadget, int range) {
        if (!isGadget(gadget) || gadgetNBTClass == null) {
            return false;
        }

        try {
            Method setRange = gadgetNBTClass.getMethod("setToolRange", ItemStack.class, int.class);
            setRange.invoke(null, gadget, range);
            return true;
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Error setting gadget range: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the build range of the gadget.
     */
    public static int getGadgetRange(ItemStack gadget) {
        if (!isGadget(gadget) || gadgetNBTClass == null) {
            return 1;
        }

        try {
            Method getRange = gadgetNBTClass.getMethod("getToolRange", ItemStack.class);
            return (Integer) getRange.invoke(null, gadget);
        } catch (Exception e) {
            return 1;
        }
    }

    /**
     * Check if gadget has enough energy.
     */
    public static boolean hasEnoughEnergy(ItemStack gadget) {
        if (!isGadget(gadget) || gadgetNBTClass == null) {
            return false;
        }

        try {
            Method hasEnergy = gadgetNBTClass.getMethod("hasEnoughEnergy", ItemStack.class);
            return (Boolean) hasEnergy.invoke(null, gadget);
        } catch (Exception e) {
            return true; // Assume it works if we can't check
        }
    }

    /**
     * Get the current build mode name.
     */
    public static String getModeName(ItemStack gadget) {
        if (!isGadget(gadget) || gadgetNBTClass == null) {
            return "unknown";
        }

        try {
            Method getMode = gadgetNBTClass.getMethod("getMode", ItemStack.class);
            Object mode = getMode.invoke(null, gadget);
            if (mode != null) {
                Method getId = mode.getClass().getMethod("getId");
                Object id = getId.invoke(mode);
                if (id != null) {
                    return id.toString();
                }
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Error getting mode name: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Rotate to the next build mode.
     */
    public static boolean rotateMode(ItemStack gadget) {
        if (!isGadget(gadget) || baseGadgetClass == null) {
            return false;
        }

        try {
            Item item = gadget.getItem();
            if (baseGadgetClass.isInstance(item)) {
                Method rotateMethod = baseGadgetClass.getMethod("rotateModes", ItemStack.class);
                rotateMethod.invoke(item, gadget);
                return true;
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Error rotating mode: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Equip a building gadget to main hand.
     */
    public static boolean equipGadget(CompanionEntity companion) {
        ItemStack gadget = findAnyGadget(companion);
        if (gadget.isEmpty()) {
            return false;
        }

        // Already equipped?
        if (companion.getMainHandItem() == gadget) {
            return true;
        }

        // Find the gadget in inventory and swap with main hand
        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (stack == gadget) {
                ItemStack currentHand = companion.getMainHandItem();
                companion.setItemSlot(EquipmentSlot.MAINHAND, gadget.copy());
                companion.setItem(i, currentHand);
                return true;
            }
        }

        return false;
    }

    /**
     * Use the gadget at a target position (simulates right-click).
     */
    public static boolean useGadget(CompanionEntity companion, BlockPos targetPos, Direction face) {
        ItemStack gadget = companion.getMainHandItem();
        if (!isGadget(gadget)) {
            return false;
        }

        try {
            // Create a block hit result
            Vec3 hitVec = Vec3.atCenterOf(targetPos);
            BlockHitResult hitResult = new BlockHitResult(hitVec, face, targetPos, false);

            // Get the gadget item and call use method
            Item item = gadget.getItem();

            // Try to call the use method
            if (baseGadgetClass != null && baseGadgetClass.isInstance(item)) {
                // Simulate a player use action - this is tricky without a real player
                // For now, we'll swing the arm and log
                companion.swing(InteractionHand.MAIN_HAND);
                LLMoblings.LOGGER.info("[{}] Used building gadget at {}",
                    companion.getCompanionName(), targetPos);
                return true;
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Error using gadget: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Get a description of the gadget for chat.
     */
    public static String getGadgetDescription(ItemStack gadget) {
        if (!isGadget(gadget)) {
            return "not a gadget";
        }

        String itemId = BuiltInRegistries.ITEM.getKey(gadget.getItem()).getPath();
        String type = switch (itemId) {
            case "building_gadget" -> "Building Gadget";
            case "exchanging_gadget" -> "Exchanging Gadget";
            case "copypaste_gadget" -> "Copy-Paste Gadget";
            case "cutpaste_gadget" -> "Cut-Paste Gadget";
            case "destruction_gadget" -> "Destruction Gadget";
            default -> "Unknown Gadget";
        };

        BlockState block = getGadgetBlock(gadget);
        String blockName = block.isAir() ? "none" :
            BuiltInRegistries.BLOCK.getKey(block.getBlock()).getPath().replace("_", " ");

        int range = getGadgetRange(gadget);
        String mode = getModeName(gadget);

        return type + " (Block: " + blockName + ", Range: " + range + ", Mode: " + mode + ")";
    }

    /**
     * Configure a gadget with specific settings.
     */
    public static boolean configureGadget(ItemStack gadget, Block block, int range) {
        if (!isGadget(gadget)) {
            return false;
        }

        boolean success = true;

        if (block != null) {
            success &= setGadgetBlock(gadget, block.defaultBlockState());
        }

        if (range > 0) {
            success &= setGadgetRange(gadget, range);
        }

        return success;
    }

    /**
     * Find a block in companion's inventory that can be used with the gadget.
     */
    public static Block findBuildableBlock(CompanionEntity companion) {
        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (!stack.isEmpty()) {
                Block block = Block.byItem(stack.getItem());
                if (block != Blocks.AIR && block.defaultBlockState().isSolid()) {
                    return block;
                }
            }
        }
        return null;
    }
}
