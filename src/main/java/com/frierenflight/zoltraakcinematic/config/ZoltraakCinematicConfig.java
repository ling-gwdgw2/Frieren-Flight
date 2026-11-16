// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common gameplay settings for spells, flight, barriers, bosses, and items.
 */
public class ZoltraakCinematicConfig {
    public static final ModConfigSpec SPEC;

    // --- Gargantua Black Hole ---
    public static final ModConfigSpec.DoubleValue PULL_RADIUS;
    public static final ModConfigSpec.DoubleValue PULL_STRENGTH;
    public static final ModConfigSpec.DoubleValue TIDAL_DAMAGE;
    public static final ModConfigSpec.DoubleValue BLAST_DAMAGE;
    public static final ModConfigSpec.DoubleValue BLAST_RADIUS;
    public static final ModConfigSpec.BooleanValue RECONSTRUCT_BLOCKS;
    public static final ModConfigSpec.BooleanValue TEAR_BLOCKS;
    public static final ModConfigSpec.IntValue MAX_DEBRIS_BLOCKS;
    public static final ModConfigSpec.BooleanValue RESPECT_MOB_GRIEFING;
    public static final ModConfigSpec.BooleanValue SET_NIGHT_TIME;
    public static final ModConfigSpec.IntValue NIGHT_TIME_TICKS;

    // --- Flight Magic ---
    public static final ModConfigSpec.DoubleValue BASE_MANA_PER_SEC;
    public static final ModConfigSpec.DoubleValue MANA_SAVE_PER_LEVEL;
    public static final ModConfigSpec.BooleanValue SCALE_COST_WITH_MOTION;
    public static final ModConfigSpec.DoubleValue SPEED_COST_POWER;
    public static final ModConfigSpec.DoubleValue MAX_SPEED_SCALE;
    public static final ModConfigSpec.DoubleValue HEIGHT_COST_POWER;
    public static final ModConfigSpec.DoubleValue MAX_HEIGHT_SCALE;
    public static final ModConfigSpec.IntValue EMPTY_MANA_SAFE_TICKS;
    public static final ModConfigSpec.BooleanValue AUTO_FLY_IN_AIR;
    public static final ModConfigSpec.BooleanValue SHOW_FLIGHT_HUD;

    // --- Beams ---
    public static final ModConfigSpec.DoubleValue BEAM_RANGE;
    public static final ModConfigSpec.DoubleValue GREAT_BEAM_RANGE;
    public static final ModConfigSpec.DoubleValue BEAM_DAMAGE_SCALE;
    public static final ModConfigSpec.BooleanValue BREAK_SHIELD_ON_HIT;
    public static final ModConfigSpec.DoubleValue BEAM_KNOCKBACK;
    public static final ModConfigSpec.IntValue BLACK_WITHER_TICKS;
    public static final ModConfigSpec.BooleanValue PROTECT_ALLIES;
    public static final ModConfigSpec.BooleanValue BEAM_BREAK_BLOCKS;
    public static final ModConfigSpec.DoubleValue BEAM_BREAK_RADIUS;

    // --- Barrage ---
    public static final ModConfigSpec.DoubleValue BARRAGE_HOMING;
    public static final ModConfigSpec.DoubleValue BARRAGE_SPEED;
    public static final ModConfigSpec.IntValue BARRAGE_LIFE_TICKS;
    public static final ModConfigSpec.IntValue BARRAGE_FIRE_INTERVAL;
    public static final ModConfigSpec.DoubleValue BARRAGE_DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue BARRAGE_SPLASH_RADIUS;

    // --- Defense Barrier ---
    public static final ModConfigSpec.DoubleValue BARRIER_HP_SCALE;
    public static final ModConfigSpec.IntValue BARRIER_DURATION_TICKS;
    public static final ModConfigSpec.BooleanValue BARRIER_BLOCK_ARROWS;
    public static final ModConfigSpec.BooleanValue BARRIER_BLOCK_MAGIC;

