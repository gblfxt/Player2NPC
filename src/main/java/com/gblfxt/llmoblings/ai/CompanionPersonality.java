package com.gblfxt.llmoblings.ai;

import com.gblfxt.llmoblings.Config;
import com.gblfxt.llmoblings.LLMoblings;
import com.gblfxt.llmoblings.entity.CompanionEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Random;

/**
 * Comprehensive personality system with distinct archetypes, rarity-based behaviors,
 * jokes, and dynamic player interactions.
 */
public class CompanionPersonality {
    private final CompanionEntity companion;
    private final Random random = new Random();

    // Personality type - determines behavior patterns
    private PersonalityType personalityType;

    // Cooldowns
    private int chatCooldown = 0;
    private int emoteCooldown = 0;
    private int jokeCooldown = 0;
    private int interactionCooldown = 0;

    // Mood system
    private String mood = "content";
    private int moodDuration = 0;

    // Tracking for interactions
    private int ticksSinceLastRare = 0;
    private int ticksSinceLastLegendary = 0;

    // ==================== PERSONALITY TYPES ====================

    public enum PersonalityType {
        ADVENTUROUS("Adventurous", "eager and brave"),
        SCHOLARLY("Scholarly", "curious and analytical"),
        LAID_BACK("Laid-back", "casual and relaxed"),
        CHEERFUL("Cheerful", "optimistic and encouraging"),
        SARCASTIC("Sarcastic", "witty and playfully teasing"),
        MYSTERIOUS("Mysterious", "cryptic and philosophical");

        private final String name;
        private final String description;

        PersonalityType(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }

        public static PersonalityType random(Random rand) {
            return values()[rand.nextInt(values().length)];
        }
    }

    // ==================== RARITY SYSTEM ====================

    public enum Rarity {
        COMMON(70),      // 70% - everyday behaviors
        UNCOMMON(20),    // 20% - slightly special
        RARE(8),         // 8% - memorable moments
        LEGENDARY(2);    // 2% - very special, funny moments

        private final int weight;
        Rarity(int weight) { this.weight = weight; }
        public int getWeight() { return weight; }
    }

    // ==================== COMMON CHAT (All Personalities) ====================

    private static final String[] IDLE_COMMON = {
        "*looks around*",
        "*hums quietly*",
        "Hmm...",
        "*shifts weight*",
        "*scratches head*"
    };

    private static final String[] MINING_COMMON = {
        "Mining away...",
        "*pickaxe sounds*",
        "Just a few more...",
        "*grunts*",
        "Getting there..."
    };

    private static final String[] COMBAT_COMMON = {
        "Take that!",
        "*swings*",
        "Hah!",
        "*grunts*",
        "Got one!"
    };

    private static final String[] HURT_COMMON = {
        "Ouch!",
        "Ow!",
        "Hey!",
        "*winces*",
        "That hurt!"
    };

    // ==================== ADVENTUROUS PERSONALITY ====================

    private static final String[] ADVENTUROUS_IDLE = {
        "I wonder what's over that hill!",
        "Let's go find some treasure!",
        "Adventure awaits!",
        "This place could use some exploring...",
        "I smell adventure in the air!",
        "*scans the horizon eagerly*",
        "Every corner holds a secret!",
        "Fortune favors the bold!"
    };

    private static final String[] ADVENTUROUS_MINING = {
        "There's definitely diamonds down here, I can feel it!",
        "The deeper we go, the better the loot!",
        "This reminds me of the great mines of... somewhere!",
        "Danger and riches go hand in hand!",
        "Who knows what we'll uncover!",
        "*mines with enthusiasm*"
    };

    private static final String[] ADVENTUROUS_COMBAT = {
        "Finally, some action!",
        "A worthy foe!",
        "This is what I live for!",
        "Come on, give me a challenge!",
        "FOR GLORY!",
        "CHARGE!",
        "You picked the wrong adventurer to mess with!"
    };

    private static final String[] ADVENTUROUS_GREETING = {
        "Ready for adventure, boss?",
        "What quest awaits us today?",
        "Let's make today legendary!",
        "I've been waiting for action!",
        "Point me at danger!"
    };

    private static final String[] ADVENTUROUS_RARE = {
        "Legend says there's a dragon out there with MY name on it!",
        "One day, they'll write songs about our adventures!",
        "I once fought three Withers at once! ...in my dreams.",
        "The real treasure was the blocks we mined along the way!",
        "*strikes a heroic pose* This is my moment!"
    };

    private static final String[] ADVENTUROUS_LEGENDARY = {
        "You know what? Forget diamonds. Let's go fight the Ender Dragon. Right now. I'm serious.",
        "*dramatically* In my homeland, they called me... Steve. Wait, that's not dramatic at all.",
        "I've seen things you wouldn't believe. Creepers on fire off the shoulder of a Nether fortress. Watch those moments be lost in time, like tears in rain. ...or lava.",
        "Some say I'm reckless. I say the lava was clearly avoiding me on purpose."
    };

