// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.frierenflight.zoltraakcinematic.client.renderer.ZoltraakPhotonBeamRenderer;
import com.frierenflight.zoltraakcinematic.entity.ZoltraakCinematicBeamEntity;
import com.frierenflight.zoltraakcinematic.client.renderer.QualSealingStoneRenderer;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicBlocks;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * ZoltraakCinematicClientEvents
 * Handles client-side entity renderer registration, dynamic camera kickback/shake,
 * and cinematic exposure flash overlays.
 */
public class ZoltraakCinematicClientEvents {

    private static int shakeDuration = 0;
    private static int maxShakeDuration = 0;
    private static float shakeIntensity = 0.0f;

    private static int flashDuration = 0;
    private static int maxFlashDuration = 0;
    private static float flashIntensity = 0.0f;
    private static int flashColorRgb = 0xFFFFFF;

    private static int barrageRumbleTicks = 0;
    private static net.minecraft.client.CameraType originalCameraType = null;
    private static boolean forcedThirdPerson = false;
    private static int cameraRestoreDelay = 0;

    public static final net.minecraft.client.KeyMapping KEY_DEFENSE = new net.minecraft.client.KeyMapping(
            "key.zoltraak_cinematic.defense",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            com.mojang.blaze3d.platform.InputConstants.KEY_X,
            "key.categories.zoltraak_cinematic"
    );
    private static long lastDefensePressTime = 0;

    public static final net.minecraft.client.KeyMapping KEY_FLIGHT = new net.minecraft.client.KeyMapping(
            "key.zoltraak_cinematic.flight",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            com.mojang.blaze3d.platform.InputConstants.KEY_V,
            "key.categories.zoltraak_cinematic"
    );

