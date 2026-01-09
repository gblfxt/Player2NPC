package com.gblfxt.player2npc.command;

import com.gblfxt.player2npc.Config;
import com.gblfxt.player2npc.Player2NPC;
import com.gblfxt.player2npc.entity.CompanionEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class CompanionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("companion")
                .then(Commands.literal("summon")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> summonCompanion(ctx, StringArgumentType.getString(ctx, "name")))
                        )
                        .executes(ctx -> summonCompanion(ctx, "Companion"))
                )
                .then(Commands.literal("dismiss")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> dismissCompanion(ctx, StringArgumentType.getString(ctx, "name")))
                        )
                        .executes(ctx -> dismissAllCompanions(ctx))
                )
                .then(Commands.literal("list")
                        .executes(CompanionCommand::listCompanions)
                )
                .then(Commands.literal("help")
                        .executes(CompanionCommand::showHelp)
                )
                .executes(CompanionCommand::showHelp)
        );
    }

    private static int summonCompanion(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        // Check companion limit
        List<CompanionEntity> existing = player.level().getEntitiesOfClass(
                CompanionEntity.class,
                player.getBoundingBox().inflate(256),
                c -> c.isOwner(player)
        );

        int maxCompanions = Config.MAX_COMPANIONS_PER_PLAYER.get();
        if (existing.size() >= maxCompanions) {
            source.sendFailure(Component.literal("You already have " + maxCompanions + " companions. Dismiss one first."));
            return 0;
        }

        // Check if name is already taken
        boolean nameTaken = existing.stream()
                .anyMatch(c -> c.getCompanionName().equalsIgnoreCase(name));
        if (nameTaken) {
            source.sendFailure(Component.literal("You already have a companion named '" + name + "'."));
            return 0;
        }

        // Spawn companion
        CompanionEntity companion = new CompanionEntity(Player2NPC.COMPANION.get(), player.level());
        companion.setCompanionName(name);
        companion.setOwner(player);
        companion.setPos(player.getX() + 1, player.getY(), player.getZ() + 1);

        player.level().addFreshEntity(companion);

        source.sendSuccess(() -> Component.literal("Summoned companion '" + name + "'! Use @" + name + " <message> to talk to them."), false);
        Player2NPC.LOGGER.info("Player {} summoned companion '{}'", player.getName().getString(), name);

        return 1;
    }

    private static int dismissCompanion(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        List<CompanionEntity> companions = player.level().getEntitiesOfClass(
                CompanionEntity.class,
                player.getBoundingBox().inflate(256),
                c -> c.isOwner(player) && c.getCompanionName().equalsIgnoreCase(name)
        );

        if (companions.isEmpty()) {
            source.sendFailure(Component.literal("No companion named '" + name + "' found."));
            return 0;
        }

        for (CompanionEntity companion : companions) {
            companion.discard();
        }

        source.sendSuccess(() -> Component.literal("Dismissed companion '" + name + "'."), false);
        return 1;
    }

    private static int dismissAllCompanions(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        List<CompanionEntity> companions = player.level().getEntitiesOfClass(
                CompanionEntity.class,
                player.getBoundingBox().inflate(256),
                c -> c.isOwner(player)
        );

        if (companions.isEmpty()) {
            source.sendFailure(Component.literal("You have no companions to dismiss."));
            return 0;
        }

        int count = companions.size();
        for (CompanionEntity companion : companions) {
            companion.discard();
        }

        source.sendSuccess(() -> Component.literal("Dismissed " + count + " companion(s)."), false);
        return 1;
    }

    private static int listCompanions(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        List<CompanionEntity> companions = player.level().getEntitiesOfClass(
                CompanionEntity.class,
                player.getBoundingBox().inflate(256),
                c -> c.isOwner(player)
        );

        if (companions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("You have no companions. Use /companion summon <name> to create one."), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder("Your companions:\n");
        for (CompanionEntity companion : companions) {
            String state = companion.getAIController() != null ?
                    companion.getAIController().getCurrentState().name() : "UNKNOWN";
            sb.append(" - ").append(companion.getCompanionName())
                    .append(" (").append(state).append(")")
                    .append(" HP: ").append((int) companion.getHealth())
                    .append("/").append((int) companion.getMaxHealth())
                    .append("\n");
        }

        source.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        String help = """
Player2NPC Commands:
  /companion summon <name> - Summon a new AI companion
  /companion dismiss <name> - Dismiss a specific companion
  /companion dismiss - Dismiss all companions
  /companion list - List your companions

Chat with companions using: @<name> <message>
Example: @Alex follow me""";

        source.sendSuccess(() -> Component.literal(help), false);
        return 1;
    }
}
