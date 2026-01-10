package com.gblfxt.player2npc.ai;

import com.gblfxt.player2npc.Config;
import com.gblfxt.player2npc.Player2NPC;
import com.gblfxt.player2npc.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

    // Owner tracking for greetings
    private boolean ownerWasNearby = false;

    public CompanionAI(CompanionEntity companion) {
        this.companion = companion;
        this.ollamaClient = new OllamaClient(companion.getCompanionName());
        this.personality = new CompanionPersonality(companion);
    }

    public void tick() {
        // Tick personality for random chatter/emotes
        personality.tick();

        // Check if owner just came nearby (for greetings)
        checkOwnerProximity();

        // Check for pending LLM response
        if (pendingAction != null && pendingAction.isDone()) {
            try {
                CompanionAction action = pendingAction.get();
                executeAction(action);
            } catch (Exception e) {
                Player2NPC.LOGGER.error("Error getting LLM response: ", e);
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
            case IDLE -> tickIdle();
        }
    }

    public void processMessage(String message) {
        if (pendingAction != null && !pendingAction.isDone()) {
            // Already processing a message, queue or ignore
            sendMessage("I'm still thinking about your last request...");
            return;
        }

        sendMessage("Thinking...");
        pendingAction = ollamaClient.chat(message);
    }

    private void executeAction(CompanionAction action) {
        // Send message if present
        if (action.getMessage() != null && !action.getMessage().isEmpty()) {
            sendMessage(action.getMessage());
        }

        Player2NPC.LOGGER.debug("Executing action: {}", action);

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
            case "autonomous", "independent", "survive" -> {
                int radius = action.getInt("radius", 32);
                startAutonomous(radius);
            }
            case "idle" -> {
                currentState = AIState.IDLE;
            }
            default -> {
                Player2NPC.LOGGER.warn("Unknown action: {}", action.getAction());
                currentState = AIState.IDLE;
            }
        }
    }

    // State behaviors
    private void tickFollow() {
        Player owner = companion.getOwner();
        if (owner == null) {
            currentState = AIState.IDLE;
            return;
        }

        double distance = companion.distanceTo(owner);
        double followDist = Config.COMPANION_FOLLOW_DISTANCE.get();

        if (distance > followDist) {
            // Move towards owner
            companion.getNavigation().moveTo(owner, 1.0);
        } else if (distance < followDist - 1) {
            // Close enough, stop
            companion.getNavigation().stop();
        }

        // Teleport if too far
        if (distance > 32) {
            Vec3 ownerPos = owner.position();
            companion.teleportTo(ownerPos.x, ownerPos.y, ownerPos.z);
        }
    }

    private void tickGoTo() {
        if (targetPos == null) {
            currentState = AIState.IDLE;
            return;
        }

        double distance = companion.position().distanceTo(Vec3.atCenterOf(targetPos));
        if (distance < 2.0) {
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
        }

        double distance = companion.distanceTo(targetEntity);
        if (distance < 2.0) {
            companion.doHurtTarget(targetEntity);
            personality.onCombat();
        } else {
            companion.getNavigation().moveTo(targetEntity, 1.2);
        }
    }

    private void tickDefending() {
        Player owner = companion.getOwner();
        if (owner == null) {
            currentState = AIState.IDLE;
            return;
        }

        // Look for threats near owner
        List<Monster> threats = companion.level().getEntitiesOfClass(
                Monster.class,
                owner.getBoundingBox().inflate(10),
                monster -> monster.isAlive() && monster.getTarget() == owner
        );

        if (!threats.isEmpty()) {
            targetEntity = threats.get(0);
            double distance = companion.distanceTo(targetEntity);
            if (distance < 2.0) {
                companion.doHurtTarget(targetEntity);
                personality.onCombat();
            } else {
                companion.getNavigation().moveTo(targetEntity, 1.2);
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

        List<LivingEntity> entities = companion.level().getEntitiesOfClass(
                LivingEntity.class,
                companion.getBoundingBox().inflate(16),
                e -> {
                    if (!e.isAlive() || e == companion || e == companion.getOwner()) {
                        return false;
                    }
                    // If specific type requested, match it
                    if (searchType != null) {
                        return e.getType() == searchType;
                    }
                    // Otherwise, target any monster
                    return e instanceof Monster;
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
        Player owner = companion.getOwner();
        if (owner == null) return;

        // TODO: Find matching items in inventory and give to owner
        sendMessage("I would give you " + count + " " + itemName + " but inventory transfer isn't implemented yet.");
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
    }

    private void sendMessage(String message) {
        Player owner = companion.getOwner();
        if (owner != null && Config.BROADCAST_COMPANION_CHAT.get()) {
            String formatted = "[" + companion.getCompanionName() + "] " + message;
            owner.sendSystemMessage(Component.literal(formatted));
        }
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
        AUTONOMOUS
    }
}