    // --- Qual Boss ---
    public static final ModConfigSpec.DoubleValue QUAL_BASE_HEALTH;
    public static final ModConfigSpec.DoubleValue QUAL_HEALTH_PER_PLAYER;
    public static final ModConfigSpec.DoubleValue QUAL_ARMOR;
    public static final ModConfigSpec.DoubleValue QUAL_ARMOR_TOUGHNESS;
    public static final ModConfigSpec.IntValue QUAL_REST_MIN_TICKS;
    public static final ModConfigSpec.IntValue QUAL_REST_MAX_TICKS;
    public static final ModConfigSpec.DoubleValue QUAL_RESET_DISTANCE;

    // --- Items & Relics ---
    public static final ModConfigSpec.DoubleValue STAFF_CAST_SPEED_BONUS;
    public static final ModConfigSpec.DoubleValue STAFF_MANA_REGEN_BONUS;
    public static final ModConfigSpec.DoubleValue CHRONOS_WATCH_RADIUS;
    public static final ModConfigSpec.IntValue CHRONOS_WATCH_DURATION_TICKS;
    public static final ModConfigSpec.IntValue CHRONOS_WATCH_COOLDOWN_SEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // 1. Gargantua Black Hole
        builder.comment("Gargantua Black Hole Settings").push("gargantua");
        PULL_RADIUS = builder
                .comment("Gravity pull radius in blocks.")
                .defineInRange("pullRadius", 100.0d, 10.0d, 300.0d);
        PULL_STRENGTH = builder
                .comment("Gravity pull acceleration applied to nearby targets.")
                .defineInRange("pullStrength", 0.12d, 0.01d, 2.0d);
        TIDAL_DAMAGE = builder
                .comment("Damage per 0.4s near the center (fraction of target Max HP).")
                .defineInRange("tidalDamage", 0.08d, 0.01d, 1.0d);
        BLAST_DAMAGE = builder
                .comment("Final explosion base damage at the end of the spell.")
                .defineInRange("blastDamage", 800.0d, 10.0d, 10000.0d);
        BLAST_RADIUS = builder
                .comment("Final explosion radius in blocks.")
                .defineInRange("blastRadius", 32.0d, 5.0d, 200.0d);
        RECONSTRUCT_BLOCKS = builder
                .comment("Restore torn blocks after the spell ends (disabled by default for server performance).")
                .define("reconstructBlocks", false);
        TEAR_BLOCKS = builder
                .comment("Pull surface blocks off the ground into swirling debris.")
                .define("tearBlocks", true);
        MAX_DEBRIS_BLOCKS = builder
                .comment("Max surface blocks torn into debris per cast to protect server TPS.")
                .defineInRange("maxDebrisBlocks", 180, 0, 1000);
        RESPECT_MOB_GRIEFING = builder
                .comment("Respect the vanilla mobGriefing gamerule.")
                .define("respectMobGriefing", true);
        SET_NIGHT_TIME = builder
                .comment("Switch world time to night when casting Gargantua.")
                .define("setNightTime", true);
        NIGHT_TIME_TICKS = builder
                .comment("Target night time in ticks when Gargantua is cast (13000 = dusk, 18000 = midnight).")
                .defineInRange("nightTimeTicks", 18000, 13000, 23000);
        builder.pop();