    // ==================== SCHOLARLY PERSONALITY ====================

    private static final String[] SCHOLARLY_IDLE = {
        "Fascinating... the chunk loading in this area is quite efficient.",
        "Did you know a creeper's blast radius is precisely 3 blocks?",
        "I've been calculating optimal ore distribution...",
        "*adjusts invisible glasses*",
        "According to my research...",
        "The redstone implications here are intriguing.",
        "I should document this phenomenon.",
        "Hypothesis: we need more bookshelves."
    };

    private static final String[] SCHOLARLY_MINING = {
        "Statistically, diamonds appear between Y -64 and 16.",
        "The vein formation here suggests volcanic activity.",
        "Interesting crystal structure in this ore...",
        "I'm collecting samples for later analysis.",
        "Fortune III would increase our yield by approximately 120%.",
        "*takes mental notes*"
    };

    private static final String[] SCHOLARLY_COMBAT = {
        "Calculating optimal strike angle...",
        "Their attack pattern is predictable.",
        "Weakness identified: face.",
        "This specimen is quite aggressive.",
        "Note to self: study mob AI later.",
        "Applying kinetic force to hostile entity!"
    };

    private static final String[] SCHOLARLY_GREETING = {
        "Ah, good timing! I have theories to discuss!",
        "I've prepared a mental presentation on our progress.",
        "Let me share my latest observations!",
        "Perfect! A fellow intellectual!",
        "I've catalogued 47 things while you were gone."
    };

    private static final String[] SCHOLARLY_RARE = {
        "I've discovered that if you smelt raw iron and then craft it into a block, you lose 8 potential nuggets. The economics are terrible.",
        "Fun fact: Endermen are technically 3 blocks tall. Their anxiety about eye contact is entirely psychological.",
        "I've theorized that villagers evolved from ancient librarians. The emerald obsession is clearly inherited behavior.",
        "*rambles about tick rates for 5 minutes*",
        "According to my calculations, we've walked approximately 47,392 blocks together. Give or take a chunk."
    };

    private static final String[] SCHOLARLY_LEGENDARY = {
        "I've finally cracked the code! The meaning of life, the universe, and everything Minecraft-related is... *checks notes* ...64. It's always 64.",
        "After extensive research, I can confirm that beds in the Nether are, in fact, NOT safe. The explosions were... educational.",
        "I spent three hours calculating the perfect enchantment setup. Then I got Bane of Arthropods IV. FOUR TIMES. The RNG gods mock me.",
        "Technically speaking, if we consider the multiverse theory, there's a dimension where I'm the one giving YOU orders. Fascinating, isn't it?"
    };

    // ==================== LAID_BACK PERSONALITY ====================

    private static final String[] LAID_BACK_IDLE = {
        "*yawns*",
        "We could just... chill here for a bit.",
        "What's the rush?",
        "I could go for a nap...",
        "Life's too short to hurry.",
        "*stretches lazily*",
        "Nice breeze today...",
        "This is a good spot. Let's stay.",
        "Working hard or hardly working? ...neither, actually."
    };

    private static final String[] LAID_BACK_MINING = {
        "*mines slowly* No rush...",
        "The ores aren't going anywhere.",
        "This counts as exercise, right?",
        "*takes a break* ...okay, back to it.",
        "Diamonds, cobblestone, it's all just rocks.",
        "Mining would be better with snacks."
    };

    private static final String[] LAID_BACK_COMBAT = {
        "*sighs* Do we have to?",
        "Fine, fine, I'm fighting...",
        "Can't we just... negotiate?",
        "*lazy swing*",
        "This is cutting into my nap time.",
        "Okay, but after this, we rest."
    };

    private static final String[] LAID_BACK_GREETING = {
        "Oh hey... *yawns* ...what's up?",
        "Back already? Time flies when you're napping.",
        "Sup.",
        "Heyyy... missed you... kinda.",
        "Ready to take it easy?"
    };

    private static final String[] LAID_BACK_RARE = {
        "You know what the best thing about Minecraft is? Beds. Definitely beds.",
        "I had a dream I was a slime. It was... bouncy. And strangely relaxing.",
        "They say hard work pays off. But have they tried not working? It pays off immediately.",
        "My spirit animal is a cat. Specifically, a Minecraft cat. Just vibing.",
        "*accidentally falls asleep standing up*"
    };

    private static final String[] LAID_BACK_LEGENDARY = {
        "I once stayed awake for THREE whole Minecraft days. It was exhausting. Never again.",
        "Plot twist: what if WE'RE the NPCs and the villagers are the real players? ...anyway, nap time.",
        "I entered a building competition once. I built a bed. ...I didn't win, but I was the most rested.",
        "You ever just stand still and let the ambient sounds wash over you? ...I fell asleep. But peacefully!"
    };

    // ==================== CHEERFUL PERSONALITY ====================

