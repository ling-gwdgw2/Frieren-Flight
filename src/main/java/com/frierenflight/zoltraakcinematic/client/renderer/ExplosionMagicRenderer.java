// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import com.frierenflight.zoltraakcinematic.entity.ExplosionMagicEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ExplosionMagicRenderer
 * Complete stylized Explosion Magic detonation sequence with volumetric bloom & Iris HDR bloom proxies:
 * - Stage 1 (t = 0..6.5): Soft-blooming diagonal beam from staff UP to top sky ring apex + 2 tilted coaxial smoke rings
 *   + lingering blooming sparkle trail
 * - Stage 2 (t = 2.4..9.0): Super-wide horizontal golden anamorphic lens-flare slash at sky apex
 * - Stage 3 (t = 4.0..52.0): Persistent soft-blooming vertical laser pillar (blazing wide column
 *   constricting into razor needle) + 4 concentric laser rings + apex entry hotspot
 * - Stage 4 (dt = 0..96):
 *   - Blinding golden-white floor & shock flash discs
 *   - Fast translucent spherical pressure shockwave shell (`shellLayer = 0.60`, dt = 0..16)
 *   - Cel sunburst fireball dome -> dark magma blotches -> painterly smoky starburst soot dissolve (`shellLayer = 0.0`)
 *   - Exterior 1.38x volumetric crimson-gold bloom corona (`shellLayer = 1.0`, floor-feathered)
 *   - Swirling golden-orange flame ribbons orbiting the soot perimeter (`Mode 5`, g = 0.85)
 *   - Ambient crimson-amber interior afterglow + 2.2x ground bloom + 3 concentric flat glowing ground crater ripples + 18 ember puddles
 *   - Two tiers of cream-white scalloped condensation clouds (Upper Crown collar `0.74h` + Mid Waist collar `0.35h`)
 *   - Sculpted rolling brown ground dust wall (flush cleanly above y=0) + 26 dark flying soot-smoke chunks
 *   - 56 craggy 3D tumbling dark boulders + 80 soft-blooming radial speed streaks + 120 twinkling blooming embers
 */
public class ExplosionMagicRenderer extends EntityRenderer<ExplosionMagicEntity> {

    public static final float BLAST_DURATION = 192.0F;

    /**
     * Vertical sky vortex column profile raised cleanly above the peak fireball dome crown (~26.8..69.3 blocks at 1.0x),
     * preserving the non-monotonic bulge-and-waist proportions and scaling with explosionSkyHeight.
     */
    public static final double SKY_RING_BASE_LIFT = 22.0;
    public static final float SKY_COLUMN_SEED = 0.42F;
    public static final double[] SKY_RING_HEIGHT_FACTORS = { 0.22, 0.42, 0.64, 0.88, 1.14, 1.42, 1.72, 2.15 };
    public static final double[] SKY_RING_SCALE_FACTORS  = { 0.26, 0.20, 0.46, 0.72, 0.48, 0.38, 0.30, 1.18 };

    public static double getSkyRingY(double radius, int ringIndex) {
        return (SKY_RING_BASE_LIFT + radius * SKY_RING_HEIGHT_FACTORS[ringIndex])
                * ZoltraakCinematicClientConfig.getExplosionSkyHeight();
    }

    public static double getSkyRingRadius(double radius, int ringIndex) {
        return radius * SKY_RING_SCALE_FACTORS[ringIndex]
                * ZoltraakCinematicClientConfig.getExplosionSkyRingScale();
    }

    public static double getSkyApexHeight(double radius) {
        return getSkyRingY(radius, SKY_RING_HEIGHT_FACTORS.length - 1);
    }

    private static final Set<ExplosionMagicEntity> ACTIVE_ENTITIES =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<Integer, DeferredExplosion> DEFERRED = new LinkedHashMap<>();

    private record DeferredExplosion(
            ExplosionMagicEntity entity,
            float partial,
            PoseStack pose,
            Matrix4f modelView,
            Matrix4f projection
    ) {}

    public ExplosionMagicRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    public static void registerClientInstance(ExplosionMagicEntity entity) {
        if (entity != null) {
            ACTIVE_ENTITIES.add(entity);
        }
    }

    public static void unregisterClientInstance(ExplosionMagicEntity entity) {
        if (entity != null) {
            ACTIVE_ENTITIES.remove(entity);
        }
    }

    public static void resetState() {
        ACTIVE_ENTITIES.clear();
        clearDeferred();
    }

    public static boolean hasDeferredDraw() {
        return !DEFERRED.isEmpty();
    }

    public static void clearDeferred() {
        DEFERRED.clear();
    }

