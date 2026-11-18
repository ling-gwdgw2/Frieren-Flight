// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.config;

import java.util.Locale;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client settings for visual effects, shaders, camera motion, and audio.
 */
public class ZoltraakCinematicClientConfig {
    public static final ModConfigSpec SPEC;

    // Visuals & Camera
    public static final ModConfigSpec.BooleanValue SCREEN_FLASH;
    public static final ModConfigSpec.DoubleValue FLASH_STRENGTH;
    public static final ModConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ModConfigSpec.DoubleValue SHAKE_STRENGTH;
    public static final ModConfigSpec.BooleanValue FLIGHT_TILT;
    public static final ModConfigSpec.BooleanValue CUTSCENE_CAMERA;
    public static final ModConfigSpec.ConfigValue<String> BARRIER_VIEW;
    public static final ModConfigSpec.DoubleValue BARRIER_OPACITY;
    public static final ModConfigSpec.ConfigValue<String> CIRCLE_VIEW;
    public static final ModConfigSpec.DoubleValue CIRCLE_OPACITY;

    // Shaders & Effects
    public static final ModConfigSpec.ConfigValue<String> HOLE_QUALITY;
    public static final ModConfigSpec.DoubleValue DISK_SIZE;
    public static final ModConfigSpec.BooleanValue LENS_FLARES;
    public static final ModConfigSpec.BooleanValue LIGHT_BENDING;
    public static final ModConfigSpec.BooleanValue COLOR_FRINGE;
    public static final ModConfigSpec.ConfigValue<String> WORLD_GLOW;

    // Audio
    public static final ModConfigSpec.BooleanValue EAR_RINGING;
    public static final ModConfigSpec.DoubleValue EAR_RINGING_VOLUME;
    public static final ModConfigSpec.DoubleValue MOD_SOUND_VOLUME;

    // Spell Glow Brightness (0.0 = 0%, 1.0 = 100%, 2.0 = 200%)
    public static final ModConfigSpec.DoubleValue ZOLTRAAK_GLOW;
    public static final ModConfigSpec.DoubleValue GREAT_ZOLTRAAK_GLOW;
    public static final ModConfigSpec.DoubleValue BARRAGE_GLOW;
    public static final ModConfigSpec.DoubleValue BARRIER_GLOW;
    public static final ModConfigSpec.DoubleValue GARGANTUA_GLOW;
    public static final ModConfigSpec.DoubleValue QUAL_GLOW;
    public static final ModConfigSpec.DoubleValue EXPLOSION_GLOW;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Visual & Camera Settings").push("visuals");

        SCREEN_FLASH = builder
                .comment("Enable brief white flashes on huge blasts (turn off if sensitive to flashes).")
                .define("screenFlash", true);

        FLASH_STRENGTH = builder
                .comment("Screen flash brightness (0.0 = off, 1.0 = full).")
                .defineInRange("flashStrength", 1.0d, 0.0d, 1.0d);

        CAMERA_SHAKE = builder
                .comment("Shake the camera on big explosions and beams.")
                .define("cameraShake", true);

        SHAKE_STRENGTH = builder
                .comment("Camera shake strength (0.0 = off, 1.0 = normal, 3.0 = strong).")
                .defineInRange("shakeStrength", 1.0d, 0.0d, 3.0d);

        FLIGHT_TILT = builder
                .comment("Tilt the camera slightly when turning during Flight Magic.")
                .define("flightTilt", true);

        CUTSCENE_CAMERA = builder
                .comment("Play smooth zoom and orbit camera angles while casting Gargantua.",
                         "Press Jump (Space) in-game to skip the camera angle.")
                .define("cutsceneCamera", true);

        BARRIER_VIEW = builder
                .comment("How your own Defense Barrier looks in first-person view (others still see the full shield).",
                         "Values: CENTER_FADE, MINIMAL, EDGE_ONLY, FULL",
                         "  CENTER_FADE - Keeps the crosshair center clear while showing the edges.",
                         "  MINIMAL     - Faint lattice that brightens when hit.",
                         "  EDGE_ONLY   - Draws only the outer frame (center completely clear).",
                         "  FULL        - Full brightness and pattern in first-person.")
                .define("barrierView", "CENTER_FADE");

        BARRIER_OPACITY = builder
                .comment("First-person barrier opacity (0.0 = visible only when hit, 1.0 = full strength).")
                .defineInRange("barrierOpacity", 0.35d, 0.0d, 1.0d);

