// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.entity;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Damage type lookup for Explosion Magic ("zoltraak_cinematic:explosion").
 */
public final class ExplosionDamage {

    public static final ResourceKey<DamageType> EXPLOSION_KEY = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "explosion")
    );

    private ExplosionDamage() {}

    public static DamageSource source(Level level, Entity directEntity, Entity causingEntity) {
        var opt = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(EXPLOSION_KEY);
        if (opt.isPresent()) {
            return new DamageSource(opt.get(), directEntity, causingEntity);
        }
        return level.damageSources().explosion(directEntity, causingEntity);
    }
}
