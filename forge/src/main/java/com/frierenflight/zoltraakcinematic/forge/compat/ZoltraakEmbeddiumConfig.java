// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.forge.compat;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import com.google.common.collect.ImmutableList;
import me.jellysquid.mods.sodium.client.gui.options.OptionGroup;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpl;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.gui.options.TextProvider;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlValueFormatter;
import me.jellysquid.mods.sodium.client.gui.options.control.CyclingControl;
import me.jellysquid.mods.sodium.client.gui.options.control.SliderControl;
import me.jellysquid.mods.sodium.client.gui.options.control.TickBoxControl;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import org.embeddedt.embeddium.api.OptionGUIConstructionEvent;
import org.embeddedt.embeddium.client.gui.options.OptionIdentifier;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Embeddium 0.3.x Video Options GUI integration for Forge 1.20.1.
 * Registers all 5 Zoltraak config pages natively inside Embeddium's sidebar alongside Oculus.
 */
public final class ZoltraakEmbeddiumConfig {

    private static final Object DUMMY_DATA = new Object();

    private static final OptionStorage<Object> STORAGE = new OptionStorage<>() {
        @Override
        public Object getData() {
            return DUMMY_DATA;
        }

        @Override
        public void save() {
            if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
                try {
                    ZoltraakCinematicClientConfig.SPEC.save();
                } catch (Exception ignored) {
                }
            }
        }
    };

    private static boolean registered = false;

    private ZoltraakEmbeddiumConfig() {}

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        OptionGUIConstructionEvent.BUS.addListener(ZoltraakEmbeddiumConfig::onBuildPages);
    }

    public enum BarrierView implements TextProvider {
        CENTER_FADE("center_fade"),
        MINIMAL("minimal"),
        EDGE_ONLY("edge_only"),
        FULL("full");

        private final String key;

        BarrierView(String key) {
            this.key = key;
        }

        @Override
        public Component getLocalizedName() {
            return Component.translatable("config.zoltraak_cinematic.barrier_view." + this.key);
        }

        public Component description() {
            return Component.translatable("config.zoltraak_cinematic.barrier_view." + this.key + ".desc");
        }

        public static BarrierView fromConfig(String raw) {
            return BarrierView.valueOf(canonicalViewName(raw));
        }
    }

    public enum CircleView implements TextProvider {
        CENTER_FADE("center_fade"),
        MINIMAL("minimal"),
        EDGE_ONLY("edge_only"),
        FULL("full");

        private final String key;

        CircleView(String key) {
            this.key = key;
        }

        @Override
        public Component getLocalizedName() {
            return Component.translatable("config.zoltraak_cinematic.circle_view." + this.key);
        }

        public Component description() {
            return Component.translatable("config.zoltraak_cinematic.circle_view." + this.key + ".desc");
        }

        public static CircleView fromConfig(String raw) {
            return CircleView.valueOf(canonicalViewName(raw));
        }
    }

    public enum HoleQuality implements TextProvider {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high"),
        ULTRA("ultra");

        private final String key;

        HoleQuality(String key) {
            this.key = key;
        }

        @Override
        public Component getLocalizedName() {
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

    public enum WorldGlowMode implements TextProvider {
        SHADERS_ONLY("shaders_only"),
        ALWAYS("always"),
        OFF("off");

        private final String key;

        WorldGlowMode(String key) {
            this.key = key;
        }

        @Override
        public Component getLocalizedName() {
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

    private static void onBuildPages(OptionGUIConstructionEvent event) {
        event.addPage(buildBarrierCameraPage());
        event.addPage(buildShadersPage());
        event.addPage(buildGlowPage());
        event.addPage(buildExplosionPage());
        event.addPage(buildAudioPage());
    }

    private static OptionPage buildBarrierCameraPage() {
        OptionGroup barrierGroup = OptionGroup.createBuilder()
                .setId(id("group_barrier"))
                .add(enumOption(
                        "barrier_view",
                        "config.zoltraak_cinematic.option.barrier_view",
                        joinDescriptions(
                                BarrierView.CENTER_FADE.description(),
                                BarrierView.MINIMAL.description(),
                                BarrierView.EDGE_ONLY.description(),
                                BarrierView.FULL.description()
                        ),
                        BarrierView.class,
                        OptionImpact.LOW,
                        val -> safeSetString(ZoltraakCinematicClientConfig.BARRIER_VIEW, val.name()),
                        () -> BarrierView.fromConfig(ZoltraakCinematicClientConfig.getBarrierView())
                ))
                .add(intOption(
                        "barrier_opacity",
                        "config.zoltraak_cinematic.option.barrier_opacity",
                        0, 100, 5,
                        v -> Component.literal(v + "%"),
                        OptionImpact.LOW,
                        val -> safeSetDouble(ZoltraakCinematicClientConfig.BARRIER_OPACITY, val / 100.0d),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getBarrierOpacity() * 100.0f, 0, 100, 5)
                ))
                .build();

        OptionGroup circleGroup = OptionGroup.createBuilder()
                .setId(id("group_magic_circle"))
                .add(enumOption(
                        "circle_view",
                        "config.zoltraak_cinematic.option.circle_view",
                        joinDescriptions(
                                CircleView.CENTER_FADE.description(),
                                CircleView.MINIMAL.description(),
                                CircleView.EDGE_ONLY.description(),
                                CircleView.FULL.description()
                        ),
                        CircleView.class,
                        OptionImpact.LOW,
                        val -> safeSetString(ZoltraakCinematicClientConfig.CIRCLE_VIEW, val.name()),
                        () -> CircleView.fromConfig(ZoltraakCinematicClientConfig.getCircleView())
                ))
                .add(intOption(
                        "circle_opacity",
                        "config.zoltraak_cinematic.option.circle_opacity",
                        0, 100, 5,
                        v -> Component.literal(v + "%"),
                        OptionImpact.LOW,
                        val -> safeSetDouble(ZoltraakCinematicClientConfig.CIRCLE_OPACITY, val / 100.0d),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getCircleOpacity() * 100.0f, 0, 100, 5)
                ))
                .build();

        OptionGroup cameraGroup = OptionGroup.createBuilder()
                .setId(id("group_camera"))
                .add(boolOption(
                        "camera_shake",
                        "config.zoltraak_cinematic.option.camera_shake",
                        OptionImpact.LOW,
                        val -> safeSetBool(ZoltraakCinematicClientConfig.CAMERA_SHAKE, val),
                        ZoltraakCinematicClientConfig::isCameraShakeEnabled
                ))
                .add(intOption(
                        "shake_strength",
                        "config.zoltraak_cinematic.option.shake_strength",
                        0, 30, 1,
                        v -> Component.literal(String.format(Locale.ROOT, "%.1fx", v / 10.0d)),
                        OptionImpact.LOW,
                        val -> safeSetDouble(ZoltraakCinematicClientConfig.SHAKE_STRENGTH, val / 10.0d),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getShakeStrength() * 10.0f, 0, 30, 1)
                ))
                .add(boolOption(
                        "screen_flash",
                        "config.zoltraak_cinematic.option.screen_flash",
                        OptionImpact.LOW,
                        val -> safeSetBool(ZoltraakCinematicClientConfig.SCREEN_FLASH, val),
                        ZoltraakCinematicClientConfig::isScreenFlashEnabled
                ))
                .add(intOption(
                        "flash_strength",
                        "config.zoltraak_cinematic.option.flash_strength",
                        0, 100, 5,
                        v -> Component.literal(v + "%"),
                        OptionImpact.LOW,
                        val -> safeSetDouble(ZoltraakCinematicClientConfig.FLASH_STRENGTH, val / 100.0d),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getFlashStrength() * 100.0f, 0, 100, 5)
                ))
                .add(boolOption(
                        "flight_tilt",
                        "config.zoltraak_cinematic.option.flight_tilt",
                        OptionImpact.LOW,
                        val -> safeSetBool(ZoltraakCinematicClientConfig.FLIGHT_TILT, val),
                        ZoltraakCinematicClientConfig::isFlightTiltEnabled
                ))
                .add(boolOption(
                        "cutscene_camera",
                        "config.zoltraak_cinematic.option.cinematic_camera",
                        OptionImpact.LOW,
                        val -> safeSetBool(ZoltraakCinematicClientConfig.CUTSCENE_CAMERA, val),
                        ZoltraakCinematicClientConfig::isCutsceneCameraEnabled
                ))
                .build();

        return new OptionPage(
                pageId("barrier_camera"),
                Component.translatable("config.zoltraak_cinematic.page.barrier_camera"),
                ImmutableList.of(barrierGroup, circleGroup, cameraGroup)
        );
    }

    private static OptionPage buildShadersPage() {
        OptionGroup blackHoleGroup = OptionGroup.createBuilder()
                .setId(id("group_black_hole"))
                .add(enumOption(
                        "black_hole_quality",
                        "config.zoltraak_cinematic.option.hole_quality",
                        joinDescriptions(
                                HoleQuality.LOW.description(),
                                HoleQuality.MEDIUM.description(),
                                HoleQuality.HIGH.description(),
                                HoleQuality.ULTRA.description()
                        ),
                        HoleQuality.class,
                        OptionImpact.HIGH,
                        val -> safeSetString(ZoltraakCinematicClientConfig.HOLE_QUALITY, val.name()),
                        () -> HoleQuality.fromConfig(ZoltraakCinematicClientConfig.getHoleQuality())
                ))
                .add(intOption(
                        "disk_size",
                        "config.zoltraak_cinematic.option.disk_size",
                        8, 40, 1,
                        v -> Component.literal(String.valueOf(v)),
                        OptionImpact.MEDIUM,
                        val -> safeSetDouble(ZoltraakCinematicClientConfig.DISK_SIZE, (double) val),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getDiskSize(), 8, 40, 1)
                ))
                .add(boolOption(
                        "light_bending",
                        "config.zoltraak_cinematic.option.light_bending",
                        OptionImpact.LOW,
                        val -> safeSetBool(ZoltraakCinematicClientConfig.LIGHT_BENDING, val),
                        ZoltraakCinematicClientConfig::isLightBendingEnabled
                ))
                .build();

        OptionGroup effectsGroup = OptionGroup.createBuilder()
                .setId(id("group_effects"))
                .add(boolOption(
                        "lens_flares",
                        "config.zoltraak_cinematic.option.lens_flares",
                        OptionImpact.LOW,
                        val -> safeSetBool(ZoltraakCinematicClientConfig.LENS_FLARES, val),
                        ZoltraakCinematicClientConfig::isLensFlaresEnabled
                ))
                .add(boolOption(
                        "color_fringe",
                        "config.zoltraak_cinematic.option.color_fringe",
                        OptionImpact.LOW,
                        val -> safeSetBool(ZoltraakCinematicClientConfig.COLOR_FRINGE, val),
                        ZoltraakCinematicClientConfig::isColorFringeEnabled
                ))
                .add(enumOption(
                        "world_glow",
                        "config.zoltraak_cinematic.option.world_glow",
                        joinDescriptions(
                                WorldGlowMode.SHADERS_ONLY.description(),
                                WorldGlowMode.ALWAYS.description(),
                                WorldGlowMode.OFF.description()
                        ),
                        WorldGlowMode.class,
                        OptionImpact.MEDIUM,
                        val -> safeSetString(ZoltraakCinematicClientConfig.WORLD_GLOW, val.name()),
                        () -> WorldGlowMode.fromConfig(ZoltraakCinematicClientConfig.getWorldGlow())
                ))
                .build();

        return new OptionPage(
                pageId("shaders"),
                Component.translatable("config.zoltraak_cinematic.page.shaders"),
                ImmutableList.of(blackHoleGroup, effectsGroup)
        );
    }

    private static OptionPage buildGlowPage() {
        OptionGroup glowGroup = OptionGroup.createBuilder()
                .setId(id("group_glow"))
                .add(glowOption("glow_zoltraak", "bloom_zoltraak",
                        ZoltraakCinematicClientConfig.ZOLTRAAK_GLOW,
                        ZoltraakCinematicClientConfig::getZoltraakGlow))
                .add(glowOption("glow_great", "bloom_great",
                        ZoltraakCinematicClientConfig.GREAT_ZOLTRAAK_GLOW,
                        ZoltraakCinematicClientConfig::getGreatZoltraakGlow))
                .add(glowOption("glow_barrage", "bloom_barrage",
                        ZoltraakCinematicClientConfig.BARRAGE_GLOW,
                        ZoltraakCinematicClientConfig::getBarrageGlow))
                .add(glowOption("glow_barrier", "bloom_barrier",
                        ZoltraakCinematicClientConfig.BARRIER_GLOW,
                        ZoltraakCinematicClientConfig::getBarrierGlow))
                .add(glowOption("glow_gargantua", "bloom_gargantua",
                        ZoltraakCinematicClientConfig.GARGANTUA_GLOW,
                        ZoltraakCinematicClientConfig::getGargantuaGlow))
                .add(glowOption("glow_qual", "bloom_qual",
                        ZoltraakCinematicClientConfig.QUAL_GLOW,
                        ZoltraakCinematicClientConfig::getQualGlow))
                .add(glowOption("glow_explosion", "bloom_explosion",
                        ZoltraakCinematicClientConfig.EXPLOSION_GLOW,
                        ZoltraakCinematicClientConfig::getExplosionGlow))
                .build();

        return new OptionPage(
                pageId("bloom"),
                Component.translatable("config.zoltraak_cinematic.page.bloom"),
                ImmutableList.of(glowGroup)
        );
    }

    private static OptionPage buildExplosionPage() {
        OptionGroup particleGroup = OptionGroup.createBuilder()
                .setId(id("group_explosion_particles"))
                .add(intOption(
                        "explosion_gold_sparks",
                        "config.zoltraak_cinematic.option.explosion_gold_sparks",
                        2, 32, 1,
                        v -> Component.translatable("config.zoltraak_cinematic.option.explosion_gold_sparks.value", v * 10),
                        OptionImpact.LOW,
                        v -> safeSetInt(ZoltraakCinematicClientConfig.EXPLOSION_GOLD_SPARKS, v * 10),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionGoldSparks() / 10.0f, 2, 32, 1)
                ))
                .add(intOption(
                        "explosion_stars",
                        "config.zoltraak_cinematic.option.explosion_stars",
                        2, 40, 1,
                        v -> Component.translatable("config.zoltraak_cinematic.option.explosion_stars.value", v * 4),
                        OptionImpact.LOW,
                        v -> safeSetInt(ZoltraakCinematicClientConfig.EXPLOSION_FOUR_POINT_STARS, v * 4),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionFourPointStars() / 4.0f, 2, 40, 1)
                ))
                .add(intOption(
                        "explosion_parabola",
                        "config.zoltraak_cinematic.option.explosion_parabola",
                        2, 40, 1,
                        v -> Component.translatable("config.zoltraak_cinematic.option.explosion_parabola.value", v * 4),
                        OptionImpact.LOW,
                        v -> safeSetInt(ZoltraakCinematicClientConfig.EXPLOSION_PARABOLA_STREAMS, v * 4),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionParabolaStreams() / 4.0f, 2, 40, 1)
                ))
                .add(intOption(
                        "explosion_particle_scale",
                        "config.zoltraak_cinematic.option.explosion_particle_scale",
                        10, 40, 1,
                        v -> Component.literal((v * 5) + "%"),
                        OptionImpact.LOW,
                        v -> safeSetDouble(ZoltraakCinematicClientConfig.EXPLOSION_PARTICLE_SCALE, (v * 5) / 100.0d),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionParticleScale() * 20.0f, 10, 40, 1)
                ))
                .build();

        OptionGroup flareGroup = OptionGroup.createBuilder()
                .setId(id("group_explosion_flares"))
                .add(intOption(
                        "explosion_cross_rays",
                        "config.zoltraak_cinematic.option.explosion_cross_rays",
                        0, 12, 1,
                        v -> v <= 0
                                ? Component.translatable("config.zoltraak_cinematic.option.explosion_cross_rays.off")
                                : Component.translatable("config.zoltraak_cinematic.option.explosion_cross_rays.value", v * 2),
                        OptionImpact.LOW,
                        v -> safeSetInt(ZoltraakCinematicClientConfig.EXPLOSION_CROSS_RAYS, v),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionCrossRays(), 0, 12, 1)
                ))
                .add(intOption(
                        "explosion_suction",
                        "config.zoltraak_cinematic.option.explosion_suction",
                        0, 30, 1,
                        v -> Component.translatable("config.zoltraak_cinematic.option.explosion_suction.value", v * 2),
                        OptionImpact.LOW,
                        v -> safeSetInt(ZoltraakCinematicClientConfig.EXPLOSION_SUCTION_LINES, v * 2),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionSuctionLines() / 2.0f, 0, 30, 1)
                ))
                .add(intOption(
                        "explosion_sky_flare",
                        "config.zoltraak_cinematic.option.explosion_sky_flare",
                        0, 50, 1,
                        v -> Component.literal((v * 5) + "%"),
                        OptionImpact.LOW,
                        v -> safeSetDouble(ZoltraakCinematicClientConfig.EXPLOSION_SKY_FLARE_STRENGTH, (v * 5) / 100.0d),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionSkyFlareStrength() * 20.0f, 0, 50, 1)
                ))
                .add(intOption(
                        "explosion_sky_height",
                        "config.zoltraak_cinematic.option.explosion_sky_height",
                        10, 50, 1,
                        v -> Component.literal((v * 5) + "%"),
                        OptionImpact.LOW,
                        v -> safeSetDouble(ZoltraakCinematicClientConfig.EXPLOSION_SKY_HEIGHT, (v * 5) / 100.0d),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionSkyHeight() * 20.0f, 10, 50, 1)
                ))
                .add(intOption(
                        "explosion_sky_ring_scale",
                        "config.zoltraak_cinematic.option.explosion_sky_ring_scale",
                        8, 50, 1,
                        v -> Component.literal((v * 5) + "%"),
                        OptionImpact.LOW,
                        v -> safeSetDouble(ZoltraakCinematicClientConfig.EXPLOSION_SKY_RING_SCALE, (v * 5) / 100.0d),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionSkyRingScale() * 20.0f, 8, 50, 1)
                ))
                .add(intOption(
                        "explosion_dome_scale",
                        "config.zoltraak_cinematic.option.explosion_dome_scale",
                        12, 40, 1,
                        v -> Component.literal((v * 5) + "%"),
                        OptionImpact.LOW,
                        v -> safeSetDouble(ZoltraakCinematicClientConfig.EXPLOSION_DOME_SCALE, (v * 5) / 100.0d),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getExplosionDomeScale() * 20.0f, 12, 40, 1)
                ))
                .build();

        return new OptionPage(
                pageId("explosion"),
                Component.translatable("config.zoltraak_cinematic.page.explosion"),
                ImmutableList.of(particleGroup, flareGroup)
        );
    }

    private static OptionPage buildAudioPage() {
        OptionGroup audioGroup = OptionGroup.createBuilder()
                .setId(id("group_audio"))
                .add(intOption(
                        "mod_sound_volume",
                        "config.zoltraak_cinematic.option.mod_audio_volume",
                        0, 40, 1,
                        v -> Component.literal((v * 5) + "%"),
                        OptionImpact.LOW,
                        val -> safeSetDouble(ZoltraakCinematicClientConfig.MOD_SOUND_VOLUME, (val * 5) / 100.0d),
                        () -> snapToRange(ZoltraakCinematicClientConfig.getModSoundVolume() * 20.0f, 0, 40, 1)
                ))
                .build();

        return new OptionPage(
                pageId("audio"),
                Component.translatable("config.zoltraak_cinematic.page.audio"),
                ImmutableList.of(audioGroup)
        );
    }

    private static OptionImpl<Object, Boolean> boolOption(
            String idPath,
            String keyPrefix,
            OptionImpact impact,
            Consumer<Boolean> setter,
            BooleanSupplier getter
    ) {
        return OptionImpl.createBuilder(Boolean.TYPE, STORAGE)
                .setId(id(idPath))
                .setName(Component.translatable(keyPrefix))
                .setTooltip(Component.translatable(keyPrefix + ".tooltip"))
                .setControl(TickBoxControl::new)
                .setImpact(impact)
                .setBinding((opts, val) -> setter.accept(val), opts -> getter.getAsBoolean())
                .build();
    }

    private static OptionImpl<Object, Integer> intOption(
            String idPath,
            String keyPrefix,
            int min,
            int max,
            int interval,
            ControlValueFormatter formatter,
            OptionImpact impact,
            IntConsumer setter,
            IntSupplier getter
    ) {
        return OptionImpl.createBuilder(Integer.TYPE, STORAGE)
                .setId(id(idPath))
                .setName(Component.translatable(keyPrefix))
                .setTooltip(Component.translatable(keyPrefix + ".tooltip"))
                .setControl(opt -> new SliderControl(opt, min, max, interval, formatter))
                .setImpact(impact)
                .setBinding((opts, val) -> setter.accept(val), opts -> getter.getAsInt())
                .build();
    }

    private static <E extends Enum<E> & TextProvider> OptionImpl<Object, E> enumOption(
            String idPath,
            String nameKey,
            Component tooltip,
            Class<E> enumClass,
            OptionImpact impact,
            Consumer<E> setter,
            java.util.function.Supplier<E> getter
    ) {
        E[] constants = enumClass.getEnumConstants();
        Component[] names = new Component[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = constants[i].getLocalizedName();
        }
        return OptionImpl.createBuilder(enumClass, STORAGE)
                .setId(id(idPath))
                .setName(Component.translatable(nameKey))
                .setTooltip(tooltip)
                .setControl(opt -> new CyclingControl<>(opt, enumClass, names))
                .setImpact(impact)
                .setBinding((opts, val) -> setter.accept(val), opts -> getter.get())
                .build();
    }

    private static OptionImpl<Object, Integer> glowOption(
            String idPath,
            String optionKey,
            ForgeConfigSpec.DoubleValue configValue,
            DoubleSupplier getter
    ) {
        return intOption(
                idPath,
                "config.zoltraak_cinematic.option." + optionKey,
                0, 40, 1,
                v -> Component.literal((v * 5) + "%"),
                OptionImpact.LOW,
                val -> safeSetDouble(configValue, (val * 5) / 100.0d),
                () -> snapToRange((float) (getter.getAsDouble() * 20.0d), 0, 40, 1)
        );
    }

    private static Component joinDescriptions(Component... parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append("\n");
            }
            out.append(parts[i]);
        }
        return out;
    }

    private static void safeSetBool(ForgeConfigSpec.BooleanValue spec, boolean val) {
        if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
            spec.set(val);
        }
    }

    private static void safeSetInt(ForgeConfigSpec.IntValue spec, int val) {
        if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
            spec.set(val);
        }
    }

    private static void safeSetDouble(ForgeConfigSpec.DoubleValue spec, double val) {
        if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
            spec.set(val);
        }
    }

    private static void safeSetString(ForgeConfigSpec.ConfigValue<String> spec, String val) {
        if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
            spec.set(val);
        }
    }

    private static int snapToRange(float raw, int min, int max, int step) {
        int clamped = Math.max(min, Math.min(max, Math.round(raw)));
        return min + Math.round((float) (clamped - min) / step) * step;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ZoltraakCinematicMod.MODID, path);
    }

    private static OptionIdentifier<Void> pageId(String path) {
        return OptionIdentifier.create(ZoltraakCinematicMod.MODID, path);
    }
}
