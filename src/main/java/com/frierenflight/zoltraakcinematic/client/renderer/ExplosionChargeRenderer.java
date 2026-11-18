// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig;
import com.frierenflight.zoltraakcinematic.entity.ZoltraakCinematicBeamEntity;
import com.frierenflight.zoltraakcinematic.spell.ExplosionSpell;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import java.util.*;

/**
 * ExplosionChargeRenderer
 * Renders the dramatic 5.5s Explosion Magic channeling sequence with radial Gaussian & Iris HDR bloom:
 * - Opening magenta-pink orb flash + expanding floor & torso-height magenta-purple shockwave rings
 * - Ground crimson-gold swirling vortex + soft radial ambient bloom
 * - 12 radial electric cyan-blue mana absorption streams surging from the ground ring into the staff orb
 * - 2 caster overhead crimson rings + 8 stacked target sky vortex rings (+10 blocks lifted) + configurable horizontal sky flare
 * - Configurable blooming golden spark dots (•), 4-color 4-point cross-stars (✦), parabola sky stream & funnel stars
 * - Camera-facing rainbow shockwave ring + configurable golden cross-rays (*) & energy suction trails at staff tip
 */
public final class ExplosionChargeRenderer {

    private record ChargeSettings(
            int goldDots,
            int casterStars,
            int arcParticles,
            int crossRays,
            int suctionLines,
            float skyFlareStrength,
            float particleScale
    ) {
        static ChargeSettings load() {
            return new ChargeSettings(
                    ZoltraakCinematicClientConfig.getExplosionGoldSparks(),
                    ZoltraakCinematicClientConfig.getExplosionFourPointStars(),
                    ZoltraakCinematicClientConfig.getExplosionParabolaStreams(),
                    ZoltraakCinematicClientConfig.getExplosionCrossRays(),
                    ZoltraakCinematicClientConfig.getExplosionSuctionLines(),
                    ZoltraakCinematicClientConfig.getExplosionSkyFlareStrength(),
                    ZoltraakCinematicClientConfig.getExplosionParticleScale()
            );
        }
    }

    private static final class ChargeState {
        int introTicks = 0;
        int lastSeenTick = 0;
        int lastIntroTick = -1;
        float smoothedProgress = 0.0F;
        boolean castingNow = false;
        boolean releasing = false;
        boolean firedRelease = false;
        int outroTicks = 0;
        int outroMaxTicks = 34;
        Vec3 smoothedTarget = null;
        Vec3 anchorCasterWorld = null;
        Vec3 anchorStaffWorld = null;
        Vec3 anchorTargetWorld = null;
    }

    private static final Map<UUID, ChargeState> ACTIVE = new HashMap<>();

    private static PoseStack deferredPose;
    private static Matrix4f deferredModelView;
    private static Matrix4f deferredProjection;
    private static Camera deferredCamera;
    private static float deferredPartial;
    private static final List<LivingEntity> DEFERRED_CASTERS = new ArrayList<>();

    private ExplosionChargeRenderer() {}