    public static boolean hasActiveEntityNear(Minecraft mc, Vec3 targetWorld) {
        if (mc == null || mc.level == null || targetWorld == null) return false;
        double maxDistSq = 24.0 * 24.0;
        for (ExplosionMagicEntity exp : ACTIVE_ENTITIES) {
            if (exp != null && !exp.isRemoved() && exp.level() == mc.level && exp.tickCount < 60) {
                if (exp.position().distanceToSqr(targetWorld) <= maxDistSq
                        || exp.getTargetPos().distanceToSqr(targetWorld) <= maxDistSq) {
                    return true;
                }
            }
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ExplosionMagicEntity exp && !exp.isRemoved() && exp.tickCount < 60) {
                if (exp.position().distanceToSqr(targetWorld) <= maxDistSq
                        || exp.getTargetPos().distanceToSqr(targetWorld) <= maxDistSq) {
                    return true;
                }
            }
        }
        return false;
    }

    static List<ExplosionMagicEntity> getActiveClientEntities(Minecraft mc, float partial) {
        List<ExplosionMagicEntity> active = new ArrayList<>();
        ACTIVE_ENTITIES.removeIf(e -> e == null || e.isRemoved() || e.level() != mc.level
                || (e.tickCount + partial) > e.getLifetimeTicks() + 4.0F);
        active.addAll(ACTIVE_ENTITIES);
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof ExplosionMagicEntity exp && !exp.isRemoved()
                        && (exp.tickCount + partial) <= exp.getLifetimeTicks()
                        && !active.contains(exp)) {
                    ACTIVE_ENTITIES.add(exp);
                    active.add(exp);
                }
            }
        }
        return active;
    }

    @Override
    public boolean shouldRender(ExplosionMagicEntity entity, Frustum camera, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ExplosionMagicEntity entity) {
        return ZoltraakRenderPass.PROXY_TEX;
    }

    @Override
    public void render(ExplosionMagicEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        // Safeguard registration; main rendering runs via RenderLevelStageEvent so Sodium/LevelRenderer culling never hides blasts.
        registerClientInstance(entity);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (ShaderCompatibility.isShadowPass()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clearDeferred();
            return;
        }

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<ExplosionMagicEntity> active = getActiveClientEntities(mc, partial);
        if (active.isEmpty()) {
            clearDeferred();
            return;
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera != null ? camera.getPosition() : mc.gameRenderer.getMainCamera().getPosition();

        if (ShaderCompatibility.useFullDetailPass()) {
            DEFERRED.clear();
            Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix());
            Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
            for (ExplosionMagicEntity exp : active) {
                Vec3 entPos = exp.getPosition(partial);
                PoseStack copy = new PoseStack();
                copy.last().pose().set(event.getPoseStack().last().pose());
                copy.last().normal().set(event.getPoseStack().last().normal());
                copy.translate(entPos.x - camPos.x, entPos.y - camPos.y, entPos.z - camPos.z);
                DEFERRED.put(exp.getId(), new DeferredExplosion(exp, partial, copy, mv, proj));
            }
            return;
        }

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        for (ExplosionMagicEntity exp : active) {
            Vec3 entPos = exp.getPosition(partial);
            pose.pushPose();
            pose.translate(entPos.x - camPos.x, entPos.y - camPos.y, entPos.z - camPos.z);
            renderInternal(exp, partial, pose.last().pose(), buffers, false);
            pose.popPose();
        }
    }

    public static void renderIrisPhase1(MultiBufferSource.BufferSource buffers, Matrix4fStack modelView) {
        if (DEFERRED.isEmpty()) return;
        float glow = ZoltraakCinematicClientConfig.getExplosionGlow();
        if (glow <= 0.004F) return;

        for (DeferredExplosion d : DEFERRED.values()) {
            if (d.entity.isRemoved()) continue;
            modelView.set(d.modelView);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(d.projection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
            ZoltraakRenderPass.renderIrisScaled(buffers, glow, () -> {
                renderInternal(d.entity, d.partial, d.pose.last().pose(), buffers, true);
                ZoltraakRenderTypes.endIrisBatch(buffers, ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
            });
        }
    }

    public static void renderDeferred(MultiBufferSource.BufferSource buffers, Matrix4fStack modelView) {
        if (DEFERRED.isEmpty()) return;
        for (DeferredExplosion d : DEFERRED.values()) {
            if (d.entity.isRemoved()) continue;
            modelView.set(d.modelView);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(d.projection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
            renderInternal(d.entity, d.partial, d.pose.last().pose(), buffers, false);
        }
    }

    private static void renderInternal(
            ExplosionMagicEntity entity,
            float partial,
            Matrix4f mat,
            MultiBufferSource buffers,
            boolean irisBloomProxy
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float timeScale = entity.getTimeScale();
        float t = (entity.tickCount + partial) / timeScale;
        if (t < 0.0F || t > ExplosionMagicEntity.LIFETIME_TICKS) return;

        float bloom = ZoltraakCinematicClientConfig.getExplosionGlow();
        Vec3 entPos = entity.getPosition(partial);
        Vec3 targetPos = entity.getTargetPos();
        Vec3 startPos = entity.getStartPos();
        boolean uninitializedSync = entity.tickCount == 0 && targetPos.distanceToSqr(entPos) > 16.0;
        if (uninitializedSync) {
            targetPos = entPos;
        }
        if (startPos.distanceToSqr(targetPos) > 256.0 * 256.0) {
            startPos = targetPos;
        }
        Vec3 relTarget = targetPos.subtract(entPos);
        Vec3 relStart = startPos.subtract(entPos);

        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 relCam = camPos.subtract(entPos);
        Vec3 camRight = new Vec3(new Vector3f(1.0F, 0.0F, 0.0F).rotate(mc.getEntityRenderDispatcher().cameraOrientation()));
        Vec3 camUp = new Vec3(new Vector3f(0.0F, 1.0F, 0.0F).rotate(mc.getEntityRenderDispatcher().cameraOrientation()));

        double radius = uninitializedSync
                ? com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicConfig.getExplosionRadius()
                : entity.getRadius();
        int seed = entity.getSeed();
        float seedNorm = (Math.abs(seed % 1000)) / 1000.0F;
        float dt = t - ExplosionMagicEntity.DETONATE_TICK;

        if (irisBloomProxy) {
            emitIrisEmissiveProxy(buffers, mat, relTarget, relStart, relCam, camRight, camUp, radius, t, dt);
            return;
        }

        boolean shadersActive = ShaderCompatibility.useFullDetailPass() && ZoltraakCinematicClientConfig.isWorldGlowActive(true);
        float vfxBloom = shadersActive ? bloom * 0.86F : bloom;
        float shaderHaloMult = shadersActive ? 0.68F : 1.0F;

        // 1. Strike Sequence: Diagonal Beam -> Sky Flare -> Vertical Pillar & 8 Sky Rings fading together (t = 0 .. 50)
        if (t < 50.0F) {
            double worldTime = (mc.player != null ? mc.player.tickCount : entity.tickCount) + partial;
            emitStrikeAndPersistentLaser(buffers, mat, relTarget, relStart, relCam, camRight, camUp, radius, t, worldTime, seed, seedNorm, vfxBloom);
        }

        // 2. Detonation & Slow-Burning Pyroclastic Aftermath Sequence (dt = -0.5 .. 192)
        if (dt >= -0.5F && dt < BLAST_DURATION) {
            float safeDt = Math.max(0.0F, dt);
            float p = Mth.clamp(safeDt / BLAST_DURATION, 0.0F, 1.0F);
            float domeP = Mth.clamp(safeDt / 160.0F, 0.0F, 1.0F);
            double visualR = radius * ZoltraakCinematicClientConfig.getExplosionDomeScale();
            float blastScale = (float) (Math.pow(Mth.clamp(safeDt / 16.0F, 0.0F, 1.0F), 0.34) * (0.85 + 0.16 * domeP));
            double domeRadius = visualR * 0.88 * blastScale;

            // 2a. Boiling Cellular Ignition Core + 3D Volumetric Raymarched Mushroom Cloud + Supersonic Mach Shells
            Vec3 domeCenter = relTarget.add(0.0, domeRadius * 0.08, 0.0);
            float heatFlash = (float) Math.exp(-safeDt * 0.058);
            float domeFade = 1.0F - smoothstep((domeP - 0.56F) / 0.22F);
            float domeAnimT = safeDt * 0.042F;

            // 2a-1. Early Blinding Cellular Core Shell (hands off seamlessly into the 3D raymarched mushroom cloud)
            if (domeFade > 0.005F && domeRadius > 0.25 && domeP < 0.78F) {
                VertexConsumer domeVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_DOME);
                emitPyroclasticExplosionDome(
                        domeVc, mat, domeCenter, domeRadius * 0.92,
                        domeP, heatFlash, 0.0F, domeFade * 0.88F, domeAnimT, 0.055F + 0.065F * domeP
                );
                ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_DOME, Math.max(0.45F, bloom));
            }

            // 2a-2. Phase 2: True 3D Volumetric Raymarched Anime Mushroom Cloud & Pyroclastic Fireball (dt = 0..186)
            if (safeDt < 186.0F) {
                float pyroP = Mth.clamp(safeDt / 186.0F, 0.0F, 1.0F);
                float pyroFade = smoothstep(pyroP / 0.025F) * (1.0F - smoothstep((pyroP - 0.78F) / 0.22F));
                if (pyroFade > 0.006F) {
                    double boundR = visualR * (1.18 + 0.44 * Math.pow(pyroP, 0.42));
                    Vec3 pyroCenter = relTarget.add(0.0, boundR * 0.42, 0.0);
                    boolean camInside = relCam.distanceTo(pyroCenter) <= boundR + 0.65;
                    VertexConsumer pyroVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_PYROCLAST);
                    emitVolumetricMushroomSphere(
                            pyroVc, mat, pyroCenter, boundR, 18, 28,
                            (float) boundR, seedNorm,
                            pyroP, heatFlash, camInside ? 1.0F : 0.0F, pyroFade
                    );
                    ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_PYROCLAST, Math.max(0.55F, bloom));
                }
            }

            // 2a-3. Exterior 1.48x Volumetric Corona & Double Translucent Supersonic Mach Shockwave Shells
            if (bloom > 0.004F && domeRadius > 0.25 && domeP < 0.86F) {
                VertexConsumer haloVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_DOME_HALO);
                if (domeP < 0.74F && domeFade > 0.005F) {
                    emitPyroclasticExplosionDome(
                            haloVc, mat, domeCenter, domeRadius * 1.48,
                            domeP, heatFlash, 1.0F, domeFade * (shadersActive ? 0.64F : 0.86F), domeAnimT, 0.030F
                    );
                }
                if (safeDt < 26.0F) {
                    float sp = safeDt / 26.0F;
                    double pressureR = visualR * (0.68 + 1.24 * Math.pow(sp, 0.50));
                    emitPyroclasticExplosionDome(
                            haloVc, mat, domeCenter, pressureR,
                            sp, 1.0F, 0.60F, (1.0F - sp) * (shadersActive ? 0.84F : 1.0F), domeAnimT, 0.015F
                    );
                }
                if (safeDt >= 4.0F && safeDt < 42.0F) {
                    float sp2 = (safeDt - 4.0F) / 38.0F;
                    double pressureR2 = visualR * (0.82 + 1.48 * Math.pow(sp2, 0.55));
                    emitPyroclasticExplosionDome(
                            haloVc, mat, domeCenter, pressureR2,
                            sp2, 0.65F, 0.60F, (1.0F - sp2) * 0.62F * (shadersActive ? 0.78F : 1.0F), domeAnimT, 0.012F
                    );
                }
                ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_DOME_HALO, vfxBloom);
            }

            // 2b. Mode 5 Pure Radial Gaussian Blooms, Swirling Gold Flame Arcs & Ground Crater Ripples (EXPLOSION_VORTEX_RING)
            if (bloom > 0.004F) {
                VertexConsumer bloomVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_VORTEX_RING);

                // Blinding golden-white ignition flash at dt = 0..18
                if (safeDt < 18.0F) {
                    float flashFade = (float) Math.exp(-safeDt * 0.22) * (1.0F - smoothstep((safeDt - 10.0F) / 8.0F));
                    double flashR = visualR * (1.42 + 1.75 * smoothstep(safeDt / 14.0F));
                    emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, 0.14, 0.0), flashR, 0.0, 0.96F, 0.10F, 0.98F, flashFade * (shadersActive ? 0.68F : 0.88F));
                    double airR = flashR * 0.68;
                    emitBillboardQuad(bloomVc, mat, relTarget.add(0.0, airR * 0.92, 0.0), camRight, camUp, flashR * 0.95, airR, 0.92F, 0.10F, 0.98F, flashFade * (shadersActive ? 0.64F : 0.82F));
                }

                // Wide Golden-Amber Ground Light Disc (visualR * 2.25) + Base Crater Fire Skirt Blend
                float groundGlowLife = (1.0F - smoothstep(0.0F, 0.90F, p));
                if (groundGlowLife > 0.01F) {
                    double groundLightR = Math.max(visualR * 1.15, domeRadius * 2.25);
                    float palTint = Mth.lerp(smoothstep(0.28F, 0.78F, domeP), 0.74F, 0.46F);
                    emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, 0.07, 0.0), groundLightR, 0.0, palTint, 0.10F, 0.98F, groundGlowLife * 0.46F * shaderHaloMult);
                    if (domeRadius > 0.5 && domeP < 0.74F) {
                        float baseBlend = (1.0F - smoothstep(0.0F, 0.74F, domeP)) * 0.54F * shaderHaloMult;
                        emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, 0.14, 0.0), domeRadius * 1.28, 0.0, 0.60F, 0.10F, 0.98F, baseBlend);
                    }
                }

                // Bright entry hotspot where vertical laser meets top crown of dome (fades with pillar & 8 sky rings at t = 50)
                if (safeDt < 22.0F && domeRadius > 0.5) {
                    float capAlpha = (1.0F - smoothstep(0.0F, 22.0F, safeDt)) * (shadersActive ? 0.76F : 0.90F);
                    double capR = visualR * (0.34 + 0.16 * (1.0F - safeDt / 22.0F));
                    emitBillboardQuad(bloomVc, mat, relTarget.add(0.0, domeRadius * 0.98, 0.0), camRight, camUp, capR, capR * 0.65, 0.96F, 0.10F, 0.98F, capAlpha);
                }

                // Swirling Golden-Orange Flame Arcs orbiting soot dome perimeter (Mode 5, g = 0.85)
                if (p >= 0.16F && p < 0.72F && domeRadius > 1.0) {
                    float arcP = (p - 0.16F) / 0.56F;
                    float arcAlpha = (float) Math.sin(arcP * Math.PI) * 0.90F;
                    emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, domeRadius * 0.22, 0.0), domeRadius * 1.18, safeDt * 0.032, 0.74F, 0.85F, 0.98F, arcAlpha);
                    emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, domeRadius * 0.48, 0.0), domeRadius * 0.94, -safeDt * 0.042 + 1.4, 0.68F, 0.85F, 0.98F, arcAlpha * 0.80F);
                }

                // Soft Crimson-Amber Interior Volumetric Afterglow cloud hovering cleanly above ground
                if (p >= 0.36F && p < 0.96F) {
                    float hazeP = (p - 0.36F) / 0.60F;
                    float hazeAlpha = (float) Math.sin(hazeP * Math.PI) * 0.46F * shaderHaloMult;
                    double hazeR = visualR * 1.02;
                    emitBillboardQuad(bloomVc, mat, relTarget.add(0.0, hazeR * 0.54, 0.0), camRight, camUp, hazeR * 1.15, hazeR * 0.56, 0.48F, 0.10F, 0.98F, hazeAlpha);
                }

                // Molten Crater Fracture & Superheated Magma Fissure Ground Decal (Mode 5, seed = 0.68, dt = 1..188)
                if (safeDt >= 1.0F && safeDt < 188.0F) {
                    float fP = (safeDt - 1.0F) / 187.0F;
                    float fEnv = smoothstep(fP / 0.06F) * (1.0F - smoothstep((fP - 0.58F) / 0.42F));
                    double crackR = visualR * (0.85 + 0.52 * smoothstep(fP / 0.24F));
                    emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, 0.12, 0.0), crackR, seedNorm * Math.PI * 2.0, fP, 0.68F, 0.98F, fEnv * 0.94F);
                }

                // 12 Crackling Surface Plasma Discharge Lightning Arcs crawling across the fireball (Mode 4a, dt = 0..58)
                if (safeDt < 58.0F && domeRadius > 1.0) {
                    float boltLife = (1.0F - smoothstep(26.0F, 58.0F, safeDt)) * (shadersActive ? 0.76F : 0.94F);
                    int boltStep = (int) (safeDt * 0.60F);
                    for (int i = 0; i < 12; i++) {
                        float hb1 = hashFloat(seed + boltStep * 37 + i * 29 + 5);
                        float hb2 = hashFloat(seed + boltStep * 53 + i * 41 + 11);
                        float hb3 = hashFloat(seed + boltStep * 71 + i * 59 + 17);
                        double thA = hb1 * Math.PI * 2.0;
                        double phA = (0.08 + 0.65 * hb2) * (Math.PI * 0.5);
                        double thB = thA + (hb3 - 0.5) * 1.45;
                        double phB = Math.max(0.05, Math.min(1.42, phA + (hb1 - 0.5) * 0.75));
                        double surfR = domeRadius * (0.96 + 0.14 * hb3);
                        Vec3 bA = relTarget.add(Math.cos(phA) * Math.cos(thA) * surfR, Math.sin(phA) * surfR + domeRadius * 0.08, Math.cos(phA) * Math.sin(thA) * surfR);
                        Vec3 bB = relTarget.add(Math.cos(phB) * Math.cos(thB) * surfR, Math.sin(phB) * surfR + domeRadius * 0.08, Math.cos(phB) * Math.sin(thB) * surfR);
                        emitSoftBloomBeamQuad(bloomVc, mat, bA, bB, relCam, 0.42 + 0.36 * hb2, 0.18F, 0.42F + 0.26F * hb3, boltLife);
                    }
                }

                // 3 Concentric Flat Glowing Gold Ground Crater Ripples + 22 Ember Puddles (Mode 5, g = 0.50 & g = 0.10)
                if (safeDt >= 4.0F && safeDt < 188.0F) {
                    float cP = (safeDt - 4.0F) / 184.0F;
                    float cEnv = smoothstep(cP / 0.10F) * (1.0F - smoothstep((cP - 0.58F) / 0.42F));
                    if (cP > 0.28F) {
                        float rEnv = (float) Math.sin(Math.min(1.0F, (cP - 0.28F) / 0.68F) * Math.PI) * 0.85F;
                        emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, 0.09, 0.0), visualR * 0.40, 0.0, 0.78F, 0.50F, 0.98F, rEnv);
                        emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, 0.10, 0.0), visualR * 0.68, 0.0, 0.74F, 0.50F, 0.98F, rEnv * 0.76F);
                        emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, 0.11, 0.0), visualR * 0.96, 0.0, 0.68F, 0.50F, 0.98F, rEnv * 0.56F);
                    }
                    int puddles = 22;
                    for (int i = 0; i < puddles; i++) {
                        float h1 = hashFloat(seed + i * 37 + 201);
                        float h2 = hashFloat(seed + i * 59 + 211);
                        double pAng = i * (Math.PI * 2.0 / puddles) + h1 * 0.45;
                        double pDist = visualR * (0.42 + 1.02 * h2);
                        double pSize = 0.60 + 0.75 * h1;
                        Vec3 puddlePos = relTarget.add(Math.cos(pAng) * pDist, 0.09, Math.sin(pAng) * pDist);
                        emitHorizDiscQuad(bloomVc, mat, puddlePos, pSize, 0.0, 0.74F + 0.10F * h1, 0.10F, 0.98F, cEnv * 0.92F);
                    }
                }

                ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_VORTEX_RING, vfxBloom);
            }

            // 2c. Crisp Expanding Outer Orange Shock Ring + Twin Flat Ground Pressure Rings (LIGHT)
            if (bloom > 0.004F) {
                VertexConsumer ringVc = buffers.getBuffer(ZoltraakRenderTypes.LIGHT);
                if (p >= 0.14F && p < 0.72F) {
                    float sp = (p - 0.14F) / 0.58F;
                    float sAlpha = (float) Math.sin(sp * Math.PI) * 0.94F;
                    double shockR = domeRadius * (0.94 + 0.36 * sp);
                    emitThinHorizRing(ringVc, mat, relTarget.add(0.0, domeRadius * 0.24, 0.0), shockR, 0.30, 40, 1.0F, 0.60F, 0.10F, sAlpha);
                }
                if (safeDt >= 1.0F && safeDt < 58.0F) {
                    float gp = (safeDt - 1.0F) / 57.0F;
                    float gAlpha = (1.0F - smoothstep(0.32F, 1.0F, gp)) * 0.78F;
                    double gShockR = visualR * (0.72 + 1.92 * Math.pow(gp, 0.54));
                    emitThinHorizRing(ringVc, mat, relTarget.add(0.0, 0.18, 0.0), gShockR, 0.42 * (1.0 - 0.5 * gp), 44, 0.98F, 0.90F, 0.72F, gAlpha);
                }
                if (safeDt >= 6.0F && safeDt < 76.0F) {
                    float gp2 = (safeDt - 6.0F) / 70.0F;
                    float gAlpha2 = (1.0F - smoothstep(0.30F, 1.0F, gp2)) * 0.60F;
                    double gShockR2 = visualR * (0.56 + 1.72 * Math.pow(gp2, 0.58));
                    emitThinHorizRing(ringVc, mat, relTarget.add(0.0, 0.14, 0.0), gShockR2, 0.28 * (1.0 - 0.45 * gp2), 40, 1.0F, 0.62F, 0.18F, gAlpha2);
                }
                ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.LIGHT, bloom);
            }

            // 2d. Rising Mushroom Cloud Collars + Dark Soot Smoke Chunks + Long-Sweeping Ground Dust Wave
            VertexConsumer dustVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_DUST_WAVE);

            // Rising Crown & Waist Cloud Collars (Type 0.0, dt = 1.0..164)
            if (safeDt >= 1.0F && safeDt < 164.0F) {
                float wp = Mth.clamp((safeDt - 1.0F) / 163.0F, 0.0F, 1.0F);
                float wFade = smoothstep(wp / 0.05F) * (1.0F - smoothstep((wp - 0.56F) / 0.44F));
                if (wFade > 0.008F) {
                    float crownFade = wFade * (1.0F - smoothstep(0.42F, 0.82F, wp));
                    if (crownFade > 0.01F) {
                        int crownCount = 32;
                        double crownR = domeRadius * (0.54 + 0.22 * wp);
                        double crownY = domeRadius * (0.72 + 0.54 * wp);
                        double cHalfW = domeRadius * (0.19 + 0.09 * wp);
                        double cHalfH = domeRadius * (0.082 + 0.042 * wp);
                        for (int i = 0; i < crownCount; i++) {
                            float hj = hashFloat(seed + i * 31 + 101);
                            float hw = hashFloat(seed + i * 43 + 113);
                            double ang = i * (Math.PI * 2.0 / crownCount) + seedNorm * 4.1 + (hj - 0.5) * 0.18 + wp * 0.25;
                            double tierScale = (i % 2 == 0) ? 1.0 : 0.86;
                            double rCur = crownR * tierScale * (0.92 + 0.16 * hj);
                            double yCur = crownY + cHalfH * ((i % 2 == 0 ? 0.25 : -0.18) + 0.32 * (hw - 0.5));
                            Vec3 cPos = relTarget.add(Math.cos(ang) * rCur, yCur, Math.sin(ang) * rCur);
                            float cSeed = ((i * 41 + Math.abs(seed % 83)) % 100) / 100.0F;
                            emitBillboardQuad(
                                    dustVc, mat, cPos, camRight, camUp,
                                    cHalfW * (0.86 + 0.28 * hw), cHalfH * (0.82 + 0.34 * hj),
                                    wp, cSeed, 0.0F, crownFade * 0.56F
                            );
                        }
                    }

                    int waistCount = 38;
                    double beltR = domeRadius * (0.94 + 0.44 * Math.pow(wp, 0.68));
                    double waistY = domeRadius * (0.35 + 0.14 * wp);
                    double halfW = domeRadius * (0.22 + 0.10 * wp);
                    double halfH = domeRadius * (0.095 + 0.044 * wp);
                    for (int i = 0; i < waistCount; i++) {
                        float hj = hashFloat(seed + i * 23 + 211);
                        float hw = hashFloat(seed + i * 37 + 223);
                        double ang = i * (Math.PI * 2.0 / waistCount) + seedNorm * 6.283 + (hj - 0.5) * 0.14 - wp * 0.18;
                        double ringMult = (i % 2 == 0) ? 1.0 : 0.84;
                        double rJitter = beltR * ringMult * (0.92 + 0.16 * hj);
                        Vec3 puffCenter = relTarget.add(
                                Math.cos(ang) * rJitter,
                                waistY + halfH * ((i % 2 == 0 ? 0.16 : -0.14) + 0.30 * (hw - 0.5)),
                                Math.sin(ang) * rJitter
                        );
                        float puffSeed = ((i * 37 + Math.abs(seed % 97)) % 100) / 100.0F;
                        emitBillboardQuad(
                                dustVc, mat, puffCenter, camRight, camUp,
                                halfW * (0.85 + 0.32 * hw), halfH * (0.80 + 0.36 * hj),
                                wp, puffSeed, 0.0F, wFade * 0.58F
                        );
                    }
                }
            }

            // Rolling Outer Ground Dust Wall (Type 1.0, dt = 1.5..188, sweeps dramatically across terrain!)
            if (safeDt >= 1.5F && safeDt < 188.0F) {
                float dp = Mth.clamp((safeDt - 1.5F) / 186.5F, 0.0F, 1.0F);
                float dFade = smoothstep(dp / 0.04F) * (1.0F - smoothstep((dp - 0.54F) / 0.46F));
                if (dFade > 0.008F) {
                    int puffCount = 64;
                    double wallR = visualR * (0.56 + 1.58 * Math.pow(dp, 0.50));
                    double halfW = visualR * (0.22 + 0.18 * smoothstep(dp / 0.36F));
                    double halfH = visualR * (0.090 + 0.098 * smoothstep(dp / 0.50F));
                    for (int i = 0; i < puffCount; i++) {
                        float hj = hashFloat(seed + i * 19 + 307);
                        float hw = hashFloat(seed + i * 41 + 317);
                        double ringOffset = switch (i % 3) {
                            case 0 -> 1.0;
                            case 1 -> 0.86;
                            default -> 0.72;
                        };
                        double ang = i * (Math.PI * 2.0 / puffCount) + seedNorm * 3.14 + (i % 3) * 0.09 + (hj - 0.5) * 0.14;
                        double rCur = wallR * ringOffset * (0.91 + 0.16 * hj);
                        double curW = halfW * (0.86 + 0.30 * hw);
                        double curH = halfH * (0.84 + 0.34 * ((i % 3) * 0.35 + hj * 0.30));
                        Vec3 puffCenter = relTarget.add(Math.cos(ang) * rCur, curH * (0.86 + 0.18 * hw), Math.sin(ang) * rCur);
                        float puffSeed = ((i * 53 + Math.abs(seed % 89)) % 100) / 100.0F;
                        emitBillboardQuad(dustVc, mat, puffCenter, camRight, camUp, curW, curH, dp, puffSeed, 1.0F, dFade * 0.90F);
                    }
                }
            }

            // 26 Wispy Soot-Smoke Trails launching outward through the fireball (Type 0.50, dt = 1.5..124)
            if (safeDt >= 1.5F && safeDt < 124.0F) {
                float sp = (safeDt - 1.5F) / 122.5F;
                float smokeAlpha = smoothstep(sp / 0.08F) * (1.0F - smoothstep(0.45F, 0.94F, sp));
                float tSec = safeDt * 0.032F;
                int chunkCount = 26;
                for (int i = 0; i < chunkCount; i++) {
                    float h1 = hashFloat(seed + i * 29 + 401);
                    float h2 = hashFloat(seed + i * 47 + 409);
                    float h3 = hashFloat(seed + i * 61 + 419);
                    double ang = h1 * Math.PI * 2.0 + i * 0.72;
                    double vH = visualR * (0.44 + 0.56 * h2);
                    double vU = visualR * (0.46 + 0.58 * h3);
                    double gx = Math.cos(ang) * (visualR * 0.24 + vH * tSec / (1.0 + tSec * 0.60));
                    double gz = Math.sin(ang) * (visualR * 0.24 + vH * tSec / (1.0 + tSec * 0.60));
                    double gy = 1.2 + vU * tSec - 0.5 * 9.5 * tSec * tSec;
                    if (gy < 0.25) continue;
                    double cSz = 0.70 + 0.90 * h1 + sp * 0.80;
                    Vec3 scPos = relTarget.add(gx, gy, gz);
                    emitBillboardQuad(dustVc, mat, scPos, camRight, camUp, cSz * 1.15, cSz * 0.92, sp, h2, 0.50F, smokeAlpha * 0.45F);
                }
            }

            ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_DUST_WAVE, Math.max(0.55F, bloom));

            // 2e. 56 Craggy 3D Dark Boulders, 96 Soft-Blooming Radial Speed Streaks & 160 Twinkling Blooming Aftermath Embers
            if (safeDt >= 0.2F && safeDt < 190.0F) {
                emitDebrisSparksAndEmbers(buffers, mat, relTarget, relCam, camRight, camUp, visualR, safeDt, seed, vfxBloom);
            }
        }
    }

    private static void emitStrikeAndPersistentLaser(
            MultiBufferSource buffers, Matrix4f mat,
            Vec3 relTarget, Vec3 relStart, Vec3 relCam, Vec3 camRight, Vec3 camUp,
            double radius, float t, double worldTime, int seed, float seedNorm, float bloom
    ) {
        double apexHeight = getSkyApexHeight(radius);
        Vec3 skyApex = relTarget.add(0.0, apexHeight, 0.0);
        Vec3 groundTip = relTarget.add(0.0, 0.15, 0.0);

        VertexConsumer vortexVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_VORTEX_RING);

        // 1. Inverted-cone sky rings holding steady at 1.0x until apex lock (t=7), smoothly converging 1.0 -> 0.72 (t=7..29), fading with pillar (t=26..50)
        // Uses SKY_COLUMN_SEED (0.42F) and worldTime * 0.055 matching ExplosionChargeRenderer 1:1 for zero handoff snap!
        if (t < 50.0F && bloom > 0.004F) {
            float collapseP = smoothstep((t - 7.0F) / 22.0F);
            float skyFade = 1.0F - smoothstep((t - 26.0F) / 24.0F);
            emitSkyVortexColumn(vortexVc, mat, relTarget, radius, 1.0F, 1.0F - 0.28F * collapseP, skyFade, SKY_COLUMN_SEED, worldTime * 0.055);
        }

        // 2. Stage 1 (t = 0 .. 26): Soft-Blooming Diagonal Golden Beam + Tilted Launch Magic Ring & Ignition Flash
        if (t < 26.0F && relStart.distanceToSqr(skyApex) > 4.0) {
            Vec3 diagAxis = skyApex.subtract(relStart).normalize();
            Vec3 beamU = diagAxis.cross(new Vec3(0.0, 1.0, 0.0));
            beamU = beamU.lengthSqr() < 1.0E-4 ? diagAxis.cross(new Vec3(1.0, 0.0, 0.0)).normalize() : beamU.normalize();
            Vec3 beamV = diagAxis.cross(beamU).normalize();

            if (t < 14.5F) {
                float diagFade = 1.0F - smoothstep((t - 8.5F) / 6.0F);
                float diagReach = Mth.clamp(t / 6.8F, 0.06F, 1.0F);
                Vec3 diagHead = relStart.lerp(skyApex, diagReach);
                emitSoftBloomBeamQuad(vortexVc, mat, relStart, diagHead, relCam, 0.78 * diagFade, 0.15F, 0.20F, diagFade * 0.98F);
            }

            // Blazing golden-white staff muzzle ignition burst (t = 0..16)
            if (t < 16.0F && bloom > 0.004F) {
                float mFade = (float) Math.exp(-t * 0.16) * (1.0F - smoothstep((t - 9.0F) / 7.0F));
                double mRad = 1.45 + 1.85 * smoothstep(t / 5.5F);
                emitBillboardQuad(vortexVc, mat, relStart, camRight, camUp, mRad * 1.45, mRad * 1.45, 0.72F, 0.10F, 0.98F, mFade * 0.80F);
                emitBillboardQuad(vortexVc, mat, relStart, camRight, camUp, mRad * 0.85, mRad * 0.85, 0.98F, 0.10F, 0.98F, mFade * 0.96F);
            }

            // Tilted crimson-gold arcane launch circle coaxial around the diagonal beam (t = 0..26)
            if (bloom > 0.004F) {
                float ringFade = 1.0F - smoothstep((t - 9.0F) / 17.0F);
                double launchR = 1.50 + 1.45 * smoothstep(t / 9.5F);
                Vec3 launchPos = relStart.lerp(skyApex, Math.min(0.16, 0.05 + t * 0.0045));
                double rotAngle = t * 0.055;
                Vec3 ru = beamU.scale(Math.cos(rotAngle)).add(beamV.scale(Math.sin(rotAngle)));
                Vec3 rv = beamU.scale(-Math.sin(rotAngle)).add(beamV.scale(Math.cos(rotAngle)));
                emitBillboardQuad(vortexVc, mat, launchPos, ru, rv, launchR, launchR, 1.0F, 0.35F, 0.50F, ringFade * 0.94F);
                emitBillboardQuad(vortexVc, mat, launchPos, ru, rv, launchR * 1.28, launchR * 1.28, 0.66F, 0.10F, 0.98F, ringFade * 0.34F);
            }
        }

        // 3. Stage 2 (t = 6.2 .. 22.0): Super-Wide Horizontal Golden Lens-Flare Slash & Apex Supernova Core
        if (t >= 6.2F && t < 22.0F && bloom > 0.004F && ZoltraakCinematicClientConfig.isLensFlaresEnabled()) {
            float flareP = (t - 6.2F) / 15.8F;
            float flareAlpha = (float) Math.sin(flareP * Math.PI);
            double slashHalfLen = radius * (1.45 + 0.92 * flareP);
            double slashHalfW = radius * 0.22 * flareAlpha;
            emitSoftBloomBeamQuad(
                    vortexVc, mat,
                    skyApex.subtract(camRight.scale(slashHalfLen)),
                    skyApex.add(camRight.scale(slashHalfLen)),
                    relCam, slashHalfW, 0.20F, 0.85F, flareAlpha * 0.98F
            );
            emitBillboardQuad(vortexVc, mat, skyApex, camRight, camUp, radius * 0.62, radius * 0.62, 0.96F, 0.10F, 0.98F, flareAlpha * 0.94F);
        }

        // 4. Stage 3 (t = 15.0 .. 50.0): Heavens Pillar Descent -> Ground Compression -> Gradual Fade Together with 8 Sky Rings
        if (t >= 15.0F && t < 50.0F) {
            float vAge = t - 15.0F;
            float strikeReach = Mth.clamp(vAge / 4.2F, 0.06F, 1.0F);
            Vec3 headPos = skyApex.lerp(groundTip, strikeReach);

            float preDetPulse = (t < 29.0F) ? smoothstep((t - 18.5F) / 9.5F) : 1.0F;
            // Exact same fade window (t = 26..50) as the 8 stacked sky magic rings
            float pillarFade = 1.0F - smoothstep((t - 26.0F) / 24.0F);
            float widePhase = (0.62F + 0.38F * preDetPulse) * pillarFade;
            double beamHalfW = radius * (0.044 + 0.28 * widePhase) * (0.35F + 0.65F * pillarFade);
            float pinkTint = 0.45F * widePhase;
            emitSoftBloomBeamQuad(vortexVc, mat, skyApex, headPos, relCam, beamHalfW, pinkTint, 0.15F, pillarFade * 0.98F);

            // Pre-detonation ground contact compression singularity right before the dome erupts (t = 18.8 .. 28.8)
            if (t >= 18.8F && t < 28.8F && bloom > 0.004F) {
                float cp = Mth.clamp((t - 18.8F) / 9.2F, 0.0F, 1.0F);
                double coreR = radius * (0.18 + 0.38 * cp * cp);
                emitBillboardQuad(vortexVc, mat, relTarget.add(0.0, 0.45, 0.0), camRight, camUp, coreR, coreR, 0.96F, 0.10F, 0.98F, (0.35F + 0.65F * cp) * (1.0F - smoothstep((t - 27.6F) / 1.2F)));
                emitHorizDiscQuad(vortexVc, mat, relTarget.add(0.0, 0.16, 0.0), coreR * 1.45, 0.0, 0.88F, 0.10F, 0.98F, (0.28F + 0.62F * cp));
            }
        }

        // 5. Lingering Diagonal Sparkle Trail (Mode 5 dot bloom, t = 1.0 .. 24.0)
        if (t >= 1.0F && t < 24.0F && bloom > 0.004F && relStart.distanceToSqr(skyApex) > 4.0) {
            float trailFade = 1.0F - smoothstep(12.0F, 24.0F, t);
            int trailSparks = 38;
            for (int i = 0; i < trailSparks; i++) {
                float h1 = hashFloat(seed + i * 29 + 301);
                float h2 = hashFloat(seed + i * 53 + 313);
                double u = (i + 0.5) / (double) trailSparks;
                if (u > t / 7.0) continue;
                Vec3 base = relStart.lerp(skyApex, u);
                Vec3 jitter = new Vec3((h1 - 0.5) * 1.6, (h2 - 0.5) * 1.6 - 0.03 * t, ((h1 * 1.7) % 1.0 - 0.5) * 1.6);
                double sz = (0.18 + 0.22 * h1) * trailFade;
                emitBillboardQuad(vortexVc, mat, base.add(jitter), camRight, camUp, sz, sz, 0.68F + 0.16F * h2, 0.10F, 0.98F, trailFade * 0.92F);
            }
        }

        // 5b. Crackling Inter-Ring & Vertical Pillar Mana Lightning Arcs (Mode 4a, fades with sky rings & pillar at t = 50)
        if (t >= 4.0F && t < 50.0F && bloom > 0.004F) {
            float skyFade = 1.0F - smoothstep((t - 26.0F) / 24.0F);
            float arcFade = smoothstep((t - 4.0F) / 4.0F) * skyFade;
            int stepSeed = (int) (t * 0.72F);
            int nRings = SKY_RING_HEIGHT_FACTORS.length;
            float collapseP = smoothstep((t - 7.0F) / 22.0F);
            float ringScale = 1.0F - 0.28F * collapseP;

            if (t < 46.0F) {
                for (int b = 0; b < 10; b++) {
                    float hb1 = hashFloat(seed + stepSeed * 29 + b * 23 + 3);
                    float hb2 = hashFloat(seed + stepSeed * 43 + b * 37 + 9);
                    int rA = Math.min(nRings - 2, (int) (hb1 * (nRings - 1)));
                    int rB = Math.min(nRings - 1, rA + 1 + (hb2 > 0.62F ? 1 : 0));
                    double angA = hb1 * Math.PI * 2.0 + t * 0.08;
                    double angB = angA + (hb2 - 0.5) * 1.10;
                    double radA = getSkyRingRadius(radius, rA) * ringScale * 0.85;
                    double radB = getSkyRingRadius(radius, rB) * ringScale * 0.85;
                    Vec3 pA = relTarget.add(Math.cos(angA) * radA, getSkyRingY(radius, rA), Math.sin(angA) * radA);
                    Vec3 pB = relTarget.add(Math.cos(angB) * radB, getSkyRingY(radius, rB), Math.sin(angB) * radB);
                    emitSoftBloomBeamQuad(vortexVc, mat, pA, pB, relCam, 0.45 + 0.38 * hb2, 0.16F, 0.42F + 0.26F * hb2, arcFade * 0.94F);
                }
            }
            // Coiling plasma lightning arcs snapping down the vertical sky pillar (t = 15..50)
            if (t >= 15.0F) {
                float pReach = Mth.clamp((t - 15.0F) / 4.2F, 0.10F, 1.0F);
                for (int b = 0; b < 6; b++) {
                    float hb1 = hashFloat(seed + stepSeed * 59 + b * 31 + 17);
                    float hb2 = hashFloat(seed + stepSeed * 71 + b * 47 + 23);
                    double y0 = apexHeight * (0.15 + 0.65 * hb1) * pReach;
                    double y1 = Math.min(apexHeight, y0 + apexHeight * (0.18 + 0.22 * hb2));
                    double a0 = hb1 * Math.PI * 2.0 + t * 0.16;
                    double a1 = a0 + 1.15;
                    double colR = radius * (0.12 + 0.16 * hb2);
                    Vec3 pA = relTarget.add(Math.cos(a0) * colR, y0, Math.sin(a0) * colR);
                    Vec3 pB = relTarget.add(Math.cos(a1) * colR, y1, Math.sin(a1) * colR);
                    emitSoftBloomBeamQuad(vortexVc, mat, pA, pB, relCam, 0.48 + 0.42 * hb2, 0.28F, 0.44F + 0.24F * hb1, arcFade * 0.92F);
                }
            }
        }

        ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_VORTEX_RING, Math.max(0.5F, bloom));

        // 6. Coaxial Smoke Rings on Diagonal Beam + Cascading Vertical Beam Halo Rings + Pre-Blast Seismic Rings
        if (t < 38.0F) {
            VertexConsumer ringVc = buffers.getBuffer(ZoltraakRenderTypes.LIGHT);
            if (t < 16.5F && relStart.distanceToSqr(skyApex) > 4.0) {
                float diagFade = 1.0F - smoothstep((t - 8.0F) / 8.5F);
                Vec3 diagAxis = skyApex.subtract(relStart).normalize();
                double adv = smoothstep(t / 16.5F);
                emitCoaxialBeamRing(ringVc, mat, relStart.lerp(skyApex, 0.12 + 0.26 * adv), diagAxis, 1.50 + t * 0.14, 0.15, 28, 1.00F, 0.88F, 0.42F, diagFade * 0.82F);
                emitCoaxialBeamRing(ringVc, mat, relStart.lerp(skyApex, 0.32 + 0.30 * adv), diagAxis, 1.25 + t * 0.12, 0.13, 28, 1.00F, 0.80F, 0.30F, diagFade * 0.70F);
                emitCoaxialBeamRing(ringVc, mat, relStart.lerp(skyApex, 0.54 + 0.32 * adv), diagAxis, 1.05 + t * 0.10, 0.11, 28, 1.00F, 0.72F, 0.22F, diagFade * 0.58F);
            }
            if (t >= 15.5F && t < 38.0F) {
                float ringAge = t - 15.5F;
                float rLife = 1.0F - smoothstep(10.0F, 22.5F, ringAge);
                double[] ringAltitudes = { 0.10, 0.26, 0.46, 0.66, 0.84 };
                for (int i = 0; i < ringAltitudes.length; i++) {
                    double ry = apexHeight * ringAltitudes[i];
                    double rr = radius * (0.18 + 0.055 * i + 0.032 * ringAge);
                    emitThinHorizRing(ringVc, mat, relTarget.add(0.0, ry, 0.0), rr, 0.15 * rLife, 32, 1.0F, 0.86F, 0.28F, rLife * 0.86F);
                }
            }
            // Fast pre-detonation seismic compression rings racing across ground right before boom (t = 19.0 .. 28.2)
            if (t >= 19.0F && t < 28.2F) {
                float sp = (t - 19.0F) / 9.2F;
                float sa = (float) Math.sin(sp * Math.PI) * 0.90F;
                double sR1 = radius * (0.72 - 0.54 * smoothstep(sp));
                double sR2 = radius * (0.12 + 0.78 * sp);
                emitThinHorizRing(ringVc, mat, relTarget.add(0.0, 0.18, 0.0), Math.max(0.5, sR1), 0.24, 36, 1.0F, 0.94F, 0.58F, sa);
                emitThinHorizRing(ringVc, mat, relTarget.add(0.0, 0.14, 0.0), sR2, 0.20, 36, 1.0F, 0.68F, 0.22F, sa * 0.76F);
            }
            ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.LIGHT, Math.max(0.45F, bloom));
        }

        // 7. Crisp Anamorphic Horizontal Glint on Sky Apex Flare Slash (ZOL_GLOW, t = 6.2 .. 22.0)
        if (t >= 6.2F && t < 22.0F && bloom > 0.004F && ZoltraakCinematicClientConfig.isLensFlaresEnabled()) {
            float flareP = (t - 6.2F) / 15.8F;
            float flareAlpha = (float) Math.sin(flareP * Math.PI);
            VertexConsumer flareVc = buffers.getBuffer(ZoltraakRenderTypes.ZOL_GLOW);
            emitBillboardQuad(flareVc, mat, skyApex, camRight, camUp, radius * 1.55, 2.0, 1.0F, 0.92F, 0.52F, flareAlpha * 0.88F);
            ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.ZOL_GLOW, bloom);
        }
    }

    private static void emitDebrisSparksAndEmbers(
            MultiBufferSource buffers, Matrix4f mat,
            Vec3 relTarget, Vec3 relCam, Vec3 camRight, Vec3 camUp,
            double radius, float dt, int seed, float bloom
    ) {
        float tSec = dt * 0.036F;

        // A. 56 Craggy 3D Dark Rock Boulders with warm inner blast rim-lighting (hang in air longer & grander)
        if (dt < 136.0F) {
            float rockAlpha = 1.0F - smoothstep((dt - 96.0F) / 40.0F);
            if (rockAlpha > 0.01F) {
                VertexConsumer rockVc = buffers.getBuffer(ZoltraakRenderTypes.BLACK_CORE);
                int rockCount = 56;
                for (int i = 0; i < rockCount; i++) {
                    float h1 = hashFloat(seed + i * 19 + 1);
                    float h2 = hashFloat(seed + i * 37 + 2);
                    float h3 = hashFloat(seed + i * 53 + 3);
                    float h4 = hashFloat(seed + i * 71 + 4);

                    double ang = h1 * Math.PI * 2.0 + i * 0.618;
                    double vHoriz = radius * (0.34 + 0.58 * h2);
                    double vUp = radius * (0.46 + 0.72 * h3);
                    double drag = 1.0 / (1.0 + tSec * 0.36);
                    double gx = Math.cos(ang) * (radius * 0.20 + vHoriz * tSec * drag);
                    double gz = Math.sin(ang) * (radius * 0.20 + vHoriz * tSec * drag);
                    double gy = 0.8 + vUp * tSec - 0.5 * 11.2 * tSec * tSec;
                    if (gy < -0.3) continue;

                    double sz = 0.16 + 0.50 * (h4 * h4);
                    Vec3 center = relTarget.add(gx, gy, gz);
                    float spinA = dt * (0.09F + 0.12F * h2) + h1 * 6.28F;
                    float spinB = dt * (0.07F + 0.10F * h3) + h2 * 6.28F;
                    if (i % 3 == 0) {
                        emitYutaponCubeShard(rockVc, mat, center, relTarget, sz * 1.12, spinA, spinB, i * 23 + seed, rockAlpha);
                    } else {
                        emitCraggyRockBoulder(rockVc, mat, center, relTarget, sz, spinA, spinB, i * 17 + seed, rockAlpha);
                    }
                }
                ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.BLACK_CORE, 1.0F);
            }
        }

        if (bloom <= 0.004F) return;

        VertexConsumer fxVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_VORTEX_RING);

        // B. 96 Soft-Blooming Radial Orange-Yellow Speed Streaks (Mode 4, dt = 0..64)
        if (dt < 64.0F) {
            float sparkFade = 1.0F - smoothstep((dt - 24.0F) / 40.0F);
            int sparkCount = 96;
            for (int i = 0; i < sparkCount; i++) {
                float h1 = hashFloat(seed + i * 29 + 11);
                float h2 = hashFloat(seed + i * 43 + 17);
                float h3 = hashFloat(seed + i * 61 + 23);

                double theta = h1 * Math.PI * 2.0;
                double phi = (0.05 + 0.74 * h2) * (Math.PI * 0.5);
                Vec3 launchDir = new Vec3(
                        Math.cos(phi) * Math.cos(theta),
                        Math.sin(phi),
                        Math.cos(phi) * Math.sin(theta)
                );

                double speed = radius * (0.95 + 1.25 * h3);
                double travel = speed * tSec * (1.0 / (1.0 + tSec * 1.02));
                Vec3 pos = relTarget.add(launchDir.scale(radius * 0.26 + travel)).add(0.0, -1.8 * tSec * tSec, 0.0);
                if (pos.y < -0.2) continue;

                double halfLen = (0.44 + 0.76 * h2) * (0.48 + 0.52 * sparkFade);
                double halfW = 0.12 + 0.09 * h1;
                Vec3 tail = pos.subtract(launchDir.scale(halfLen));
                Vec3 head = pos.add(launchDir.scale(halfLen));
                emitSoftBloomBeamQuad(fxVc, mat, tail, head, relCam, halfW, 0.10F, 0.85F, sparkFade * 0.96F);
            }
        }

        // C. 160 Twinkling Blooming Golden-Orange Aftermath Ember Sparks rising slowly over the crater (dt = 10..190)
        if (dt >= 10.0F && dt < 190.0F) {
            float ep = (dt - 10.0F) / 180.0F;
            float emberEnv = smoothstep(ep / 0.12F) * (1.0F - smoothstep((ep - 0.68F) / 0.32F));
            int emberCount = 160;
            for (int i = 0; i < emberCount; i++) {
                float h1 = hashFloat(seed + i * 31 + 101);
                float h2 = hashFloat(seed + i * 47 + 107);
                float h3 = hashFloat(seed + i * 73 + 113);

                double ang = h1 * Math.PI * 2.0 + tSec * (0.18 + 0.26 * h2);
                double rDist = radius * (0.12 + 0.88 * Math.sqrt(h2)) * (0.85 + 0.30 * ep);
                double ey = 0.35 + h3 * (radius * 0.72) + Math.sin(tSec * 1.9 + i) * 0.55 + ep * 4.5;
                Vec3 ePos = relTarget.add(Math.cos(ang) * rDist, ey, Math.sin(ang) * rDist);

                float twinkle = 0.48F + 0.52F * (float) Math.sin(dt * (0.22 + 0.16 * h1) + i * 1.7);
                float ea = emberEnv * twinkle * 0.95F;
                if (ea <= 0.02F) continue;
                double eSize = 0.16 + 0.20 * h1;
                emitBillboardQuad(fxVc, mat, ePos, camRight, camUp, eSize, eSize, 0.66F + 0.18F * h2, 0.10F, 0.98F, ea);
            }
        }

        ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_VORTEX_RING, bloom);
    }

    private static void emitIrisEmissiveProxy(
            MultiBufferSource buffers, Matrix4f mat,
            Vec3 relTarget, Vec3 relStart, Vec3 relCam, Vec3 camRight, Vec3 camUp,
            double radius, float t, float dt
    ) {
        VertexConsumer quadVc = buffers.getBuffer(ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
        double apexHeight = getSkyApexHeight(radius);
        Vec3 skyApex = relTarget.add(0.0, apexHeight, 0.0);
        Vec3 axU = new Vec3(1.0, 0.0, 0.0);
        Vec3 axV = new Vec3(0.0, 0.0, 1.0);

        // HDR proxies for shaderpack bloom around beams, thin sky vortex rims, fireball peak flash & crater embers
        // 1. Diagonal golden beam + tilted crimson-gold launch magic ring & muzzle flash HDR bloom (t = 0..24)
        if (t < 24.0F && relStart.distanceToSqr(skyApex) > 4.0) {
            Vec3 diagAxis = skyApex.subtract(relStart).normalize();
            Vec3 beamU = diagAxis.cross(new Vec3(0.0, 1.0, 0.0));
            beamU = beamU.lengthSqr() < 1.0E-4 ? diagAxis.cross(new Vec3(1.0, 0.0, 0.0)).normalize() : beamU.normalize();
            Vec3 beamV = diagAxis.cross(beamU).normalize();

            if (t < 14.5F) {
                float dFade = (1.0F - smoothstep((t - 8.5F) / 6.0F)) * 0.78F;
                float diagReach = Mth.clamp(t / 6.8F, 0.08F, 1.0F);
                Vec3 diagHead = relStart.lerp(skyApex, diagReach);
                float g = ZoltraakRenderPass.irisFadeGuard(dFade);
                Vec3 diagSide = diagAxis.cross(relCam.subtract(relStart));
                diagSide = diagSide.lengthSqr() < 1.0E-5 ? camRight : diagSide.normalize();
                ZoltraakRenderPass.emitFeatheredBeamSegment(
                        quadVc, mat, relStart, diagHead, diagSide,
                        0.28 * dFade, 0.84 * dFade,
                        0.98F * g, 0.90F * g, 0.66F * g, dFade,
                        0.98F * g, 0.48F * g, 0.10F * g, dFade * 0.58F
                );
            }
            if (t < 16.0F) {
                float mFade = (float) Math.exp(-t * 0.16) * (1.0F - smoothstep((t - 9.0F) / 7.0F)) * 0.80F;
                float mg = ZoltraakRenderPass.irisFadeGuard(mFade);
                ZoltraakRenderPass.emitFeatheredStarBurst(
                        quadVc, mat, relStart, camRight, camUp, 1.6 + 1.8 * smoothstep(t / 5.5F),
                        1.00F * mg, 0.86F * mg, 0.40F * mg, mFade
                );
            }
            float rFade = (1.0F - smoothstep((t - 9.0F) / 15.0F)) * 0.64F;
            if (rFade > 0.015F) {
                float rg = ZoltraakRenderPass.irisFadeGuard(rFade);
                double launchR = 1.50 + 1.45 * smoothstep(t / 9.5F);
                Vec3 launchPos = relStart.lerp(skyApex, Math.min(0.16, 0.05 + t * 0.0045));
                ZoltraakRenderPass.emitFeatheredRing(
                        quadVc, mat, launchPos, beamU, beamV,
                        launchR * 0.70, launchR * 0.84, launchR * 1.00, 24,
                        1.00F * rg, 0.46F * rg, 0.10F * rg, rFade
                );
            }
        }

        // 2. Sky Apex horizontal lens flare HDR bloom (t = 6.2..22.0)
        if (t >= 6.2F && t < 22.0F) {
            float flareP = (t - 6.2F) / 15.8F;
            float flareA = (float) Math.sin(flareP * Math.PI) * 0.82F;
            float fg = ZoltraakRenderPass.irisFadeGuard(flareA);
            ZoltraakRenderPass.emitFeatheredStarBurst(
                    quadVc, mat, skyApex, camRight, camUp, radius * 1.10,
                    0.98F * fg, 0.88F * fg, 0.42F * fg, flareA
            );
            if (ZoltraakCinematicClientConfig.isLensFlaresEnabled()) {
                ZoltraakRenderPass.emitFeatheredLensStreak(
                        quadVc, mat, skyApex, camRight, camUp, radius * 1.25,
                        0.14 * (0.4 + 0.6 * flareA), 0.68 * (0.4 + 0.6 * flareA),
                        0.98F * fg, 0.88F * fg, 0.44F * fg, flareA * 0.68F,
                        0.98F * fg, 0.52F * fg, 0.10F * fg, flareA * 0.36F
                );
            }
        }

        // 3. Sky Vortex Rings thin double rim HDR accent (t = 0..50, matched 1:1 with ExplosionChargeRenderer Iris proxy)
        if (t < 50.0F) {
            float collapseP = smoothstep((t - 7.0F) / 22.0F);
            float skyFade = (1.0F - smoothstep((t - 26.0F) / 24.0F)) * 0.56F;
            int ringCount = SKY_RING_HEIGHT_FACTORS.length;
            float denom = Math.max(1.0F, ringCount - 1.0F);
            for (int i = 0; i < ringCount; i++) {
                double ry = getSkyRingY(radius, i);
                double rr = getSkyRingRadius(radius, i) * (1.0 - 0.28 * collapseP);
                float ringA = skyFade * (0.52F + 0.48F * (i / denom));
                float sg = ZoltraakRenderPass.irisFadeGuard(ringA);
                ZoltraakRenderPass.emitFeatheredRing(
                        quadVc, mat, relTarget.add(0.0, ry, 0.0), axU, axV,
                        rr * 0.75, rr * 0.82, rr * 0.89, 26,
                        1.00F * sg, 0.44F * sg, 0.08F * sg, ringA
                );
                ZoltraakRenderPass.emitFeatheredRing(
                        quadVc, mat, relTarget.add(0.0, ry, 0.0), axU, axV,
                        rr * 0.24, rr * 0.285, rr * 0.33, 20,
                        1.00F * sg, 0.62F * sg, 0.16F * sg, ringA * 0.72F
                );
            }
        }

        // 4. Vertical Laser Pillar + Pre-Detonation Compression Core HDR bloom (fades together with 8 sky rings at t = 26..50)
        if (t >= 15.0F && t < 50.0F) {
            float vAge = t - 15.0F;
            float strikeReach = Mth.clamp(vAge / 4.2F, 0.06F, 1.0F);
            Vec3 curTip = skyApex.lerp(relTarget, strikeReach);
            float pillarFade = 1.0F - smoothstep((t - 26.0F) / 24.0F);
            float skyFade = pillarFade * 0.84F;
            float g = ZoltraakRenderPass.irisFadeGuard(skyFade);
            Vec3 beamSide = relTarget.subtract(skyApex).cross(relCam.subtract(skyApex));
            beamSide = beamSide.lengthSqr() < 1.0E-5 ? new Vec3(1.0, 0.0, 0.0) : beamSide.normalize();
            ZoltraakRenderPass.emitFeatheredBeamSegment(
                    quadVc, mat, skyApex, curTip, beamSide,
                    radius * (0.04 + 0.12 * pillarFade) * (0.35 + 0.65 * pillarFade),
                    radius * (0.12 + 0.30 * pillarFade) * (0.35 + 0.65 * pillarFade),
                    0.98F * g, 0.90F * g, 0.68F * g, skyFade,
                    0.98F * g, 0.38F * g, 0.09F * g, skyFade * 0.56F
            );
            if (t >= 18.8F && t < 28.8F) {
                float cp = Mth.clamp((t - 18.8F) / 9.2F, 0.0F, 1.0F);
                float cFade = cp * (1.0F - smoothstep((t - 27.6F) / 1.2F)) * 0.80F;
                float cg = ZoltraakRenderPass.irisFadeGuard(cFade);
                ZoltraakRenderPass.emitFeatheredStarBurst(
                        quadVc, mat, relTarget.add(0.0, 0.45, 0.0), camRight, camUp, radius * (0.28 + 0.44 * cp),
                        1.00F * cg, 0.88F * cg, 0.48F * cg, cFade
                );
            }
        }

        // 5. 3D Fireball Dome + Equatorial Rim + Ground Flash HDR bloom for Iris / Complementary (dt = 0..126)
        if (dt >= 0.0F && dt < 126.0F) {
            float p = Mth.clamp(dt / 126.0F, 0.0F, 1.0F);
            float glowFade = (float) Math.exp(-dt * 0.016) * (1.0F - smoothstep((p - 0.54F) / 0.46F));
            if (glowFade > 0.012F) {
                float fg = ZoltraakRenderPass.irisFadeGuard(glowFade);
                float growth = (float) Math.pow(Mth.clamp(dt / 16.0F, 0.0F, 1.0F), 0.34);
                double visualR = radius * ZoltraakCinematicClientConfig.getExplosionDomeScale();
                double domeR = visualR * 0.88 * growth;

                // Ground ignition bloom ring
                ZoltraakRenderPass.emitFeatheredRing(
                        quadVc, mat, relTarget.add(0.0, 0.18, 0.0), axU, axV,
                        domeR * 0.54, domeR * 1.12, domeR * 1.70, 28,
                        0.98F * fg, 0.62F * fg, 0.16F * fg, glowFade * 0.66F
                );

                // Silhouette outer halo ring facing camera so shaderpack blooms the fireball rim cleanly
                ZoltraakRenderPass.emitFeatheredRing(
                        quadVc, mat, relTarget.add(0.0, domeR * 0.38, 0.0), camRight, camUp,
                        domeR * 0.78, domeR * 0.96, domeR * 1.16, 28,
                        0.98F * fg, 0.48F * fg, 0.09F * fg, glowFade * 0.60F
                );

                // Center starburst flare for peak flash
                ZoltraakRenderPass.emitFeatheredStarBurst(
                        quadVc, mat, relTarget.add(0.0, domeR * 0.46, 0.0), camRight, camUp, domeR * 1.04,
                        0.98F * fg, 0.72F * fg, 0.24F * fg, glowFade * 0.74F
                );
            }
        }

        // 6. Crater aftermath warm ember rim HDR bloom (dt = 16..186)
        if (dt >= 16.0F && dt < 186.0F) {
            float ep = (dt - 16.0F) / 170.0F;
            float emberA = smoothstep(ep / 0.10F) * (1.0F - smoothstep(0.48F, 1.0F, ep)) * 0.45F;
            float eg = ZoltraakRenderPass.irisFadeGuard(emberA);
            if (emberA > 0.015F) {
                double visualR = radius * ZoltraakCinematicClientConfig.getExplosionDomeScale();
                ZoltraakRenderPass.emitFeatheredRing(
                        quadVc, mat, relTarget.add(0.0, 0.16, 0.0), axU, axV,
                        visualR * 0.28, visualR * 0.64, visualR * 0.98, 24,
                        0.96F * eg, 0.38F * eg, 0.06F * eg, emberA
                );
            }
        }
    }

    /**
     * Emits a camera-facing quad ribbon using EXPLOSION_VORTEX_RING Mode 4 (`b = 0.92f`),
     * which renders a blinding white-yellow core surrounded by a wide, velvety-soft orange-gold bloom halo.
     */
    public static void emitSoftBloomBeamQuad(
            VertexConsumer vc, Matrix4f mat, Vec3 start, Vec3 end, Vec3 relCam,
            double halfW, float progressTint, float seedMode, float alpha
    ) {
        Vec3 axis = end.subtract(start);
        if (axis.lengthSqr() < 1.0E-5) return;
        Vec3 side = axis.cross(relCam.subtract(start));
        side = side.lengthSqr() < 1.0E-5 ? new Vec3(1.0, 0.0, 0.0) : side.normalize().scale(halfW);

        Vec3 s0 = start.subtract(side);
        Vec3 s1 = start.add(side);
        Vec3 e1 = end.add(side);
        Vec3 e0 = end.subtract(side);
        float rc = Mth.clamp(progressTint, 0.0F, 1.0F);
        float gc = Mth.clamp(seedMode, 0.0F, 1.0F);
        float ac = Mth.clamp(alpha, 0.0F, 1.0F);

        vc.addVertex(mat, (float) s0.x, (float) s0.y, (float) s0.z).setUv(0.0F, 0.0F).setColor(rc, gc, 0.92F, ac);
        vc.addVertex(mat, (float) e0.x, (float) e0.y, (float) e0.z).setUv(1.0F, 0.0F).setColor(rc, gc, 0.92F, ac);
        vc.addVertex(mat, (float) e1.x, (float) e1.y, (float) e1.z).setUv(1.0F, 1.0F).setColor(rc, gc, 0.92F, ac);
        vc.addVertex(mat, (float) s1.x, (float) s1.y, (float) s1.z).setUv(0.0F, 1.0F).setColor(rc, gc, 0.92F, ac);
    }

    /**
     * Emits the 8 stacked fiery sky vortex rings above the blast target matching the reference
     * silhouette (+10 blocks lifted off ground with alternating lower bulge and upper crown).
     */
    public static void emitSkyVortexColumn(
            VertexConsumer vc, Matrix4f mat, Vec3 center, double radius,
            float castProgress, float scaleMult, float alpha, float seedNorm, double spinPhase
    ) {
        if (alpha <= 0.005F) return;
        int ringCount = SKY_RING_HEIGHT_FACTORS.length;
        float denom = Math.max(1.0F, ringCount - 1.0F);
        for (int i = 0; i < ringCount; i++) {
            float appearStart = 0.12F + i * (0.36F / denom);
            float ringReveal = smoothstep((castProgress - appearStart) / 0.14F);
            if (ringReveal <= 0.01F) continue;

            float layerFade = alpha * ringReveal * (0.52F + 0.48F * (i / denom));
            double r = getSkyRingRadius(radius, i) * scaleMult * (0.76 + 0.24 * ringReveal);
            Vec3 pos = center.add(0.0, getSkyRingY(radius, i), 0.0);
            double dirSpin = (i % 2 == 0 ? 1.0 : -1.15) * spinPhase + i * 0.72;
            emitHorizDiscQuad(vc, mat, pos, r, dirSpin, castProgress, (seedNorm + i * 0.17F) % 1.0F, 0.5F, layerFade);
        }
    }

    public static void emitBillboardQuad(
            VertexConsumer vc, Matrix4f mat, Vec3 center, Vec3 right, Vec3 up,
            double halfW, double halfH, float r, float g, float b, float a
    ) {
        Vec3 u = right.scale(halfW);
        Vec3 v = up.scale(halfH);
        float rc = Mth.clamp(r, 0.0F, 1.0F);
        float gc = Mth.clamp(g, 0.0F, 1.0F);
        float bc = Mth.clamp(b, 0.0F, 1.0F);
        float ac = Mth.clamp(a, 0.0F, 1.0F);

        Vec3 p0 = center.subtract(u).subtract(v);
        Vec3 p1 = center.add(u).subtract(v);
        Vec3 p2 = center.add(u).add(v);
        Vec3 p3 = center.subtract(u).add(v);

        vc.addVertex(mat, (float) p0.x, (float) p0.y, (float) p0.z).setUv(0.0F, 1.0F).setColor(rc, gc, bc, ac);
        vc.addVertex(mat, (float) p1.x, (float) p1.y, (float) p1.z).setUv(1.0F, 1.0F).setColor(rc, gc, bc, ac);
        vc.addVertex(mat, (float) p2.x, (float) p2.y, (float) p2.z).setUv(1.0F, 0.0F).setColor(rc, gc, bc, ac);
        vc.addVertex(mat, (float) p3.x, (float) p3.y, (float) p3.z).setUv(0.0F, 0.0F).setColor(rc, gc, bc, ac);
    }

    public static void emitHorizDiscQuad(
            VertexConsumer vc, Matrix4f mat, Vec3 center, double radius, double rotRad,
            float r, float g, float b, float a
    ) {
        double c = Math.cos(rotRad) * radius;
        double s = Math.sin(rotRad) * radius;
        Vec3 u = new Vec3(c, 0.0, s);
        Vec3 v = new Vec3(-s, 0.0, c);
        float rc = Mth.clamp(r, 0.0F, 1.0F);
        float gc = Mth.clamp(g, 0.0F, 1.0F);
        float bc = Mth.clamp(b, 0.0F, 1.0F);
        float ac = Mth.clamp(a, 0.0F, 1.0F);

        Vec3 p0 = center.subtract(u).subtract(v);
        Vec3 p1 = center.add(u).subtract(v);
        Vec3 p2 = center.add(u).add(v);
        Vec3 p3 = center.subtract(u).add(v);

        vc.addVertex(mat, (float) p0.x, (float) p0.y, (float) p0.z).setUv(0.0F, 1.0F).setColor(rc, gc, bc, ac);
        vc.addVertex(mat, (float) p1.x, (float) p1.y, (float) p1.z).setUv(1.0F, 1.0F).setColor(rc, gc, bc, ac);
        vc.addVertex(mat, (float) p2.x, (float) p2.y, (float) p2.z).setUv(1.0F, 0.0F).setColor(rc, gc, bc, ac);
        vc.addVertex(mat, (float) p3.x, (float) p3.y, (float) p3.z).setUv(0.0F, 0.0F).setColor(rc, gc, bc, ac);
    }

    /** Emits a crisp 4-point concave anime star (✦) with a white-hot center. */
    public static void emitFourPointStar(
            VertexConsumer vc, Matrix4f mat, Vec3 center, Vec3 right, Vec3 up,
            double radius, float r, float g, float b, float a
    ) {
        float rc = Mth.clamp(r, 0.0F, 1.0F);
        float gc = Mth.clamp(g, 0.0F, 1.0F);
        float bc = Mth.clamp(b, 0.0F, 1.0F);
        float ac = Mth.clamp(a, 0.0F, 1.0F);

        double waist = radius * 0.22;
        Vec3 tipT = center.add(up.scale(radius));
        Vec3 tipB = center.subtract(up.scale(radius));
        Vec3 tipR = center.add(right.scale(radius));
        Vec3 tipL = center.subtract(right.scale(radius));

        Vec3 wTR = center.add(right.scale(waist)).add(up.scale(waist));
        Vec3 wBR = center.add(right.scale(waist)).subtract(up.scale(waist));
        Vec3 wBL = center.subtract(right.scale(waist)).subtract(up.scale(waist));
        Vec3 wTL = center.subtract(right.scale(waist)).add(up.scale(waist));

        emitTri(vc, mat, center, tipT, wTR, rc, gc, bc, ac);
        emitTri(vc, mat, center, wTR, tipR, rc, gc, bc, ac);
        emitTri(vc, mat, center, tipR, wBR, rc, gc, bc, ac);
        emitTri(vc, mat, center, wBR, tipB, rc, gc, bc, ac);
        emitTri(vc, mat, center, tipB, wBL, rc, gc, bc, ac);
        emitTri(vc, mat, center, wBL, tipL, rc, gc, bc, ac);
        emitTri(vc, mat, center, tipL, wTL, rc, gc, bc, ac);
        emitTri(vc, mat, center, wTL, tipT, rc, gc, bc, ac);
    }

    private static void emitThinHorizRing(
            VertexConsumer vc, Matrix4f mat, Vec3 center, double radius, double halfWidth,
            int segs, float r, float g, float b, float a
    ) {
        float rc = Mth.clamp(r, 0.0F, 1.0F);
        float gc = Mth.clamp(g, 0.0F, 1.0F);
        float bc = Mth.clamp(b, 0.0F, 1.0F);
        float ac = Mth.clamp(a, 0.0F, 1.0F);
        double rIn = Math.max(0.1, radius - halfWidth);
        double rOut = radius + halfWidth;

        for (int i = 0; i < segs; i++) {
            double a0 = i * (Math.PI * 2.0 / segs);
            double a1 = (i + 1) * (Math.PI * 2.0 / segs);
            Vec3 i0 = center.add(Math.cos(a0) * rIn, 0.0, Math.sin(a0) * rIn);
            Vec3 o0 = center.add(Math.cos(a0) * rOut, 0.0, Math.sin(a0) * rOut);
            Vec3 o1 = center.add(Math.cos(a1) * rOut, 0.0, Math.sin(a1) * rOut);
            Vec3 i1 = center.add(Math.cos(a1) * rIn, 0.0, Math.sin(a1) * rIn);
            emitTri(vc, mat, i0, o0, o1, rc, gc, bc, ac);
            emitTri(vc, mat, i0, o1, i1, rc, gc, bc, ac);
        }
    }

    private static void emitCoaxialBeamRing(
            VertexConsumer vc, Matrix4f mat, Vec3 center, Vec3 axis,
            double radius, double halfWidth, int segs,
            float r, float g, float b, float a
    ) {
        Vec3 u = axis.cross(new Vec3(0.0, 1.0, 0.0));
        u = u.lengthSqr() < 0.01 ? axis.cross(new Vec3(1.0, 0.0, 0.0)).normalize() : u.normalize();
        Vec3 v = axis.cross(u).normalize();

        float rc = Mth.clamp(r, 0.0F, 1.0F);
        float gc = Mth.clamp(g, 0.0F, 1.0F);
        float bc = Mth.clamp(b, 0.0F, 1.0F);
        float ac = Mth.clamp(a, 0.0F, 1.0F);
        double rIn = Math.max(0.05, radius - halfWidth);
        double rOut = radius + halfWidth;

        for (int i = 0; i < segs; i++) {
            double a0 = i * (Math.PI * 2.0 / segs);
            double a1 = (i + 1) * (Math.PI * 2.0 / segs);
            Vec3 dir0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 dir1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            Vec3 i0 = center.add(dir0.scale(rIn));
            Vec3 o0 = center.add(dir0.scale(rOut));
            Vec3 o1 = center.add(dir1.scale(rOut));
            Vec3 i1 = center.add(dir1.scale(rIn));
            emitTri(vc, mat, i0, o0, o1, rc, gc, bc, ac);
            emitTri(vc, mat, i0, o1, i1, rc, gc, bc, ac);
        }
    }

    // 12-vertex / 20-face deformed craggy rock icosahedron with warm blast rim-lighting
    private static final float PHI = 1.6180339F;
    private static final float[][] ICO_VERTS = {
            {-1.0F,  PHI, 0.0F}, { 1.0F,  PHI, 0.0F}, {-1.0F, -PHI, 0.0F}, { 1.0F, -PHI, 0.0F},
            { 0.0F, -1.0F,  PHI}, { 0.0F,  1.0F,  PHI}, { 0.0F, -1.0F, -PHI}, { 0.0F,  1.0F, -PHI},
            {  PHI, 0.0F, -1.0F}, {  PHI, 0.0F,  1.0F}, { -PHI, 0.0F, -1.0F}, { -PHI, 0.0F,  1.0F}
    };
    private static final int[][] ICO_FACES = {
            {0,11,5}, {0,5,1}, {0,1,7}, {0,7,10}, {0,10,11},
            {1,5,9}, {5,11,4}, {11,10,2}, {10,7,6}, {7,1,8},
            {3,9,4}, {3,4,2}, {3,2,6}, {3,6,8}, {3,8,9},
            {4,9,5}, {2,4,11}, {6,2,10}, {8,6,7}, {9,8,1}
    };

    public static void emitCraggyRockBoulder(
            VertexConsumer vc, Matrix4f mat, Vec3 center, Vec3 blastOrigin,
            double radius, float rotX, float rotY, int rockSeed, float alpha
    ) {
        float sx = 0.75F + 0.50F * hashFloat(rockSeed + 1);
        float sy = 0.65F + 0.55F * hashFloat(rockSeed + 2);
        float sz = 0.75F + 0.50F * hashFloat(rockSeed + 3);

        Vec3[] v = new Vec3[12];
        for (int i = 0; i < 12; i++) {
            float deform = 0.52F + 0.24F * hashFloat(rockSeed * 13 + i * 7);
            Vector3f p = new Vector3f(
                    ICO_VERTS[i][0] * sx * deform,
                    ICO_VERTS[i][1] * sy * deform,
                    ICO_VERTS[i][2] * sz * deform
            );
            p.rotateX(rotX).rotateY(rotY).mul((float) radius);
            v[i] = center.add(p.x, p.y, p.z);
        }

        Vec3 toBlast = blastOrigin.subtract(center);
        Vec3 blastDir = toBlast.lengthSqr() > 1.0E-4 ? toBlast.normalize() : new Vec3(0.0, -1.0, 0.0);
        Vec3 skyDir = new Vec3(0.35, 0.85, 0.30).normalize();

        for (int[] f : ICO_FACES) {
            Vec3 n = v[f[1]].subtract(v[f[0]]).cross(v[f[2]].subtract(v[f[0]]));
            Vec3 unitN = n.lengthSqr() > 1.0E-6 ? n.normalize() : skyDir;
            double skyDot = unitN.dot(skyDir) * 0.5 + 0.5;
            double blastDot = Math.max(0.0, unitN.dot(blastDir));

            float baseR = (float) (0.045 + 0.08 * skyDot + 0.92 * blastDot * blastDot);
            float baseG = (float) (0.032 + 0.06 * skyDot + 0.42 * blastDot * blastDot);
            float baseB = (float) (0.035 + 0.06 * skyDot + 0.08 * blastDot * blastDot);
            emitTri(vc, mat, v[f[0]], v[f[1]], v[f[2]], Mth.clamp(baseR, 0.0F, 1.0F), Mth.clamp(baseG, 0.0F, 1.0F), Mth.clamp(baseB, 0.0F, 1.0F), alpha);
        }
    }

    // Stylized cubic Yutapon anime debris shard (crisp jet-black backlit silhouette + blazing orange blast rim)
    private static final float[][] CUBE_VERTS = {
            {-1, -1, -1}, { 1, -1, -1}, { 1,  1, -1}, {-1,  1, -1},
            {-1, -1,  1}, { 1, -1,  1}, { 1,  1,  1}, {-1,  1,  1}
    };
    private static final int[][] CUBE_TRIS = {
            {0, 2, 1}, {0, 3, 2}, {4, 5, 6}, {4, 6, 7},
            {0, 1, 5}, {0, 5, 4}, {3, 7, 6}, {3, 6, 2},
            {0, 4, 7}, {0, 7, 3}, {1, 2, 6}, {1, 6, 5}
    };

    private static void emitYutaponCubeShard(
            VertexConsumer vc, Matrix4f mat, Vec3 center, Vec3 blastOrigin,
            double radius, float rotX, float rotY, int shardSeed, float alpha
    ) {
        float sx = 0.65F + 0.55F * hashFloat(shardSeed + 1);
        float sy = 0.55F + 0.65F * hashFloat(shardSeed + 2);
        float sz = 0.65F + 0.55F * hashFloat(shardSeed + 3);
        Vec3[] v = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            Vector3f p = new Vector3f(CUBE_VERTS[i][0] * sx, CUBE_VERTS[i][1] * sy, CUBE_VERTS[i][2] * sz);
            p.rotateX(rotX).rotateY(rotY).mul((float) radius);
            v[i] = center.add(p.x, p.y, p.z);
        }
        Vec3 toBlast = blastOrigin.subtract(center);
        Vec3 blastDir = toBlast.lengthSqr() > 1.0E-4 ? toBlast.normalize() : new Vec3(0.0, -1.0, 0.0);
        for (int[] f : CUBE_TRIS) {
            Vec3 n = v[f[1]].subtract(v[f[0]]).cross(v[f[2]].subtract(v[f[0]]));
            Vec3 unitN = n.lengthSqr() > 1.0E-6 ? n.normalize() : blastDir;
            double bDot = Math.max(0.0, unitN.dot(blastDir));
            float hot = (float) (bDot * bDot * bDot);
            float r = Mth.clamp(0.035F + 0.96F * hot, 0.0F, 1.0F);
            float g = Mth.clamp(0.025F + 0.46F * hot, 0.0F, 1.0F);
            float b = Mth.clamp(0.030F + 0.09F * hot, 0.0F, 1.0F);
            emitTri(vc, mat, v[f[0]], v[f[1]], v[f[2]], r, g, b, alpha);
        }
    }

    /** Emits the world-space bounding sphere for the 3D volumetric raymarched mushroom cloud (explosion_pyroclast). */
    private static void emitVolumetricMushroomSphere(
            VertexConsumer vc, Matrix4f mat, Vec3 center, double radius,
            int rings, int sectors, float uBoundRadius, float vSeedNorm,
            float progress, float heatFlash, float insideFlag, float alpha
    ) {
        float cr = Mth.clamp(progress, 0.0F, 1.0F);
        float cg = Mth.clamp(heatFlash, 0.0F, 1.0F);
        float cb = Mth.clamp(insideFlag, 0.0F, 1.0F);
        float ca = Mth.clamp(alpha, 0.0F, 1.0F);

        for (int ri = 0; ri < rings; ri++) {
            double phi1 = -Math.PI * 0.5 + (ri / (double) rings) * Math.PI;
            double phi2 = -Math.PI * 0.5 + ((ri + 1) / (double) rings) * Math.PI;
            for (int si = 0; si < sectors; si++) {
                double th1 = (si / (double) sectors) * Math.PI * 2.0;
                double th2 = ((si + 1) / (double) sectors) * Math.PI * 2.0;

                if (ri > 0) {
                    emitVolumeSphereVert(vc, mat, center, radius, phi1, th1, uBoundRadius, vSeedNorm, cr, cg, cb, ca);
                    emitVolumeSphereVert(vc, mat, center, radius, phi1, th2, uBoundRadius, vSeedNorm, cr, cg, cb, ca);
                    emitVolumeSphereVert(vc, mat, center, radius, phi2, th2, uBoundRadius, vSeedNorm, cr, cg, cb, ca);
                }
                if (ri < rings - 1) {
                    emitVolumeSphereVert(vc, mat, center, radius, phi1, th1, uBoundRadius, vSeedNorm, cr, cg, cb, ca);
                    emitVolumeSphereVert(vc, mat, center, radius, phi2, th2, uBoundRadius, vSeedNorm, cr, cg, cb, ca);
                    emitVolumeSphereVert(vc, mat, center, radius, phi2, th1, uBoundRadius, vSeedNorm, cr, cg, cb, ca);
                }
            }
        }
    }

    private static void emitVolumeSphereVert(
            VertexConsumer vc, Matrix4f mat, Vec3 center, double radius,
            double phi, double th, float u, float v,
            float r, float g, float b, float a
    ) {
        float nx = (float) (Math.cos(phi) * Math.sin(th));
        float ny = (float) Math.sin(phi);
        float nz = (float) (Math.cos(phi) * Math.cos(th));
        float px = (float) (center.x + nx * radius);
        float py = (float) (center.y + ny * radius);
        float pz = (float) (center.z + nz * radius);
        vc.addVertex(mat, px, py, pz).setUv(u, v).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }

    private static void emitPyroclasticExplosionDome(
            VertexConsumer vc,
            Matrix4f mat,
            Vec3 center,
            double radius,
            float progress,
            float heatFlash,
            float shellLayer,
            float fade,
            float timeSec,
            float lobeAmp
    ) {
        int rings = 30;
        int sectors = 56;
        double minPhi = -0.32 * (Math.PI * 0.5);
        double maxPhi = Math.PI * 0.5;
        double xzFlare = shellLayer <= 0.35F ? (1.06 + 0.04 * progress) : 1.04;
        double yStretch = shellLayer <= 0.35F ? (0.98 + 0.12 * smoothstep(progress / 0.65F)) : 1.02;

        float cr = Mth.clamp(progress, 0.0F, 1.0F);
        float cg = Mth.clamp(heatFlash, 0.0F, 1.0F);
        float cb = Mth.clamp(shellLayer, 0.0F, 1.0F);
        float ca = Mth.clamp(fade, 0.0F, 1.0F);

        for (int ri = 0; ri < rings; ri++) {
            float tf1 = ri / (float) rings;
            float tf2 = (ri + 1) / (float) rings;
            double phi1 = Mth.lerp(tf1, minPhi, maxPhi);
            double phi2 = Mth.lerp(tf2, minPhi, maxPhi);

            for (int si = 0; si < sectors; si++) {
                float sf1 = si / (float) sectors;
                float sf2 = (si + 1) / (float) sectors;
                double th1 = sf1 * Math.PI * 2.0;
                double th2 = sf2 * Math.PI * 2.0;

                if (ri < rings - 1) {
                    emitPyroVertex(vc, mat, center, radius, xzFlare, yStretch, lobeAmp, timeSec, phi1, th1, sf1, tf1, cr, cg, cb, ca);
                    emitPyroVertex(vc, mat, center, radius, xzFlare, yStretch, lobeAmp, timeSec, phi1, th2, sf2, tf1, cr, cg, cb, ca);
                    emitPyroVertex(vc, mat, center, radius, xzFlare, yStretch, lobeAmp, timeSec, phi2, th2, sf2, tf2, cr, cg, cb, ca);
                }
                emitPyroVertex(vc, mat, center, radius, xzFlare, yStretch, lobeAmp, timeSec, phi1, th1, sf1, tf1, cr, cg, cb, ca);
                emitPyroVertex(vc, mat, center, radius, xzFlare, yStretch, lobeAmp, timeSec, phi2, th2, sf2, tf2, cr, cg, cb, ca);
                emitPyroVertex(vc, mat, center, radius, xzFlare, yStretch, lobeAmp, timeSec, phi2, th1, sf1, tf2, cr, cg, cb, ca);
            }
        }
    }

    private static void emitPyroVertex(
            VertexConsumer vc,
            Matrix4f mat,
            Vec3 center,
            double radius,
            double xzFlare,
            double yStretch,
            float lobeAmp,
            float timeSec,
            double phi,
            double th,
            float u,
            float v,
            float r,
            float g,
            float b,
            float a
    ) {
        double cp = Math.cos(phi);
        double sp = Math.sin(phi);
        double ct = Math.cos(th);
        double st = Math.sin(th);

        double lobeWave = Math.sin(th * 3.0 + timeSec * 1.4) * 0.50
                + Math.cos(th * 5.0 - phi * 2.5 - timeSec * 1.9) * 0.35
                + Math.sin(th * 7.0 + phi * 3.0 + timeSec * 1.1) * 0.15;
        double radScale = 1.0 + lobeAmp * lobeWave * Math.max(0.15, cp);

        float nx = (float) (cp * st);
        float ny = (float) sp;
        float nz = (float) (cp * ct);
        float px = (float) (center.x + nx * radius * xzFlare * radScale);
        float py = (float) (center.y + ny * radius * yStretch * radScale);
        float pz = (float) (center.z + nz * radius * xzFlare * radScale);
        vc.addVertex(mat, px, py, pz).setUv(u, v).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }

    private static void emitTri(
            VertexConsumer vc, Matrix4f mat, Vec3 a, Vec3 b, Vec3 c,
            float r, float g, float bl, float al
    ) {
        vc.addVertex(mat, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, al);
        vc.addVertex(mat, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, al);
        vc.addVertex(mat, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, bl, al);
    }

    public static float hashFloat(int n) {
        int x = (n << 13) ^ n;
        return 1.0F - ((x * (x * x * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0F * 0.5F;
    }

    public static float smoothstep(float x) {
        float t = Mth.clamp(x, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        return smoothstep((x - edge0) / Math.max(1.0E-5F, edge1 - edge0));
    }
}
