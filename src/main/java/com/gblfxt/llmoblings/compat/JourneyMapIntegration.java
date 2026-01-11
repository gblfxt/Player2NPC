package com.gblfxt.llmoblings.compat;

import com.gblfxt.llmoblings.LLMoblings;
import com.gblfxt.llmoblings.entity.CompanionEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Integration with JourneyMap for showing companion icons on the map.
 *
 * Note: Full JourneyMap integration requires the JourneyMap API dependency.
 * This class provides basic tracking that other map mods may also utilize.
 */
public class JourneyMapIntegration {

    private static Boolean journeyMapLoaded = null;
    private static final Map<UUID, CompanionMarker> companionMarkers = new HashMap<>();

    public static boolean isJourneyMapLoaded() {
        if (journeyMapLoaded == null) {
            journeyMapLoaded = ModList.get().isLoaded("journeymap");
            if (journeyMapLoaded) {
                LLMoblings.LOGGER.info("JourneyMap detected - companion map markers enabled");
            }
        }
        return journeyMapLoaded;
    }

    /**
     * Update or create a marker for a companion.
     * Called periodically from CompanionEntity tick.
     */
    public static void updateCompanionMarker(CompanionEntity companion) {
        if (!isJourneyMapLoaded()) return;

        UUID companionId = companion.getUUID();
        CompanionMarker marker = companionMarkers.computeIfAbsent(companionId,
                id -> new CompanionMarker(companion));

        marker.update(companion);
    }

    /**
     * Remove a companion's marker when they are removed.
     */
    public static void removeCompanionMarker(CompanionEntity companion) {
        companionMarkers.remove(companion.getUUID());
    }

    /**
     * Get companion info for display.
     * This can be used by other map mods that check for this data.
     */
    public static String getCompanionMapInfo(CompanionEntity companion) {
        return String.format("%s (HP: %d/%d, %s)",
                companion.getCompanionName(),
                (int) companion.getHealth(),
                (int) companion.getMaxHealth(),
                companion.getAIController() != null ?
                        companion.getAIController().getCurrentState().name() : "IDLE"
        );
    }

    /**
     * Simple marker data class for companion tracking.
     */
    public static class CompanionMarker {
        private final UUID companionId;
        private String name;
        private double x, y, z;
        private String dimension;
        private String state;
        private int health;
        private int maxHealth;

        public CompanionMarker(CompanionEntity companion) {
            this.companionId = companion.getUUID();
            update(companion);
        }

        public void update(CompanionEntity companion) {
            this.name = companion.getCompanionName();
            this.x = companion.getX();
            this.y = companion.getY();
            this.z = companion.getZ();
            this.dimension = companion.level().dimension().location().toString();
            this.state = companion.getAIController() != null ?
                    companion.getAIController().getCurrentState().name() : "IDLE";
            this.health = (int) companion.getHealth();
            this.maxHealth = (int) companion.getMaxHealth();
        }

        // Getters for external use
        public UUID getCompanionId() { return companionId; }
        public String getName() { return name; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public String getDimension() { return dimension; }
        public String getState() { return state; }
        public int getHealth() { return health; }
        public int getMaxHealth() { return maxHealth; }
    }

    /**
     * Get all tracked companion markers.
     * Can be used by client-side rendering or other map integrations.
     */
    public static Map<UUID, CompanionMarker> getAllMarkers() {
        return new HashMap<>(companionMarkers);
    }
}