    public static void resetState() {
        ACTIVE.clear();
        clearDeferred();
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            resetState();
            return;
        }
        int tick = mc.player != null ? mc.player.tickCount : 0;
        Iterator<Map.Entry<UUID, ChargeState>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            ChargeState s = it.next().getValue();
            if (s.releasing) {
                s.outroTicks++;
                if (s.outroTicks >= s.outroMaxTicks) {
                    s.releasing = false;
                    s.introTicks = 0;
                    s.outroTicks = 0;
                    s.smoothedProgress = 0.0F;
                    s.smoothedTarget = null;
                    s.anchorCasterWorld = null;
                    s.anchorStaffWorld = null;
                    s.anchorTargetWorld = null;
                }
            } else if (!s.castingNow && s.introTicks > 0) {
                s.releasing = true;
                s.firedRelease = (s.smoothedProgress >= 0.72F);
                s.outroTicks = 0;
                s.outroMaxTicks = s.firedRelease ? 44 : 22;
            }
            if (tick - s.lastSeenTick > 10 && s.introTicks <= 0 && !s.releasing) {
                it.remove();
            }
        }
    }

    public static boolean hasDeferredDraw() {
        return deferredPose != null && !DEFERRED_CASTERS.isEmpty();
    }

    public static void clearDeferred() {
        deferredPose = null;
        deferredModelView = null;
        deferredProjection = null;
        deferredCamera = null;
        DEFERRED_CASTERS.clear();
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        int gameTick = mc.player != null ? mc.player.tickCount : 0;
        List<LivingEntity> candidates = new ArrayList<>();
        boolean anyActive = false;

        Vec3 camOrigin = event.getCamera() != null ? event.getCamera().getPosition()
                : (mc.player != null ? mc.player.position() : Vec3.ZERO);
        for (var ent : mc.level.entitiesForRendering()) {
            if (!(ent instanceof LivingEntity living)) continue;
            if (living != mc.player
                    && living.distanceToSqr(camOrigin) > 512.0 * 512.0
                    && (mc.player == null || living.distanceToSqr(mc.player) > 512.0 * 512.0)) {
                continue;
            }
            candidates.add(living);

            boolean isCastingExplosion = false;
            float progress = 0.0F;
            if (living == mc.player) {
                if (ClientMagicData.isCasting() && isExplosionSpellId(ClientMagicData.getCastingSpellId())) {
                    isCastingExplosion = true;
                    progress = Mth.clamp(ClientMagicData.getCastCompletionPercent(), 0.0F, 1.0F);
                }
            } else {
                SyncedSpellData synced = ClientMagicData.getSyncedSpellData(living);
                if (synced != null && synced.isCasting() && isExplosionSpellId(synced.getCastingSpellId())) {
                    isCastingExplosion = true;
                    progress = 0.5F;
                }
            }

            ChargeState state = ACTIVE.computeIfAbsent(living.getUUID(), k -> new ChargeState());
            state.lastSeenTick = gameTick;
            state.castingNow = isCastingExplosion;

            if (isCastingExplosion) {
                if (state.releasing) {
                    state.releasing = false;
                    state.outroTicks = 0;
                }
                if (state.lastIntroTick != gameTick) {
                    state.lastIntroTick = gameTick;
                    if (state.introTicks < 6) state.introTicks++;
                }
                state.smoothedProgress = living == mc.player
                        ? progress
                        : Mth.clamp(state.smoothedProgress + 0.014F, 0.0F, 0.98F);
                Vec3 rawTarget = ExplosionSpell.computeTargetPosition(mc.level, living, partial, ExplosionSpell.CAST_RANGE);
                state.smoothedTarget = state.smoothedTarget == null ? rawTarget : state.smoothedTarget.lerp(rawTarget, 0.25);
                state.anchorCasterWorld = living.getPosition(partial);
                state.anchorStaffWorld = ZoltraakCinematicBeamEntity.getStaffAnchorPos(living, partial);
                state.anchorTargetWorld = state.smoothedTarget;
            } else if (!state.releasing && state.introTicks > 0) {
                state.releasing = true;
                state.firedRelease = (state.smoothedProgress >= 0.72F);
                state.outroTicks = 0;
                state.outroMaxTicks = state.firedRelease ? 44 : 22;
                if (state.anchorCasterWorld == null) {
                    state.anchorCasterWorld = living.getPosition(partial);
                    state.anchorStaffWorld = ZoltraakCinematicBeamEntity.getStaffAnchorPos(living, partial);
                    state.anchorTargetWorld = state.smoothedTarget != null ? state.smoothedTarget
                            : ExplosionSpell.computeTargetPosition(mc.level, living, partial, ExplosionSpell.CAST_RANGE);
                }
            }

            if (state.introTicks > 0 || state.releasing) {
                anyActive = true;
            }
        }

        if (!anyActive) {
            clearDeferred();
            return;
        }

        if (ShaderCompatibility.useFullDetailPass()) {
            if (ShaderCompatibility.isShadowPass()) return;
            deferredPose = new PoseStack();
            deferredPose.last().pose().set(event.getPoseStack().last().pose());
            deferredPose.last().normal().set(event.getPoseStack().last().normal());
            deferredModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            deferredProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            deferredCamera = event.getCamera();
            deferredPartial = partial;
            DEFERRED_CASTERS.clear();
            DEFERRED_CASTERS.addAll(candidates);
            return;
        }

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        renderInternal(event.getPoseStack(), buffers, event.getCamera(), partial, candidates, false);
    }

    public static void renderIrisPhase1(MultiBufferSource.BufferSource buffers, Matrix4fStack modelView) {
        if (!hasDeferredDraw()) return;
        float glow = ZoltraakCinematicClientConfig.getExplosionGlow();
        if (glow <= 0.004F) return;

        modelView.set(deferredModelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(deferredProjection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        ZoltraakRenderPass.renderIrisScaled(buffers, glow, () -> {
            renderInternal(deferredPose, buffers, deferredCamera, deferredPartial, DEFERRED_CASTERS, true);
            ZoltraakRenderTypes.endIrisBatch(buffers, ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
        });
    }

    public static void renderDeferred(MultiBufferSource.BufferSource buffers, Matrix4fStack modelView) {
        if (!hasDeferredDraw()) return;
        modelView.set(deferredModelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(deferredProjection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
        renderInternal(deferredPose, buffers, deferredCamera, deferredPartial, DEFERRED_CASTERS, false);
    }

    private static boolean isExplosionSpellId(String spellId) {
        return spellId != null && (spellId.equals("zoltraak_cinematic:explosion") || spellId.endsWith(":explosion"));
    }

    private static void renderInternal(
            PoseStack pose, MultiBufferSource.BufferSource buffers,
            Camera camera, float partial, List<LivingEntity> casters, boolean irisBloomProxy
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float bloom = ZoltraakCinematicClientConfig.getExplosionGlow();
        if (bloom <= 0.004F) return;

        Vec3 camPos = camera.getPosition();
        Vec3 camRight = new Vec3(new Vector3f(1.0F, 0.0F, 0.0F).rotate(mc.getEntityRenderDispatcher().cameraOrientation()));
        Vec3 camUp = new Vec3(new Vector3f(0.0F, 1.0F, 0.0F).rotate(mc.getEntityRenderDispatcher().cameraOrientation()));
        double radius = ZoltraakCinematicConfig.getExplosionRadius();
        int gameTick = mc.player != null ? mc.player.tickCount : 0;
        float time = gameTick + partial;
        ChargeSettings cfg = ChargeSettings.load();

        pose.pushPose();
        Matrix4f mat = pose.last().pose();

        try {
            for (LivingEntity caster : casters) {
                ChargeState state = ACTIVE.get(caster.getUUID());
                if (state == null || (state.introTicks <= 0 && !state.releasing)) continue;

                boolean releasing = state.releasing;
                boolean firedRelease = releasing && state.firedRelease;
                float outroT = releasing ? Mth.clamp(state.outroTicks + partial, 0.0F, state.outroMaxTicks) : 0.0F;
                float op = releasing ? Mth.clamp(outroT / Math.max(1, state.outroMaxTicks), 0.0F, 1.0F) : 0.0F;
                float fade = releasing
                        ? (1.0F - ExplosionMagicRenderer.smoothstep(op))
                        : ExplosionMagicRenderer.smoothstep(state.introTicks / 6.0F);
                if (fade <= 0.008F) continue;

                float p = firedRelease ? 1.0F : state.smoothedProgress;
                boolean isFpLocal = caster == mc.player && mc.getCameraEntity() == mc.player
                        && mc.options.getCameraType().isFirstPerson();

                Vec3 casterWorld = releasing && state.anchorCasterWorld != null ? state.anchorCasterWorld : caster.getPosition(partial);
                Vec3 staffWorld = releasing && state.anchorStaffWorld != null ? state.anchorStaffWorld : ZoltraakCinematicBeamEntity.getStaffAnchorPos(caster, partial);
                Vec3 targetWorld = releasing && state.anchorTargetWorld != null
                        ? state.anchorTargetWorld
                        : (state.smoothedTarget != null ? state.smoothedTarget
                        : ExplosionSpell.computeTargetPosition(mc.level, caster, partial, ExplosionSpell.CAST_RANGE));

                Vec3 casterPos = casterWorld.subtract(camPos);
                Vec3 staffRel = staffWorld.subtract(camPos);
                Vec3 targetRel = targetWorld.subtract(camPos);

                if (irisBloomProxy) {
                    emitIrisChargeProxy(buffers, mat, casterPos, staffRel, targetRel, camRight, camUp, radius, p, time, fade, releasing, firedRelease, outroT, op, isFpLocal, cfg);
                    continue;
                }

                boolean shadersActive = ShaderCompatibility.useFullDetailPass() && ZoltraakCinematicClientConfig.isWorldGlowActive(true);
                float shaderHaloScale = shadersActive ? 0.68F : 1.0F;
                float fpDamp = isFpLocal ? 0.45F : 1.0F;

                // =========================================================================
                // PASS 1: EXPLOSION_VORTEX_RING (Modes 0..5 — True Soft Radial & Volumetric Bloom!)
                // =========================================================================
                VertexConsumer vortexVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_VORTEX_RING);

                double groundExpand = releasing ? (1.0 + (firedRelease ? 0.16 : 0.08) * ExplosionMagicRenderer.smoothstep(op / 0.72F)) : 1.0;
                double groundR = (3.2 + 4.4 * ExplosionMagicRenderer.smoothstep(p / 0.42F)) * (isFpLocal ? 0.85 : 1.0) * groundExpand;
                float gAlpha = fade * (isFpLocal ? 0.72F : 0.96F);

                // 1a. Pure Radial Gaussian Bloom Floor & Fountain Glow (Mode 5, seed=0.10) — zero crossbars!
                ExplosionMagicRenderer.emitHorizDiscQuad(
                        vortexVc, mat, casterPos.add(0.0, 0.04, 0.0),
                        groundR * 1.32, 0.0, 0.56F, 0.10F, 0.98F, fade * (isFpLocal ? 0.14F : 0.26F) * shaderHaloScale
                );
                ExplosionMagicRenderer.emitBillboardQuad(
                        vortexVc, mat, casterPos.add(0.0, 2.0, 0.0), camRight, camUp,
                        groundR * 0.72, 2.2, 0.26F, 0.10F, 0.98F, fade * (isFpLocal ? 0.12F : 0.22F) * shaderHaloScale * (releasing ? 0.65F : 1.0F)
                );

                // 1b. Opening Blinding Magenta-Pink Orb Flash + Floor & Torso Magenta-Violet Shockwave Rings
                if (!releasing && p < 0.28F) {
                    float openP = p / 0.28F;
                    float openFlash = (float) Math.exp(-openP * 2.6) * fade;
                    double burstR = (1.8 + 2.2 * (1.0 - openP)) * (isFpLocal ? 0.28 : 1.0);
                    ExplosionMagicRenderer.emitBillboardQuad(
                            vortexVc, mat, staffRel, camRight, camUp, burstR, burstR,
                            0.14F, 0.10F, 0.98F, openFlash * (isFpLocal ? 0.36F : 0.82F) * (shadersActive ? 0.65F : 1.0F)
                    );
                    double purpleFloorR = (1.6 + 6.2 * ExplosionMagicRenderer.smoothstep(openP)) * (isFpLocal ? 0.80 : 1.0);
                    float ringFade = (1.0F - openP) * fade * (isFpLocal ? 0.58F : 0.82F);
                    ExplosionMagicRenderer.emitHorizDiscQuad(
                            vortexVc, mat, casterPos.add(0.0, 0.12, 0.0),
                            purpleFloorR, 0.0, 0.08F, 0.50F, 0.98F, ringFade
                    );
                    // Torso-level sweeping purple-violet ring
                    double torsoR = (1.4 + 4.4 * ExplosionMagicRenderer.smoothstep(openP)) * (isFpLocal ? 0.72 : 1.0);
                    ExplosionMagicRenderer.emitHorizDiscQuad(
                            vortexVc, mat, casterPos.add(0.0, 1.55, 0.0),
                            torsoR, 0.0, 0.10F, 0.50F, 0.98F, ringFade * 0.72F
                    );
                }

                // Expanding Crimson-Gold Ground Discharge Shockwave Ripple when Explosion releases cleanly
                if (firedRelease && outroT < 20.0F) {
                    float rpWave = outroT / 20.0F;
                    float waveAlpha = (1.0F - ExplosionMagicRenderer.smoothstep(rpWave)) * (isFpLocal ? 0.45F : 0.84F);
                    double shockR = groundR * (0.42 + 1.22 * ExplosionMagicRenderer.smoothstep(rpWave));
                    ExplosionMagicRenderer.emitHorizDiscQuad(
                            vortexVc, mat, casterPos.add(0.0, 0.12, 0.0),
                            shockR, 0.0, 0.68F, 0.50F, 0.98F, waveAlpha
                    );
                }

                // 1c. Ground crimson-gold swirling vortex beneath caster (Mode 0) - spins faster as full charge nears
                float peakPressure = releasing ? 0.0F : ExplosionMagicRenderer.smoothstep((p - 0.68F) / 0.32F);
                ExplosionMagicRenderer.emitHorizDiscQuad(
                        vortexVc, mat, casterPos.add(0.0, 0.07, 0.0),
                        groundR, -time * (releasing ? 0.032 : (0.055 + 0.048 * peakPressure)), p, 0.21F, 0.0F, gAlpha
                );
                // Critical overload golden-crimson pulse ring sweeping out when approaching full charge
                if (!releasing && p > 0.72F) {
                    float pulsePhase = ((p - 0.72F) / 0.28F);
                    float pulseA = (float) Math.sin(pulsePhase * Math.PI) * fade * (isFpLocal ? 0.32F : 0.62F);
                    ExplosionMagicRenderer.emitHorizDiscQuad(
                            vortexVc, mat, casterPos.add(0.0, 0.10, 0.0),
                            groundR * (0.55 + 0.68 * pulsePhase), 0.0, 0.78F, 0.50F, 0.98F, pulseA
                    );
                }

                // 1d. Two smaller crimson vortex rings hovering directly above the caster (Mode 1)
                double ringLift = releasing ? 0.65 * op : 0.0;
                double ringWiden = releasing ? (1.0 + 0.12 * op) : 1.0;
                if (p > 0.10F) {
                    float cRing1 = ExplosionMagicRenderer.smoothstep((p - 0.10F) / 0.18F) * fade;
                    ExplosionMagicRenderer.emitHorizDiscQuad(
                            vortexVc, mat, casterPos.add(0.0, 4.4 + ringLift * 0.7, 0.0),
                            2.5 * ringWiden * (releasing ? 1.0 : cRing1), time * (releasing ? 0.038 : (0.065 + 0.050 * peakPressure)), p, 0.37F, 0.5F, cRing1 * 0.92F
                    );
                }
                if (p > 0.20F) {
                    float cRing2 = ExplosionMagicRenderer.smoothstep((p - 0.20F) / 0.20F) * fade;
                    ExplosionMagicRenderer.emitHorizDiscQuad(
                            vortexVc, mat, casterPos.add(0.0, 7.8 + ringLift, 0.0),
                            4.2 * ringWiden * (releasing ? 1.0 : cRing2), -time * (releasing ? 0.030 : (0.052 + 0.045 * peakPressure)), p, 0.61F, 0.5F, cRing2 * 0.95F
                    );
                }

                // 1e. 8 Stacked Fiery Sky Vortex Rings + Gathering Pre-Ignition Core (skipped during outro if fired)
                if (!firedRelease && p > 0.12F) {
                    float skyScale = 1.0F - 0.10F * ExplosionMagicRenderer.smoothstep((p - 0.72F) / 0.28F);
                    double skySpin = time * (0.055 + 0.065 * peakPressure);
                    ExplosionMagicRenderer.emitSkyVortexColumn(
                            vortexVc, mat, targetRel, radius,
                            p, skyScale, fade * 0.98F, 0.42F, skySpin
                    );
                    if (p > 0.54F) {
                        float coreP = ExplosionMagicRenderer.smoothstep((p - 0.54F) / 0.46F);
                        Vec3 apexCenter = targetRel.add(0.0, ExplosionMagicRenderer.getSkyApexHeight(radius), 0.0);
                        double coreR = radius * (0.14 + 0.24 * coreP);
                        ExplosionMagicRenderer.emitBillboardQuad(
                                vortexVc, mat, apexCenter, camRight, camUp, coreR, coreR,
                                0.82F + 0.14F * coreP, 0.10F, 0.98F, fade * coreP * (shadersActive ? 0.68F : 0.88F)
                        );
                    }
                    float skyBloomA = ExplosionMagicRenderer.smoothstep((p - 0.12F) / 0.25F) * fade * (shadersActive ? 0.16F : 0.20F);
                    if (skyBloomA > 0.008F) {
                        int nRings = ExplosionMagicRenderer.SKY_RING_HEIGHT_FACTORS.length;
                        for (int i = 0; i < nRings; i++) {
                            double ry = ExplosionMagicRenderer.getSkyRingY(radius, i);
                            double rr = radius * ExplosionMagicRenderer.SKY_RING_SCALE_FACTORS[i] * 1.15 * skyScale;
                            ExplosionMagicRenderer.emitHorizDiscQuad(
                                    vortexVc, mat, targetRel.add(0.0, ry, 0.0), rr, 0.0,
                                    0.48F, 0.10F, 0.98F, skyBloomA * (0.50F + 0.50F * (i / (float) Math.max(1, nRings - 1)))
                            );
                        }
                    }
                }

                // 1f. Radial Inward-Converging Electric Cyan-Blue Mana Streams rushing into the Staff Orb (Mode 3)
                if (!releasing && (!isFpLocal || p > 0.08F) && p < 0.74F) {
                    emitManaAbsorptionStreams(
                            vortexVc, mat, casterPos, staffRel, camRight,
                            p, time, fade * (isFpLocal ? 0.42F : 0.90F), isFpLocal, false
                    );
                }

                // 1g. Vivid Magenta-Pink Staff Orb Multi-Layer Radial Bloom Corona (Mode 5, radial glow)
                float orbFade = releasing ? (1.0F - ExplosionMagicRenderer.smoothstep(outroT / (firedRelease ? 15.0F : 12.0F))) : fade;
                if (orbFade > 0.015F) {
                    double orbExpand = releasing ? (1.0 + 0.42 * ExplosionMagicRenderer.smoothstep(outroT / 10.0F)) : 1.0;
                    double orbSize = (0.54 + 0.68 * Math.pow(p, 1.1)) * (isFpLocal ? 0.34 : 1.0) * orbExpand;
                    ExplosionMagicRenderer.emitBillboardQuad(
                            vortexVc, mat, staffRel, camRight, camUp, orbSize * 2.05, orbSize * 2.05,
                            releasing && firedRelease ? 0.68F : 0.08F, 0.10F, 0.98F,
                            orbFade * (0.44F + 0.18F * p) * fpDamp * (shadersActive ? 0.85F : 1.0F)
                    );
                    ExplosionMagicRenderer.emitBillboardQuad(
                            vortexVc, mat, staffRel, camRight, camUp, orbSize * 1.15, orbSize * 1.15,
                            0.15F, 0.10F, 0.98F, orbFade * (0.72F + 0.14F * p) * (isFpLocal ? 0.60F : 1.0F)
                    );
                    ExplosionMagicRenderer.emitBillboardQuad(
                            vortexVc, mat, staffRel, camRight, camUp, orbSize * 0.52, orbSize * 0.52,
                            0.98F, 0.10F, 0.98F, orbFade * (isFpLocal ? 0.78F : 0.96F)
                    );
                }

                // 1h. Rainbow Shockwave Circle + Configurable Golden Converging Cross-Rays (*) + Suction Trails
                boolean activeRainbow = (!releasing && p > 0.56F) || (firedRelease && outroT < 16.0F);
                if (activeRainbow) {
                    float rp = Mth.clamp((p - 0.56F) / 0.42F, 0.0F, 1.0F);
                    float rEnv = releasing
                            ? (1.0F - ExplosionMagicRenderer.smoothstep(outroT / 16.0F)) * 0.68F
                            : (float) Math.sin(rp * Math.PI) * fade;
                    double rExpand = releasing ? (1.0 + 0.78 * ExplosionMagicRenderer.smoothstep(outroT / 16.0F)) : 1.0;
                    double staffRingR = (1.8 + 2.1 * Math.sin(rp * Math.PI * 0.78)) * (isFpLocal ? 0.52 : 1.0) * rExpand;
                    ExplosionMagicRenderer.emitBillboardQuad(
                            vortexVc, mat, staffRel, camRight, camUp,
                            staffRingR, staffRingR, p, 0.67F, 0.72F, rEnv * 0.94F
                    );

                    float rayFade = releasing ? (1.0F - ExplosionMagicRenderer.smoothstep(outroT / 11.0F)) * 0.75F : rEnv;
                    if (cfg.crossRays() > 0 && rayFade > 0.02F) {
                        double baseRayLen = staffRingR * (isFpLocal ? 0.88 : 1.38) * cfg.particleScale();
                        double baseRayW = (isFpLocal ? 0.07 : 0.17) * rayFade * (0.7 + 0.3 * cfg.particleScale());
                        for (int k = 0; k < cfg.crossRays(); k++) {
                            double ang = k * (Math.PI / cfg.crossRays()) + 0.32 + time * 0.022;
                            double lenMult = (cfg.crossRays() > 2 && k % 2 == 1) ? 0.78 : 1.05;
                            double wMult = (cfg.crossRays() > 2 && k % 2 == 1) ? 0.76 : 1.05;
                            Vec3 rayDir = camRight.scale(Math.cos(ang)).add(camUp.scale(Math.sin(ang)));
                            ExplosionMagicRenderer.emitSoftBloomBeamQuad(
                                    vortexVc, mat,
                                    staffRel.subtract(rayDir.scale(baseRayLen * lenMult)),
                                    staffRel.add(rayDir.scale(baseRayLen * lenMult)),
                                    Vec3.ZERO, baseRayW * wMult, 0.35F, 0.85F, rayFade * (isFpLocal ? 0.48F : 0.88F)
                            );
                        }
                    }

                    if (!releasing) {
                        int suctionCount = cfg.suctionLines();
                        for (int s = 0; s < suctionCount; s++) {
                            float sh = ExplosionMagicRenderer.hashFloat(s * 31 + 5);
                            float sh2 = ExplosionMagicRenderer.hashFloat(s * 53 + 17);
                            float sCyc = (time * (0.058F + 0.020F * sh2) + s / (float) suctionCount) % 1.0F;
                            double sAng = s * (Math.PI * 2.0 / suctionCount) + sh * 0.42;
                            double rOut = staffRingR * ((1.52 + 0.32 * sh) * (1.0 - 0.86 * sCyc));
                            Vec3 dir2D = camRight.scale(Math.cos(sAng)).add(camUp.scale(Math.sin(sAng)));
                            double trailSpan = (0.72 + 0.42 * sh2) * cfg.particleScale();
                            Vec3 tailP = staffRel.add(dir2D.scale(rOut + trailSpan));
                            Vec3 headP = staffRel.add(dir2D.scale(Math.max(0.14, rOut - 0.30)));
                            float sAlpha = rEnv * (float) Math.sin(sCyc * Math.PI) * 0.88F;
                            ExplosionMagicRenderer.emitSoftBloomBeamQuad(
                                    vortexVc, mat, tailP, headP, Vec3.ZERO, 0.10 * cfg.particleScale(), 0.25F, 0.85F, sAlpha
                            );
                        }
                    }
                }

                // Buoyant drift offsets so embers & 4-point stars drift upward & fade softly on finish/cancel
                double outroRise = releasing ? outroT * 0.14 : 0.0;
                double outroSpread = releasing ? (1.0 + outroT * 0.015) : 1.0;

                // 1i. Configurable Rising Golden-Yellow & Orange Blooming Round Spark Dots (•) around caster (Mode 5)
                int goldDots = cfg.goldDots();
                for (int i = 0; i < goldDots; i++) {
                    float h1 = ExplosionMagicRenderer.hashFloat(i * 23 + 3);
                    float h2 = ExplosionMagicRenderer.hashFloat(i * 47 + 11);
                    float h3 = ExplosionMagicRenderer.hashFloat(i * 61 + 29);
                    float cyc = (time * (0.015F + 0.016F * h1) + i / (float) goldDots) % 1.0F;
                    double ang = i * 2.39996 + time * (0.022 + 0.012 * h3) + h2 * 2.0;
                    double rOrb = ((isFpLocal ? 1.5 : 0.7) + (isFpLocal ? 2.6 : 4.6) * Math.sqrt(h2)) * outroSpread;
                    double dy = 0.10 + cyc * (5.2 + 1.8 * p) + outroRise * (0.65 + 0.7 * h1);
                    Vec3 dotPos = casterPos.add(Math.cos(ang) * rOrb, dy, Math.sin(ang) * rOrb);
                    float dotA = fade * (float) Math.sin(cyc * Math.PI) * (0.72F + 0.28F * p);
                    if (dotA <= 0.02F) continue;
                    double dotSz = (0.11 + 0.13 * h1) * cfg.particleScale();
                    ExplosionMagicRenderer.emitBillboardQuad(
                            vortexVc, mat, dotPos, camRight, camUp, dotSz, dotSz,
                            0.64F + 0.20F * h2, 0.10F, 0.98F, dotA
                    );
                }

                // =========================================================================
                // PASS 2: Blooming 4-Point Cross-Stars (✦) -> Mode 5 Bloom Halo + LIGHT Star Mesh
                // =========================================================================
                VertexConsumer starVc = buffers.getBuffer(ZoltraakRenderTypes.LIGHT);

                int casterStars = cfg.casterStars();
                for (int i = 0; i < casterStars; i++) {
                    float spSeed = ExplosionMagicRenderer.hashFloat(i * 41 + 9);
                    float spBand = ExplosionMagicRenderer.hashFloat(i * 73 + 13);
                    float cycle = (time * (0.018F + 0.014F * spSeed) + i / (float) casterStars) % 1.0F;
                    double ang = i * 2.399 + time * (0.036 + 0.010 * spBand);
                    double rOrbit = (isFpLocal ? 1.8 : 3.8) * (0.30 + 0.82 * (1.0 - cycle * 0.45)) * outroSpread;
                    double sy = 0.20 + cycle * 5.2 + outroRise * (0.65 + 0.7 * spSeed);
                    Vec3 spPos = casterPos.add(Math.cos(ang) * rOrbit, sy, Math.sin(ang) * rOrbit);
                    float glintAlpha = fade * (float) Math.sin(cycle * Math.PI) * 0.95F;
                    if (glintAlpha <= 0.02F) continue;
                    double spSize = (0.16 + 0.16 * spSeed) * cfg.particleScale() * (0.68 + 0.42 * (float) Math.sin(time * 0.25 + i));
                    emitBloomingFourPointStar(starVc, vortexVc, mat, spPos, camRight, camUp, spSize, i % 4, glintAlpha);
                }

                // Parabolic Spark & Star Stream Arcing from Caster UP to Target Sky Funnel
                float arcEnv = releasing ? Math.max(0.0F, 1.0F - ExplosionMagicRenderer.smoothstep(outroT / 16.0F)) : 1.0F;
                if (p > 0.16F && arcEnv > 0.02F) {
                    float arcFade = ExplosionMagicRenderer.smoothstep((p - 0.16F) / 0.20F) * fade * arcEnv;
                    Vec3 topFunnel = targetRel.add(0.0, ExplosionMagicRenderer.getSkyApexHeight(radius), 0.0);
                    float arcHeightBoost = 0.70F + 0.30F * ZoltraakCinematicClientConfig.getExplosionSkyHeight();
                    int arcParticles = cfg.arcParticles();
                    for (int i = 0; i < arcParticles; i++) {
                        float h = ExplosionMagicRenderer.hashFloat(i * 71 + 19);
                        float hSide = ExplosionMagicRenderer.hashFloat(i * 89 + 31);
                        float u = (time * (0.025F + 0.010F * hSide) + i / (float) arcParticles) % 1.0F;
                        Vec3 baseLine = staffRel.lerp(topFunnel, u);
                        double arcLift = Math.sin(u * Math.PI) * (5.6 + 4.2 * h) * arcHeightBoost + outroRise * 0.45;
                        double sideSpread = Math.sin(u * Math.PI * 2.0 + i * 0.9) * (0.9 + 1.6 * hSide);
                        Vec3 arcPos = baseLine.add(camRight.scale(sideSpread)).add(0.0, arcLift, 0.0);
                        float pa = arcFade * (float) Math.sin(u * Math.PI) * 0.92F;
                        if (i % 3 == 0) {
                            emitBloomingFourPointStar(starVc, vortexVc, mat, arcPos, camRight, camUp, (0.18 + 0.14 * h) * cfg.particleScale(), i % 4, pa);
                        } else {
                            double gSz = (0.16 + 0.12 * h) * cfg.particleScale();
                            ExplosionMagicRenderer.emitBillboardQuad(
                                    vortexVc, mat, arcPos, camRight, camUp, gSz, gSz,
                                    0.72F + 0.10F * hSide, 0.10F, 0.98F, pa
                            );
                        }
                    }
                }

                // Swirling Funnel Embers & Multi-Colored 4-Point Stars Inside Target Sky Funnel
                if (p > 0.14F && (!releasing || !firedRelease)) {
                    float funnelFade = ExplosionMagicRenderer.smoothstep((p - 0.14F) / 0.22F) * fade;
                    int funnelCount = Math.max(24, Math.round(cfg.arcParticles() * 1.15F));
                    double maxH = ExplosionMagicRenderer.getSkyApexHeight(radius);
                    double minH = Math.max(4.0, ExplosionMagicRenderer.getSkyRingY(radius, 0) * 0.56);
                    for (int i = 0; i < funnelCount; i++) {
                        float h = ExplosionMagicRenderer.hashFloat(i * 59 + 17);
                        float cyc = (time * (0.018F + 0.012F * h) + i / (float) funnelCount) % 1.0F;
                        double fy = minH + cyc * (maxH - minH);
                        double funnelR = radius * (0.08 + 0.64 * Math.pow(cyc, 1.32)) * (0.55 + 0.55 * h);
                        double ang = i * 2.1 + time * 0.075 - cyc * 4.2;
                        Vec3 fPos = targetRel.add(Math.cos(ang) * funnelR, fy, Math.sin(ang) * funnelR);
                        float sa = funnelFade * (float) Math.sin(cyc * Math.PI) * 0.92F;
                        double fSize = (0.22 + 0.24 * h) * cfg.particleScale();
                        emitBloomingFourPointStar(starVc, vortexVc, mat, fPos, camRight, camUp, fSize, i % 5, sa);
                    }
                }

                float batchBloom = shadersActive ? bloom * 0.86F : bloom;
                ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_VORTEX_RING, batchBloom);
                ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.LIGHT, Math.max(0.45F, batchBloom));

                // =========================================================================
                // PASS 3: ZOL_GLOW (Configurable Anamorphic Horizontal Sky Flare across Target Rings)
                // =========================================================================
                if (!firedRelease && p > 0.42F && cfg.skyFlareStrength() > 0.01F && ZoltraakCinematicClientConfig.isLensFlaresEnabled()) {
                    float flareStr = cfg.skyFlareStrength();
                    float glintP = ExplosionMagicRenderer.smoothstep((p - 0.42F) / 0.24F) * fade;
                    VertexConsumer flareVc = buffers.getBuffer(ZoltraakRenderTypes.ZOL_GLOW);
                    Vec3 topRingCenter = targetRel.add(0.0, ExplosionMagicRenderer.getSkyApexHeight(radius), 0.0);
                    double glintW = radius * (1.24 * Math.max(0.35, Math.sqrt(flareStr)));
                    double glintH = 1.45 * (0.65 + 0.35 * flareStr);
                    float outerA = Mth.clamp(glintP * Math.min(1.35F, flareStr) * (shadersActive ? 0.56F : 0.82F), 0.0F, 0.96F);
                    float coreA = Mth.clamp(glintP * Math.min(1.50F, flareStr) * (shadersActive ? 0.68F : 0.92F), 0.0F, 0.98F);
                    ExplosionMagicRenderer.emitBillboardQuad(
                            flareVc, mat, topRingCenter, camRight, camUp, glintW, glintH,
                            1.0F, 0.82F, 0.30F, outerA
                    );
                    ExplosionMagicRenderer.emitBillboardQuad(
                            flareVc, mat, topRingCenter, camRight, camUp, glintW * 0.82, glintH * 0.38,
                            1.0F, 0.96F, 0.72F, coreA
                    );
                    ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.ZOL_GLOW, batchBloom);
                }
            }
        } finally {
            pose.popPose();
        }
    }

    private static void emitIrisChargeProxy(
            MultiBufferSource.BufferSource buffers, Matrix4f mat,
            Vec3 casterPos, Vec3 staffRel, Vec3 targetRel, Vec3 camRight, Vec3 camUp,
            double radius, float p, float time, float fade,
            boolean releasing, boolean firedRelease, float outroT, float op,
            boolean isFpLocal, ChargeSettings cfg
    ) {
        VertexConsumer irisVc = buffers.getBuffer(ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
        Vec3 axU = new Vec3(1.0, 0.0, 0.0);
        Vec3 axV = new Vec3(0.0, 0.0, 1.0);

        // Emit crisp rim-focused HDR proxies so Complementary/Iris bloom radiates cleanly around sharp details without pancake fill or FP whiteout
        double groundExpand = releasing ? (1.0 + (firedRelease ? 0.16 : 0.08) * ExplosionMagicRenderer.smoothstep(op / 0.72F)) : 1.0;
        double groundR = (3.2 + 4.4 * ExplosionMagicRenderer.smoothstep(p / 0.42F)) * (isFpLocal ? 0.85 : 1.0) * groundExpand;
        float groundA = fade * (isFpLocal ? 0.20F : 0.62F);
        float gg = ZoltraakRenderPass.irisFadeGuard(groundA);
        ZoltraakRenderPass.emitFeatheredRing(
                irisVc, mat, casterPos.add(0.0, 0.10, 0.0), axU, axV,
                groundR * 0.78, groundR * 0.87, groundR * 0.96, 28,
                1.00F * gg, 0.46F * gg, 0.08F * gg, groundA
        );
        ZoltraakRenderPass.emitFeatheredRing(
                irisVc, mat, casterPos.add(0.0, 0.11, 0.0), axU, axV,
                groundR * 0.46, groundR * 0.54, groundR * 0.62, 24,
                0.98F * gg, 0.28F * gg, 0.05F * gg, groundA * 0.75F
        );
        if (firedRelease && outroT < 20.0F) {
            float rpWave = outroT / 20.0F;
            float waveAlpha = (1.0F - ExplosionMagicRenderer.smoothstep(rpWave)) * (isFpLocal ? 0.20F : 0.54F);
            float wg = ZoltraakRenderPass.irisFadeGuard(waveAlpha);
            double shockR = groundR * (0.42 + 1.22 * ExplosionMagicRenderer.smoothstep(rpWave));
            ZoltraakRenderPass.emitFeatheredRing(
                    irisVc, mat, casterPos.add(0.0, 0.12, 0.0), axU, axV,
                    shockR * 0.80, shockR * 0.90, shockR * 1.00, 26,
                    1.00F * wg, 0.62F * wg, 0.16F * wg, waveAlpha
            );
        }

        // Caster overhead thin rim rings
        double ringLift = releasing ? 0.65 * op : 0.0;
        double ringWiden = releasing ? (1.0 + 0.12 * op) : 1.0;
        if (p > 0.14F) {
            float ca = fade * (isFpLocal ? 0.18F : 0.56F);
            float cg = ZoltraakRenderPass.irisFadeGuard(ca);
            ZoltraakRenderPass.emitFeatheredRing(irisVc, mat, casterPos.add(0.0, 4.4 + ringLift * 0.7, 0.0), axU, axV, 1.85 * ringWiden, 2.20 * ringWiden, 2.55 * ringWiden, 22, 1.00F * cg, 0.44F * cg, 0.08F * cg, ca);
            ZoltraakRenderPass.emitFeatheredRing(irisVc, mat, casterPos.add(0.0, 7.8 + ringLift, 0.0), axU, axV, 3.25 * ringWiden, 3.85 * ringWiden, 4.45 * ringWiden, 22, 1.00F * cg, 0.44F * cg, 0.08F * cg, ca);
        }

        // Electric cyan-blue mana streams converging cleanly into staff orb (matches Phase 2 packet timing)
        if (!releasing && (!isFpLocal || p > 0.10F) && p < 0.74F) {
            emitManaAbsorptionStreams(
                    irisVc, mat, casterPos, staffRel, camRight,
                    p, time, fade * (isFpLocal ? 0.14F : 0.56F), isFpLocal, true
            );
        }

        // Target sky vortex column crisp thin double rim glow (skipped during outro when fired because ExplosionEntity takes over)
        if (!firedRelease && p > 0.14F) {
            float skyAlpha = ExplosionMagicRenderer.smoothstep((p - 0.14F) / 0.24F) * fade * 0.58F;
            float g = ZoltraakRenderPass.irisFadeGuard(skyAlpha);
            double[] sRatio = ExplosionMagicRenderer.SKY_RING_SCALE_FACTORS;
            int ringCount = sRatio.length;
            for (int i = 0; i < ringCount; i++) {
                double ry = ExplosionMagicRenderer.getSkyRingY(radius, i);
                double rr = radius * sRatio[i];
                float ringA = skyAlpha * (0.55F + 0.45F * (i / (float) Math.max(1, ringCount - 1)));
                ZoltraakRenderPass.emitFeatheredRing(
                        irisVc, mat, targetRel.add(0.0, ry, 0.0), axU, axV,
                        rr * 0.76, rr * 0.83, rr * 0.90, 26,
                        1.00F * g, 0.44F * g, 0.08F * g, ringA
                );
                ZoltraakRenderPass.emitFeatheredRing(
                        irisVc, mat, targetRel.add(0.0, ry, 0.0), axU, axV,
                        rr * 0.32, rr * 0.37, rr * 0.42, 20,
                        1.00F * g, 0.62F * g, 0.16F * g, ringA * 0.72F
                );
            }
            if (p > 0.42F && cfg.skyFlareStrength() > 0.01F && ZoltraakCinematicClientConfig.isLensFlaresEnabled()) {
                float flareStr = cfg.skyFlareStrength();
                float glintP = ExplosionMagicRenderer.smoothstep((p - 0.42F) / 0.24F) * fade;
                float gg2 = ZoltraakRenderPass.irisFadeGuard(glintP);
                Vec3 apexCenter = targetRel.add(0.0, ExplosionMagicRenderer.getSkyApexHeight(radius), 0.0);
                double flareReach = radius * 0.92 * Math.max(0.35, Math.sqrt(flareStr));
                ZoltraakRenderPass.emitFeatheredLensStreak(
                        irisVc, mat, apexCenter, camRight, camUp, flareReach,
                        0.12 * flareStr, 0.72 * Math.min(1.6, 0.65 + 0.30 * flareStr),
                        1.00F * gg2, 0.90F * gg2, 0.44F * gg2, Mth.clamp(glintP * 0.38F * Math.min(1.3F, flareStr), 0.0F, 0.64F),
                        0.98F * gg2, 0.54F * gg2, 0.10F * gg2, Mth.clamp(glintP * 0.18F * Math.min(1.3F, flareStr), 0.0F, 0.36F)
                );
            }
        }

        // Staff orb + rainbow ring + configurable cross-rays & suction HDR shaderpack bloom
        float orbFade = releasing ? (1.0F - ExplosionMagicRenderer.smoothstep(outroT / (firedRelease ? 15.0F : 12.0F))) : fade;
        if (orbFade > 0.015F) {
            float orbA = (0.42F + 0.34F * p) * orbFade * (isFpLocal ? 0.26F : 0.92F);
            float og = ZoltraakRenderPass.irisFadeGuard(orbA);
            double orbExpand = releasing ? (1.0 + 0.42 * ExplosionMagicRenderer.smoothstep(outroT / 10.0F)) : 1.0;
            ZoltraakRenderPass.emitFeatheredStarBurst(
                    irisVc, mat, staffRel, camRight, camUp, (0.58 + 0.68 * p) * (isFpLocal ? 0.36 : 1.05) * orbExpand,
                    1.00F * og, 0.36F * og, 0.90F * og, orbA
            );
        }
        boolean activeRainbow = (!releasing && p > 0.56F) || (firedRelease && outroT < 16.0F);
        if (activeRainbow) {
            float rp = Mth.clamp((p - 0.56F) / 0.42F, 0.0F, 1.0F);
            float rEnv = releasing
                    ? (1.0F - ExplosionMagicRenderer.smoothstep(outroT / 16.0F)) * (isFpLocal ? 0.16F : 0.46F)
                    : (float) Math.sin(rp * Math.PI) * fade * (isFpLocal ? 0.18F : 0.52F);
            float rg = ZoltraakRenderPass.irisFadeGuard(rEnv);
            double rExpand = releasing ? (1.0 + 0.78 * ExplosionMagicRenderer.smoothstep(outroT / 16.0F)) : 1.0;
            double staffRingR = (1.8 + 2.1 * Math.sin(rp * Math.PI * 0.78)) * (isFpLocal ? 0.52 : 1.0) * rExpand;
            ZoltraakRenderPass.emitFeatheredRing(
                    irisVc, mat, staffRel, camRight, camUp,
                    staffRingR * 0.68, staffRingR * 0.75, staffRingR * 0.84, 24,
                    0.96F * rg, 0.84F * rg, 1.00F * rg, rEnv
            );
            float rayEnv = releasing ? (1.0F - ExplosionMagicRenderer.smoothstep(outroT / 11.0F)) * (isFpLocal ? 0.16F : 0.45F) : rEnv;
            if (cfg.crossRays() > 0 && rayEnv > 0.015F) {
                double baseRayLen = staffRingR * (isFpLocal ? 0.72 : 1.18) * cfg.particleScale();
                for (int k = 0; k < cfg.crossRays(); k++) {
                    double ang = k * (Math.PI / cfg.crossRays()) + 0.32 + time * 0.022;
                    Vec3 rayDir = camRight.scale(Math.cos(ang)).add(camUp.scale(Math.sin(ang)));
                    Vec3 raySide = camRight.scale(-Math.sin(ang)).add(camUp.scale(Math.cos(ang)));
                    ZoltraakRenderPass.emitFeatheredLensStreak(
                            irisVc, mat, staffRel, rayDir, raySide, baseRayLen,
                            0.035 * cfg.particleScale(), 0.16 * cfg.particleScale(),
                            1.00F * rg, 0.92F * rg, 0.56F * rg, rayEnv * 0.85F,
                            0.98F * rg, 0.58F * rg, 0.12F * rg, rayEnv * 0.40F
                    );
                }
            }
            if (!releasing) {
                int suctionStep = Math.max(1, cfg.suctionLines() / 16);
                for (int s = 0; s < cfg.suctionLines(); s += suctionStep) {
                    float sh = ExplosionMagicRenderer.hashFloat(s * 31 + 5);
                    float sh2 = ExplosionMagicRenderer.hashFloat(s * 53 + 17);
                    float sCyc = (time * (0.058F + 0.020F * sh2) + s / (float) cfg.suctionLines()) % 1.0F;
                    double sAng = s * (Math.PI * 2.0 / cfg.suctionLines()) + sh * 0.42;
                    double rOut = staffRingR * ((1.48 + 0.28 * sh) * (1.0 - 0.86 * sCyc));
                    Vec3 dir2D = camRight.scale(Math.cos(sAng)).add(camUp.scale(Math.sin(sAng)));
                    Vec3 midP = staffRel.add(dir2D.scale(rOut + 0.16));
                    Vec3 perp = dir2D.cross(camRight.cross(camUp));
                    if (perp.lengthSqr() < 1.0E-4) perp = camUp; else perp = perp.normalize();
                    float sa = rEnv * (float) Math.sin(sCyc * Math.PI) * 0.88F;
                    if (sa > 0.02F) {
                        ZoltraakRenderPass.emitFeatheredLensStreak(
                                irisVc, mat, midP, dir2D, perp,
                                (0.36 + 0.18 * sh2) * cfg.particleScale(),
                                0.035 * cfg.particleScale(), 0.14 * cfg.particleScale(),
                                1.00F * rg, 0.90F * rg, 0.46F * rg, sa,
                                0.98F * rg, 0.54F * rg, 0.10F * rg, sa * 0.40F
                        );
                    }
                }
            }
        }

        // Orbiting gold sparks & 4-color 4-point stars + parabolic stream Iris HDR bloom proxies (buoyant drift on release)
        double outroRise = releasing ? outroT * 0.14 : 0.0;
        double outroSpread = releasing ? (1.0 + outroT * 0.015) : 1.0;
        float ptMult = fade * (isFpLocal ? 0.20F : 0.46F);
        float pg = ZoltraakRenderPass.irisFadeGuard(ptMult);
        int dotStep = Math.max(1, cfg.goldDots() / 64);
        for (int i = 0; i < cfg.goldDots(); i += dotStep) {
            float h1 = ExplosionMagicRenderer.hashFloat(i * 23 + 3);
            float h2 = ExplosionMagicRenderer.hashFloat(i * 47 + 11);
            float h3 = ExplosionMagicRenderer.hashFloat(i * 61 + 29);
            float cyc = (time * (0.015F + 0.016F * h1) + i / (float) cfg.goldDots()) % 1.0F;
            double ang = i * 2.39996 + time * (0.022 + 0.012 * h3) + h2 * 2.0;
            double rOrb = ((isFpLocal ? 1.5 : 0.7) + (isFpLocal ? 2.6 : 4.6) * Math.sqrt(h2)) * outroSpread;
            double dy = 0.10 + cyc * (5.2 + 1.8 * p) + outroRise * (0.65 + 0.7 * h1);
            float da = ptMult * (float) Math.sin(cyc * Math.PI);
            if (da <= 0.02F) continue;
            ZoltraakRenderPass.emitFeatheredStarBurst(
                    irisVc, mat, casterPos.add(Math.cos(ang) * rOrb, dy, Math.sin(ang) * rOrb),
                    camRight, camUp, (0.24 + 0.18 * h1) * cfg.particleScale(),
                    1.00F * pg, 0.84F * pg, 0.28F * pg, da
            );
        }

        int starStep = Math.max(1, cfg.casterStars() / 48);
        for (int i = 0; i < cfg.casterStars(); i += starStep) {
            float spSeed = ExplosionMagicRenderer.hashFloat(i * 41 + 9);
            float spBand = ExplosionMagicRenderer.hashFloat(i * 73 + 13);
            float cycle = (time * (0.018F + 0.014F * spSeed) + i / (float) cfg.casterStars()) % 1.0F;
            double ang = i * 2.399 + time * (0.036 + 0.010 * spBand);
            double rOrbit = (isFpLocal ? 1.8 : 3.8) * (0.30 + 0.82 * (1.0 - cycle * 0.45)) * outroSpread;
            double sy = 0.20 + cycle * 5.2 + outroRise * (0.65 + 0.7 * spSeed);
            float sa = ptMult * 1.15F * (float) Math.sin(cycle * Math.PI);
            if (sa <= 0.02F) continue;
            float sr = 1.00F, sg2 = 0.88F, sb = 0.24F;
            switch (i % 4) {
                case 0 -> { sr = 0.26F; sg2 = 1.00F; sb = 0.42F; }
                case 1 -> { sr = 1.00F; sg2 = 0.30F; sb = 0.88F; }
                case 2 -> { sr = 0.24F; sg2 = 0.90F; sb = 1.00F; }
                default -> { sr = 1.00F; sg2 = 0.88F; sb = 0.24F; }
            }
            ZoltraakRenderPass.emitFeatheredStarBurst(
                    irisVc, mat, casterPos.add(Math.cos(ang) * rOrbit, sy, Math.sin(ang) * rOrbit),
                    camRight, camUp, (0.36 + 0.28 * spSeed) * cfg.particleScale(),
                    sr * pg, sg2 * pg, sb * pg, Mth.clamp(sa, 0.0F, 0.65F)
            );
        }

        float arcEnv = releasing ? Math.max(0.0F, 1.0F - ExplosionMagicRenderer.smoothstep(outroT / 16.0F)) : 1.0F;
        if (p > 0.16F && arcEnv > 0.02F) {
            float arcFade = ExplosionMagicRenderer.smoothstep((p - 0.16F) / 0.20F) * ptMult * 1.10F * arcEnv;
            Vec3 topFunnel = targetRel.add(0.0, ExplosionMagicRenderer.getSkyApexHeight(radius), 0.0);
            int arcStep = Math.max(1, cfg.arcParticles() / 40);
            for (int i = 0; i < cfg.arcParticles(); i += arcStep) {
                float h = ExplosionMagicRenderer.hashFloat(i * 71 + 19);
                float hSide = ExplosionMagicRenderer.hashFloat(i * 89 + 31);
                float u = (time * (0.025F + 0.010F * hSide) + i / (float) cfg.arcParticles()) % 1.0F;
                Vec3 baseLine = staffRel.lerp(topFunnel, u);
                double arcLift = Math.sin(u * Math.PI) * (5.6 + 4.2 * h) + outroRise * 0.45;
                double sideSpread = Math.sin(u * Math.PI * 2.0 + i * 0.9) * (0.9 + 1.6 * hSide);
                Vec3 arcPos = baseLine.add(camRight.scale(sideSpread)).add(0.0, arcLift, 0.0);
                float pa = arcFade * (float) Math.sin(u * Math.PI);
                if (pa > 0.02F) {
                    ZoltraakRenderPass.emitFeatheredStarBurst(
                            irisVc, mat, arcPos, camRight, camUp, (0.34 + 0.22 * h) * cfg.particleScale(),
                            1.00F * pg, 0.82F * pg, 0.30F * pg, Mth.clamp(pa, 0.0F, 0.60F)
                    );
                }
            }
        }
    }

    /** Emits a crisp 4-point star (✦) paired with a pure Gaussian radial bloom halo behind it (Mode 5). */
    private static void emitBloomingFourPointStar(
            VertexConsumer starVc, VertexConsumer bloomVc, Matrix4f mat,
            Vec3 center, Vec3 right, Vec3 up, double radius, int colorVariant, float alpha
    ) {
        float r, g, b, palTint;
        switch (colorVariant) {
            case 0 -> { r = 0.22F; g = 1.00F; b = 0.36F; palTint = 0.92F; } // Neon green
            case 1 -> { r = 1.00F; g = 0.26F; b = 0.88F; palTint = 0.08F; } // Hot magenta-pink
            case 2 -> { r = 0.20F; g = 0.88F; b = 1.00F; palTint = 0.26F; } // Electric cyan
            case 3 -> { r = 1.00F; g = 0.90F; b = 0.22F; palTint = 0.84F; } // Warm gold
            default -> { r = 1.00F; g = 0.56F; b = 0.14F; palTint = 0.68F; } // Fiery orange-gold
        }
        ExplosionMagicRenderer.emitBillboardQuad(bloomVc, mat, center, right, up, radius * 2.4, radius * 2.4, palTint, 0.10F, 0.98F, alpha * 0.62F);
        ExplosionMagicRenderer.emitFourPointStar(starVc, mat, center, right, up, radius, r, g, b, alpha);
    }

    /**
     * Emits animated radial fountain streams that erupt off the outer ground circle and surge
     * directly inward into {@code staffRel}, contracting and fully absorbing into the orb by p=0.72.
     */
    private static void emitManaAbsorptionStreams(
            VertexConsumer vc, Matrix4f mat,
            Vec3 casterPos, Vec3 staffRel, Vec3 camRight,
            float p, float time, float baseAlpha, boolean isFpLocal, boolean irisProxy
    ) {
        float gatherEnv = ExplosionMagicRenderer.smoothstep((p - 0.02F) / 0.10F)
                * (1.0F - ExplosionMagicRenderer.smoothstep((p - 0.56F) / 0.16F));
        float alpha = baseAlpha * gatherEnv;
        if (alpha <= 0.015F) return;

        double tighten = 1.0 - 0.52 * ExplosionMagicRenderer.smoothstep((p - 0.36F) / 0.34F);
        double absorbPull = 0.62 * ExplosionMagicRenderer.smoothstep((p - 0.44F) / 0.26F);
        Vec3 groundHub = new Vec3(
                Mth.lerp(0.35, casterPos.x, staffRel.x),
                casterPos.y,
                Mth.lerp(0.35, casterPos.z, staffRel.z)
        );

        int totalStreams = irisProxy ? 10 : 12;
        float irisGuard = irisProxy ? ZoltraakRenderPass.irisFadeGuard(alpha) : 1.0F;

        for (int a = 0; a < totalStreams; a++) {
            float h0 = ExplosionMagicRenderer.hashFloat(a * 29 + 7);
            float h1 = ExplosionMagicRenderer.hashFloat(a * 43 + 19);
            float h2 = ExplosionMagicRenderer.hashFloat(a * 67 + 31);

            double streamAng = a * (Math.PI * 2.0 / totalStreams) + (h0 - 0.5) * 0.26 + time * 0.012;
            double cosA = Math.cos(streamAng);
            double sinA = Math.sin(streamAng);
            double rootR = (isFpLocal ? 2.2 : (2.9 + 1.15 * h1)) * tighten;
            double archH = (isFpLocal ? 1.7 : (2.2 + 1.35 * h2)) * tighten;

            Vec3 root = groundHub.add(cosA * rootR, 0.08, sinA * rootR);
            Vec3 mid1 = groundHub.add(Math.cos(streamAng + 0.07) * (rootR * 0.94), archH * 0.96, Math.sin(streamAng + 0.07) * (rootR * 0.94));
            Vec3 mid2 = staffRel.add(Math.cos(streamAng + 0.03) * (rootR * 0.22), Math.max(0.42, archH * 0.42), Math.sin(streamAng + 0.03) * (rootR * 0.22));
            double plumeW = (isFpLocal ? 0.065 : (0.095 + 0.045 * h2)) * (0.78 + 0.22 * p);

            for (int w = 0; w < 2; w++) {
                double speed = 0.044 + 0.016 * h2;
                double cyc = (time * speed + a * 0.31 + w * 0.50) % 1.0;
                double accelCyc = cyc * (0.62 + 0.38 * cyc);
                double uEndRaw = accelCyc * 1.48 + absorbPull;
                double uStartRaw = uEndRaw - 0.50;
                double uStart = Mth.clamp(uStartRaw, 0.0, 1.0);
                double uEnd = Mth.clamp(uEndRaw, 0.0, 1.0);
                if (uEnd - uStart <= 0.06) continue;

                float waveSeed = ((a * 17 + w * 43) % 100) / 100.0F;
                if (irisProxy) {
                    emitIrisStreamPacket(vc, mat, camRight, root, mid1, mid2, staffRel, uStart, uEnd, plumeW, irisGuard, alpha);
                } else {
                    emitCyanStreamPacket(vc, mat, camRight, root, mid1, mid2, staffRel, uStart, uEnd, p, waveSeed, plumeW, alpha);
                }
            }
        }
    }

    private static Vec3 evalCubicBezier(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double u) {
        double mu = 1.0 - u;
        return a.scale(mu * mu * mu)
                .add(b.scale(3.0 * mu * mu * u))
                .add(c.scale(3.0 * mu * u * u))
                .add(d.scale(u * u * u));
    }

    private static void emitCyanStreamPacket(
            VertexConsumer vc, Matrix4f mat, Vec3 fallbackRight,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            double uStart, double uEnd,
            float progress, float seedNorm, double width, float alpha
    ) {
        final int segs = 12;
        Vec3[] pts = new Vec3[segs + 1];
        Vec3[] edges = new Vec3[segs + 1];

        for (int i = 0; i <= segs; i++) {
            double localU = i / (double) segs;
            pts[i] = evalCubicBezier(a, b, c, d, Mth.lerp(localU, uStart, uEnd));
        }

        for (int i = 0; i <= segs; i++) {
            double localU = i / (double) segs;
            Vec3 tang = pts[Math.min(segs, i + 1)].subtract(pts[Math.max(0, i - 1)]);
            Vec3 side = tang.cross(pts[i].scale(-1.0));
            side = side.lengthSqr() < 1.0E-5 ? fallbackRight : side.normalize();
            double packetShape = Math.pow(localU, 0.72) * ExplosionMagicRenderer.smoothstep(1.0F, 0.84F, (float) localU);
            double orbPinch = uEnd > 0.90 ? (1.0 - 0.55 * ExplosionMagicRenderer.smoothstep(0.90F, 1.0F, (float) Mth.lerp(localU, uStart, uEnd))) : 1.0;
            edges[i] = side.scale(width * (0.20 + 0.95 * packetShape) * orbPinch);
        }

        for (int i = 0; i < segs; i++) {
            float lu0 = i / (float) segs;
            float lu1 = (i + 1) / (float) segs;
            Vec3 l0 = pts[i].subtract(edges[i]);
            Vec3 l1 = pts[i + 1].subtract(edges[i + 1]);
            Vec3 r1 = pts[i + 1].add(edges[i + 1]);
            Vec3 r0 = pts[i].add(edges[i]);

            vc.addVertex(mat, (float) l0.x, (float) l0.y, (float) l0.z).setUv(lu0, 0.0F).setColor(progress, seedNorm, 0.84F, alpha);
            vc.addVertex(mat, (float) l1.x, (float) l1.y, (float) l1.z).setUv(lu1, 0.0F).setColor(progress, seedNorm, 0.84F, alpha);
            vc.addVertex(mat, (float) r1.x, (float) r1.y, (float) r1.z).setUv(lu1, 1.0F).setColor(progress, seedNorm, 0.84F, alpha);
            vc.addVertex(mat, (float) r0.x, (float) r0.y, (float) r0.z).setUv(lu0, 1.0F).setColor(progress, seedNorm, 0.84F, alpha);
        }
    }

    private static void emitIrisStreamPacket(
            VertexConsumer irisVc, Matrix4f mat, Vec3 fallbackRight,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            double uStart, double uEnd,
            double width, float bg, float alpha
    ) {
        final int steps = 6;
        Vec3 prev = evalCubicBezier(a, b, c, d, uStart);
        for (int s = 1; s <= steps; s++) {
            double lu = s / (double) steps;
            Vec3 pt = evalCubicBezier(a, b, c, d, Mth.lerp(lu, uStart, uEnd));
            Vec3 side = pt.subtract(prev).cross(prev.scale(-1.0));
            side = side.lengthSqr() < 1.0E-5 ? fallbackRight : side.normalize();
            float segAlpha = alpha * (float) (Math.pow(lu, 0.70) * (1.0 - Math.max(0.0, (lu - 0.88) / 0.12)));
            if (segAlpha > 0.015F) {
                ZoltraakRenderPass.emitFeatheredBeamSegment(
                        irisVc, mat, prev, pt, side,
                        width * 0.45, width * 1.85,
                        0.50F * bg, 0.94F * bg, 1.00F * bg, segAlpha,
                        0.06F * bg, 0.56F * bg, 1.00F * bg, segAlpha * 0.50F
                );
            }
            prev = pt;
        }
    }
}
