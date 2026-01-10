package com.gblfxt.player2npc.ai;

import com.gblfxt.player2npc.entity.CompanionEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.Random;

/**
 * Adds personality, random chatter, and emotes to companions.
 */
public class CompanionPersonality {
    private final CompanionEntity companion;
    private final Random random = new Random();

    private int chatCooldown = 0;
    private int emoteCooldown = 0;
    private String mood = "content";
    private int moodDuration = 0;

    // Idle chatter
    private static final String[] IDLE_CHAT = {
        "Nice weather today...",
        "*whistles*",
        "*hums a tune*",
        "Wonder what's over that hill...",
        "I could go for some cake right about now.",
        "*stretches*",
        "Hmm, what should we do next?",
        "*looks around curiously*",
        "This is a nice spot.",
        "*yawns*",
        "I've got a good feeling about today!",
        "*taps foot impatiently*",
        "So... seen any good caves lately?",
        "*kicks a pebble*"
    };

    // Mining chatter
    private static final String[] MINING_CHAT = {
        "Dig dig dig!",
        "*pickaxe sounds*",
        "There's gotta be diamonds around here somewhere...",
        "My back is gonna hurt tomorrow.",
        "Ooh, is that ore?",
        "*grunts with effort*",
        "Mining away~",
        "Just a few more...",
        "This rock is stubborn!",
        "Hope I don't hit lava..."
    };

    // Hunting chatter
    private static final String[] HUNTING_CHAT = {
        "Here, piggy piggy...",
        "*sneaks quietly*",
        "Sorry little guy, but we need food.",
        "Dinner time!",
        "Got one!",
        "*hunting intensifies*",
        "Nature provides.",
        "The hunt is on!"
    };

    // Combat chatter
    private static final String[] COMBAT_CHAT = {
        "Take that!",
        "For glory!",
        "*battle cry*",
        "Come at me!",
        "Is that all you've got?",
        "Watch your back!",
        "Behind you!",
        "*grunts*",
        "Stay down!",
        "Too easy!"
    };

    // Finding good stuff
    private static final String[] EXCITED_CHAT = {
        "Ooh, shiny!",
        "Jackpot!",
        "Look what I found!",
        "This is the good stuff!",
        "Score!",
        "*eyes light up*",
        "Today's our lucky day!"
    };

    // Tired/bored chatter
    private static final String[] TIRED_CHAT = {
        "*sighs*",
        "Are we there yet?",
        "I need a break...",
        "*yawns loudly*",
        "My feet hurt.",
        "How much longer?",
        "Zzz... huh? I'm awake!"
    };

    // Greeting owner
    private static final String[] GREETING_CHAT = {
        "Hey boss!",
        "Good to see you!",
        "Ready for adventure?",
        "What's the plan?",
        "Reporting for duty!",
        "*waves enthusiastically*",
        "Let's go!",
        "I missed you!"
    };

    // Night time
    private static final String[] NIGHT_CHAT = {
        "It's getting dark...",
        "*looks around nervously*",
        "Did you hear that?",
        "Maybe we should find shelter?",
        "I don't like the dark...",
        "Creepy...",
        "*stays close*"
    };

    // Weather comments
    private static final String[] RAIN_CHAT = {
        "*shakes off water*",
        "Great, now I'm wet.",
        "Should've brought an umbrella.",
        "I love the rain! ...said no one ever.",
        "*splashes in puddle*",
        "At least it's not thunder... wait."
    };

    public CompanionPersonality(CompanionEntity companion) {
        this.companion = companion;
    }

    public void tick() {
        if (chatCooldown > 0) chatCooldown--;
        if (emoteCooldown > 0) emoteCooldown--;
        if (moodDuration > 0) moodDuration--;

        if (moodDuration <= 0) {
            mood = "content";
        }

        // Random chance for idle behavior
        if (random.nextInt(2000) == 0) {  // ~0.05% per tick, roughly every 100 seconds
            doRandomBehavior();
        }
    }

    private void doRandomBehavior() {
        if (chatCooldown > 0) return;

        int behavior = random.nextInt(3);
        switch (behavior) {
            case 0 -> doEmote();
            case 1 -> doIdleChat();
            case 2 -> doEnvironmentComment();
        }
    }

    /**
     * Call this when companion starts a task.
     */
    public void onTaskStart(String taskType) {
        if (chatCooldown > 0 || random.nextInt(3) != 0) return;  // 33% chance

        String message = switch (taskType.toLowerCase()) {
            case "mining", "gathering" -> getRandomFrom(MINING_CHAT);
            case "hunting" -> getRandomFrom(HUNTING_CHAT);
            case "attacking", "defending", "combat" -> getRandomFrom(COMBAT_CHAT);
            default -> null;
        };

        if (message != null) {
            say(message);
        }
    }

