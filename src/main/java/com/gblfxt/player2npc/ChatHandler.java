package com.gblfxt.player2npc;

import com.gblfxt.player2npc.entity.CompanionEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.List;

@EventBusSubscriber(modid = Player2NPC.MOD_ID)
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

        // Parse companion name and message
        // Format: @name message or just @message (for any companion)
        final String targetName;
        final String actualMessage;

        // Check if first word is a companion name
        int spaceIndex = afterPrefix.indexOf(' ');
        if (spaceIndex > 0) {
            String potentialName = afterPrefix.substring(0, spaceIndex);
            String restOfMessage = afterPrefix.substring(spaceIndex + 1).trim();

            // Check if player has a companion with this name
            List<CompanionEntity> namedCompanions = player.level().getEntitiesOfClass(
                    CompanionEntity.class,
                    player.getBoundingBox().inflate(64),
                    c -> c.isOwner(player) && c.getCompanionName().equalsIgnoreCase(potentialName)
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
        List<CompanionEntity> companions = player.level().getEntitiesOfClass(
                CompanionEntity.class,
                player.getBoundingBox().inflate(64),
                c -> c.isOwner(player) &&
                        (targetName.isEmpty() || c.getCompanionName().equalsIgnoreCase(targetName))
        );

        if (companions.isEmpty()) {
            // No companions found - don't cancel event, let message through as normal chat
            // User may have typed @ for other reasons
            return;
        }

        // Send message to companion(s)
        for (CompanionEntity companion : companions) {
            companion.onChatMessage(player, actualMessage);
            if (!targetName.isEmpty()) break; // Only first if specific name given
        }

        // Cancel the chat event so it doesn't broadcast to other players
        event.setCanceled(true);
    }
}
