// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.forge;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.frierenflight.zoltraakcinematic.client.ZoltraakCinematicClientEvents;
import com.frierenflight.zoltraakcinematic.command.GargantuaCommands;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig;
import com.frierenflight.zoltraakcinematic.event.DefenseEvents;
import com.frierenflight.zoltraakcinematic.event.FlightEvents;
import com.frierenflight.zoltraakcinematic.forge.network.ForgeNetworkHandler;
import com.frierenflight.zoltraakcinematic.registry.*;
import io.redspace.ironsspellbooks.registries.CreativeTabRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(ZoltraakCinematicMod.MODID)
public class ZoltraakCinematicForgeMod {

    public ZoltraakCinematicForgeMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext context = ModLoadingContext.get();

        context.registerConfig(ModConfig.Type.COMMON, ZoltraakCinematicConfig.SPEC, "zoltraak_cinematic-common.toml");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.registerConfig(ModConfig.Type.CLIENT, ZoltraakCinematicClientConfig.SPEC, "zoltraak_cinematic-client.toml");
            ClientSetup.registerConfigScreen(context);
            ClientSetup.registerOptionalCompat();
        }

        // Register Deferred Registers to Forge Mod Event Bus
        ModCinematicAttributes.register(modBus);
        ModCinematicSchools.register(modBus);
        ModCinematicBlocks.register(modBus);
        ModCinematicItems.register(modBus);
        ModCinematicSounds.register(modBus);
        ModCinematicMobEffects.register(modBus);
        ModCinematicEntities.register(modBus);
        ModCinematicSpells.register(modBus);

        // Register Commands
        GargantuaCommands.register();

        // Initialize and register network channel
        ForgeNetworkHandler.register();

        // Register event listeners
        DefenseEvents.register();
        FlightEvents.register();

        // Register Creative Tab contents
        modBus.addListener(this::buildCreativeModeTabContents);

        // Register Client Events & Renderers
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ZoltraakCinematicClientEvents.register(modBus);
        }
    }

    private void buildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeTabRegistry.EQUIPMENT_TAB.getKey()) {
            if (ModCinematicItems.FRIEREN_STAFF != null) event.accept(ModCinematicItems.FRIEREN_STAFF.get());
            if (ModCinematicItems.MIRROR_LOTUS_RING != null) event.accept(ModCinematicItems.MIRROR_LOTUS_RING.get());
            if (ModCinematicItems.MANA_CONCEALMENT_PENDANT != null) event.accept(ModCinematicItems.MANA_CONCEALMENT_PENDANT.get());
            if (ModCinematicItems.ORDINARY_GRIMOIRE != null) event.accept(ModCinematicItems.ORDINARY_GRIMOIRE.get());
            if (ModCinematicItems.ELDER_SAGE_GRIMOIRE != null) event.accept(ModCinematicItems.ELDER_SAGE_GRIMOIRE.get());
            if (ModCinematicItems.HORN_OF_CORRUPTION != null) event.accept(ModCinematicItems.HORN_OF_CORRUPTION.get());
            if (ModCinematicItems.CORRUPTION_CORE != null) event.accept(ModCinematicItems.CORRUPTION_CORE.get());
            if (ModCinematicItems.CHRONOS_DILATATION_WATCH != null) event.accept(ModCinematicItems.CHRONOS_DILATATION_WATCH.get());
            if (ModCinematicItems.GARGANTUA_SCROLL != null) event.accept(ModCinematicItems.GARGANTUA_SCROLL.get().getDefaultInstance());
            if (ModCinematicItems.QUAL_SEALING_STONE != null) event.accept(ModCinematicItems.QUAL_SEALING_STONE.get());
            if (ModCinematicItems.QUAL_SPAWN_EGG != null) event.accept(ModCinematicItems.QUAL_SPAWN_EGG.get());
        }
    }

    private static final class ClientSetup {
        private static void registerConfigScreen(ModLoadingContext context) {
            context.registerExtensionPoint(
                    net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                            (minecraft, screen) -> new com.frierenflight.zoltraakcinematic.client.gui.ZoltraakConfigScreen(screen)
                    )
            );
        }

        private static void registerOptionalCompat() {
            if (net.minecraftforge.fml.ModList.get().isLoaded("embeddium")) {
                com.frierenflight.zoltraakcinematic.forge.compat.ZoltraakEmbeddiumConfig.register();
            }
        }
    }
}