        // 2. Flight Magic
        builder.comment("Flight Magic Settings").push("flight");
        BASE_MANA_PER_SEC = builder
                .comment("Base mana cost per second at Flight level 1.")
                .defineInRange("baseManaPerSec", 50.0d, 5.0d, 200.0d);
        MANA_SAVE_PER_LEVEL = builder
                .comment("Mana saved per second for each extra spell level.")
                .defineInRange("manaSavePerLevel", 2.5d, 0.0d, 10.0d);
        SCALE_COST_WITH_MOTION = builder
                .comment("Increase mana drain when flying fast or high above ground.")
                .define("scaleCostWithMotion", true);
        SPEED_COST_POWER = builder
                .comment("Curve exponent for extra mana drain at high flying speed.")
                .defineInRange("speedCostPower", 1.8d, 1.0d, 3.0d);
        MAX_SPEED_SCALE = builder
                .comment("Maximum speed multiplier for mana drain.")
                .defineInRange("maxSpeedScale", 8.0d, 1.0d, 20.0d);
        HEIGHT_COST_POWER = builder
                .comment("Curve exponent for extra mana drain when flying high above ground.")
                .defineInRange("heightCostPower", 1.7d, 1.0d, 3.0d);
        MAX_HEIGHT_SCALE = builder
                .comment("Maximum altitude multiplier for mana drain.")
                .defineInRange("maxHeightScale", 15.0d, 1.0d, 30.0d);
        EMPTY_MANA_SAFE_TICKS = builder
                .comment("Slow Falling ticks given if mana runs out midair (70 ticks = 3.5s).")
                .defineInRange("emptyManaSafeTicks", 70, 0, 200);
        AUTO_FLY_IN_AIR = builder
                .comment("Automatically start flying in midair while the Flight effect is active.")
                .define("autoFlyInAir", true);
        SHOW_FLIGHT_HUD = builder
                .comment("Show flight mana drain, altitude, and speed on the action bar.")
                .define("showFlightHud", true);
        builder.pop();

        // 3. Beams
        builder.comment("Zoltraak Beam Settings").push("beams");
        BEAM_RANGE = builder
                .comment("Max range in blocks for standard and Corrupted Zoltraak.")
                .defineInRange("beamRange", 128.0d, 16.0d, 256.0d);
        GREAT_BEAM_RANGE = builder
                .comment("Max range in blocks for charged Great Zoltraak.")
                .defineInRange("greatBeamRange", 192.0d, 32.0d, 512.0d);
        BEAM_DAMAGE_SCALE = builder
                .comment("Global damage multiplier for Zoltraak beams.")
                .defineInRange("beamDamageScale", 1.0d, 0.1d, 10.0d);
        BREAK_SHIELD_ON_HIT = builder
                .comment("Disable target shields when hit by a Zoltraak beam.")
                .define("breakShieldOnHit", true);
        BEAM_KNOCKBACK = builder
                .comment("Knockback strength applied to hit targets.")
                .defineInRange("beamKnockback", 1.5d, 0.0d, 5.0d);
        BLACK_WITHER_TICKS = builder
                .comment("Wither duration in ticks applied by Corrupted Zoltraak (120 ticks = 6.0s).")
                .defineInRange("blackWitherTicks", 120, 0, 600);
        PROTECT_ALLIES = builder
                .comment("Prevent beams from hitting team members, allies, or tamed pets.")
                .define("protectAllies", true);
        BEAM_BREAK_BLOCKS = builder
                .comment("Allow beams to carve tunnels through blocks when mobGriefing is enabled.")
                .define("beamBreakBlocks", true);
        BEAM_BREAK_RADIUS = builder
                .comment("Tunnel radius in blocks for standard beams (Great Zoltraak uses 2x).")
                .defineInRange("beamBreakRadius", 1.25d, 0.5d, 4.0d);
        builder.pop();

        // 4. Barrage
        builder.comment("Rapid Barrage Settings").push("barrage");
        BARRAGE_HOMING = builder
                .comment("Steering strength toward targets (0.0 = straight, 1.0 = instant turn).")
                .defineInRange("homingStrength", 0.34d, 0.0d, 1.0d);
        BARRAGE_SPEED = builder
                .comment("Flight speed of barrage bolts in blocks per tick.")
                .defineInRange("projectileSpeed", 3.12d, 0.5d, 6.0d);
        BARRAGE_LIFE_TICKS = builder
                .comment("Max bolt lifetime in ticks before disappearing (60 ticks = 3.0s).")
                .defineInRange("lifeTicks", 60, 20, 200);
        BARRAGE_FIRE_INTERVAL = builder
                .comment("Ticks between barrage volleys (10 ticks = 0.5s).")
                .defineInRange("fireIntervalTicks", 10, 4, 30);
        BARRAGE_DAMAGE_SCALE = builder
                .comment("Damage multiplier for barrage bolts.")
                .defineInRange("damageScale", 1.0d, 0.1d, 5.0d);
        BARRAGE_SPLASH_RADIUS = builder
                .comment("Splash damage radius in blocks around bolt impact.")
                .defineInRange("splashRadius", 1.2d, 0.0d, 4.0d);
        builder.pop();

