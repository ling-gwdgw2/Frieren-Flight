// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.client.model.QualBossModel;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import com.frierenflight.zoltraakcinematic.entity.boss.QualBossEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom renderer for the Qual boss, pairing procedural magic geometry with core shaders:
 * ground circle, back halo, dark mana wisps, chest/horn flares, charge vortex, and warp ring.
 */
public class QualBossRenderer extends GeoEntityRenderer<QualBossEntity> {

    private static final List<DeferredQualVfxDraw> DEFERRED_DRAWS = new ArrayList<>();

    public record DeferredQualVfxDraw(
            QualBossRenderer renderer,
            QualBossEntity entity,
            float partialTick,
            PoseStack pose,
            Matrix4f modelView,
            Matrix4f projection
    ) {}

    public static boolean hasDeferredDraw() {
        return !DEFERRED_DRAWS.isEmpty();
    }

    public static void clearDeferred() {
        DEFERRED_DRAWS.clear();
    }

    public static void renderIrisPhase1(MultiBufferSource.BufferSource bufferSource, Matrix4fStack modelViewStack) {
        if (DEFERRED_DRAWS.isEmpty()) return;
        float bloomScale = ZoltraakCinematicClientConfig.getQualGlow();
        for (DeferredQualVfxDraw draw : DEFERRED_DRAWS) {
            if (draw.entity.isRemoved() || draw.entity.isInvisible()) continue;
            modelViewStack.set(draw.modelView);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(draw.projection, VertexSorting.DISTANCE_TO_ORIGIN);
            ZoltraakRenderPass.renderIrisScaled(bufferSource, bloomScale,
                    () -> draw.renderer.emitIrisEmissiveProxy(draw.entity, draw.partialTick, draw.pose, bufferSource));
        }
    }

    public static void renderDeferred(MultiBufferSource.BufferSource bufferSource, Matrix4fStack modelViewStack) {
        if (DEFERRED_DRAWS.isEmpty()) return;
        for (DeferredQualVfxDraw draw : DEFERRED_DRAWS) {
            if (draw.entity.isRemoved() || draw.entity.isInvisible()) continue;
            modelViewStack.set(draw.modelView);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(draw.projection, VertexSorting.DISTANCE_TO_ORIGIN);
            draw.renderer.renderQualMathematicalVFX(draw.entity, draw.partialTick, draw.pose, bufferSource);
        }
        DEFERRED_DRAWS.clear();
    }