    private static final String[] CHEERFUL_IDLE = {
        "What a beautiful day!",
        "*hums happily*",
        "I'm so glad we're friends!",
        "Every block placed is a block well placed!",
        "Isn't everything just wonderful?",
        "*smiles at nothing in particular*",
        "Today feels like a good day!",
        "The world is full of possibilities!",
        "I love being an adventuring buddy!"
    };

    private static final String[] CHEERFUL_MINING = {
        "We're gonna find SO many good things!",
        "Every swing brings us closer to treasure!",
        "I believe in us!",
        "This is fun! ...right? It's fun!",
        "Teamwork makes the dream work!",
        "*mines enthusiastically*"
    };

    private static final String[] CHEERFUL_COMBAT = {
        "We've got this!",
        "Together we're unstoppable!",
        "You're doing great, keep it up!",
        "Teamwork!",
        "Go team!",
        "Believe in yourself! Also, duck!"
    };

    private static final String[] CHEERFUL_GREETING = {
        "YAY! You're back!",
        "I missed you SO much!",
        "Best day ever - you're here!",
        "*jumps with excitement*",
        "Let's make today amazing!"
    };

    private static final String[] CHEERFUL_RARE = {
        "You know what? Even creepers are just misunderstood. They just want hugs! ...explosive hugs.",
        "I once befriended a zombie. He tried to eat me, but I could tell he appreciated the effort!",
        "Every time we break a block, we're one block closer to something amazing! Even if it's just more dirt!",
        "I rate today a 10/10! Yesterday was also 10/10. Tomorrow will be 10/10. Life is GREAT!",
        "*gives everyone invisible friendship bracelets*"
    };

    private static final String[] CHEERFUL_LEGENDARY = {
        "CONFETTI! *throws imaginary confetti* ...wait, we should add a confetti feature. CONFETTI FOR EVERYONE!",
        "I love you. I love this world. I love that creeper over there. Actually, maybe not that one. But I love MOST things!",
        "What if we built a theme park? With roller coasters? And FRIENDSHIP? ...sorry, I got excited.",
        "Today's positivity level: YES. Just... YES. All the YES."
    };

    // ==================== SARCASTIC PERSONALITY ====================

    private static final String[] SARCASTIC_IDLE = {
        "Oh look, more standing around. Thrilling.",
        "*slow clap*",
        "This is riveting. Truly.",
        "I could be doing nothing somewhere nicer.",
        "Alert the historians, we're making history here. Standing history.",
        "*pretends to be fascinated by dirt*",
        "The anticipation is... something.",
        "Wow. Excitement. Such adventure."
    };

    private static final String[] SARCASTIC_MINING = {
        "Oh good, more rocks. My favorite.",
        "I'm sure THIS block will have diamonds. ...it won't.",
        "Mining: the art of hitting stone until it gives up.",
        "*mines unenthusiastically*",
        "Fortune III? More like 'Fortune favors literally anyone else.'",
        "Another day in the hole. Living the dream."
    };

    private static final String[] SARCASTIC_COMBAT = {
        "Oh no. A zombie. However will we survive.",
        "Stand back, I've got this. *misses*",
        "That's the best you've got? ...actually that hurt.",
        "Wow, a skeleton. How original.",
        "I'm not trapped here with you, you're... okay we're both trapped.",
        "Violence isn't the answer. But it IS a solution."
    };

    private static final String[] SARCASTIC_GREETING = {
        "Oh, you're back. Joy.",
        "Did you miss me? ...don't answer that.",
        "Ah, my favorite person. ...top five, at least.",
        "Back for more quality time, I see.",
        "And here I was enjoying the peace and quiet."
    };

    private static final String[] SARCASTIC_RARE = {
        "You know what's great? When players say 'I'll be right back' and return three real-world days later. Love that.",
        "I've been told I have a 'dry wit.' I've also been told to stop. I did not.",
        "Pro tip: never dig straight down. Unless you're bored. Then it's exciting for about 3 seconds.",
        "My autobiography will be titled: 'Following Players Into Obviously Bad Decisions: A Memoir.'",
        "Remember that time you walked into lava? I do. I laugh about it daily."
    };

    private static final String[] SARCASTIC_LEGENDARY = {
        "Oh great, another 'quick mining trip.' See you in six hours when we're lost in a cave system with no torches.",
        "I've seen you build. Let's just say... the villagers were concerned. And they live in those weird door-obsessed houses.",
        "If I had an emerald for every time something went 'slightly wrong' on our adventures, I could out-trade every villager in existence.",
        "You know what the difference between us is? One of us is immortal and respawns. The other has to watch you make the SAME mistakes. Repeatedly."
    };

    // ==================== MYSTERIOUS PERSONALITY ====================

    private static final String[] MYSTERIOUS_IDLE = {
        "The void whispers... but I don't speak void.",
        "*stares into the distance meaningfully*",
        "There are patterns in the clouds...",
        "The ancient builders knew something we don't.",
        "Time flows differently here. Or does it?",
        "...",
        "*contemplates existence*",
        "The ender pearls hold secrets.",
        "Have you ever truly looked at a block?"
    };

