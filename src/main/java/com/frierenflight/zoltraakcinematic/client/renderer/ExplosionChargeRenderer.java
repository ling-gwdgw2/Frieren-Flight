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
 * Renders the 3.5s Explosion Magic channeling sequence with radial Gaussian & Iris HDR bloom:
 * - Opening magenta-pink orb flash + expanding floor & torso-height magenta-purple shockwave rings
 * - Ground crimson-gold swirling vortex + soft radial ambient bloom
 * - 12 twisting tall electric cyan-blue fountain ribbons with soft blue bloom
 * - 2 caster overhead crimson rings + 6 inverted-cone target sky vortex rings + golden apex glint
 * - 80 blooming golden spark dots, 32 blooming 4-point cross-stars, arcing spark stream & swirling funnel stars
 * - Camera-facing rainbow shockwave ring + golden cross-rays & suction trails at staff tip
 * - 18 craggy dark rocks spiralling toward the staff
 */
public final class ExplosionChargeRenderer {

    private static final class ChargeState {
        int introTicks = 0;
        int lastSeenTick = 0;
        float smoothedProgress = 0.0F;
        boolean castingNow = false;
        Vec3 smoothedTarget = null;
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
            if (tick - s.lastSeenTick > 10 && s.introTicks <= 0) {
                it.remove();
            } else if (!s.castingNow && s.introTicks > 0) {
                s.introTicks = Math.max(0, s.introTicks - 2);
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
                if (state.introTicks < 6) state.introTicks++;
                state.smoothedProgress = living == mc.player
                        ? progress
                        : Mth.clamp(state.smoothedProgress + 0.014F, 0.0F, 0.98F);
            } else {
                state.introTicks = Math.max(0, state.introTicks - 2);
                if (state.introTicks == 0) {
                    state.smoothedProgress = 0.0F;
                    state.smoothedTarget = null;
                }
            }

