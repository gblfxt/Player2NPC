package com.gblfxt.llmoblings.client;

import net.minecraft.resources.ResourceLocation;

/**
 * Manages companion skin selection.
 * Uses built-in Minecraft skins for reliability.
 */
public class SkinManager {

    // Built-in Minecraft player skins
    private static final ResourceLocation[] SKINS = {
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/alex.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/ari.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/efe.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/kai.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/makena.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/noor.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/sunny.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/zuri.png"),
    };

    // Slim model skins (for potential future use)
    private static final ResourceLocation[] SLIM_SKINS = {
        ResourceLocation.withDefaultNamespace("textures/entity/player/slim/alex.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/slim/ari.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/slim/efe.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/slim/kai.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/slim/makena.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/slim/noor.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/slim/sunny.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/player/slim/zuri.png"),
    };

    public static final ResourceLocation STEVE = SKINS[0];
    public static final ResourceLocation ALEX = SKINS[1];

    /**
     * Get a skin based on the companion's skin index.
     */
    public static ResourceLocation getSkinByIndex(int index) {
        if (index < 0 || index >= SKINS.length) {
            return STEVE;
        }
        return SKINS[index];
    }

    /**
     * Get a deterministic skin based on companion name.
     * Same name always gets same skin.
     */
    public static ResourceLocation getSkinForName(String name) {
        if (name == null || name.isEmpty()) {
            return STEVE;
        }
        int hash = Math.abs(name.hashCode());
        return SKINS[hash % SKINS.length];
    }

    /**
     * Get a random skin index to store on the entity.
     */
    public static int getRandomSkinIndex() {
        return (int) (Math.random() * SKINS.length);
    }

    /**
     * Get total number of available skins.
     */
    public static int getSkinCount() {
        return SKINS.length;
    }
}