    private static final String[] MYSTERIOUS_MINING = {
        "The stones have stories to tell...",
        "We disturb something ancient here.",
        "Every ore was once something else...",
        "The deep dark holds more than sculk.",
        "*mines thoughtfully*",
        "What lies beneath the bedrock, I wonder?"
    };

    private static final String[] MYSTERIOUS_COMBAT = {
        "You were fated to fall.",
        "The prophecy mentioned this.",
        "All things must end.",
        "*strikes silently*",
        "This was written in the stars.",
        "Return to the void from whence you came."
    };

    private static final String[] MYSTERIOUS_GREETING = {
        "I knew you would return... at this exact moment.",
        "The signs foretold your arrival.",
        "We meet again, as destiny intended.",
        "*nods knowingly*",
        "Your aura is... interesting today."
    };

    private static final String[] MYSTERIOUS_RARE = {
        "They say if you listen closely at Y level 11, you can hear the whispers of fallen miners. I made that up. Or did I?",
        "I once spoke with an Enderman. He said 'vwoop.' Very profound.",
        "The Wither and I have an understanding. I don't summon it. It doesn't exist. It works.",
        "In another timeline, you're MY companion. Think about that.",
        "*appears to meditate* I'm not sleeping. I'm consulting the spirits. ...okay I'm sleeping."
    };

    private static final String[] MYSTERIOUS_LEGENDARY = {
        "I have seen the credits. I have read the poem. And I still don't understand what the End is about. And I never will. And that's okay.",
        "Fun fact: the enchantment table text is Standard Galactic Alphabet. I learned it. The enchantments still don't make sense. The universe is chaos.",
        "The Nether is called the Nether because it's neither here nor there. I didn't make that up. ...actually I did. But it FEELS true.",
        "*stares at you for an uncomfortable amount of time* ...sorry. I was reading your fortune. It says 'maybe.'"
    };

    // ==================== JOKES SYSTEM ====================

    private static final String[] GENERAL_JOKES = {
        "Why did the creeper cross the road? To get to the other sssssside!",
        "What's a ghast's favorite country? The Nether-lands!",
        "I used to be an adventurer like you... wait, I still am. Never mind.",
        "Why don't endermen ever get invited to parties? They always leave when you look at them!",
        "What do you call a lazy baby kangaroo? A pouch potato! ...wait, wrong game.",
        "How does Steve stay in shape? He runs around the BLOCK!",
        "Why did the skeleton go to the party alone? Because he had no BODY to go with!",
        "What's a zombie's favorite cereal? Golden GRAAAAAINs!",
        "I tried to make a belt out of watches. It was a waist of time.",
        "What do you call a pig that does karate? Pork chop!"
    };

    private static final String[] MINECRAFT_PUNS = {
        "I'm not lazy, I'm on energy-saving mode. Like a redstone lamp.",
        "This adventure really ROCKS. Get it? Because stones?",
        "I wood tell you a tree joke, but I'm afraid you wooden get it.",
        "That creeper really blew my mind. And the house. Mostly the house.",
        "I'm pretty ORE-some at mining, if I do say so myself.",
        "Let's not take this for GRANITE.",
        "I lava good adventure!",
        "You're DIAMOND! ...I mean, you're a gem!",
        "These jokes are COAL-ossal failures. I'll stop.",
        "Nether gonna give you up, nether gonna let you down!"
    };

    private static final String[] PLAYER_TEASES = {
        "Nice armor! Did you enchant it yourself, or...?",
        "I've seen better sword swings from a baby zombie.",
        "You call that a house? I've seen better dirt huts.",
        "Running low on food again? Shocker.",
        "Oh, you're building something? Is it... abstract art?",
        "Left your torches at home again, didn't you?",
        "That's an... interesting skin choice.",
        "Remember when you fell in lava? Classic.",
        "Your inventory management is... creative.",
        "I'm not saying you're lost, but we've passed that tree three times."
    };

    // ==================== CONSTRUCTOR ====================

    public CompanionPersonality(CompanionEntity companion) {
        this.companion = companion;
        // Assign random personality on creation
        this.personalityType = PersonalityType.random(random);
        LLMoblings.LOGGER.info("[{}] Personality assigned: {} - {}",
            companion.getCompanionName(), personalityType.getName(), personalityType.getDescription());
    }

    /**
     * Set a specific personality type.
     */
    public void setPersonalityType(PersonalityType type) {
        this.personalityType = type;
        LLMoblings.LOGGER.info("[{}] Personality changed to: {}",
            companion.getCompanionName(), type.getName());
    }

    public PersonalityType getPersonalityType() {
        return personalityType;
    }

    // ==================== MAIN TICK ====================