            if (state.introTicks > 0) {
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

        pose.pushPose();
        Matrix4f mat = pose.last().pose();

        try {
            for (LivingEntity caster : casters) {
                ChargeState state = ACTIVE.get(caster.getUUID());
                if (state == null || state.introTicks <= 0) continue;

                float fade = ExplosionMagicRenderer.smoothstep(state.introTicks / 6.0F);
                float p = state.smoothedProgress;
                boolean isFpLocal = caster == mc.player && mc.getCameraEntity() == mc.player
                        && mc.options.getCameraType().isFirstPerson();

                Vec3 casterPos = caster.getPosition(partial).subtract(camPos);
                Vec3 rawTarget = ExplosionSpell.computeTargetPosition(mc.level, caster, partial, ExplosionSpell.CAST_RANGE);
                if (state.smoothedTarget == null) {
                    state.smoothedTarget = rawTarget;
                } else {
                    state.smoothedTarget = state.smoothedTarget.lerp(rawTarget, 0.25);
                }
                Vec3 targetRel = state.smoothedTarget.subtract(camPos);
                Vec3 staffRel = ZoltraakCinematicBeamEntity.getStaffAnchorPos(caster, partial).subtract(camPos);

                if (irisBloomProxy) {
                    emitIrisChargeProxy(buffers, mat, casterPos, staffRel, targetRel, camRight, camUp, radius, p, time, fade, isFpLocal);
                    continue;
                }

                boolean shadersActive = ShaderCompatibility.useFullDetailPass() && ZoltraakCinematicClientConfig.isWorldGlowActive(true);
                float shaderHaloScale = shadersActive ? 0.36F : 1.0F;
                float fpDamp = isFpLocal ? 0.45F : 1.0F;

                // =========================================================================
                // PASS 1: EXPLOSION_VORTEX_RING (Modes 0..5 — True Soft Radial & Volumetric Bloom!)
                // =========================================================================
                VertexConsumer vortexVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_VORTEX_RING);

                double groundR = (3.2 + 4.4 * ExplosionMagicRenderer.smoothstep(p / 0.42F)) * (isFpLocal ? 0.85 : 1.0);
                float gAlpha = fade * (isFpLocal ? 0.72F : 0.96F);

                // 1a. Pure Radial Gaussian Bloom Floor & Fountain Glow (Mode 5, seed=0.10) — zero crossbars!
                ExplosionMagicRenderer.emitHorizDiscQuad(
                        vortexVc, mat, casterPos.add(0.0, 0.04, 0.0),
                        groundR * 1.32, 0.0, 0.56F, 0.10F, 0.98F, fade * (isFpLocal ? 0.14F : 0.26F) * shaderHaloScale
                );
                ExplosionMagicRenderer.emitBillboardQuad(
                        vortexVc, mat, casterPos.add(0.0, 2.0, 0.0), camRight, camUp,
                        groundR * 0.72, 2.2, 0.26F, 0.10F, 0.98F, fade * (isFpLocal ? 0.12F : 0.22F) * shaderHaloScale
                );

                // 1b. Opening Blinding Magenta-Pink Orb Flash + Floor & Torso Magenta-Violet Shockwave Rings
                if (p < 0.28F) {
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

                // 1c. Ground crimson-gold swirling vortex beneath caster (Mode 0)
                ExplosionMagicRenderer.emitHorizDiscQuad(
                        vortexVc, mat, casterPos.add(0.0, 0.07, 0.0),
                        groundR, -time * 0.055, p, 0.21F, 0.0F, gAlpha
                );

                // 1d. Two smaller crimson vortex rings hovering directly above the caster (Mode 1)
                if (p > 0.10F) {
                    float cRing1 = ExplosionMagicRenderer.smoothstep((p - 0.10F) / 0.18F) * fade;
                    ExplosionMagicRenderer.emitHorizDiscQuad(
                            vortexVc, mat, casterPos.add(0.0, 4.4, 0.0),
                            2.5 * cRing1, time * 0.065, p, 0.37F, 0.5F, cRing1 * 0.92F
                    );
                }
                if (p > 0.20F) {
                    float cRing2 = ExplosionMagicRenderer.smoothstep((p - 0.20F) / 0.20F) * fade;
                    ExplosionMagicRenderer.emitHorizDiscQuad(
                            vortexVc, mat, casterPos.add(0.0, 7.8, 0.0),
                            4.2 * cRing2, -time * 0.052, p, 0.61F, 0.5F, cRing2 * 0.95F
                    );
                }

                // 1e. 6 Stacked Inverted-Cone Fiery Sky Vortex Rings + Subtle Crimson-Amber Rim Glow above target
                if (p > 0.12F) {
                    ExplosionMagicRenderer.emitSkyVortexColumn(
                            vortexVc, mat, targetRel, radius,
                            p, 1.0F, fade * 0.98F, 0.42F, time * 0.055
                    );
                    float skyBloomA = ExplosionMagicRenderer.smoothstep((p - 0.12F) / 0.25F) * fade * (shadersActive ? 0.08F : 0.20F);
                    if (skyBloomA > 0.008F) {
                        for (int i = 0; i < ExplosionMagicRenderer.SKY_RING_HEIGHT_FACTORS.length; i++) {
                            double ry = radius * ExplosionMagicRenderer.SKY_RING_HEIGHT_FACTORS[i];
                            double rr = radius * ExplosionMagicRenderer.SKY_RING_SCALE_FACTORS[i] * 1.15;
                            ExplosionMagicRenderer.emitHorizDiscQuad(
                                    vortexVc, mat, targetRel.add(0.0, ry, 0.0), rr, 0.0,
                                    0.48F, 0.10F, 0.98F, skyBloomA * (0.60F + 0.40F * (i / 5.0F))
                            );
                        }
                    }
                }

                // 1f. 12 Swirling Tall Electric Cyan-Blue Fountain Ribbons (Mode 3)
                if (!isFpLocal || p > 0.08F) {
                    int totalRibbons = 12;
                    double archGrow = ExplosionMagicRenderer.smoothstep((p - 0.03F) / 0.30F);
                    for (int a = 0; a < totalRibbons; a++) {
                        boolean innerLayer = (a >= 8);
                        double phase = (a % 8) * (Math.PI * 2.0 / 8.0) + (innerLayer ? 0.42 : 0.0) + time * 0.048;
                        double baseR = (isFpLocal ? 2.3 : (innerLayer ? 2.5 : 3.8)) * (0.82 + 0.18 * archGrow);
                        double crestH = (isFpLocal ? 2.7 : (innerLayer ? 3.5 : 4.6)) * (0.62 + 0.38 * archGrow) + 0.35 * Math.sin(a * 1.9 + time * 0.09);

                        Vec3 root = casterPos.add(Math.cos(phase) * baseR, 0.10, Math.sin(phase) * baseR);
                        Vec3 mid1 = casterPos.add(Math.cos(phase + 0.48) * baseR * 0.92, crestH, Math.sin(phase + 0.48) * baseR * 0.92);
                        Vec3 mid2 = staffRel.add(Math.cos(phase + 1.05) * (isFpLocal ? 0.85 : 1.55), (isFpLocal ? 1.2 : 2.1), Math.sin(phase + 1.05) * (isFpLocal ? 0.85 : 1.55));
                        double plumeW = (isFpLocal ? 0.09 : (innerLayer ? 0.15 : 0.22)) * (0.72 + 0.38 * p);
                        emitCyanFountainRibbon(vortexVc, mat, root, mid1, mid2, staffRel, p, (a * 0.11F) % 1.0F, plumeW, fade * (isFpLocal ? 0.44F : 0.88F));
                    }
                }

                // 1g. Vivid Magenta-Pink Staff Orb Multi-Layer Radial Bloom Corona (Mode 5, radial glow)
                double orbSize = (0.54 + 0.68 * Math.pow(p, 1.1)) * (isFpLocal ? 0.34 : 1.0);
                ExplosionMagicRenderer.emitBillboardQuad(
                        vortexVc, mat, staffRel, camRight, camUp, orbSize * 2.05, orbSize * 2.05,
                        0.08F, 0.10F, 0.98F, fade * (0.44F + 0.18F * p) * fpDamp * (shadersActive ? 0.65F : 1.0F)
                );
                ExplosionMagicRenderer.emitBillboardQuad(
                        vortexVc, mat, staffRel, camRight, camUp, orbSize * 1.15, orbSize * 1.15,
                        0.15F, 0.10F, 0.98F, fade * (0.72F + 0.14F * p) * (isFpLocal ? 0.60F : 1.0F)
                );
                ExplosionMagicRenderer.emitBillboardQuad(
                        vortexVc, mat, staffRel, camRight, camUp, orbSize * 0.52, orbSize * 0.52,
                        0.98F, 0.10F, 0.98F, fade * (isFpLocal ? 0.78F : 0.96F)
                );

                // 1h. Rainbow Shockwave Circle + Long Golden Converging Cross-Rays (*) + Suction Trails
                if (p > 0.56F) {
                    float rp = Mth.clamp((p - 0.56F) / 0.42F, 0.0F, 1.0F);
                    float rEnv = (float) Math.sin(rp * Math.PI) * fade;
                    double staffRingR = (1.8 + 2.1 * Math.sin(rp * Math.PI * 0.78)) * (isFpLocal ? 0.52 : 1.0);
                    ExplosionMagicRenderer.emitBillboardQuad(
                            vortexVc, mat, staffRel, camRight, camUp,
                            staffRingR, staffRingR, p, 0.67F, 0.72F, rEnv * 0.94F
                    );

                    double rayLen = staffRingR * (isFpLocal ? 0.85 : 1.32);
                    double rayW = (isFpLocal ? 0.07 : 0.18) * rEnv;
                    for (int k = 0; k < 4; k++) {
                        double ang = k * (Math.PI * 0.25) + 0.32 + time * 0.02;
                        Vec3 rayDir = camRight.scale(Math.cos(ang)).add(camUp.scale(Math.sin(ang)));
                        ExplosionMagicRenderer.emitSoftBloomBeamQuad(
                                vortexVc, mat,
                                staffRel.subtract(rayDir.scale(rayLen)),
                                staffRel.add(rayDir.scale(rayLen)),
                                Vec3.ZERO, rayW, 0.35F, 0.85F, rEnv * (isFpLocal ? 0.45F : 0.86F)
                        );
                    }

                    int suctionCount = 14;
                    for (int s = 0; s < suctionCount; s++) {
                        float sh = ExplosionMagicRenderer.hashFloat(s * 31 + 5);
                        float sCyc = (time * 0.065F + s / (float) suctionCount) % 1.0F;
                        double sAng = s * (Math.PI * 2.0 / suctionCount) + sh * 0.5;
                        double rOut = staffRingR * (1.45 - 1.15 * sCyc);
                        Vec3 dir2D = camRight.scale(Math.cos(sAng)).add(camUp.scale(Math.sin(sAng)));
                        Vec3 tailP = staffRel.add(dir2D.scale(rOut + 0.65));
                        Vec3 headP = staffRel.add(dir2D.scale(Math.max(0.15, rOut - 0.35)));
                        float sAlpha = rEnv * (float) Math.sin(sCyc * Math.PI) * 0.85F;
                        ExplosionMagicRenderer.emitSoftBloomBeamQuad(
                                vortexVc, mat, tailP, headP, Vec3.ZERO, 0.10, 0.25F, 0.85F, sAlpha
                        );
                    }
                }

                // 1i. 80 Rising Golden-Yellow & Orange Blooming Round Spark Dots (•) around caster (Mode 5)
                int goldDots = 80;
                for (int i = 0; i < goldDots; i++) {
                    float h1 = ExplosionMagicRenderer.hashFloat(i * 23 + 3);
                    float h2 = ExplosionMagicRenderer.hashFloat(i * 47 + 11);
                    float cyc = (time * (0.016F + 0.015F * h1) + i / (float) goldDots) % 1.0F;
                    double ang = i * 2.39996 + time * 0.025 + h2 * 2.0;
                    double rOrb = (isFpLocal ? 1.4 : 0.8) + (isFpLocal ? 2.4 : 4.2) * Math.sqrt(h2);
                    double dy = 0.12 + cyc * (4.8 + 1.6 * p);
                    Vec3 dotPos = casterPos.add(Math.cos(ang) * rOrb, dy, Math.sin(ang) * rOrb);
                    float dotA = fade * (float) Math.sin(cyc * Math.PI) * (0.68F + 0.32F * p);
                    if (dotA <= 0.02F) continue;
                    double dotSz = 0.11 + 0.12 * h1;
                    ExplosionMagicRenderer.emitBillboardQuad(
                            vortexVc, mat, dotPos, camRight, camUp, dotSz, dotSz,
                            0.66F + 0.18F * h2, 0.10F, 0.98F, dotA
                    );
                }

                // =========================================================================
                // PASS 2: Blooming 4-Point Cross-Stars (✦) -> Mode 5 Bloom Halo + LIGHT Star Mesh
                // =========================================================================
                VertexConsumer starVc = buffers.getBuffer(ZoltraakRenderTypes.LIGHT);

                int casterStars = 32;
                for (int i = 0; i < casterStars; i++) {
                    float spSeed = ExplosionMagicRenderer.hashFloat(i * 41 + 9);
                    float cycle = (time * (0.019F + 0.013F * spSeed) + i / (float) casterStars) % 1.0F;
                    double ang = i * 2.399 + time * 0.038;
                    double rOrbit = (isFpLocal ? 1.8 : 3.4) * (0.32 + 0.78 * (1.0 - cycle * 0.48));
                    double sy = 0.25 + cycle * 4.8;
                    Vec3 spPos = casterPos.add(Math.cos(ang) * rOrbit, sy, Math.sin(ang) * rOrbit);
                    float glintAlpha = fade * (float) Math.sin(cycle * Math.PI) * 0.94F;
                    if (glintAlpha <= 0.02F) continue;
                    double spSize = (0.15 + 0.15 * spSeed) * (0.68 + 0.42 * (float) Math.sin(time * 0.25 + i));
                    emitBloomingFourPointStar(starVc, vortexVc, mat, spPos, camRight, camUp, spSize, i % 4, glintAlpha);
                }

                // Parabolic Spark & Star Stream Arcing from Caster UP to Target Sky Funnel
                if (p > 0.20F) {
                    float arcFade = ExplosionMagicRenderer.smoothstep((p - 0.20F) / 0.20F) * fade;
                    Vec3 topFunnel = targetRel.add(0.0, radius * ExplosionMagicRenderer.SKY_RING_HEIGHT_FACTORS[5], 0.0);
                    int arcParticles = 28;
                    for (int i = 0; i < arcParticles; i++) {
                        float h = ExplosionMagicRenderer.hashFloat(i * 71 + 19);
                        float u = (time * 0.028F + i / (float) arcParticles) % 1.0F;
                        Vec3 baseLine = staffRel.lerp(topFunnel, u);
                        double arcLift = Math.sin(u * Math.PI) * (6.0 + 3.0 * h);
                        double sideSpread = Math.sin(u * Math.PI * 2.0 + i) * (0.8 + 1.2 * h);
                        Vec3 arcPos = baseLine.add(camRight.scale(sideSpread)).add(0.0, arcLift, 0.0);
                        float pa = arcFade * (float) Math.sin(u * Math.PI) * 0.90F;
                        if (i % 3 == 0) {
                            emitBloomingFourPointStar(starVc, vortexVc, mat, arcPos, camRight, camUp, 0.18 + 0.12 * h, i % 4, pa);
                        } else {
                            ExplosionMagicRenderer.emitBillboardQuad(
                                    vortexVc, mat, arcPos, camRight, camUp, 0.16 + 0.10 * h, 0.16 + 0.10 * h,
                                    0.74F, 0.10F, 0.98F, pa
                            );
                        }
                    }
                }

                // Swirling Funnel Embers & Multi-Colored 4-Point Stars Inside Target Sky Funnel
                if (p > 0.16F) {
                    float funnelFade = ExplosionMagicRenderer.smoothstep((p - 0.16F) / 0.22F) * fade;
                    int funnelCount = 48;
                    double maxH = radius * ExplosionMagicRenderer.SKY_RING_HEIGHT_FACTORS[5];
                    for (int i = 0; i < funnelCount; i++) {
                        float h = ExplosionMagicRenderer.hashFloat(i * 59 + 17);
                        float cyc = (time * (0.018F + 0.012F * h) + i / (float) funnelCount) % 1.0F;
                        double fy = 0.8 + cyc * maxH;
                        double funnelR = radius * (0.08 + 0.62 * Math.pow(cyc, 1.32)) * (0.55 + 0.55 * h);
                        double ang = i * 2.1 + time * 0.075 - cyc * 4.2;
                        Vec3 fPos = targetRel.add(Math.cos(ang) * funnelR, fy, Math.sin(ang) * funnelR);
                        float sa = funnelFade * (float) Math.sin(cyc * Math.PI) * 0.92F;
                        double fSize = 0.22 + 0.24 * h;
                        emitBloomingFourPointStar(starVc, vortexVc, mat, fPos, camRight, camUp, fSize, i % 5, sa);
                    }
                }

                float batchBloom = shadersActive ? bloom * 0.64F : bloom;
                ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_VORTEX_RING, batchBloom);
                ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.LIGHT, Math.max(0.45F, batchBloom));

                // =========================================================================
                // PASS 3: ZOL_GLOW (Crisp Anamorphic Horizontal Sky Glint across Target Rings)
                // =========================================================================
                if (p > 0.50F) {
                    float glintP = ExplosionMagicRenderer.smoothstep((p - 0.50F) / 0.25F) * fade;
                    VertexConsumer flareVc = buffers.getBuffer(ZoltraakRenderTypes.ZOL_GLOW);
                    Vec3 topRingCenter = targetRel.add(0.0, radius * ExplosionMagicRenderer.SKY_RING_HEIGHT_FACTORS[4], 0.0);
                    double glintW = radius * 1.15;
                    ExplosionMagicRenderer.emitBillboardQuad(
                            flareVc, mat, topRingCenter, camRight, camUp, glintW, 1.3,
                            1.0F, 0.86F, 0.34F, glintP * (shadersActive ? 0.52F : 0.82F)
                    );
                    ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.ZOL_GLOW, batchBloom);
                }

