package com.gblfxt.llmoblings;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // Ollama settings
    public static final ModConfigSpec.ConfigValue<String> OLLAMA_HOST;
    public static final ModConfigSpec.ConfigValue<Integer> OLLAMA_PORT;
    public static final ModConfigSpec.ConfigValue<String> OLLAMA_MODEL;
    public static final ModConfigSpec.ConfigValue<Integer> OLLAMA_TIMEOUT;

    // Companion settings
    public static final ModConfigSpec.ConfigValue<Integer> MAX_COMPANIONS_PER_PLAYER;
    public static final ModConfigSpec.ConfigValue<Boolean> COMPANIONS_TAKE_DAMAGE;
    public static final ModConfigSpec.ConfigValue<Boolean> COMPANIONS_NEED_FOOD;
    public static final ModConfigSpec.ConfigValue<Double> COMPANION_FOLLOW_DISTANCE;
    public static final ModConfigSpec.ConfigValue<Integer> ITEM_PICKUP_RADIUS;
    public static final ModConfigSpec.ConfigValue<Boolean> COMPANIONS_LOAD_CHUNKS;

    // Chat settings
    public static final ModConfigSpec.ConfigValue<String> CHAT_PREFIX;
    public static final ModConfigSpec.ConfigValue<Boolean> BROADCAST_COMPANION_CHAT;
    public static final ModConfigSpec.ConfigValue<Boolean> ALLOW_OTHER_PLAYER_INTERACTION;

    static {
        BUILDER.comment("Ollama LLM Configuration").push("ollama");

        OLLAMA_HOST = BUILDER
                .comment("Ollama server hostname or IP address")
                .define("host", "192.168.70.24");

        OLLAMA_PORT = BUILDER
                .comment("Ollama server port")
                .defineInRange("port", 11434, 1, 65535);

        OLLAMA_MODEL = BUILDER
                .comment("Ollama model to use (e.g., llama3:8b, mistral:7b, gemma:2b)")
                .define("model", "llama3:8b");

        OLLAMA_TIMEOUT = BUILDER
                .comment("Request timeout in seconds")
                .defineInRange("timeout", 30, 5, 300);

        BUILDER.pop();

        BUILDER.comment("Companion Behavior").push("companion");

        MAX_COMPANIONS_PER_PLAYER = BUILDER
                .comment("Maximum number of AI companions per player")
                .defineInRange("maxPerPlayer", 3, 1, 10);

        COMPANIONS_TAKE_DAMAGE = BUILDER
                .comment("Whether companions can take damage")
                .define("takeDamage", true);

        COMPANIONS_NEED_FOOD = BUILDER
                .comment("Whether companions need food (hunger system)")
                .define("needFood", false);

        COMPANION_FOLLOW_DISTANCE = BUILDER
                .comment("Default follow distance from owner")
                .defineInRange("followDistance", 5.0, 1.0, 50.0);

        ITEM_PICKUP_RADIUS = BUILDER
                .comment("Radius in blocks for item pickup")
                .defineInRange("itemPickupRadius", 3, 0, 10);

        COMPANIONS_LOAD_CHUNKS = BUILDER
                .comment("Whether companions force-load their chunk (allows them to work when players are offline)")
                .define("loadChunks", true);

        BUILDER.pop();

        BUILDER.comment("Chat Settings").push("chat");

        CHAT_PREFIX = BUILDER
                .comment("Prefix to address companions in chat (e.g., '@companion')")
                .define("prefix", "@");

        BROADCAST_COMPANION_CHAT = BUILDER
                .comment("Whether to broadcast companion responses to all nearby players")
                .define("broadcastChat", true);

        ALLOW_OTHER_PLAYER_INTERACTION = BUILDER
                .comment("Whether other players (not the owner) can talk to and command companions")
                .define("allowOtherPlayerInteraction", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
