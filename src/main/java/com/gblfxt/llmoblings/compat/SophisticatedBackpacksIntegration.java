package com.gblfxt.llmoblings.compat;

import com.gblfxt.llmoblings.LLMoblings;
import com.gblfxt.llmoblings.entity.CompanionEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Integration with Sophisticated Backpacks mod for portable storage.
 */
public class SophisticatedBackpacksIntegration {

    private static Boolean sbLoaded = null;
    private static Class<?> backpackItemClass = null;
    private static Class<?> backpackWrapperClass = null;

    public static boolean isSophisticatedBackpacksLoaded() {
        if (sbLoaded == null) {
            sbLoaded = ModList.get().isLoaded("sophisticatedbackpacks");
            if (sbLoaded) {
                LLMoblings.LOGGER.info("Sophisticated Backpacks detected - backpack support enabled");
                try {
                    backpackItemClass = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.item.BackpackItem");
                    backpackWrapperClass = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper");
                } catch (ClassNotFoundException e) {
                    LLMoblings.LOGGER.warn("Could not load Sophisticated Backpacks classes: {}", e.getMessage());
                }
            }
        }
        return sbLoaded;
    }

    /**
     * Check if an item is a backpack.
     */
    public static boolean isBackpack(ItemStack stack) {
        if (!isSophisticatedBackpacksLoaded() || stack.isEmpty()) {
            return false;
        }

        // Check by class
        if (backpackItemClass != null && backpackItemClass.isInstance(stack.getItem())) {
            return true;
        }

        // Fallback: check by registry name
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return itemId.startsWith("sophisticatedbackpacks:") && itemId.contains("backpack");
    }

