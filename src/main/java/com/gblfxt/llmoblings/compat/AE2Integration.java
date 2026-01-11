package com.gblfxt.llmoblings.compat;

import com.gblfxt.llmoblings.LLMoblings;
import com.gblfxt.llmoblings.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Integration with Applied Energistics 2 for accessing ME storage networks.
 * Uses reflection to avoid hard dependency on AE2.
 */
public class AE2Integration {

    private static Boolean ae2Loaded = null;

    public static boolean isAE2Loaded() {
        if (ae2Loaded == null) {
            ae2Loaded = ModList.get().isLoaded("ae2");
            if (ae2Loaded) {
                LLMoblings.LOGGER.info("AE2 detected - ME network integration enabled");
            }
        }
        return ae2Loaded;
    }

    /**
     * Find ME network access points (terminals preferred) near the companion.
     * Prioritizes actual terminals over buses and interfaces.
     */
    public static List<BlockPos> findMEAccessPoints(Level level, BlockPos center, int radius) {
        List<BlockPos> terminals = new ArrayList<>();
        List<BlockPos> otherAccess = new ArrayList<>();

        if (!isAE2Loaded()) {
            return terminals;
        }

        try {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -5; y <= 5; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos pos = center.offset(x, y, z);
                        BlockEntity be = level.getBlockEntity(pos);

                        if (be != null) {
                            int priority = getMEAccessPriority(be);
                            if (priority == 1) {
                                terminals.add(pos);  // High priority - terminals
                            } else if (priority == 2) {
                                otherAccess.add(pos);  // Lower priority - interfaces, chests
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Error scanning for ME access points: {}", e.getMessage());
        }

        // Return terminals first, then other access points
        if (!terminals.isEmpty()) {
            LLMoblings.LOGGER.info("AE2: Found {} terminals", terminals.size());
            return terminals;
        }
        if (!otherAccess.isEmpty()) {
            LLMoblings.LOGGER.info("AE2: No terminals, using {} other access points", otherAccess.size());
        }
        return otherAccess;
    }

    /**
     * Get priority of ME access point.
     * Returns: 1 = terminal (best), 2 = interface/chest/cable, 0 = not an access point
     */
    private static int getMEAccessPriority(BlockEntity be) {
        String className = be.getClass().getName().toLowerCase();
        String simpleName = be.getClass().getSimpleName();

        if (!className.contains("appeng") && !className.contains("ae2")) {
            return 0;
        }

        // Skip import/export buses - they're one-way and not good for retrieval
        // But DON'T skip CableBusBlockEntity - that's where terminals live!
        if ((className.contains("import") || className.contains("export")) &&
            !className.contains("cablebus")) {
            LLMoblings.LOGGER.debug("AE2: Skipping import/export: {}", simpleName);
            return 0;
        }

        // Priority 1: CableBusBlockEntity - this is where terminals, panels, etc. are attached
        // In AE2, terminals are "parts" attached to cable buses, not standalone blocks
        if (className.contains("cablebus")) {
            // Check if this cable has a terminal part attached
            if (hasTerminalPart(be)) {
                LLMoblings.LOGGER.info("AE2: Found TERMINAL (cable with terminal part): {}", simpleName);
                return 1;
            }
            // Even without terminal, cable buses can access the grid
            LLMoblings.LOGGER.debug("AE2: Found cable bus (no terminal): {}", simpleName);
            return 2;
        }

        // Priority 1: Standalone terminals (if any mod adds them)
        if (className.contains("terminal") || className.contains("craftingmonitor")) {
            LLMoblings.LOGGER.info("AE2: Found TERMINAL: {}", simpleName);
            return 1;
        }

        // Priority 2: ME Chests, Drives (can access grid)
        if (className.contains("mechest") || className.contains("mchest") ||
            className.contains("chest") || className.contains("drive")) {
            LLMoblings.LOGGER.info("AE2: Found chest/drive: {}", simpleName);
            return 2;
        }

        // Priority 2: Generic interface
        if (className.contains("interface") || className.contains("pattern")) {
            LLMoblings.LOGGER.info("AE2: Found interface/pattern: {}", simpleName);
            return 2;
        }

        return 0;
    }

    /**
     * Check if a CableBusBlockEntity has a terminal part attached.
     */
    private static boolean hasTerminalPart(BlockEntity be) {
        try {
            // Try to get the cable bus and check its parts
            var getPart = be.getClass().getMethod("getPart", Class.forName("net.minecraft.core.Direction"));

            // Check all 6 sides + center (null)
            Object[] directions = new Object[]{null};  // Start with center/no direction
            try {
                Class<?> directionClass = Class.forName("net.minecraft.core.Direction");
                Object[] enumValues = directionClass.getEnumConstants();
                directions = new Object[enumValues.length + 1];
                directions[0] = null;
                System.arraycopy(enumValues, 0, directions, 1, enumValues.length);
            } catch (Exception ignored) {}

            for (Object dir : directions) {
                try {
                    Object part = getPart.invoke(be, dir);
                    if (part != null) {
                        String partClass = part.getClass().getName().toLowerCase();
                        if (partClass.contains("terminal") || partClass.contains("monitor") ||
                            partClass.contains("panel")) {
                            LLMoblings.LOGGER.info("AE2: Found terminal part: {} on side {}",
                                    part.getClass().getSimpleName(), dir);
                            return true;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("AE2: Could not check for terminal parts: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Try to extract items from an ME network.
     * Returns items that were successfully extracted.
     */
    public static List<ItemStack> extractItems(Level level, BlockPos accessPoint,
                                                java.util.function.Predicate<ItemStack> filter, int maxCount) {
        List<ItemStack> extracted = new ArrayList<>();

        if (!isAE2Loaded()) {
            return extracted;
        }

        try {
            BlockEntity be = level.getBlockEntity(accessPoint);
            if (be == null) return extracted;

            // Use reflection to access AE2's grid system
            Object grid = getGridFromBlockEntity(be);
            if (grid == null) {
                LLMoblings.LOGGER.info("AE2: Could not get grid from block entity at {}", accessPoint);
                return extracted;
            }

            Object storageService = getStorageService(grid);
            if (storageService == null) {
                LLMoblings.LOGGER.info("AE2: Could not get storage service from grid");
                return extracted;
            }

            Object inventory = getInventory(storageService);
            if (inventory == null) {
                LLMoblings.LOGGER.info("AE2: Could not get inventory from storage service");
                return extracted;
            }

            // Get available stacks and extract matching ones
            extracted = extractMatchingItems(inventory, filter, maxCount);
            LLMoblings.LOGGER.info("AE2: Extracted {} item stacks from ME network", extracted.size());

        } catch (Exception e) {
            LLMoblings.LOGGER.warn("Error extracting from ME network: {}", e.getMessage());
            e.printStackTrace();
        }

        return extracted;
    }

    /**
     * Query what items are available in the ME network.
     */
    public static List<ItemStack> queryAvailableItems(Level level, BlockPos accessPoint,
                                                       java.util.function.Predicate<ItemStack> filter) {
        List<ItemStack> available = new ArrayList<>();

        if (!isAE2Loaded()) {
            return available;
        }

        try {
            BlockEntity be = level.getBlockEntity(accessPoint);
            if (be == null) return available;

            Object grid = getGridFromBlockEntity(be);
            if (grid == null) return available;

            Object storageService = getStorageService(grid);
            if (storageService == null) return available;

            Object inventory = getInventory(storageService);
            if (inventory == null) return available;

            available = queryItems(inventory, filter);

        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Error querying ME network: {}", e.getMessage());
        }

        return available;
    }

    // Reflection helpers to access AE2 internals

    private static Object getGridFromBlockEntity(BlockEntity be) {
        try {
            LLMoblings.LOGGER.info("AE2: Trying to get grid from: {}", be.getClass().getSimpleName());

            // Get Direction enum values for methods that need a direction parameter
            Class<?> directionClass = net.minecraft.core.Direction.class;
            Object[] directions = directionClass.getEnumConstants();

            // Try to get IGridNode from the block entity
            for (var method : be.getClass().getMethods()) {
                if (method.getName().equals("getGridNode") || method.getName().equals("getMainNode")) {
                    method.setAccessible(true);
                    Object node = null;

                    // Check if method requires a Direction parameter
                    if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(directionClass)) {
                        // Try each direction to find a valid grid node
                        for (Object dir : directions) {
                            try {
                                node = method.invoke(be, dir);
                                if (node != null) {
                                    LLMoblings.LOGGER.info("AE2: {}({}) returned node", method.getName(), dir);
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                        // Also try null direction
                        if (node == null) {
                            try {
                                node = method.invoke(be, (Object) null);
                            } catch (Exception ignored) {}
                        }
                    } else if (method.getParameterCount() == 0) {
                        // No parameters needed
                        node = method.invoke(be);
                    }

                    if (node != null) {
                        LLMoblings.LOGGER.info("AE2: {} returned: {}", method.getName(), node.getClass().getSimpleName());
                        // Get grid from node
                        for (var nodeMethod : node.getClass().getMethods()) {
                            if (nodeMethod.getName().equals("getGrid") && nodeMethod.getParameterCount() == 0) {
                                Object grid = nodeMethod.invoke(node);
                                if (grid != null) {
                                    LLMoblings.LOGGER.info("AE2: Got grid: {}", grid.getClass().getSimpleName());
                                    return grid;
                                }
                            }
                        }
                    }
                }
            }

            // Try direct grid access
            for (var method : be.getClass().getMethods()) {
                if (method.getName().equals("getGrid") && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    Object grid = method.invoke(be);
                    if (grid != null) {
                        LLMoblings.LOGGER.info("AE2: Direct getGrid() returned: {}", grid.getClass().getSimpleName());
                        return grid;
                    }
                }
            }

            LLMoblings.LOGGER.info("AE2: No grid access method found on {}", be.getClass().getSimpleName());
        } catch (Exception e) {
            LLMoblings.LOGGER.warn("AE2: Error getting grid from {}: {}", be.getClass().getSimpleName(), e.getMessage());
        }
        return null;
    }

    private static Object getStorageService(Object grid) {
        try {
            LLMoblings.LOGGER.info("AE2: Getting storage service from grid: {}", grid);

            // IGrid.getStorageService() or getService(IStorageService.class)
            for (var method : grid.getClass().getMethods()) {
                if (method.getName().equals("getStorageService")) {
                    Object result = method.invoke(grid);
                    LLMoblings.LOGGER.info("AE2: getStorageService() returned: {}", result);
                    return result;
                }
            }

            // Try generic getService method
            for (var method : grid.getClass().getMethods()) {
                if (method.getName().equals("getService") && method.getParameterCount() == 1) {
                    // Find IStorageService class
                    Class<?> storageServiceClass = Class.forName("appeng.api.networking.storage.IStorageService");
                    Object result = method.invoke(grid, storageServiceClass);
                    LLMoblings.LOGGER.info("AE2: getService(IStorageService) returned: {}", result);
                    return result;
                }
            }

            LLMoblings.LOGGER.warn("AE2: No storage service method found on grid");
        } catch (Exception e) {
            LLMoblings.LOGGER.warn("AE2: Could not get storage service: {}", e.getMessage());
        }
        return null;
    }

    private static Object getInventory(Object storageService) {
        try {
            LLMoblings.LOGGER.info("AE2: Getting inventory from storage service: {}", storageService);

            for (var method : storageService.getClass().getMethods()) {
                if (method.getName().equals("getInventory") && method.getParameterCount() == 0) {
                    Object result = method.invoke(storageService);
                    LLMoblings.LOGGER.info("AE2: getInventory() returned: {}", result);
                    return result;
                }
            }

            LLMoblings.LOGGER.warn("AE2: No getInventory method found");
        } catch (Exception e) {
            LLMoblings.LOGGER.warn("AE2: Could not get inventory: {}", e.getMessage());
        }
        return null;
    }

    private static List<ItemStack> extractMatchingItems(Object inventory,
                                                         java.util.function.Predicate<ItemStack> filter,
                                                         int maxCount) {
        List<ItemStack> extracted = new ArrayList<>();

        try {
            LLMoblings.LOGGER.info("AE2: Scanning ME inventory for items...");

            // Get available stacks via getAvailableStacks()
            var getStacksMethod = inventory.getClass().getMethod("getAvailableStacks");
            Object keyCounter = getStacksMethod.invoke(inventory);

            if (keyCounter == null) {
                LLMoblings.LOGGER.warn("AE2: getAvailableStacks() returned null");
                return extracted;
            }

            LLMoblings.LOGGER.info("AE2: Got key counter: {}", keyCounter.getClass().getSimpleName());

            // Get iterator and cast to java.util.Iterator to avoid Guava module access issues
            var iteratorMethod = keyCounter.getClass().getMethod("iterator");
            Object iteratorObj = iteratorMethod.invoke(keyCounter);

            // Cast to Iterator interface to avoid module access issues with Guava's internal iterator
            @SuppressWarnings("unchecked")
            java.util.Iterator<Object> iterator = (java.util.Iterator<Object>) iteratorObj;

            int extractedCount = 0;
            int totalItemsScanned = 0;
            int matchingItems = 0;

            while (extractedCount < maxCount && iterator.hasNext()) {
                Object entryObj = iterator.next();
                totalItemsScanned++;

                // Cast to Map.Entry to avoid module access issues with fastutil's internal classes
                @SuppressWarnings("unchecked")
                java.util.Map.Entry<Object, Long> entry = (java.util.Map.Entry<Object, Long>) entryObj;
                Object key = entry.getKey();
                long available = entry.getValue();

                // Check if it's an item key
                if (key != null && key.getClass().getName().contains("AEItemKey")) {
                    var toStackMethod = key.getClass().getMethod("toStack");
                    ItemStack stack = (ItemStack) toStackMethod.invoke(key);

                    if (!stack.isEmpty() && filter.test(stack)) {
                        matchingItems++;

                        LLMoblings.LOGGER.info("AE2: Found matching item: {} x{}", stack.getItem(), available);

                        int toExtract = (int) Math.min(available, Math.min(stack.getMaxStackSize(), maxCount - extractedCount));

                        // Actually extract from the network
                        ItemStack extractedStack = extractFromNetwork(inventory, key, toExtract);
                        if (!extractedStack.isEmpty()) {
                            extracted.add(extractedStack);
                            extractedCount += extractedStack.getCount();
                            LLMoblings.LOGGER.info("AE2: Extracted {} x{}", extractedStack.getItem(), extractedStack.getCount());
                        } else {
                            LLMoblings.LOGGER.warn("AE2: extractFromNetwork returned empty for {}", stack.getItem());
                        }
                    }
                }
            }

            LLMoblings.LOGGER.info("AE2: Scanned {} items, {} matched filter, {} extracted",
                    totalItemsScanned, matchingItems, extractedCount);

        } catch (Exception e) {
            LLMoblings.LOGGER.warn("AE2: Error extracting items: {}", e.getMessage());
            e.printStackTrace();
        }

        return extracted;
    }

    private static ItemStack extractFromNetwork(Object inventory, Object key, int amount) {
        try {
            // MEStorage.extract(AEKey, long, Actionable, IActionSource)
            var extractMethod = inventory.getClass().getMethod("extract",
                    Class.forName("appeng.api.stacks.AEKey"),
                    long.class,
                    Class.forName("appeng.api.config.Actionable"),
                    Class.forName("appeng.api.networking.security.IActionSource"));

            // Get Actionable.MODULATE (actually perform the action)
            Class<?> actionableClass = Class.forName("appeng.api.config.Actionable");
            Object modulate = actionableClass.getField("MODULATE").get(null);

            // Create a BaseActionSource
            Class<?> actionSourceClass = Class.forName("appeng.api.networking.security.IActionSource");
            Object actionSource = Class.forName("appeng.me.helpers.BaseActionSource")
                    .getDeclaredConstructor().newInstance();

            long extracted = (long) extractMethod.invoke(inventory, key, (long) amount, modulate, actionSource);

            if (extracted > 0) {
                var toStackMethod = key.getClass().getMethod("toStack", int.class);
                return (ItemStack) toStackMethod.invoke(key, (int) extracted);
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.trace("Could not extract from network: {}", e.getMessage());
        }

        return ItemStack.EMPTY;
    }

    private static List<ItemStack> queryItems(Object inventory, java.util.function.Predicate<ItemStack> filter) {
        List<ItemStack> items = new ArrayList<>();

        try {
            var getStacksMethod = inventory.getClass().getMethod("getAvailableStacks");
            Object keyCounter = getStacksMethod.invoke(inventory);

            if (keyCounter == null) return items;

            var iteratorMethod = keyCounter.getClass().getMethod("iterator");
            Object iteratorObj = iteratorMethod.invoke(keyCounter);

            // Cast to Iterator interface to avoid module access issues with Guava's internal iterator
            @SuppressWarnings("unchecked")
            java.util.Iterator<Object> iterator = (java.util.Iterator<Object>) iteratorObj;

            while (iterator.hasNext()) {
                Object entryObj = iterator.next();

                // Cast to Map.Entry to avoid module access issues with fastutil's internal classes
                @SuppressWarnings("unchecked")
                java.util.Map.Entry<Object, Long> entry = (java.util.Map.Entry<Object, Long>) entryObj;
                Object key = entry.getKey();
                long amount = entry.getValue();

                if (key != null && key.getClass().getName().contains("AEItemKey")) {
                    var toStackMethod = key.getClass().getMethod("toStack");
                    ItemStack stack = (ItemStack) toStackMethod.invoke(key);

                    if (!stack.isEmpty() && filter.test(stack)) {
                        stack.setCount((int) Math.min(amount, stack.getMaxStackSize()));
                        items.add(stack);
                    }
                }
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Error querying items: {}", e.getMessage());
        }

        return items;
    }

    /**
     * Request crafting of an item from the ME network.
     * Returns true if the crafting job was successfully submitted.
     */
    public static boolean requestCrafting(Level level, BlockPos accessPoint, Item item, int count) {
        if (!isAE2Loaded()) {
            return false;
        }

        try {
            BlockEntity be = level.getBlockEntity(accessPoint);
            if (be == null) return false;

            Object grid = getGridFromBlockEntity(be);
            if (grid == null) {
                LLMoblings.LOGGER.info("AE2 Craft: Could not get grid");
                return false;
            }

            // Get crafting service
            Object craftingService = getCraftingService(grid);
            if (craftingService == null) {
                LLMoblings.LOGGER.info("AE2 Craft: Could not get crafting service");
                return false;
            }

            // Create AEItemKey for the item we want to craft
            Object aeItemKey = createAEItemKey(item);
            if (aeItemKey == null) {
                LLMoblings.LOGGER.info("AE2 Craft: Could not create AEItemKey for {}", item);
                return false;
            }

            // Check if item is craftable
            if (!isCraftable(craftingService, aeItemKey)) {
                LLMoblings.LOGGER.info("AE2 Craft: {} is not craftable (no pattern)", item);
                return false;
            }

            // Submit crafting request
            boolean submitted = submitCraftingRequest(craftingService, grid, aeItemKey, count);
            if (submitted) {
                LLMoblings.LOGGER.info("AE2 Craft: Submitted crafting request for {} x{}", item, count);
            }
            return submitted;

        } catch (Exception e) {
            LLMoblings.LOGGER.warn("AE2 Craft: Error requesting craft: {}", e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    private static Object getCraftingService(Object grid) {
        try {
            // Try getCraftingService() first
            for (var method : grid.getClass().getMethods()) {
                if (method.getName().equals("getCraftingService") && method.getParameterCount() == 0) {
                    return method.invoke(grid);
                }
            }

            // Try getService(ICraftingService.class)
            for (var method : grid.getClass().getMethods()) {
                if (method.getName().equals("getService") && method.getParameterCount() == 1) {
                    Class<?> craftingServiceClass = Class.forName("appeng.api.networking.crafting.ICraftingService");
                    return method.invoke(grid, craftingServiceClass);
                }
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("AE2 Craft: Could not get crafting service: {}", e.getMessage());
        }
        return null;
    }

    private static Object createAEItemKey(Item item) {
        try {
            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            var ofMethod = aeItemKeyClass.getMethod("of", ItemStack.class);
            return ofMethod.invoke(null, new ItemStack(item));
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("AE2 Craft: Could not create AEItemKey: {}", e.getMessage());
        }
        return null;
    }

    private static boolean isCraftable(Object craftingService, Object aeItemKey) {
        try {
            var isCraftableMethod = craftingService.getClass().getMethod("isCraftable",
                    Class.forName("appeng.api.stacks.AEKey"));
            return (boolean) isCraftableMethod.invoke(craftingService, aeItemKey);
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("AE2 Craft: Could not check craftability: {}", e.getMessage());
        }
        return false;
    }

    private static boolean submitCraftingRequest(Object craftingService, Object grid, Object aeItemKey, int count) {
        try {
            // Create a crafting calculation
            // ICraftingService.beginCraftingCalculation(Level, IActionSource, AEKey, long, CalculationStrategy)

            Class<?> calcStrategyClass = Class.forName("appeng.api.networking.crafting.CalculationStrategy");
            Object craftOnly = calcStrategyClass.getField("CRAFT_ONLY").get(null);

            Class<?> actionSourceClass = Class.forName("appeng.api.networking.security.IActionSource");
            Object actionSource = Class.forName("appeng.me.helpers.BaseActionSource")
                    .getDeclaredConstructor().newInstance();

            // Get the level from the grid
            Object level = null;
            for (var method : grid.getClass().getMethods()) {
                if (method.getName().equals("getLevel") || method.getName().equals("getPivot")) {
                    try {
                        Object result = method.invoke(grid);
                        if (result instanceof net.minecraft.world.level.Level) {
                            level = result;
                            break;
                        }
                        // If it's a node, get level from that
                        if (result != null) {
                            for (var m : result.getClass().getMethods()) {
                                if (m.getName().equals("getLevel") && m.getParameterCount() == 0) {
                                    Object l = m.invoke(result);
                                    if (l instanceof net.minecraft.world.level.Level) {
                                        level = l;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (level == null) {
                LLMoblings.LOGGER.warn("AE2 Craft: Could not get level from grid");
                return false;
            }

            // Begin the crafting calculation
            var beginMethod = craftingService.getClass().getMethod("beginCraftingCalculation",
                    net.minecraft.world.level.Level.class,
                    actionSourceClass,
                    Class.forName("appeng.api.stacks.AEKey"),
                    long.class,
                    calcStrategyClass);

            Object futureJob = beginMethod.invoke(craftingService, level, actionSource, aeItemKey, (long) count, craftOnly);

            if (futureJob == null) {
                LLMoblings.LOGGER.info("AE2 Craft: beginCraftingCalculation returned null");
                return false;
            }

            // Get the crafting plan from the future (this blocks but should be quick)
            var getMethod = futureJob.getClass().getMethod("get", long.class, java.util.concurrent.TimeUnit.class);
            Object craftingPlan = getMethod.invoke(futureJob, 5L, java.util.concurrent.TimeUnit.SECONDS);

            if (craftingPlan == null) {
                LLMoblings.LOGGER.info("AE2 Craft: Could not get crafting plan");
                return false;
            }

            // Check if the plan is valid (simulation was successful)
            var simulationMethod = craftingPlan.getClass().getMethod("simulation");
            boolean isSimulation = (boolean) simulationMethod.invoke(craftingPlan);

            // Check bytes used - if 0, nothing to craft
            var bytesMethod = craftingPlan.getClass().getMethod("bytes");
            long bytes = (long) bytesMethod.invoke(craftingPlan);

            LLMoblings.LOGGER.info("AE2 Craft: Plan simulation={}, bytes={}", isSimulation, bytes);

            if (bytes <= 0) {
                LLMoblings.LOGGER.info("AE2 Craft: Crafting plan has 0 bytes, nothing to craft");
                return false;
            }

            // Submit the job
            // ICraftingService.submitJob(ICraftingPlan, ICraftingRequester, ICraftingCPU, boolean, IActionSource)
            var submitMethod = craftingService.getClass().getMethod("submitJob",
                    Class.forName("appeng.api.networking.crafting.ICraftingPlan"),
                    Class.forName("appeng.api.networking.crafting.ICraftingRequester"),
                    Class.forName("appeng.api.networking.crafting.ICraftingCPU"),
                    boolean.class,
                    actionSourceClass);

            Object linkResult = submitMethod.invoke(craftingService, craftingPlan, null, null, false, actionSource);

            if (linkResult != null) {
                LLMoblings.LOGGER.info("AE2 Craft: Job submitted successfully!");
                return true;
            } else {
                LLMoblings.LOGGER.info("AE2 Craft: submitJob returned null (possibly no CPU available)");
            }

        } catch (Exception e) {
            LLMoblings.LOGGER.warn("AE2 Craft: Error submitting craft request: {}", e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Get a list of iron armor items.
     */
    public static List<Item> getIronArmorItems() {
        List<Item> items = new ArrayList<>();
        items.add(net.minecraft.world.item.Items.IRON_HELMET);
        items.add(net.minecraft.world.item.Items.IRON_CHESTPLATE);
        items.add(net.minecraft.world.item.Items.IRON_LEGGINGS);
        items.add(net.minecraft.world.item.Items.IRON_BOOTS);
        items.add(net.minecraft.world.item.Items.IRON_SWORD);
        return items;
    }

    /**
     * Get a list of diamond armor items.
     */
    public static List<Item> getDiamondArmorItems() {
        List<Item> items = new ArrayList<>();
        items.add(net.minecraft.world.item.Items.DIAMOND_HELMET);
        items.add(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE);
        items.add(net.minecraft.world.item.Items.DIAMOND_LEGGINGS);
        items.add(net.minecraft.world.item.Items.DIAMOND_BOOTS);
        items.add(net.minecraft.world.item.Items.DIAMOND_SWORD);
        return items;
    }
}