        // 5. Defense Barrier
        builder.comment("Defense Barrier Settings").push("barrier");
        BARRIER_HP_SCALE = builder
                .comment("Health multiplier for directional shields and 360 domes.")
                .defineInRange("hpScale", 1.0d, 0.1d, 10.0d);
        BARRIER_DURATION_TICKS = builder
                .comment("Base barrier duration in ticks (300 ticks = 15.0s).")
                .defineInRange("durationTicks", 300, 60, 1200);
        BARRIER_BLOCK_ARROWS = builder
                .comment("Deflect arrows, tridents, and standard projectiles.")
                .define("blockArrows", true);
        BARRIER_BLOCK_MAGIC = builder
                .comment("Block incoming hostile magic projectiles.")
                .define("blockMagic", true);
        builder.pop();

        // 6. Qual Boss
        builder.comment("Qual Boss Settings").push("qual_boss");
        QUAL_BASE_HEALTH = builder
                .comment("Qual's base max health for solo fights.")
                .defineInRange("baseHealth", 1500.0d, 200.0d, 10000.0d);
        QUAL_HEALTH_PER_PLAYER = builder
                .comment("Extra health added for each nearby player.")
                .defineInRange("healthPerPlayer", 200.0d, 0.0d, 2000.0d);
        QUAL_ARMOR = builder
                .comment("Qual's base armor.")
                .defineInRange("armor", 30.0d, 0.0d, 100.0d);
        QUAL_ARMOR_TOUGHNESS = builder
                .comment("Qual's armor toughness.")
                .defineInRange("armorToughness", 10.0d, 0.0d, 50.0d);
        QUAL_REST_MIN_TICKS = builder
                .comment("Minimum pause ticks after Qual finishes barrage (200 ticks = 10.0s).")
                .defineInRange("restMinTicks", 200, 60, 400);
        QUAL_REST_MAX_TICKS = builder
                .comment("Maximum pause ticks after Qual finishes barrage (240 ticks = 12.0s).")
                .defineInRange("restMaxTicks", 240, 80, 600);
        QUAL_RESET_DISTANCE = builder
                .comment("Distance in blocks before Qual despawns back into the sealing stone.")
                .defineInRange("resetDistance", 96.0d, 32.0d, 256.0d);
        builder.pop();

        // 7. Items & Relics
        builder.comment("Staff & Relic Settings").push("items");
        STAFF_CAST_SPEED_BONUS = builder
                .comment("Cast speed bonus (+15%) from Frieren's Staff.")
                .defineInRange("staffCastSpeedBonus", 0.15d, 0.0d, 1.0d);
        STAFF_MANA_REGEN_BONUS = builder
                .comment("Mana regen bonus (mana/s) from Frieren's Staff.")
                .defineInRange("staffManaRegenBonus", 5.0d, 0.0d, 50.0d);
        CHRONOS_WATCH_RADIUS = builder
                .comment("Bubble radius in blocks for the Chronos Time Watch.")
                .defineInRange("watchRadius", 16.0d, 4.0d, 64.0d);
        CHRONOS_WATCH_DURATION_TICKS = builder
                .comment("Slow-time duration in ticks for the Chronos Time Watch (200 ticks = 10.0s).")
                .defineInRange("watchDurationTicks", 200, 20, 400);
        CHRONOS_WATCH_COOLDOWN_SEC = builder
                .comment("Cooldown in seconds for the Chronos Time Watch.")
                .defineInRange("watchCooldownSec", 60, 10, 600);
        builder.pop();