    /**
     * Find a backpack in the companion's inventory.
     */
    public static ItemStack findBackpack(CompanionEntity companion) {
        // Check main hand first
        ItemStack mainHand = companion.getMainHandItem();
        if (isBackpack(mainHand)) {
            return mainHand;
        }

        // Check off hand
        ItemStack offHand = companion.getOffhandItem();
        if (isBackpack(offHand)) {
            return offHand;
        }

        // Check inventory
        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (isBackpack(stack)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Find the slot index of a backpack in companion's inventory.
     */
    public static int findBackpackSlot(CompanionEntity companion) {
        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (isBackpack(stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get the backpack wrapper which provides access to inventory.
     */
    private static Object getBackpackWrapper(ItemStack backpack) {
        if (!isBackpack(backpack) || backpackWrapperClass == null) {
            return null;
        }

        try {
            // Try to get wrapper via static method: BackpackWrapper.fromStack(stack)
            Method fromStack = backpackWrapperClass.getMethod("fromStack", ItemStack.class);
            return fromStack.invoke(null, backpack);
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Could not get backpack wrapper via fromStack: {}", e.getMessage());
        }

        // Try alternative approach - get from item
        try {
            Item item = backpack.getItem();
            for (Method method : item.getClass().getMethods()) {
                if (method.getName().contains("getWrapper") || method.getName().contains("getInventory")) {
                    if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == ItemStack.class) {
                        return method.invoke(item, backpack);
                    }
                }
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Could not get backpack wrapper via item method: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get the inventory handler from a backpack wrapper.
     */
    private static Object getInventoryHandler(Object wrapper) {
        if (wrapper == null) return null;

        try {
            // Try getInventoryHandler() method
            Method getInventory = wrapper.getClass().getMethod("getInventoryHandler");
            return getInventory.invoke(wrapper);
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Could not get inventory handler: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get the number of slots in a backpack.
     */
    public static int getBackpackSlots(ItemStack backpack) {
        Object wrapper = getBackpackWrapper(backpack);
        Object inventory = getInventoryHandler(wrapper);

        if (inventory == null) {
            // Estimate based on backpack type
            String itemId = BuiltInRegistries.ITEM.getKey(backpack.getItem()).getPath();
            if (itemId.contains("netherite")) return 81;
            if (itemId.contains("diamond")) return 72;
            if (itemId.contains("gold")) return 54;
            if (itemId.contains("iron")) return 45;
            if (itemId.contains("copper")) return 36;
            return 27; // Default leather backpack
        }

        try {
            Method getSlots = inventory.getClass().getMethod("getSlots");
            return (Integer) getSlots.invoke(inventory);
        } catch (Exception e) {
            return 27;
        }
    }

    /**
     * Get an item from a backpack slot.
     */
    public static ItemStack getBackpackItem(ItemStack backpack, int slot) {
        Object wrapper = getBackpackWrapper(backpack);
        Object inventory = getInventoryHandler(wrapper);

        if (inventory == null) return ItemStack.EMPTY;

        try {
            Method getStackInSlot = inventory.getClass().getMethod("getStackInSlot", int.class);
            return (ItemStack) getStackInSlot.invoke(inventory, slot);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Set an item in a backpack slot.
     */
    public static boolean setBackpackItem(ItemStack backpack, int slot, ItemStack toSet) {
        Object wrapper = getBackpackWrapper(backpack);
        Object inventory = getInventoryHandler(wrapper);

        if (inventory == null) return false;

        try {
            Method setStackInSlot = inventory.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
            setStackInSlot.invoke(inventory, slot, toSet);
            return true;
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Could not set backpack item: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Insert an item into a backpack (finds first available slot).
     */
    public static ItemStack insertIntoBackpack(ItemStack backpack, ItemStack toInsert) {
        if (toInsert.isEmpty()) return ItemStack.EMPTY;

        Object wrapper = getBackpackWrapper(backpack);
        Object inventory = getInventoryHandler(wrapper);

        if (inventory == null) {
            LLMoblings.LOGGER.debug("Could not get backpack inventory for insertion");
            return toInsert;
        }

        try {
            // Try insertItem method if available
            Method insertItem = null;
            for (Method m : inventory.getClass().getMethods()) {
                if (m.getName().equals("insertItem") && m.getParameterCount() >= 2) {
                    insertItem = m;
                    break;
                }
            }

            if (insertItem != null) {
                // insertItem(slot, stack, simulate)
                int slots = getBackpackSlots(backpack);
                ItemStack remaining = toInsert.copy();

                for (int slot = 0; slot < slots && !remaining.isEmpty(); slot++) {
                    if (insertItem.getParameterCount() == 3) {
                        remaining = (ItemStack) insertItem.invoke(inventory, slot, remaining, false);
                    } else {
                        remaining = (ItemStack) insertItem.invoke(inventory, slot, remaining);
                    }
                }
                return remaining;
            }

            // Fallback: manual slot-by-slot insertion
            int slots = getBackpackSlots(backpack);
            ItemStack remaining = toInsert.copy();

            for (int slot = 0; slot < slots && !remaining.isEmpty(); slot++) {
                ItemStack inSlot = getBackpackItem(backpack, slot);

                if (inSlot.isEmpty()) {
                    // Empty slot - put item here
                    setBackpackItem(backpack, slot, remaining.copy());
                    remaining = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(inSlot, remaining)) {
                    // Same item - try to stack
                    int space = inSlot.getMaxStackSize() - inSlot.getCount();
                    if (space > 0) {
                        int toMove = Math.min(space, remaining.getCount());
                        inSlot.grow(toMove);
                        remaining.shrink(toMove);
                        setBackpackItem(backpack, slot, inSlot);
                    }
                }
            }

            return remaining;

        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Error inserting into backpack: {}", e.getMessage());
            return toInsert;
        }
    }

    /**
     * Extract an item from a backpack by item type.
     */
    public static ItemStack extractFromBackpack(ItemStack backpack, Item itemType, int maxCount) {
        int slots = getBackpackSlots(backpack);
        ItemStack extracted = ItemStack.EMPTY;
        int remaining = maxCount;

        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack inSlot = getBackpackItem(backpack, slot);
            if (!inSlot.isEmpty() && inSlot.getItem() == itemType) {
                int toTake = Math.min(inSlot.getCount(), remaining);

                if (extracted.isEmpty()) {
                    extracted = inSlot.copy();
                    extracted.setCount(toTake);
                } else {
                    extracted.grow(toTake);
                }

                inSlot.shrink(toTake);
                if (inSlot.isEmpty()) {
                    setBackpackItem(backpack, slot, ItemStack.EMPTY);
                } else {
                    setBackpackItem(backpack, slot, inSlot);
                }

                remaining -= toTake;
            }
        }

        return extracted;
    }

    /**
     * Count items in a backpack.
     */
    public static int countItemsInBackpack(ItemStack backpack) {
        int slots = getBackpackSlots(backpack);
        int count = 0;

        for (int slot = 0; slot < slots; slot++) {
            ItemStack inSlot = getBackpackItem(backpack, slot);
            if (!inSlot.isEmpty()) {
                count += inSlot.getCount();
            }
        }

        return count;
    }

    /**
     * Count used slots in a backpack.
     */
    public static int countUsedSlots(ItemStack backpack) {
        int slots = getBackpackSlots(backpack);
        int used = 0;

        for (int slot = 0; slot < slots; slot++) {
            ItemStack inSlot = getBackpackItem(backpack, slot);
            if (!inSlot.isEmpty()) {
                used++;
            }
        }

        return used;
    }

    /**
     * Get a list of items in the backpack (summarized).
     */
    public static List<ItemStack> getBackpackContents(ItemStack backpack) {
        List<ItemStack> contents = new ArrayList<>();
        int slots = getBackpackSlots(backpack);

        for (int slot = 0; slot < slots; slot++) {
            ItemStack inSlot = getBackpackItem(backpack, slot);
            if (!inSlot.isEmpty()) {
                contents.add(inSlot.copy());
            }
        }

        return contents;
    }

    /**
     * Store all non-essential items from companion inventory into backpack.
     */
    public static int storeItemsInBackpack(CompanionEntity companion, ItemStack backpack, boolean keepGear) {
        int stored = 0;

        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (stack.isEmpty() || stack == backpack) continue;

            // Skip gear if requested
            if (keepGear) {
                Item item = stack.getItem();
                if (item instanceof net.minecraft.world.item.SwordItem ||
                    item instanceof net.minecraft.world.item.AxeItem ||
                    item instanceof net.minecraft.world.item.PickaxeItem ||
                    item instanceof net.minecraft.world.item.ArmorItem ||
                    isBackpack(stack)) {
                    continue;
                }
            }

            // Try to insert into backpack
            ItemStack remaining = insertIntoBackpack(backpack, stack);
            if (remaining.isEmpty()) {
                companion.setItem(i, ItemStack.EMPTY);
                stored++;
            } else if (remaining.getCount() < stack.getCount()) {
                companion.setItem(i, remaining);
                stored++;
            }
        }

        return stored;
    }

    /**
     * Get a description of the backpack for chat.
     */
    public static String getBackpackDescription(ItemStack backpack) {
        if (!isBackpack(backpack)) {
            return "not a backpack";
        }

        String itemId = BuiltInRegistries.ITEM.getKey(backpack.getItem()).getPath();
        String tier = "Leather";
        if (itemId.contains("netherite")) tier = "Netherite";
        else if (itemId.contains("diamond")) tier = "Diamond";
        else if (itemId.contains("gold")) tier = "Gold";
        else if (itemId.contains("iron")) tier = "Iron";
        else if (itemId.contains("copper")) tier = "Copper";

        int slots = getBackpackSlots(backpack);
        int used = countUsedSlots(backpack);
        int items = countItemsInBackpack(backpack);

        return tier + " Backpack (" + used + "/" + slots + " slots, " + items + " items)";
    }

    /**
     * Equip backpack to back slot if Curios is available, otherwise keep in inventory.
     */
    public static boolean equipBackpack(CompanionEntity companion) {
        ItemStack backpack = findBackpack(companion);
        if (backpack.isEmpty()) {
            return false;
        }

        // For now, just ensure backpack is in a good position (first inventory slot)
        // Curios integration could be added later for back slot equipping
        int slot = findBackpackSlot(companion);
        if (slot > 0) {
            // Move to first slot for easy access
            ItemStack first = companion.getItem(0);
            companion.setItem(0, backpack);
            companion.setItem(slot, first);
        }

        return true;
    }
}
