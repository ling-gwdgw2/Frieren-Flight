// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.frierenflight.zoltraakcinematic.entity.GargantuaEntity;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-pass HDR black hole shader pipeline for active Gargantua entities.
 * Pass 1 (gargantua): Relativistic volumetric raymarching into GL_RGBA16F HDR target.
 * Pass 2 (gargantua_bloom_down): 6-level energy-preserving downsample + Gaussian pyramid.
 * Pass 3 (gargantua_bloom_up): 5-level B-spline bicubic + tent upsample pyramid.
 * Pass 4 (gargantua_composite): Analytic gravitational lensing, world glow, and filmic tonemapping.
 */
public final class GargantuaPostProcessor {
    private static final ResourceLocation NOISE_TEX =
            ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "textures/effect/noise_tile.png");

    private static final Set<GargantuaEntity> ACTIVE_CLIENT_INSTANCES =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final int BLOOM_LEVELS = 6;
    private static final float[] BLOOM_OCTAVE_WEIGHTS = new float[]{1.35f, 1.25f, 1.05f, 0.80f, 0.50f, 0.25f};

    private static TextureTarget sceneCopy;
    private static TextureTarget postTarget;

    // GL_RGBA16F floating-point HDR targets for Pass 1..3
    private static int hdrWidth = -1;
    private static int hdrHeight = -1;
    private static int hdrFbo = 0;
    private static int hdrTex = 0;
    private static final int[] downFbos = new int[BLOOM_LEVELS];
    private static final int[] downTexs = new int[BLOOM_LEVELS];
    private static final int[] upFbos = new int[BLOOM_LEVELS - 1];
    private static final int[] upTexs = new int[BLOOM_LEVELS - 1];
    private static final int[] mipWidths = new int[BLOOM_LEVELS];
    private static final int[] mipHeights = new int[BLOOM_LEVELS];

    private static int configuredNoiseTexId = -1;
    private static boolean hasPendingIrisPostPass = false;
    private static boolean hasCapturedIrisProjection = false;
    private static boolean irisRenderedThisFrame = false;
    private static boolean loggedErrorOnce = false;
    private static final Matrix4f PENDING_IRIS_PROJECTION = new Matrix4f();
    private static float pendingIrisPartial = 0.0f;

    // Reusable scratch buffers to eliminate per-frame allocations in the post pass
    private static final Matrix4f SCRATCH_INV_PROJ = new Matrix4f();
    private static final Matrix4f SCRATCH_VIEW_ROT = new Matrix4f();
    private static final Vector3f SCRATCH_REL_POS = new Vector3f();
    private static final Vector3f SCRATCH_EYE_CENTER = new Vector3f();
    private static final Vector3f SCRATCH_EYE_AXIS = new Vector3f();
    private static final Vector3f SCRATCH_EYE_DISK_U = new Vector3f();
    private static final Vector4f SCRATCH_CORNER = new Vector4f();
    private static final int[] SCRATCH_BOUNDS = new int[4];
    private static final float[] SCRATCH_CLEAR_COLOR = new float[4];

    public static void clearDeferredIrisPass() {
        hasPendingIrisPostPass = false;
        hasCapturedIrisProjection = false;
        irisRenderedThisFrame = false;
    }

    public static boolean hasPendingIrisPass() {
        return hasPendingIrisPostPass;
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

        List<GargantuaEntity> active = getActiveClientEntities(mc);
        if (active.isEmpty()) {
            return;
        }

        Matrix4f projection = hasCapturedIrisProjection
                ? new Matrix4f(PENDING_IRIS_PROJECTION)
                : new Matrix4f(RenderSystem.getProjectionMatrix());
        float partial = hasCapturedIrisProjection ? pendingIrisPartial : 0.0f;
        irisRenderedThisFrame = true;

        Matrix4f view = new Matrix4f().rotation(new org.joml.Quaternionf(camera.rotation()).conjugate());
        renderGargantua(active, projection, view, camera, partial);
    }

    public static void registerClientInstance(GargantuaEntity entity) {
        if (entity != null) {
            ACTIVE_CLIENT_INSTANCES.add(entity);
        }
    }

    public static void unregisterClientInstance(GargantuaEntity entity) {
        if (entity != null) {
            ACTIVE_CLIENT_INSTANCES.remove(entity);
        }
    }

    public static List<GargantuaEntity> getActiveClientEntities(Minecraft mc) {
        List<GargantuaEntity> active = new ArrayList<>();
        ACTIVE_CLIENT_INSTANCES.removeIf(e -> e.isRemoved() || !e.isAlive() || e.level() != mc.level);
        active.addAll(ACTIVE_CLIENT_INSTANCES);
        if (mc.level != null) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof GargantuaEntity g && g.isAlive() && !active.contains(g)) {
                    active.add(g);
                }
            }
        }
        return active;
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (ShaderCompatibility.isShadowPass()) {
            return;
        }

        // Capture world projection & partialTick inside LevelRenderer.renderLevel BEFORE GameRendererMixin
        // invokes ZoltraakRenderPass.finish() (Shift.AFTER), because Stage.AFTER_LEVEL fires after Shift.AFTER!
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            if (!ShaderCompatibility.useFullDetailPass()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                hasPendingIrisPostPass = false;
                return;
            }

            List<GargantuaEntity> active = getActiveClientEntities(mc);
            if (active.isEmpty()) {
                hasPendingIrisPostPass = false;
                return;
            }

            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
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

            List<GargantuaEntity> active = getActiveClientEntities(mc);
            if (active.isEmpty()) {
                hasPendingIrisPostPass = false;
                return;
            }

            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Matrix4f projection = event.getProjectionMatrix() != null ? event.getProjectionMatrix() : RenderSystem.getProjectionMatrix();

            if (ShaderCompatibility.useFullDetailPass() && irisRenderedThisFrame) {
                hasPendingIrisPostPass = false;
                return;
            }
            hasPendingIrisPostPass = false;
            irisRenderedThisFrame = true;

            Camera camera = event.getCamera() != null ? event.getCamera() : (mc.gameRenderer != null ? mc.gameRenderer.getMainCamera() : null);
            if (camera == null) return;

            Matrix4f view = new Matrix4f().rotation(new org.joml.Quaternionf(camera.rotation()).conjugate());
            renderGargantua(active, projection, view, camera, partial);
        }
    }

    public static void renderGargantua(List<GargantuaEntity> entities, Matrix4f projection, Matrix4f view, Camera camera, float partialTicks) {
        if (entities == null || entities.isEmpty() || projection == null || view == null || camera == null) {
            return;
        }

        ShaderInstance rayShader = ZoltraakCinematicShaders.gargantua;
        ShaderInstance downShader = ZoltraakCinematicShaders.gargantuaBloomDown;
        ShaderInstance upShader = ZoltraakCinematicShaders.gargantuaBloomUp;
        ShaderInstance compShader = ZoltraakCinematicShaders.gargantuaComposite;
        if (rayShader == null || downShader == null || upShader == null || compShader == null || !RenderSystem.isOnRenderThread()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (mc.level == null || main == null || main.width <= 0 || main.height <= 0) {
            return;
        }

        int prevReadFbo = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDrawFbo = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean prevStencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, SCRATCH_CLEAR_COLOR);
        ShaderInstance prevShader = RenderSystem.getShader();
        float[] prevShaderColor = RenderSystem.getShaderColor().clone();
        int prevActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int depthCompareSavedTex = -1;
        int depthCompareSavedMode = GL14.GL_NONE;

        try {
            ensureTargets(main.width, main.height);

            if (prevScissor) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            if (prevStencil) {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
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
            int savedTex0Binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            if (depthSamplerId > 0 && GL11.glIsTexture(depthSamplerId)) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthSamplerId);
                int cmpMode = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE);
                if (cmpMode != GL14.GL_NONE) {
                    depthCompareSavedTex = depthSamplerId;
                    depthCompareSavedMode = cmpMode;
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL14.GL_NONE);
                }
            }
            int noiseId = getNoiseTextureId(mc);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedTex0Binding);
            GlStateManager._activeTexture(prevActiveTex != 0 ? prevActiveTex : GL13.GL_TEXTURE0);

            SCRATCH_INV_PROJ.set(projection).invert();
            SCRATCH_VIEW_ROT.set(view).setTranslation(0.0f, 0.0f, 0.0f);
            Vec3 camPos = camera.getPosition();

            boolean worldGlowActive = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isWorldGlowActive(isShaderPack);
            float diskSizeCfg = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getDiskSize();
            float discScaleUnit = Math.max(0.45f, Math.min(1.80f, diskSizeCfg / 22.0f)) * 1.22f;
            float boundRadiusUnit = Math.max(7.5f, 6.2f * discScaleUnit + 1.5f);

            int renderedCount = 0;
            int blitX0 = 0;
            int blitY0 = 0;
            int blitX1 = main.width;
            int blitY1 = main.height;

            for (int i = 0; i < entities.size(); i++) {
                GargantuaEntity entity = entities.get(i);
                if (entity == null || entity.isRemoved()) continue;

                float rg = entity.gravitationalRadius(partialTicks);
                float opened = entity.opened(partialTicks);
                float brightness = entity.brightness(partialTicks);
                if (rg < 0.01f || opened <= 0.0005f || brightness <= 0.0f) continue;

                boolean isBlasting = entity.isBlasting(partialTicks);
                float blastProg = isBlasting ? entity.blastProgress(partialTicks) : 0.0f;
                float crit = entity.criticality(partialTicks);

                Vec3 center = entity.centre(partialTicks);
                SCRATCH_REL_POS.set(
                        (float) (center.x - camPos.x),
                        (float) (center.y - camPos.y),
                        (float) (center.z - camPos.z)
                );
                SCRATCH_VIEW_ROT.transformPosition(SCRATCH_REL_POS, SCRATCH_EYE_CENTER);

                // Use dynamic scissored bounds whenever full-screen worldGlow, late crit vignette, and 4-frame Impact Frame are inactive
                boolean useScissor = (entities.size() == 1)
                        && !worldGlowActive
                        && (isBlasting ? (blastProg >= 0.045f) : (crit < 0.70f));
                int px0 = 0;
                int py0 = 0;
                int px1 = main.width;
                int py1 = main.height;

                if (useScissor) {
                    float uScale = Math.max(rg * 1.65f, 0.05f);
                    float sedov = isBlasting ? (float) Math.pow(Math.max(blastProg, 1.0e-4f), 0.54) : 0.0f;
                    float shockR = 0.42f + 12.2f * sedov;
                    float activeBoundUnit = isBlasting ? Math.max(boundRadiusUnit, shockR + 4.5f) : boundRadiusUnit;
                    float outerVisualRadius = activeBoundUnit * uScale * 1.45f;
                    int status = computeProjectedBounds(
                            SCRATCH_EYE_CENTER, outerVisualRadius, projection, main.width, main.height, SCRATCH_BOUNDS
                    );
                    if (status < 0) {
                        // Completely behind or outside viewport -> skip all GPU passes cleanly
                        continue;
                    } else if (status == 1) {
                        px0 = SCRATCH_BOUNDS[0];
                        py0 = SCRATCH_BOUNDS[1];
                        px1 = SCRATCH_BOUNDS[2];
                        py1 = SCRATCH_BOUNDS[3];
                    } else {
                        useScissor = false;
                    }
                }

                if (renderedCount == 0) {
                    blitX0 = px0;
                    blitY0 = py0;
                    blitX1 = px1;
                    blitY1 = py1;

                    // Copy screen (only the active bounding rect when scissored) into sceneCopy
                    GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, activeDrawFbo);
                    int prevReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
                    if (activeDrawFbo > 0 && prevReadBuffer != GL30.GL_COLOR_ATTACHMENT0) {
                        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                    }
                    GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
                    GlStateManager._glBlitFrameBuffer(
                            blitX0, blitY0, blitX1, blitY1,
                            blitX0, blitY0, blitX1, blitY1,
                            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST
                    );
                    if (activeDrawFbo > 0 && prevReadBuffer != GL30.GL_COLOR_ATTACHMENT0) {
                        GL11.glReadBuffer(prevReadBuffer);
                    }

                    RenderSystem.disableBlend();
                    RenderSystem.disableDepthTest();
                    RenderSystem.depthMask(false);
                    RenderSystem.disableCull();
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                } else {
                    GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, postTarget.frameBufferId);
                    GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
                    GlStateManager._glBlitFrameBuffer(
                            0, 0, postTarget.width, postTarget.height,
                            0, 0, sceneCopy.width, sceneCopy.height,
                            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST
                    );
                    blitX0 = 0;
                    blitY0 = 0;
                    blitX1 = main.width;
                    blitY1 = main.height;
                    useScissor = false;
                }

                Vec3 axis = entity.spinAxis();
                SCRATCH_EYE_AXIS.set((float) axis.x, (float) axis.y, (float) axis.z);
                SCRATCH_VIEW_ROT.transformDirection(SCRATCH_EYE_AXIS).normalize();

                Vec3 worldTangent = Math.abs(axis.y) < 0.99 ? axis.cross(new Vec3(0, 1, 0)).normalize() : axis.cross(new Vec3(1, 0, 0)).normalize();
                SCRATCH_EYE_DISK_U.set((float) worldTangent.x, (float) worldTangent.y, (float) worldTangent.z);
                SCRATCH_VIEW_ROT.transformDirection(SCRATCH_EYE_DISK_U).normalize();

                boolean lensingEnabled = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isLightBendingEnabled();
                boolean chromaticEnabled = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isColorFringeEnabled();
                float lensMult = !lensingEnabled ? 0.0f : (chromaticEnabled ? 2.0f : 1.0f);
                float cinematicWeight = com.frierenflight.zoltraakcinematic.client.cinematic.CinematicRigController.isPlaying()
                        ? com.frierenflight.zoltraakcinematic.client.cinematic.CinematicRigController.rigWeight()
                        : 0.0f;

                float critAndBlast = isBlasting ? (1.005f + 0.995f * blastProg) : Math.min(0.998f, crit);
                float timeSec = ((entity.level().getGameTime() % 72000L) + partialTicks) / 20.0f;
                float swallowed = (float) Math.min(entity.getSwallowedCount(), 10);

                float holeSteps = (float) com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getHoleSteps();
                float worldGlowFlag = worldGlowActive ? 1.0f : 0.0f;
                float lensFlareFlag = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isLensFlaresEnabled() ? 1.0f : 0.0f;
                float effectiveBrightness = brightness * com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getGargantuaGlow();

                float ndcX0 = (2.0f * px0) / main.width - 1.0f;
                float ndcY0 = (2.0f * py0) / main.height - 1.0f;
                float ndcX1 = (2.0f * px1) / main.width - 1.0f;
                float ndcY1 = (2.0f * py1) / main.height - 1.0f;
                float u0 = (float) px0 / main.width;
                float v0 = (float) py0 / main.height;
                float u1 = (float) px1 / main.width;
                float v1 = (float) py1 / main.height;

                // =========================================================================
                // PASS 1: Relativistic Volumetric Raymarching -> hdrFbo (GL_RGBA16F)
                // =========================================================================
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, hdrFbo);
                GlStateManager._viewport(0, 0, main.width, main.height);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);

                if (useScissor) {
                    GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);
                    GL11.glScissor(px0, py0, px1 - px0, py1 - py0);
                }

                rayShader.setSampler("DepthSampler", depthSamplerId);
                rayShader.setSampler("NoiseSampler", noiseId);
                rayShader.safeGetUniform("HoleCentre").set(SCRATCH_EYE_CENTER.x, SCRATCH_EYE_CENTER.y, SCRATCH_EYE_CENTER.z, rg);
                rayShader.safeGetUniform("SpinAxis").set(SCRATCH_EYE_AXIS.x, SCRATCH_EYE_AXIS.y, SCRATCH_EYE_AXIS.z, lensMult);
                rayShader.safeGetUniform("DiskU").set(SCRATCH_EYE_DISK_U.x, SCRATCH_EYE_DISK_U.y, SCRATCH_EYE_DISK_U.z, cinematicWeight);
                rayShader.safeGetUniform("HoleState").set(opened, critAndBlast, timeSec, swallowed);
                rayShader.safeGetUniform("DiskShape").set(worldGlowFlag, diskSizeCfg, holeSteps, effectiveBrightness);
                rayShader.safeGetUniform("InverseProjectionMat").set(SCRATCH_INV_PROJ);

                RenderSystem.setShader(() -> rayShader);
                drawScreenQuad(ndcX0, ndcY0, ndcX1, ndcY1, u0, v0, u1, v1);

                // =========================================================================
                // PASS 2: 6-Level HDR Bloom Mip Downsample & Gaussian Blur Pyramid
                // =========================================================================
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                RenderSystem.setShader(() -> downShader);
                for (int lvl = 0; lvl < BLOOM_LEVELS; lvl++) {
                    int srcTex = (lvl == 0) ? hdrTex : downTexs[lvl - 1];
                    int srcW = (lvl == 0) ? main.width : mipWidths[lvl - 1];
                    int srcH = (lvl == 0) ? main.height : mipHeights[lvl - 1];

                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, downFbos[lvl]);
                    GlStateManager._viewport(0, 0, mipWidths[lvl], mipHeights[lvl]);
                    downShader.setSampler("SourceSampler", srcTex);
                    downShader.safeGetUniform("BloomTexel").set(1.0f / srcW, 1.0f / srcH, (float) lvl, 1.0f + lvl * 0.15f);
                    drawScreenQuad(-1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f);
                }

                // =========================================================================
                // PASS 3: 5-Level Bicubic B-Spline + Tent Filter Bloom Upsample Pyramid
                // =========================================================================
                RenderSystem.setShader(() -> upShader);
                for (int lvl = BLOOM_LEVELS - 2; lvl >= 0; lvl--) {
                    int lowTex = (lvl == BLOOM_LEVELS - 2) ? downTexs[lvl + 1] : upTexs[lvl + 1];
                    float highW = BLOOM_OCTAVE_WEIGHTS[lvl];
                    float lowW = (lvl == BLOOM_LEVELS - 2) ? BLOOM_OCTAVE_WEIGHTS[lvl + 1] : 1.0f;
                    int lowWRes = mipWidths[lvl + 1];
                    int lowHRes = mipHeights[lvl + 1];

                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, upFbos[lvl]);
                    GlStateManager._viewport(0, 0, mipWidths[lvl], mipHeights[lvl]);
                    upShader.setSampler("HighMipSampler", downTexs[lvl]);
                    upShader.setSampler("LowMipSampler", lowTex);
                    upShader.safeGetUniform("UpsampleParams").set(1.0f / lowWRes, 1.0f / lowHRes, highW, lowW);
                    drawScreenQuad(-1.0f, -1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f);
                }

                // =========================================================================
                // PASS 4: Analytic Lensing, World Glow & Filmic Composite
                // =========================================================================
                postTarget.bindWrite(true);
                if (useScissor) {
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);
                    GL11.glScissor(px0, py0, px1 - px0, py1 - py0);
                } else {
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                }

                compShader.setSampler("SceneSampler", sceneCopy.getColorTextureId());
                compShader.setSampler("DepthSampler", depthSamplerId);
                compShader.setSampler("HdrSampler", hdrTex);
                compShader.setSampler("BloomSampler", upTexs[0]);

                compShader.safeGetUniform("HoleCentre").set(SCRATCH_EYE_CENTER.x, SCRATCH_EYE_CENTER.y, SCRATCH_EYE_CENTER.z, rg);
                compShader.safeGetUniform("SpinAxis").set(SCRATCH_EYE_AXIS.x, SCRATCH_EYE_AXIS.y, SCRATCH_EYE_AXIS.z, lensMult);
                compShader.safeGetUniform("DiskU").set(SCRATCH_EYE_DISK_U.x, SCRATCH_EYE_DISK_U.y, SCRATCH_EYE_DISK_U.z, cinematicWeight);
                compShader.safeGetUniform("HoleState").set(opened, critAndBlast, timeSec, swallowed);
                compShader.safeGetUniform("DiskShape").set(worldGlowFlag, diskSizeCfg, lensFlareFlag, effectiveBrightness);
                compShader.safeGetUniform("InverseProjectionMat").set(SCRATCH_INV_PROJ);

                RenderSystem.setShader(() -> compShader);
                drawScreenQuad(ndcX0, ndcY0, ndcX1, ndcY1, u0, v0, u1, v1);
                renderedCount++;
            }

            if (renderedCount > 0) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, postTarget.frameBufferId);
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, activeDrawFbo);
                GlStateManager._glBlitFrameBuffer(
                        blitX0, blitY0, blitX1, blitY1,
                        blitX0, blitY0, blitX1, blitY1,
                        GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST
                );
            }

        } catch (Throwable t) {
            if (!loggedErrorOnce) {
                loggedErrorOnce = true;
                System.err.println("[ZoltraakCinematic] Gargantua post-processing error: " + t.getMessage());
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
            GL11.glClearColor(SCRATCH_CLEAR_COLOR[0], SCRATCH_CLEAR_COLOR[1], SCRATCH_CLEAR_COLOR[2], SCRATCH_CLEAR_COLOR[3]);
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

        if (hdrFbo == 0 || hdrWidth != width || hdrHeight != height) {
            destroyHdrBuffers();
            hdrWidth = width;
            hdrHeight = height;

            int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int prevFbo = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

            hdrTex = GlStateManager._genTexture();
            hdrFbo = GL30.glGenFramebuffers();
            initRgba16fTarget(hdrFbo, hdrTex, width, height);

            for (int lvl = 0; lvl < BLOOM_LEVELS; lvl++) {
                int mw = Math.max(1, width >> (lvl + 1));
                int mh = Math.max(1, height >> (lvl + 1));
                mipWidths[lvl] = mw;
                mipHeights[lvl] = mh;

                downTexs[lvl] = GlStateManager._genTexture();
                downFbos[lvl] = GL30.glGenFramebuffers();
                initRgba16fTarget(downFbos[lvl], downTexs[lvl], mw, mh);

                if (lvl < BLOOM_LEVELS - 1) {
                    upTexs[lvl] = GlStateManager._genTexture();
                    upFbos[lvl] = GL30.glGenFramebuffers();
                    initRgba16fTarget(upFbos[lvl], upTexs[lvl], mw, mh);
                }
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        }
    }

    private static void initRgba16fTarget(int fbo, int tex, int w, int h) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, w, h, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
    }

    private static void destroyHdrBuffers() {
        if (hdrFbo > 0) {
            GL30.glDeleteFramebuffers(hdrFbo);
            hdrFbo = 0;
        }
        if (hdrTex > 0) {
            GlStateManager._deleteTexture(hdrTex);
            hdrTex = 0;
        }
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            if (downFbos[i] > 0) {
                GL30.glDeleteFramebuffers(downFbos[i]);
                downFbos[i] = 0;
            }
            if (downTexs[i] > 0) {
                GlStateManager._deleteTexture(downTexs[i]);
                downTexs[i] = 0;
            }
            if (i < BLOOM_LEVELS - 1) {
                if (upFbos[i] > 0) {
                    GL30.glDeleteFramebuffers(upFbos[i]);
                    upFbos[i] = 0;
                }
                if (upTexs[i] > 0) {
                    GlStateManager._deleteTexture(upTexs[i]);
                    upTexs[i] = 0;
                }
            }
        }
        hdrWidth = -1;
        hdrHeight = -1;
    }

    /**
     * Projects the bounding cube of (eyeCenter, radius) into screen pixel bounds [x0, y0, x1, y1].
     * Returns -1 if completely outside view frustum, 0 if clipping near plane (use fullscreen), or 1 if bounded.
     */
    private static int computeProjectedBounds(
            Vector3f eyeCenter, float radius, Matrix4f projection, int width, int height, int[] outBounds
    ) {
        if (eyeCenter.z - radius >= -0.25f) {
            return -1;
        }
        if (eyeCenter.z + radius >= -0.25f) {
            return 0;
        }

        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (int sx = -1; sx <= 1; sx += 2) {
            float cx = eyeCenter.x + sx * radius;
            for (int sy = -1; sy <= 1; sy += 2) {
                float cy = eyeCenter.y + sy * radius;
                for (int sz = -1; sz <= 1; sz += 2) {
                    SCRATCH_CORNER.set(cx, cy, eyeCenter.z + sz * radius, 1.0f);
                    projection.transform(SCRATCH_CORNER);
                    if (SCRATCH_CORNER.w <= 1.0e-4f) {
                        return 0;
                    }
                    float nx = SCRATCH_CORNER.x / SCRATCH_CORNER.w;
                    float ny = SCRATCH_CORNER.y / SCRATCH_CORNER.w;
                    if (nx < minX) minX = nx;
                    if (nx > maxX) maxX = nx;
                    if (ny < minY) minY = ny;
                    if (ny > maxY) maxY = ny;
                }
            }
        }

        if (maxX < -1.0f || minX > 1.0f || maxY < -1.0f || minY > 1.0f) {
            return -1;
        }

        int px0 = Mth.clamp((int) Math.floor(((minX + 1.0f) * 0.5f) * width) - 24, 0, width);
        int py0 = Mth.clamp((int) Math.floor(((minY + 1.0f) * 0.5f) * height) - 24, 0, height);
        int px1 = Mth.clamp((int) Math.ceil(((maxX + 1.0f) * 0.5f) * width) + 24, 0, width);
        int py1 = Mth.clamp((int) Math.ceil(((maxY + 1.0f) * 0.5f) * height) + 24, 0, height);
        if (px1 <= px0 || py1 <= py0) {
            return -1;
        }

        outBounds[0] = px0;
        outBounds[1] = py0;
        outBounds[2] = px1;
        outBounds[3] = py1;
        return 1;
    }

    private static int getNoiseTextureId(Minecraft mc) {
        AbstractTexture tex = mc.getTextureManager().getTexture(NOISE_TEX);
        if (tex == null) {
            mc.getTextureManager().bindForSetup(NOISE_TEX);
            tex = mc.getTextureManager().getTexture(NOISE_TEX);
        }
        if (tex != null) {
            int texId = tex.getId();
            if (texId != configuredNoiseTexId) {
                tex.bind();
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                configuredNoiseTexId = texId;
            }
            return texId;
        }
        return 0;
    }

    static void drawScreenQuad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(x0, y0, 0.0f).setUv(u0, v0);
        builder.addVertex(x1, y0, 0.0f).setUv(u1, v0);
        builder.addVertex(x1, y1, 0.0f).setUv(u1, v1);
        builder.addVertex(x0, y1, 0.0f).setUv(u0, v1);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    public static void release() {
        hasPendingIrisPostPass = false;
        hasCapturedIrisProjection = false;
        irisRenderedThisFrame = false;
        loggedErrorOnce = false;
        configuredNoiseTexId = -1;
        ACTIVE_CLIENT_INSTANCES.clear();
        destroyHdrBuffers();
        if (postTarget != null) {
            postTarget.destroyBuffers();
            postTarget = null;
        }
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
            sceneCopy = null;
        }
    }

    private GargantuaPostProcessor() {}
}
