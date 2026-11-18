// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.spell;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.frierenflight.zoltraakcinematic.entity.ZoltraakCinematicBeamEntity;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Corrupted Zoltraak (Human-Killing Magic - Qual's Original Curse).
 * Heavy charged beam with Event-Horizon Dark Void & Electric Magenta aesthetics.
 * Inflicts Wither and Armor-Piercing damage.
 */
public class CorruptedZoltraakSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "corrupted_zoltraak");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(com.frierenflight.zoltraakcinematic.registry.ModCinematicSchools.ORDINARY_MAGIC_RESOURCE)
            .setMaxLevel(10)
            .setCooldownSeconds(4.5)
            .build();

    public CorruptedZoltraakSpell() {
        this.baseManaCost = 250;
        this.manaCostPerLevel = 7;
        this.baseSpellPower = 100;
        this.spellPowerPerLevel = 9;
        this.castTime = 0;
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
        return CastType.INSTANT;
    }

    @Override
    public Optional<net.minecraft.sounds.SoundEvent> getCastStartSound() {
        return Optional.empty();
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ONE_HANDED_RAY_SHOOT;
    }

    @Override
    public CastResult canBeCastedBy(int spellLevel, CastSource castSource, MagicData magicData, net.minecraft.world.entity.player.Player player) {
        if (player != null && player.isCreative()) {
            return new CastResult(CastResult.Type.SUCCESS);
        }
        return super.canBeCastedBy(spellLevel, castSource, magicData, player);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            float damage = getSpellPower(spellLevel, entity);
            Vec3 spawnPos = ZoltraakCinematicBeamEntity.getStaffAnchorPos(entity);
            // Player crouch trigger for Great Zoltraak is temporarily disabled (planned as a separate spell);
            // keep Phase 4 overdrive burst exclusively for Qual Boss.
            boolean isLarge = entity instanceof com.frierenflight.zoltraakcinematic.entity.boss.QualBossEntity qualBoss
                    && qualBoss.getPhase() >= 4
                    && qualBoss.getRandom().nextFloat() < 0.45f;
            float range = (float) (isLarge
                    ? com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getGreatBeamRange()
                    : com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getBeamRange());

            // Spawns Beam with Color Theme 1 (HUMAN_KILLING / Dark Purple)
            ZoltraakCinematicBeamEntity beam = new ZoltraakCinematicBeamEntity(serverLevel, entity, damage, range, 1);
            if (isLarge) {
                beam.setMode(com.frierenflight.zoltraakcinematic.client.renderer.ZoltraakMode.LARGE);
            }
            beam.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            beam.setYRot(net.minecraft.util.Mth.wrapDegrees(entity.getYRot()));
            beam.setXRot(net.minecraft.util.Mth.clamp(entity.getXRot(), -90.0f, 90.0f));
            serverLevel.addFreshEntity(beam);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.distance", com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getBeamRange()),
                Component.translatable("ui.irons_spellbooks.radius", 12.0),
                Component.translatable("spell.zoltraak_cinematic.corrupted_zoltraak.perk")
        );
    }
}
