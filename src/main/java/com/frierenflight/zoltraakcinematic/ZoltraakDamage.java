// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic;

import com.frierenflight.zoltraakcinematic.platform.Services;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Spell damage utility handling rapid invulnerability-frame bypass, multipart resolution, and magic resistance scaling.
 */
public final class ZoltraakDamage {

    private static final ThreadLocal<Boolean> BYPASS_BARRIER = ThreadLocal.withInitial(() -> false);

    public static void runBypassingBarriers(Runnable action) {
        boolean prev = BYPASS_BARRIER.get();
        BYPASS_BARRIER.set(true);
        try {
            action.run();
        } finally {
            BYPASS_BARRIER.set(prev);
        }
    }

    public static boolean isBypassingBarriers() {
        return BYPASS_BARRIER.get();
    }

    /**
     * Resolves an entity or multipart entity segment (e.g. Ender Dragon parts) to its owning LivingEntity.
     */
    public static LivingEntity resolveLivingTarget(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living;
        }
        if (entity != null) {
            Entity parent = Services.PLATFORM.getPartParent(entity);
            if (parent instanceof LivingEntity parentLiving) {
                return parentLiving;
            }
        }
        return null;
    }

    public static boolean apply(LivingEntity target, float amount, DamageSource source, boolean rapid) {
        return apply(target, amount, source, null, rapid);
    }

    public static boolean apply(LivingEntity target, float amount, DamageSource source, SchoolType school, boolean rapid) {
        if (amount <= 0.0F || target == null || target.isDeadOrDying()) {
            return false;
        }

        // ISS DamageSources.applyDamage already applies school resistance for SpellDamageSource.
        // Only compute school resist here for custom non-SpellDamageSource types (e.g. Explosion, Gargantua).
        if (school != null && !(source instanceof SpellDamageSource)) {
            float resistMult = DamageSources.getResist(target, school);
            amount *= Math.max(0.0f, resistMult);
            if (amount <= 0.0F) {
                return false;
            }
        }

        // Clear prior invulnerability only BEFORE applying so multi-hit/rapid spells register
        // without stripping post-hit i-frames from unrelated attacks on subsequent ticks.
        if (rapid) {
            target.invulnerableTime = 0;
        }

        return DamageSources.applyDamage(target, amount, source);
    }

    private ZoltraakDamage() {}
}
