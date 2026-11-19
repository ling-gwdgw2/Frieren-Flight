// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.spell;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig;
import com.frierenflight.zoltraakcinematic.entity.ExplosionMagicEntity;
import com.frierenflight.zoltraakcinematic.entity.ZoltraakCinematicBeamEntity;
import com.frierenflight.zoltraakcinematic.platform.Services;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Explosion Magic (Crimson Demon Ultimate).
 * Channels stacked crimson vortex rings over the target during a 3.5s chant (70 ticks),
 * strikes with a blazing beam, detonates into a stylized radial sunburst fireball & crater,
 * drains remaining mana to 0, and leaves brief exhaustion debuffs in non-Creative modes.
 */
public class ExplosionSpell extends AbstractSpell {

    public static final double CAST_RANGE = 72.0;

    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "explosion");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.LEGENDARY)
            .setSchoolResource(SchoolRegistry.FIRE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(120.0)
            .build();

    public ExplosionSpell() {
        this.baseManaCost = 1000;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 600;
        this.spellPowerPerLevel = 20;
        this.castTime = 110; // ~5.5 seconds default dramatic chant
    }

    @Override
    public int getCastTime(int spellLevel) {
        return Math.max(20, ZoltraakCinematicConfig.getExplosionCastTimeTicks());
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
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
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
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
    public CastResult canBeCastedBy(int spellLevel, CastSource castSource, MagicData magicData, Player player) {
        if (player != null && player.isCreative()) {
            return new CastResult(CastResult.Type.SUCCESS);
        }
        return super.canBeCastedBy(spellLevel, castSource, magicData, player);
    }

    @Override
    public void onServerCastTick(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        super.onServerCastTick(level, spellLevel, entity, playerMagicData);

        if (entity instanceof Player player && player.isCreative() && playerMagicData != null) {
            float minCreativeMana = this.getManaCost(spellLevel) * 2.0f;
            if (playerMagicData.getMana() < minCreativeMana) {
                playerMagicData.setMana(minCreativeMana);
            }
        }
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            Vec3 startPos = ZoltraakCinematicBeamEntity.getStaffAnchorPos(entity, 1.0f);
            Vec3 targetPos = computeTargetPosition(serverLevel, entity, 1.0f, CAST_RANGE);
            float power = getSpellPower(spellLevel, entity);

            ExplosionMagicEntity blast = new ExplosionMagicEntity(serverLevel, entity, startPos, targetPos, power);
            serverLevel.addFreshEntity(blast);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);

        if (!level.isClientSide) {
            boolean isCreative = entity instanceof Player player && player.isCreative();
            if (!isCreative) {
                if (ZoltraakCinematicConfig.isExplosionDrainMana() && playerMagicData != null) {
                    playerMagicData.setMana(0.0f);
                    if (entity instanceof ServerPlayer serverPlayer) {
                        Services.PLATFORM.syncPlayerMana(serverPlayer, playerMagicData);
                    }
                }
                if (ZoltraakCinematicConfig.isExplosionExhaustionEnabled()) {
                    int duration = Math.max(20, ZoltraakCinematicConfig.getExplosionExhaustionTicks());
                    entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 4, false, true, true));
                    entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2, false, true, true));
                    entity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, duration, 2, false, true, true));
                }
            }
        }
    }

    /**
     * Resolves the blast target coordinates along the caster's aim ray,
     * snapping slightly upward sky-aims down onto nearby ground so the spell remains grounded cleanly.
     */
    public static Vec3 computeTargetPosition(Level level, LivingEntity entity, float partial, double maxRange) {
        Vec3 eye = entity.getEyePosition(partial);
        Vec3 look = ZoltraakCinematicBeamEntity.getAccurateLookVector(entity, partial);
        Vec3 end = eye.add(look.scale(maxRange));

        HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, entity));
        if (hit.getType() != HitResult.Type.MISS) {
            return hit.getLocation();
        }

        Vec3 downEnd = end.subtract(0.0, 40.0, 0.0);
        HitResult downHit = level.clip(new ClipContext(end, downEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, entity));
        if (downHit.getType() != HitResult.Type.MISS) {
            return downHit.getLocation();
        }
        return end;
    }

    /**
     * Shared center blast damage formula (~1200 at baseSpellPower 600).
     */
    public static float computeBlastDamage(float power) {
        return (float) ((120.0 + power * 1.80) * ZoltraakCinematicConfig.getExplosionDamageScale());
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float estDamage = computeBlastDamage(getSpellPower(spellLevel, caster));
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(estDamage, 1)),
                Component.translatable("ui.irons_spellbooks.radius", Utils.stringTruncation((float) ZoltraakCinematicConfig.getExplosionRadius(), 1)),
                Component.translatable("ui.irons_spellbooks.distance", Utils.stringTruncation((float) CAST_RANGE, 1))
        );
    }
}
