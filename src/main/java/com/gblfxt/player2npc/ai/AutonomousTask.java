package com.gblfxt.player2npc.ai;

import com.gblfxt.player2npc.Player2NPC;
import com.gblfxt.player2npc.compat.AE2Integration;
import com.gblfxt.player2npc.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Predicate;

/**
 * Autonomous behavior for companions - independent survival and base management.
 */
public class AutonomousTask {
    private final CompanionEntity companion;
    private final int baseRadius;

    // Current autonomous sub-task
    private AutonomousState currentState = AutonomousState.ASSESSING;
    private int ticksInState = 0;
    private int reportCooldown = 0;

    // Targets
    private BlockPos targetStorage = null;
    private BlockPos meAccessPoint = null;  // AE2 ME network access
    private net.minecraft.world.entity.Entity huntTarget = null;
    private BlockPos homePos = null;

    // Resource tracking
    private final Map<String, Integer> baseResources = new HashMap<>();
    private final List<String> needs = new ArrayList<>();
    private int foodCount = 0;
    private boolean hasWeapon = false;
    private boolean hasArmor = false;

    public enum AutonomousState {
        ASSESSING,      // Scanning base, checking resources
        HUNTING,        // Hunting animals for food
        GATHERING,      // Mining/gathering resources
        EQUIPPING,      // Equipping armor/weapons
        STORING,        // Depositing items in chests
        PATROLLING,     // Guarding the area
        RESTING         // Idle near base
    }

    public AutonomousTask(CompanionEntity companion, int baseRadius) {
        this.companion = companion;
        this.baseRadius = baseRadius;
        this.homePos = companion.blockPosition();
    }

    public void tick() {
        ticksInState++;
        reportCooldown--;

        switch (currentState) {
            case ASSESSING -> tickAssessing();
            case HUNTING -> tickHunting();
            case GATHERING -> tickGathering();
            case EQUIPPING -> tickEquipping();
            case STORING -> tickStoring();
            case PATROLLING -> tickPatrolling();
            case RESTING -> tickResting();
        }
    }

    private void tickAssessing() {
        if (ticksInState == 1) {
            report("Assessing the area...");
        }

        // Scan for storage containers
        if (ticksInState == 20) {
            scanStorage();
        }

        // Check own inventory
        if (ticksInState == 40) {
            assessSelf();
        }

        // Determine needs and next action
        if (ticksInState >= 60) {
            determineNextAction();
        }
    }

    private void scanStorage() {
        baseResources.clear();
        List<BlockPos> storageBlocks = findStorageContainers();

        for (BlockPos pos : storageBlocks) {
            BlockEntity be = companion.level().getBlockEntity(pos);
            if (be instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (!stack.isEmpty()) {
                        String name = stack.getItem().toString();
                        baseResources.merge(name, stack.getCount(), Integer::sum);
                    }
                }
            }
        }

        if (!storageBlocks.isEmpty()) {
            targetStorage = storageBlocks.get(0);
        }

        // Also check for AE2 ME networks
        List<BlockPos> meAccessPoints = AE2Integration.findMEAccessPoints(
                companion.level(), companion.blockPosition(), baseRadius);

        if (!meAccessPoints.isEmpty()) {
            meAccessPoint = meAccessPoints.get(0);

            // Query ME network for available items
            List<ItemStack> meItems = AE2Integration.queryAvailableItems(
                    companion.level(), meAccessPoint,
                    stack -> true  // Get all items
            );

            for (ItemStack stack : meItems) {
                String name = stack.getItem().toString();
                baseResources.merge(name, stack.getCount(), Integer::sum);
            }

            report("Found ME network access point!");
        }

