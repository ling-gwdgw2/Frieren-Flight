// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.compat.sodium;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.frierenflight.zoltraakcinematic.client.gui.ZoltraakConfigScreen;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Sodium Config API integration for Zoltraak visual, shader, glow, and audio settings.
 */
public class ZoltraakSodiumConfig implements ConfigEntryPoint {

    private static final int ZOLTRAAK_CYAN = 0x33C0FF;
    private static final ResourceLocation LOGO = id("textures/gui/logo.png");

    private static final StorageEventHandler STORAGE = () -> {
        if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
            try {
                ZoltraakCinematicClientConfig.SPEC.save();
            } catch (Exception ignored) {
            }
        }
    };

    public enum BarrierView {
        CENTER_FADE("center_fade"),
        MINIMAL("minimal"),
        EDGE_ONLY("edge_only"),
        FULL("full");

        private final String key;

        BarrierView(String key) {
            this.key = key;
        }

        public Component label() {
            return Component.translatable("config.zoltraak_cinematic.barrier_view." + this.key);
        }

        public Component description() {
            return Component.translatable("config.zoltraak_cinematic.barrier_view." + this.key + ".desc");
        }

        public static BarrierView fromConfig(String raw) {
            return BarrierView.valueOf(canonicalViewName(raw));
        }
    }

    public enum CircleView {
        CENTER_FADE("center_fade"),
        MINIMAL("minimal"),
        EDGE_ONLY("edge_only"),
        FULL("full");

        private final String key;

        CircleView(String key) {
            this.key = key;
        }

        public Component label() {
            return Component.translatable("config.zoltraak_cinematic.circle_view." + this.key);
        }

        public Component description() {
            return Component.translatable("config.zoltraak_cinematic.circle_view." + this.key + ".desc");
        }

        public static CircleView fromConfig(String raw) {
            return CircleView.valueOf(canonicalViewName(raw));
        }
    }

    private static String canonicalViewName(String raw) {
        if (raw == null) {
            return "CENTER_FADE";
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "MINIMAL", "LIGHT" -> "MINIMAL";
            case "EDGE_ONLY", "PERIMETER" -> "EDGE_ONLY";
            case "FULL" -> "FULL";
            default -> "CENTER_FADE";
        };
    }

    public enum HoleQuality {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high"),
        ULTRA("ultra");

        private final String key;

        HoleQuality(String key) {
            this.key = key;
        }

        public Component label() {
            return Component.translatable("config.zoltraak_cinematic.hole_quality." + this.key);
        }

        public Component description() {
            return Component.translatable("config.zoltraak_cinematic.hole_quality." + this.key + ".desc");
        }

        public static HoleQuality fromConfig(String raw) {
            if (raw == null) {
                return MEDIUM;
            }
            return switch (raw.trim().toUpperCase(Locale.ROOT)) {
                case "LOW" -> LOW;
                case "HIGH" -> HIGH;
                case "ULTRA" -> ULTRA;
                default -> MEDIUM;
            };
        }
    }

    public enum WorldGlowMode {
        SHADERS_ONLY("shaders_only"),
        ALWAYS("always"),
        OFF("off");

        private final String key;

        WorldGlowMode(String key) {
            this.key = key;
        }

        public Component label() {
            return Component.translatable("config.zoltraak_cinematic.world_glow." + this.key);
        }

        public Component description() {
            return Component.translatable("config.zoltraak_cinematic.world_glow." + this.key + ".desc");
        }

        public static WorldGlowMode fromConfig(String raw) {
            if (raw == null) {
                return SHADERS_ONLY;
            }
            return switch (raw.trim().toUpperCase(Locale.ROOT)) {
                case "ALWAYS" -> ALWAYS;
                case "OFF" -> OFF;
                default -> SHADERS_ONLY;
            };
        }
    }

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        ModOptionsBuilder mod = builder.registerOwnModOptions()
                .setName("Zoltraak")
                .setNonTintedIcon(LOGO)
                .setColorTheme(builder.createColorTheme().setBaseThemeRGB(ZOLTRAAK_CYAN));

        mod.addPage(builder.createExternalPage()
                .setName(Component.translatable("config.zoltraak_cinematic.sodium_page"))
                .setScreenConsumer(parent -> Minecraft.getInstance().setScreen(new ZoltraakConfigScreen(parent))));

        // Page 1: Barrier & Camera
        mod.addPage(builder.createOptionPage()
                .setName(Component.translatable("config.zoltraak_cinematic.page.barrier_camera"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("config.zoltraak_cinematic.group.barrier"))
                        .addOption(builder.createEnumOption(id("barrier_view"), BarrierView.class)
                                .setName(Component.translatable("config.zoltraak_cinematic.option.barrier_view"))
                                .setTooltip(BarrierView::description)
                                .setElementNameProvider(BarrierView::label)
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(BarrierView.CENTER_FADE)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.BARRIER_VIEW.set(val.name());
                                            }
                                        },
                                        () -> BarrierView.fromConfig(ZoltraakCinematicClientConfig.getBarrierView())))
                        .addOption(builder.createIntegerOption(id("barrier_opacity"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.barrier_opacity"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.barrier_opacity.tooltip"))
                                .setRange(0, 100, 5)
                                .setValueFormatter(v -> Component.literal(v + "%"))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(35)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.BARRIER_OPACITY.set(val / 100.0d);
                                            }
                                        },
                                        () -> snapToRange(ZoltraakCinematicClientConfig.getBarrierOpacity() * 100.0f, 0, 100, 5))))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("config.zoltraak_cinematic.group.magic_circle"))
                        .addOption(builder.createEnumOption(id("circle_view"), CircleView.class)
                                .setName(Component.translatable("config.zoltraak_cinematic.option.circle_view"))
                                .setTooltip(CircleView::description)
                                .setElementNameProvider(CircleView::label)
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(CircleView.CENTER_FADE)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.CIRCLE_VIEW.set(val.name());
                                            }
                                        },
                                        () -> CircleView.fromConfig(ZoltraakCinematicClientConfig.getCircleView())))
                        .addOption(builder.createIntegerOption(id("circle_opacity"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.circle_opacity"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.circle_opacity.tooltip"))
                                .setRange(0, 100, 5)
                                .setValueFormatter(v -> Component.literal(v + "%"))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(45)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.CIRCLE_OPACITY.set(val / 100.0d);
                                            }
                                        },
                                        () -> snapToRange(ZoltraakCinematicClientConfig.getCircleOpacity() * 100.0f, 0, 100, 5))))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("config.zoltraak_cinematic.group.camera"))
                        .addOption(builder.createBooleanOption(id("camera_shake"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.camera_shake"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.camera_shake.tooltip"))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(true)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.CAMERA_SHAKE.set(val);
                                            }
                                        },
                                        ZoltraakCinematicClientConfig::isCameraShakeEnabled))
                        .addOption(builder.createIntegerOption(id("shake_strength"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.shake_strength"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.shake_strength.tooltip"))
                                // Scale 0.0..3.0 -> 0..30 (0.1x steps) to stay within IntegerCache (-128..127) for Sodium StatefulOption reference checks
                                .setRange(0, 30, 1)
                                .setValueFormatter(v -> Component.literal(String.format(Locale.ROOT, "%.1fx", v / 10.0d)))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(10)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.SHAKE_STRENGTH.set(val / 10.0d);
                                            }
                                        },
                                        () -> snapToRange(ZoltraakCinematicClientConfig.getShakeStrength() * 10.0f, 0, 30, 1)))
                        .addOption(builder.createBooleanOption(id("screen_flash"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.screen_flash"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.screen_flash.tooltip"))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(true)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.SCREEN_FLASH.set(val);
                                            }
                                        },
                                        ZoltraakCinematicClientConfig::isScreenFlashEnabled))
                        .addOption(builder.createIntegerOption(id("flash_strength"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.flash_strength"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.flash_strength.tooltip"))
                                .setRange(0, 100, 5)
                                .setValueFormatter(v -> Component.literal(v + "%"))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(100)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.FLASH_STRENGTH.set(val / 100.0d);
                                            }
                                        },
                                        () -> snapToRange(ZoltraakCinematicClientConfig.getFlashStrength() * 100.0f, 0, 100, 5)))
                        .addOption(builder.createBooleanOption(id("flight_tilt"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.flight_tilt"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.flight_tilt.tooltip"))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(true)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.FLIGHT_TILT.set(val);
                                            }
                                        },
                                        ZoltraakCinematicClientConfig::isFlightTiltEnabled))
                        .addOption(builder.createBooleanOption(id("cutscene_camera"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.cinematic_camera"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.cinematic_camera.tooltip"))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(true)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.CUTSCENE_CAMERA.set(val);
                                            }
                                        },
                                        ZoltraakCinematicClientConfig::isCutsceneCameraEnabled))));

        // Page 2: Shaders & FX
        mod.addPage(builder.createOptionPage()
                .setName(Component.translatable("config.zoltraak_cinematic.page.shaders"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("config.zoltraak_cinematic.group.black_hole"))
                        .addOption(builder.createEnumOption(id("black_hole_quality"), HoleQuality.class)
                                .setName(Component.translatable("config.zoltraak_cinematic.option.hole_quality"))
                                .setTooltip(HoleQuality::description)
                                .setElementNameProvider(HoleQuality::label)
                                .setImpact(OptionImpact.HIGH)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(HoleQuality.MEDIUM)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.HOLE_QUALITY.set(val.name());
                                            }
                                        },
                                        () -> HoleQuality.fromConfig(ZoltraakCinematicClientConfig.getHoleQuality())))
                        .addOption(builder.createIntegerOption(id("disk_size"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.disk_size"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.disk_size.tooltip"))
                                .setRange(8, 40, 1)
                                .setValueFormatter(v -> Component.literal(String.valueOf(v)))
                                .setImpact(OptionImpact.MEDIUM)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(22)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.DISK_SIZE.set((double) val);
                                            }
                                        },
                                        () -> snapToRange(ZoltraakCinematicClientConfig.getDiskSize(), 8, 40, 1)))
                        .addOption(builder.createBooleanOption(id("light_bending"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.light_bending"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.light_bending.tooltip"))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(true)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.LIGHT_BENDING.set(val);
                                            }
                                        },
                                        ZoltraakCinematicClientConfig::isLightBendingEnabled)))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("config.zoltraak_cinematic.group.effects"))
                        .addOption(builder.createBooleanOption(id("lens_flares"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.lens_flares"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.lens_flares.tooltip"))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(true)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.LENS_FLARES.set(val);
                                            }
                                        },
                                        ZoltraakCinematicClientConfig::isLensFlaresEnabled))
                        .addOption(builder.createBooleanOption(id("color_fringe"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.color_fringe"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.color_fringe.tooltip"))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(true)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.COLOR_FRINGE.set(val);
                                            }
                                        },
                                        ZoltraakCinematicClientConfig::isColorFringeEnabled))
                        .addOption(builder.createEnumOption(id("world_glow"), WorldGlowMode.class)
                                .setName(Component.translatable("config.zoltraak_cinematic.option.world_glow"))
                                .setTooltip(WorldGlowMode::description)
                                .setElementNameProvider(WorldGlowMode::label)
                                .setImpact(OptionImpact.MEDIUM)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(WorldGlowMode.SHADERS_ONLY)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.WORLD_GLOW.set(val.name());
                                            }
                                        },
                                        () -> WorldGlowMode.fromConfig(ZoltraakCinematicClientConfig.getWorldGlow())))));

        // Page 3: Spell Glow
        OptionGroupBuilder glowGroup = builder.createOptionGroup()
                .setName(Component.translatable("config.zoltraak_cinematic.bloom_header"));
        addGlowOption(builder, glowGroup, "glow_zoltraak", "bloom_zoltraak",
                ZoltraakCinematicClientConfig.ZOLTRAAK_GLOW,
                ZoltraakCinematicClientConfig::getZoltraakGlow);
        addGlowOption(builder, glowGroup, "glow_great", "bloom_great",
                ZoltraakCinematicClientConfig.GREAT_ZOLTRAAK_GLOW,
                ZoltraakCinematicClientConfig::getGreatZoltraakGlow);
        addGlowOption(builder, glowGroup, "glow_barrage", "bloom_barrage",
                ZoltraakCinematicClientConfig.BARRAGE_GLOW,
                ZoltraakCinematicClientConfig::getBarrageGlow);
        addGlowOption(builder, glowGroup, "glow_barrier", "bloom_barrier",
                ZoltraakCinematicClientConfig.BARRIER_GLOW,
                ZoltraakCinematicClientConfig::getBarrierGlow);
        addGlowOption(builder, glowGroup, "glow_gargantua", "bloom_gargantua",
                ZoltraakCinematicClientConfig.GARGANTUA_GLOW,
                ZoltraakCinematicClientConfig::getGargantuaGlow);
        addGlowOption(builder, glowGroup, "glow_qual", "bloom_qual",
                ZoltraakCinematicClientConfig.QUAL_GLOW,
                ZoltraakCinematicClientConfig::getQualGlow);
        addGlowOption(builder, glowGroup, "glow_explosion", "bloom_explosion",
                ZoltraakCinematicClientConfig.EXPLOSION_GLOW,
                ZoltraakCinematicClientConfig::getExplosionGlow);

        mod.addPage(builder.createOptionPage()
                .setName(Component.translatable("config.zoltraak_cinematic.page.bloom"))
                .addOptionGroup(glowGroup));

        // Page 4: Explosion Charge Particles & Flares (values scaled inside 0..50 to preserve Sodium IntegerCache safety)
        OptionGroupBuilder expParticleGroup = builder.createOptionGroup()
                .setName(Component.translatable("config.zoltraak_cinematic.group.explosion_particles"));
        addScaledIntOption(builder, expParticleGroup, "explosion_gold_sparks", "explosion_gold_sparks",
                2, 32, 16,
                v -> Component.translatable("config.zoltraak_cinematic.option.explosion_gold_sparks.value", v * 10),
                v -> safeSetInt(ZoltraakCinematicClientConfig.EXPLOSION_GOLD_SPARKS, v * 10),
                () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionGoldSparks() / 10.0f, 2, 32, 1));
        addScaledIntOption(builder, expParticleGroup, "explosion_stars", "explosion_stars",
                2, 40, 16,
                v -> Component.translatable("config.zoltraak_cinematic.option.explosion_stars.value", v * 4),
                v -> safeSetInt(ZoltraakCinematicClientConfig.EXPLOSION_FOUR_POINT_STARS, v * 4),
                () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionFourPointStars() / 4.0f, 2, 40, 1));
        addScaledIntOption(builder, expParticleGroup, "explosion_parabola", "explosion_parabola",
                2, 40, 16,
                v -> Component.translatable("config.zoltraak_cinematic.option.explosion_parabola.value", v * 4),
                v -> safeSetInt(ZoltraakCinematicClientConfig.EXPLOSION_PARABOLA_STREAMS, v * 4),
                () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionParabolaStreams() / 4.0f, 2, 40, 1));
        addScaledIntOption(builder, expParticleGroup, "explosion_particle_scale", "explosion_particle_scale",
                10, 40, 23,
                v -> Component.literal((v * 5) + "%"),
                v -> safeSetDouble(ZoltraakCinematicClientConfig.EXPLOSION_PARTICLE_SCALE, (v * 5) / 100.0d),
                () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionParticleScale() * 20.0f, 10, 40, 1));

        OptionGroupBuilder expFlareGroup = builder.createOptionGroup()
                .setName(Component.translatable("config.zoltraak_cinematic.group.explosion_flares"));
        addScaledIntOption(builder, expFlareGroup, "explosion_cross_rays", "explosion_cross_rays",
                0, 12, 6,
                v -> v <= 0
                        ? Component.translatable("config.zoltraak_cinematic.option.explosion_cross_rays.off")
                        : Component.translatable("config.zoltraak_cinematic.option.explosion_cross_rays.value", v * 2),
                v -> safeSetInt(ZoltraakCinematicClientConfig.EXPLOSION_CROSS_RAYS, v),
                () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionCrossRays(), 0, 12, 1));
        addScaledIntOption(builder, expFlareGroup, "explosion_suction", "explosion_suction",
                0, 30, 14,
                v -> Component.translatable("config.zoltraak_cinematic.option.explosion_suction.value", v * 2),
                v -> safeSetInt(ZoltraakCinematicClientConfig.EXPLOSION_SUCTION_LINES, v * 2),
                () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionSuctionLines() / 2.0f, 0, 30, 1));
        addScaledIntOption(builder, expFlareGroup, "explosion_sky_flare", "explosion_sky_flare",
                0, 50, 27,
                v -> Component.literal((v * 5) + "%"),
                v -> safeSetDouble(ZoltraakCinematicClientConfig.EXPLOSION_SKY_FLARE_STRENGTH, (v * 5) / 100.0d),
                () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionSkyFlareStrength() * 20.0f, 0, 50, 1));
        addScaledIntOption(builder, expFlareGroup, "explosion_sky_height", "explosion_sky_height",
                10, 50, 20,
                v -> Component.literal((v * 5) + "%"),
                v -> safeSetDouble(ZoltraakCinematicClientConfig.EXPLOSION_SKY_HEIGHT, (v * 5) / 100.0d),
                () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionSkyHeight() * 20.0f, 10, 50, 1));
        addScaledIntOption(builder, expFlareGroup, "explosion_sky_ring_scale", "explosion_sky_ring_scale",
                8, 50, 20,
                v -> Component.literal((v * 5) + "%"),
                v -> safeSetDouble(ZoltraakCinematicClientConfig.EXPLOSION_SKY_RING_SCALE, (v * 5) / 100.0d),
                () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionSkyRingScale() * 20.0f, 8, 50, 1));
        addScaledIntOption(builder, expFlareGroup, "explosion_dome_scale", "explosion_dome_scale",
                12, 40, 20,
                v -> Component.literal((v * 5) + "%"),
                v -> safeSetDouble(ZoltraakCinematicClientConfig.EXPLOSION_DOME_SCALE, (v * 5) / 100.0d),
                () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionDomeScale() * 20.0f, 12, 40, 1));

        mod.addPage(builder.createOptionPage()
                .setName(Component.translatable("config.zoltraak_cinematic.page.explosion"))
                .addOptionGroup(expParticleGroup)
                .addOptionGroup(expFlareGroup));

        // Page 5: Audio
        mod.addPage(builder.createOptionPage()
                .setName(Component.translatable("config.zoltraak_cinematic.page.audio"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("config.zoltraak_cinematic.group.audio"))
                        .addOption(builder.createBooleanOption(id("ear_ringing"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.ear_ringing"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.ear_ringing.tooltip"))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(true)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.EAR_RINGING.set(val);
                                            }
                                        },
                                        ZoltraakCinematicClientConfig::isEarRingingEnabled))
                        .addOption(builder.createIntegerOption(id("ear_ringing_volume"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.ear_ringing_volume"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.ear_ringing_volume.tooltip"))
                                .setRange(0, 100, 5)
                                .setValueFormatter(v -> Component.literal(v + "%"))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(80)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.EAR_RINGING_VOLUME.set(val / 100.0d);
                                            }
                                        },
                                        () -> snapToRange(ZoltraakCinematicClientConfig.getEarRingingVolume() * 100.0f, 0, 100, 5)))
                        .addOption(builder.createIntegerOption(id("mod_sound_volume"))
                                .setName(Component.translatable("config.zoltraak_cinematic.option.mod_audio_volume"))
                                .setTooltip(Component.translatable("config.zoltraak_cinematic.option.mod_audio_volume.tooltip"))
                                // Scale 0%..200% -> 0..40 (5% per step) to cover full 0.0..2.0 config range inside IntegerCache (-128..127)
                                .setRange(0, 40, 1)
                                .setValueFormatter(v -> Component.literal((v * 5) + "%"))
                                .setImpact(OptionImpact.LOW)
                                .setStorageHandler(STORAGE)
                                .setDefaultValue(20)
                                .setBinding(
                                        val -> {
                                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                                ZoltraakCinematicClientConfig.MOD_SOUND_VOLUME.set((val * 5) / 100.0d);
                                            }
                                        },
                                        () -> snapToRange(ZoltraakCinematicClientConfig.getModSoundVolume() * 20.0f, 0, 40, 1)))));
    }

    private static OptionGroupBuilder addGlowOption(
            ConfigBuilder builder,
            OptionGroupBuilder group,
            String idPath,
            String optionKey,
            ModConfigSpec.DoubleValue configValue,
            DoubleSupplier getter
    ) {
        String keyPrefix = "config.zoltraak_cinematic.option." + optionKey;
        return group.addOption(builder.createIntegerOption(id(idPath))
                .setName(Component.translatable(keyPrefix))
                .setTooltip(Component.translatable(keyPrefix + ".tooltip"))
                // Scale 0%..200% -> 0..40 (5% per step) to stay inside IntegerCache (-128..127)
                .setRange(0, 40, 1)
                .setValueFormatter(v -> Component.literal((v * 5) + "%"))
                .setImpact(OptionImpact.LOW)
                .setStorageHandler(STORAGE)
                .setDefaultValue(20)
                .setBinding(
                        val -> {
                            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                                configValue.set((val * 5) / 100.0d);
                            }
                        },
                        () -> snapToRange((float) (getter.getAsDouble() * 20.0d), 0, 40, 1)));
    }

    private static void addScaledIntOption(
            ConfigBuilder builder,
            OptionGroupBuilder group,
            String idPath,
            String optionKey,
            int min,
            int max,
            int defVal,
            IntFunction<Component> formatter,
            IntConsumer setter,
            IntSupplier getter
    ) {
        String keyPrefix = "config.zoltraak_cinematic.option." + optionKey;
        group.addOption(builder.createIntegerOption(id(idPath))
                .setName(Component.translatable(keyPrefix))
                .setTooltip(Component.translatable(keyPrefix + ".tooltip"))
                .setRange(min, max, 1)
                .setValueFormatter(formatter::apply)
                .setImpact(OptionImpact.LOW)
                .setStorageHandler(STORAGE)
                .setDefaultValue(defVal)
                .setBinding(setter::accept, getter::getAsInt));
    }

    private static void safeSetInt(ModConfigSpec.IntValue spec, int val) {
        if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
            spec.set(val);
        }
    }

    private static void safeSetDouble(ModConfigSpec.DoubleValue spec, double val) {
        if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
            spec.set(val);
        }
    }

    private static int snapToRange(float raw, int min, int max, int step) {
        int clamped = Math.max(min, Math.min(max, Math.round(raw)));
        return min + Math.round((float) (clamped - min) / step) * step;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, path);
    }
}