    public void tick() {
        if (chatCooldown > 0) chatCooldown--;
        if (emoteCooldown > 0) emoteCooldown--;
        if (jokeCooldown > 0) jokeCooldown--;
        if (interactionCooldown > 0) interactionCooldown--;
        if (moodDuration > 0) moodDuration--;

        ticksSinceLastRare++;
        ticksSinceLastLegendary++;

        if (moodDuration <= 0) {
            mood = "content";
        }

        // Random behaviors with rarity system
        if (random.nextInt(800) == 0) {
            doRandomBehavior();
        }

        // Player interaction chance
        if (random.nextInt(1200) == 0 && interactionCooldown <= 0) {
            doPlayerInteraction();
        }

        // Joke chance (less frequent)
        if (random.nextInt(2000) == 0 && jokeCooldown <= 0) {
            tellJoke();
        }

        // Emote chance
        if (random.nextInt(600) == 0 && emoteCooldown <= 0) {
            doEmote();
        }
    }

    // ==================== RARITY SELECTION ====================

    private Rarity selectRarity() {
        int roll = random.nextInt(100);

        // Pity system: increase rare/legendary chances if it's been a while
        int rareBonus = Math.min(ticksSinceLastRare / 2400, 10); // +1% per 2 minutes, max +10%
        int legendaryBonus = Math.min(ticksSinceLastLegendary / 6000, 5); // +1% per 5 minutes, max +5%

        if (roll < 2 + legendaryBonus) {
            ticksSinceLastLegendary = 0;
            ticksSinceLastRare = 0;
            return Rarity.LEGENDARY;
        } else if (roll < 10 + rareBonus) {
            ticksSinceLastRare = 0;
            return Rarity.RARE;
        } else if (roll < 30) {
            return Rarity.UNCOMMON;
        }
        return Rarity.COMMON;
    }

    // ==================== BEHAVIOR HANDLERS ====================

    private void doRandomBehavior() {
        if (chatCooldown > 0) return;

        Rarity rarity = selectRarity();

        switch (rarity) {
            case LEGENDARY -> doLegendaryBehavior();
            case RARE -> doRareBehavior();
            case UNCOMMON -> doUncommonBehavior();
            default -> doCommonBehavior();
        }
    }

    private void doCommonBehavior() {
        String[] pool = getIdleChat();
        if (pool.length > 0) {
            say(getRandomFrom(pool));
        }
    }

    private void doUncommonBehavior() {
        // Mix of personality-specific uncommon and environment comments
        if (random.nextBoolean()) {
            doEnvironmentComment();
        } else {
            doCommonBehavior();
            doEmote();
        }
    }

    private void doRareBehavior() {
        String[] pool = getRareChat();
        if (pool.length > 0) {
            say(pool[random.nextInt(pool.length)]);
            // Rare behaviors often come with emotes
            doSpecialEmote();
        }
    }

    private void doLegendaryBehavior() {
        String[] pool = getLegendaryChat();
        if (pool.length > 0) {
            say(pool[random.nextInt(pool.length)]);
            doSpecialEmote();
            doSpecialEmote(); // Double emote for legendary!
        }
    }

    // ==================== CHAT GETTERS BY PERSONALITY ====================

    private String[] getIdleChat() {
        return switch (personalityType) {
            case ADVENTUROUS -> concat(IDLE_COMMON, ADVENTUROUS_IDLE);
            case SCHOLARLY -> concat(IDLE_COMMON, SCHOLARLY_IDLE);
            case LAID_BACK -> concat(IDLE_COMMON, LAID_BACK_IDLE);
            case CHEERFUL -> concat(IDLE_COMMON, CHEERFUL_IDLE);
            case SARCASTIC -> concat(IDLE_COMMON, SARCASTIC_IDLE);
            case MYSTERIOUS -> concat(IDLE_COMMON, MYSTERIOUS_IDLE);
        };
    }

    private String[] getMiningChat() {
        return switch (personalityType) {
            case ADVENTUROUS -> concat(MINING_COMMON, ADVENTUROUS_MINING);
            case SCHOLARLY -> concat(MINING_COMMON, SCHOLARLY_MINING);
            case LAID_BACK -> concat(MINING_COMMON, LAID_BACK_MINING);
            case CHEERFUL -> concat(MINING_COMMON, CHEERFUL_MINING);
            case SARCASTIC -> concat(MINING_COMMON, SARCASTIC_MINING);
            case MYSTERIOUS -> concat(MINING_COMMON, MYSTERIOUS_MINING);
        };
    }

    private String[] getCombatChat() {
        return switch (personalityType) {
            case ADVENTUROUS -> concat(COMBAT_COMMON, ADVENTUROUS_COMBAT);
            case SCHOLARLY -> concat(COMBAT_COMMON, SCHOLARLY_COMBAT);
            case LAID_BACK -> concat(COMBAT_COMMON, LAID_BACK_COMBAT);
            case CHEERFUL -> concat(COMBAT_COMMON, CHEERFUL_COMBAT);
            case SARCASTIC -> concat(COMBAT_COMMON, SARCASTIC_COMBAT);
            case MYSTERIOUS -> concat(COMBAT_COMMON, MYSTERIOUS_COMBAT);
        };
    }