        Player2NPC.LOGGER.debug("Scanned {} storage containers + {} ME access points, found {} item types",
                storageBlocks.size(), meAccessPoints.size(), baseResources.size());
    }

    private List<BlockPos> findStorageContainers() {
        List<BlockPos> containers = new ArrayList<>();
        BlockPos center = companion.blockPosition();

        for (int x = -baseRadius; x <= baseRadius; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -baseRadius; z <= baseRadius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockEntity be = companion.level().getBlockEntity(pos);
                    if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) {
                        containers.add(pos);
                    }
                }
            }
        }
        return containers;
    }

    private void assessSelf() {
        foodCount = 0;
        hasWeapon = false;
        hasArmor = false;

        // Check inventory
        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();

                // Food check
                if (item.getFoodProperties(stack, companion) != null) {
                    foodCount += stack.getCount();
                }

                // Weapon check
                if (item instanceof SwordItem || item instanceof AxeItem) {
                    hasWeapon = true;
                }
            }
        }

        // Check equipped armor
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!companion.getItemBySlot(slot).isEmpty()) {
                hasArmor = true;
                break;
            }
        }

        Player2NPC.LOGGER.debug("Self assessment: food={}, hasWeapon={}, hasArmor={}",
                foodCount, hasWeapon, hasArmor);
    }

    private void determineNextAction() {
        needs.clear();

        // Priority 1: Equip available gear
        if (shouldEquip()) {
            changeState(AutonomousState.EQUIPPING);
            return;
        }

        // Priority 2: Get food if low
        if (foodCount < 5) {
            needs.add("food");

            // Try to get food from ME network first
            if (meAccessPoint != null && tryGetFoodFromME()) {
                assessSelf();  // Re-check food count
                if (foodCount >= 5) {
                    report("Got food from the ME network!");
                    changeState(AutonomousState.ASSESSING);
                    return;
                }
            }

            // Fall back to hunting
            changeState(AutonomousState.HUNTING);
            return;
        }

        // Priority 3: Store excess items
        if (hasExcessItems() && targetStorage != null) {
            changeState(AutonomousState.STORING);
            return;
        }

        // Priority 4: Patrol if well-equipped
        if (hasWeapon) {
            changeState(AutonomousState.PATROLLING);
            return;
        }

        // Default: Rest
        changeState(AutonomousState.RESTING);
    }

    private boolean tryGetFoodFromME() {
        if (meAccessPoint == null) return false;

        // Move to ME access point if not close
        double distance = companion.position().distanceTo(Vec3.atCenterOf(meAccessPoint));
        if (distance > 5.0) {
            companion.getNavigation().moveTo(
                    meAccessPoint.getX() + 0.5,
                    meAccessPoint.getY(),
                    meAccessPoint.getZ() + 0.5,
                    1.0
            );
            return false;  // Will try again next tick
        }

        // Extract food from ME network
        List<ItemStack> food = AE2Integration.extractItems(
                companion.level(),
                meAccessPoint,
                stack -> stack.getItem().getFoodProperties(stack, companion) != null,
                16  // Get up to 16 food items
        );

        if (!food.isEmpty()) {
            int totalFood = 0;
            for (ItemStack stack : food) {
                ItemStack remaining = companion.addToInventory(stack);
                totalFood += stack.getCount() - remaining.getCount();
            }
            if (totalFood > 0) {
                report("Retrieved " + totalFood + " food from ME network.");
                return true;
            }
        }

        return false;
    }

    private boolean shouldEquip() {
        // Check inventory for unequipped armor/weapons
        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();

            // Check for better weapon
            if ((item instanceof SwordItem || item instanceof AxeItem) &&
                    companion.getMainHandItem().isEmpty()) {
                return true;
            }

            // Check for armor
            if (item instanceof ArmorItem armorItem) {
                EquipmentSlot slot = armorItem.getEquipmentSlot();
                if (companion.getItemBySlot(slot).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void tickHunting() {
        if (ticksInState == 1) {
            report("Hunting for food...");
            huntTarget = findHuntTarget();
        }

        if (huntTarget == null || !huntTarget.isAlive()) {
            huntTarget = findHuntTarget();
            if (huntTarget == null) {
                report("No animals nearby to hunt.");
                changeState(AutonomousState.ASSESSING);
                return;
            }
        }

        double distance = companion.distanceTo(huntTarget);

        if (distance < 2.5) {
            // Attack
            companion.doHurtTarget(huntTarget);
            companion.swing(companion.getUsedItemHand());

            if (!huntTarget.isAlive()) {
                report("Got one!");
                huntTarget = null;
                // Check if we have enough food now
                assessSelf();
                if (foodCount >= 10) {
                    changeState(AutonomousState.ASSESSING);
                }
            }
        } else {
            // Chase
            companion.getNavigation().moveTo(huntTarget, 1.2);
        }

        // Timeout
        if (ticksInState > 600) {
            report("Hunting taking too long, reassessing...");
            changeState(AutonomousState.ASSESSING);
        }
    }

    private net.minecraft.world.entity.Entity findHuntTarget() {
        AABB searchBox = companion.getBoundingBox().inflate(baseRadius);

        // Look for passive mobs
        List<Animal> animals = companion.level().getEntitiesOfClass(Animal.class, searchBox,
                animal -> animal.isAlive() &&
                        !(animal instanceof Wolf) &&  // Don't hunt wolves
                        !(animal instanceof Cat) &&   // Don't hunt cats
                        !(animal instanceof Parrot)   // Don't hunt parrots
        );

        return animals.stream()
                .min(Comparator.comparingDouble(a -> companion.distanceTo(a)))
                .orElse(null);
    }

    private void tickGathering() {
        // Delegate to mining task if needed
        // For now, transition back to assessing
        if (ticksInState > 100) {
            changeState(AutonomousState.ASSESSING);
        }
    }

    private void tickEquipping() {
        if (ticksInState == 1) {
            report("Equipping gear...");
        }

        // Find and equip items
        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();

            // Equip weapon to mainhand
            if ((item instanceof SwordItem || item instanceof AxeItem) &&
                    companion.getMainHandItem().isEmpty()) {
                companion.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                companion.setItem(i, ItemStack.EMPTY);
                report("Equipped " + item.getDescription().getString());
                continue;
            }

            // Equip armor
            if (item instanceof ArmorItem armorItem) {
                EquipmentSlot slot = armorItem.getEquipmentSlot();
                if (companion.getItemBySlot(slot).isEmpty()) {
                    companion.setItemSlot(slot, stack.copy());
                    companion.setItem(i, ItemStack.EMPTY);
                    report("Equipped " + item.getDescription().getString());
                }
            }
        }

        if (ticksInState >= 20) {
            assessSelf();
            changeState(AutonomousState.ASSESSING);
        }
    }

    private void tickStoring() {
        if (targetStorage == null) {
            changeState(AutonomousState.ASSESSING);
            return;
        }

        double distance = companion.position().distanceTo(Vec3.atCenterOf(targetStorage));

        if (distance > 3.0) {
            companion.getNavigation().moveTo(
                    targetStorage.getX() + 0.5,
                    targetStorage.getY(),
                    targetStorage.getZ() + 0.5,
                    1.0
            );
        } else {
            // Deposit items
            BlockEntity be = companion.level().getBlockEntity(targetStorage);
            if (be instanceof Container container) {
                depositItems(container);
            }
            changeState(AutonomousState.ASSESSING);
        }

        if (ticksInState > 200) {
            changeState(AutonomousState.ASSESSING);
        }
    }

    private void depositItems(Container container) {
        int deposited = 0;

        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (stack.isEmpty()) continue;

            // Keep food, weapons, armor
            Item item = stack.getItem();
            if (item.getFoodProperties(stack, companion) != null || item instanceof SwordItem ||
                    item instanceof AxeItem || item instanceof ArmorItem) {
                continue;
            }

            // Try to deposit
            for (int j = 0; j < container.getContainerSize(); j++) {
                ItemStack containerStack = container.getItem(j);
                if (containerStack.isEmpty()) {
                    container.setItem(j, stack.copy());
                    companion.setItem(i, ItemStack.EMPTY);
                    deposited++;
                    break;
                } else if (ItemStack.isSameItemSameComponents(containerStack, stack) &&
                        containerStack.getCount() < containerStack.getMaxStackSize()) {
                    int space = containerStack.getMaxStackSize() - containerStack.getCount();
                    int toAdd = Math.min(space, stack.getCount());
                    containerStack.grow(toAdd);
                    stack.shrink(toAdd);
                    if (stack.isEmpty()) {
                        companion.setItem(i, ItemStack.EMPTY);
                        deposited++;
                        break;
                    }
                }
            }
        }

        if (deposited > 0) {
            report("Stored " + deposited + " items.");
        }
    }

    private void tickPatrolling() {
        if (ticksInState == 1) {
            report("Patrolling the area...");
        }

        // Check for threats
        AABB patrolBox = companion.getBoundingBox().inflate(baseRadius);
        List<Monster> threats = companion.level().getEntitiesOfClass(Monster.class, patrolBox,
                Monster::isAlive);

        if (!threats.isEmpty()) {
            Monster nearest = threats.stream()
                    .min(Comparator.comparingDouble(m -> companion.distanceTo(m)))
                    .orElse(null);

            if (nearest != null) {
                double distance = companion.distanceTo(nearest);

                if (distance < 2.5) {
                    companion.doHurtTarget(nearest);
                    companion.swing(companion.getUsedItemHand());
                } else {
                    companion.getNavigation().moveTo(nearest, 1.2);
                }

                if (!nearest.isAlive() && reportCooldown <= 0) {
                    report("Threat eliminated!");
                    reportCooldown = 100;
                }
                return;
            }
        }

        // Wander around home
        if (companion.getNavigation().isDone() && ticksInState % 100 == 0) {
            BlockPos wanderTarget = homePos.offset(
                    companion.getRandom().nextInt(baseRadius * 2) - baseRadius,
                    0,
                    companion.getRandom().nextInt(baseRadius * 2) - baseRadius
            );
            companion.getNavigation().moveTo(wanderTarget.getX(), wanderTarget.getY(), wanderTarget.getZ(), 0.8);
        }

        // Periodically reassess
        if (ticksInState > 600) {
            changeState(AutonomousState.ASSESSING);
        }
    }

    private void tickResting() {
        if (ticksInState == 1) {
            report("Taking a break near base.");
        }

        // Stay near home
        double distFromHome = companion.position().distanceTo(Vec3.atCenterOf(homePos));
        if (distFromHome > baseRadius) {
            companion.getNavigation().moveTo(homePos.getX(), homePos.getY(), homePos.getZ(), 0.8);
        }

        // Periodically reassess
        if (ticksInState > 400) {
            changeState(AutonomousState.ASSESSING);
        }
    }

    private boolean hasExcessItems() {
        int usedSlots = 0;
        for (int i = 0; i < companion.getContainerSize(); i++) {
            if (!companion.getItem(i).isEmpty()) {
                usedSlots++;
            }
        }
        return usedSlots > companion.getContainerSize() / 2;
    }

    private void changeState(AutonomousState newState) {
        Player2NPC.LOGGER.debug("Autonomous state: {} -> {}", currentState, newState);
        currentState = newState;
        ticksInState = 0;
    }

    private void report(String message) {
        if (companion.getOwner() != null) {
            companion.getOwner().sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("[" + companion.getCompanionName() + "] " + message)
            );
        }
    }

    public AutonomousState getCurrentState() {
        return currentState;
    }

    public String getStatusReport() {
        return String.format("Mode: Autonomous (%s) | Food: %d | Armed: %s | Armored: %s",
                currentState, foodCount, hasWeapon ? "Yes" : "No", hasArmor ? "Yes" : "No");
    }
}
