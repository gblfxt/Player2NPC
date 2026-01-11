package com.gblfxt.llmoblings.data;

import com.gblfxt.llmoblings.LLMoblings;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists companion data across dismiss/summon cycles.
 * Stored per-player, keyed by companion name.
 */
public class CompanionSaveData extends SavedData {

    private static final String DATA_NAME = "llmoblings_companions";

    // Map: PlayerUUID -> (CompanionName -> CompanionNBT)
    private final Map<UUID, Map<String, CompoundTag>> playerCompanions = new HashMap<>();

    public CompanionSaveData() {
    }

    public static CompanionSaveData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(CompanionSaveData::new, CompanionSaveData::load),
                DATA_NAME
        );
    }

    public static CompanionSaveData load(CompoundTag tag, HolderLookup.Provider provider) {
        CompanionSaveData data = new CompanionSaveData();

        ListTag playersList = tag.getList("Players", 10);
        for (int i = 0; i < playersList.size(); i++) {
            CompoundTag playerTag = playersList.getCompound(i);
            UUID playerUUID = playerTag.getUUID("UUID");

            Map<String, CompoundTag> companions = new HashMap<>();
            ListTag companionsList = playerTag.getList("Companions", 10);
            for (int j = 0; j < companionsList.size(); j++) {
                CompoundTag companionTag = companionsList.getCompound(j);
                String name = companionTag.getString("Name");
                companions.put(name.toLowerCase(), companionTag);
            }

            data.playerCompanions.put(playerUUID, companions);
        }

        LLMoblings.LOGGER.info("Loaded companion save data for {} players", data.playerCompanions.size());
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag playersList = new ListTag();

        for (Map.Entry<UUID, Map<String, CompoundTag>> entry : playerCompanions.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("UUID", entry.getKey());

            ListTag companionsList = new ListTag();
            for (CompoundTag companionTag : entry.getValue().values()) {
                companionsList.add(companionTag);
            }
            playerTag.put("Companions", companionsList);

            playersList.add(playerTag);
        }

        tag.put("Players", playersList);
        return tag;
    }

    /**
     * Save a companion's data when it's dismissed.
     */
    public void saveCompanion(UUID playerUUID, String companionName, CompoundTag data) {
        playerCompanions.computeIfAbsent(playerUUID, k -> new HashMap<>())
                .put(companionName.toLowerCase(), data);
        setDirty();
        LLMoblings.LOGGER.info("Saved companion '{}' for player {}", companionName, playerUUID);
    }

    /**
     * Load a companion's saved data when summoning.
     * Returns null if no saved data exists.
     */
    public CompoundTag loadCompanion(UUID playerUUID, String companionName) {
        Map<String, CompoundTag> companions = playerCompanions.get(playerUUID);
        if (companions != null) {
            CompoundTag data = companions.get(companionName.toLowerCase());
            if (data != null) {
                LLMoblings.LOGGER.info("Loaded saved data for companion '{}' of player {}", companionName, playerUUID);
                return data;
            }
        }
        return null;
    }

    /**
     * Check if a player has a saved companion with this name.
     */
    public boolean hasSavedCompanion(UUID playerUUID, String companionName) {
        Map<String, CompoundTag> companions = playerCompanions.get(playerUUID);
        return companions != null && companions.containsKey(companionName.toLowerCase());
    }

    /**
     * Remove saved data for a companion (when permanently deleted).
     */
    public void deleteCompanion(UUID playerUUID, String companionName) {
        Map<String, CompoundTag> companions = playerCompanions.get(playerUUID);
        if (companions != null) {
            companions.remove(companionName.toLowerCase());
            setDirty();
            LLMoblings.LOGGER.info("Deleted saved data for companion '{}' of player {}", companionName, playerUUID);
        }
    }
}
