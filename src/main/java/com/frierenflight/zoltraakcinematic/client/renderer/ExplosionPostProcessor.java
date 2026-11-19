// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import com.frierenflight.zoltraakcinematic.entity.ExplosionMagicEntity;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.List;

/**
 * Full-screen anime cinema post-processing pass for Explosion Magic.
 * Renders dynamic terrain firelight, sweeping ground shockwave illumination, sky eclipse,
 * horizon Mie glow, radial shockwave screen refraction + CA, god rays, and Nakamura impact frames.
 */
public final class ExplosionPostProcessor {

    private static TextureTarget sceneCopy;
    private static TextureTarget postTarget;

    private static boolean hasPendingIrisPostPass = false;
    private static boolean hasCapturedIrisProjection = false;
    private static boolean irisRenderedThisFrame = false;
    private static boolean loggedErrorOnce = false;
    private static final Matrix4f PENDING_IRIS_PROJECTION = new Matrix4f();
    private static float pendingIrisPartial = 0.0f;

    private static final Matrix4f SCRATCH_INV_PROJ = new Matrix4f();
    private static final Matrix4f SCRATCH_VIEW_ROT = new Matrix4f();
    private static final Vector3f SCRATCH_REL_POS = new Vector3f();
    private static final Vector3f SCRATCH_EYE_BLAST = new Vector3f();
    private static final Vector3f SCRATCH_EYE_APEX = new Vector3f();
    private static final Vector4f SCRATCH_CLIP = new Vector4f();

    private record PostJob(
            Vec3 blastWorld,
            Vec3 skyApexWorld,
            float visualRadius,
            float chargeIntensity,
            float normTime,
            float dt,
            float blastProgress
    ) {}

    private ExplosionPostProcessor() {}

