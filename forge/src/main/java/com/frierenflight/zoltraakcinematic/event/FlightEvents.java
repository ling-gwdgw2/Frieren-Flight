// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.event;

import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig;
import com.frierenflight.zoltraakcinematic.platform.Services;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicMobEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FlightEvents (Forge 1.20.1)
 * Handles continuous flight mechanics, exponential speed/altitude mana consumption,
 * ability synchronization, fall-damage immunity, landing protection, and action bar HUD.
 */
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
        UUID id = player.getUUID();
        activeFlyers.add(id);
        landingProtection.remove(id);

        if (player.onGround()) {
            player.setDeltaMovement(player.getDeltaMovement().add(0, 0.45, 0));
            player.hurtMarked = true;
        }
    }

    public static void disableFlight(ServerPlayer player) {
        UUID id = player.getUUID();
        activeFlyers.remove(id);
        landingProtection.add(id);

        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().flying = false;
            player.getAbilities().mayfly = false;
            player.onUpdateAbilities();
        }
        player.fallDistance = 0.0f;
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            activeFlyers.remove(event.getEntity().getUUID());
            // Retain landingProtection across logout so players reconnecting in mid-air do not plunge to their death.
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.hasEffect(ModCinematicMobEffects.FLIGHT.get())) {
                enableFlight(player);
            } else if (!player.onGround()) {
                landingProtection.add(player.getUUID());
            }
        }
    }

    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().is(DamageTypes.FALL) && event.getEntity() instanceof ServerPlayer player) {
            UUID id = player.getUUID();
            if (player.hasEffect(ModCinematicMobEffects.FLIGHT.get())
                    || activeFlyers.contains(id)
                    || landingProtection.remove(id)) {
                event.setCanceled(true);
                player.fallDistance = 0.0f;
            }
        }
    }

    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        if (player.onGround()) {
            landingProtection.remove(player.getUUID());
        }

        if (player.hasEffect(ModCinematicMobEffects.FLIGHT.get())) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
            activeFlyers.add(player.getUUID());

            boolean isFlying = player.getAbilities().flying;
            boolean isAirborne = !player.onGround() && !player.isInWater() && !player.isInLava();

            if (ZoltraakCinematicConfig.isAutoFlyInAir() && isAirborne && !isFlying) {
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                isFlying = true;
            }

            if (isFlying || isAirborne) {
                player.fallDistance = 0.0f;

                int spellLevel = player.getEffect(ModCinematicMobEffects.FLIGHT.get()).getAmplifier() + 1;

                float basePerSec = (float) Math.max(1.0, ZoltraakCinematicConfig.getBaseManaPerSec()
                        - (spellLevel - 1) * ZoltraakCinematicConfig.getManaSavePerLevel());
                float basePerTick = basePerSec / 20.0f;

                double dx = player.getX() - player.xOld;
                double dy = player.getY() - player.yOld;
                double dz = player.getZ() - player.zOld;
                double displacementSpeed = Math.sqrt(dx * dx + dy * dy + dz * dz);

                Vec3 vel = player.getDeltaMovement();
                double speed = Math.max(vel.length(), displacementSpeed);
                double verticalClimb = Math.max(vel.y, dy);

                boolean scalingEnabled = ZoltraakCinematicConfig.isMotionCostScalingEnabled();

                float speedMult = 1.0f;
                if (scalingEnabled && speed > 0.10) {
                    double excess = speed - 0.10;
                    speedMult += (float) Math.pow(excess / 0.18, ZoltraakCinematicConfig.getSpeedCostPower());
                }
                if (player.isSprinting()) {
                    speedMult += 0.5f;
                }
                speedMult = (float) Math.min(speedMult, ZoltraakCinematicConfig.getMaxSpeedScale());

                Vec3 start = player.position();
                Vec3 end = new Vec3(start.x, Math.max(player.level().getMinBuildHeight(), start.y - 128.0), start.z);
                BlockHitResult hit = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
                double heightAboveGround = (hit.getType() == HitResult.Type.BLOCK) ? Math.max(0.0, start.y - hit.getLocation().y) : 128.0;

                float heightMult = 1.0f;
                if (scalingEnabled && heightAboveGround > 3.0) {
                    double hExcess = heightAboveGround - 3.0;
                    heightMult += (float) Math.pow(hExcess / 10.0, ZoltraakCinematicConfig.getHeightCostPower());
                }
                if (scalingEnabled && verticalClimb > 0.05) {
                    heightMult += (float) (verticalClimb * 3.0);
                }
                heightMult = (float) Math.min(heightMult, ZoltraakCinematicConfig.getMaxHeightScale());

                float totalMult = 1.0f + (speedMult - 1.0f) + (heightMult - 1.0f);
                float drainThisTick = basePerTick * totalMult;

                if (!player.isCreative()) {
                    MagicData magicData = MagicData.getPlayerMagicData(player);
                    float currentMana = magicData.getMana();

                    if (currentMana < drainThisTick) {
                        player.removeEffect(ModCinematicMobEffects.FLIGHT.get());
                        disableFlight(player);
                        magicData.setMana(0.0f);
                        Services.PLATFORM.syncPlayerMana(player, magicData);

                        int emergencyTicks = ZoltraakCinematicConfig.getEmptyManaSafeTicks();
                        if (emergencyTicks > 0) {
                            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, emergencyTicks, 0, false, false, false));
                        }

                        ServerLevel sl = player.serverLevel();
                        sl.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 1.6f);
                        sl.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.4f);

                        player.displayClientMessage(
                                Component.translatable("ui.zoltraak_cinematic.flight_mana_depleted").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                                true
                        );
                        return;
                    } else {
                        magicData.setMana(currentMana - drainThisTick);
                        if (player.tickCount % 4 == 0) {
                            Services.PLATFORM.syncPlayerMana(player, magicData);
                        }
                    }
                }

                if (ZoltraakCinematicConfig.isFlightHudEnabled() && player.tickCount % 8 == 0) {
                    float drainPerSec = drainThisTick * 20.0f;
                    Component hud = Component.translatable(
                            "ui.zoltraak_cinematic.flight_hud",
                            String.format(Locale.ROOT, "%.0f", drainPerSec),
                            String.format(Locale.ROOT, "%.0f", heightAboveGround),
                            String.format(Locale.ROOT, "%.1f", speedMult));
                    player.displayClientMessage(hud, true);
                }
            } else {
                player.fallDistance = 0.0f;
            }
        } else {
            if (activeFlyers.contains(player.getUUID())) {
                disableFlight(player);
            }
        }
    }
}
