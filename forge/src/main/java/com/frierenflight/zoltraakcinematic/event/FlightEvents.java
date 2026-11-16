// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.event;

import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig;
import com.frierenflight.zoltraakcinematic.platform.Services;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicMobEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FlightEvents {
    private static final Set<UUID> activeFlyers = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> landingProtection = ConcurrentHashMap.newKeySet();

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(FlightEvents::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, FlightEvents::onLivingHurt);
        MinecraftForge.EVENT_BUS.addListener(FlightEvents::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(FlightEvents::onPlayerLoggedIn);
    }

    public static void enableFlight(ServerPlayer player) {
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.fallDistance = 0.0f;
        UUID id = player.getGameProfile().getId();
        activeFlyers.add(id);
        landingProtection.remove(id);
    }

    public static void disableFlight(ServerPlayer player) {
        UUID id = player.getGameProfile().getId();
        activeFlyers.remove(id);
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            landingProtection.add(id);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            UUID id = event.getEntity().getGameProfile().getId();
            activeFlyers.remove(id);
            landingProtection.remove(id);
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.hasEffect(ModCinematicMobEffects.FLIGHT.get())) {
                enableFlight(player);
            }
        }
    }

    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (event.getSource().is(DamageTypes.FALL)) {
                UUID id = player.getGameProfile().getId();
                if (activeFlyers.contains(id) || landingProtection.contains(id)) {
                    event.setCanceled(true);
                    landingProtection.remove(id);
                }
            }
        }
    }

    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        UUID id = player.getGameProfile().getId();
        boolean hasFlightEffect = player.hasEffect(ModCinematicMobEffects.FLIGHT.get());
        if (!hasFlightEffect) {
            if (activeFlyers.contains(id)) {
                disableFlight(player);
            }
            if (player.onGround()) {
                landingProtection.remove(id);
            }
            return;
        }

        if (!player.getAbilities().mayfly) {
            enableFlight(player);
        }

        if (player.getAbilities().flying && !player.isCreative() && !player.isSpectator()) {
            player.fallDistance = 0.0f;
            MagicData magicData = MagicData.getPlayerMagicData(player);

            float baseDrain = 2.0f;
            Vec3 delta = player.getDeltaMovement();
            double horizontalSpeed = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float speedDrain = (float) (horizontalSpeed * 5.0f);
            float currentDrain = (baseDrain + speedDrain) / 20.0f;

            if (magicData.getMana() < currentDrain) {
                player.removeEffect(ModCinematicMobEffects.FLIGHT.get());
                disableFlight(player);
                player.displayClientMessage(
                        Component.translatable("ui.irons_spellbooks.not_enough_mana").withStyle(ChatFormatting.RED),
                        true
                );
                player.level().playSound(null, player.blockPosition(), SoundEvents.CONDUIT_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 0.8F);
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false, true));
            } else {
                magicData.setMana(Math.max(0.0f, magicData.getMana() - currentDrain));
                Services.PLATFORM.syncPlayerMana(player, magicData);
            }

            if (player.level() instanceof ServerLevel serverLevel) {
                if (horizontalSpeed > 0.15) {
                    Vec3 pos = player.position().add(0, 0.2, 0);
                    serverLevel.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 2, 0.2, 0.1, 0.2, 0.02);
                }
            }
        }
    }
}
