// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.gui;

import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * In-game visual and audio settings screen using vanilla widgets.
 */
public class ZoltraakConfigScreen extends Screen {

    private final Screen parent;
    private int currentTab = 0; // 0: Barrier, 1: Camera, 2: Shaders, 3: Glow, 4: Explosion, 5: Audio

    public ZoltraakConfigScreen(Screen parent) {
        super(Component.translatable("config.zoltraak_cinematic.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.rebuildAllWidgets();
    }

    private void rebuildAllWidgets() {
        this.clearWidgets();
        int centerX = this.width / 2;

        // --- Category Tabs ---
        int tabY = 36;
        String[] tabKeys = {"barrier", "camera", "shaders", "bloom", "explosion", "audio"};
        String[] tabIcons = {"🛡 ", "🎥 ", "✨ ", "✦ ", "💥 ", "🔊 "};
        int tabCount = tabKeys.length;
        int tabSpacing = Math.min(64, Math.max(48, (this.width - 20) / tabCount));
        int tabWidth = tabSpacing - 3;
        int tabStartX = centerX - (tabSpacing * tabCount - (tabSpacing - tabWidth)) / 2;

        for (int i = 0; i < tabCount; i++) {
            final int tabIndex = i;
            boolean active = (currentTab == tabIndex);
            MutableComponent label = Component.literal(tabIcons[i])
                    .append(Component.translatable("config.zoltraak_cinematic.tab." + tabKeys[i]));
            if (active) {
                label.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            } else {
                label.withStyle(ChatFormatting.GRAY);
            }
            Button tabBtn = Button.builder(label, btn -> {
                this.currentTab = tabIndex;
                this.rebuildAllWidgets();
            }).bounds(tabStartX + i * tabSpacing, tabY, tabWidth, 20).build();
            this.addRenderableWidget(tabBtn);
        }

        // --- Content Area by Tab ---
        int contentY = 66;
        int col1X = centerX - 180;
        int col2X = centerX + 6;
        int btnW = 174;
        int btnH = 20;

        switch (this.currentTab) {
            case 0 -> buildBarrierTab(col1X, col2X, contentY, btnW, btnH);
            case 1 -> buildCameraTab(col1X, col2X, contentY, btnW, btnH);
            case 2 -> buildShaderTab(col1X, col2X, contentY, btnW, btnH);
            case 3 -> buildBloomTab(col1X, col2X, contentY, btnW, btnH);
            case 4 -> buildExplosionTab(col1X, col2X, contentY, btnW, btnH);
            case 5 -> buildAudioTab(col1X, col2X, contentY, btnW, btnH);
        }

        // --- Footer Controls ---
        int footerY = getFooterY();

        Button resetBtn = Button.builder(Component.translatable("config.zoltraak_cinematic.reset"), btn -> {
            this.resetDefaults();
            this.rebuildAllWidgets();
        }).bounds(col1X, footerY, btnW, btnH)
                .tooltip(Tooltip.create(Component.translatable("config.zoltraak_cinematic.reset.tooltip")))
                .build();
        this.addRenderableWidget(resetBtn);

        Button doneBtn = Button.builder(Component.translatable("config.zoltraak_cinematic.done"), btn -> this.onClose())
                .bounds(col2X, footerY, btnW, btnH)
                .tooltip(Tooltip.create(Component.translatable("config.zoltraak_cinematic.done.tooltip")))
                .build();
        this.addRenderableWidget(doneBtn);
    }

    private int getFooterY() {
        return Math.max(202, this.height - 26);
    }

    // Tab 0: Barrier
    private void buildBarrierTab(int c1, int c2, int y, int w, int h) {
        addFirstPersonViewRow(
                c1, c2, y, w, h,
                "barrier_view", "option.barrier_view", "option.barrier_opacity",
                ZoltraakCinematicClientConfig.getBarrierView(),
                ZoltraakCinematicClientConfig.getBarrierOpacity(),
                ZoltraakCinematicClientConfig.BARRIER_VIEW,
                ZoltraakCinematicClientConfig.BARRIER_OPACITY);
    }

    private void addFirstPersonViewRow(int c1, int c2, int y, int w, int h,
                                       String viewPrefix, String optionViewKey, String optionOpacityKey,
                                       String currentView, float currentOpacity,
                                       ModConfigSpec.ConfigValue<String> viewSpec,
                                       ModConfigSpec.DoubleValue opacitySpec) {
        String basePrefix = "config.zoltraak_cinematic.";
        CycleButton<String> viewBtn = CycleButton.<String>builder(val ->
                        Component.translatable(basePrefix + viewPrefix + "." + val.toLowerCase(Locale.ROOT)))
                .withValues(List.of("CENTER_FADE", "MINIMAL", "EDGE_ONLY", "FULL"))
                .withInitialValue(currentView)
                .withTooltip(val -> Tooltip.create(
                        Component.translatable(basePrefix + viewPrefix + "." + val.toLowerCase(Locale.ROOT) + ".desc")))
                .create(c1, y, w, h, Component.translatable(basePrefix + optionViewKey),
                        (btn, val) -> safeSet(viewSpec, val));
        this.addRenderableWidget(viewBtn);

        ConfigSlider opacitySlider = new ConfigSlider(c2, y, w, h, 0.0, 1.0, currentOpacity,
                val -> Component.translatable(basePrefix + optionOpacityKey + ".value", Math.round(val * 100.0)),
                val -> safeSet(opacitySpec, roundToStep(val, 100.0)));
        opacitySlider.setTooltip(Tooltip.create(Component.translatable(basePrefix + optionOpacityKey + ".tooltip")));
        this.addRenderableWidget(opacitySlider);
    }

    // Tab 1: Camera & Motion
    private void buildCameraTab(int c1, int c2, int y, int w, int h) {
        boolean shake = ZoltraakCinematicClientConfig.isCameraShakeEnabled();
        CycleButton<Boolean> shakeBtn = CycleButton.onOffBuilder(shake)
                .withTooltip(val -> Tooltip.create(Component.translatable("config.zoltraak_cinematic.option.camera_shake.tooltip")))
                .create(c1, y, w, h, Component.translatable("config.zoltraak_cinematic.option.camera_shake"),
                        (btn, val) -> safeSet(ZoltraakCinematicClientConfig.CAMERA_SHAKE, val));
        this.addRenderableWidget(shakeBtn);

        float shakeStrength = ZoltraakCinematicClientConfig.getShakeStrength();
        ConfigSlider shakeSlider = new ConfigSlider(c2, y, w, h, 0.0, 3.0, shakeStrength,
                val -> Component.translatable("config.zoltraak_cinematic.option.shake_strength.value",
                        String.format(Locale.ROOT, "%.1f", val)),
                val -> safeSet(ZoltraakCinematicClientConfig.SHAKE_STRENGTH, roundToStep(val, 10.0)));
        shakeSlider.setTooltip(Tooltip.create(Component.translatable("config.zoltraak_cinematic.option.shake_strength.tooltip")));
        this.addRenderableWidget(shakeSlider);

        boolean flash = ZoltraakCinematicClientConfig.isScreenFlashEnabled();
        CycleButton<Boolean> flashBtn = CycleButton.onOffBuilder(flash)
                .withTooltip(val -> Tooltip.create(Component.translatable("config.zoltraak_cinematic.option.screen_flash.tooltip")))
                .create(c1, y + 26, w, h, Component.translatable("config.zoltraak_cinematic.option.screen_flash"),
                        (btn, val) -> safeSet(ZoltraakCinematicClientConfig.SCREEN_FLASH, val));
        this.addRenderableWidget(flashBtn);

        float flashStrength = ZoltraakCinematicClientConfig.getFlashStrength();
        ConfigSlider flashSlider = new ConfigSlider(c2, y + 26, w, h, 0.0, 1.0, flashStrength,
                val -> Component.translatable("config.zoltraak_cinematic.option.flash_strength.value", Math.round(val * 100.0)),
                val -> safeSet(ZoltraakCinematicClientConfig.FLASH_STRENGTH, roundToStep(val, 100.0)));
        flashSlider.setTooltip(Tooltip.create(Component.translatable("config.zoltraak_cinematic.option.flash_strength.tooltip")));
        this.addRenderableWidget(flashSlider);

        boolean cineCam = ZoltraakCinematicClientConfig.isCutsceneCameraEnabled();
        CycleButton<Boolean> cineBtn = CycleButton.onOffBuilder(cineCam)
                .withTooltip(val -> Tooltip.create(Component.translatable("config.zoltraak_cinematic.option.cinematic_camera.tooltip")))
                .create(c1, y + 52, w, h, Component.translatable("config.zoltraak_cinematic.option.cinematic_camera"),
                        (btn, val) -> safeSet(ZoltraakCinematicClientConfig.CUTSCENE_CAMERA, val));
        this.addRenderableWidget(cineBtn);

        boolean tilt = ZoltraakCinematicClientConfig.isFlightTiltEnabled();
        CycleButton<Boolean> tiltBtn = CycleButton.onOffBuilder(tilt)
                .withTooltip(val -> Tooltip.create(Component.translatable("config.zoltraak_cinematic.option.flight_tilt.tooltip")))
                .create(c2, y + 52, w, h, Component.translatable("config.zoltraak_cinematic.option.flight_tilt"),
                        (btn, val) -> safeSet(ZoltraakCinematicClientConfig.FLIGHT_TILT, val));
        this.addRenderableWidget(tiltBtn);

        addFirstPersonViewRow(
                c1, c2, y + 78, w, h,
                "circle_view", "option.circle_view", "option.circle_opacity",
                ZoltraakCinematicClientConfig.getCircleView(),
                ZoltraakCinematicClientConfig.getCircleOpacity(),
                ZoltraakCinematicClientConfig.CIRCLE_VIEW,
                ZoltraakCinematicClientConfig.CIRCLE_OPACITY);
    }

    // Tab 2: Shaders & FX
    private void buildShaderTab(int c1, int c2, int y, int w, int h) {
        String quality = ZoltraakCinematicClientConfig.getHoleQuality();
        CycleButton<String> qualityBtn = CycleButton.<String>builder(val ->
                        Component.translatable("config.zoltraak_cinematic.hole_quality." + val.toLowerCase(Locale.ROOT)))
                .withValues(List.of("LOW", "MEDIUM", "HIGH", "ULTRA"))
                .withInitialValue(quality)
                .withTooltip(val -> Tooltip.create(
                        Component.translatable("config.zoltraak_cinematic.hole_quality." + val.toLowerCase(Locale.ROOT) + ".desc")))
                .create(c1, y, w, h, Component.translatable("config.zoltraak_cinematic.option.hole_quality"),
                        (btn, val) -> safeSet(ZoltraakCinematicClientConfig.HOLE_QUALITY, val));
        this.addRenderableWidget(qualityBtn);

        double diskSize = ZoltraakCinematicClientConfig.getDiskSize();
        ConfigSlider diskSlider = new ConfigSlider(c2, y, w, h, 8.0, 40.0, diskSize,
                val -> Component.translatable("config.zoltraak_cinematic.option.disk_size.value",
                        String.format(Locale.ROOT, "%.1f", val)),
                val -> safeSet(ZoltraakCinematicClientConfig.DISK_SIZE, roundToStep(val, 10.0)));
        diskSlider.setTooltip(Tooltip.create(Component.translatable("config.zoltraak_cinematic.option.disk_size.tooltip")));
        this.addRenderableWidget(diskSlider);

        boolean flares = ZoltraakCinematicClientConfig.isLensFlaresEnabled();
        CycleButton<Boolean> flareBtn = CycleButton.onOffBuilder(flares)
                .withTooltip(val -> Tooltip.create(Component.translatable("config.zoltraak_cinematic.option.lens_flares.tooltip")))
                .create(c1, y + 26, w, h, Component.translatable("config.zoltraak_cinematic.option.lens_flares"),
                        (btn, val) -> safeSet(ZoltraakCinematicClientConfig.LENS_FLARES, val));
        this.addRenderableWidget(flareBtn);

        boolean bending = ZoltraakCinematicClientConfig.isLightBendingEnabled();
        CycleButton<Boolean> bendBtn = CycleButton.onOffBuilder(bending)
                .withTooltip(val -> Tooltip.create(Component.translatable("config.zoltraak_cinematic.option.light_bending.tooltip")))
                .create(c2, y + 26, w, h, Component.translatable("config.zoltraak_cinematic.option.light_bending"),
                        (btn, val) -> safeSet(ZoltraakCinematicClientConfig.LIGHT_BENDING, val));
        this.addRenderableWidget(bendBtn);

        boolean fringe = ZoltraakCinematicClientConfig.isColorFringeEnabled();
        CycleButton<Boolean> fringeBtn = CycleButton.onOffBuilder(fringe)
                .withTooltip(val -> Tooltip.create(Component.translatable("config.zoltraak_cinematic.option.color_fringe.tooltip")))
                .create(c1, y + 52, w, h, Component.translatable("config.zoltraak_cinematic.option.color_fringe"),
                        (btn, val) -> safeSet(ZoltraakCinematicClientConfig.COLOR_FRINGE, val));
        this.addRenderableWidget(fringeBtn);

        String worldGlow = ZoltraakCinematicClientConfig.getWorldGlow();
        CycleButton<String> glowBtn = CycleButton.<String>builder(val ->
                        Component.translatable("config.zoltraak_cinematic.world_glow." + val.toLowerCase(Locale.ROOT)))
                .withValues(List.of("SHADERS_ONLY", "ALWAYS", "OFF"))
                .withInitialValue(worldGlow)
                .withTooltip(val -> Tooltip.create(
                        Component.translatable("config.zoltraak_cinematic.world_glow." + val.toLowerCase(Locale.ROOT) + ".desc")))
                .create(c2, y + 52, w, h, Component.translatable("config.zoltraak_cinematic.option.world_glow"),
                        (btn, val) -> safeSet(ZoltraakCinematicClientConfig.WORLD_GLOW, val));
        this.addRenderableWidget(glowBtn);
    }

    // Tab 3: Spell Glow
    private void buildBloomTab(int c1, int c2, int y, int w, int h) {
        addGlowSlider(c1, y, w, h, "bloom_zoltraak",
                ZoltraakCinematicClientConfig.ZOLTRAAK_GLOW,
                ZoltraakCinematicClientConfig.getZoltraakGlow());
        addGlowSlider(c2, y, w, h, "bloom_great",
                ZoltraakCinematicClientConfig.GREAT_ZOLTRAAK_GLOW,
                ZoltraakCinematicClientConfig.getGreatZoltraakGlow());

        addGlowSlider(c1, y + 26, w, h, "bloom_barrage",
                ZoltraakCinematicClientConfig.BARRAGE_GLOW,
                ZoltraakCinematicClientConfig.getBarrageGlow());
        addGlowSlider(c2, y + 26, w, h, "bloom_barrier",
                ZoltraakCinematicClientConfig.BARRIER_GLOW,
                ZoltraakCinematicClientConfig.getBarrierGlow());

        addGlowSlider(c1, y + 52, w, h, "bloom_gargantua",
                ZoltraakCinematicClientConfig.GARGANTUA_GLOW,
                ZoltraakCinematicClientConfig.getGargantuaGlow());
        addGlowSlider(c2, y + 52, w, h, "bloom_qual",
                ZoltraakCinematicClientConfig.QUAL_GLOW,
                ZoltraakCinematicClientConfig.getQualGlow());
        addGlowSlider(c1, y + 78, w, h, "bloom_explosion",
                ZoltraakCinematicClientConfig.EXPLOSION_GLOW,
                ZoltraakCinematicClientConfig.getExplosionGlow());
    }

    private void addGlowSlider(int x, int y, int w, int h, String optionKey,
                               ModConfigSpec.DoubleValue configValue, float current) {
        String keyPrefix = "config.zoltraak_cinematic.option." + optionKey;
        ConfigSlider slider = new ConfigSlider(x, y, w, h, 0.0, 2.0, current,
                val -> Component.translatable(keyPrefix + ".value", Math.round(val * 100.0)),
                val -> safeSet(configValue, roundToStep(val, 100.0)));
        slider.setTooltip(Tooltip.create(Component.translatable(keyPrefix + ".tooltip")));
        this.addRenderableWidget(slider);
    }

    // Tab 4: Explosion Charge Particles & Flares
    private void buildExplosionTab(int c1, int c2, int y, int w, int h) {
        int rowY = y - 2;
        int rowStep = 22;
        int rowH = Math.min(h, 19);

        addIntSlider(c1, rowY, w, rowH, "explosion_gold_sparks",
                ZoltraakCinematicClientConfig.EXPLOSION_GOLD_SPARKS, 20, 320, 10,
                ZoltraakCinematicClientConfig.getExplosionGoldSparks(),
                v -> Component.translatable("config.zoltraak_cinematic.option.explosion_gold_sparks.value", v));
        addIntSlider(c2, rowY, w, rowH, "explosion_stars",
                ZoltraakCinematicClientConfig.EXPLOSION_FOUR_POINT_STARS, 8, 160, 4,
                ZoltraakCinematicClientConfig.getExplosionFourPointStars(),
                v -> Component.translatable("config.zoltraak_cinematic.option.explosion_stars.value", v));

        addIntSlider(c1, rowY + rowStep, w, rowH, "explosion_parabola",
                ZoltraakCinematicClientConfig.EXPLOSION_PARABOLA_STREAMS, 8, 160, 4,
                ZoltraakCinematicClientConfig.getExplosionParabolaStreams(),
                v -> Component.translatable("config.zoltraak_cinematic.option.explosion_parabola.value", v));
        addIntSlider(c2, rowY + rowStep, w, rowH, "explosion_cross_rays",
                ZoltraakCinematicClientConfig.EXPLOSION_CROSS_RAYS, 0, 12, 1,
                ZoltraakCinematicClientConfig.getExplosionCrossRays(),
                v -> v <= 0
                        ? Component.translatable("config.zoltraak_cinematic.option.explosion_cross_rays.off")
                        : Component.translatable("config.zoltraak_cinematic.option.explosion_cross_rays.value", v * 2));

        addIntSlider(c1, rowY + rowStep * 2, w, rowH, "explosion_suction",
                ZoltraakCinematicClientConfig.EXPLOSION_SUCTION_LINES, 0, 60, 2,
                ZoltraakCinematicClientConfig.getExplosionSuctionLines(),
                v -> Component.translatable("config.zoltraak_cinematic.option.explosion_suction.value", v));
        addPercentSlider(c2, rowY + rowStep * 2, w, rowH, "explosion_sky_flare",
                ZoltraakCinematicClientConfig.EXPLOSION_SKY_FLARE_STRENGTH, 0.0, 2.5,
                ZoltraakCinematicClientConfig.getExplosionSkyFlareStrength());

        addPercentSlider(c1, rowY + rowStep * 3, w, rowH, "explosion_particle_scale",
                ZoltraakCinematicClientConfig.EXPLOSION_PARTICLE_SCALE, 0.5, 2.0,
                ZoltraakCinematicClientConfig.getExplosionParticleScale());
        addGlowSlider(c2, rowY + rowStep * 3, w, rowH, "bloom_explosion",
                ZoltraakCinematicClientConfig.EXPLOSION_GLOW,
                ZoltraakCinematicClientConfig.getExplosionGlow());

        addPercentSlider(c1, rowY + rowStep * 4, w, rowH, "explosion_sky_height",
                ZoltraakCinematicClientConfig.EXPLOSION_SKY_HEIGHT, 0.5, 2.5,
                ZoltraakCinematicClientConfig.getExplosionSkyHeight());
        addPercentSlider(c2, rowY + rowStep * 4, w, rowH, "explosion_sky_ring_scale",
                ZoltraakCinematicClientConfig.EXPLOSION_SKY_RING_SCALE, 0.4, 2.5,
                ZoltraakCinematicClientConfig.getExplosionSkyRingScale());

        addPercentSlider(c1, rowY + rowStep * 5, w, rowH, "explosion_dome_scale",
                ZoltraakCinematicClientConfig.EXPLOSION_DOME_SCALE, 0.6, 2.0,
                ZoltraakCinematicClientConfig.getExplosionDomeScale());
    }

    private void addIntSlider(int x, int y, int w, int h, String optionKey,
                              ModConfigSpec.IntValue configValue, int min, int max, int step,
                              int current, Function<Integer, Component> formatter) {
        String keyPrefix = "config.zoltraak_cinematic.option." + optionKey;
        ConfigSlider slider = new ConfigSlider(x, y, w, h, min, max, current,
                val -> formatter.apply(snapInt(val, min, max, step)),
                val -> safeSet(configValue, snapInt(val, min, max, step)));
        slider.setTooltip(Tooltip.create(Component.translatable(keyPrefix + ".tooltip")));
        this.addRenderableWidget(slider);
    }

    private void addPercentSlider(int x, int y, int w, int h, String optionKey,
                                  ModConfigSpec.DoubleValue configValue, double min, double max, float current) {
        String keyPrefix = "config.zoltraak_cinematic.option." + optionKey;
        ConfigSlider slider = new ConfigSlider(x, y, w, h, min, max, current,
                val -> Component.translatable(keyPrefix + ".value", Math.round(roundToStep(val, 20.0) * 100.0)),
                val -> safeSet(configValue, Math.max(min, Math.min(max, roundToStep(val, 20.0)))));
        slider.setTooltip(Tooltip.create(Component.translatable(keyPrefix + ".tooltip")));
        this.addRenderableWidget(slider);
    }

    private static int snapInt(double raw, int min, int max, int step) {
        int snapped = (int) (Math.round(raw / step) * (long) step);
        return Math.max(min, Math.min(max, snapped));
    }

    // Tab 5: Audio
    private void buildAudioTab(int c1, int c2, int y, int w, int h) {
        boolean ringing = ZoltraakCinematicClientConfig.isEarRingingEnabled();
        CycleButton<Boolean> ringBtn = CycleButton.onOffBuilder(ringing)
                .withTooltip(val -> Tooltip.create(Component.translatable("config.zoltraak_cinematic.option.ear_ringing.tooltip")))
                .create(c1, y, w, h, Component.translatable("config.zoltraak_cinematic.option.ear_ringing"),
                        (btn, val) -> safeSet(ZoltraakCinematicClientConfig.EAR_RINGING, val));
        this.addRenderableWidget(ringBtn);

        float ringVol = ZoltraakCinematicClientConfig.getEarRingingVolume();
        ConfigSlider ringSlider = new ConfigSlider(c2, y, w, h, 0.0, 1.0, ringVol,
                val -> Component.translatable("config.zoltraak_cinematic.option.ear_ringing_volume.value", Math.round(val * 100.0)),
                val -> safeSet(ZoltraakCinematicClientConfig.EAR_RINGING_VOLUME, roundToStep(val, 100.0)));
        ringSlider.setTooltip(Tooltip.create(Component.translatable("config.zoltraak_cinematic.option.ear_ringing_volume.tooltip")));
        this.addRenderableWidget(ringSlider);

        float modVol = ZoltraakCinematicClientConfig.getModSoundVolume();
        ConfigSlider modSlider = new ConfigSlider(c1, y + 26, w, h, 0.0, 2.0, modVol,
                val -> Component.translatable("config.zoltraak_cinematic.option.mod_audio_volume.value", Math.round(val * 100.0)),
                val -> safeSet(ZoltraakCinematicClientConfig.MOD_SOUND_VOLUME, roundToStep(val, 100.0)));
        modSlider.setTooltip(Tooltip.create(Component.translatable("config.zoltraak_cinematic.option.mod_audio_volume.tooltip")));
        this.addRenderableWidget(modSlider);
    }

    private void resetDefaults() {
        if (!ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
            return;
        }
        ZoltraakCinematicClientConfig.BARRIER_VIEW.set("CENTER_FADE");
        ZoltraakCinematicClientConfig.BARRIER_OPACITY.set(0.35d);
        ZoltraakCinematicClientConfig.CIRCLE_VIEW.set("CENTER_FADE");
        ZoltraakCinematicClientConfig.CIRCLE_OPACITY.set(0.45d);
        ZoltraakCinematicClientConfig.FLIGHT_TILT.set(true);

        ZoltraakCinematicClientConfig.CAMERA_SHAKE.set(true);
        ZoltraakCinematicClientConfig.SHAKE_STRENGTH.set(1.0d);
        ZoltraakCinematicClientConfig.SCREEN_FLASH.set(true);
        ZoltraakCinematicClientConfig.FLASH_STRENGTH.set(1.0d);
        ZoltraakCinematicClientConfig.CUTSCENE_CAMERA.set(true);

        ZoltraakCinematicClientConfig.HOLE_QUALITY.set("MEDIUM");
        ZoltraakCinematicClientConfig.DISK_SIZE.set(22.0d);
        ZoltraakCinematicClientConfig.LENS_FLARES.set(true);
        ZoltraakCinematicClientConfig.LIGHT_BENDING.set(true);
        ZoltraakCinematicClientConfig.COLOR_FRINGE.set(true);
        ZoltraakCinematicClientConfig.WORLD_GLOW.set("SHADERS_ONLY");

        ZoltraakCinematicClientConfig.EAR_RINGING.set(true);
        ZoltraakCinematicClientConfig.EAR_RINGING_VOLUME.set(0.8d);
        ZoltraakCinematicClientConfig.MOD_SOUND_VOLUME.set(1.0d);
        ZoltraakCinematicClientConfig.ZOLTRAAK_GLOW.set(1.0d);
        ZoltraakCinematicClientConfig.GREAT_ZOLTRAAK_GLOW.set(1.0d);
        ZoltraakCinematicClientConfig.BARRAGE_GLOW.set(1.0d);
        ZoltraakCinematicClientConfig.BARRIER_GLOW.set(1.0d);
        ZoltraakCinematicClientConfig.GARGANTUA_GLOW.set(1.0d);
        ZoltraakCinematicClientConfig.QUAL_GLOW.set(1.0d);
        ZoltraakCinematicClientConfig.EXPLOSION_GLOW.set(1.0d);
        ZoltraakCinematicClientConfig.EXPLOSION_GOLD_SPARKS.set(160);
        ZoltraakCinematicClientConfig.EXPLOSION_FOUR_POINT_STARS.set(64);
        ZoltraakCinematicClientConfig.EXPLOSION_PARABOLA_STREAMS.set(64);
        ZoltraakCinematicClientConfig.EXPLOSION_CROSS_RAYS.set(6);
        ZoltraakCinematicClientConfig.EXPLOSION_SUCTION_LINES.set(28);
        ZoltraakCinematicClientConfig.EXPLOSION_SKY_FLARE_STRENGTH.set(1.35d);
        ZoltraakCinematicClientConfig.EXPLOSION_PARTICLE_SCALE.set(1.15d);
        ZoltraakCinematicClientConfig.EXPLOSION_SKY_HEIGHT.set(1.0d);
        ZoltraakCinematicClientConfig.EXPLOSION_SKY_RING_SCALE.set(1.0d);
        ZoltraakCinematicClientConfig.EXPLOSION_DOME_SCALE.set(1.0d);
        saveConfig();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Override vanilla 1.21.1 blur/panorama background so custom backdrop and headers stay visible
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xF2060912, 0xFC020408);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        Component titleText = Component.translatable("config.zoltraak_cinematic.title")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        Component subtitleText = Component.translatable("config.zoltraak_cinematic.subtitle")
                .withStyle(ChatFormatting.GRAY);
        guiGraphics.drawCenteredString(this.font, titleText, centerX, 10, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, subtitleText, centerX, 22, 0xAAAAAA);

        guiGraphics.fill(centerX - 185, 59, centerX + 185, 60, 0x4433C0FF);
        int footerLineY = getFooterY() - 6;
        guiGraphics.fill(centerX - 185, footerLineY, centerX + 185, footerLineY + 1, 0x4433C0FF);
    }

    @Override
    public void onClose() {
        saveConfig();
        if (this.parent != null
                && "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen".equals(this.parent.getClass().getName())) {
            refreshSodiumOptions();
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private static <T> void safeSet(ModConfigSpec.ConfigValue<T> specValue, T value) {
        if (ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
            specValue.set(value);
        }
    }

    private static double roundToStep(double value, double scale) {
        return Math.round(value * scale) / scale;
    }

    private static void saveConfig() {
        if (!ZoltraakCinematicClientConfig.SPEC.isLoaded()) {
            return;
        }
        try {
            ZoltraakCinematicClientConfig.SPEC.save();
        } catch (Exception ignored) {
        }
    }

    private static void refreshSodiumOptions() {
        try {
            Class<?> configManagerClass = Class.forName("net.caffeinemc.mods.sodium.client.config.ConfigManager");
            Object config = configManagerClass.getField("CONFIG").get(null);
            if (config != null) {
                config.getClass().getMethod("resetAllOptionsFromBindings").invoke(config);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Generic range slider bound to a client config value. */
    public static class ConfigSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final Function<Double, Component> messageProvider;
        private final Consumer<Double> onChange;

        public ConfigSlider(int x, int y, int width, int height, double min, double max, double current,
                            Function<Double, Component> messageProvider, Consumer<Double> onChange) {
            super(x, y, width, height, Component.empty(),
                    (Math.max(min, Math.min(max, current)) - min) / (max - min));
            this.min = min;
            this.max = max;
            this.messageProvider = messageProvider;
            this.onChange = onChange;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            double actual = this.min + this.value * (this.max - this.min);
            this.setMessage(this.messageProvider.apply(actual));
        }

        @Override
        protected void applyValue() {
            double actual = this.min + this.value * (this.max - this.min);
            this.onChange.accept(actual);
        }
    }
}