    public static final net.minecraft.client.KeyMapping KEY_CONFIG = new net.minecraft.client.KeyMapping(
            "key.zoltraak_cinematic.config",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            // Backslash avoids colliding with Iris's default shader toggle keybind (K)
            com.mojang.blaze3d.platform.InputConstants.KEY_BACKSLASH,
            "key.categories.zoltraak_cinematic"
    );

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ZoltraakCinematicClientEvents::onRegisterEntityRenderers);
        modEventBus.addListener(com.frierenflight.zoltraakcinematic.client.renderer.ZoltraakCinematicShaders::onRegisterShaders);
        modEventBus.addListener(ZoltraakCinematicClientEvents::onRegisterKeyMappings);

        // Register gameplay client events on the NeoForge Event Bus
        NeoForge.EVENT_BUS.addListener(ZoltraakCinematicClientEvents::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(ZoltraakCinematicClientEvents::onRenderGui);
        NeoForge.EVENT_BUS.addListener(ZoltraakCinematicClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ZoltraakCinematicClientEvents::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(com.frierenflight.zoltraakcinematic.client.renderer.ZoltraakBarrageArrayRenderer::renderArray);
        NeoForge.EVENT_BUS.addListener(com.frierenflight.zoltraakcinematic.client.renderer.ExplosionChargeRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(com.frierenflight.zoltraakcinematic.client.renderer.ExplosionMagicRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(com.frierenflight.zoltraakcinematic.client.renderer.GargantuaPreviewRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(com.frierenflight.zoltraakcinematic.client.renderer.ZoltraakRenderPass::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(com.frierenflight.zoltraakcinematic.client.renderer.GargantuaPostProcessor::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(ZoltraakCinematicClientEvents::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(ZoltraakCinematicClientEvents::onEntityLeaveLevel);
        NeoForge.EVENT_BUS.addListener(ZoltraakCinematicClientEvents::onPlaySound);
    }

    public static void onPlaySound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        net.minecraft.client.resources.sounds.SoundInstance original = event.getSound();
        if (original == null) return;
        net.minecraft.resources.ResourceLocation loc = original.getLocation();
        if (loc != null && loc.getNamespace().equals(ZoltraakCinematicMod.MODID)) {
            if (loc.getPath().equals("tinnitus") && !com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isEarRingingEnabled()) {
                event.setSound(null);
                return;
            }
            float masterVol = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getModSoundVolume();
            if (masterVol <= 0.001f) {
                event.setSound(null);
                return;
            }
            if (Math.abs(masterVol - 1.0f) > 0.001f && !(original instanceof net.minecraft.client.resources.sounds.TickableSoundInstance)) {
                event.setSound(new net.minecraft.client.resources.sounds.SoundInstance() {
                    @Override
                    public net.minecraft.resources.ResourceLocation getLocation() {
                        return original.getLocation();
                    }

                    @Override
                    public net.minecraft.client.sounds.WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager manager) {
                        return original.resolve(manager);
                    }

                    @Override
                    public net.minecraft.client.resources.sounds.Sound getSound() {
                        return original.getSound();
                    }

                    @Override
                    public net.minecraft.sounds.SoundSource getSource() {
                        return original.getSource();
                    }

                    @Override
                    public boolean isLooping() {
                        return original.isLooping();
                    }

                    @Override
                    public boolean isRelative() {
                        return original.isRelative();
                    }

                    @Override
                    public int getDelay() {
                        return original.getDelay();
                    }

                    @Override
                    public float getVolume() {
                        return original.getVolume() * masterVol;
                    }

                    @Override
                    public float getPitch() {
                        return original.getPitch();
                    }

                    @Override
                    public double getX() {
                        return original.getX();
                    }

                    @Override
                    public double getY() {
                        return original.getY();
                    }

                    @Override
                    public double getZ() {
                        return original.getZ();
                    }

                    @Override
                    public Attenuation getAttenuation() {
                        return original.getAttenuation();
                    }

                    @Override
                    public boolean canStartSilent() {
                        return original.canStartSilent();
                    }

                    @Override
                    public boolean canPlaySound() {
                        return original.canPlaySound();
                    }
                });
            }
        }
    }

    public static void onEntityJoinLevel(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            if (event.getEntity() instanceof com.frierenflight.zoltraakcinematic.entity.GargantuaEntity g) {
                com.frierenflight.zoltraakcinematic.client.renderer.GargantuaPostProcessor.registerClientInstance(g);
            } else if (event.getEntity() instanceof com.frierenflight.zoltraakcinematic.entity.ExplosionMagicEntity exp) {
                com.frierenflight.zoltraakcinematic.client.renderer.ExplosionMagicRenderer.registerClientInstance(exp);
            }
        }
    }

    public static void onEntityLeaveLevel(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            if (event.getEntity() instanceof com.frierenflight.zoltraakcinematic.entity.GargantuaEntity g) {
                com.frierenflight.zoltraakcinematic.client.renderer.GargantuaPostProcessor.unregisterClientInstance(g);
            } else if (event.getEntity() instanceof com.frierenflight.zoltraakcinematic.entity.ExplosionMagicEntity exp) {
                com.frierenflight.zoltraakcinematic.client.renderer.ExplosionMagicRenderer.unregisterClientInstance(exp);
            }
        }
    }

    public static void onRegisterKeyMappings(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(KEY_DEFENSE);
        event.register(KEY_FLIGHT);
        event.register(KEY_CONFIG);
    }

    public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            shakeDuration = 0;
            maxShakeDuration = 0;
            shakeIntensity = 0.0f;
            flashDuration = 0;
            flashIntensity = 0.0f;
            flashColorRgb = 0xFFFFFF;
            barrageRumbleTicks = 0;
            if (forcedThirdPerson && originalCameraType != null) {
                Minecraft.getInstance().options.setCameraType(originalCameraType);
                originalCameraType = null;
            }
            forcedThirdPerson = false;
            cameraRestoreDelay = 0;
            com.frierenflight.zoltraakcinematic.client.renderer.GargantuaPostProcessor.release();
            com.frierenflight.zoltraakcinematic.client.renderer.GargantuaPreviewRenderer.resetState();
            com.frierenflight.zoltraakcinematic.client.renderer.ExplosionChargeRenderer.resetState();
            com.frierenflight.zoltraakcinematic.client.renderer.ExplosionMagicRenderer.resetState();
        }
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModCinematicEntities.ZOLTRAAK_BEAM.get(), ZoltraakPhotonBeamRenderer::new);
        event.registerEntityRenderer(ModCinematicEntities.ZOLTRAAK_BARRAGE_PROJECTILE.get(), com.frierenflight.zoltraakcinematic.client.renderer.ZoltraakBarrageProjectileRenderer::new);
        event.registerEntityRenderer(ModCinematicEntities.DEFENSE_BARRIER.get(), com.frierenflight.zoltraakcinematic.client.renderer.DefenseBarrierRenderer::new);
        event.registerEntityRenderer(ModCinematicEntities.QUAL_BOSS.get(), com.frierenflight.zoltraakcinematic.client.renderer.QualBossRenderer::new);
        event.registerEntityRenderer(ModCinematicEntities.GARGANTUA.get(), com.frierenflight.zoltraakcinematic.client.renderer.GargantuaRenderer::new);
        event.registerEntityRenderer(ModCinematicEntities.EXPLOSION.get(), com.frierenflight.zoltraakcinematic.client.renderer.ExplosionMagicRenderer::new);
        event.registerBlockEntityRenderer(ModCinematicBlocks.QUAL_SEALING_STONE_BE.get(), QualSealingStoneRenderer::new);
    }

    /**
     * Triggers cinematic screen kickback and flash when Zoltraak discharges (Tick 8).
     */
    public static void triggerFireShock(Entity beam, Vec3 beamPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double dist = mc.player.position().distanceTo(beamPos);
        boolean isCaster = (beam instanceof ZoltraakCinematicBeamEntity b)
                && (b.getOwner() == mc.player || b.casterId() == mc.player.getId());

        boolean shakeEnabled = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isCameraShakeEnabled();
        float shakeMult = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getShakeStrength();

        if (isCaster) {
            triggerCameraShake(6, 1.0f * shakeMult);
            if (mc.options.getCameraType().isFirstPerson()) {
                triggerFlash(3, 1.0f);
            }
        } else if (dist < 32.0) {
            float factor = (float) (1.0 - dist / 32.0);
            triggerCameraShake((int) (4 * factor), 0.65f * factor * shakeMult);
        }
    }

    /**
     * Triggers concussive camera thump when a Zoltraak beam detonates on impact near the viewer.
     */
    public static void triggerImpactShock(ZoltraakCinematicBeamEntity beam, Vec3 impactPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isCameraShakeEnabled()) return;

        double dist = mc.player.position().distanceTo(impactPos);
        double maxDist = 48.0 * beam.scale();
        if (dist < maxDist) {
            float shakeMult = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getShakeStrength();
            float factor = (float) Math.pow(1.0 - dist / maxDist, 1.35);
            triggerCameraShake(Math.max(3, (int) (7 * factor)), 0.92F * factor * shakeMult);
        }
    }

    public static void triggerCameraShake(int duration, float intensity) {
        if (!com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isCameraShakeEnabled()) return;
        int d = Math.max(1, duration);
        if (shakeDuration <= 0 || d >= shakeDuration) {
            shakeDuration = d;
            maxShakeDuration = d;
        }
        shakeIntensity = Math.max(shakeIntensity, intensity);
    }

    /**
     * Triggers continuous micro-rumble screen vibration during rapid barrage fire.
     */
    public static void triggerBarrageMicroRumble() {
        if (!com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isCameraShakeEnabled()) return;
        barrageRumbleTicks = Math.max(barrageRumbleTicks, 3);
    }

    /**
     * Applies high-speed camera kickback & harmonic screen vibration during spell release,
     * and subtle aerodynamic banking roll during flight.
     */
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        boolean shakeEnabled = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isCameraShakeEnabled();
        float shakeMult = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getShakeStrength();

        if (shakeEnabled && shakeDuration > 0 && maxShakeDuration > 0) {
            float partial = (float) event.getPartialTick();
            float t = Math.max(0.0f, (maxShakeDuration - shakeDuration) + partial);
            float progress = net.minecraft.util.Mth.clamp((shakeDuration - partial) / (float) maxShakeDuration, 0.0f, 1.0f) * shakeIntensity;

            // Sharp upward kick from immense spell recoil, decaying cleanly
            float pitchKick = -2.8f * (float) Math.exp(-t * 0.65) * shakeIntensity;

            // Multi-frequency harmonic jitter for cinematic impact feel
            float pitchJitter = (float) Math.sin(t * 26.0) * 0.95f * progress;
            float yawJitter   = (float) Math.cos(t * 21.0) * 0.75f * progress;
            float rollJitter  = (float) Math.sin(t * 16.0) * 1.35f * progress;

            event.setPitch(event.getPitch() + pitchKick + pitchJitter);
            event.setYaw(event.getYaw() + yawJitter);
            event.setRoll(event.getRoll() + rollJitter);
        }

        if (shakeEnabled && barrageRumbleTicks > 0) {
            float rumbleTime = (float) event.getPartialTick() + (3 - barrageRumbleTicks);
            float rumblePitch = (float) Math.sin(rumbleTime * 34.0) * 0.16f * shakeMult;
            float rumbleYaw   = (float) Math.cos(rumbleTime * 28.0) * 0.13f * shakeMult;
            float rumbleRoll  = (float) Math.sin(rumbleTime * 22.0) * 0.10f * shakeMult;

            event.setPitch(event.getPitch() + rumblePitch);
            event.setYaw(event.getYaw() + rumbleYaw);
            event.setRoll(event.getRoll() + rumbleRoll);
        }

        // Flight camera tilt
        if (com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isFlightTiltEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.hasEffect(com.frierenflight.zoltraakcinematic.registry.ModCinematicMobEffects.FLIGHT) && mc.player.getAbilities().flying) {
                float yRotDelta = mc.player.getYRot() - mc.player.yRotO;
                while (yRotDelta < -180.0f) yRotDelta += 360.0f;
                while (yRotDelta >= 180.0f) yRotDelta -= 360.0f;
                float targetRoll = net.minecraft.util.Mth.clamp(-yRotDelta * 0.45f, -6.5f, 6.5f);
                event.setRoll(event.getRoll() + targetRoll);
            }
        }
    }

    public static void triggerFlash(int duration, float intensity) {
        triggerFlash(duration, intensity, 0xFFFFFF);
    }

    public static void triggerFlash(int duration, float intensity, int rgb) {
        if (!com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isScreenFlashEnabled()) return;
        flashDuration = duration;
        maxFlashDuration = duration;
        flashIntensity = intensity * com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getFlashStrength();
        flashColorRgb = rgb & 0xFFFFFF;
    }

    public static void triggerExplosionDetonation(com.frierenflight.zoltraakcinematic.entity.ExplosionMagicEntity exp) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 targetPos = exp.getTargetPos();
        double dist = mc.player.position().distanceTo(targetPos);
        double maxDist = Math.max(72.0, exp.getRadius() * 4.0);
        if (dist >= maxDist) return;

        float factor = (float) Math.pow(1.0 - dist / maxDist, 1.2);
        if (com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isCameraShakeEnabled()) {
            float shakeMult = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getShakeStrength();
            triggerCameraShake(Math.max(12, (int) (32 * factor)), 2.65F * factor * shakeMult);
        }

        float flashScale = mc.options.getCameraType().isFirstPerson() ? 0.94F : 0.62F;
        triggerFlash(11, flashScale * factor, 0xFFE8A0);

        double earMaxDist = Math.max(40.0, exp.getRadius() * 2.2);
        if (dist < earMaxDist && com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isEarRingingEnabled()) {
            float earVol = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getEarRingingVolume();
            if (earVol > 0.005F) {
                float earFactor = (float) Math.pow(Math.max(0.0, 1.0 - dist / earMaxDist), 1.3);
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        com.frierenflight.zoltraakcinematic.registry.ModCinematicSounds.TINNITUS.get(),
                        1.0f,
                        Math.min(1.0f, 0.85f * earVol * earFactor)
                ));
            }
        }
    }

    /**
     * Renders a brief high-exposure blinding flash and chromatic edge fringe in First-Person.
     */
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (flashDuration > 0 && maxFlashDuration > 0) {
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float progress = Math.max(0.0f, (float) flashDuration - partial) / (float) maxFlashDuration * flashIntensity;
            int alpha = (int) (net.minecraft.util.Mth.clamp(progress, 0.0f, 1.0f) * 230.0f);
            if (alpha > 0) {
                int w = event.getGuiGraphics().guiWidth();
                int h = event.getGuiGraphics().guiHeight();

                // High-exposure blinding flash overlay (supports warm gold for Explosion or pure white)
                int flashColor = (alpha << 24) | (flashColorRgb & 0xFFFFFF);
                event.getGuiGraphics().fill(0, 0, w, h, flashColor);

                // Screen edge color fringe (cyan left, violet right)
                if (com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isColorFringeEnabled()) {
                    int borderAlpha = (int) (progress * 90.0f);
                    if (borderAlpha > 0) {
                        int cyanEdge = (borderAlpha << 24) | 0x00E5FF;
                        int violetEdge = (borderAlpha << 24) | 0xC040FF;
                        event.getGuiGraphics().fill(0, 0, 8, h, cyanEdge);
                        event.getGuiGraphics().fill(w - 8, 0, w, h, violetEdge);
                    }
                }
            }
        }

        // Gargantua cinematic screen effects (Supernova flash & Act 3 Implosion darkness)
        com.frierenflight.zoltraakcinematic.client.cinematic.GargantuaScreenFx.renderGui(event);
    }

    /**
     * Decays active camera shake and flash timers every client tick and observes entities for discharge VFX.
     */
    public static void onClientTick(ClientTickEvent.Post event) {
        if (shakeDuration > 0) {
            shakeDuration--;
            if (shakeDuration == 0) {
                shakeIntensity = 0.0f;
                maxShakeDuration = 0;
            }
        }
        if (flashDuration > 0) {
            flashDuration--;
            if (flashDuration == 0) {
                flashIntensity = 0.0f;
                maxFlashDuration = 0;
            }
        }
        if (barrageRumbleTicks > 0) {
            barrageRumbleTicks--;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && !mc.isPaused()) {
            // Tick Gargantua cinematic screen overlays & acoustic events
            com.frierenflight.zoltraakcinematic.client.cinematic.GargantuaScreenFx.clientTick(mc);
            com.frierenflight.zoltraakcinematic.client.cinematic.GargantuaCasterPose.clientTick(mc);
            com.frierenflight.zoltraakcinematic.client.cinematic.CinematicRigController.tick();

            // Gargantua Incantation Cutscene (Act 0: Close-up & Dutch tilt) & Explosion Peak Micro-Tremor
            if (mc.player != null && io.redspace.ironsspellbooks.player.ClientMagicData.isCasting()) {
                String spellId = io.redspace.ironsspellbooks.player.ClientMagicData.getCastingSpellId();
                if (spellId != null && spellId.contains("gargantua")) {
                    if (!com.frierenflight.zoltraakcinematic.client.cinematic.CinematicRigController.isPlaying()) {
                        com.frierenflight.zoltraakcinematic.client.cinematic.GargantuaCutscenes.playIncantation(mc.player, 80);
                    }
                } else if (spellId != null && (spellId.equals("zoltraak_cinematic:explosion") || spellId.endsWith(":explosion"))) {
                    if (io.redspace.ironsspellbooks.player.ClientMagicData.getCastCompletionPercent() > 0.78F) {
                        triggerBarrageMicroRumble();
                    }
                }
            }

            // Allow skipping cinematic cutscenes by pressing Jump (Spacebar)
            if (com.frierenflight.zoltraakcinematic.client.cinematic.CinematicRigController.isPlaying() && mc.options.keyJump.consumeClick()) {
                com.frierenflight.zoltraakcinematic.client.cinematic.CinematicRigController.cancelActive();
                cameraRestoreDelay = 0;
            }

            if (mc.screen == null && mc.player != null) {
                while (KEY_CONFIG.consumeClick()) {
                    mc.setScreen(new com.frierenflight.zoltraakcinematic.client.gui.ZoltraakConfigScreen(null));
                }

                while (KEY_DEFENSE.consumeClick()) {
                    long now = System.currentTimeMillis();
                    long elapsed = now - lastDefensePressTime;

                    int mode;
                    if (mc.player.isCrouching()) {
                        // Sneak + X -> Instant 360-degree Dome shortcut!
                        mode = com.frierenflight.zoltraakcinematic.entity.DefenseBarrierEntity.MODE_DOME;
                        lastDefensePressTime = 0;
                    } else if (elapsed <= 350) {
                        // Double tap X -> 360-degree Geodesic Honeycomb Dome!
                        mode = com.frierenflight.zoltraakcinematic.entity.DefenseBarrierEntity.MODE_DOME;
                        lastDefensePressTime = 0;
                    } else {
                        // Single tap X -> Flat 19-cell Honeycomb Shield in front!
                        mode = com.frierenflight.zoltraakcinematic.entity.DefenseBarrierEntity.MODE_DIRECTIONAL;
                        lastDefensePressTime = now;
                    }

                    com.frierenflight.zoltraakcinematic.platform.Services.PLATFORM.sendDefenseCastToServer(mode);
                }

                while (KEY_FLIGHT.consumeClick()) {
                    com.frierenflight.zoltraakcinematic.platform.Services.PLATFORM.sendFlightToggleToServer();
                }

                // Seamless zero-latency client flight re-engagement when airborne
                if (mc.player.hasEffect(com.frierenflight.zoltraakcinematic.registry.ModCinematicMobEffects.FLIGHT)) {
                    if (!mc.player.onGround() && !mc.player.isInWater() && !mc.player.isInLava() && !mc.player.getAbilities().flying) {
                        mc.player.getAbilities().flying = true;
                    }
                }
            }

            // Automatic Third-Person Perspective Switching for Barrage Casting
            boolean isBarrageCasting = false;
            if (mc.player != null && io.redspace.ironsspellbooks.player.ClientMagicData.isCasting()) {
                String spellId = io.redspace.ironsspellbooks.player.ClientMagicData.getCastingSpellId();
                if (spellId != null && (spellId.endsWith("zoltraak_barrage") || spellId.endsWith("corrupted_barrage"))) {
                    isBarrageCasting = true;
                }
            }

            boolean isCutsceneActive = com.frierenflight.zoltraakcinematic.client.cinematic.CinematicRigController.isPlaying();
            if (isBarrageCasting || isCutsceneActive) {
                if (!forcedThirdPerson) {
                    originalCameraType = mc.options.getCameraType();
                    // Force cinematic third-person back view so player is framed by the 24-circle matrix
                    if (originalCameraType.isFirstPerson()) {
                        mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    }
                    forcedThirdPerson = true;
                }
                cameraRestoreDelay = isBarrageCasting ? 10 : 0; // Keep delay primed only for Barrage fade-out
            } else {
                if (forcedThirdPerson) {
                    if (cameraRestoreDelay > 0) {
                        cameraRestoreDelay--;
                    } else {
                        if (originalCameraType != null) {
                            mc.options.setCameraType(originalCameraType);
                            originalCameraType = null;
                        }
                        forcedThirdPerson = false;
                    }
                }
            }

            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof ZoltraakCinematicBeamEntity beam && !beam.isRemoved()) {
                    int fireTick = beam.mode().charge;
                    int fireWindowEnd = fireTick + Math.min(8, Math.max(2, beam.mode().pulse));
                    if (beam.fired()
                            && beam.tickCount <= fireWindowEnd
                            && beam.markClientFireShockTriggered()) {
                        triggerFireShock(beam, beam.visualOrigin(1.0f));
                    }
                    if (beam.fired()
                            && beam.impact()
                            && beam.mode() != com.frierenflight.zoltraakcinematic.client.renderer.ZoltraakMode.LARGE
                            && beam.age(1.0f) >= beam.impactAge()
                            && beam.markClientImpactShockTriggered()) {
                        triggerImpactShock(beam, beam.end());
                    }
                } else if (entity instanceof com.frierenflight.zoltraakcinematic.entity.ZoltraakBarrageProjectileEntity proj && !proj.isRemoved()) {
                    if (proj.markClientEffectsTriggered()) {
                        Entity shooter = proj.getOwner();
                        if (shooter instanceof net.minecraft.world.entity.LivingEntity living) {
                            com.frierenflight.zoltraakcinematic.client.renderer.ZoltraakBarrageArrayRenderer.onCircleFired(living, proj.getCircleIndex());
                        }
                        if (shooter == mc.player) {
                            triggerBarrageMicroRumble();
                        }
                    }
                } else if (entity instanceof com.frierenflight.zoltraakcinematic.entity.ExplosionMagicEntity exp && !exp.isRemoved()) {
                    float scale = exp.getTimeScale();
                    int detonateTick = exp.getDetonateTick();
                    if (!exp.clientDetonated && exp.tickCount >= Math.round(18.0F * scale) && exp.tickCount < detonateTick) {
                        if (mc.player.position().distanceToSqr(exp.getTargetPos()) < 96.0 * 96.0) {
                            triggerBarrageMicroRumble();
                        }
                    }
                    if (!exp.clientDetonated && exp.tickCount >= detonateTick) {
                        exp.clientDetonated = true;
                        triggerExplosionDetonation(exp);
                    }
                }
            }
        }

        com.frierenflight.zoltraakcinematic.client.renderer.ZoltraakBarrageArrayRenderer.clientTick();
        com.frierenflight.zoltraakcinematic.client.renderer.GargantuaPreviewRenderer.clientTick();
        com.frierenflight.zoltraakcinematic.client.renderer.ExplosionChargeRenderer.clientTick();
    }
}
