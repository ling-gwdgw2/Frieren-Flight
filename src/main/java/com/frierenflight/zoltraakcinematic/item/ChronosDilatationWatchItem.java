// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.item;

import com.frierenflight.zoltraakcinematic.registry.ModCinematicAttributes;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.curios.CurioBaseItem;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * ⏳ Chronos Dilatation Watch (นาฬิกาพกยืดเวลาสัมพัทธภาพ).
 * An ancient pocket watch whose ticks dilate near intense gravitational fields.
 * Curios Charm slot item: +15% Cooldown Reduction, +35% Black Hole Spell Power.
 */
public class ChronosDilatationWatchItem extends CurioBaseItem {
    public static final String CHARM_SLOT = "charm";

    public ChronosDilatationWatchItem() {
        super(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant());
        withAttributes(CHARM_SLOT,
                new AttributeContainer(AttributeRegistry.COOLDOWN_REDUCTION, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                new AttributeContainer(ModCinematicAttributes.BLACK_HOLE_SPELL_POWER, 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
        );
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            triggerTimeDilationField(level, player);
            int cooldownTicks = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getChronosWatchCooldownSec() * 20;
            player.getCooldowns().addCooldown(this, Math.max(20, cooldownTicks));
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static void triggerTimeDilationField(net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity caster) {
        double radius = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getChronosWatchRadius();
        int duration = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getChronosWatchDurationTicks();

        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                net.minecraft.sounds.SoundEvents.BELL_RESONATE, net.minecraft.sounds.SoundSource.PLAYERS, 2.0f, 0.6f);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 1.6f);

        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    caster.getX(), caster.getY() + 1.0, caster.getZ(), 60, radius * 0.4, 0.8, radius * 0.4, 0.2);

            net.minecraft.world.phys.AABB box = caster.getBoundingBox().inflate(radius);
            boolean respectFF = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.isProtectAllies();
            List<net.minecraft.world.entity.LivingEntity> targets = level.getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class, box,
                    e -> e != caster && e.isAlive() && (!respectFF || !io.redspace.ironsspellbooks.damage.DamageSources.isFriendlyFireBetween(caster, e))
            );

            for (net.minecraft.world.entity.LivingEntity target : targets) {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, duration, 3, false, true, true));
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, duration, 3, false, true, true));
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("item.zoltraak_cinematic.chronos_dilatation_watch.desc")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