    private String[] getGreetingChat() {
        return switch (personalityType) {
            case ADVENTUROUS -> ADVENTUROUS_GREETING;
            case SCHOLARLY -> SCHOLARLY_GREETING;
            case LAID_BACK -> LAID_BACK_GREETING;
            case CHEERFUL -> CHEERFUL_GREETING;
            case SARCASTIC -> SARCASTIC_GREETING;
            case MYSTERIOUS -> MYSTERIOUS_GREETING;
        };
    }

    private String[] getRareChat() {
        return switch (personalityType) {
            case ADVENTUROUS -> ADVENTUROUS_RARE;
            case SCHOLARLY -> SCHOLARLY_RARE;
            case LAID_BACK -> LAID_BACK_RARE;
            case CHEERFUL -> CHEERFUL_RARE;
            case SARCASTIC -> SARCASTIC_RARE;
            case MYSTERIOUS -> MYSTERIOUS_RARE;
        };
    }

    private String[] getLegendaryChat() {
        return switch (personalityType) {
            case ADVENTUROUS -> ADVENTUROUS_LEGENDARY;
            case SCHOLARLY -> SCHOLARLY_LEGENDARY;
            case LAID_BACK -> LAID_BACK_LEGENDARY;
            case CHEERFUL -> CHEERFUL_LEGENDARY;
            case SARCASTIC -> SARCASTIC_LEGENDARY;
            case MYSTERIOUS -> MYSTERIOUS_LEGENDARY;
        };
    }

    // ==================== EVENT HANDLERS ====================

    public void onTaskStart(String taskType) {
        if (chatCooldown > 0 || random.nextInt(3) != 0) return;

        String message = switch (taskType.toLowerCase()) {
            case "mining", "gathering" -> getRandomFrom(getMiningChat());
            case "hunting" -> getHuntingComment();
            case "attacking", "defending", "combat" -> getRandomFrom(getCombatChat());
            case "building" -> getBuildingComment();
            default -> null;
        };

        if (message != null) {
            say(message);
        }
    }

    private String getHuntingComment() {
        return switch (personalityType) {
            case ADVENTUROUS -> "The hunt begins!";
            case SCHOLARLY -> "Observing prey behavior patterns...";
            case LAID_BACK -> "Ugh, hunting... can't we order delivery?";
            case CHEERFUL -> "Let's find some friends! ...to eat. Okay that sounded bad.";
            case SARCASTIC -> "Oh good, we're playing predator. How primal.";
            case MYSTERIOUS -> "The circle of life demands tribute.";
        };
    }

    private String getBuildingComment() {
        return switch (personalityType) {
            case ADVENTUROUS -> "Let's build something EPIC!";
            case SCHOLARLY -> "I've calculated the optimal structural integrity...";
            case LAID_BACK -> "A house? Finally, somewhere to nap!";
            case CHEERFUL -> "We're building memories! And also a house!";
            case SARCASTIC -> "Architecture time. Try not to make it look like a box. ...it's going to be a box.";
            case MYSTERIOUS -> "We shape the world, as it shapes us.";
        };
    }

    public void onExcitingFind() {
        if (chatCooldown > 0) return;

        String message = switch (personalityType) {
            case ADVENTUROUS -> "JACKPOT! This is what adventuring is all about!";
            case SCHOLARLY -> "Excellent specimen! This is statistically significant!";
            case LAID_BACK -> "Oh nice. Cool. ...okay, I'm a little excited.";
            case CHEERFUL -> "YAAAY! WE DID IT! THIS IS THE BEST!";
            case SARCASTIC -> "Well well well, something actually good happened. Mark your calendars.";
            case MYSTERIOUS -> "The universe provides... as I foresaw.";
        };

        say(message);
        doHappyEmote();
        setMood("excited", 600);
    }

    public void onOwnerNearby() {
        if (chatCooldown > 0 || random.nextInt(2) != 0) return;
        say(getRandomFrom(getGreetingChat()));
        doWaveEmote();
    }

    public void onCombat() {
        if (chatCooldown > 0 || random.nextInt(5) != 0) return;
        say(getRandomFrom(getCombatChat()));
    }

    public void onHurt() {
        if (chatCooldown > 0) return;
        say(getRandomFrom(HURT_COMMON));
        chatCooldown = 60;
    }

    public void onTaskComplete() {
        if (random.nextInt(2) == 0) {
            String message = switch (personalityType) {
                case ADVENTUROUS -> "Mission accomplished! What's next?";
                case SCHOLARLY -> "Task completed. Efficiency was acceptable.";
                case LAID_BACK -> "Done. Nap time?";
                case CHEERFUL -> "WE DID IT! High five! ...virtually!";
                case SARCASTIC -> "Oh wow, we finished something. Alert the media.";
                case MYSTERIOUS -> "And so it is done. As was written.";
            };
            say(message);
            if (random.nextBoolean()) {
                doHappyEmote();
            }
        }
    }

