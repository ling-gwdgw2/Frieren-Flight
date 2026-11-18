// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.spell;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.frierenflight.zoltraakcinematic.entity.DefenseBarrierEntity;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicSchools;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
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

import java.util.List;

public class DefensiveMagicSpell extends AbstractSpell {
    private final ResourceLocation spellId = ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "defense");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.UNCOMMON)
            .setSchoolResource(ModCinematicSchools.ORDINARY_MAGIC_RESOURCE)
            .setMaxLevel(10)
            .setCooldownSeconds(12.0)
            .build();

    public DefensiveMagicSpell() {
        this.baseManaCost = 200;
        this.manaCostPerLevel = 10;
        this.baseSpellPower = 150;
        this.spellPowerPerLevel = 23; // Level 1 = 150, Level 10 = 357+ (scales with Spell Power)
        this.castTime = 0; // Instant deployment!
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
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.SELF_CAST_ANIMATION;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return AnimationHolder.none();
    }

    @Override
    public CastResult canBeCastedBy(int spellLevel, CastSource castSource, MagicData magicData, net.minecraft.world.entity.player.Player player) {
        if (player != null && player.isCreative()) {
            return new CastResult(CastResult.Type.SUCCESS);
        }
        return super.canBeCastedBy(spellLevel, castSource, magicData, player);
    }

    public int getDurationTicks(int spellLevel, LivingEntity caster) {
        int baseDuration = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getBarrierDurationTicks();
        return baseDuration + (spellLevel - 1) * 18; // 12.0s at Lv.1 scaling to 20.1s at Lv.10
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        float shieldCapacity = getSpellPower(spellLevel, caster) * (float) com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getBarrierHpScale();
        float domeCapacity = shieldCapacity * 0.75f;
        return List.of(
                Component.translatable("ui.zoltraak_cinematic.defense_capacity",
                        Utils.stringTruncation(shieldCapacity, 1) + " / Dome " + Utils.stringTruncation(domeCapacity, 1)),
                Component.translatable("ui.zoltraak_cinematic.defense_duration", Utils.stringTruncation(getDurationTicks(spellLevel, caster) / 20.0f, 1))
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            DefenseBarrierEntity existing = DefenseBarrierEntity.find(caster);
            int mode = caster.isCrouching() ? DefenseBarrierEntity.MODE_DOME : DefenseBarrierEntity.MODE_DIRECTIONAL;
            // 360-degree Dome trades 25% total absorption capacity for omnidirectional coverage.
            float modeScale = (mode == DefenseBarrierEntity.MODE_DOME) ? 0.75f : 1.0f;
            float capacity = getSpellPower(spellLevel, caster) * modeScale;
            int duration = getDurationTicks(spellLevel, caster);

            if (existing == null) {
                serverLevel.addFreshEntity(DefenseBarrierEntity.create(serverLevel, caster, capacity, mode, duration));
            } else {
                existing.refresh(capacity, mode, duration);
            }
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
