package com.gblfxt.player2npc;

import com.gblfxt.player2npc.command.CompanionCommand;
import com.gblfxt.player2npc.entity.CompanionEntity;
import com.gblfxt.player2npc.network.NetworkHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(Player2NPC.MOD_ID)
public class Player2NPC {
    public static final String MOD_ID = "player2npc";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    // Entity type registry
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);

    // Companion entity type
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> COMPANION =
            ENTITY_TYPES.register("companion", () -> EntityType.Builder.<CompanionEntity>of(CompanionEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(id("companion").toString()));

    public Player2NPC(IEventBus modEventBus, ModContainer modContainer) {
        // Register entity types
        ENTITY_TYPES.register(modEventBus);

        // Register mod event listeners
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerEntityAttributes);
        modEventBus.addListener(this::registerPayloads);

        // Register game event listeners
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);

        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        LOGGER.info("Player2NPC initialized - AI companions powered by Ollama");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NetworkHandler.register();
            LOGGER.info("Player2NPC network registered");
        });
    }

    private void registerEntityAttributes(final EntityAttributeCreationEvent event) {
        event.put(COMPANION.get(), CompanionEntity.createAttributes().build());
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        NetworkHandler.registerPayloads(event);
    }

    private void registerCommands(final RegisterCommandsEvent event) {
        CompanionCommand.register(event.getDispatcher());
        LOGGER.info("Player2NPC commands registered");
    }

    private void onServerTick(ServerTickEvent.Post event) {
        // AI controllers are ticked by the entity itself in CompanionEntity.tick()
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
