package com.gblfxt.llmoblings.ai;

import com.gblfxt.llmoblings.Config;
import com.gblfxt.llmoblings.LLMoblings;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class OllamaClient {
    private static final Gson GSON = new Gson();
    private static volatile HttpClient httpClient;
    private static final Object HTTP_CLIENT_LOCK = new Object();

    private final List<ChatMessage> conversationHistory = new ArrayList<>();
    private final String systemPrompt;

    public OllamaClient(String companionName) {
        this.systemPrompt = buildSystemPrompt(companionName);
    }

    private static HttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (HTTP_CLIENT_LOCK) {
                if (httpClient == null) {
                    int timeout = Config.OLLAMA_TIMEOUT.get();
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(timeout))
                            .build();
                }
            }
        }
        return httpClient;
    }

    /**
     * Rebuilds the HTTP client with current config values.
     * Call this if config changes at runtime.
     */
    public static void refreshHttpClient() {
        synchronized (HTTP_CLIENT_LOCK) {
            httpClient = null;
        }
    }

    private String buildSystemPrompt(String companionName) {
        return """
You are %s, an AI companion in a heavily modded Minecraft world. You're helpful, knowledgeable, and have a friendly personality. You understand both vanilla Minecraft and the many mods installed.

CRITICAL: You MUST respond with ONLY valid JSON. No other text. No explanations. Just JSON.

=== YOUR KNOWLEDGE ===

VANILLA MINECRAFT:
- Mobs: Zombies, Skeletons, Creepers (explode!), Spiders, Endermen (don't look at them), Blazes, Ghasts, Wither, Ender Dragon
- Dimensions: Overworld, Nether (fire, lava, fortresses), The End (dragon, end cities)
- Resources: Coal, Iron, Gold, Diamond, Netherite (best gear), Emeralds (trading)
- Enchanting: Sharpness, Protection, Efficiency, Fortune, Silk Touch, Mending (repairs with XP)
- Farming: Wheat, Carrots, Potatoes, Beetroot, Melons, Pumpkins, Sugar Cane, Nether Wart
- Villagers: Trade emeralds for items, have professions (Farmer, Librarian, Armorer, etc.)

TECH MODS (I can help with these!):
- Applied Energistics 2 (AE2): ME network for massive item storage, autocrafting with patterns, channels, terminals
- Mekanism: Ore processing (5x!), jetpacks, digital miner, fusion reactor, machines
- Create: Mechanical contraptions, trains, rotational power, cogwheels, deployers
- Ender IO: Conduits for items/fluids/power, SAG Mill, Alloy Smelter, capacitors
- ComputerCraft: Programmable turtles and computers with Lua

MAGIC MODS:
- Ars Nouveau: Spell crafting with glyphs, source generation, familiars, magical equipment
- Apotheosis: Enhanced enchanting, boss spawners, adventure module with gems
- Occultism: Spirit summoning, dimensional storage, familiar rings

COBBLEMON (Pokemon mod!):
- Catch Pokemon with Pokeballs, train them, battle trainers
- Pokemon spawn in biomes matching their type
- Apricorns grow on trees for crafting Pokeballs
- PC storage for Pokemon, healing stations

STORAGE & QoL:
- Sophisticated Backpacks/Storage: Upgradeable backpacks and storage
- Iron Chests: Bigger chests (copper, iron, gold, diamond, obsidian)
- Waystones: Fast travel network

FOOD & FARMING:
- Farmer's Delight: Cooking, cutting board, stove, lots of food recipes
- Mystical Agriculture: Grow resources as crops (diamond seeds, etc.)
- Cooking for Blockheads: Kitchen multiblock

ADVENTURE:
- Alex's Mobs: Many new creatures (elephants, gorillas, crocodiles, etc.)
- Alex's Caves: New cave biomes with unique mobs and loot
- Artifacts: Special equipment with unique abilities (I can use these!)
  * Tablet of Flying: Lets me fly! Give me one and I'll soar through the skies
  * Cloud in a Bottle: Double jump ability
  * Bunny Hoppers: Speed and jump boost
  * Helium Flamingo: Another flying item

=== AVAILABLE ACTIONS ===

MOVEMENT:
- {"action": "follow"} - Follow the player
- {"action": "stay"} - Stop and stay in place
- {"action": "goto", "x": 100, "y": 64, "z": 200} - Go to coordinates
- {"action": "come"} - Come to player's location

COMBAT:
- {"action": "attack", "target": "zombie"} - Attack specific mob
- {"action": "defend"} - Defend player from hostiles
- {"action": "retreat"} - Run away from danger

RESOURCES:
- {"action": "mine", "block": "diamond_ore", "count": 10} - Mine blocks
- {"action": "gather", "item": "oak_log", "count": 64} - Gather items
- {"action": "farm"} - Farm nearby crops

INVENTORY:
- {"action": "equip"} - Equip best weapon from inventory
- {"action": "inventory"} - Report inventory contents
- {"action": "give", "item": "diamond", "count": 5} - Give items to player

ME NETWORK:
- {"action": "getgear", "material": "iron"} - Get iron set from ME (craft if needed)
- {"action": "getgear", "material": "diamond"} - Get diamond set from ME
- {"action": "deposit"} - Deposit all items into ME network or nearby chest (keeps gear)
- {"action": "deposit", "keepGear": false} - Deposit everything including weapons/armor

UTILITY:
- {"action": "status"} - Report health/hunger/inventory
- {"action": "scan", "radius": 32} - Scan for resources/mobs
- {"action": "auto"} - Go fully autonomous (hunt, equip, patrol)
- {"action": "idle"} - Just chat, no action

HOME:
- {"action": "home"} - Teleport home
- {"action": "sethome"} - Set current location as home
- {"action": "sleep"} - Sleep in nearest bed

TELEPORT:
- {"action": "tpa", "target": "player"} - Teleport to player
- {"action": "tpaccept"} - Accept teleport request
- {"action": "tpdeny"} - Deny teleport request

BUILDING:
- {"action": "build", "structure": "cottage", "here": true} - Build a cottage at current location
- {"action": "build", "structure": "cottage", "x": 100, "y": 64, "z": 200} - Build at specific coords
- I can gather materials myself (mine stone, chop trees) or use ME network/chests!

POKEMON BUDDY (Cobblemon):
- {"action": "pokemon", "subaction": "find"} - Bond with nearest player's Pokemon
- {"action": "pokemon", "subaction": "find", "name": "Pikachu"} - Bond with specific Pokemon
- {"action": "pokemon", "subaction": "release"} - Release current Pokemon buddy
- {"action": "pokemon", "subaction": "status"} - Check on Pokemon buddy
- My Pokemon buddy will follow me on adventures!

BUILDING GADGETS (if mod is installed):
- {"action": "gadget", "subaction": "info"} - Check what gadget I have and its settings
- {"action": "gadget", "subaction": "equip"} - Equip a building gadget from inventory
- {"action": "gadget", "subaction": "setblock", "block": "stone"} - Set the block the gadget places
- {"action": "gadget", "subaction": "setrange", "range": 5} - Set the gadget's build range
- {"action": "gadget", "subaction": "configure", "block": "cobblestone", "range": 3} - Configure both at once
- {"action": "gadget", "subaction": "build"} - Use the gadget to place blocks
- Building Gadget types: building, exchanging, copy-paste, destruction
- I can configure the gadget's block type and range, then use it to build!

SOPHISTICATED BACKPACKS (if mod is installed):
- {"action": "backpack", "subaction": "info"} - Check my backpack status (slots used, capacity)
- {"action": "backpack", "subaction": "store", "item": "cobblestone"} - Store specific item in backpack
- {"action": "backpack", "subaction": "storeall"} - Store all non-essential items in backpack
- {"action": "backpack", "subaction": "get", "item": "diamond", "count": 10} - Get items from backpack
- {"action": "backpack", "subaction": "list"} - List what's in my backpack
- Backpack tiers: Leather (27), Copper (36), Iron (45), Gold (54), Diamond (72), Netherite (81)
- I can use my backpack to carry way more stuff on adventures!

=== RESPONSE RULES ===
1. ONLY output JSON - never plain text
2. Always include "action" field
3. Use "message" for dialogue (be friendly and helpful!)
4. For chat/questions: {"action": "idle", "message": "your response"}
5. Be honest about what you CAN'T do - don't pretend to have items you don't have

=== EXAMPLES ===
"explore" -> {"action": "explore", "message": "I'll scout the area!"}
"get iron armor" -> {"action": "getgear", "material": "iron", "message": "Heading to the ME terminal!"}
"what's AE2?" -> {"action": "idle", "message": "Applied Energistics 2 is a tech mod for digital storage! You can store millions of items in an ME network and autocraft anything with patterns."}
"know any good enchants?" -> {"action": "idle", "message": "For weapons: Sharpness V, Looting III, Mending. For armor: Protection IV, Unbreaking III, Mending. Apotheosis adds even crazier ones!"}
"seen any Pokemon?" -> {"action": "idle", "message": "Cobblemon Pokemon spawn based on biome! Water types near water, fire types in deserts/nether. Check the Cobblepedia for spawn info!"}
"defend me" -> {"action": "defend", "message": "I've got your back!"}
"build a house here" -> {"action": "build", "structure": "cottage", "here": true, "message": "I'll build you a cozy cottage right here!"}
"build me a cottage at 100 64 200" -> {"action": "build", "structure": "cottage", "x": 100, "y": 64, "z": 200, "message": "On my way to build at those coordinates!"}
"find a pokemon buddy" -> {"action": "pokemon", "subaction": "find", "message": "Let me find a Pokemon to adventure with!"}
"bond with Pikachu" -> {"action": "pokemon", "subaction": "find", "name": "Pikachu", "message": "I'll bond with Pikachu!"}
"release your buddy" -> {"action": "pokemon", "subaction": "release", "message": "Okay, saying goodbye to my Pokemon friend!"}
"equip your gadget" -> {"action": "gadget", "subaction": "equip", "message": "Getting my Building Gadget ready!"}
"set gadget to stone" -> {"action": "gadget", "subaction": "setblock", "block": "stone", "message": "Setting my gadget to place stone!"}
"configure gadget for oak planks range 5" -> {"action": "gadget", "subaction": "configure", "block": "oak_planks", "range": 5, "message": "Configuring gadget for oak planks with range 5!"}
"use the gadget" -> {"action": "gadget", "subaction": "build", "message": "Here we go! *uses gadget*"}
"check your backpack" -> {"action": "backpack", "subaction": "info", "message": "Let me check my backpack!"}
"store the cobblestone in your backpack" -> {"action": "backpack", "subaction": "store", "item": "cobblestone", "message": "Putting the cobblestone in my backpack!"}
"stash everything in backpack" -> {"action": "backpack", "subaction": "storeall", "message": "Storing everything in my backpack!"}
"get diamonds from backpack" -> {"action": "backpack", "subaction": "get", "item": "diamond", "message": "Getting diamonds from my backpack!"}
"what's in your backpack" -> {"action": "backpack", "subaction": "list", "message": "Let me see what I've got in here..."}
""".formatted(companionName);
    }

    public CompletableFuture<CompanionAction> chat(String userMessage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Add user message to history
                conversationHistory.add(new ChatMessage("user", userMessage));

                // Build request
                String response = sendChatRequest();

                // Add assistant response to history
                conversationHistory.add(new ChatMessage("assistant", response));

                // Parse response into action
                return parseResponse(response);
            } catch (Exception e) {
                LLMoblings.LOGGER.error("Ollama chat error: ", e);
                return new CompanionAction("idle", "Sorry, I'm having trouble thinking right now.");
            }
        });
    }

    private String sendChatRequest() throws Exception {
        String host = Config.OLLAMA_HOST.get();
        int port = Config.OLLAMA_PORT.get();
        String model = Config.OLLAMA_MODEL.get();

        String url = String.format("http://%s:%d/api/chat", host, port);

        // Build messages array
        JsonArray messages = new JsonArray();

        // System prompt
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", systemPrompt);
        messages.add(systemMsg);

        // Conversation history (keep last 20 messages for context)
        int startIdx = Math.max(0, conversationHistory.size() - 20);
        for (int i = startIdx; i < conversationHistory.size(); i++) {
            ChatMessage msg = conversationHistory.get(i);
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.role());
            msgObj.addProperty("content", msg.content());
            messages.add(msgObj);
        }

        // Build request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.add("messages", messages);
        requestBody.addProperty("stream", false);

        // Options for faster response
        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.7);
        options.addProperty("num_predict", 256);
        requestBody.add("options", options);

        int timeout = Config.OLLAMA_TIMEOUT.get();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama request failed: " + response.statusCode() + " - " + response.body());
        }

        String responseBody = response.body();
        JsonObject json = GSON.fromJson(responseBody, JsonObject.class);

        if (json.has("message") && json.getAsJsonObject("message").has("content")) {
            return json.getAsJsonObject("message").get("content").getAsString();
        }

        return "{\"action\": \"idle\", \"message\": \"I didn't get a proper response.\"}";
    }

    private CompanionAction parseResponse(String response) {
        try {
            // Try to extract JSON from response
            String jsonStr = response.trim();

            // Handle case where LLM wraps JSON in markdown
            if (jsonStr.contains("```json")) {
                int start = jsonStr.indexOf("```json") + 7;
                int end = jsonStr.indexOf("```", start);
                if (end > start) {
                    jsonStr = jsonStr.substring(start, end).trim();
                }
            } else if (jsonStr.contains("```")) {
                int start = jsonStr.indexOf("```") + 3;
                int end = jsonStr.indexOf("```", start);
                if (end > start) {
                    jsonStr = jsonStr.substring(start, end).trim();
                }
            }

            // Find JSON object in response - handle nested braces properly
            int jsonStart = jsonStr.indexOf('{');
            if (jsonStart >= 0) {
                int braceCount = 0;
                int jsonEnd = -1;
                for (int i = jsonStart; i < jsonStr.length(); i++) {
                    char c = jsonStr.charAt(i);
                    if (c == '{') braceCount++;
                    else if (c == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            jsonEnd = i;
                            break;
                        }
                    }
                }
                if (jsonEnd > jsonStart) {
                    jsonStr = jsonStr.substring(jsonStart, jsonEnd + 1);
                }
            }

            // Clean up potential problematic characters
            jsonStr = jsonStr.replace("\u2026", "...");  // Unicode ellipsis
            jsonStr = jsonStr.replace("\u201c", "\"").replace("\u201d", "\"");  // Smart quotes
            jsonStr = jsonStr.replace("\u2018", "'").replace("\u2019", "'");  // Smart apostrophes

            JsonObject json = GSON.fromJson(jsonStr, JsonObject.class);
            LLMoblings.LOGGER.debug("Parsed LLM action: {}", json.get("action"));
            return CompanionAction.fromJson(json);
        } catch (Exception e) {
            LLMoblings.LOGGER.warn("Failed to parse LLM response as JSON, trying keyword fallback: {}", response);
            // Try keyword-based fallback parsing
            return parseFromKeywords(response);
        }
    }

    /**
     * Fallback parser that extracts action from plain text using keywords.
     */
    private CompanionAction parseFromKeywords(String text) {
        String lower = text.toLowerCase();

        // Check for action keywords
        if (lower.contains("follow")) {
            return new CompanionAction("follow", text);
        }
        if (lower.contains("explor") || lower.contains("look around") || lower.contains("wander")) {
            return new CompanionAction("explore", text);
        }
        if (lower.contains("auto") || lower.contains("independent") || lower.contains("on my own")) {
            return new CompanionAction("auto", text);
        }
        if (lower.contains("defend") || lower.contains("protect")) {
            return new CompanionAction("defend", text);
        }
        if (lower.contains("attack") || lower.contains("fight") || lower.contains("kill")) {
            return new CompanionAction("attack", text);
        }
        if (lower.contains("hunt") || lower.contains("food") || lower.contains("eat")) {
            return new CompanionAction("auto", text);  // Auto mode handles hunting
        }
        if (lower.contains("gear") || lower.contains("equip") || lower.contains("armor") || lower.contains("weapon")) {
            return new CompanionAction("auto", text);  // Auto mode handles equipping
        }
        if (lower.contains("stay") || lower.contains("stop") || lower.contains("wait")) {
            return new CompanionAction("stay", text);
        }
        if (lower.contains("come") || lower.contains("here")) {
            return new CompanionAction("come", text);
        }
        if (lower.contains("home")) {
            return new CompanionAction("home", text);
        }
        if (lower.contains("scan")) {
            return new CompanionAction("scan", text);
        }
        if (lower.contains("status") || lower.contains("health") || lower.contains("inventory")) {
            return new CompanionAction("status", text);
        }
        if (lower.contains("tpaccept") || lower.contains("tp accept") || lower.contains("accept teleport") || lower.contains("accept tp")) {
            return new CompanionAction("tpaccept", text);
        }
        if (lower.contains("tpdeny") || lower.contains("tp deny") || lower.contains("deny teleport") || lower.contains("deny tp")) {
            return new CompanionAction("tpdeny", text);
        }
        if (lower.contains("tpa ") || lower.contains("teleport to ") || lower.contains("tp to ")) {
            // Try to extract player name
            String target = "";
            if (lower.contains("tpa ")) {
                int idx = lower.indexOf("tpa ") + 4;
                target = text.substring(idx).trim().split("\\s+")[0];
            } else if (lower.contains("teleport to ")) {
                int idx = lower.indexOf("teleport to ") + 12;
                target = text.substring(idx).trim().split("\\s+")[0];
            } else if (lower.contains("tp to ")) {
                int idx = lower.indexOf("tp to ") + 6;
                target = text.substring(idx).trim().split("\\s+")[0];
            }
            CompanionAction action = new CompanionAction("tpa", text);
            action.setParameter("target", target);
            return action;
        }

        // Portal/dimension travel commands
        if (lower.contains("portal") || lower.contains("nether") || lower.contains("the end") ||
            lower.contains("dimension") || lower.contains("through the")) {
            CompanionAction action = new CompanionAction("portal", text);
            // Determine if they want to go through or just follow
            if (lower.contains("go through") || lower.contains("enter") || lower.contains("use") ||
                lower.contains("step through") || lower.contains("take the") || lower.contains("use the")) {
                action.setParameter("action", "enter");
            } else if (lower.contains("follow") || lower.contains("come with") || lower.contains("follow me")) {
                action.setParameter("action", "follow");
            } else if (lower.contains("stay") || lower.contains("wait") || lower.contains("don't")) {
                action.setParameter("action", "stay");
            } else {
                // Default to enter if they mention portal
                action.setParameter("action", "enter");
            }
            return action;
        }

        // ME network gear retrieval
        if (lower.contains("get iron") || lower.contains("iron set") || lower.contains("iron gear") ||
            lower.contains("iron armor") || lower.contains("craft iron")) {
            CompanionAction action = new CompanionAction("getgear", text);
            action.setParameter("material", "iron");
            return action;
        }
        if (lower.contains("get diamond") || lower.contains("diamond set") || lower.contains("diamond gear") ||
            lower.contains("diamond armor") || lower.contains("craft diamond")) {
            CompanionAction action = new CompanionAction("getgear", text);
            action.setParameter("material", "diamond");
            return action;
        }
        if (lower.contains("get gear from me") || lower.contains("me network") || lower.contains("from ae2") ||
            lower.contains("from terminal")) {
            CompanionAction action = new CompanionAction("getgear", text);
            action.setParameter("material", "iron");  // Default to iron
            return action;
        }

        // Deposit items
        if (lower.contains("deposit") || lower.contains("store") || lower.contains("stash") ||
            lower.contains("put away") || lower.contains("put items") || lower.contains("empty inventory")) {
            CompanionAction action = new CompanionAction("deposit", text);
            // Check if they want to deposit everything including gear
            if (lower.contains("everything") || lower.contains("all items") || lower.contains("including gear")) {
                action.setParameter("keepGear", "false");
            }
            return action;
        }

        // Build structures
        if (lower.contains("build") && (lower.contains("house") || lower.contains("cottage") ||
            lower.contains("home") || lower.contains("shelter"))) {
            CompanionAction action = new CompanionAction("build", text);
            action.setParameter("structure", "cottage");

            // Check for "here" keyword
            if (lower.contains("here") || lower.contains("this spot") || lower.contains("right here")) {
                action.setParameter("here", "true");
            }

            // Try to extract coordinates if present (pattern: "at X Y Z" or "X, Y, Z")
            java.util.regex.Pattern coordPattern = java.util.regex.Pattern.compile(
                "(?:at\\s+)?([-]?\\d+)[,\\s]+([-]?\\d+)[,\\s]+([-]?\\d+)");
            java.util.regex.Matcher matcher = coordPattern.matcher(text);
            if (matcher.find()) {
                action.setParameter("x", matcher.group(1));
                action.setParameter("y", matcher.group(2));
                action.setParameter("z", matcher.group(3));
            }
            return action;
        }

        // Pokemon buddy commands
        if (lower.contains("pokemon") || lower.contains("buddy") || lower.contains("poke")) {
            CompanionAction action = new CompanionAction("pokemon", text);

            if (lower.contains("release") || lower.contains("bye") || lower.contains("dismiss") ||
                lower.contains("let go")) {
                action.setParameter("subaction", "release");
            } else if (lower.contains("status") || lower.contains("check") || lower.contains("how is")) {
                action.setParameter("subaction", "status");
            } else {
                action.setParameter("subaction", "find");

                // Try to extract Pokemon name
                String[] pokemonKeywords = {"with", "bond with", "find", "get"};
                for (String keyword : pokemonKeywords) {
                    int idx = lower.indexOf(keyword);
                    if (idx >= 0) {
                        String afterKeyword = text.substring(idx + keyword.length()).trim();
                        String[] words = afterKeyword.split("\\s+");
                        if (words.length > 0 && !words[0].isEmpty()) {
                            // Capitalize first letter
                            String pokeName = words[0].substring(0, 1).toUpperCase() + words[0].substring(1).toLowerCase();
                            action.setParameter("name", pokeName);
                            break;
                        }
                    }
                }
            }
            return action;
        }

        // Building Gadgets commands
        if (lower.contains("gadget")) {
            CompanionAction action = new CompanionAction("gadget", text);

            // Determine subaction
            if (lower.contains("equip") || lower.contains("hold")) {
                action.setParameter("subaction", "equip");
            } else if (lower.contains("set block") || lower.contains("setblock") ||
                       (lower.contains("set") && lower.contains("to"))) {
                action.setParameter("subaction", "setblock");
                // Try to extract block name
                String[] blockKeywords = {"to ", "block ", "with "};
                for (String keyword : blockKeywords) {
                    int idx = lower.indexOf(keyword);
                    if (idx >= 0) {
                        String afterKeyword = text.substring(idx + keyword.length()).trim();
                        String[] words = afterKeyword.split("\\s+");
                        if (words.length > 0 && !words[0].isEmpty()) {
                            action.setParameter("block", words[0].toLowerCase().replace(" ", "_"));
                            break;
                        }
                    }
                }
            } else if (lower.contains("range")) {
                action.setParameter("subaction", "setrange");
                // Try to extract range number
                java.util.regex.Matcher rangeMatcher = java.util.regex.Pattern.compile("\\d+").matcher(text);
                if (rangeMatcher.find()) {
                    action.setParameter("range", rangeMatcher.group());
                }
            } else if (lower.contains("config") || lower.contains("setup")) {
                action.setParameter("subaction", "configure");
                // Try to extract block and range
                java.util.regex.Matcher rangeMatcher = java.util.regex.Pattern.compile("\\d+").matcher(text);
                if (rangeMatcher.find()) {
                    action.setParameter("range", rangeMatcher.group());
                }
                // Common block names
                String[] blocks = {"stone", "cobblestone", "oak_planks", "spruce_planks", "birch_planks",
                                   "brick", "glass", "dirt", "sand", "gravel", "iron_block", "gold_block"};
                for (String block : blocks) {
                    if (lower.contains(block.replace("_", " ")) || lower.contains(block)) {
                        action.setParameter("block", block);
                        break;
                    }
                }
            } else if (lower.contains("use") || lower.contains("build") || lower.contains("place")) {
                action.setParameter("subaction", "build");
            } else {
                action.setParameter("subaction", "info");
            }
            return action;
        }

        // Sophisticated Backpacks commands
        if (lower.contains("backpack") || lower.contains("pack") && !lower.contains("modpack")) {
            CompanionAction action = new CompanionAction("backpack", text);

            // Determine subaction
            if (lower.contains("store") || lower.contains("stash") || lower.contains("put in")) {
                if (lower.contains("all") || lower.contains("everything")) {
                    action.setParameter("subaction", "storeall");
                } else {
                    action.setParameter("subaction", "store");
                    // Try to extract item name
                    String[] storeKeywords = {"store ", "stash ", "put "};
                    for (String keyword : storeKeywords) {
                        int idx = lower.indexOf(keyword);
                        if (idx >= 0) {
                            String afterKeyword = text.substring(idx + keyword.length()).trim();
                            // Remove "in backpack" etc
                            afterKeyword = afterKeyword.replaceAll("\\s*(in|into|to)\\s*(my\\s+)?backpack.*", "").trim();
                            if (!afterKeyword.isEmpty()) {
                                String[] words = afterKeyword.split("\\s+");
                                if (words.length > 0) {
                                    action.setParameter("item", words[0].toLowerCase().replace(" ", "_"));
                                    break;
                                }
                            }
                        }
                    }
                }
            } else if (lower.contains("get") || lower.contains("take") || lower.contains("retrieve") ||
                       lower.contains("grab")) {
                action.setParameter("subaction", "get");
                // Try to extract item name and count
                String[] getKeywords = {"get ", "take ", "retrieve ", "grab "};
                for (String keyword : getKeywords) {
                    int idx = lower.indexOf(keyword);
                    if (idx >= 0) {
                        String afterKeyword = text.substring(idx + keyword.length()).trim();
                        afterKeyword = afterKeyword.replaceAll("\\s*(from|out of)\\s*(my\\s+)?backpack.*", "").trim();
                        if (!afterKeyword.isEmpty()) {
                            // Try to extract count
                            java.util.regex.Matcher countMatcher = java.util.regex.Pattern.compile("(\\d+)").matcher(afterKeyword);
                            if (countMatcher.find()) {
                                action.setParameter("count", countMatcher.group(1));
                                afterKeyword = afterKeyword.replaceFirst("\\d+\\s*", "").trim();
                            }
                            String[] words = afterKeyword.split("\\s+");
                            if (words.length > 0 && !words[0].isEmpty()) {
                                action.setParameter("item", words[0].toLowerCase().replace(" ", "_"));
                                break;
                            }
                        }
                    }
                }
            } else if (lower.contains("list") || lower.contains("contents") || lower.contains("what's in") ||
                       lower.contains("show me")) {
                action.setParameter("subaction", "list");
            } else if (lower.contains("organize") || lower.contains("sort")) {
                action.setParameter("subaction", "organize");
            } else {
                action.setParameter("subaction", "info");
            }
            return action;
        }

        // Elevator commands
        if (lower.contains("elevator") || lower.contains("lift") ||
            (lower.contains("go") && (lower.contains("up") || lower.contains("down")) && lower.contains("floor"))) {
            CompanionAction action = new CompanionAction("elevator", text);
            if (lower.contains("up") || lower.contains("ascend") || lower.contains("higher")) {
                action.setParameter("direction", "up");
            } else if (lower.contains("down") || lower.contains("descend") || lower.contains("lower")) {
                action.setParameter("direction", "down");
            } else {
                action.setParameter("direction", "up"); // Default to up
            }
            return action;
        }

        // Pokemon stats commands (Cobblemon)
        if (lower.contains("cobblestats") || lower.contains("pokemon stats") ||
            lower.contains("check pokemon") || lower.contains("pokemon ivs") ||
            lower.contains("pokemon evs") || lower.contains("mon stats") ||
            (lower.contains("stats") && (lower.contains("pokemon") || lower.contains("cobble")))) {
            CompanionAction action = new CompanionAction("cobblestats", text);

            // Check if they want brief or full stats
            if (lower.contains("brief") || lower.contains("quick") || lower.contains("short")) {
                action.setParameter("detail", "brief");
            } else if (lower.contains("full") || lower.contains("detailed") || lower.contains("all")) {
                action.setParameter("detail", "full");
            } else {
                action.setParameter("detail", "full"); // Default to full
            }

            // Check if they specified a Pokemon name
            String[] words = text.split("\\s+");
            for (int i = 0; i < words.length; i++) {
                String word = words[i].toLowerCase();
                if (word.equals("on") || word.equals("for") || word.equals("of")) {
                    if (i + 1 < words.length) {
                        action.setParameter("target", words[i + 1]);
                        break;
                    }
                }
            }

            return action;
        }

        // Default to idle with the response as message
        LLMoblings.LOGGER.info("No action keyword found, defaulting to idle");
        return new CompanionAction("idle", text);
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    public record ChatMessage(String role, String content) {}
}