    public QualBossRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new QualBossModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.6f;
    }

    @Override
    public boolean shouldRender(QualBossEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return entity.shouldRender(camX, camY, camZ);
    }

    @Override
    public void render(QualBossEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // Pass 1: GeckoLib Entity Animation
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        if (entity.isInvisible()) return;

        // Pass 2: Mathematical Procedural Shader VFX Pipeline
        if (!ZoltraakRenderPass.isReplaying() && ShaderCompatibility.useFullDetailPass()) {
            if (ShaderCompatibility.isShadowPass()) {
                return;
            }
            PoseStack copy = new PoseStack();
            copy.last().pose().set(poseStack.last().pose());
            copy.last().normal().set(poseStack.last().normal());
            DEFERRED_DRAWS.add(new DeferredQualVfxDraw(
                    this,
                    entity,
                    partialTick,
                    copy,
                    new Matrix4f(RenderSystem.getModelViewMatrix()),
                    new Matrix4f(RenderSystem.getProjectionMatrix())
            ));
            return;
        }

        renderQualMathematicalVFX(entity, partialTick, poseStack, bufferSource);
    }

    private void emitIrisEmissiveProxy(QualBossEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        float gameTime = entity.tickCount + partialTick;
        boolean isOverdrive = entity.getPhase() == 4;
        boolean isCataclysmic = entity.isCataclysmicCharging() || entity.getCastingState() == QualBossEntity.CAST_STATE_CATACLYSMIC;
        int flashTicks = entity.getTeleportFlashTicks();

        Vec3 camRight = new Vec3(new Vector3f(1.0f, 0.0f, 0.0f).rotate(camera.rotation()));
        Vec3 camUp = new Vec3(new Vector3f(0.0f, 1.0f, 0.0f).rotate(camera.rotation()));
        VertexConsumer quadVc = bufferSource.getBuffer(ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
        Matrix4f baseMat = poseStack.last().pose();

        // 1. Dark Mana Miasma wisps
        int particleCount = isOverdrive ? 8 : 5;
        for (int i = 0; i < particleCount; i++) {
            float offset = i * 1.618f;
            float lifeProgress = (gameTime * 0.020f + offset) % 1.0f;
            float alphaFactor = Mth.sin(lifeProgress * (float) Math.PI);
            float wispA = Mth.clamp(alphaFactor * (isOverdrive ? 0.85f : 0.65f) * 0.48f, 0.0f, 0.44f);
            float size = (0.32f + 0.09f * Mth.sin(offset + gameTime * 0.08f)) * alphaFactor;
            if (wispA <= 0.015f || size <= 0.01f) continue;

            float wg = ZoltraakRenderPass.irisFadeGuard(wispA);
            float angle = offset * 2.4f + gameTime * 0.025f;
            float rad = 0.55f + 0.20f * Mth.sin(offset * 3.1f + gameTime * 0.015f);
            Vec3 wispPos = new Vec3(Mth.cos(angle) * rad, 0.25f + lifeProgress * 1.85f, Mth.sin(angle) * rad);
            ZoltraakRenderPass.emitFeatheredStarBurst(
                    quadVc, baseMat, wispPos, camRight, camUp, size,
                    0.76f * wg, 0.22f * wg, 1.00f * wg, wispA
            );
        }

        // 2. Charge vortex
        if (isCataclysmic) {
            Vec3 vortexCenter = new Vec3(0.0, 1.85, 1.35);
            float corePulse = 2.05f + 0.40f * Mth.sin(gameTime * 0.8f);
            ZoltraakRenderPass.emitFeatheredStarBurst(
                    quadVc, baseMat, vortexCenter, camRight, camUp, corePulse,
                    0.80f, 0.24f, 1.00f, 0.42f
            );
            ZoltraakRenderPass.emitFeatheredStarBurst(
                    quadVc, baseMat, vortexCenter, camRight, camUp, corePulse * 0.52f,
                    0.96f, 0.52f, 1.00f, 0.48f
            );
            int arms = 4;
            int steps = 12;
            for (int a = 0; a < arms; a++) {
                float armOffset = (float) (a * Math.PI * 2.0 / arms);
                for (int i = 0; i < steps; i++) {
                    float u0 = (float) i / steps;
                    float u1 = (float) (i + 1) / steps;
                    float r0 = 4.2f * (float) Math.pow(1.0f - u0, 1.6);
                    float r1 = 4.2f * (float) Math.pow(1.0f - u1, 1.6);
                    float th0 = u0 * (float) Math.PI * 6.0f + armOffset + gameTime * 0.45f;
                    float th1 = u1 * (float) Math.PI * 6.0f + armOffset + gameTime * 0.45f;
                    Vec3 p0 = new Vec3(r0 * Mth.cos(th0), 1.85 + (u0 - 0.5f) * 0.8f, 1.35 + r0 * Mth.sin(th0));
                    Vec3 p1 = new Vec3(r1 * Mth.cos(th1), 1.85 + (u1 - 0.5f) * 0.8f, 1.35 + r1 * Mth.sin(th1));
                    Vec3 seg = p1.subtract(p0);
                    if (seg.lengthSqr() < 1.0E-6) continue;
                    Vec3 segSide = seg.cross(camRight.cross(camUp));
                    segSide = segSide.lengthSqr() < 1.0E-5 ? camUp : segSide.normalize();
                    float sa = Mth.clamp(0.88f * (1.0f - u0) * 0.48f, 0.0f, 0.46f);
                    float sg = ZoltraakRenderPass.irisFadeGuard(sa);
                    ZoltraakRenderPass.emitFeatheredBeamSegment(
                            quadVc, baseMat, p0, p1, segSide,
                            0.04, 0.22 * (1.0 - u0 * 0.4),
                            0.88f * sg, 0.32f * sg, 1.00f * sg, sa,
                            0.58f * sg, 0.14f * sg, 0.96f * sg, sa * 0.65f
                    );
                }
            }
        }

        // 3. Teleport / Reactive Barrier Shockwave Ring
        if (flashTicks > 0) {
            float progress = 1.0f - Math.max(0.0f, (flashTicks - partialTick) / 12.0f);
            if (progress >= 0.0f && progress <= 1.0f) {
                float radius = 5.2f * (1.0f - (float) Math.exp(-4.5 * progress));
                float a = Mth.clamp((1.0f - progress) * (1.0f - progress) * 0.42f, 0.0f, 0.44f);
                if (a > 0.02f) {
                    float ag = ZoltraakRenderPass.irisFadeGuard(a);
                    ZoltraakRenderPass.emitFeatheredRing(
                            quadVc, baseMat, new Vec3(0.0, 1.2, 0.0), new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0),
                            Math.max(0.1, radius - 0.85), radius, radius + 1.05, 28,
                            0.84f * ag, 0.28f * ag, 1.00f * ag, a
                    );
                }
            }
        }

        ZoltraakRenderTypes.endIrisBatch(bufferSource, ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
    }

    private void renderQualMathematicalVFX(QualBossEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        float bloomScale = ZoltraakCinematicClientConfig.getQualGlow();
        if (bloomScale <= 0.004f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        float gameTime = entity.tickCount + partialTick;
        int phase = entity.getPhase();
        boolean isCataclysmic = entity.isCataclysmicCharging() || entity.getCastingState() == QualBossEntity.CAST_STATE_CATACLYSMIC;
        boolean isOverdrive = phase == 4;

        // Ambient dark mana wisps
        renderSubtleDarkMiasma(entity, camera, gameTime, poseStack, bufferSource, isOverdrive);

        // Charge vortex
        if (isCataclysmic) {
            renderCataclysmicSingularity(entity, camera, gameTime, poseStack, bufferSource);
        }

        // Teleport shockwave
        int flashTicks = entity.getTeleportFlashTicks();
        if (flashTicks > 0) {
            renderTeleportShockwave(flashTicks, partialTick, poseStack, bufferSource);
        }

        // Flush batches for draw order and bloom scaling
        ZoltraakRenderPass.endBatchWithBloom(bufferSource, ZoltraakRenderTypes.BLACK_GLOW, bloomScale);
        ZoltraakRenderPass.endBatchWithBloom(bufferSource, ZoltraakRenderTypes.CORONA, bloomScale);
        ZoltraakRenderPass.endBatchWithBloom(bufferSource, ZoltraakRenderTypes.LIGHT, bloomScale);
        ZoltraakRenderPass.endBatchWithBloom(bufferSource, ZoltraakRenderTypes.ZOL_IMPACT, bloomScale);
    }

    /** Ambient dark mana wisps floating around Qual. */
    private void renderSubtleDarkMiasma(QualBossEntity entity, Camera camera, float gameTime, PoseStack poseStack, MultiBufferSource bufferSource, boolean isOverdrive) {
        VertexConsumer glowBuilder = bufferSource.getBuffer(ZoltraakRenderTypes.BLACK_GLOW);
        int particleCount = isOverdrive ? 8 : 5;

        for (int i = 0; i < particleCount; i++) {
            // Deterministic pseudo-random drifting cycle per particle
            float offset = (float) i * 1.618f;
            float lifeProgress = ((gameTime * 0.020f + offset) % 1.0f); // Slow gentle upward drift

            float angle = offset * 2.4f + gameTime * 0.025f;
            float radius = 0.55f + 0.20f * Mth.sin(offset * 3.1f + gameTime * 0.015f);
            float px = Mth.cos(angle) * radius;
            float pz = Mth.sin(angle) * radius;
            float py = 0.25f + lifeProgress * 1.85f; // Softly ascends from waist to above head

            // Smooth sinusoidal fade-in and fade-out
            float alphaFactor = Mth.sin(lifeProgress * (float) Math.PI);
            int alpha = (int) (alphaFactor * (isOverdrive ? 110 : 75)); // Translucent and pleasant
            float size = (0.18f + 0.05f * Mth.sin(offset + gameTime * 0.08f)) * alphaFactor;

            if (alpha <= 4 || size <= 0.01f) continue;

            poseStack.pushPose();
            poseStack.translate(px, py, pz);
            poseStack.mulPose(camera.rotation());

            // Dark void / corrupted purple wisp
            renderQuad(poseStack, glowBuilder, size, 110, 15, 160, alpha);
            poseStack.popPose();
        }
        flush(bufferSource, ZoltraakRenderTypes.BLACK_GLOW);
    }

    /** Charge vortex around Qual before casting. */
    private void renderCataclysmicSingularity(QualBossEntity entity, Camera camera, float gameTime, PoseStack poseStack, MultiBufferSource bufferSource) {
        poseStack.pushPose();
        poseStack.translate(0.0, 1.85, 1.35);

        float corePulse = 1.6f + 0.35f * Mth.sin(gameTime * 0.8f);
        poseStack.pushPose();
        poseStack.mulPose(camera.rotation());

        VertexConsumer coronaBuilder = bufferSource.getBuffer(ZoltraakRenderTypes.CORONA);
        int coronaAge = Mth.clamp(Math.round(gameTime % 255.0f), 0, 255);
        renderQuad(poseStack, coronaBuilder, corePulse, coronaAge, 80, 255, 255);
        flush(bufferSource, ZoltraakRenderTypes.CORONA);

        if (com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isLensFlaresEnabled()) {
            VertexConsumer flareBuilder = bufferSource.getBuffer(ZoltraakRenderTypes.BLACK_GLOW);
            renderQuad(poseStack, flareBuilder, corePulse * 1.8f, 230, 30, 255, 240);
            flush(bufferSource, ZoltraakRenderTypes.BLACK_GLOW);
        }

        poseStack.popPose();

        VertexConsumer lightBuilder = bufferSource.getBuffer(ZoltraakRenderTypes.LIGHT);
        Matrix4f mat = poseStack.last().pose();
        int arms = 4;
        int steps = 16;
        for (int a = 0; a < arms; a++) {
            float armOffset = (float) (a * Math.PI * 2.0 / arms);
            for (int i = 0; i < steps; i++) {
                float u0 = (float) i / steps;
                float u1 = (float) (i + 1) / steps;

                float r0 = 4.2f * (float) Math.pow(1.0f - u0, 1.6);
                float r1 = 4.2f * (float) Math.pow(1.0f - u1, 1.6);

                float theta0 = u0 * (float) Math.PI * 6.0f + armOffset + gameTime * 0.45f;
                float theta1 = u1 * (float) Math.PI * 6.0f + armOffset + gameTime * 0.45f;

                float x0 = r0 * Mth.cos(theta0);
                float z0 = r0 * Mth.sin(theta0);
                float y0 = (u0 - 0.5f) * 0.8f;

                float x1 = r1 * Mth.cos(theta1);
                float z1 = r1 * Mth.sin(theta1);
                float y1 = (u1 - 0.5f) * 0.8f;

                float w = 0.08f * (1.0f - u0 * 0.5f);
                int red = 240;
                int green = (int) (30 * (1.0f - u0));
                int blue = 255;
                int alpha = (int) (230 * (1.0f - u0));

                lightBuilder.addVertex(mat, x0, y0 - w, z0).setColor(red, green, blue, alpha);
                lightBuilder.addVertex(mat, x1, y1 - w, z1).setColor(red, green, blue, alpha);
                lightBuilder.addVertex(mat, x1, y1 + w, z1).setColor(red, green, blue, alpha);

                lightBuilder.addVertex(mat, x0, y0 - w, z0).setColor(red, green, blue, alpha);
                lightBuilder.addVertex(mat, x1, y1 + w, z1).setColor(red, green, blue, alpha);
                lightBuilder.addVertex(mat, x0, y0 + w, z0).setColor(red, green, blue, alpha);
            }
        }
        flush(bufferSource, ZoltraakRenderTypes.LIGHT);

        poseStack.popPose();
    }

    /** Teleport shockwave ring. */
    private void renderTeleportShockwave(int flashTicks, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        VertexConsumer impactBuilder = bufferSource.getBuffer(ZoltraakRenderTypes.ZOL_IMPACT);

        float progress = 1.0f - Math.max(0.0f, (flashTicks - partialTick) / 12.0f);
        if (progress < 0.0f || progress > 1.0f) return;

        float radius = 5.2f * (1.0f - (float) Math.exp(-4.5 * progress));
        int alpha = (int) (255 * (1.0f - progress) * (1.0f - progress));

        poseStack.pushPose();
        poseStack.translate(0.0, 1.2, 0.0);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
        renderQuad(poseStack, impactBuilder, radius, 220, 40, 255, alpha);
        poseStack.popPose();
        flush(bufferSource, ZoltraakRenderTypes.ZOL_IMPACT);
    }

    private static void renderQuad(PoseStack poseStack, VertexConsumer builder, float radius, int r, int g, int b, int a) {
        Matrix4f mat = poseStack.last().pose();
        builder.addVertex(mat, -radius, radius, 0.0f).setUv(0.0f, 0.0f).setColor(r, g, b, a);
        builder.addVertex(mat, radius, radius, 0.0f).setUv(1.0f, 0.0f).setColor(r, g, b, a);
        builder.addVertex(mat, radius, -radius, 0.0f).setUv(1.0f, 1.0f).setColor(r, g, b, a);
        builder.addVertex(mat, -radius, -radius, 0.0f).setUv(0.0f, 1.0f).setColor(r, g, b, a);
    }

    private static void flush(MultiBufferSource b, RenderType t) {
        ZoltraakRenderPass.endBatchWithBloom(b, t, ZoltraakCinematicClientConfig.getQualGlow());
    }
}