    public static void clearDeferredIrisPass() {
        hasPendingIrisPostPass = false;
        hasCapturedIrisProjection = false;
        irisRenderedThisFrame = false;
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (ShaderCompatibility.isShadowPass()) {
            return;
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            if (!ShaderCompatibility.useFullDetailPass()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                hasPendingIrisPostPass = false;
                return;
            }
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            if (!hasAnyActivePostWork(mc, partial)) {
                hasPendingIrisPostPass = false;
                return;
            }
            Matrix4f projection = event.getProjectionMatrix() != null ? event.getProjectionMatrix() : RenderSystem.getProjectionMatrix();
            PENDING_IRIS_PROJECTION.set(projection);
            pendingIrisPartial = partial;
            hasCapturedIrisProjection = true;
            hasPendingIrisPostPass = true;
            return;
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                hasPendingIrisPostPass = false;
                return;
            }
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            if (ShaderCompatibility.useFullDetailPass() && irisRenderedThisFrame) {
                hasPendingIrisPostPass = false;
                return;
            }
            if (!hasAnyActivePostWork(mc, partial)) {
                hasPendingIrisPostPass = false;
                return;
            }

            hasPendingIrisPostPass = false;
            irisRenderedThisFrame = true;

            Camera camera = event.getCamera() != null ? event.getCamera()
                    : (mc.gameRenderer != null ? mc.gameRenderer.getMainCamera() : null);
            if (camera == null) {
                return;
            }
            Matrix4f projection = event.getProjectionMatrix() != null ? event.getProjectionMatrix() : RenderSystem.getProjectionMatrix();
            Matrix4f view = new Matrix4f().rotation(new Quaternionf(camera.rotation()).conjugate());
            renderPostPass(mc, projection, view, camera, partial);
        }
    }

    public static void renderDeferredIrisPass() {
        if (!ShaderCompatibility.useFullDetailPass() || ShaderCompatibility.isShadowPass() || irisRenderedThisFrame) {
            return;
        }
        hasPendingIrisPostPass = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) {
            return;
        }
        float partial = hasCapturedIrisProjection ? pendingIrisPartial : 0.0f;
        if (!hasAnyActivePostWork(mc, partial)) {
            return;
        }

        Matrix4f projection = hasCapturedIrisProjection
                ? new Matrix4f(PENDING_IRIS_PROJECTION)
                : new Matrix4f(RenderSystem.getProjectionMatrix());
        irisRenderedThisFrame = true;

        Matrix4f view = new Matrix4f().rotation(new Quaternionf(camera.rotation()).conjugate());
        renderPostPass(mc, projection, view, camera, partial);
    }

    private static boolean hasAnyActivePostWork(Minecraft mc, float partial) {
        if (ZoltraakCinematicShaders.explosionPost == null) {
            return false;
        }
        if (!ExplosionMagicRenderer.getActiveClientEntities(mc, partial).isEmpty()) {
            return true;
        }
        ExplosionChargeRenderer.ChargePostAnchor charge = ExplosionChargeRenderer.getStrongestChargeAnchor(mc, partial);
        return charge != null && charge.progress() > 0.28F;
    }

    private static void renderPostPass(Minecraft mc, Matrix4f projection, Matrix4f view, Camera camera, float partial) {
        ShaderInstance postShader = ZoltraakCinematicShaders.explosionPost;
        if (postShader == null || !RenderSystem.isOnRenderThread()) {
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            return;
        }

        Vec3 camPos = camera.getPosition();
        float domeScale = ZoltraakCinematicClientConfig.getExplosionDomeScale();
        PostJob job = resolvePrimaryJob(mc, camPos, partial, domeScale);
        if (job == null) {
            return;
        }

        double dist = camPos.distanceTo(job.blastWorld);
        double maxDist = Math.max(160.0, job.visualRadius * 7.5);
        if (dist >= maxDist) {
            return;
        }
        float distFactor = (float) Math.pow(Mth.clamp(1.0 - dist / maxDist, 0.0, 1.0), 0.72);

        int prevReadFbo = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDrawFbo = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean prevStencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        ShaderInstance prevShader = RenderSystem.getShader();
        float[] prevShaderColor = RenderSystem.getShaderColor().clone();
        int prevActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int depthCompareSavedTex = -1;
        int depthCompareSavedMode = GL14.GL_NONE;

        try {
            ensureTargets(main.width, main.height);

            if (prevScissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (prevStencil) GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.colorMask(true, true, true, true);

            boolean isShaderPack = ShaderCompatibility.useFullDetailPass();
            int irisDepthTex = ShaderCompatibility.getShaderDepthTexture();
            int activeDrawFbo = isShaderPack ? main.frameBufferId : ((prevDrawFbo != 0) ? prevDrawFbo : main.frameBufferId);

            int depthSamplerId = (irisDepthTex > 0 && GL11.glIsTexture(irisDepthTex)) ? irisDepthTex : main.getDepthTextureId();
            if ((depthSamplerId <= 0 || !GL11.glIsTexture(depthSamplerId)) && activeDrawFbo > 0) {
                int oldFbo = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, activeDrawFbo);
                depthSamplerId = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFbo);
            }

            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            int savedTex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            if (depthSamplerId > 0 && GL11.glIsTexture(depthSamplerId)) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthSamplerId);
                int cmpMode = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE);
                if (cmpMode != GL14.GL_NONE) {
                    depthCompareSavedTex = depthSamplerId;
                    depthCompareSavedMode = cmpMode;
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL14.GL_NONE);
                }
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedTex0);
            GlStateManager._activeTexture(prevActiveTex != 0 ? prevActiveTex : GL13.GL_TEXTURE0);

            // Copy active scene into sceneCopy
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, activeDrawFbo);
            int prevReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
            if (activeDrawFbo > 0 && prevReadBuffer != GL30.GL_COLOR_ATTACHMENT0) {
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
            GlStateManager._glBlitFrameBuffer(
                    0, 0, main.width, main.height,
                    0, 0, sceneCopy.width, sceneCopy.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST
            );
            if (activeDrawFbo > 0 && prevReadBuffer != GL30.GL_COLOR_ATTACHMENT0) {
                GL11.glReadBuffer(prevReadBuffer);
            }

            SCRATCH_INV_PROJ.set(projection).invert();
            SCRATCH_VIEW_ROT.set(view).setTranslation(0.0f, 0.0f, 0.0f);

            SCRATCH_REL_POS.set(
                    (float) (job.blastWorld.x - camPos.x),
                    (float) (job.blastWorld.y - camPos.y),
                    (float) (job.blastWorld.z - camPos.z)
            );
            SCRATCH_VIEW_ROT.transformPosition(SCRATCH_REL_POS, SCRATCH_EYE_BLAST);

            SCRATCH_REL_POS.set(
                    (float) (job.skyApexWorld.x - camPos.x),
                    (float) (job.skyApexWorld.y - camPos.y),
                    (float) (job.skyApexWorld.z - camPos.z)
            );
            SCRATCH_VIEW_ROT.transformPosition(SCRATCH_REL_POS, SCRATCH_EYE_APEX);

            // Project elevated blast core to 2D screen UV for god rays & radial impact rays
            Vec3 coreWorld = job.blastWorld.add(0.0, job.visualRadius * 0.38, 0.0);
            SCRATCH_REL_POS.set(
                    (float) (coreWorld.x - camPos.x),
                    (float) (coreWorld.y - camPos.y),
                    (float) (coreWorld.z - camPos.z)
            );
            SCRATCH_VIEW_ROT.transformPosition(SCRATCH_REL_POS);
            SCRATCH_CLIP.set(SCRATCH_REL_POS.x, SCRATCH_REL_POS.y, SCRATCH_REL_POS.z, 1.0f);
            projection.transform(SCRATCH_CLIP);

            float screenU = 0.5f;
            float screenV = 0.5f;
            float inFrontWeight = 0.0f;
            if (SCRATCH_REL_POS.z < -0.25f && SCRATCH_CLIP.w > 1.0e-4f) {
                float ndcX = SCRATCH_CLIP.x / SCRATCH_CLIP.w;
                float ndcY = SCRATCH_CLIP.y / SCRATCH_CLIP.w;
                screenU = ndcX * 0.5f + 0.5f;
                screenV = ndcY * 0.5f + 0.5f;
                float edgeDist = Math.max(Math.abs(ndcX), Math.abs(ndcY));
                inFrontWeight = Mth.clamp(1.0f - Math.max(0.0f, (edgeDist - 0.95f) / 0.85f), 0.0f, 1.0f);
            }

            float bloomScale = ZoltraakCinematicClientConfig.getExplosionGlow();
            float flashStrength = ZoltraakCinematicClientConfig.isScreenFlashEnabled()
                    ? ZoltraakCinematicClientConfig.getFlashStrength()
                    : 0.0f;
            float worldGlowFlag = !"OFF".equals(ZoltraakCinematicClientConfig.getWorldGlow()) ? 1.0f : 0.0f;
            float caFlag = ZoltraakCinematicClientConfig.isColorFringeEnabled() ? 1.0f : 0.0f;
            float impactFlag = ZoltraakCinematicClientConfig.isScreenFlashEnabled() ? 1.0f : 0.0f;

            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            postTarget.bindWrite(true);
            postShader.setSampler("SceneSampler", sceneCopy.getColorTextureId());
            postShader.setSampler("DepthSampler", depthSamplerId);
            postShader.safeGetUniform("InverseProjectionMat").set(SCRATCH_INV_PROJ);
            postShader.safeGetUniform("BlastOrigin").set(SCRATCH_EYE_BLAST.x, SCRATCH_EYE_BLAST.y, SCRATCH_EYE_BLAST.z, job.visualRadius);
            postShader.safeGetUniform("SkyApex").set(SCRATCH_EYE_APEX.x, SCRATCH_EYE_APEX.y, SCRATCH_EYE_APEX.z, job.chargeIntensity);
            postShader.safeGetUniform("BlastTiming").set(job.normTime, job.dt, job.blastProgress, bloomScale);
            postShader.safeGetUniform("BlastScreen").set(screenU, screenV, inFrontWeight, flashStrength);
            postShader.safeGetUniform("BlastFlags").set(worldGlowFlag, caFlag, impactFlag, distFactor);

            RenderSystem.setShader(() -> postShader);
            GargantuaPostProcessor.drawScreenQuad(-1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f);

            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, postTarget.frameBufferId);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, activeDrawFbo);
            GlStateManager._glBlitFrameBuffer(
                    0, 0, postTarget.width, postTarget.height,
                    0, 0, main.width, main.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST
            );
        } catch (Throwable t) {
            if (!loggedErrorOnce) {
                loggedErrorOnce = true;
                System.err.println("[ZoltraakCinematic] Explosion post-processing error: " + t.getMessage());
                t.printStackTrace();
            }
        } finally {
            if (depthCompareSavedTex > 0) {
                GlStateManager._activeTexture(GL13.GL_TEXTURE0);
                int prevTex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthCompareSavedTex);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, depthCompareSavedMode);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex0);
            }
            GlStateManager._activeTexture(prevActiveTex != 0 ? prevActiveTex : GL13.GL_TEXTURE0);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
            GlStateManager._viewport(0, 0, main.viewWidth, main.viewHeight);
            if (prevScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (prevStencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
            if (prevDepthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (prevCull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (prevBlend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(prevDepthMask);
            RenderSystem.setShaderColor(prevShaderColor[0], prevShaderColor[1], prevShaderColor[2], prevShaderColor[3]);
            RenderSystem.setShader(() -> prevShader);
            RenderSystem.defaultBlendFunc();
        }
    }

    private static PostJob resolvePrimaryJob(Minecraft mc, Vec3 camPos, float partial, float domeScale) {
        List<ExplosionMagicEntity> active = ExplosionMagicRenderer.getActiveClientEntities(mc, partial);
        ExplosionMagicEntity best = null;
        double bestScore = -1.0;

        for (ExplosionMagicEntity exp : active) {
            if (exp == null || exp.isRemoved()) continue;
            float timeScale = exp.getTimeScale();
            float t = (exp.tickCount + partial) / timeScale;
            if (t < 0.0F || t > ExplosionMagicEntity.LIFETIME_TICKS) continue;
            Vec3 target = exp.getTargetPos();
            double dist = Math.max(4.0, camPos.distanceTo(target));
            float dt = t - ExplosionMagicEntity.DETONATE_TICK;
            double urgency = (dt >= 0.0F && dt < 24.0F) ? 3.0 : (dt < 0.0F ? 1.8 : 1.0);
            double score = urgency / dist;
            if (score > bestScore) {
                bestScore = score;
                best = exp;
            }
        }

        if (best != null) {
            float timeScale = best.getTimeScale();
            float t = (best.tickCount + partial) / timeScale;
            float dt = t - ExplosionMagicEntity.DETONATE_TICK;
            float blastProg = dt >= 0.0F ? Mth.clamp(dt / ExplosionMagicRenderer.BLAST_DURATION, 0.0F, 1.0F) : -1.0F;
            double radius = best.getRadius();
            Vec3 target = best.getTargetPos();
            Vec3 apex = target.add(0.0, ExplosionMagicRenderer.getSkyApexHeight(radius), 0.0);
            return new PostJob(target, apex, (float) (radius * domeScale), 0.0F, t, dt, blastProg);
        }

        ExplosionChargeRenderer.ChargePostAnchor charge = ExplosionChargeRenderer.getStrongestChargeAnchor(mc, partial);
        if (charge != null && charge.progress() > 0.28F) {
            Vec3 target = charge.targetWorld();
            double radius = charge.radius();
            Vec3 apex = target.add(0.0, ExplosionMagicRenderer.getSkyApexHeight(radius), 0.0);
            return new PostJob(target, apex, (float) (radius * domeScale), charge.progress(), -1.0F, -10.0F, -1.0F);
        }
        return null;
    }

    private static void ensureTargets(int width, int height) {
        if (postTarget == null || postTarget.width != width || postTarget.height != height) {
            if (postTarget != null) postTarget.destroyBuffers();
            postTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            postTarget.setFilterMode(GL11.GL_LINEAR);
        }
        if (sceneCopy == null || sceneCopy.width != width || sceneCopy.height != height) {
            if (sceneCopy != null) sceneCopy.destroyBuffers();
            sceneCopy = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            sceneCopy.setFilterMode(GL11.GL_LINEAR);
        }
    }

    public static void release() {
        hasPendingIrisPostPass = false;
        hasCapturedIrisProjection = false;
        irisRenderedThisFrame = false;
        loggedErrorOnce = false;
        if (postTarget != null) {
            postTarget.destroyBuffers();
            postTarget = null;
        }
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
            sceneCopy = null;
        }
    }
}