    /**
     * Call when companion finds something good.
     */
    public void onExcitingFind() {
        if (chatCooldown > 0) return;
        say(getRandomFrom(EXCITED_CHAT));
        doHappyEmote();
        setMood("excited", 600);
    }

    /**
     * Call when owner comes online or approaches.
     */
    public void onOwnerNearby() {
        if (chatCooldown > 0 || random.nextInt(2) != 0) return;  // 50% chance
        say(getRandomFrom(GREETING_CHAT));
        doWaveEmote();
    }

    /**
     * Call during combat.
     */
    public void onCombat() {
        if (chatCooldown > 0 || random.nextInt(5) != 0) return;  // 20% chance
        say(getRandomFrom(COMBAT_CHAT));
    }

    /**
     * Call when taking damage.
     */
    public void onHurt() {
        if (chatCooldown > 0) return;

        String[] hurtChat = {"Ouch!", "Hey!", "That hurt!", "*winces*", "Ow ow ow!"};
        say(getRandomFrom(hurtChat));
        chatCooldown = 60;  // Short cooldown for hurt messages
    }

    /**
     * Call when task completes successfully.
     */
    public void onTaskComplete() {
        if (random.nextInt(2) == 0) {  // 50% chance
            String[] completeChat = {"Done!", "All finished!", "Easy peasy!", "That's that!", "Mission complete!"};
            say(getRandomFrom(completeChat));
            if (random.nextBoolean()) {
                doHappyEmote();
            }
        }
    }

    private void doIdleChat() {
        if (mood.equals("tired")) {
            say(getRandomFrom(TIRED_CHAT));
        } else {
            say(getRandomFrom(IDLE_CHAT));
        }
    }

    private void doEnvironmentComment() {
        if (companion.level() instanceof ServerLevel serverLevel) {
            // Check time of day
            long dayTime = serverLevel.getDayTime() % 24000;
            if (dayTime >= 13000 && dayTime <= 23000) {  // Night
                say(getRandomFrom(NIGHT_CHAT));
                return;
            }

            // Check weather
            if (serverLevel.isRaining()) {
                say(getRandomFrom(RAIN_CHAT));
                return;
            }
        }

        // Default to idle chat
        doIdleChat();
    }

    private void doEmote() {
        if (emoteCooldown > 0) return;

        int emote = random.nextInt(5);
        switch (emote) {
            case 0 -> doWaveEmote();
            case 1 -> doHappyEmote();
            case 2 -> doLookAroundEmote();
            case 3 -> doStretchEmote();
            case 4 -> doNodEmote();
        }
    }

    private void doWaveEmote() {
        if (emoteCooldown > 0) return;
        companion.swing(companion.getUsedItemHand());
        emoteCooldown = 100;
    }

    private void doHappyEmote() {
        if (emoteCooldown > 0) return;

        if (companion.level() instanceof ServerLevel serverLevel) {
            // Spawn heart particles
            serverLevel.sendParticles(
                ParticleTypes.HEART,
                companion.getX(), companion.getY() + 2, companion.getZ(),
                3, 0.3, 0.3, 0.3, 0.1
            );
        }
        emoteCooldown = 200;
    }

    private void doLookAroundEmote() {
        if (emoteCooldown > 0) return;

        // Rotate head randomly
        float newYaw = companion.getYRot() + (random.nextFloat() - 0.5f) * 90;
        companion.setYRot(newYaw);
        emoteCooldown = 60;
    }

    private void doStretchEmote() {
        if (emoteCooldown > 0) return;

        // Jump slightly
        if (companion.onGround()) {
            companion.setDeltaMovement(companion.getDeltaMovement().add(0, 0.2, 0));
        }
        emoteCooldown = 100;
    }

    private void doNodEmote() {
        if (emoteCooldown > 0) return;

        // Quick head movement (swing arm as substitute)
        companion.swing(companion.getUsedItemHand());
        emoteCooldown = 80;
    }

    public void doSadEmote() {
        if (companion.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                ParticleTypes.SMOKE,
                companion.getX(), companion.getY() + 2, companion.getZ(),
                5, 0.2, 0.2, 0.2, 0.02
            );
        }
        setMood("sad", 400);
    }

    public void doAngryEmote() {
        if (companion.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                ParticleTypes.ANGRY_VILLAGER,
                companion.getX(), companion.getY() + 2, companion.getZ(),
                3, 0.3, 0.3, 0.3, 0.1
            );
        }
        setMood("angry", 300);
    }

    private void say(String message) {
        Player owner = companion.getOwner();
        if (owner != null) {
            String formatted = "[" + companion.getCompanionName() + "] " + message;
            owner.sendSystemMessage(Component.literal(formatted));
        }
        chatCooldown = 400;  // 20 seconds cooldown
    }

    private void setMood(String newMood, int duration) {
        this.mood = newMood;
        this.moodDuration = duration;
    }

    public String getMood() {
        return mood;
    }

    private String getRandomFrom(String[] options) {
        return options[random.nextInt(options.length)];
    }
}
