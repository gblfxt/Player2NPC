package com.gblfxt.llmoblings;

import com.gblfxt.llmoblings.compat.FTBTeamsIntegration;
import com.gblfxt.llmoblings.entity.CompanionEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.List;

@EventBusSubscriber(modid = LLMoblings.MOD_ID)
public class ChatHandler {

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        String message = event.getRawText();
        String prefix = Config.CHAT_PREFIX.get();

        // Check if message starts with prefix (e.g., "@companion message" or "@Alex do something")
        if (!message.startsWith(prefix)) {
            return;
        }

        ServerPlayer player = event.getPlayer();
        String afterPrefix = message.substring(prefix.length()).trim();

        LLMoblings.LOGGER.debug("Chat message with prefix from {}: {}", player.getName().getString(), afterPrefix);

        // Parse companion name and message
        // Format: @name message or just @message (for any companion)
        final String targetName;
        final String actualMessage;

        // Check if first word is a companion name
        int spaceIndex = afterPrefix.indexOf(' ');
        if (spaceIndex > 0) {
            String potentialName = afterPrefix.substring(0, spaceIndex);
            String restOfMessage = afterPrefix.substring(spaceIndex + 1).trim();

            // Check if there's a companion with this name nearby (owned by anyone if multi-player interaction enabled)
            boolean allowOtherPlayers = Config.ALLOW_OTHER_PLAYER_INTERACTION.get();
            List<CompanionEntity> namedCompanions = player.level().getEntitiesOfClass(
                    CompanionEntity.class,
                    player.getBoundingBox().inflate(64),
                    c -> {
                        boolean nameMatch = c.getCompanionName().equalsIgnoreCase(potentialName);
                        boolean canInteract = c.isOwner(player) || allowOtherPlayers;
                        return nameMatch && canInteract;
                    }
            );

            if (!namedCompanions.isEmpty()) {
                targetName = potentialName;
                actualMessage = restOfMessage;
            } else {
                targetName = "";
                actualMessage = afterPrefix;
            }
        } else {
            targetName = "";
            actualMessage = afterPrefix;
        }

        // Find target companions
        boolean allowOtherPlayers = Config.ALLOW_OTHER_PLAYER_INTERACTION.get();
        List<CompanionEntity> companions = player.level().getEntitiesOfClass(
                CompanionEntity.class,
                player.getBoundingBox().inflate(64),
                c -> {
                    boolean nameMatch = targetName.isEmpty() || c.getCompanionName().equalsIgnoreCase(targetName);
                    boolean canInteract = c.isOwner(player) || allowOtherPlayers;
                    return nameMatch && canInteract;
                }
        );

        if (companions.isEmpty()) {
            LLMoblings.LOGGER.debug("No companions found for player {} with target name '{}'",
                    player.getName().getString(), targetName);
            // No companions found - don't cancel event, let message through as normal chat
            return;
        }

        // Send message to companion(s)
        for (CompanionEntity companion : companions) {
            boolean isOwner = companion.isOwner(player);
            boolean isTeammate = false;

            // Check if player is on same FTB Team as owner
            if (!isOwner && FTBTeamsIntegration.isModLoaded()) {
                Player owner = companion.getOwner();
                if (owner != null) {
                    isTeammate = FTBTeamsIntegration.areOnSameTeam(player, owner);
                    if (isTeammate) {
                        LLMoblings.LOGGER.info("[{}] {} is a teammate of owner - granting command access",
                                companion.getCompanionName(), player.getName().getString());
                    }
                }
            }

            LLMoblings.LOGGER.info("[{}] Received message from {} (owner={}, teammate={}): {}",
                    companion.getCompanionName(), player.getName().getString(), isOwner, isTeammate, actualMessage);

            // Pass the sender info - companion will respond appropriately
            // Teammates get same privileges as owner
            companion.onChatMessage(player, actualMessage, isOwner || isTeammate);

            if (!targetName.isEmpty()) break; // Only first if specific name given
        }

        // Cancel the chat event so it doesn't broadcast to other players
        event.setCanceled(true);
    }
}