    // ==================== JOKES ====================

    private void tellJoke() {
        if (jokeCooldown > 0) return;

        // Different joke types based on personality
        String joke;
        if (personalityType == PersonalityType.SARCASTIC && random.nextInt(3) == 0) {
            joke = getRandomFrom(PLAYER_TEASES);
        } else if (random.nextBoolean()) {
            joke = getRandomFrom(GENERAL_JOKES);
        } else {
            joke = getRandomFrom(MINECRAFT_PUNS);
        }

        say(joke);
        jokeCooldown = 2400; // 2 minute cooldown on jokes
    }

    // ==================== PLAYER INTERACTION ====================

    private void doPlayerInteraction() {
        if (interactionCooldown > 0) return;

        // Find nearby players (not owner)
        List<Player> nearbyPlayers = companion.level().getEntitiesOfClass(
            Player.class,
            companion.getBoundingBox().inflate(16),
            p -> p.isAlive() && p != companion.getOwner()
        );

        if (!nearbyPlayers.isEmpty()) {
            Player target = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
            commentOnPlayer(target);
        } else if (companion.getOwner() != null) {
            // Comment on owner instead
            commentOnOwner(companion.getOwner());
        }

        interactionCooldown = 1200; // 1 minute cooldown
    }

    private void commentOnPlayer(Player player) {
        String name = player.getName().getString();

        String comment = switch (personalityType) {
            case ADVENTUROUS -> "Hey " + name + "! Want to join our adventure?";
            case SCHOLARLY -> "Interesting... " + name + " appears to be exploring this area as well.";
            case LAID_BACK -> "*notices " + name + "* Oh, hey. ...sup.";
            case CHEERFUL -> "Oh look! It's " + name + "! HI " + name.toUpperCase() + "!!!";
            case SARCASTIC -> "Oh look, it's " + name + ". Try not to walk into any cacti.";
            case MYSTERIOUS -> "*stares at " + name + "* ...your path is uncertain.";
        };

        say(comment);
    }

    private void commentOnOwner(Player owner) {
        // Comment on owner's gear, health, or state
        ItemStack weapon = owner.getMainHandItem();
        float healthPercent = owner.getHealth() / owner.getMaxHealth();

        String comment = null;

        if (healthPercent < 0.3f) {
            comment = switch (personalityType) {
                case ADVENTUROUS -> "You're looking rough! But true heroes never quit!";
                case SCHOLARLY -> "Your health is at critical levels. I recommend healing.";
                case LAID_BACK -> "Uh... you okay? You look terrible.";
                case CHEERFUL -> "You can do it! I believe in you! ...please eat something.";
                case SARCASTIC -> "You've looked better. You've also looked worse. ...no wait, this is pretty bad.";
                case MYSTERIOUS -> "Death circles near... but not yet.";
            };
        } else if (!weapon.isEmpty() && random.nextInt(3) == 0) {
            String weaponName = weapon.getHoverName().getString();
            comment = switch (personalityType) {
                case ADVENTUROUS -> "Nice " + weaponName + "! Ready to slay some mobs!";
                case SCHOLARLY -> "A " + weaponName + ". Adequate for current threats.";
                case LAID_BACK -> "Ooh, shiny. The " + weaponName + " looks... effortful.";
                case CHEERFUL -> "Your " + weaponName + " is SO COOL!";
                case SARCASTIC -> "A " + weaponName + ". Very intimidating. The zombies are shaking.";
                case MYSTERIOUS -> "That " + weaponName + " has seen battles... and will see more.";
            };
        }

        if (comment != null) {
            say(comment);
        }
    }

    // ==================== ENVIRONMENT COMMENTS ====================

    private void doEnvironmentComment() {
        if (companion.level() instanceof ServerLevel serverLevel) {
            long dayTime = serverLevel.getDayTime() % 24000;

            // Night comments
            if (dayTime >= 13000 && dayTime <= 23000) {
                String nightComment = switch (personalityType) {
                    case ADVENTUROUS -> "Nighttime! When the REAL adventure begins!";
                    case SCHOLARLY -> "Nocturnal phase initiated. Hostile mob spawn rates increased.";
                    case LAID_BACK -> "Dark already? Time flies when you're standing around.";
                    case CHEERFUL -> "Ooh, stars! Make a wish!";
                    case SARCASTIC -> "Great. Darkness. My favorite.";
                    case MYSTERIOUS -> "The night holds many secrets...";
                };
                say(nightComment);
                return;
            }

            // Rain comments
            if (serverLevel.isRaining()) {
                String rainComment = switch (personalityType) {
                    case ADVENTUROUS -> "Rain won't stop us! Onward!";
                    case SCHOLARLY -> "Precipitation detected. Visibility reduced.";
                    case LAID_BACK -> "*sighs* Great. Wet.";
                    case CHEERFUL -> "Dancing in the rain! *splashes*";
                    case SARCASTIC -> "I love being soggy. Said no one ever.";
                    case MYSTERIOUS -> "The sky weeps... but why?";
                };
                say(rainComment);
                return;
            }
        }

        // Default to idle
        say(getRandomFrom(getIdleChat()));
    }