        CIRCLE_VIEW = builder
                .comment("How your own Zoltraak magic circle looks in first-person view (others still see the full circle).",
                         "Values: CENTER_FADE, MINIMAL, EDGE_ONLY, FULL",
                         "  CENTER_FADE - Fades center runes and bloom around the crosshair.",
                         "  MINIMAL     - Faint magic circle with minimal bloom.",
                         "  EDGE_ONLY   - Keeps only the outer rune rings (center completely clear).",
                         "  FULL        - Full brightness and bloom in first-person.")
                .define("circleView", "CENTER_FADE");

        CIRCLE_OPACITY = builder
                .comment("First-person Zoltraak magic circle opacity (0.0 = nearly hidden, 1.0 = full strength).")
                .defineInRange("circleOpacity", 0.45d, 0.0d, 1.0d);

        builder.pop();

        builder.comment("Shader & Effect Settings").push("shaders");

        HOLE_QUALITY = builder
                .comment("Gargantua black hole ray quality.",
                         "Values: LOW (32 steps), MEDIUM (72 steps), HIGH (96 steps), ULTRA (128 steps).")
                .define("blackHoleQuality", "MEDIUM");

        DISK_SIZE = builder
                .comment("Outer radius of Gargantua's glowing ring (default: 22.0). Larger values use more GPU.")
                .defineInRange("diskSize", 22.0d, 8.0d, 40.0d);

        LENS_FLARES = builder
                .comment("Add horizontal light streaks across bright beam cores.")
                .define("lensFlares", true);

        LIGHT_BENDING = builder
                .comment("Bend background scenery around the Gargantua black hole.")
                .define("lightBending", true);

        COLOR_FRINGE = builder
                .comment("Add subtle RGB color fringes at screen edges during big blasts.")
                .define("colorFringe", true);

        WORLD_GLOW = builder
                .comment("Controls spell glow lighting on terrain and water.",
                         "Values: SHADERS_ONLY (only when Iris/Oculus is active), ALWAYS, OFF.")
                .define("worldGlow", "SHADERS_ONLY");

        builder.pop();

        builder.comment("Sound Settings").push("audio");

        EAR_RINGING = builder
                .comment("Play a short ringing sound after close-range huge explosions.")
                .define("earRinging", true);

        EAR_RINGING_VOLUME = builder
                .comment("Volume for the post-explosion ear ringing sound (0.0 = mute, 1.0 = full).")
                .defineInRange("earRingingVolume", 0.8d, 0.0d, 1.0d);

        MOD_SOUND_VOLUME = builder
                .comment("Master volume for all custom Zoltraak spells, beams, and black hole sounds.")
                .defineInRange("modSoundVolume", 1.0d, 0.0d, 2.0d);

        builder.pop();

        builder.comment("Per-Spell Glow Brightness (0.0 = off, 1.0 = normal, 2.0 = double)").push("glow");

        ZOLTRAAK_GLOW = builder
                .comment("Glow brightness for standard and corrupted Zoltraak beams.")
                .defineInRange("zoltraak", 1.0d, 0.0d, 2.0d);

        GREAT_ZOLTRAAK_GLOW = builder
                .comment("Glow brightness for Great Zoltraak large beams.")
                .defineInRange("greatZoltraak", 1.0d, 0.0d, 2.0d);

        BARRAGE_GLOW = builder
                .comment("Glow brightness for 24-circle arrays and barrage bolts.")
                .defineInRange("barrage", 1.0d, 0.0d, 2.0d);

        BARRIER_GLOW = builder
                .comment("Glow brightness for Defense Barrier seams and sparks.")
                .defineInRange("barrier", 1.0d, 0.0d, 2.0d);

        GARGANTUA_GLOW = builder
                .comment("Glow brightness for the Gargantua black hole ring and core.")
                .defineInRange("gargantua", 1.0d, 0.0d, 2.0d);

        QUAL_GLOW = builder
                .comment("Glow brightness for Qual boss effects and sealing stone runes.")
                .defineInRange("qual", 1.0d, 0.0d, 2.0d);

        EXPLOSION_GLOW = builder
                .comment("Glow brightness for Crimson Explosion Magic rings, laser, and fireball dome.")
                .defineInRange("explosion", 1.0d, 0.0d, 2.0d);

        builder.pop();

