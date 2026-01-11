package com.gblfxt.llmoblings.compat;

import com.gblfxt.llmoblings.LLMoblings;
import com.gblfxt.llmoblings.entity.CompanionEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Integration with the Artifacts mod for special item abilities.
 */
public class ArtifactsIntegration {

    private static Boolean artifactsLoaded = null;
    private static Item tabletOfFlying = null;
    private static Item cloudInABottle = null;
    private static Item rocketBoots = null;

    public static boolean isArtifactsLoaded() {
        if (artifactsLoaded == null) {
            artifactsLoaded = ModList.get().isLoaded("artifacts");
            if (artifactsLoaded) {
                LLMoblings.LOGGER.info("Artifacts mod detected - special item integration enabled");
                // Cache the item references
                try {
                    tabletOfFlying = BuiltInRegistries.ITEM.get(
                            ResourceLocation.fromNamespaceAndPath("artifacts", "antidote_vessel"));
                    // Try various names for the flying tablet
                    for (String name : new String[]{"tablet_of_flying", "cloud_in_a_bottle", "helium_flamingo"}) {
                        Item item = BuiltInRegistries.ITEM.get(
                                ResourceLocation.fromNamespaceAndPath("artifacts", name));
                        if (item != null && !item.equals(net.minecraft.world.item.Items.AIR)) {
                            if (name.contains("tablet") || name.contains("flying")) {
                                tabletOfFlying = item;
                                LLMoblings.LOGGER.info("Found Tablet of Flying: {}", name);
                            } else if (name.contains("cloud")) {
                                cloudInABottle = item;
                                LLMoblings.LOGGER.info("Found Cloud in a Bottle: {}", name);
                            }
                        }
                    }
                } catch (Exception e) {
                    LLMoblings.LOGGER.debug("Could not cache Artifacts items: {}", e.getMessage());
                }
            }
        }
        return artifactsLoaded;
    }

    /**
     * Check if the companion has a flying artifact and should be able to fly.
     */
    public static boolean canFly(CompanionEntity companion) {
        if (!isArtifactsLoaded()) {
            return false;
        }

        // Check all equipment slots and inventory for flying items
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = companion.getItemBySlot(slot);
            if (isFlyingArtifact(stack)) {
                return true;
            }
        }

        // Check inventory
        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (isFlyingArtifact(stack)) {
                return true;
            }
        }

        // Also check held items
        if (isFlyingArtifact(companion.getMainHandItem()) || isFlyingArtifact(companion.getOffhandItem())) {
            return true;
        }

        return false;
    }

    /**
     * Check if an item is a flying artifact.
     */
    public static boolean isFlyingArtifact(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        String itemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();

        // Check for known flying artifacts
        return itemName.contains("tablet_of_flying") ||
               itemName.contains("helium_flamingo") ||
               itemName.contains("crystal_heart") ||
               (itemName.contains("artifacts") && itemName.contains("fly"));
    }

    /**
     * Check if an item gives a speed boost.
     */
    public static boolean isSpeedArtifact(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        String itemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();

        return itemName.contains("bunny_hoppers") ||
               itemName.contains("running_shoes") ||
               itemName.contains("kitty_slippers") ||
               itemName.contains("flippers");
    }

    /**
     * Check for any special artifacts and apply effects.
     */
    public static void applyArtifactEffects(CompanionEntity companion) {
        if (!isArtifactsLoaded()) {
            return;
        }

        boolean canFly = canFly(companion);

        // Apply or remove flying ability based on artifacts
        if (canFly) {
            companion.setNoGravity(true);
            // The actual flying logic will be handled in CompanionEntity tick
        } else {
            companion.setNoGravity(false);
        }
    }
}