        SPEC = builder.build();
    }

    // --- Gargantua ---
    public static double getPullRadius() {
        return SPEC.isLoaded() ? PULL_RADIUS.get() : 100.0d;
    }

    public static double getPullStrength() {
        return SPEC.isLoaded() ? PULL_STRENGTH.get() : 0.12d;
    }

    public static double getTidalDamage() {
        return SPEC.isLoaded() ? TIDAL_DAMAGE.get() : 0.08d;
    }

    public static double getBlastDamage() {
        return SPEC.isLoaded() ? BLAST_DAMAGE.get() : 800.0d;
    }

    public static double getBlastRadius() {
        return SPEC.isLoaded() ? BLAST_RADIUS.get() : 32.0d;
    }

    public static boolean isReconstructBlocksEnabled() {
        return SPEC.isLoaded() && RECONSTRUCT_BLOCKS.get();
    }

    public static boolean isTearBlocksEnabled() {
        return !SPEC.isLoaded() || TEAR_BLOCKS.get();
    }

    public static int getMaxDebrisBlocks() {
        return SPEC.isLoaded() ? MAX_DEBRIS_BLOCKS.get() : 180;
    }

    public static boolean isRespectMobGriefing() {
        return !SPEC.isLoaded() || RESPECT_MOB_GRIEFING.get();
    }

    public static boolean isSetNightTimeEnabled() {
        return !SPEC.isLoaded() || SET_NIGHT_TIME.get();
    }

    public static int getNightTimeTicks() {
        return SPEC.isLoaded() ? NIGHT_TIME_TICKS.get() : 18000;
    }

    // --- Flight ---
    public static double getBaseManaPerSec() {
        return SPEC.isLoaded() ? BASE_MANA_PER_SEC.get() : 50.0d;
    }

    public static double getManaSavePerLevel() {
        return SPEC.isLoaded() ? MANA_SAVE_PER_LEVEL.get() : 2.5d;
    }

    public static boolean isMotionCostScalingEnabled() {
        return !SPEC.isLoaded() || SCALE_COST_WITH_MOTION.get();
    }

    public static double getSpeedCostPower() {
        return SPEC.isLoaded() ? SPEED_COST_POWER.get() : 1.8d;
    }

    public static double getMaxSpeedScale() {
        return SPEC.isLoaded() ? MAX_SPEED_SCALE.get() : 8.0d;
    }

    public static double getHeightCostPower() {
        return SPEC.isLoaded() ? HEIGHT_COST_POWER.get() : 1.7d;
    }

    public static double getMaxHeightScale() {
        return SPEC.isLoaded() ? MAX_HEIGHT_SCALE.get() : 15.0d;
    }

    public static int getEmptyManaSafeTicks() {
        return SPEC.isLoaded() ? EMPTY_MANA_SAFE_TICKS.get() : 70;
    }

    public static boolean isAutoFlyInAir() {
        return !SPEC.isLoaded() || AUTO_FLY_IN_AIR.get();
    }

    public static boolean isFlightHudEnabled() {
        return !SPEC.isLoaded() || SHOW_FLIGHT_HUD.get();
    }

    // --- Beams ---
    public static double getBeamRange() {
        return SPEC.isLoaded() ? BEAM_RANGE.get() : 128.0d;
    }

    public static double getGreatBeamRange() {
        return SPEC.isLoaded() ? GREAT_BEAM_RANGE.get() : 192.0d;
    }

    public static double getBeamDamageScale() {
        return SPEC.isLoaded() ? BEAM_DAMAGE_SCALE.get() : 1.0d;
    }

    public static boolean isBreakShieldOnHit() {
        return !SPEC.isLoaded() || BREAK_SHIELD_ON_HIT.get();
    }

    public static double getBeamKnockback() {
        return SPEC.isLoaded() ? BEAM_KNOCKBACK.get() : 1.5d;
    }

    public static int getBlackWitherTicks() {
        return SPEC.isLoaded() ? BLACK_WITHER_TICKS.get() : 120;
    }

    public static boolean isProtectAllies() {
        return !SPEC.isLoaded() || PROTECT_ALLIES.get();
    }

    public static boolean isBeamBreakBlocks() {
        return !SPEC.isLoaded() || BEAM_BREAK_BLOCKS.get();
    }

    public static double getBeamBreakRadius() {
        return SPEC.isLoaded() ? BEAM_BREAK_RADIUS.get() : 1.25d;
    }

    // --- Barrage ---
    public static double getBarrageHoming() {
        return SPEC.isLoaded() ? BARRAGE_HOMING.get() : 0.34d;
    }

    public static double getBarrageSpeed() {
        return SPEC.isLoaded() ? BARRAGE_SPEED.get() : 3.12d;
    }

    public static int getBarrageLifeTicks() {
        return SPEC.isLoaded() ? BARRAGE_LIFE_TICKS.get() : 60;
    }

    public static int getBarrageFireInterval() {
        return SPEC.isLoaded() ? BARRAGE_FIRE_INTERVAL.get() : 10;
    }

    public static double getBarrageDamageScale() {
        return SPEC.isLoaded() ? BARRAGE_DAMAGE_SCALE.get() : 1.0d;
    }

    public static double getBarrageSplashRadius() {
        return SPEC.isLoaded() ? BARRAGE_SPLASH_RADIUS.get() : 1.2d;
    }

    // --- Defense Barrier ---
    public static double getBarrierHpScale() {
        return SPEC.isLoaded() ? BARRIER_HP_SCALE.get() : 1.0d;
    }

    public static int getBarrierDurationTicks() {
        return SPEC.isLoaded() ? BARRIER_DURATION_TICKS.get() : 300;
    }

    public static boolean isBarrierBlockArrows() {
        return !SPEC.isLoaded() || BARRIER_BLOCK_ARROWS.get();
    }

    public static boolean isBarrierBlockMagic() {
        return !SPEC.isLoaded() || BARRIER_BLOCK_MAGIC.get();
    }

    // --- Qual Boss ---
    public static double getQualBaseHealth() {
        return SPEC.isLoaded() ? QUAL_BASE_HEALTH.get() : 1500.0d;
    }

    public static double getQualHealthPerPlayer() {
        return SPEC.isLoaded() ? QUAL_HEALTH_PER_PLAYER.get() : 200.0d;
    }

    public static double getQualArmor() {
        return SPEC.isLoaded() ? QUAL_ARMOR.get() : 30.0d;
    }

    public static double getQualArmorToughness() {
        return SPEC.isLoaded() ? QUAL_ARMOR_TOUGHNESS.get() : 10.0d;
    }

    public static int getQualRestMinTicks() {
        return SPEC.isLoaded() ? QUAL_REST_MIN_TICKS.get() : 200;
    }

    public static int getQualRestMaxTicks() {
        return SPEC.isLoaded() ? QUAL_REST_MAX_TICKS.get() : 240;
    }

    public static double getQualResetDistance() {
        return SPEC.isLoaded() ? QUAL_RESET_DISTANCE.get() : 96.0d;
    }

    // --- Items & Relics ---
    public static double getStaffCastSpeedBonus() {
        return SPEC.isLoaded() ? STAFF_CAST_SPEED_BONUS.get() : 0.15d;
    }

    public static double getStaffManaRegenBonus() {
        return SPEC.isLoaded() ? STAFF_MANA_REGEN_BONUS.get() : 5.0d;
    }

    public static double getChronosWatchRadius() {
        return SPEC.isLoaded() ? CHRONOS_WATCH_RADIUS.get() : 16.0d;
    }

    public static int getChronosWatchDurationTicks() {
        return SPEC.isLoaded() ? CHRONOS_WATCH_DURATION_TICKS.get() : 200;
    }

    public static int getChronosWatchCooldownSec() {
        return SPEC.isLoaded() ? CHRONOS_WATCH_COOLDOWN_SEC.get() : 60;
    }
}