        SPEC = builder.build();
    }

    public static boolean isScreenFlashEnabled() {
        return !SPEC.isLoaded() || SCREEN_FLASH.get();
    }

    public static float getFlashStrength() {
        return SPEC.isLoaded() ? FLASH_STRENGTH.get().floatValue() : 1.0f;
    }

    public static boolean isCameraShakeEnabled() {
        return !SPEC.isLoaded() || CAMERA_SHAKE.get();
    }

    public static float getShakeStrength() {
        return SPEC.isLoaded() ? SHAKE_STRENGTH.get().floatValue() : 1.0f;
    }

    public static boolean isFlightTiltEnabled() {
        return !SPEC.isLoaded() || FLIGHT_TILT.get();
    }

    public static boolean isCutsceneCameraEnabled() {
        return !SPEC.isLoaded() || CUTSCENE_CAMERA.get();
    }

    /** Normalized first-person barrier view: CENTER_FADE, MINIMAL, EDGE_ONLY, or FULL. */
    public static String getBarrierView() {
        return normalizeViewMode(BARRIER_VIEW);
    }

    /** First-person Defense Barrier opacity clamped to [0.0, 1.0]. */
    public static float getBarrierOpacity() {
        return clampedUnitFloat(BARRIER_OPACITY, 0.35F);
    }

    /** Normalized first-person Zoltraak magic circle view: CENTER_FADE, MINIMAL, EDGE_ONLY, or FULL. */
    public static String getCircleView() {
        return normalizeViewMode(CIRCLE_VIEW);
    }

    /** First-person Zoltraak magic circle opacity clamped to [0.0, 1.0]. */
    public static float getCircleOpacity() {
        return clampedUnitFloat(CIRCLE_OPACITY, 0.45F);
    }

    private static String normalizeViewMode(ModConfigSpec.ConfigValue<String> specValue) {
        if (!SPEC.isLoaded()) {
            return "CENTER_FADE";
        }
        String raw = specValue.get();
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

    private static float clampedUnitFloat(ModConfigSpec.DoubleValue specValue, float fallback) {
        if (!SPEC.isLoaded()) {
            return fallback;
        }
        return (float) Math.max(0.0d, Math.min(1.0d, specValue.get()));
    }

    public static String getHoleQuality() {
        if (!SPEC.isLoaded() || HOLE_QUALITY.get() == null) {
            return "MEDIUM";
        }
        return switch (HOLE_QUALITY.get().trim().toUpperCase(Locale.ROOT)) {
            case "LOW" -> "LOW";
            case "HIGH" -> "HIGH";
            case "ULTRA" -> "ULTRA";
            default -> "MEDIUM";
        };
    }

    public static int getHoleSteps() {
        return switch (getHoleQuality()) {
            case "LOW" -> 32;
            case "HIGH" -> 96;
            case "ULTRA" -> 128;
            default -> 72;
        };
    }

    public static float getDiskSize() {
        return SPEC.isLoaded() ? DISK_SIZE.get().floatValue() : 22.0f;
    }

    public static boolean isLensFlaresEnabled() {
        return !SPEC.isLoaded() || LENS_FLARES.get();
    }

    public static boolean isLightBendingEnabled() {
        return !SPEC.isLoaded() || LIGHT_BENDING.get();
    }

    public static boolean isColorFringeEnabled() {
        return !SPEC.isLoaded() || COLOR_FRINGE.get();
    }

    /** Normalized world glow setting: SHADERS_ONLY, ALWAYS, or OFF. */
    public static String getWorldGlow() {
        if (!SPEC.isLoaded() || WORLD_GLOW.get() == null) {
            return "SHADERS_ONLY";
        }
        return switch (WORLD_GLOW.get().trim().toUpperCase(Locale.ROOT)) {
            case "ALWAYS" -> "ALWAYS";
            case "OFF" -> "OFF";
            default -> "SHADERS_ONLY";
        };
    }

    public static boolean isWorldGlowActive(boolean shadersActive) {
        return switch (getWorldGlow()) {
            case "ALWAYS" -> true;
            case "OFF" -> false;
            default -> shadersActive;
        };
    }

    public static boolean isEarRingingEnabled() {
        return !SPEC.isLoaded() || EAR_RINGING.get();
    }

    public static float getEarRingingVolume() {
        return SPEC.isLoaded() ? EAR_RINGING_VOLUME.get().floatValue() : 0.8f;
    }

    public static float getModSoundVolume() {
        return SPEC.isLoaded() ? MOD_SOUND_VOLUME.get().floatValue() : 1.0f;
    }

    private static float safeGlow(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? Math.max(0.0f, Math.min(2.0f, value.get().floatValue())) : 1.0f;
    }

    public static float getZoltraakGlow() {
        return safeGlow(ZOLTRAAK_GLOW);
    }

    public static float getGreatZoltraakGlow() {
        return safeGlow(GREAT_ZOLTRAAK_GLOW);
    }

    public static float getBarrageGlow() {
        return safeGlow(BARRAGE_GLOW);
    }

    public static float getBarrierGlow() {
        return safeGlow(BARRIER_GLOW);
    }

    public static float getGargantuaGlow() {
        return safeGlow(GARGANTUA_GLOW);
    }

    public static float getQualGlow() {
        return safeGlow(QUAL_GLOW);
    }

    public static float getExplosionGlow() {
        return safeGlow(EXPLOSION_GLOW);
    }
}
