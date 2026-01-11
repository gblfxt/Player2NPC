package com.gblfxt.llmoblings.compat;

import com.gblfxt.llmoblings.LLMoblings;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Integration with FTB Teams mod.
 * Allows team members to interact with companions owned by teammates.
 */
public class FTBTeamsIntegration {
    private static boolean isLoaded = false;
    private static boolean checkedLoaded = false;

    // Cached reflection references
    private static Class<?> ftbTeamsAPIClass;
    private static Method apiMethod;
    private static Method getManagerMethod;
    private static Method getTeamForPlayerIDMethod;
    private static Method getTeamIdMethod;

    public static boolean isModLoaded() {
        if (!checkedLoaded) {
            isLoaded = ModList.get().isLoaded("ftbteams");
            checkedLoaded = true;
            if (isLoaded) {
                LLMoblings.LOGGER.info("FTB Teams detected - team member interaction enabled");
                initReflection();
            }
        }
        return isLoaded;
    }

    private static void initReflection() {
        try {
            ftbTeamsAPIClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            apiMethod = ftbTeamsAPIClass.getMethod("api");

            Object apiInstance = apiMethod.invoke(null);
            Class<?> apiClass = apiInstance.getClass();

            // Get the manager
            for (Method m : apiClass.getMethods()) {
                if (m.getName().equals("getManager")) {
                    getManagerMethod = m;
                    break;
                }
            }

            if (getManagerMethod != null) {
                Object manager = getManagerMethod.invoke(apiInstance);
                Class<?> managerClass = manager.getClass();

                // Find getTeamForPlayerID method
                for (Method m : managerClass.getMethods()) {
                    if (m.getName().equals("getTeamForPlayerID")) {
                        getTeamForPlayerIDMethod = m;
                        break;
                    }
                }
            }

            LLMoblings.LOGGER.info("FTB Teams reflection initialized successfully");
        } catch (Exception e) {
            LLMoblings.LOGGER.warn("Failed to initialize FTB Teams reflection: {}", e.getMessage());
            isLoaded = false;
        }
    }

    /**
     * Check if two players are on the same FTB Team.
     *
     * @param player1 First player
     * @param player2 Second player
     * @return true if both players are on the same team
     */
    public static boolean areOnSameTeam(Player player1, Player player2) {
        if (!isModLoaded()) return false;
        if (player1 == null || player2 == null) return false;

        return areOnSameTeam(player1.getUUID(), player2.getUUID());
    }

    /**
     * Check if two player UUIDs are on the same FTB Team.
     */
    public static boolean areOnSameTeam(UUID uuid1, UUID uuid2) {
        if (!isModLoaded()) return false;
        if (uuid1 == null || uuid2 == null) return false;
        if (uuid1.equals(uuid2)) return true; // Same player

        try {
            Object apiInstance = apiMethod.invoke(null);
            Object manager = getManagerMethod.invoke(apiInstance);

            // Get team for player 1
            Object team1Optional = getTeamForPlayerIDMethod.invoke(manager, uuid1);
            Object team2Optional = getTeamForPlayerIDMethod.invoke(manager, uuid2);

            // Check if both have teams
            if (team1Optional instanceof Optional<?> opt1 && team2Optional instanceof Optional<?> opt2) {
                if (opt1.isPresent() && opt2.isPresent()) {
                    Object team1 = opt1.get();
                    Object team2 = opt2.get();

                    // Get team IDs and compare
                    if (getTeamIdMethod == null) {
                        for (Method m : team1.getClass().getMethods()) {
                            if (m.getName().equals("getId") || m.getName().equals("getTeamId")) {
                                getTeamIdMethod = m;
                                break;
                            }
                        }
                    }

                    if (getTeamIdMethod != null) {
                        Object id1 = getTeamIdMethod.invoke(team1);
                        Object id2 = getTeamIdMethod.invoke(team2);
                        return id1.equals(id2);
                    }
                }
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Error checking FTB Teams membership: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Get the team name for a player, if they have one.
     */
    public static String getTeamName(Player player) {
        if (!isModLoaded() || player == null) return null;

        try {
            Object apiInstance = apiMethod.invoke(null);
            Object manager = getManagerMethod.invoke(apiInstance);
            Object teamOptional = getTeamForPlayerIDMethod.invoke(manager, player.getUUID());

            if (teamOptional instanceof Optional<?> opt && opt.isPresent()) {
                Object team = opt.get();
                for (Method m : team.getClass().getMethods()) {
                    if (m.getName().equals("getName") || m.getName().equals("getDisplayName")) {
                        Object name = m.invoke(team);
                        if (name != null) {
                            return name.toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Error getting FTB Team name: {}", e.getMessage());
        }

        return null;
    }
}
