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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fullscreen black hole shader pass for active Gargantua entities.
 * Handles bent light rays, rotating glow ring, background star distortion, and scene depth occlusion.
 */
public final class GargantuaPostProcessor {
    private static final ResourceLocation NOISE_TEX =
            ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "textures/effect/noise_tile.png");

    private static final Set<GargantuaEntity> ACTIVE_CLIENT_INSTANCES =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static TextureTarget sceneCopy;
    private static TextureTarget postTarget;
    private static boolean hasPendingIrisPostPass = false;
    private static boolean hasCapturedIrisProjection = false;
    private static boolean irisRenderedThisFrame = false;
    private static boolean loggedErrorOnce = false;
    private static final Matrix4f PENDING_IRIS_PROJECTION = new Matrix4f();
    private static float pendingIrisPartial = 0.0f;

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

            if (ShaderCompatibility.useFullDetailPass()) {
                // Normally already rendered via ZoltraakRenderPass.finish() -> renderDeferredIrisPass().
                // Fallback here only if finish() did not execute this frame.
                if (!irisRenderedThisFrame) {
                    PENDING_IRIS_PROJECTION.set(projection);
                    pendingIrisPartial = partial;
                    hasCapturedIrisProjection = true;
                    renderDeferredIrisPass();
                } else {
                    hasPendingIrisPostPass = false;
                }
                return;
            }

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

        ShaderInstance shader = ZoltraakCinematicShaders.gargantua;
        if (shader == null || !RenderSystem.isOnRenderThread()) {
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
            int activeDrawFbo = (prevDrawFbo != 0) ? prevDrawFbo : main.frameBufferId;

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
            int cosmicId = CosmicTextures.getMilkyWayTextureId();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedTex0Binding);
            GlStateManager._activeTexture(prevActiveTex != 0 ? prevActiveTex : GL13.GL_TEXTURE0);

            Matrix4f invProj = new Matrix4f(projection).invert();
            Matrix4f viewRotation = new Matrix4f(view).setTranslation(0.0f, 0.0f, 0.0f);
            Matrix4f invViewRot = new Matrix4f(viewRotation).invert();
            Vec3 camPos = camera.getPosition();

            int renderedCount = 0;

            for (int i = 0; i < entities.size(); i++) {
                GargantuaEntity entity = entities.get(i);
                if (entity == null || entity.isRemoved()) continue;

                float rg = entity.gravitationalRadius(partialTicks);
                float brightness = entity.brightness(partialTicks);
                if (rg < 0.01f || brightness <= 0.0f) continue;

                if (renderedCount == 0) {
                    // Copy initial screen into sceneCopy ONLY when there is an entity to actually render
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

                    postTarget.bindWrite(true);
                    RenderSystem.disableBlend();
                    RenderSystem.disableDepthTest();
                    RenderSystem.depthMask(false);
                    RenderSystem.disableCull();
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                } else {
                    // Ping-pong copy from postTarget back to sceneCopy for next black hole
                    GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, postTarget.frameBufferId);
                    GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
                    GlStateManager._glBlitFrameBuffer(
                            0, 0, postTarget.width, postTarget.height,
                            0, 0, sceneCopy.width, sceneCopy.height,
                            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST
                    );
                    postTarget.bindWrite(true);
                }

                Vec3 center = entity.centre(partialTicks);
                Vector3f relPos = new Vector3f(
                        (float) (center.x - camPos.x),
                        (float) (center.y - camPos.y),
                        (float) (center.z - camPos.z)
                );
                Vector3f eyeCenter = viewRotation.transformPosition(relPos, new Vector3f());

                Vec3 axis = entity.spinAxis();
                Vector3f eyeAxis = viewRotation.transformDirection(
                        new Vector3f((float) axis.x, (float) axis.y, (float) axis.z),
                        new Vector3f()
                ).normalize();

                Vec3 worldTangent = Math.abs(axis.y) < 0.99 ? axis.cross(new Vec3(0, 1, 0)).normalize() : axis.cross(new Vec3(1, 0, 0)).normalize();
                Vector3f eyeDiskU = viewRotation.transformDirection(
                        new Vector3f((float) worldTangent.x, (float) worldTangent.y, (float) worldTangent.z),
                        new Vector3f()
                ).normalize();

                shader.setSampler("SceneSampler", sceneCopy.getColorTextureId());
                shader.setSampler("DepthSampler", depthSamplerId);
                shader.setSampler("NoiseSampler", noiseId);
                shader.setSampler("CosmicSampler", cosmicId > 0 ? cosmicId : noiseId);

                boolean lensingEnabled = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isLightBendingEnabled();
                boolean chromaticEnabled = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isColorFringeEnabled();
                float lensMult = !lensingEnabled ? 0.0f : (chromaticEnabled ? 2.0f : 1.0f);
                float cinematicWeight = com.frierenflight.zoltraakcinematic.client.cinematic.CinematicRigController.isPlaying()
                        ? com.frierenflight.zoltraakcinematic.client.cinematic.CinematicRigController.rigWeight()
                        : 0.0f;
                shader.safeGetUniform("HoleCentre").set(eyeCenter.x, eyeCenter.y, eyeCenter.z, rg);
                shader.safeGetUniform("SpinAxis").set(eyeAxis.x, eyeAxis.y, eyeAxis.z, lensMult);
                shader.safeGetUniform("DiskU").set(eyeDiskU.x, eyeDiskU.y, eyeDiskU.z, cinematicWeight);

                float opened = entity.opened(partialTicks);
                float crit = entity.criticality(partialTicks);
                float blast = entity.blastFlash(partialTicks);
                float critAndBlast = (blast > 0.001f) ? (1.0f + blast) : crit;
                float timeSec = ((entity.level().getGameTime() % 72000L) + partialTicks) / 20.0f;
                float swallowed = (float) Math.min(entity.getSwallowedCount(), 10);
                shader.safeGetUniform("HoleState").set(opened, critAndBlast, timeSec, swallowed);

                float holeSteps = (float) com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getHoleSteps();
                float diskSize = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getDiskSize();
                float worldGlowFlag = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isWorldGlowActive(isShaderPack) ? 1.0f : 0.0f;
                float effectiveBrightness = brightness * com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getGargantuaGlow();
                shader.safeGetUniform("DiskShape").set(worldGlowFlag, diskSize, holeSteps, effectiveBrightness);
                shader.safeGetUniform("InverseProjectionMat").set(invProj);

                shader.safeGetUniform("InvViewRotMat").set(invViewRot);

                RenderSystem.setShader(() -> shader);
                drawFullscreenQuad();
                renderedCount++;
            }

            if (renderedCount > 0) {
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, postTarget.frameBufferId);
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, activeDrawFbo);
                GlStateManager._glBlitFrameBuffer(
                        0, 0, postTarget.width, postTarget.height,
                        0, 0, main.width, main.height,
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
    }

    private static int getNoiseTextureId(Minecraft mc) {
        AbstractTexture tex = mc.getTextureManager().getTexture(NOISE_TEX);
        if (tex == null) {
            mc.getTextureManager().bindForSetup(NOISE_TEX);
            tex = mc.getTextureManager().getTexture(NOISE_TEX);
        }
        if (tex != null) {
            tex.bind();
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            return tex.getId();
        }
        return 0;
    }

    private static void drawFullscreenQuad() {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(-1.0f, -1.0f, 0.0f).setUv(0.0f, 0.0f);
        builder.addVertex( 1.0f, -1.0f, 0.0f).setUv(1.0f, 0.0f);
        builder.addVertex( 1.0f,  1.0f, 0.0f).setUv(1.0f, 1.0f);
        builder.addVertex(-1.0f,  1.0f, 0.0f).setUv(0.0f, 1.0f);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    public static void release() {
        hasPendingIrisPostPass = false;
        hasCapturedIrisProjection = false;
        irisRenderedThisFrame = false;
        loggedErrorOnce = false;
        ACTIVE_CLIENT_INSTANCES.clear();
        if (postTarget != null) {
            postTarget.destroyBuffers();
            postTarget = null;
        }
        if (sceneCopy != null) {
            sceneCopy.destroyBuffers();
            sceneCopy = null;
        }
        CosmicTextures.clear();
    }

    private GargantuaPostProcessor() {}
}
