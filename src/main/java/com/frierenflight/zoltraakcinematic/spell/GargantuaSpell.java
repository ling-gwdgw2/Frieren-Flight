// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.spell;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.frierenflight.zoltraakcinematic.entity.GargantuaEntity;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicSounds;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * 🌌 Gargantua — Supreme Endgame Black Hole Magic.
 * Manifests a supermassive Kerr black hole with general relativistic gravitational lensing,
 * volumetric accretion disk, photon rings, and cataclysmic event horizon collapse.
 */
public class GargantuaSpell extends AbstractSpell {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "gargantua");

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(com.frierenflight.zoltraakcinematic.registry.ModCinematicSchools.BLACK_HOLE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(600.0) // 10 minutes
            .setAllowCrafting(true)
            .build();

    public GargantuaSpell() {
        this.baseManaCost = 300;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 500;
        this.spellPowerPerLevel = 0;
        this.castTime = 80; // 4.0 seconds (80 ticks)
    }

    @Override
    public ResourceLocation getSpellResource() {
        return ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public Optional<net.minecraft.sounds.SoundEvent> getCastStartSound() {
        return Optional.of(ModCinematicSounds.SINGULARITY_CHARGE.get());
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CHARGE_ANIMATION;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.FINISH_ANIMATION;
    }

    @Override
    public boolean requiresLearning() {
        return false;
    }

    @Override
    public CastResult canBeCastedBy(int spellLevel, CastSource castSource, MagicData magicData, net.minecraft.world.entity.player.Player player) {
        if (player != null && player.isCreative()) {
            return new CastResult(CastResult.Type.SUCCESS);
        }
        return super.canBeCastedBy(spellLevel, castSource, magicData, player);
    }

    /**
     * Computes the exact 3D world-space spawn center for Gargantua using a direct crosshair raycast
     * with ceiling clearance probing, vertical wall step-back, and line-of-sight verification.
     * Shared 1:1 between server spell execution and client pre-cast boundary shader preview.
     */
    public static Vec3 computeTargetPosition(Level level, LivingEntity entity, float partialTicks) {
        Vec3 eyePos = entity.getEyePosition(partialTicks);
        Vec3 lookVec = entity.getViewVector(partialTicks);
        double range = 56.0d;
        Vec3 traceEnd = eyePos.add(lookVec.scale(range));

        // Direct line-of-sight raycast from the caster's crosshair
        HitResult hit = level.clip(new ClipContext(eyePos, traceEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        Vec3 targetPos;

        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 hitPos = hit.getLocation();
            double hitDist = eyePos.distanceTo(hitPos);
            Direction hitFace = (hit instanceof BlockHitResult bhr) ? bhr.getDirection() : Direction.UP;

            if (hitFace == Direction.UP) {
                // Aimed at ground, canyon floor, or room floor:
                // Check if there is an overhead ceiling directly above the floor target
                HitResult ceilingProbe = level.clip(new ClipContext(
                        hitPos.add(0, 0.1d, 0), hitPos.add(0, 36.0d, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));

                if (ceilingProbe.getType() != HitResult.Type.MISS) {
                    // Enclosed subterranean space (cave/room/underhang):
                    // Center safely between floor and ceiling, leaving clearance on both sides
                    double roomHeight = ceilingProbe.getLocation().y - hitPos.y;
                    double hover = net.minecraft.util.Mth.clamp(roomHeight * 0.5d, 1.5d, Math.max(1.5d, roomHeight - 2.5d));
                    targetPos = new Vec3(hitPos.x, hitPos.y + hover, hitPos.z);
                } else {
                    // Open canyon floor or open landscape:
                    // Hover at an intuitive, comfortable elevation above the targeted ground
                    double hover = net.minecraft.util.Mth.clamp(hitDist * 0.22d, 2.5d, 8.5d);
                    targetPos = new Vec3(hitPos.x, hitPos.y + hover, hitPos.z);
                }
            } else if (hitFace == Direction.DOWN) {
                // Aimed at a ceiling: drop safely below ceiling into the open room/cave
                double drop = net.minecraft.util.Mth.clamp(hitDist * 0.20d, 2.0d, 4.0d);
                targetPos = hitPos.subtract(0, drop, 0);
            } else {
                // Aimed at a vertical wall (deep canyon wall, high cliff, or building wall):
                // Stay strictly in front of the wall in open air — NEVER punch behind or through it!
                double stepBack = net.minecraft.util.Mth.clamp(hitDist * 0.25d, 2.0d, 4.5d);
                Vec3 normal = Vec3.atLowerCornerOf(hitFace.getNormal());
                targetPos = hitPos.subtract(lookVec.scale(stepBack)).add(normal.scale(0.8d));
            }
        } else {
            // Open sky or boundless expanse: spawn directly along the player's crosshair
            targetPos = eyePos.add(lookVec.scale(44.0d));
        }

        // Bulletproof line-of-sight verification:
        // Ensure targetPos is directly visible from caster's eye and never obstructed by terrain/overhangs
        HitResult losCheck = level.clip(new ClipContext(
                eyePos, targetPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (losCheck.getType() != HitResult.Type.MISS) {
            double safeDist = Math.max(2.0d, eyePos.distanceTo(losCheck.getLocation()) - 1.5d);
            targetPos = eyePos.add(lookVec.scale(safeDist));
        }

        // Verify target position is not embedded inside any solid block
        net.minecraft.core.BlockPos targetBp = net.minecraft.core.BlockPos.containing(targetPos);
        if (!level.getBlockState(targetBp).isAir()) {
            Vec3 toEye = eyePos.subtract(targetPos);
            if (toEye.lengthSqr() > 1.0E-4) {
                Vec3 toEyeNorm = toEye.normalize();
                for (int step = 1; step <= 8; step++) {
                    Vec3 testPos = targetPos.add(toEyeNorm.scale(step * 0.5d));
                    if (level.getBlockState(net.minecraft.core.BlockPos.containing(testPos)).isAir()) {
                        targetPos = testPos;
                        break;
                    }
                }
            }
        }

        return targetPos;
    }

    /**
     * Computes the 3D world-space Kerr spin axis (~5.8 deg Interstellar grazing angle + ~3.8 deg roll)
     * relative to the caster's line of sight toward targetPos.
     */
    public static Vec3 computeSpinAxis(LivingEntity entity, Vec3 targetPos, float partialTicks) {
        Vec3 eyePos = entity.getEyePosition(partialTicks);
        Vec3 lookVec = entity.getViewVector(partialTicks);
        Vec3 toHole = targetPos.subtract(eyePos);
        Vec3 toHoleDir = toHole.lengthSqr() > 1.0e-4d ? toHole.normalize() : lookVec.normalize();
        Vec3 worldUp = new Vec3(0.0d, 1.0d, 0.0d);
        Vec3 holeRight = toHoleDir.cross(worldUp);
        if (holeRight.lengthSqr() < 1.0e-5d) {
            holeRight = new Vec3(1.0d, 0.0d, 0.0d);
        } else {
            holeRight = holeRight.normalize();
        }
        Vec3 holeUp = holeRight.cross(toHoleDir).normalize();

        double rollRad = Math.toRadians(3.8d);
        Vec3 tiltedUp = holeUp.scale(Math.cos(rollRad)).add(holeRight.scale(Math.sin(rollRad)));
        double grazingRad = Math.toRadians(5.8d);
        return tiltedUp.scale(Math.cos(grazingRad))
                .subtract(toHoleDir.scale(Math.sin(grazingRad)))
                .normalize();
    }

    /**
     * Transitions world time to night when the Gargantua black hole spell is activated,
     * preserving the day counter cleanly and immediately syncing connected clients.
     *
     * @param force true when invoked explicitly from a command; false when auto-triggered on spell activation.
     */
    public static void applyNightTimeOnActivation(ServerLevel serverLevel, boolean force) {
        if (serverLevel == null) {
            return;
        }
        if (!force && !com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.isSetNightTimeEnabled()) {
            return;
        }

        long targetMod = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getNightTimeTicks();
        long currentDayTime = serverLevel.getDayTime();
        long currentMod = Math.floorMod(currentDayTime, 24000L);
        if (!force && currentMod >= 14000L && currentMod <= 22000L) {
            // Already deep night: preserve existing nocturnal progression without skipping a day.
            return;
        }

        long delta = Math.floorMod(targetMod - currentMod, 24000L);
        long nextDayTime = currentDayTime + delta;

        net.minecraft.server.MinecraftServer server = serverLevel.getServer();
        if (server != null) {
            for (ServerLevel lvl : server.getAllLevels()) {
                lvl.setDayTime(nextDayTime);
            }
            boolean daylight = serverLevel.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT);
            server.getPlayerList().broadcastAll(
                    new net.minecraft.network.protocol.game.ClientboundSetTimePacket(
                            serverLevel.getGameTime(), nextDayTime, daylight),
                    serverLevel.dimension()
            );
        } else {
            serverLevel.setDayTime(nextDayTime);
        }
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            applyNightTimeOnActivation(serverLevel, false);

            Vec3 targetPos = computeTargetPosition(serverLevel, entity, 1.0f);
            Vec3 spinAxis = computeSpinAxis(entity, targetPos, 1.0f);

            float spellPower = getSpellPower(spellLevel, entity);
            GargantuaEntity blackHole = new GargantuaEntity(serverLevel, entity, targetPos, spinAxis, spellPower);
            serverLevel.addFreshEntity(blackHole);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float power = getSpellPower(spellLevel, caster);
        float powerMult = power / Math.max(1.0f, (float) this.baseSpellPower);
        float blastDamage = (float) com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getBlastDamage() * powerMult;
        return List.of(
                Component.translatable("spell.zoltraak_cinematic.gargantua.desc"),
                Component.translatable("ui.irons_spellbooks.radius", (int) com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getPullRadius()),
                Component.translatable("ui.irons_spellbooks.damage", io.redspace.ironsspellbooks.api.util.Utils.stringTruncation(blastDamage, 1))
        );
    }
}