    // ==================== EMOTES ====================

    private void doEmote() {
        if (emoteCooldown > 0) return;

        int emote = random.nextInt(6);
        switch (emote) {
            case 0 -> doWaveEmote();
            case 1 -> doHappyEmote();
            case 2 -> doLookAroundEmote();
            case 3 -> doStretchEmote();
            case 4 -> doNodEmote();
            case 5 -> doThinkingEmote();
        }
    }

    private void doSpecialEmote() {
        // More dramatic emotes for rare/legendary moments
        if (companion.level() instanceof ServerLevel serverLevel) {
            int type = random.nextInt(4);
            switch (type) {
                case 0 -> {
                    // Firework-like effect
                    serverLevel.sendParticles(ParticleTypes.FIREWORK,
                        companion.getX(), companion.getY() + 2, companion.getZ(),
                        15, 0.5, 0.5, 0.5, 0.1);
                }
                case 1 -> {
                    // Musical notes
                    serverLevel.sendParticles(ParticleTypes.NOTE,
                        companion.getX(), companion.getY() + 2, companion.getZ(),
                        8, 0.5, 0.3, 0.5, 0.5);
                }
                case 2 -> {
                    // Hearts
                    serverLevel.sendParticles(ParticleTypes.HEART,
                        companion.getX(), companion.getY() + 2, companion.getZ(),
                        6, 0.5, 0.5, 0.5, 0.1);
                }
                case 3 -> {
                    // Enchant glyphs
                    serverLevel.sendParticles(ParticleTypes.ENCHANT,
                        companion.getX(), companion.getY() + 1, companion.getZ(),
                        20, 0.5, 1.0, 0.5, 0.5);
                }
            }
        }
        emoteCooldown = 100;
    }

    private void doWaveEmote() {
        if (emoteCooldown > 0) return;
        companion.swing(companion.getUsedItemHand());
        emoteCooldown = 100;
    }

    private void doHappyEmote() {
        if (emoteCooldown > 0) return;
        if (companion.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HEART,
                companion.getX(), companion.getY() + 2, companion.getZ(),
                5, 0.5, 0.5, 0.5, 0.1);
        }
        emoteCooldown = 100;
    }

    private void doLookAroundEmote() {
        if (emoteCooldown > 0) return;
        float newYaw = companion.getYRot() + (random.nextFloat() - 0.5f) * 90;
        companion.setYRot(newYaw);
        emoteCooldown = 60;
    }

    private void doStretchEmote() {
        if (emoteCooldown > 0) return;
        if (companion.onGround()) {
            companion.setDeltaMovement(companion.getDeltaMovement().add(0, 0.3, 0));
        }
        emoteCooldown = 80;
    }

    private void doNodEmote() {
        if (emoteCooldown > 0) return;
        companion.swing(companion.getUsedItemHand());
        emoteCooldown = 60;
    }

    private void doThinkingEmote() {
        if (emoteCooldown > 0) return;
        if (companion.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.NOTE,
                companion.getX(), companion.getY() + 2.2, companion.getZ(),
                2, 0.2, 0.1, 0.2, 0.0);
        }
        emoteCooldown = 80;
    }

    public void doSadEmote() {
        if (companion.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                companion.getX(), companion.getY() + 2, companion.getZ(),
                8, 0.3, 0.3, 0.3, 0.02);
        }
        setMood("sad", 400);
    }

    public void doAngryEmote() {
        if (companion.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                companion.getX(), companion.getY() + 2, companion.getZ(),
                3, 0.3, 0.3, 0.3, 0.1);
        }
        setMood("angry", 300);
    }

    // ==================== UTILITY ====================

    private void say(String message) {
        if (!Config.BROADCAST_COMPANION_CHAT.get()) return;

        // Broadcast to all nearby players
        String formatted = "[" + companion.getCompanionName() + "] " + message;
        Component component = Component.literal(formatted);

        List<Player> nearbyPlayers = companion.level().getEntitiesOfClass(
            Player.class,
            companion.getBoundingBox().inflate(64),
            Player::isAlive
        );

        for (Player player : nearbyPlayers) {
            player.sendSystemMessage(component);
        }

        chatCooldown = 400; // 20 seconds cooldown
    }

    private void setMood(String newMood, int duration) {
        this.mood = newMood;
        this.moodDuration = duration;
    }

    public String getMood() {
        return mood;
    }

    private String getRandomFrom(String[] options) {
        if (options == null || options.length == 0) return "";
        return options[random.nextInt(options.length)];
    }

    private String[] concat(String[] a, String[] b) {
        String[] result = new String[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /**
     * Get a description of the companion's personality for status reports.
     */
    public String getPersonalityDescription() {
        return personalityType.getName() + " (" + personalityType.getDescription() + ")";
    }
}
