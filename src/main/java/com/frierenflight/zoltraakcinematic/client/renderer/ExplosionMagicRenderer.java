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

    public static final float BLAST_DURATION = 96.0F;

    /** Inverted cone (funnel) sky ring heights & scales rising above the blast target. */
    public static final double[] SKY_RING_HEIGHT_FACTORS = { 0.19, 0.34, 0.50, 0.68, 0.89, 1.14 };
    public static final double[] SKY_RING_SCALE_FACTORS  = { 0.16, 0.25, 0.36, 0.50, 0.68, 1.15 };

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

    private static List<ExplosionMagicEntity> getActiveClientEntities(Minecraft mc, float partial) {
        List<ExplosionMagicEntity> active = new ArrayList<>();
        ACTIVE_ENTITIES.removeIf(e -> e == null || e.isRemoved() || e.level() != mc.level
                || (e.tickCount + partial) > ExplosionMagicEntity.LIFETIME_TICKS + 4.0F);
        active.addAll(ACTIVE_ENTITIES);
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof ExplosionMagicEntity exp && !exp.isRemoved()
                        && (exp.tickCount + partial) <= ExplosionMagicEntity.LIFETIME_TICKS
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

        float t = entity.tickCount + partial;
        if (t < 0.0F || t > ExplosionMagicEntity.LIFETIME_TICKS) return;

        float bloom = ZoltraakCinematicClientConfig.getExplosionGlow();
        Vec3 entPos = entity.getPosition(partial);
        Vec3 targetPos = entity.getTargetPos();
        Vec3 startPos = entity.getStartPos();
        if (entity.tickCount == 0 && targetPos.distanceToSqr(entPos) > 16.0) {
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

        double radius = entity.getRadius();
        int seed = entity.getSeed();
        float seedNorm = (Math.abs(seed % 1000)) / 1000.0F;
        float dt = t - ExplosionMagicEntity.DETONATE_TICK;

        if (irisBloomProxy) {
            emitIrisEmissiveProxy(buffers, mat, relTarget, relStart, relCam, camRight, camUp, radius, t, dt);
            return;
        }

        boolean shadersActive = ShaderCompatibility.useFullDetailPass() && ZoltraakCinematicClientConfig.isWorldGlowActive(true);
        float vfxBloom = shadersActive ? bloom * 0.64F : bloom;
        float shaderHaloMult = shadersActive ? 0.38F : 1.0F;

        // 1. Strike Sequence: Diagonal Beam -> Horizontal Sky Flare -> Persistent Vertical Laser Pillar (t = 0 .. 52)
        if (t < 52.0F) {
            emitStrikeAndPersistentLaser(buffers, mat, relTarget, relStart, relCam, camRight, camUp, radius, t, seed, seedNorm, vfxBloom);
        }

        // 2. Detonation & Aftermath Sequence (dt = -0.5 .. 96)
        if (dt >= -0.5F && dt < BLAST_DURATION) {
            float safeDt = Math.max(0.0F, dt);
            float p = Mth.clamp(safeDt / BLAST_DURATION, 0.0F, 1.0F);
            float blastScale = (float) (Math.pow(Mth.clamp(safeDt / 9.0F, 0.0F, 1.0F), 0.32) * (0.86 + 0.14 * p));
            double domeRadius = radius * 0.68 * blastScale;

            // 2a. Stylized Cel-Shaded Sunburst Dome + Fast Spherical Shockwave Shell + Volumetric Bloom Dome
            Vec3 axX = new Vec3(0.0, 0.0, 1.0);
            Vec3 axY = new Vec3(1.0, 0.0, 0.0);
            Vec3 axZ = new Vec3(0.0, 1.0, 0.0);
            Vec3 domeCenter = relTarget.add(0.0, 0.06, 0.0);
            float heatFlash = (float) Math.exp(-safeDt * 0.18);
            float domeFade = 1.0F - smoothstep((p - 0.86F) / 0.14F);

            if (domeFade > 0.005F && domeRadius > 0.25) {
                VertexConsumer domeVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_DOME);
                ZoltraakImpactFx.emitSurfaceHemisphereDome(
                        domeVc, mat, domeCenter, axX, axY, axZ,
                        domeRadius, 28, 56,
                        p, heatFlash, 0.0F, domeFade
                );
                ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_DOME, Math.max(0.45F, bloom));

                if (bloom > 0.004F) {
                    VertexConsumer haloVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_DOME_HALO);
                    // Exterior 1.38x volumetric bloom corona (tempered under shaderpacks so crimson/amber stays vivid)
                    if (p < 0.58F) {
                        ZoltraakImpactFx.emitSurfaceHemisphereDome(
                                haloVc, mat, domeCenter, axX, axY, axZ,
                                domeRadius * 1.38, 24, 48,
                                p, heatFlash, 1.0F, domeFade * (shadersActive ? 0.45F : 0.90F)
                        );
                    }
                    // Fast translucent spherical pressure shockwave dome (dt = 0..16)
                    if (safeDt < 16.0F) {
                        float sp = safeDt / 16.0F;
                        double pressureR = radius * (0.58 + 0.96 * Math.pow(sp, 0.52));
                        ZoltraakImpactFx.emitSurfaceHemisphereDome(
                                haloVc, mat, domeCenter, axX, axY, axZ,
                                pressureR, 20, 40,
                                sp, 1.0F, 0.60F, (1.0F - sp) * (shadersActive ? 0.68F : 1.0F)
                        );
                    }
                    ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_DOME_HALO, vfxBloom);
                }
            }

            // 2b. Mode 5 Pure Radial Gaussian Blooms, Swirling Gold Flame Arcs & Ground Crater Ripples (EXPLOSION_VORTEX_RING)
            if (bloom > 0.004F) {
                VertexConsumer bloomVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_VORTEX_RING);

                // Blinding golden-white ignition flash at dt = 0..10
                if (safeDt < 10.0F) {
                    float flashFade = (float) Math.exp(-safeDt * 0.34) * (1.0F - smoothstep((safeDt - 5.0F) / 5.0F));
                    double flashR = radius * (1.28 + 1.50 * smoothstep(safeDt / 8.0F));
                    emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, 0.14, 0.0), flashR, 0.0, 0.96F, 0.10F, 0.98F, flashFade * (shadersActive ? 0.48F : 0.88F));
                    double airR = flashR * 0.68;
                    emitBillboardQuad(bloomVc, mat, relTarget.add(0.0, airR * 0.92, 0.0), camRight, camUp, flashR * 0.95, airR, 0.92F, 0.10F, 0.98F, flashFade * (shadersActive ? 0.44F : 0.82F));
                }

                // Wide Golden-Amber Ground Light Disc (radius * 2.2)
                float groundGlowLife = (1.0F - smoothstep(0.0F, 0.88F, p));
                if (groundGlowLife > 0.01F) {
                    double groundLightR = Math.max(radius * 0.95, domeRadius * 2.25);
                    float palTint = Mth.lerp(smoothstep(0.28F, 0.78F, p), 0.72F, 0.46F);
                    emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, 0.07, 0.0), groundLightR, 0.0, palTint, 0.10F, 0.98F, groundGlowLife * 0.44F * shaderHaloMult);
                }

                // Bright entry hotspot where vertical laser meets top crown of dome
                if (safeDt < 26.0F && domeRadius > 0.5) {
                    float capAlpha = (1.0F - smoothstep(8.0F, 26.0F, safeDt)) * (shadersActive ? 0.56F : 0.88F);
                    double capR = radius * (0.28 + 0.12 * (1.0F - safeDt / 26.0F));
                    emitBillboardQuad(bloomVc, mat, relTarget.add(0.0, domeRadius * 0.98, 0.0), camRight, camUp, capR, capR * 0.65, 0.96F, 0.10F, 0.98F, capAlpha);
                }

                // Swirling Golden-Orange Flame Arcs orbiting soot dome perimeter (Mode 5, g = 0.85)
                if (p >= 0.22F && p < 0.70F && domeRadius > 1.0) {
                    float arcP = (p - 0.22F) / 0.48F;
                    float arcAlpha = (float) Math.sin(arcP * Math.PI) * 0.88F;
                    emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, domeRadius * 0.22, 0.0), domeRadius * 1.18, safeDt * 0.04, 0.74F, 0.85F, 0.98F, arcAlpha);
                    emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, domeRadius * 0.48, 0.0), domeRadius * 0.92, -safeDt * 0.05 + 1.4, 0.68F, 0.85F, 0.98F, arcAlpha * 0.78F);
                }

                // Soft Crimson-Amber Interior Volumetric Afterglow cloud hovering cleanly above ground
                if (p >= 0.42F && p < 0.96F) {
                    float hazeP = (p - 0.42F) / 0.54F;
                    float hazeAlpha = (float) Math.sin(hazeP * Math.PI) * 0.44F * shaderHaloMult;
                    double hazeR = radius * 0.85;
                    emitBillboardQuad(bloomVc, mat, relTarget.add(0.0, hazeR * 0.52, 0.0), camRight, camUp, hazeR * 1.15, hazeR * 0.56, 0.48F, 0.10F, 0.98F, hazeAlpha);
                }

                // 3 Concentric Flat Glowing Gold Ground Crater Ripples + 18 Ember Puddles (Mode 5, g = 0.50 & g = 0.10)
                if (safeDt >= 3.0F && safeDt < 92.0F) {
                    float cP = (safeDt - 3.0F) / 89.0F;
                    float cEnv = smoothstep(cP / 0.12F) * (1.0F - smoothstep((cP - 0.56F) / 0.44F));
                    if (cP > 0.35F) {
                        float rEnv = (float) Math.sin(Math.min(1.0F, (cP - 0.35F) / 0.62F) * Math.PI) * 0.82F;
                        emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, 0.09, 0.0), radius * 0.36, 0.0, 0.78F, 0.50F, 0.98F, rEnv);
                        emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, 0.10, 0.0), radius * 0.60, 0.0, 0.74F, 0.50F, 0.98F, rEnv * 0.76F);
                        emitHorizDiscQuad(bloomVc, mat, relTarget.add(0.0, 0.11, 0.0), radius * 0.84, 0.0, 0.68F, 0.50F, 0.98F, rEnv * 0.54F);
                    }
                    int puddles = 18;
                    for (int i = 0; i < puddles; i++) {
                        float h1 = hashFloat(seed + i * 37 + 201);
                        float h2 = hashFloat(seed + i * 59 + 211);
                        double pAng = i * (Math.PI * 2.0 / puddles) + h1 * 0.45;
                        double pDist = radius * (0.42 + 0.98 * h2);
                        double pSize = 0.55 + 0.65 * h1;
                        Vec3 puddlePos = relTarget.add(Math.cos(pAng) * pDist, 0.09, Math.sin(pAng) * pDist);
                        emitHorizDiscQuad(bloomVc, mat, puddlePos, pSize, 0.0, 0.74F + 0.10F * h1, 0.10F, 0.98F, cEnv * 0.92F);
                    }
                }

                ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_VORTEX_RING, vfxBloom);
            }

            // 2c. Crisp Expanding Outer Orange Shock Ring + Fast Flat Ground Pressure Ring (LIGHT)
            if (bloom > 0.004F) {
                VertexConsumer ringVc = buffers.getBuffer(ZoltraakRenderTypes.LIGHT);
                if (p >= 0.18F && p < 0.78F) {
                    float sp = (p - 0.18F) / 0.60F;
                    float sAlpha = (float) Math.sin(sp * Math.PI) * 0.92F;
                    double shockR = domeRadius * (0.94 + 0.30 * sp);
                    emitThinHorizRing(ringVc, mat, relTarget.add(0.0, domeRadius * 0.22, 0.0), shockR, 0.26, 40, 1.0F, 0.60F, 0.10F, sAlpha);
                }
                if (safeDt >= 1.0F && safeDt < 34.0F) {
                    float gp = (safeDt - 1.0F) / 33.0F;
                    float gAlpha = (1.0F - smoothstep(0.35F, 1.0F, gp)) * 0.70F;
                    double gShockR = radius * (0.65 + 1.38 * Math.pow(gp, 0.56));
                    emitThinHorizRing(ringVc, mat, relTarget.add(0.0, 0.16, 0.0), gShockR, 0.34 * (1.0 - 0.5 * gp), 40, 0.98F, 0.90F, 0.72F, gAlpha);
                }
                ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.LIGHT, bloom);
            }

            // 2d. TWO Tiers of Cream-White Scalloped Clouds on Dome + Dark Soot Smoke Chunks + Ground Dust Wave
            VertexConsumer dustVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_DUST_WAVE);

            // Waist & Crown Cloud Collars (Type 0.0, dt = 0.6..76)
            if (safeDt >= 0.6F && safeDt < 76.0F) {
                float wp = Mth.clamp((safeDt - 0.6F) / 75.0F, 0.0F, 1.0F);
                float wFade = smoothstep(wp / 0.05F) * (1.0F - smoothstep((wp - 0.58F) / 0.42F));
                if (wFade > 0.008F) {
                    float crownFade = wFade * (1.0F - smoothstep(0.38F, 0.72F, wp));
                    if (crownFade > 0.01F) {
                        int crownCount = 18;
                        double crownR = domeRadius * (0.56 + 0.14 * wp);
                        double crownY = domeRadius * 0.74;
                        double cHalfW = domeRadius * (0.16 + 0.05 * wp);
                        double cHalfH = domeRadius * (0.072 + 0.025 * wp);
                        for (int i = 0; i < crownCount; i++) {
                            double ang = i * (Math.PI * 2.0 / crownCount) + seedNorm * 4.1;
                            Vec3 cPos = relTarget.add(Math.cos(ang) * crownR, crownY + cHalfH * 0.14 * Math.cos(i * 2.0), Math.sin(ang) * crownR);
                            float cSeed = ((i * 41 + Math.abs(seed % 83)) % 100) / 100.0F;
                            emitBillboardQuad(dustVc, mat, cPos, camRight, camUp, cHalfW, cHalfH, wp, cSeed, 0.0F, crownFade);
                        }
                    }

                    int waistCount = 28;
                    double beltR = domeRadius * (0.90 + 0.25 * Math.pow(wp, 0.68));
                    double waistY = domeRadius * 0.35;
                    double halfW = domeRadius * (0.22 + 0.08 * wp);
                    double halfH = domeRadius * (0.095 + 0.035 * wp);
                    for (int i = 0; i < waistCount; i++) {
                        double ang = i * (Math.PI * 2.0 / waistCount) + seedNorm * 6.283;
                        double rJitter = beltR * (0.96 + 0.08 * Math.cos(i * 2.3 + seedNorm * 4.0));
                        Vec3 puffCenter = relTarget.add(
                                Math.cos(ang) * rJitter,
                                waistY + halfH * 0.18 * Math.sin(i * 3.0),
                                Math.sin(ang) * rJitter
                        );
                        float puffSeed = ((i * 37 + Math.abs(seed % 97)) % 100) / 100.0F;
                        emitBillboardQuad(dustVc, mat, puffCenter, camRight, camUp, halfW, halfH, wp, puffSeed, 0.0F, wFade);
                    }
                }
            }

            // Rolling Outer Ground Dust Wall (Type 1.0, bottom tangent flush above y=0 so ZERO sharp floor cut!)
            if (safeDt >= 1.2F && safeDt < 92.0F) {
                float dp = Mth.clamp((safeDt - 1.2F) / 90.8F, 0.0F, 1.0F);
                float dFade = smoothstep(dp / 0.05F) * (1.0F - smoothstep((dp - 0.56F) / 0.44F));
                if (dFade > 0.008F) {
                    int puffCount = 40;
                    double wallR = radius * (0.50 + 1.04 * Math.pow(dp, 0.52));
                    double halfW = radius * (0.17 + 0.12 * smoothstep(dp / 0.38F));
                    // Rises slightly taller in late aftermath while sitting cleanly flush on y=0
                    double halfH = radius * (0.072 + 0.068 * smoothstep(dp / 0.52F));
                    for (int i = 0; i < puffCount; i++) {
                        double ringOffset = (i % 2 == 0) ? 1.0 : 0.84;
                        double ang = i * (Math.PI * 2.0 / puffCount) + seedNorm * 3.14 + (i % 2) * 0.08;
                        double rCur = wallR * ringOffset * (0.95 + 0.10 * Math.sin(i * 3.1));
                        double curH = halfH * (0.88 + 0.24 * ((i % 3) * 0.5));
                        Vec3 puffCenter = relTarget.add(Math.cos(ang) * rCur, curH * 0.92, Math.sin(ang) * rCur);
                        float puffSeed = ((i * 53 + Math.abs(seed % 89)) % 100) / 100.0F;
                        emitBillboardQuad(dustVc, mat, puffCenter, camRight, camUp, halfW, curH, dp, puffSeed, 1.0F, dFade);
                    }
                }
            }

            // 26 Dark Flying Soot-Smoke Chunks launching outward through the fireball (Type 0.50)
            if (safeDt >= 1.0F && safeDt < 56.0F) {
                float sp = (safeDt - 1.0F) / 55.0F;
                float smokeAlpha = smoothstep(sp / 0.08F) * (1.0F - smoothstep(0.55F, 1.0F, sp));
                float tSec = safeDt * 0.05F;
                int chunkCount = 26;
                for (int i = 0; i < chunkCount; i++) {
                    float h1 = hashFloat(seed + i * 29 + 401);
                    float h2 = hashFloat(seed + i * 47 + 409);
                    float h3 = hashFloat(seed + i * 61 + 419);
                    double ang = h1 * Math.PI * 2.0 + i * 0.72;
                    double vH = radius * (0.42 + 0.52 * h2);
                    double vU = radius * (0.40 + 0.55 * h3);
                    double gx = Math.cos(ang) * (radius * 0.22 + vH * tSec / (1.0 + tSec * 0.65));
                    double gz = Math.sin(ang) * (radius * 0.22 + vH * tSec / (1.0 + tSec * 0.65));
                    double gy = 1.2 + vU * tSec - 0.5 * 14.0 * tSec * tSec;
                    if (gy < 0.25) continue;
                    double cSz = 0.55 + 0.75 * h1 + sp * 0.45;
                    Vec3 scPos = relTarget.add(gx, gy, gz);
                    emitBillboardQuad(dustVc, mat, scPos, camRight, camUp, cSz, cSz * 0.85, sp, h2, 0.50F, smokeAlpha * 0.85F);
                }
            }

            ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_DUST_WAVE, Math.max(0.55F, bloom));

            // 2e. 56 Craggy 3D Dark Boulders, 80 Soft-Blooming Radial Speed Streaks & 120 Twinkling Blooming Aftermath Embers
            if (safeDt >= 0.2F && safeDt < 95.0F) {
                emitDebrisSparksAndEmbers(buffers, mat, relTarget, relCam, camRight, camUp, radius, safeDt, seed, vfxBloom);
            }
        }
    }

    private static void emitStrikeAndPersistentLaser(
            MultiBufferSource buffers, Matrix4f mat,
            Vec3 relTarget, Vec3 relStart, Vec3 relCam, Vec3 camRight, Vec3 camUp,
            double radius, float t, int seed, float seedNorm, float bloom
    ) {
        double apexHeight = radius * SKY_RING_HEIGHT_FACTORS[5];
        Vec3 skyApex = relTarget.add(0.0, apexHeight, 0.0);
        Vec3 groundTip = relTarget.add(0.0, 0.15, 0.0);

        VertexConsumer vortexVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_VORTEX_RING);

        // 1. Inverted-cone sky rings holding then collapsing as vertical pillar fires (t = 0 .. 17)
        if (t < 17.0F && bloom > 0.004F) {
            float collapseP = Mth.clamp((t - 5.0F) / 12.0F, 0.0F, 1.0F);
            float skyFade = 1.0F - smoothstep((t - 8.0F) / 9.0F);
            emitSkyVortexColumn(vortexVc, mat, relTarget, radius, 1.0F, 1.0F - 0.26F * collapseP, skyFade, seedNorm, t * 0.09);
        }

        // 2. Stage 1 (t = 0 .. 6.2): Soft-Blooming Diagonal Golden Beam from Caster Staff UP to Sky Apex
        if (t < 6.2F && relStart.distanceToSqr(skyApex) > 4.0) {
            float diagFade = 1.0F - smoothstep((t - 3.4F) / 2.8F);
            float diagReach = Mth.clamp(t / 2.6F, 0.08F, 1.0F);
            Vec3 diagHead = relStart.lerp(skyApex, diagReach);
            emitSoftBloomBeamQuad(vortexVc, mat, relStart, diagHead, relCam, 0.72 * diagFade, 0.15F, 0.20F, diagFade * 0.98F);
        }

        // 3. Stage 2 (t = 2.4 .. 8.8): Super-Wide Horizontal Golden Lens-Flare Slash at Sky Apex
        if (t >= 2.4F && t < 8.8F && bloom > 0.004F) {
            float flareP = (t - 2.4F) / 6.4F;
            float flareAlpha = (float) Math.sin(flareP * Math.PI);
            double slashHalfLen = radius * (1.42 + 0.80 * flareP);
            double slashHalfW = radius * 0.20 * flareAlpha;
            emitSoftBloomBeamQuad(
                    vortexVc, mat,
                    skyApex.subtract(camRight.scale(slashHalfLen)),
                    skyApex.add(camRight.scale(slashHalfLen)),
                    relCam, slashHalfW, 0.20F, 0.85F, flareAlpha * 0.98F
            );
            emitBillboardQuad(vortexVc, mat, skyApex, camRight, camUp, radius * 0.55, radius * 0.55, 0.96F, 0.10F, 0.98F, flareAlpha * 0.90F);
        }

        // 4. Stage 3 (t = 4.0 .. 52.0): Persistent Soft-Blooming Vertical Laser Pillar
        if (t >= 4.0F && t < 52.0F) {
            float vAge = t - 4.0F;
            float strikeReach = Mth.clamp(vAge / 1.6F, 0.08F, 1.0F);
            Vec3 headPos = skyApex.lerp(groundTip, strikeReach);

            float widePhase = 1.0F - smoothstep(12.0F, 22.0F, vAge);
            float needleFade = 1.0F - smoothstep(34.0F, 48.0F, vAge);
            double beamHalfW = radius * (0.044 + 0.26 * widePhase) * needleFade;
            float pinkTint = 0.45F * widePhase;
            emitSoftBloomBeamQuad(vortexVc, mat, skyApex, headPos, relCam, beamHalfW, pinkTint, 0.15F, needleFade * 0.98F);
        }

        // 5. Lingering Diagonal Sparkle Trail (Mode 5 dot bloom, t = 0.5 .. 13.5)
        if (t >= 0.5F && t < 13.5F && bloom > 0.004F && relStart.distanceToSqr(skyApex) > 4.0) {
            float trailFade = 1.0F - smoothstep(6.0F, 13.5F, t);
            int trailSparks = 32;
            for (int i = 0; i < trailSparks; i++) {
                float h1 = hashFloat(seed + i * 29 + 301);
                float h2 = hashFloat(seed + i * 53 + 313);
                double u = (i + 0.5) / (double) trailSparks;
                if (u > t / 2.5) continue;
                Vec3 base = relStart.lerp(skyApex, u);
                Vec3 jitter = new Vec3((h1 - 0.5) * 1.6, (h2 - 0.5) * 1.6 - 0.04 * t, ((h1 * 1.7) % 1.0 - 0.5) * 1.6);
                double sz = (0.18 + 0.20 * h1) * trailFade;
                emitBillboardQuad(vortexVc, mat, base.add(jitter), camRight, camUp, sz, sz, 0.68F + 0.16F * h2, 0.10F, 0.98F, trailFade * 0.90F);
            }
        }

        ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.EXPLOSION_VORTEX_RING, Math.max(0.5F, bloom));

        // 6. Coaxial Smoke Rings on Diagonal Beam + Concentric Golden Beam Rings on Vertical Laser
        if (t < 18.0F) {
            VertexConsumer ringVc = buffers.getBuffer(ZoltraakRenderTypes.LIGHT);
            if (t < 6.5F && relStart.distanceToSqr(skyApex) > 4.0) {
                float diagFade = 1.0F - smoothstep((t - 3.6F) / 2.9F);
                Vec3 diagAxis = skyApex.subtract(relStart).normalize();
                emitCoaxialBeamRing(ringVc, mat, relStart.lerp(skyApex, 0.26), diagAxis, 1.45 + t * 0.18, 0.12, 24, 0.96F, 0.94F, 0.90F, diagFade * 0.72F);
                emitCoaxialBeamRing(ringVc, mat, relStart.lerp(skyApex, 0.58), diagAxis, 1.15 + t * 0.15, 0.10, 24, 0.96F, 0.94F, 0.90F, diagFade * 0.62F);
            }
            if (t >= 4.2F && t < 17.0F) {
                float ringAge = t - 4.2F;
                float rLife = 1.0F - smoothstep(6.0F, 12.8F, ringAge);
                double[] ringAltitudes = { 0.10, 0.28, 0.52, 0.78 };
                for (int i = 0; i < ringAltitudes.length; i++) {
                    double ry = apexHeight * ringAltitudes[i];
                    double rr = radius * (0.18 + 0.06 * i + 0.045 * ringAge);
                    emitThinHorizRing(ringVc, mat, relTarget.add(0.0, ry, 0.0), rr, 0.14 * rLife, 32, 1.0F, 0.86F, 0.28F, rLife * 0.85F);
                }
            }
            ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.LIGHT, Math.max(0.45F, bloom));
        }

        // 7. Crisp Anamorphic Horizontal Glint on Sky Apex Flare Slash (ZOL_GLOW)
        if (t >= 2.4F && t < 8.8F && bloom > 0.004F) {
            float flareP = (t - 2.4F) / 6.4F;
            float flareAlpha = (float) Math.sin(flareP * Math.PI);
            VertexConsumer flareVc = buffers.getBuffer(ZoltraakRenderTypes.ZOL_GLOW);
            emitBillboardQuad(flareVc, mat, skyApex, camRight, camUp, radius * 1.45, 1.8, 1.0F, 0.92F, 0.52F, flareAlpha * 0.85F);
            ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.ZOL_GLOW, bloom);
        }
    }

    private static void emitDebrisSparksAndEmbers(
            MultiBufferSource buffers, Matrix4f mat,
            Vec3 relTarget, Vec3 relCam, Vec3 camRight, Vec3 camUp,
            double radius, float dt, int seed, float bloom
    ) {
        float tSec = dt * 0.05F;

        // A. 56 Craggy 3D Dark Rock Boulders with warm inner blast rim-lighting
        if (dt < 74.0F) {
            float rockAlpha = 1.0F - smoothstep((dt - 50.0F) / 24.0F);
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
                    double vUp = radius * (0.44 + 0.70 * h3);
                    double drag = 1.0 / (1.0 + tSec * 0.42);
                    double gx = Math.cos(ang) * (radius * 0.20 + vHoriz * tSec * drag);
                    double gz = Math.sin(ang) * (radius * 0.20 + vHoriz * tSec * drag);
                    double gy = 0.8 + vUp * tSec - 0.5 * 16.5 * tSec * tSec;
                    if (gy < -0.3) continue;

                    double sz = 0.15 + 0.44 * (h4 * h4);
                    Vec3 center = relTarget.add(gx, gy, gz);
                    float spinA = dt * (0.12F + 0.15F * h2) + h1 * 6.28F;
                    float spinB = dt * (0.09F + 0.12F * h3) + h2 * 6.28F;
                    emitCraggyRockBoulder(rockVc, mat, center, relTarget, sz, spinA, spinB, i * 17 + seed, rockAlpha);
                }
                ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.BLACK_CORE, 1.0F);
            }
        }

        if (bloom <= 0.004F) return;

        VertexConsumer fxVc = buffers.getBuffer(ZoltraakRenderTypes.EXPLOSION_VORTEX_RING);

        // B. 80 Soft-Blooming Radial Orange-Yellow Speed Streaks (Mode 4)
        if (dt < 40.0F) {
            float sparkFade = 1.0F - smoothstep((dt - 14.0F) / 26.0F);
            int sparkCount = 80;
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

                double speed = radius * (0.95 + 1.20 * h3);
                double travel = speed * tSec * (1.0 / (1.0 + tSec * 1.12));
                Vec3 pos = relTarget.add(launchDir.scale(radius * 0.26 + travel)).add(0.0, -2.4 * tSec * tSec, 0.0);
                if (pos.y < -0.2) continue;

                double halfLen = (0.42 + 0.72 * h2) * (0.48 + 0.52 * sparkFade);
                double halfW = 0.12 + 0.09 * h1;
                Vec3 tail = pos.subtract(launchDir.scale(halfLen));
                Vec3 head = pos.add(launchDir.scale(halfLen));
                emitSoftBloomBeamQuad(fxVc, mat, tail, head, relCam, halfW, 0.10F, 0.85F, sparkFade * 0.96F);
            }
        }

        // C. 120 Twinkling Blooming Golden-Orange Aftermath Ember Sparks (Mode 5 radial glow dots)
        if (dt >= 8.0F && dt < 95.0F) {
            float ep = (dt - 8.0F) / 87.0F;
            float emberEnv = smoothstep(ep / 0.14F) * (1.0F - smoothstep((ep - 0.66F) / 0.34F));
            int emberCount = 120;
            for (int i = 0; i < emberCount; i++) {
                float h1 = hashFloat(seed + i * 31 + 101);
                float h2 = hashFloat(seed + i * 47 + 107);
                float h3 = hashFloat(seed + i * 73 + 113);

                double ang = h1 * Math.PI * 2.0 + tSec * (0.22 + 0.32 * h2);
                double rDist = radius * (0.12 + 0.84 * Math.sqrt(h2)) * (0.85 + 0.25 * ep);
                double ey = 0.35 + h3 * (radius * 0.66) + Math.sin(tSec * 2.2 + i) * 0.45 + ep * 2.2;
                Vec3 ePos = relTarget.add(Math.cos(ang) * rDist, ey, Math.sin(ang) * rDist);

                float twinkle = 0.50F + 0.50F * (float) Math.sin(dt * (0.28 + 0.18 * h1) + i * 1.7);
                float ea = emberEnv * twinkle * 0.95F;
                if (ea <= 0.02F) continue;
                double eSize = 0.16 + 0.18 * h1;
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
        double apexHeight = radius * SKY_RING_HEIGHT_FACTORS[5];
        Vec3 skyApex = relTarget.add(0.0, apexHeight, 0.0);
        Vec3 axU = new Vec3(1.0, 0.0, 0.0);
        Vec3 axV = new Vec3(0.0, 0.0, 1.0);

        // Restrained HDR proxies so shaderpacks bloom cleanly around sharp details without blowing colors to white/yellow
        // 1. Diagonal golden beam HDR bloom (t = 0..6.5)
        if (t < 6.5F && relStart.distanceToSqr(skyApex) > 4.0) {
            float dFade = (1.0F - smoothstep((t - 3.4F) / 3.1F)) * 0.36F;
            float g = ZoltraakRenderPass.irisFadeGuard(dFade);
            Vec3 diagSide = skyApex.subtract(relStart).cross(relCam.subtract(relStart));
            diagSide = diagSide.lengthSqr() < 1.0E-5 ? camRight : diagSide.normalize();
            ZoltraakRenderPass.emitFeatheredBeamSegment(
                    quadVc, mat, relStart, skyApex, diagSide,
                    0.22 * dFade, 0.65 * dFade,
                    0.96F * g, 0.88F * g, 0.62F * g, dFade,
                    0.96F * g, 0.44F * g, 0.08F * g, dFade * 0.45F
            );
        }

        // 2. Sky Apex horizontal lens flare HDR bloom (t = 2.4..9.0)
        if (t >= 2.4F && t < 9.0F) {
            float flareP = (t - 2.4F) / 6.6F;
            float flareA = (float) Math.sin(flareP * Math.PI) * 0.34F;
            float fg = ZoltraakRenderPass.irisFadeGuard(flareA);
            ZoltraakRenderPass.emitFeatheredStarBurst(
                    quadVc, mat, skyApex, camRight, camUp, radius * 0.90,
                    0.96F * fg, 0.84F * fg, 0.34F * fg, flareA
            );
        }

        // 3. Sky Vortex Rings thin rim HDR accent (t = 0..17)
        if (t < 17.0F) {
            float skyFade = (1.0F - smoothstep((t - 8.0F) / 9.0F)) * 0.16F;
            float sg = ZoltraakRenderPass.irisFadeGuard(skyFade);
            for (int i = 0; i < SKY_RING_HEIGHT_FACTORS.length; i++) {
                double ry = radius * SKY_RING_HEIGHT_FACTORS[i];
                double rr = radius * SKY_RING_SCALE_FACTORS[i];
                ZoltraakRenderPass.emitFeatheredRing(
                        quadVc, mat, relTarget.add(0.0, ry, 0.0), axU, axV,
                        rr * 0.72, rr * 0.81, rr * 0.90, 22,
                        0.96F * sg, 0.32F * sg, 0.05F * sg, skyFade
                );
            }
        }

        // 4. Persistent Vertical Laser Pillar HDR bloom (t = 4..48)
        if (t >= 4.0F && t < 48.0F) {
            float vAge = t - 4.0F;
            float widePhase = 1.0F - smoothstep(12.0F, 22.0F, vAge);
            float skyFade = (1.0F - smoothstep(32.0F, 44.0F, vAge)) * 0.38F;
            float g = ZoltraakRenderPass.irisFadeGuard(skyFade);
            Vec3 beamSide = relTarget.subtract(skyApex).cross(relCam.subtract(skyApex));
            beamSide = beamSide.lengthSqr() < 1.0E-5 ? new Vec3(1.0, 0.0, 0.0) : beamSide.normalize();
            ZoltraakRenderPass.emitFeatheredBeamSegment(
                    quadVc, mat, skyApex, relTarget, beamSide,
                    radius * (0.03 + 0.10 * widePhase),
                    radius * (0.09 + 0.24 * widePhase),
                    0.96F * g, 0.88F * g, 0.62F * g, skyFade,
                    0.96F * g, 0.30F * g, 0.08F * g, skyFade * 0.45F
            );
        }

        // 5. 3D Fireball Dome + Equatorial Rim + Ground Flash HDR bloom for Iris / Complementary (dt = 0..64)
        if (dt >= 0.0F && dt < 64.0F) {
            float p = Mth.clamp(dt / 64.0F, 0.0F, 1.0F);
            float glowFade = (float) Math.exp(-dt * 0.032) * (1.0F - smoothstep((p - 0.52F) / 0.48F));
            if (glowFade > 0.015F) {
                float fg = ZoltraakRenderPass.irisFadeGuard(glowFade);
                float growth = (float) Math.pow(Mth.clamp(dt / 9.0F, 0.0F, 1.0F), 0.32);
                double domeR = radius * 0.68 * growth;

                // Ground ignition bloom ring
                ZoltraakRenderPass.emitFeatheredRing(
                        quadVc, mat, relTarget.add(0.0, 0.18, 0.0), axU, axV,
                        domeR * 0.50, domeR * 1.10, domeR * 1.65, 28,
                        0.96F * fg, 0.56F * fg, 0.12F * fg, glowFade * 0.22F
                );

                // Silhouette outer halo ring facing camera so shaderpack blooms the fireball rim cleanly
                ZoltraakRenderPass.emitFeatheredRing(
                        quadVc, mat, relTarget.add(0.0, domeR * 0.38, 0.0), camRight, camUp,
                        domeR * 0.72, domeR * 0.94, domeR * 1.18, 26,
                        0.96F * fg, 0.40F * fg, 0.07F * fg, glowFade * 0.18F
                );

                // Center starburst flare for peak flash
                ZoltraakRenderPass.emitFeatheredStarBurst(
                        quadVc, mat, relTarget.add(0.0, domeR * 0.46, 0.0), camRight, camUp, domeR * 0.96,
                        0.96F * fg, 0.66F * fg, 0.20F * fg, glowFade * 0.26F
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
     * Emits the 6 stacked inverted-cone (funnel) fiery sky vortex rings above the blast target
     * (smallest ring at bottom tapering outward to a wide vortex ring at top).
     */
    public static void emitSkyVortexColumn(
            VertexConsumer vc, Matrix4f mat, Vec3 center, double radius,
            float castProgress, float scaleMult, float alpha, float seedNorm, double spinPhase
    ) {
        if (alpha <= 0.005F) return;
        for (int i = 0; i < SKY_RING_HEIGHT_FACTORS.length; i++) {
            float appearStart = 0.12F + i * 0.072F;
            float ringReveal = smoothstep((castProgress - appearStart) / 0.15F);
            if (ringReveal <= 0.01F) continue;

            float layerFade = alpha * ringReveal * (0.80F + 0.20F * (i / 5.0F));
            double r = radius * SKY_RING_SCALE_FACTORS[i] * scaleMult * (0.72 + 0.28 * ringReveal);
            Vec3 pos = center.add(0.0, radius * SKY_RING_HEIGHT_FACTORS[i], 0.0);
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

            float baseR = (float) (0.14 + 0.14 * skyDot + 0.38 * blastDot * blastDot);
            float baseG = (float) (0.11 + 0.11 * skyDot + 0.17 * blastDot * blastDot);
            float baseB = (float) (0.09 + 0.09 * skyDot + 0.04 * blastDot * blastDot);
            emitTri(vc, mat, v[f[0]], v[f[1]], v[f[2]], Mth.clamp(baseR, 0.0F, 1.0F), Mth.clamp(baseG, 0.0F, 1.0F), Mth.clamp(baseB, 0.0F, 1.0F), alpha);
        }
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
