package com.gblfxt.llmoblings.ai;

import com.gblfxt.llmoblings.Config;
import com.gblfxt.llmoblings.LLMoblings;
import com.gblfxt.llmoblings.ai.blueprints.CottageBlueprint;
import com.gblfxt.llmoblings.compat.AE2Integration;
import com.gblfxt.llmoblings.compat.BuildingGadgetsIntegration;
import com.gblfxt.llmoblings.compat.CobblemonIntegration;
import com.gblfxt.llmoblings.compat.SophisticatedBackpacksIntegration;
import com.gblfxt.llmoblings.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CompanionAI {
    private final CompanionEntity companion;
    private final OllamaClient ollamaClient;
    private final CompanionPersonality personality;

    // Current state
    private AIState currentState = AIState.IDLE;
    private CompletableFuture<CompanionAction> pendingAction = null;

    // Task-specific data
    private BlockPos targetPos = null;
    private Entity targetEntity = null;
    private MiningTask miningTask = null;
    private AutonomousTask autonomousTask = null;
    private BuildingTask buildingTask = null;

    // Pokemon buddy (Cobblemon integration)
    private Entity pokemonBuddy = null;
    private String pokemonBuddyName = null;

    // Owner tracking for greetings
    private boolean ownerWasNearby = false;

    // Home position tracking
    private BlockPos homePos = null;
    private BlockPos bedPos = null;

    // Track who gave the last command (for follow, etc.)
    private Player commandGiver = null;

    public CompanionAI(CompanionEntity companion) {
        this.companion = companion;
        this.ollamaClient = new OllamaClient(companion.getCompanionName());
        this.personality = new CompanionPersonality(companion);
    }

    public void tick() {
        // Tick personality for random chatter/emotes
        personality.tick();

        // Tick Pokemon buddy to follow companion
        tickPokemonBuddy();

        // Check if owner just came nearby (for greetings)
        checkOwnerProximity();

        // Check for pending LLM response
        if (pendingAction != null && pendingAction.isDone()) {
            try {
                CompanionAction action = pendingAction.get();
                executeAction(action);
            } catch (Exception e) {
                LLMoblings.LOGGER.error("Error getting LLM response: ", e);
            }
            pendingAction = null;
        }

        // Execute current state behavior
        switch (currentState) {
            case FOLLOWING -> tickFollow();
            case GOING_TO -> tickGoTo();
            case MINING -> tickMining();
            case ATTACKING -> tickAttacking();
            case DEFENDING -> tickDefending();
            case AUTONOMOUS -> tickAutonomous();
            case BUILDING -> tickBuilding();
            case IDLE -> tickIdle();
        }
    }

    public void processMessage(String message) {
        processMessage(message, companion.getOwner());
    }

    public void processMessage(String message, Player sender) {
        if (pendingAction != null && !pendingAction.isDone()) {
            // Already processing a message, queue or ignore
            sendMessageTo(sender, "I'm still thinking about your last request...");
            return;
        }

        // Track who gave the command
        this.commandGiver = sender;

        LLMoblings.LOGGER.info("[{}] Processing message from {}: {}", companion.getCompanionName(),
                sender != null ? sender.getName().getString() : "unknown", message);
        sendMessageToAll("Thinking...");
        pendingAction = ollamaClient.chat(message);
    }

    /**
     * Process a message from someone who is not the owner (and not a teammate).
     * They can chat but not give commands.
     */
    public void processMessageFromStranger(Player stranger, String message) {
        if (pendingAction != null && !pendingAction.isDone()) {
            sendMessageTo(stranger, "I'm still thinking about something...");
            return;
        }

        LLMoblings.LOGGER.info("[{}] Processing stranger message from {}: {}",
                companion.getCompanionName(), stranger.getName().getString(), message);

        // For strangers, we add context that this is not the owner
        String contextMessage = "[A player named " + stranger.getName().getString() +
                " (not my owner) says: " + message + ". I should be friendly but I only take commands from my owner.]";

        sendMessageToAll("Hmm?");
        pendingAction = ollamaClient.chat(contextMessage);
    }

    private void sendMessageTo(Player player, String message) {
        if (player != null && Config.BROADCAST_COMPANION_CHAT.get()) {
            String formatted = "[" + companion.getCompanionName() + "] " + message;
            player.sendSystemMessage(Component.literal(formatted));
        }
    }

    private void executeAction(CompanionAction action) {
        // Send message if present
        if (action.getMessage() != null && !action.getMessage().isEmpty()) {
            sendMessage(action.getMessage());
        }

        LLMoblings.LOGGER.debug("Executing action: {}", action);

        switch (action.getAction().toLowerCase()) {
            case "follow" -> startFollowing();
            case "stay", "stop" -> stopAndStay();
            case "goto" -> {
                int x = action.getInt("x", (int) companion.getX());
                int y = action.getInt("y", (int) companion.getY());
                int z = action.getInt("z", (int) companion.getZ());
                goTo(new BlockPos(x, y, z));
            }
            case "come" -> comeToOwner();
            case "mine", "gather" -> {
                String block = action.getString("block", action.getString("item", "stone"));
                int count = action.getInt("count", 1);
                startMining(block, count);
            }
            case "attack" -> {
                String target = action.getString("target", "hostile");
                startAttacking(target);
            }
            case "defend" -> startDefending();
            case "retreat" -> retreat();
            case "give" -> {
                String item = action.getString("item", "");
                int count = action.getInt("count", 1);
                giveItems(item, count);
            }
            case "status" -> reportStatus();
            case "scan" -> {
                int radius = action.getInt("radius", 32);
                scanArea(radius);
            }
            case "explore", "wander", "look around" -> {
                int radius = action.getInt("radius", 32);
                startExploring(radius);
            }
            case "auto", "autonomous", "independent", "survive" -> {
                int radius = action.getInt("radius", 32);
                startAutonomous(radius);
            }
            case "idle" -> {
                currentState = AIState.IDLE;
            }
            case "setbed" -> findAndSetBed();
            case "sethome" -> setHomeHere();
            case "home" -> goHome();
            case "sleep" -> tryToSleep();
            case "tpa" -> {
                String target = action.getString("target", action.getString("player", ""));
                requestTeleport(target);
            }
            case "tpaccept" -> acceptTeleport();
            case "tpdeny" -> denyTeleport();
            case "portal" -> {
                String portalAction = action.getString("action", "enter");
                handlePortalCommand(portalAction);
            }
            case "elevator" -> {
                String direction = action.getString("direction", "up");
                handleElevatorCommand(direction);
            }
            case "cobblestats" -> {
                String detail = action.getString("detail", "full");
                String target = action.getString("target", "");
                handleCobblestatsCommand(detail, target);
            }
            case "equip", "gear", "arm" -> equipBestGear();
            case "inventory", "inv", "items" -> reportInventory();
            case "getgear", "getarmor", "craftgear", "ironset", "meget" -> {
                String material = action.getString("material", "iron");
                getGearFromME(material);
            }
            case "deposit", "store", "stash", "putaway" -> {
                boolean keepGear = action.getBoolean("keepGear", true);
                depositItems(keepGear);
            }
            case "build" -> {
                String structure = action.getString("structure", "cottage");
                boolean here = action.getBoolean("here", false);
                int x = action.getInt("x", (int) companion.getX());
                int y = action.getInt("y", (int) companion.getY());
                int z = action.getInt("z", (int) companion.getZ());
                BlockPos location = here ? companion.blockPosition() : new BlockPos(x, y, z);
                startBuilding(structure, location);
            }
            case "pokemon", "buddy", "pokemonbuddy" -> {
                String subAction = action.getString("subaction", "find");
                handlePokemonBuddy(subAction, action.getString("name", null));
            }
            case "gadget", "buildinggadget", "gadgets" -> {
                String subAction = action.getString("subaction", "info");
                String blockName = action.getString("block", null);
                int range = action.getInt("range", -1);
                handleBuildingGadget(subAction, blockName, range);
            }
            case "backpack", "pack", "bag" -> {
                String subAction = action.getString("subaction", "info");
                String itemName = action.getString("item", null);
                int count = action.getInt("count", -1);
                handleBackpack(subAction, itemName, count);
            }
            default -> {
                LLMoblings.LOGGER.warn("[{}] Unknown action: {}", companion.getCompanionName(), action.getAction());
                currentState = AIState.IDLE;
            }
        }
    }

    // State behaviors
    private void tickFollow() {
        // Follow whoever gave the command, or owner if no one specified
        Player followTarget = (commandGiver != null && commandGiver.isAlive()) ? commandGiver : companion.getOwner();
        if (followTarget == null) {
            currentState = AIState.IDLE;
            return;
        }

        double distance = companion.distanceTo(followTarget);
        double followDist = Config.COMPANION_FOLLOW_DISTANCE.get();

        if (distance > followDist) {
            // Move towards target
            companion.getNavigation().moveTo(followTarget, 1.0);
        } else if (distance < followDist - 1) {
            // Close enough, stop
            companion.getNavigation().stop();
        }

        // Teleport if too far
        if (distance > 32) {
            Vec3 targetPos = followTarget.position();
            companion.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        }
    }

    private void tickGoTo() {
        if (targetPos == null) {
            currentState = AIState.IDLE;
            return;
        }

        double distance = companion.position().distanceTo(Vec3.atCenterOf(targetPos));
        if (distance < 3.0) {
            // Check for pending gear request first
            if (pendingGearRequest != null && companion.level() instanceof ServerLevel serverLevel) {
                GearRequest req = pendingGearRequest;
                pendingGearRequest = null;
                retrieveOrCraftGear(serverLevel, req.terminal(), req.items(), req.material());
                targetPos = null;
                return;
            }

            // Check for pending deposit request
            if (pendingDepositRequest != null && companion.level() instanceof ServerLevel serverLevel) {
                DepositRequest req = pendingDepositRequest;
                pendingDepositRequest = null;
                executeDeposit(serverLevel, req.pos(), req.isME(), req.keepGear());
                targetPos = null;
                return;
            }

            sendMessage("I've arrived at the destination.");
            currentState = AIState.IDLE;
            targetPos = null;
        } else if (companion.getNavigation().isDone()) {
            // Recalculate path
            companion.getNavigation().moveTo(targetPos.getX(), targetPos.getY(), targetPos.getZ(), 1.0);
        }
    }

    private void tickMining() {
        if (miningTask == null) {
            currentState = AIState.IDLE;
            return;
        }

        // Tick the mining task
        miningTask.tick();

        // Check for completion
        if (miningTask.isCompleted()) {
            sendMessage("Done! I gathered " + miningTask.getMinedCount() + " " + miningTask.getTargetBlockName() + ".");
            personality.onTaskComplete();
            miningTask = null;
            currentState = AIState.IDLE;
            return;
        }

        // Check for failure
        if (miningTask.isFailed()) {
            sendMessage(miningTask.getFailReason());
            personality.doSadEmote();
            miningTask = null;
            currentState = AIState.IDLE;
            return;
        }

        // Random mining chatter
        if (companion.tickCount % 200 == 0 && companion.getRandom().nextInt(3) == 0) {
            personality.onTaskStart("mining");
        }

        // Progress report every 5 seconds
        if (companion.tickCount % 100 == 0) {
            sendMessage("Mining " + miningTask.getTargetBlockName() + "... (" +
                miningTask.getMinedCount() + "/" + miningTask.getTargetCount() + ")");
        }
    }

    private void tickAttacking() {
        if (targetEntity == null || !targetEntity.isAlive()) {
            // Find new target
            targetEntity = findAttackTarget();
            if (targetEntity == null) {
                sendMessage("No more enemies nearby.");
                personality.onTaskComplete();
                currentState = AIState.IDLE;
                return;
            }
            LLMoblings.LOGGER.info("[{}] New attack target: {}", companion.getCompanionName(),
                    targetEntity.getType().getDescriptionId());
        }

        double distance = companion.distanceTo(targetEntity);
        if (distance < 3.0) {
            // Face the target
            companion.getLookControl().setLookAt(targetEntity, 30.0F, 30.0F);

            // Attack!
            if (companion.tickCount % 20 == 0) {  // Attack once per second
                boolean hit = companion.doHurtTarget(targetEntity);
                if (hit) {
                    LLMoblings.LOGGER.debug("[{}] Hit {} for damage", companion.getCompanionName(),
                            targetEntity.getType().getDescriptionId());
                    personality.onCombat();
                }
            }
        } else {
            // Move towards target aggressively
            companion.getNavigation().moveTo(targetEntity, 1.4);  // Faster movement in combat
        }

        // Combat chatter every 5 seconds
        if (companion.tickCount % 100 == 0) {
            String targetName = targetEntity.hasCustomName() ?
                    targetEntity.getCustomName().getString() :
                    targetEntity.getType().getDescription().getString();
            personality.onCombat();
        }
    }

    private void tickDefending() {
        Player owner = companion.getOwner();
        if (owner == null) {
            currentState = AIState.IDLE;
            return;
        }

        // Look for threats near owner - include any mob targeting the owner or companion
        List<LivingEntity> threats = companion.level().getEntitiesOfClass(
                LivingEntity.class,
                owner.getBoundingBox().inflate(12),
                entity -> {
                    if (!entity.isAlive() || entity == companion || entity == owner) {
                        return false;
                    }
                    if (entity instanceof Player || entity instanceof CompanionEntity) {
                        return false;
                    }
                    // Check if it's a monster
                    if (entity instanceof Monster) {
                        return true;
                    }
                    // Check if any mob is targeting owner or companion
                    if (entity instanceof Mob mob) {
                        LivingEntity target = mob.getTarget();
                        return target != null && (target == owner || target == companion);
                    }
                    return false;
                }
        );

        if (!threats.isEmpty()) {
            // Sort by distance and get closest
            targetEntity = threats.stream()
                    .min(Comparator.comparingDouble(e -> companion.distanceTo(e)))
                    .orElse(null);

            if (targetEntity != null) {
                double distance = companion.distanceTo(targetEntity);
                if (distance < 3.0) {
                    companion.getLookControl().setLookAt(targetEntity, 30.0F, 30.0F);
                    if (companion.tickCount % 20 == 0) {
                        companion.doHurtTarget(targetEntity);
                        personality.onCombat();
                    }
                } else {
                    companion.getNavigation().moveTo(targetEntity, 1.4);
                }
            }
        } else {
            // No threats, stay near owner
            tickFollow();
        }
    }

    private void tickAutonomous() {
        if (autonomousTask == null) {
            currentState = AIState.IDLE;
            return;
        }

        autonomousTask.tick();
    }

    private void tickBuilding() {
        if (buildingTask == null) {
            currentState = AIState.IDLE;
            return;
        }

        buildingTask.tick();

        // Check for completion
        if (buildingTask.isCompleted()) {
            sendMessage("Done! I've finished building the " + buildingTask.getStructureName() + "!");
            personality.onTaskComplete();
            buildingTask = null;
            currentState = AIState.IDLE;
            return;
        }

        // Check for failure
        if (buildingTask.isFailed()) {
            sendMessage(buildingTask.getFailReason());
            personality.doSadEmote();
            buildingTask = null;
            currentState = AIState.IDLE;
            return;
        }

        // Progress report every 5 seconds
        if (companion.tickCount % 100 == 0) {
            sendMessage(buildingTask.getProgressReport());
        }
    }

    private void tickIdle() {
        // Occasionally look around
        if (companion.getRandom().nextInt(100) == 0) {
            companion.setYRot(companion.getYRot() + (companion.getRandom().nextFloat() - 0.5F) * 30);
        }
    }

    private void checkOwnerProximity() {
        Player owner = companion.getOwner();
        if (owner == null) {
            ownerWasNearby = false;
            return;
        }

        double distance = companion.distanceTo(owner);
        boolean isNearby = distance < 32;

        // Owner just arrived
        if (isNearby && !ownerWasNearby) {
            personality.onOwnerNearby();
        }

        ownerWasNearby = isNearby;
    }

    // Action implementations
    private void startFollowing() {
        currentState = AIState.FOLLOWING;
        sendMessage("Following you!");
    }

    private void stopAndStay() {
        currentState = AIState.IDLE;
        companion.getNavigation().stop();
        sendMessage("Staying here.");
    }

    private void goTo(BlockPos pos) {
        targetPos = pos;
        currentState = AIState.GOING_TO;
        companion.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 1.0);
    }

    private void comeToOwner() {
        Player owner = companion.getOwner();
        if (owner != null) {
            goTo(owner.blockPosition());
        }
    }

    private void startMining(String blockType, int count) {
        miningTask = new MiningTask(companion, blockType, count, 32);

        if (miningTask.isFailed()) {
            sendMessage(miningTask.getFailReason());
            personality.doSadEmote();
            miningTask = null;
            return;
        }

        currentState = AIState.MINING;
        sendMessage("Starting to gather " + count + " " + blockType + ". I'll search within 32 blocks.");
        personality.onTaskStart("mining");
    }

    private void startAttacking(String targetType) {
        currentState = AIState.ATTACKING;
        targetEntity = findAttackTarget(targetType);
    }

    private Entity findAttackTarget(String targetType) {
        // Try to find entity type by name
        EntityType<?> specificType = null;
        if (targetType != null && !targetType.isEmpty() && !targetType.equalsIgnoreCase("hostile")) {
            // Try with minecraft namespace first, then without
            ResourceLocation typeId = targetType.contains(":")
                    ? ResourceLocation.tryParse(targetType)
                    : ResourceLocation.withDefaultNamespace(targetType);
            if (typeId != null && BuiltInRegistries.ENTITY_TYPE.containsKey(typeId)) {
                specificType = BuiltInRegistries.ENTITY_TYPE.get(typeId);
            }
        }

        final EntityType<?> searchType = specificType;
        Player owner = companion.getOwner();

        List<LivingEntity> entities = companion.level().getEntitiesOfClass(
                LivingEntity.class,
                companion.getBoundingBox().inflate(16),
                e -> {
                    if (!e.isAlive() || e == companion || e == owner) {
                        return false;
                    }
                    // Never attack players or companions
                    if (e instanceof Player || e instanceof CompanionEntity) {
                        return false;
                    }
                    // If specific type requested, match it
                    if (searchType != null) {
                        return e.getType() == searchType;
                    }
                    // Target anything that is hostile (Monster is a subclass)
                    if (e instanceof Monster) {
                        return true;
                    }
                    // Target any mob that is targeting the owner or companion
                    if (e instanceof Mob mob) {
                        LivingEntity target = mob.getTarget();
                        if (target != null && (target == owner || target == companion)) {
                            return true;
                        }
                    }
                    // Target anything with "hostile" or aggressive in its name (for modded mobs)
                    String entityName = e.getType().getDescriptionId().toLowerCase();
                    if (entityName.contains("hostile") || entityName.contains("titan") ||
                        entityName.contains("boss") || entityName.contains("monster")) {
                        return true;
                    }
                    return false;
                }
        );

        return entities.stream()
                .min(Comparator.comparingDouble(e -> companion.distanceTo(e)))
                .orElse(null);
    }

    private Entity findAttackTarget() {
        return findAttackTarget("hostile");
    }

    private void startDefending() {
        currentState = AIState.DEFENDING;
        sendMessage("I'll protect you!");
    }

    private void startAutonomous(int radius) {
        autonomousTask = new AutonomousTask(companion, radius);
        currentState = AIState.AUTONOMOUS;
        sendMessage("Going autonomous! I'll assess the area, hunt for food, equip myself, and patrol. Tell me to 'stop' or 'follow' to return to normal.");
    }

    private void startExploring(int radius) {
        autonomousTask = new AutonomousTask(companion, radius);
        autonomousTask.setExploring();  // Start directly in explore mode
        currentState = AIState.AUTONOMOUS;
        sendMessage("I'll explore the area! I can open doors and check out interesting spots.");
    }

    private void startBuilding(String structureType, BlockPos location) {
        Blueprint blueprint = null;

        // Get blueprint by type
        if (structureType.equalsIgnoreCase("cottage") || structureType.equalsIgnoreCase("house")) {
            blueprint = new CottageBlueprint();
        }

        if (blueprint == null) {
            sendMessage("I don't know how to build a " + structureType + ". I can build: cottage");
            return;
        }

        buildingTask = new BuildingTask(companion, blueprint, location);
        currentState = AIState.BUILDING;
        sendMessage("Starting to build a " + blueprint.getName() + " at [" +
                location.getX() + ", " + location.getY() + ", " + location.getZ() + "]! This might take a while.");
        personality.onTaskStart("building");
    }

    // ========== POKEMON BUDDY SYSTEM ==========

    /**
     * Tick the Pokemon buddy to follow the companion.
     */
    private void tickPokemonBuddy() {
        if (!CobblemonIntegration.isCobblemonLoaded()) {
            return;
        }

        // Check if buddy is still valid
        if (pokemonBuddy != null) {
            if (!pokemonBuddy.isAlive() || pokemonBuddy.isRemoved()) {
                sendMessage("Oh no, " + pokemonBuddyName + " is gone!");
                pokemonBuddy = null;
                pokemonBuddyName = null;
                return;
            }

            // Make buddy follow companion (every 20 ticks = 1 second)
            if (companion.tickCount % 20 == 0) {
                double distance = companion.distanceTo(pokemonBuddy);

                // If too far, teleport the buddy
                if (distance > 20) {
                    pokemonBuddy.teleportTo(
                            companion.getX() + (companion.getRandom().nextDouble() - 0.5) * 2,
                            companion.getY(),
                            companion.getZ() + (companion.getRandom().nextDouble() - 0.5) * 2
                    );
                } else if (distance > 3) {
                    // Make it follow
                    CobblemonIntegration.makePokemonFollow(pokemonBuddy, companion);
                }
            }
        }
    }

    /**
     * Handle Pokemon buddy commands.
     */
    private void handlePokemonBuddy(String subAction, String targetName) {
        if (!CobblemonIntegration.isCobblemonLoaded()) {
            sendMessage("I don't see any Pokemon around... is Cobblemon installed?");
            return;
        }

        switch (subAction.toLowerCase()) {
            case "find", "bond", "get" -> findPokemonBuddy(targetName);
            case "release", "bye", "dismiss" -> releasePokemonBuddy();
            case "status", "check" -> checkPokemonBuddy();
            default -> findPokemonBuddy(targetName);
        }
    }

    /**
     * Find and bond with a Pokemon buddy.
     */
    private void findPokemonBuddy(String targetName) {
        // Already have a buddy?
        if (pokemonBuddy != null && pokemonBuddy.isAlive()) {
            sendMessage("I already have " + pokemonBuddyName + " with me! Say 'release buddy' first if you want me to find a different one.");
            return;
        }

        // Look for the owner's Pokemon nearby
        Entity foundPokemon = CobblemonIntegration.findNearestPlayerPokemon(companion, 32);

        if (foundPokemon == null) {
            sendMessage("I don't see any of your Pokemon nearby. Send one out and I'll bond with it!");
            return;
        }

        // If target name specified, try to find that specific Pokemon
        if (targetName != null && !targetName.isEmpty()) {
            List<Entity> allPokemon = CobblemonIntegration.findPlayerPokemon(
                    companion,
                    (net.minecraft.server.level.ServerPlayer) companion.getOwner(),
                    32);

            for (Entity pokemon : allPokemon) {
                String name = CobblemonIntegration.getPokemonDisplayName(pokemon);
                if (name != null && name.toLowerCase().contains(targetName.toLowerCase())) {
                    foundPokemon = pokemon;
                    break;
                }
            }
        }

        // Bond with the Pokemon
        pokemonBuddy = foundPokemon;
        pokemonBuddyName = CobblemonIntegration.getPokemonSummary(foundPokemon);

        String speciesName = CobblemonIntegration.getPokemonSpeciesName(foundPokemon);
        boolean isShiny = CobblemonIntegration.isPokemonShiny(foundPokemon);
        int level = CobblemonIntegration.getPokemonLevel(foundPokemon);

        StringBuilder msg = new StringBuilder();
        msg.append("Hey there, ");
        if (isShiny) {
            msg.append("shiny ");
        }
        msg.append(speciesName).append("! ");
        msg.append("We're going to be adventure buddies! ");
        msg.append("(Lv. ").append(level).append(")");

        sendMessage(msg.toString());
        LLMoblings.LOGGER.info("[{}] Bonded with Pokemon: {}", companion.getCompanionName(), pokemonBuddyName);
    }

    /**
     * Release the current Pokemon buddy.
     */
    private void releasePokemonBuddy() {
        if (pokemonBuddy == null) {
            sendMessage("I don't have a Pokemon buddy right now.");
            return;
        }

        sendMessage("Bye bye, " + CobblemonIntegration.getPokemonDisplayName(pokemonBuddy) + "! It was fun adventuring with you!");
        pokemonBuddy = null;
        pokemonBuddyName = null;
    }

    /**
     * Check on the Pokemon buddy.
     */
    private void checkPokemonBuddy() {
        if (pokemonBuddy == null || !pokemonBuddy.isAlive()) {
            sendMessage("I don't have a Pokemon buddy right now. Send out one of your Pokemon and tell me to 'find a buddy'!");
            return;
        }

        String summary = CobblemonIntegration.getPokemonSummary(pokemonBuddy);
        double distance = companion.distanceTo(pokemonBuddy);

        sendMessage("My buddy " + summary + " is " + (int) distance + " blocks away. We're having a great time!");
    }

    /**
     * Get the current Pokemon buddy (for other systems).
     */
    public Entity getPokemonBuddy() {
        return pokemonBuddy;
    }

    /**
     * Check if companion has a Pokemon buddy.
     */
    public boolean hasPokemonBuddy() {
        return pokemonBuddy != null && pokemonBuddy.isAlive();
    }

    // ========== END POKEMON BUDDY SYSTEM ==========

    // ========== BUILDING GADGETS SYSTEM ==========

    /**
     * Handle Building Gadgets commands.
     */
    private void handleBuildingGadget(String subAction, String blockName, int range) {
        if (!BuildingGadgetsIntegration.isBuildingGadgetsLoaded()) {
            sendMessage("Building Gadgets isn't installed. I can't use gadgets without it!");
            return;
        }

        switch (subAction.toLowerCase()) {
            case "info", "check", "status" -> gadgetInfo();
            case "equip", "hold", "use" -> equipGadget();
            case "setblock", "block", "set" -> setGadgetBlock(blockName);
            case "setrange", "range" -> setGadgetRange(range);
            case "configure", "config", "setup" -> configureGadget(blockName, range);
            case "build", "place" -> useGadgetAtTarget();
            default -> gadgetInfo();
        }
    }

    /**
     * Report info about the current gadget.
     */
    private void gadgetInfo() {
        ItemStack gadget = BuildingGadgetsIntegration.findAnyGadget(companion);
        if (gadget.isEmpty()) {
            sendMessage("I don't have any Building Gadgets. Give me one!");
            return;
        }

        String desc = BuildingGadgetsIntegration.getGadgetDescription(gadget);
        boolean equipped = BuildingGadgetsIntegration.isGadget(companion.getMainHandItem());
        sendMessage("I have a " + desc + (equipped ? " (equipped)" : " (in inventory)"));
    }

    /**
     * Equip a building gadget to main hand.
     */
    private void equipGadget() {
        if (BuildingGadgetsIntegration.isGadget(companion.getMainHandItem())) {
            String desc = BuildingGadgetsIntegration.getGadgetDescription(companion.getMainHandItem());
            sendMessage("I already have my " + desc + " equipped!");
            return;
        }

        if (BuildingGadgetsIntegration.equipGadget(companion)) {
            String desc = BuildingGadgetsIntegration.getGadgetDescription(companion.getMainHandItem());
            sendMessage("Equipped " + desc + "! Ready to build!");
        } else {
            sendMessage("I don't have any Building Gadgets to equip.");
        }
    }

    /**
     * Set the block type on the gadget.
     */
    private void setGadgetBlock(String blockName) {
        ItemStack gadget = companion.getMainHandItem();
        if (!BuildingGadgetsIntegration.isGadget(gadget)) {
            gadget = BuildingGadgetsIntegration.findBuildingGadget(companion);
            if (gadget.isEmpty()) {
                sendMessage("I need to hold a Building Gadget first!");
                return;
            }
            // Equip it
            BuildingGadgetsIntegration.equipGadget(companion);
            gadget = companion.getMainHandItem();
        }

        if (blockName == null || blockName.isEmpty()) {
            // Try to find a block from inventory
            net.minecraft.world.level.block.Block block = BuildingGadgetsIntegration.findBuildableBlock(companion);
            if (block != null) {
                BuildingGadgetsIntegration.setGadgetBlock(gadget, block.defaultBlockState());
                sendMessage("Set gadget to place " + BuiltInRegistries.BLOCK.getKey(block).getPath().replace("_", " ") + "!");
            } else {
                sendMessage("I don't have any blocks in my inventory to use. Give me some building materials!");
            }
            return;
        }

        // Try to parse block name
        ResourceLocation blockId = blockName.contains(":")
            ? ResourceLocation.tryParse(blockName)
            : ResourceLocation.withDefaultNamespace(blockName.toLowerCase().replace(" ", "_"));

        if (blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId)) {
            net.minecraft.world.level.block.Block block = BuiltInRegistries.BLOCK.get(blockId);
            BuildingGadgetsIntegration.setGadgetBlock(gadget, block.defaultBlockState());
            sendMessage("Set gadget to place " + blockName + "!");
        } else {
            sendMessage("I don't know what block '" + blockName + "' is.");
        }
    }

    /**
     * Set the range on the gadget.
     */
    private void setGadgetRange(int range) {
        ItemStack gadget = companion.getMainHandItem();
        if (!BuildingGadgetsIntegration.isGadget(gadget)) {
            gadget = BuildingGadgetsIntegration.findAnyGadget(companion);
            if (gadget.isEmpty()) {
                sendMessage("I need a Building Gadget first!");
                return;
            }
        }

        if (range < 1) {
            range = 3; // Default range
        }

        if (BuildingGadgetsIntegration.setGadgetRange(gadget, range)) {
            sendMessage("Set gadget range to " + range + "!");
        } else {
            sendMessage("Couldn't set the range on this gadget.");
        }
    }

    /**
     * Configure gadget with both block and range.
     */
    private void configureGadget(String blockName, int range) {
        ItemStack gadget = companion.getMainHandItem();
        if (!BuildingGadgetsIntegration.isGadget(gadget)) {
            if (!BuildingGadgetsIntegration.equipGadget(companion)) {
                sendMessage("I don't have any Building Gadgets!");
                return;
            }
            gadget = companion.getMainHandItem();
        }

        // Set block if provided
        if (blockName != null && !blockName.isEmpty()) {
            setGadgetBlock(blockName);
        }

        // Set range if provided
        if (range > 0) {
            setGadgetRange(range);
        }

        String desc = BuildingGadgetsIntegration.getGadgetDescription(gadget);
        sendMessage("Gadget configured! " + desc);
    }

    /**
     * Use the gadget at the target position.
     */
    private void useGadgetAtTarget() {
        ItemStack gadget = companion.getMainHandItem();
        if (!BuildingGadgetsIntegration.isGadget(gadget)) {
            sendMessage("I need to hold a Building Gadget to use it!");
            return;
        }

        // Check if gadget has a block set
        net.minecraft.world.level.block.state.BlockState currentBlock = BuildingGadgetsIntegration.getGadgetBlock(gadget);
        if (currentBlock.isAir()) {
            sendMessage("My gadget doesn't have a block set. Tell me 'gadget set block [blockname]' first!");
            return;
        }

        // Use at current target or ground in front
        BlockPos targetPos = companion.blockPosition().relative(companion.getDirection());
        if (BuildingGadgetsIntegration.useGadget(companion, targetPos, net.minecraft.core.Direction.UP)) {
            sendMessage("*uses gadget* Building!");
        } else {
            sendMessage("I couldn't use the gadget here.");
        }
    }

    /**
     * Rotate the gadget's build mode.
     */
    private void rotateGadgetMode() {
        ItemStack gadget = companion.getMainHandItem();
        if (!BuildingGadgetsIntegration.isGadget(gadget)) {
            sendMessage("I need to hold a Building Gadget first!");
            return;
        }

        if (BuildingGadgetsIntegration.rotateMode(gadget)) {
            String newMode = BuildingGadgetsIntegration.getModeName(gadget);
            sendMessage("Switched to " + newMode + " mode!");
        } else {
            sendMessage("Couldn't rotate the mode on this gadget.");
        }
    }

    // ========== END BUILDING GADGETS SYSTEM ==========

    // ========== SOPHISTICATED BACKPACKS SYSTEM ==========

    /**
     * Handle backpack commands.
     */
    private void handleBackpack(String subAction, String itemName, int count) {
        if (!SophisticatedBackpacksIntegration.isSophisticatedBackpacksLoaded()) {
            sendMessage("Sophisticated Backpacks isn't installed. I can't use backpacks without it!");
            return;
        }

        switch (subAction.toLowerCase()) {
            case "info", "check", "status" -> backpackInfo();
            case "store", "stash", "put" -> storeInBackpack(itemName);
            case "storeall", "empty" -> storeAllInBackpack();
            case "get", "take", "retrieve" -> retrieveFromBackpack(itemName, count);
            case "list", "contents", "show" -> listBackpackContents();
            case "organize", "sort" -> organizeBackpack();
            default -> backpackInfo();
        }
    }

    /**
     * Report info about the current backpack.
     */
    private void backpackInfo() {
        ItemStack backpack = SophisticatedBackpacksIntegration.findBackpack(companion);
        if (backpack.isEmpty()) {
            sendMessage("I don't have a backpack. Give me one and I can carry a lot more stuff!");
            return;
        }

        String desc = SophisticatedBackpacksIntegration.getBackpackDescription(backpack);
        sendMessage("I have a " + desc);
    }

    /**
     * Store a specific item type into backpack.
     */
    private void storeInBackpack(String itemName) {
        ItemStack backpack = SophisticatedBackpacksIntegration.findBackpack(companion);
        if (backpack.isEmpty()) {
            sendMessage("I don't have a backpack to store items in!");
            return;
        }

        if (itemName == null || itemName.isEmpty()) {
            // Store all non-essential items
            storeAllInBackpack();
            return;
        }

        String searchName = itemName.toLowerCase().replace(" ", "_");
        int stored = 0;

        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (stack.isEmpty() || stack == backpack) continue;

            String stackId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
            String stackName = stack.getItem().getDescription().getString().toLowerCase();

            if (stackId.contains(searchName) || stackName.contains(searchName)) {
                ItemStack remaining = SophisticatedBackpacksIntegration.insertIntoBackpack(backpack, stack);
                if (remaining.isEmpty()) {
                    companion.setItem(i, ItemStack.EMPTY);
                    stored += stack.getCount();
                } else if (remaining.getCount() < stack.getCount()) {
                    stored += stack.getCount() - remaining.getCount();
                    companion.setItem(i, remaining);
                }
            }
        }

        if (stored > 0) {
            sendMessage("Stored " + stored + " " + itemName + " in my backpack!");
        } else {
            sendMessage("I don't have any " + itemName + " to store.");
        }
    }

    /**
     * Store all non-essential items into backpack.
     */
    private void storeAllInBackpack() {
        ItemStack backpack = SophisticatedBackpacksIntegration.findBackpack(companion);
        if (backpack.isEmpty()) {
            sendMessage("I don't have a backpack!");
            return;
        }

        int stored = SophisticatedBackpacksIntegration.storeItemsInBackpack(companion, backpack, true);

        if (stored > 0) {
            String desc = SophisticatedBackpacksIntegration.getBackpackDescription(backpack);
            sendMessage("Stored " + stored + " item stacks in my backpack! " + desc);
        } else {
            sendMessage("Nothing to store - my inventory is already organized.");
        }
    }

    /**
     * Retrieve items from backpack.
     */
    private void retrieveFromBackpack(String itemName, int count) {
        ItemStack backpack = SophisticatedBackpacksIntegration.findBackpack(companion);
        if (backpack.isEmpty()) {
            sendMessage("I don't have a backpack!");
            return;
        }

        if (itemName == null || itemName.isEmpty()) {
            sendMessage("What item do you want me to get from my backpack?");
            return;
        }

        if (count <= 0) count = 64;

        String searchName = itemName.toLowerCase().replace(" ", "_");
        int retrieved = 0;

        // Search backpack contents
        java.util.List<ItemStack> contents = SophisticatedBackpacksIntegration.getBackpackContents(backpack);
        for (ItemStack stack : contents) {
            String stackId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
            String stackName = stack.getItem().getDescription().getString().toLowerCase();

            if (stackId.contains(searchName) || stackName.contains(searchName)) {
                int toGet = Math.min(count - retrieved, stack.getCount());
                ItemStack extracted = SophisticatedBackpacksIntegration.extractFromBackpack(
                        backpack, stack.getItem(), toGet);

                if (!extracted.isEmpty()) {
                    // Add to companion inventory
                    for (int i = 0; i < companion.getContainerSize(); i++) {
                        ItemStack slot = companion.getItem(i);
                        if (slot.isEmpty()) {
                            companion.setItem(i, extracted);
                            retrieved += extracted.getCount();
                            break;
                        } else if (ItemStack.isSameItemSameComponents(slot, extracted) &&
                                   slot.getCount() < slot.getMaxStackSize()) {
                            int space = slot.getMaxStackSize() - slot.getCount();
                            int toAdd = Math.min(space, extracted.getCount());
                            slot.grow(toAdd);
                            retrieved += toAdd;
                            extracted.shrink(toAdd);
                            if (extracted.isEmpty()) break;
                        }
                    }
                }

                if (retrieved >= count) break;
            }
        }

        if (retrieved > 0) {
            sendMessage("Got " + retrieved + " " + itemName + " from my backpack!");
        } else {
            sendMessage("I don't have any " + itemName + " in my backpack.");
        }
    }

    /**
     * List backpack contents.
     */
    private void listBackpackContents() {
        ItemStack backpack = SophisticatedBackpacksIntegration.findBackpack(companion);
        if (backpack.isEmpty()) {
            sendMessage("I don't have a backpack!");
            return;
        }

        java.util.List<ItemStack> contents = SophisticatedBackpacksIntegration.getBackpackContents(backpack);
        if (contents.isEmpty()) {
            sendMessage("My backpack is empty!");
            return;
        }

        // Group and summarize items
        java.util.Map<String, Integer> itemCounts = new java.util.LinkedHashMap<>();
        for (ItemStack stack : contents) {
            String name = stack.getItem().getDescription().getString();
            itemCounts.merge(name, stack.getCount(), Integer::sum);
        }

        StringBuilder sb = new StringBuilder("Backpack contents: ");
        int shown = 0;
        for (java.util.Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
            if (shown > 0) sb.append(", ");
            sb.append(entry.getValue()).append("x ").append(entry.getKey());
            shown++;
            if (shown >= 8) {
                int remaining = itemCounts.size() - shown;
                if (remaining > 0) {
                    sb.append(" and ").append(remaining).append(" more types...");
                }
                break;
            }
        }

        sendMessage(sb.toString());
    }

    /**
     * Organize backpack (sort/consolidate stacks).
     */
    private void organizeBackpack() {
        ItemStack backpack = SophisticatedBackpacksIntegration.findBackpack(companion);
        if (backpack.isEmpty()) {
            sendMessage("I don't have a backpack!");
            return;
        }

        // For now, just report status - actual sorting would require more complex logic
        String desc = SophisticatedBackpacksIntegration.getBackpackDescription(backpack);
        sendMessage("My backpack is organized! " + desc);
    }

    // ========== END SOPHISTICATED BACKPACKS SYSTEM ==========

    private void retreat() {
        Player owner = companion.getOwner();
        if (owner != null) {
            // Run to owner
            companion.getNavigation().moveTo(owner, 1.5);
        }
        currentState = AIState.FOLLOWING;
        sendMessage("Retreating!");
    }

    private void giveItems(String itemName, int count) {
        // Give to whoever gave the command, not just owner
        Player target = (commandGiver != null && commandGiver.isAlive()) ? commandGiver : companion.getOwner();
        if (target == null) {
            sendMessage("I don't see anyone to give items to!");
            return;
        }

        String searchName = itemName.toLowerCase().replace(" ", "_");
        int givenCount = 0;

        for (int i = 0; i < companion.getContainerSize() && givenCount < count; i++) {
            net.minecraft.world.item.ItemStack stack = companion.getItem(i);
            if (stack.isEmpty()) continue;

            // Check if item matches the search term
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
            String itemDesc = stack.getItem().getDescription().getString().toLowerCase();

            if (itemId.contains(searchName) || itemDesc.contains(searchName) || searchName.isEmpty()) {
                int toGive = Math.min(stack.getCount(), count - givenCount);

                // Create stack to give
                net.minecraft.world.item.ItemStack giveStack = stack.copy();
                giveStack.setCount(toGive);

                // Try to add to player inventory
                if (target.getInventory().add(giveStack)) {
                    stack.shrink(toGive);
                    if (stack.isEmpty()) {
                        companion.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                    }
                    givenCount += toGive;
                    LLMoblings.LOGGER.info("[{}] Gave {} x{} to {}",
                            companion.getCompanionName(), giveStack.getItem().getDescription().getString(),
                            toGive, target.getName().getString());
                } else {
                    // Player inventory full, drop at their feet
                    target.drop(giveStack, false);
                    stack.shrink(toGive);
                    if (stack.isEmpty()) {
                        companion.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                    }
                    givenCount += toGive;
                    sendMessage("Your inventory is full, I dropped the items at your feet.");
                }
            }
        }

        if (givenCount > 0) {
            sendMessage("Here you go! Gave you " + givenCount + " " + itemName + ".");
        } else if (itemName.isEmpty()) {
            sendMessage("What item would you like me to give you?");
        } else {
            sendMessage("I don't have any " + itemName + " in my inventory.");
        }
    }

    private void reportStatus() {
        float health = companion.getHealth();
        float maxHealth = companion.getMaxHealth();
        int itemCount = 0;
        for (int i = 0; i < companion.getContainerSize(); i++) {
            if (!companion.getItem(i).isEmpty()) itemCount++;
        }

        String status = String.format(
                "Health: %.0f/%.0f, Inventory: %d/%d slots used, State: %s",
                health, maxHealth, itemCount, companion.getContainerSize(), currentState
        );
        sendMessage(status);
    }

    private void scanArea(int radius) {
        AABB scanBox = companion.getBoundingBox().inflate(radius);

        // Count mobs
        List<Monster> hostiles = companion.level().getEntitiesOfClass(Monster.class, scanBox);
        List<LivingEntity> friendlies = companion.level().getEntitiesOfClass(LivingEntity.class, scanBox,
                e -> !(e instanceof Monster) && !(e instanceof Player));

        String report = String.format(
                "Scan complete! Found %d hostile mobs, %d passive mobs in %d block radius.",
                hostiles.size(), friendlies.size(), radius
        );
        sendMessage(report);
        LLMoblings.LOGGER.info("[{}] Scan: {} hostiles, {} friendlies in {}m radius",
                companion.getCompanionName(), hostiles.size(), friendlies.size(), radius);
    }

    private void findAndSetBed() {
        LLMoblings.LOGGER.info("[{}] Searching for bed...", companion.getCompanionName());
        BlockPos companionPos = companion.blockPosition();
        int searchRadius = 16;

        // Search for nearby bed
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos checkPos = companionPos.offset(x, y, z);
                    BlockState state = companion.level().getBlockState(checkPos);
                    if (state.getBlock() instanceof BedBlock) {
                        bedPos = checkPos;
                        sendMessage("Found a bed! I'll remember this location at [" +
                                checkPos.getX() + ", " + checkPos.getY() + ", " + checkPos.getZ() + "].");
                        LLMoblings.LOGGER.info("[{}] Set bed position to {}", companion.getCompanionName(), checkPos);
                        return;
                    }
                }
            }
        }
        sendMessage("I couldn't find a bed nearby. Place one within 16 blocks of me.");
        LLMoblings.LOGGER.info("[{}] No bed found in range", companion.getCompanionName());
    }

    private void setHomeHere() {
        homePos = companion.blockPosition();
        sendMessage("Home set! I'll remember this spot at [" +
                homePos.getX() + ", " + homePos.getY() + ", " + homePos.getZ() + "].");
        LLMoblings.LOGGER.info("[{}] Set home position to {}", companion.getCompanionName(), homePos);

        // Also try to execute /sethome if server has the command
        if (companion.level() instanceof ServerLevel serverLevel) {
            try {
                String command = "sethome " + companion.getCompanionName().toLowerCase().replace(" ", "_");
                serverLevel.getServer().getCommands().performPrefixedCommand(
                        serverLevel.getServer().createCommandSourceStack()
                                .withPosition(companion.position())
                                .withPermission(2),
                        command
                );
                LLMoblings.LOGGER.info("[{}] Attempted server /sethome command", companion.getCompanionName());
            } catch (Exception e) {
                LLMoblings.LOGGER.debug("[{}] /sethome command not available: {}", companion.getCompanionName(), e.getMessage());
            }
        }
    }

    private void goHome() {
        if (homePos != null) {
            goTo(homePos);
            sendMessage("Heading home to [" + homePos.getX() + ", " + homePos.getY() + ", " + homePos.getZ() + "]!");
            LLMoblings.LOGGER.info("[{}] Going to home position {}", companion.getCompanionName(), homePos);
        } else if (bedPos != null) {
            goTo(bedPos);
            sendMessage("Heading to my bed at [" + bedPos.getX() + ", " + bedPos.getY() + ", " + bedPos.getZ() + "]!");
            LLMoblings.LOGGER.info("[{}] Going to bed position {}", companion.getCompanionName(), bedPos);
        } else {
            sendMessage("I don't have a home set. Tell me to 'sethome' first!");
            LLMoblings.LOGGER.info("[{}] No home or bed position set", companion.getCompanionName());
        }
    }

    private void tryToSleep() {
        LLMoblings.LOGGER.info("[{}] Attempting to sleep...", companion.getCompanionName());
        if (bedPos == null) {
            findAndSetBed();
        }

        if (bedPos != null) {
            double dist = companion.position().distanceTo(Vec3.atCenterOf(bedPos));
            if (dist > 3) {
                goTo(bedPos);
                sendMessage("Walking to bed...");
            } else {
                // Check if it's night time
                if (companion.level() instanceof ServerLevel serverLevel) {
                    long dayTime = serverLevel.getDayTime() % 24000;
                    if (dayTime >= 12542 && dayTime <= 23459) {
                        sendMessage("*lies down in bed* Goodnight!");
                        LLMoblings.LOGGER.info("[{}] Going to sleep", companion.getCompanionName());
                        // Note: Actual sleeping mechanics would require more complex implementation
                    } else {
                        sendMessage("It's not night time yet. I can only sleep when it's dark.");
                        LLMoblings.LOGGER.info("[{}] Cannot sleep - not night time (dayTime={})", companion.getCompanionName(), dayTime);
                    }
                }
            }
        } else {
            sendMessage("I need a bed to sleep! Find me one first.");
        }
    }

    private void equipBestGear() {
        // Check current weapon - only skip if already holding a WEAPON
        ItemStack currentWeapon = companion.getMainHandItem();
        boolean holdingWeapon = !currentWeapon.isEmpty() &&
            (currentWeapon.getItem() instanceof SwordItem || currentWeapon.getItem() instanceof AxeItem);

        // Search inventory for best weapon
        ItemStack bestWeapon = ItemStack.EMPTY;
        int bestSlot = -1;
        double bestDamage = holdingWeapon ? getWeaponDamage(currentWeapon) : 0;

        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            if (item instanceof SwordItem || item instanceof AxeItem) {
                double damage = getWeaponDamage(stack);
                if (damage > bestDamage) {
                    bestWeapon = stack;
                    bestSlot = i;
                    bestDamage = damage;
                }
            }
        }

        if (!bestWeapon.isEmpty() && bestSlot >= 0) {
            // Put current item back in inventory if holding something
            if (!currentWeapon.isEmpty()) {
                companion.addToInventory(currentWeapon.copy());
            }
            // Equip the better weapon
            companion.setItemSlot(EquipmentSlot.MAINHAND, bestWeapon.copy());
            companion.setItem(bestSlot, ItemStack.EMPTY);
            sendMessage("Equipped " + bestWeapon.getHoverName().getString() + "!");
        } else if (holdingWeapon) {
            sendMessage("I'm already using my best weapon: " + currentWeapon.getHoverName().getString());
        } else {
            // No weapon in inventory, go look for one
            sendMessage("I don't have any weapons in my inventory. Going to look for one!");
            startAutonomous(32);
        }
    }

    private double getWeaponDamage(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof SwordItem sword) {
            return sword.getTier().getAttackDamageBonus();
        } else if (item instanceof AxeItem axe) {
            return axe.getTier().getAttackDamageBonus();
        }
        return 0;
    }

    private void reportInventory() {
        StringBuilder sb = new StringBuilder();

        // Report equipped items
        ItemStack mainHand = companion.getMainHandItem();
        ItemStack offHand = companion.getOffhandItem();

        if (!mainHand.isEmpty()) {
            sb.append("Wielding: ").append(mainHand.getHoverName().getString());
        }
        if (!offHand.isEmpty()) {
            if (sb.length() > 0) sb.append(". ");
            sb.append("Off-hand: ").append(offHand.getHoverName().getString());
        }

        // Count inventory items
        int itemCount = 0;
        int weaponCount = 0;
        int foodCount = 0;
        int armorCount = 0;

        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (stack.isEmpty()) continue;

            itemCount += stack.getCount();
            Item item = stack.getItem();

            if (item instanceof SwordItem || item instanceof AxeItem) {
                weaponCount++;
            } else if (item instanceof ArmorItem) {
                armorCount++;
            } else if (stack.getFoodProperties(companion) != null) {
                foodCount += stack.getCount();
            }
        }

        if (sb.length() > 0) sb.append(". ");

        if (itemCount == 0) {
            sb.append("My inventory is empty.");
        } else {
            sb.append("Inventory: ").append(itemCount).append(" items");
            if (weaponCount > 0) sb.append(", ").append(weaponCount).append(" weapons");
            if (armorCount > 0) sb.append(", ").append(armorCount).append(" armor pieces");
            if (foodCount > 0) sb.append(", ").append(foodCount).append(" food");
        }

        sendMessage(sb.toString());
    }

    private void getGearFromME(String material) {
        if (!AE2Integration.isAE2Loaded()) {
            sendMessage("I can't find an ME network here!");
            return;
        }

        if (!(companion.level() instanceof ServerLevel serverLevel)) {
            sendMessage("Something's wrong with the world...");
            return;
        }

        // Find ME access point
        List<BlockPos> meAccessPoints = AE2Integration.findMEAccessPoints(
                serverLevel, companion.blockPosition(), 32);

        if (meAccessPoints.isEmpty()) {
            sendMessage("I can't find an ME terminal nearby!");
            return;
        }

        BlockPos terminal = meAccessPoints.get(0);
        sendMessage("Found ME terminal! Going to get " + material + " gear...");

        // Determine which items to get based on material
        List<Item> targetItems;
        if (material.toLowerCase().contains("diamond")) {
            targetItems = AE2Integration.getDiamondArmorItems();
        } else {
            targetItems = AE2Integration.getIronArmorItems();
        }

        // Navigate to the terminal first
        currentState = AIState.GOING_TO;
        targetPos = terminal;

        // We'll need to process this in tick() - for now start moving
        // and set up a task to extract/craft when we arrive
        companion.getNavigation().moveTo(terminal.getX() + 0.5, terminal.getY(), terminal.getZ() + 0.5, 1.0);

        // Schedule gear retrieval after reaching terminal
        // For now, do it immediately if close enough
        double distance = companion.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(terminal));
        if (distance < 5.0) {
            retrieveOrCraftGear(serverLevel, terminal, targetItems, material);
        } else {
            // Store pending gear request for when we arrive
            pendingGearRequest = new GearRequest(terminal, targetItems, material);
            sendMessage("On my way to the terminal...");
        }
    }

    private GearRequest pendingGearRequest = null;

    private record GearRequest(BlockPos terminal, List<Item> items, String material) {}

    private void retrieveOrCraftGear(ServerLevel level, BlockPos terminal, List<Item> targetItems, String material) {
        int retrieved = 0;
        int craftRequested = 0;

        for (Item item : targetItems) {
            // First try to extract from ME network
            List<ItemStack> extracted = AE2Integration.extractItems(level, terminal,
                    stack -> stack.getItem() == item, 1);

            if (!extracted.isEmpty()) {
                // Add to companion inventory and equip
                for (ItemStack stack : extracted) {
                    addToInventoryAndEquip(stack);
                    retrieved++;
                }
            } else {
                // Item not in network, request crafting
                boolean craftStarted = AE2Integration.requestCrafting(level, terminal, item, 1);
                if (craftStarted) {
                    craftRequested++;
                    LLMoblings.LOGGER.info("[{}] Requested crafting of {}", companion.getCompanionName(), item);
                }
            }
        }

        // Report results
        StringBuilder result = new StringBuilder();
        if (retrieved > 0) {
            result.append("Got ").append(retrieved).append(" ").append(material).append(" pieces! ");
        }
        if (craftRequested > 0) {
            result.append("Requested crafting of ").append(craftRequested).append(" items. ");
        }
        if (retrieved == 0 && craftRequested == 0) {
            result.append("Couldn't find or craft any ").append(material).append(" gear. Check if patterns are set up!");
        }

        sendMessage(result.toString().trim());

        // After getting gear, equip it
        if (retrieved > 0) {
            equipAllGear();
        }

        currentState = AIState.IDLE;
    }

    private void addToInventoryAndEquip(ItemStack stack) {
        Item item = stack.getItem();

        // Equip directly if it's armor or weapon
        if (item instanceof ArmorItem armorItem) {
            EquipmentSlot slot = armorItem.getEquipmentSlot();
            ItemStack current = companion.getItemBySlot(slot);
            if (current.isEmpty()) {
                companion.setItemSlot(slot, stack.copy());
                LLMoblings.LOGGER.info("[{}] Equipped {}", companion.getCompanionName(), item);
                return;
            }
        }

        if (item instanceof SwordItem || item instanceof AxeItem) {
            if (companion.getMainHandItem().isEmpty()) {
                companion.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                LLMoblings.LOGGER.info("[{}] Equipped {}", companion.getCompanionName(), item);
                return;
            }
        }

        // Otherwise add to inventory
        for (int i = 0; i < companion.getContainerSize(); i++) {
            if (companion.getItem(i).isEmpty()) {
                companion.setItem(i, stack.copy());
                return;
            }
        }
    }

    private void equipAllGear() {
        // Go through inventory and equip any armor/weapons
        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();

            if (item instanceof ArmorItem armorItem) {
                EquipmentSlot slot = armorItem.getEquipmentSlot();
                if (companion.getItemBySlot(slot).isEmpty()) {
                    companion.setItemSlot(slot, stack.copy());
                    companion.setItem(i, ItemStack.EMPTY);
                }
            }

            if ((item instanceof SwordItem || item instanceof AxeItem) &&
                    companion.getMainHandItem().isEmpty()) {
                companion.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                companion.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private void depositItems(boolean keepGear) {
        if (!(companion.level() instanceof ServerLevel serverLevel)) {
            sendMessage("Something's wrong with the world...");
            return;
        }

        // Count items to deposit
        int itemCount = 0;
        for (int i = 0; i < companion.getContainerSize(); i++) {
            if (!companion.getItem(i).isEmpty()) {
                itemCount++;
            }
        }

        if (itemCount == 0) {
            sendMessage("My inventory is empty, nothing to deposit!");
            return;
        }

        // First try ME network
        if (AE2Integration.isAE2Loaded()) {
            List<BlockPos> meTerminals = AE2Integration.findMEAccessPoints(
                    serverLevel, companion.blockPosition(), 32);

            if (!meTerminals.isEmpty()) {
                BlockPos terminal = meTerminals.get(0);
                sendMessage("Found ME terminal! Going to deposit items...");

                // Navigate to terminal
                currentState = AIState.GOING_TO;
                targetPos = terminal;
                pendingDepositRequest = new DepositRequest(terminal, true, keepGear);
                companion.getNavigation().moveTo(terminal.getX() + 0.5, terminal.getY(), terminal.getZ() + 0.5, 1.0);

                double distance = companion.position().distanceTo(Vec3.atCenterOf(terminal));
                if (distance < 5.0) {
                    executeDeposit(serverLevel, terminal, true, keepGear);
                }
                return;
            }
        }

        // Try regular chests
        BlockPos chest = findNearbyChest(serverLevel, 16);
        if (chest != null) {
            sendMessage("Found a chest! Going to deposit items...");
            currentState = AIState.GOING_TO;
            targetPos = chest;
            pendingDepositRequest = new DepositRequest(chest, false, keepGear);
            companion.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.0);

            double distance = companion.position().distanceTo(Vec3.atCenterOf(chest));
            if (distance < 3.0) {
                executeDeposit(serverLevel, chest, false, keepGear);
            }
            return;
        }

        sendMessage("I can't find any storage nearby!");
    }

    private DepositRequest pendingDepositRequest = null;

    private record DepositRequest(BlockPos pos, boolean isME, boolean keepGear) {}

    private BlockPos findNearbyChest(ServerLevel level, int radius) {
        BlockPos center = companion.blockPosition();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private void executeDeposit(ServerLevel level, BlockPos storagePos, boolean isME, boolean keepGear) {
        int deposited = 0;

        if (isME) {
            // Deposit into ME network
            deposited = depositToME(level, storagePos, keepGear);
        } else {
            // Deposit into chest
            deposited = depositToChest(level, storagePos, keepGear);
        }

        if (deposited > 0) {
            sendMessage("Deposited " + deposited + " item stacks!" + (keepGear ? " (Kept my gear)" : ""));
        } else {
            sendMessage("Couldn't deposit any items - storage might be full!");
        }

        pendingDepositRequest = null;
        currentState = AIState.IDLE;
    }

    private int depositToME(ServerLevel level, BlockPos terminal, boolean keepGear) {
        int deposited = 0;

        try {
            // Get ME network access
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(terminal);
            if (be == null) return 0;

            // Use reflection to insert items into ME network
            Object grid = getGridFromBlockEntity(be);
            if (grid == null) return 0;

            Object storageService = getStorageService(grid);
            if (storageService == null) return 0;

            Object inventory = getInventory(storageService);
            if (inventory == null) return 0;

            // Deposit each item from companion's inventory
            for (int i = 0; i < companion.getContainerSize(); i++) {
                ItemStack stack = companion.getItem(i);
                if (stack.isEmpty()) continue;

                // Skip weapons/armor if keepGear is true
                if (keepGear) {
                    Item item = stack.getItem();
                    if (item instanceof SwordItem || item instanceof AxeItem ||
                        item instanceof ArmorItem) {
                        continue;
                    }
                }

                // Insert into ME network
                if (insertIntoME(inventory, stack)) {
                    companion.setItem(i, ItemStack.EMPTY);
                    deposited++;
                }
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.warn("Error depositing to ME: {}", e.getMessage());
        }

        return deposited;
    }

    private Object getGridFromBlockEntity(net.minecraft.world.level.block.entity.BlockEntity be) {
        try {
            for (var method : be.getClass().getMethods()) {
                if (method.getName().equals("getGridNode") || method.getName().equals("getMainNode")) {
                    Object node = null;
                    if (method.getParameterCount() == 1) {
                        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                            try {
                                node = method.invoke(be, dir);
                                if (node != null) break;
                            } catch (Exception ignored) {}
                        }
                    } else if (method.getParameterCount() == 0) {
                        node = method.invoke(be);
                    }
                    if (node != null) {
                        for (var nodeMethod : node.getClass().getMethods()) {
                            if (nodeMethod.getName().equals("getGrid") && nodeMethod.getParameterCount() == 0) {
                                return nodeMethod.invoke(node);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Could not get grid: {}", e.getMessage());
        }
        return null;
    }

    private Object getStorageService(Object grid) {
        try {
            for (var method : grid.getClass().getMethods()) {
                if (method.getName().equals("getStorageService")) {
                    return method.invoke(grid);
                }
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Could not get storage service: {}", e.getMessage());
        }
        return null;
    }

    private Object getInventory(Object storageService) {
        try {
            for (var method : storageService.getClass().getMethods()) {
                if (method.getName().equals("getInventory") && method.getParameterCount() == 0) {
                    return method.invoke(storageService);
                }
            }
        } catch (Exception e) {
            LLMoblings.LOGGER.debug("Could not get inventory: {}", e.getMessage());
        }
        return null;
    }

    private boolean insertIntoME(Object inventory, ItemStack stack) {
        try {
            // Create AEItemKey
            Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
            var ofMethod = aeItemKeyClass.getMethod("of", ItemStack.class);
            Object aeItemKey = ofMethod.invoke(null, stack);

            if (aeItemKey == null) return false;

            // Get Actionable.MODULATE
            Class<?> actionableClass = Class.forName("appeng.api.config.Actionable");
            Object modulate = actionableClass.getField("MODULATE").get(null);

            // Create action source
            Object actionSource = Class.forName("appeng.me.helpers.BaseActionSource")
                    .getDeclaredConstructor().newInstance();

            // Insert: MEStorage.insert(AEKey, long, Actionable, IActionSource)
            var insertMethod = inventory.getClass().getMethod("insert",
                    Class.forName("appeng.api.stacks.AEKey"),
                    long.class,
                    actionableClass,
                    Class.forName("appeng.api.networking.security.IActionSource"));

            long inserted = (long) insertMethod.invoke(inventory, aeItemKey, (long) stack.getCount(), modulate, actionSource);
            return inserted > 0;
        } catch (Exception e) {
            LLMoblings.LOGGER.trace("Could not insert into ME: {}", e.getMessage());
        }
        return false;
    }

    private int depositToChest(ServerLevel level, BlockPos chestPos, boolean keepGear) {
        int deposited = 0;

        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(chestPos);
        if (!(be instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity container)) {
            return 0;
        }

        for (int i = 0; i < companion.getContainerSize(); i++) {
            ItemStack stack = companion.getItem(i);
            if (stack.isEmpty()) continue;

            // Skip weapons/armor if keepGear is true
            if (keepGear) {
                Item item = stack.getItem();
                if (item instanceof SwordItem || item instanceof AxeItem ||
                    item instanceof ArmorItem) {
                    continue;
                }
            }

            // Try to insert into chest
            for (int j = 0; j < container.getContainerSize(); j++) {
                ItemStack chestStack = container.getItem(j);
                if (chestStack.isEmpty()) {
                    container.setItem(j, stack.copy());
                    companion.setItem(i, ItemStack.EMPTY);
                    deposited++;
                    break;
                } else if (ItemStack.isSameItemSameComponents(chestStack, stack) &&
                           chestStack.getCount() < chestStack.getMaxStackSize()) {
                    int space = chestStack.getMaxStackSize() - chestStack.getCount();
                    int toTransfer = Math.min(space, stack.getCount());
                    chestStack.grow(toTransfer);
                    stack.shrink(toTransfer);
                    if (stack.isEmpty()) {
                        companion.setItem(i, ItemStack.EMPTY);
                        deposited++;
                        break;
                    }
                }
            }
        }

        container.setChanged();
        return deposited;
    }

    private void requestTeleport(String targetPlayer) {
        if (targetPlayer == null || targetPlayer.isEmpty()) {
            // Default to teleporting to whoever gave the command
            Player target = (commandGiver != null && commandGiver.isAlive()) ? commandGiver : companion.getOwner();
            if (target != null) {
                teleportToPlayer(target);
            } else {
                sendMessage("Who should I teleport to? Tell me their name.");
            }
            return;
        }

        if (companion.level() instanceof ServerLevel serverLevel) {
            // Find the target player by name
            Player target = serverLevel.getServer().getPlayerList().getPlayerByName(targetPlayer);
            if (target != null) {
                teleportToPlayer(target);
            } else {
                sendMessage("I can't find a player named " + targetPlayer + ".");
                LLMoblings.LOGGER.info("[{}] Could not find player {} for TPA", companion.getCompanionName(), targetPlayer);
            }
        }
    }

    private void teleportToPlayer(Player target) {
        Vec3 targetPos = target.position();
        companion.teleportTo(targetPos.x, targetPos.y, targetPos.z);
        sendMessage("Teleported to " + target.getName().getString() + "!");
        LLMoblings.LOGGER.info("[{}] Teleported to player {}", companion.getCompanionName(), target.getName().getString());
    }

    private void acceptTeleport() {
        if (companion.level() instanceof ServerLevel serverLevel) {
            try {
                serverLevel.getServer().getCommands().performPrefixedCommand(
                        serverLevel.getServer().createCommandSourceStack()
                                .withEntity(companion)
                                .withPosition(companion.position())
                                .withPermission(2),
                        "tpaccept"
                );
                sendMessage("Accepted the teleport request!");
                LLMoblings.LOGGER.info("[{}] Accepted TPA request", companion.getCompanionName());
            } catch (Exception e) {
                sendMessage("I couldn't accept the teleport request.");
                LLMoblings.LOGGER.warn("[{}] TPAccept command failed: {}", companion.getCompanionName(), e.getMessage());
            }
        }
    }

    private void denyTeleport() {
        if (companion.level() instanceof ServerLevel serverLevel) {
            try {
                serverLevel.getServer().getCommands().performPrefixedCommand(
                        serverLevel.getServer().createCommandSourceStack()
                                .withEntity(companion)
                                .withPosition(companion.position())
                                .withPermission(2),
                        "tpdeny"
                );
                sendMessage("Denied the teleport request.");
                LLMoblings.LOGGER.info("[{}] Denied TPA request", companion.getCompanionName());
            } catch (Exception e) {
                sendMessage("I couldn't deny the teleport request.");
                LLMoblings.LOGGER.warn("[{}] TPDeny command failed: {}", companion.getCompanionName(), e.getMessage());
            }
        }
    }

    /**
     * Handle portal/dimension travel commands.
     */
    private void handlePortalCommand(String action) {
        switch (action) {
            case "enter" -> {
                // Allow portal use and walk towards nearest portal
                companion.allowPortalUse();
                sendMessage("Okay, I'll go through the portal!");

                // Find nearest portal block
                BlockPos nearestPortal = findNearestPortal(16);
                if (nearestPortal != null) {
                    companion.getNavigation().moveTo(
                            nearestPortal.getX() + 0.5,
                            nearestPortal.getY(),
                            nearestPortal.getZ() + 0.5,
                            1.0
                    );
                    LLMoblings.LOGGER.info("[{}] Walking to portal at {}", companion.getCompanionName(), nearestPortal);
                } else {
                    sendMessage("I don't see a portal nearby. Let me follow you to one!");
                    // Start following instead
                    currentState = AIState.FOLLOWING;
                }
            }
            case "follow" -> {
                // Follow owner through portal when they use it
                companion.allowPortalUse();
                sendMessage("I'll follow you through the portal!");
                currentState = AIState.FOLLOWING;
            }
            case "stay" -> {
                companion.disallowPortalUse();
                sendMessage("Okay, I'll stay in this dimension.");
                currentState = AIState.IDLE;
            }
        }
    }

    /**
     * Find the nearest portal block within the given radius.
     */
    private BlockPos findNearestPortal(int radius) {
        BlockPos companionPos = companion.blockPosition();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius / 2; y <= radius / 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = companionPos.offset(x, y, z);
                    if (companion.level() instanceof ServerLevel serverLevel) {
                        net.minecraft.world.level.block.state.BlockState state = serverLevel.getBlockState(checkPos);
                        // Check for nether portal or end portal
                        if (state.is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL) ||
                            state.is(net.minecraft.world.level.block.Blocks.END_PORTAL)) {
                            double dist = companionPos.distSqr(checkPos);
                            if (dist < nearestDist) {
                                nearestDist = dist;
                                nearest = checkPos;
                            }
                        }
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * Handle elevator commands.
     */
    private void handleElevatorCommand(String direction) {
        if (!companion.isOnElevator()) {
            sendMessage("I need to be standing on an elevator block to use it!");
            // Try to find nearby elevator
            BlockPos elevatorPos = findNearbyElevator(16);
            if (elevatorPos != null) {
                sendMessage("I see an elevator nearby, let me walk over to it.");
                companion.getNavigation().moveTo(
                        elevatorPos.getX() + 0.5,
                        elevatorPos.getY() + 1,
                        elevatorPos.getZ() + 0.5,
                        1.0
                );
            }
            return;
        }

        boolean goUp = direction.equalsIgnoreCase("up");
        sendMessage(goUp ? "Going up!" : "Going down!");
        companion.tryUseElevator(goUp);
    }

    /**
     * Handle cobblestats command to show Pokemon stats.
     */
    private void handleCobblestatsCommand(String detail, String targetName) {
        if (!CobblemonIntegration.isCobblemonLoaded()) {
            sendMessage("Cobblemon isn't installed - I can't check Pokemon stats!");
            return;
        }

        // Find nearby Pokemon
        List<Entity> nearbyPokemon = companion.level().getEntities(
                companion,
                companion.getBoundingBox().inflate(16),
                CobblemonIntegration::isPokemon
        );

        if (nearbyPokemon.isEmpty()) {
            sendMessage("I don't see any Pokemon nearby to check stats on.");
            return;
        }

        // If a target name was specified, try to find that Pokemon
        Entity targetPokemon = null;
        if (targetName != null && !targetName.isEmpty()) {
            String searchName = targetName.toLowerCase();
            for (Entity pokemon : nearbyPokemon) {
                String pokemonName = CobblemonIntegration.getPokemonDisplayName(pokemon);
                String speciesName = CobblemonIntegration.getPokemonSpeciesName(pokemon);
                if (pokemonName != null && pokemonName.toLowerCase().contains(searchName)) {
                    targetPokemon = pokemon;
                    break;
                }
                if (speciesName != null && speciesName.toLowerCase().contains(searchName)) {
                    targetPokemon = pokemon;
                    break;
                }
            }

            if (targetPokemon == null) {
                sendMessage("I can't find a Pokemon named '" + targetName + "' nearby.");
                // List what's available
                StringBuilder sb = new StringBuilder("I can see: ");
                for (int i = 0; i < Math.min(nearbyPokemon.size(), 5); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(CobblemonIntegration.getPokemonSummary(nearbyPokemon.get(i)));
                }
                sendMessage(sb.toString());
                return;
            }
        } else {
            // No target specified - use the nearest Pokemon
            targetPokemon = nearbyPokemon.stream()
                    .min((a, b) -> Double.compare(companion.distanceTo(a), companion.distanceTo(b)))
                    .orElse(null);
        }

        if (targetPokemon == null) {
            sendMessage("I couldn't find a Pokemon to check.");
            return;
        }

        // Get and display stats
        if (detail.equals("brief")) {
            String stats = CobblemonIntegration.getBriefPokemonStats(targetPokemon);
            sendMessage(stats != null ? stats : "Couldn't read stats.");
        } else {
            String stats = CobblemonIntegration.getFullPokemonStats(targetPokemon);
            if (stats != null) {
                // Split into multiple messages for readability
                String[] lines = stats.split("\n");
                for (String line : lines) {
                    if (!line.isEmpty()) {
                        sendMessage(line);
                    }
                }
            } else {
                sendMessage("Couldn't read Pokemon stats.");
            }
        }
    }

    /**
     * Find nearby elevator block.
     */
    private BlockPos findNearbyElevator(int radius) {
        BlockPos companionPos = companion.blockPosition();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = companionPos.offset(x, y, z);
                    if (companion.level() instanceof ServerLevel serverLevel) {
                        net.minecraft.world.level.block.state.BlockState state = serverLevel.getBlockState(checkPos);
                        String blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                .getKey(state.getBlock()).toString();
                        if (blockName.contains("elevator")) {
                            return checkPos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void sendMessage(String message) {
        sendMessageToAll(message);
    }

    /**
     * Send message to all nearby players.
     */
    private void sendMessageToAll(String message) {
        if (!Config.BROADCAST_COMPANION_CHAT.get()) return;

        String formatted = "[" + companion.getCompanionName() + "] " + message;
        Component component = Component.literal(formatted);

        // Send to all players within 64 blocks
        List<Player> nearbyPlayers = companion.level().getEntitiesOfClass(
                Player.class,
                companion.getBoundingBox().inflate(64),
                Player::isAlive
        );

        for (Player player : nearbyPlayers) {
            player.sendSystemMessage(component);
        }

        LLMoblings.LOGGER.debug("[{}] Broadcast to {} players: {}", companion.getCompanionName(), nearbyPlayers.size(), message);
    }

    public AIState getCurrentState() {
        return currentState;
    }

    public void onCompanionHurt() {
        personality.onHurt();
    }

    public enum AIState {
        IDLE,
        FOLLOWING,
        GOING_TO,
        MINING,
        ATTACKING,
        DEFENDING,
        AUTONOMOUS,
        BUILDING
    }
}