                // =========================================================================
                // PASS 4: BLACK_CORE (18 Craggy Dark Rocks Rising & Spiraling Into Staff Orb)
                // =========================================================================
                if (p > 0.18F) {
                    float rFade = ExplosionMagicRenderer.smoothstep((p - 0.18F) / 0.20F) * fade;
                    VertexConsumer rockVc = buffers.getBuffer(ZoltraakRenderTypes.BLACK_CORE);
                    int floatRocks = 18;
                    for (int i = 0; i < floatRocks; i++) {
                        float h = ExplosionMagicRenderer.hashFloat(i * 67 + 3);
                        float liftCycle = (time * (0.015F + 0.008F * p) + i / (float) floatRocks) % 1.0F;
                        double ang = i * (Math.PI * 2.0 / floatRocks) + time * 0.035 + liftCycle * 1.8;
                        double startR = (isFpLocal ? 2.4 : 3.6) + 0.8 * h;
                        Vec3 orbitPos = casterPos.add(Math.cos(ang) * startR * (1.0 - 0.55 * liftCycle), 0.20 + liftCycle * 3.8, Math.sin(ang) * startR * (1.0 - 0.55 * liftCycle));
                        double pullToStaff = Math.pow(liftCycle, 2.2) * Mth.clamp((p - 0.40F) / 0.60F, 0.0F, 0.85F);
                        Vec3 rockPos = orbitPos.lerp(staffRel, pullToStaff);
                        float rockVis = (float) Math.sin(liftCycle * Math.PI) * rFade;
                        ExplosionMagicRenderer.emitCraggyRockBoulder(
                                rockVc, mat, rockPos, staffRel, (0.14 + 0.15 * h) * (1.0 - 0.35 * pullToStaff),
                                time * 0.08F + i, time * 0.06F + h * 3.0F, i * 13, rockVis
                        );
                    }
                    ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.BLACK_CORE, 1.0F);
                }
            }
        } finally {
            pose.popPose();
        }
    }

    private static void emitIrisChargeProxy(
            MultiBufferSource.BufferSource buffers, Matrix4f mat,
            Vec3 casterPos, Vec3 staffRel, Vec3 targetRel, Vec3 camRight, Vec3 camUp,
            double radius, float p, float time, float fade, boolean isFpLocal
    ) {
        VertexConsumer irisVc = buffers.getBuffer(ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
        Vec3 axU = new Vec3(1.0, 0.0, 0.0);
        Vec3 axV = new Vec3(0.0, 0.0, 1.0);

        // Keep Iris Phase 1 emissive proxies restrained so shaderpack HDR bloom accents rings without washing out details.
        float groundA = fade * (isFpLocal ? 0.07F : 0.18F);
        float gg = ZoltraakRenderPass.irisFadeGuard(groundA);
        ZoltraakRenderPass.emitFeatheredRing(
                irisVc, mat, casterPos.add(0.0, 0.10, 0.0), axU, axV,
                2.8, 3.6, 4.4, 24, 0.96F * gg, 0.32F * gg, 0.05F * gg, groundA
        );

        // Caster overhead thin rim rings
        if (p > 0.14F) {
            float ca = fade * (isFpLocal ? 0.06F : 0.14F);
            float cg = ZoltraakRenderPass.irisFadeGuard(ca);
            ZoltraakRenderPass.emitFeatheredRing(irisVc, mat, casterPos.add(0.0, 4.4, 0.0), axU, axV, 1.8, 2.2, 2.6, 20, 0.96F * cg, 0.28F * cg, 0.05F * cg, ca);
            ZoltraakRenderPass.emitFeatheredRing(irisVc, mat, casterPos.add(0.0, 7.8, 0.0), axU, axV, 3.1, 3.8, 4.5, 20, 0.96F * cg, 0.28F * cg, 0.05F * cg, ca);
        }

        // Cyan-blue fountain ribbons subtle HDR accent for Iris shaderpacks (heavily dampened in first-person)
        if (!isFpLocal || p > 0.14F) {
            float ribA = fade * (isFpLocal ? 0.05F : 0.16F);
            float bg = ZoltraakRenderPass.irisFadeGuard(ribA);
            int ribbons = 8;
            for (int a = 0; a < ribbons; a++) {
                double phase = a * (Math.PI * 2.0 / ribbons) + time * 0.048;
                double baseR = isFpLocal ? 2.3 : 3.6;
                Vec3 root = casterPos.add(Math.cos(phase) * baseR, 0.12, Math.sin(phase) * baseR);
                Vec3 mid1 = casterPos.add(Math.cos(phase + 0.48) * baseR * 0.90, isFpLocal ? 2.6 : 4.2, Math.sin(phase + 0.48) * baseR * 0.90);
                Vec3 mid2 = staffRel.add(Math.cos(phase + 1.05) * (isFpLocal ? 0.85 : 1.45), isFpLocal ? 1.1 : 1.9, Math.sin(phase + 1.05) * (isFpLocal ? 0.85 : 1.45));
                Vec3 prev = root;
                for (int s = 1; s <= 6; s++) {
                    double u = s / 6.0;
                    double mu = 1.0 - u;
                    Vec3 pt = root.scale(mu * mu * mu).add(mid1.scale(3 * mu * mu * u)).add(mid2.scale(3 * mu * u * u)).add(staffRel.scale(u * u * u));
                    Vec3 side = pt.subtract(prev).cross(prev.scale(-1.0));
                    side = side.lengthSqr() < 1.0E-5 ? camRight : side.normalize();
                    ZoltraakRenderPass.emitFeatheredBeamSegment(
                            irisVc, mat, prev, pt, side, 0.05, 0.16,
                            0.36F * bg, 0.84F * bg, 1.0F * bg, ribA,
                            0.04F * bg, 0.48F * bg, 0.96F * bg, ribA * 0.45F
                    );
                    prev = pt;
                }
            }
        }

        // Target sky vortex column crisp thin rim glow (avoids yellow solid fill under Iris bloom)
        if (p > 0.14F) {
            float skyAlpha = ExplosionMagicRenderer.smoothstep((p - 0.14F) / 0.24F) * fade * 0.16F;
            float g = ZoltraakRenderPass.irisFadeGuard(skyAlpha);
            double[] hRatio = ExplosionMagicRenderer.SKY_RING_HEIGHT_FACTORS;
            double[] sRatio = ExplosionMagicRenderer.SKY_RING_SCALE_FACTORS;
            for (int i = 0; i < hRatio.length; i++) {
                double ry = radius * hRatio[i];
                double rr = radius * sRatio[i];
                ZoltraakRenderPass.emitFeatheredRing(
                        irisVc, mat, targetRel.add(0.0, ry, 0.0), axU, axV,
                        rr * 0.72, rr * 0.81, rr * 0.90, 24,
                        0.96F * g, 0.30F * g, 0.05F * g, skyAlpha
                );
            }
        }

        float orbA = (0.20F + 0.14F * p) * fade * (isFpLocal ? 0.22F : 0.75F);
        float og = ZoltraakRenderPass.irisFadeGuard(orbA);
        ZoltraakRenderPass.emitFeatheredStarBurst(
                irisVc, mat, staffRel, camRight, camUp, (0.42 + 0.56 * p) * (isFpLocal ? 0.30 : 0.85),
                0.96F * og, 0.30F * og, 0.84F * og, orbA
        );
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

    private static void emitCyanFountainRibbon(
            VertexConsumer vc, Matrix4f mat,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
            float progress, float seedNorm, double width, float alpha
    ) {
        final int segs = 20;
        Vec3[] pts = new Vec3[segs + 1];
        Vec3[] edges = new Vec3[segs + 1];

        for (int i = 0; i <= segs; i++) {
            double u = i / (double) segs;
            double mu = 1.0 - u;
            pts[i] = a.scale(mu * mu * mu)
                    .add(b.scale(3.0 * mu * mu * u))
                    .add(c.scale(3.0 * mu * u * u))
                    .add(d.scale(u * u * u));
        }

        for (int i = 0; i <= segs; i++) {
            double u = i / (double) segs;
            Vec3 tang = pts[Math.min(segs, i + 1)].subtract(pts[Math.max(0, i - 1)]);
            Vec3 side = tang.cross(pts[i].scale(-1.0));
            side = side.lengthSqr() < 1.0E-5 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            double profile = Math.pow(Math.sin(Math.PI * u), 0.62);
            edges[i] = side.scale(width * profile);
        }

        for (int i = 0; i < segs; i++) {
            float u0 = i / (float) segs;
            float u1 = (i + 1) / (float) segs;
            Vec3 l0 = pts[i].subtract(edges[i]);
            Vec3 l1 = pts[i + 1].subtract(edges[i + 1]);
            Vec3 r1 = pts[i + 1].add(edges[i + 1]);
            Vec3 r0 = pts[i].add(edges[i]);

            vc.addVertex(mat, (float) l0.x, (float) l0.y, (float) l0.z).setUv(u0, 0.0F).setColor(progress, seedNorm, 0.84F, alpha);
            vc.addVertex(mat, (float) l1.x, (float) l1.y, (float) l1.z).setUv(u1, 0.0F).setColor(progress, seedNorm, 0.84F, alpha);
            vc.addVertex(mat, (float) r1.x, (float) r1.y, (float) r1.z).setUv(u1, 1.0F).setColor(progress, seedNorm, 0.84F, alpha);
            vc.addVertex(mat, (float) r0.x, (float) r0.y, (float) r0.z).setUv(u0, 1.0F).setColor(progress, seedNorm, 0.84F, alpha);
        }
    }
}
