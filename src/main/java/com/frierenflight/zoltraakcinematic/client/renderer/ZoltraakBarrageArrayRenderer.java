// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.client.ZoltraakCinematicClientEvents;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import com.frierenflight.zoltraakcinematic.spell.FernBarrageSpell;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.*;

/**
 * 24-circle casting array renderer for Fern's Grand Barrage (outer arc, wings, and inner cross).
 * Tilts circles toward aim target, clears the center in first-person, and adds muzzle shockwaves.
 */
public class ZoltraakBarrageArrayRenderer {

    public static class ArrayState {
        public int introTicks = 0;
        public final int[] muzzleTicks = new int[FernBarrageSpell.BARRAGE_CIRCLE_COUNT];
        public int lastSeenTick = 0;
        public int colorTheme = 0;
        public boolean wasCasting = false;
    }

    private static final Map<UUID, ArrayState> ACTIVE_ARRAYS = new HashMap<>();

    public static void onCircleFired(LivingEntity caster, int circleIndex) {
        if (caster == null || circleIndex < 0 || circleIndex >= FernBarrageSpell.BARRAGE_CIRCLE_COUNT) return;
        ArrayState state = ACTIVE_ARRAYS.computeIfAbsent(caster.getUUID(), k -> new ArrayState());
        state.muzzleTicks[circleIndex] = 4; // 4 ticks of expanding shockwave ring
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE_ARRAYS.clear();
            return;
        }

        int currentTick = mc.player != null ? mc.player.tickCount : 0;
        Iterator<Map.Entry<UUID, ArrayState>> iter = ACTIVE_ARRAYS.entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry<UUID, ArrayState> entry = iter.next();
            ArrayState state = entry.getValue();

            // Tick muzzle flash timers for all 24 circles
            for (int i = 0; i < FernBarrageSpell.BARRAGE_CIRCLE_COUNT; i++) {
                if (state.muzzleTicks[i] > 0) {
                    state.muzzleTicks[i]--;
                }
            }

            // Clean up stale arrays if not updated within 10 ticks
            if (currentTick - state.lastSeenTick > 10) {
                iter.remove();
            }
        }
    }

    private static PoseStack deferredPose;
    private static org.joml.Matrix4f deferredModelView;
    private static org.joml.Matrix4f deferredProjection;
    private static Camera deferredCamera;
    private static float deferredPartial;
    private static final List<LivingEntity> deferredEntities = new ArrayList<>();

    public static boolean hasDeferredDraw() {
        return deferredPose != null && !deferredEntities.isEmpty();
    }

    public static void clearDeferred() {
        deferredPose = null;
        deferredModelView = null;
        deferredProjection = null;
        deferredCamera = null;
        deferredEntities.clear();
    }

    public static void renderIrisPhase1(MultiBufferSource.BufferSource bufferSource, org.joml.Matrix4fStack modelView) {
        if (!hasDeferredDraw() || !ZoltraakCinematicClientConfig.isWorldGlowActive(true)) return;
        modelView.set(deferredModelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(deferredProjection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);

        float bloomScale = ZoltraakCinematicClientConfig.getBarrageGlow();
        ZoltraakRenderPass.renderIrisScaled(bufferSource, bloomScale,
                () -> renderCirclesInternal(deferredPose, bufferSource, deferredCamera, deferredPartial, deferredEntities, true));
    }

    public static void renderDeferred(MultiBufferSource.BufferSource bufferSource, org.joml.Matrix4fStack modelView) {
        if (!hasDeferredDraw()) return;
        modelView.set(deferredModelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(deferredProjection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);

        renderCirclesAndFlashes(deferredPose, bufferSource, deferredCamera, deferredPartial, deferredEntities);
    }

    public static void renderArray(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        float partialTick = (float) event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        PoseStack poseStack = event.getPoseStack();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        int gameTick = mc.player.tickCount;

        // Check local player and all nearby players
        List<LivingEntity> checkEntities = new ArrayList<>();
        checkEntities.add(mc.player);
        for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
            if (p != mc.player && p.distanceToSqr(mc.player) < 4096.0) {
                checkEntities.add(p);
            }
        }

        // Update state and particles for all entities
        boolean anyActive = false;
        for (LivingEntity living : checkEntities) {
            boolean isBarrage = false;
            int colorTheme = 0;

            if (living == mc.player) {
                if (ClientMagicData.isCasting()) {
                    String spellId = ClientMagicData.getCastingSpellId();
                    if (spellId != null && (spellId.equals("zoltraak_cinematic:zoltraak_barrage") || spellId.endsWith("zoltraak_barrage"))) {
                        isBarrage = true;
                        colorTheme = 0;
                    } else if (spellId != null && (spellId.equals("zoltraak_cinematic:corrupted_barrage") || spellId.endsWith("corrupted_barrage"))) {
                        isBarrage = true;
                        colorTheme = 1;
                    }
                }
            } else {
                SyncedSpellData synced = ClientMagicData.getSyncedSpellData(living);
                if (synced != null && synced.isCasting()) {
                    String spellId = synced.getCastingSpellId();
                    if (spellId != null && (spellId.equals("zoltraak_cinematic:zoltraak_barrage") || spellId.endsWith("zoltraak_barrage"))) {
                        isBarrage = true;
                        colorTheme = 0;
                    } else if (spellId != null && (spellId.equals("zoltraak_cinematic:corrupted_barrage") || spellId.endsWith("corrupted_barrage"))) {
                        isBarrage = true;
                        colorTheme = 1;
                    }
                }
            }

            ArrayState state = ACTIVE_ARRAYS.computeIfAbsent(living.getUUID(), k -> new ArrayState());
            state.lastSeenTick = gameTick;

            if (isBarrage) {
                state.colorTheme = colorTheme;
                state.wasCasting = true;
                if (state.introTicks < 8) state.introTicks++;
            } else {
                if (state.wasCasting) {
                    state.wasCasting = false;
                }
                state.introTicks = Math.max(0, state.introTicks - 1);
            }

            if (state.introTicks > 0) {
                anyActive = true;
            }
        }

        if (!anyActive) {
            clearDeferred();
            return;
        }

        // If a shaderpack is active, defer Phase 1 Iris glow and Phase 2 crisp overlay passes
        if (ShaderCompatibility.useFullDetailPass()) {
            if (ShaderCompatibility.isShadowPass()) return;

            deferredPose = new PoseStack();
            deferredPose.last().pose().set(poseStack.last().pose());
            deferredPose.last().normal().set(poseStack.last().normal());
            deferredModelView = new org.joml.Matrix4f(RenderSystem.getModelViewMatrix());
            deferredProjection = new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());
            deferredCamera = camera;
            deferredPartial = partialTick;
            deferredEntities.clear();
            deferredEntities.addAll(checkEntities);
            return;
        }

        // Standard pass (shaders disabled)
        renderCirclesAndFlashes(poseStack, bufferSource, camera, partialTick, checkEntities);
    }

    private static void renderCirclesAndFlashes(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                                Camera camera, float partialTick, List<LivingEntity> checkEntities) {
        if (ZoltraakCinematicClientConfig.getBarrageGlow() <= 0.004f) return;
        renderCirclesInternal(poseStack, bufferSource, camera, partialTick, checkEntities, false);
    }

    private static void renderCirclesInternal(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                              Camera camera, float partialTick, List<LivingEntity> checkEntities,
                                              boolean irisBridge) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 camPos = camera.getPosition();
        int gameTick = mc.player != null ? mc.player.tickCount : 0;
        float gameTime = gameTick + partialTick;
        RenderType irisCircleType = irisBridge ? ZoltraakRenderTypes.irisEmissiveTextured(ZoltraakRenderTypes.TEX_MAGIC_CIRCLE) : null;
        int irisPasses = irisBridge ? 2 : 1;

        for (int irisPass = 0; irisPass < irisPasses; irisPass++) {
            RenderType activeIrisType = irisBridge ? (irisPass == 0 ? irisCircleType : ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD) : null;
            VertexConsumer activeIrisVc = irisBridge ? bufferSource.getBuffer(activeIrisType) : null;

            for (LivingEntity living : checkEntities) {
                ArrayState state = ACTIVE_ARRAYS.get(living.getUUID());
                if (state == null || state.introTicks <= 0) continue;

                RenderType circleType = irisBridge ? activeIrisType
                        : (state.colorTheme == 1 ? ZoltraakRenderTypes.BLACK_CIRCLE : ZoltraakRenderTypes.ZOL_CIRCLE);
                VertexConsumer circleBuilder = irisBridge ? activeIrisVc : bufferSource.getBuffer(circleType);

                float introProgress = Math.min(1.0f, (state.introTicks + partialTick) / 8.0f);
                float scaleEase = introProgress * (2.0f - introProgress);

                Vec3 eyePos = living.getEyePosition(partialTick);
                float yRot = living.getViewYRot(partialTick);
                float xRot = living.getViewXRot(partialTick);

                Vec3 flatLook = Vec3.directionFromRotation(0, yRot);
                Vec3 right = Vec3.directionFromRotation(0, yRot + 90);
                Vec3 worldUp = new Vec3(0, 1, 0);

                ZoltraakColorTheme theme = ZoltraakColorTheme.fromId(state.colorTheme);
                boolean isFirstPerson = (living == mc.player && mc.options.getCameraType().isFirstPerson());

                for (int i = 0; i < FernBarrageSpell.BARRAGE_CIRCLE_COUNT; i++) {
                    Vec3 local = FernBarrageSpell.BARRAGE_CIRCLE_OFFSETS[i];
                    float baseR = FernBarrageSpell.BARRAGE_CIRCLE_SCALES[i];

                    double fwd = isFirstPerson ? Math.max(1.15, local.z + 1.25) : local.z;
                    double xPos = isFirstPerson ? local.x * 1.30 : local.x;
                    double yPos = isFirstPerson ? local.y * 1.15 : local.y;

                    Vec3 circlePos = eyePos
                            .add(right.scale(xPos))
                            .add(worldUp.scale(yPos))
                            .add(flatLook.scale(fwd));

                    float aimDist = 48.0f;
                    float pitchTilt = (float) Math.toDegrees(Math.atan2(-yPos, aimDist));
                    float yawTilt = (float) Math.toDegrees(Math.atan2(xPos, aimDist));

                    poseStack.pushPose();
                    poseStack.translate(circlePos.x - camPos.x, circlePos.y - camPos.y, circlePos.z - camPos.z);
                    poseStack.mulPose(Axis.YP.rotationDegrees(-yRot + yawTilt));
                    poseStack.mulPose(Axis.XP.rotationDegrees(xRot + pitchTilt));

                    float spin = (gameTime * (15.0f + (i % 6) * 4.0f)) * (i % 2 == 0 ? 1 : -1);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(spin));

                    float currentRadius = baseR * scaleEase;
                    if (irisBridge) {
                        float a = 0.92f * scaleEase;
                        if (a > 0.015f && currentRadius > 0.01f) {
                            boolean black = state.colorTheme == 1;
                            float guard = ZoltraakRenderPass.irisFadeGuard(scaleEase);
                            float cr = (black ? 0.78f : 0.40f) * guard;
                            float cg = (black ? 0.28f : 0.94f) * guard;
                            float cb = 1.00f * guard;
                            Matrix4f mat = poseStack.last().pose();
                            if (irisPass == 0) {
                                Vec3 ru = new Vec3(currentRadius, 0.0, 0.0);
                                Vec3 rv = new Vec3(0.0, currentRadius, 0.0);
                                for (int s = 0; s < 2; s++) {
                                    ZoltraakRenderPass.emitTexturedDisc(circleBuilder, mat, Vec3.ZERO, ru, rv, cr, cg, cb, a);
                                }
                            } else {
                                Vec3 uUnit = new Vec3(1.0, 0.0, 0.0);
                                Vec3 vUnit = new Vec3(0.0, 1.0, 0.0);
                                float haloR = (black ? 0.62f : 0.22f) * guard;
                                float haloG = (black ? 0.16f : 0.78f) * guard;
                                ZoltraakRenderPass.emitFeatheredRing(
                                        circleBuilder, mat, Vec3.ZERO, uUnit, vUnit,
                                        currentRadius * 0.58, currentRadius * 1.02, currentRadius * 1.48, 22,
                                        haloR, haloG, cb, 0.56f * scaleEase
                                );
                                ZoltraakRenderPass.emitFeatheredStarBurst(
                                        circleBuilder, mat, Vec3.ZERO, uUnit, vUnit, currentRadius * 1.25,
                                        cr, cg, cb, 0.34f * scaleEase
                                );
                                if (state.muzzleTicks[i] > 0) {
                                    float muzzleFrac = Mth.clamp((state.muzzleTicks[i] - partialTick) / 4.0f, 0.0f, 1.0f);
                                    float shockR = currentRadius * (1.05f + (1.0f - muzzleFrac) * 0.85f);
                                    ZoltraakRenderPass.emitFeatheredStarBurst(
                                            circleBuilder, mat, Vec3.ZERO, uUnit, vUnit, shockR,
                                            cr, cg, cb, 0.78f * muzzleFrac * scaleEase
                                    );
                                }
                            }
                        }
                    } else {
                        int alpha = (int) (140 * scaleEase);
                        int formationR = (int) (Mth.clamp(introProgress, 0.0f, 1.0f) * 255.0f);
                        renderDisc(poseStack, circleBuilder, currentRadius, alpha, formationR, theme.ringG, theme.ringB);
                    }
                    poseStack.popPose();
                }
            }
            if (irisBridge) {
                bufferSource.endBatch(activeIrisType);
            } else {
                float bloomScale = ZoltraakCinematicClientConfig.getBarrageGlow();
                ZoltraakRenderPass.endBatchWithBloom(bufferSource, ZoltraakRenderTypes.ZOL_CIRCLE, bloomScale);
                ZoltraakRenderPass.endBatchWithBloom(bufferSource, ZoltraakRenderTypes.BLACK_CIRCLE, bloomScale);

                boolean anyMuzzle = false;
                VertexConsumer fxBuilder = null;
                for (LivingEntity living : checkEntities) {
                    ArrayState state = ACTIVE_ARRAYS.get(living.getUUID());
                    if (state == null || state.introTicks <= 0) continue;

                    float introProgress = Math.min(1.0f, (state.introTicks + partialTick) / 8.0f);
                    float scaleEase = introProgress * (2.0f - introProgress);
                    Vec3 eyePos = living.getEyePosition(partialTick);
                    float yRot = living.getViewYRot(partialTick);
                    float xRot = living.getViewXRot(partialTick);
                    Vec3 flatLook = Vec3.directionFromRotation(0, yRot);
                    Vec3 right = Vec3.directionFromRotation(0, yRot + 90);
                    Vec3 worldUp = new Vec3(0, 1, 0);
                    ZoltraakColorTheme theme = ZoltraakColorTheme.fromId(state.colorTheme);
                    boolean isFirstPerson = (living == mc.player && mc.options.getCameraType().isFirstPerson());

                    for (int i = 0; i < FernBarrageSpell.BARRAGE_CIRCLE_COUNT; i++) {
                        if (state.muzzleTicks[i] <= 0) continue;
                        float muzzleFrac = Mth.clamp((state.muzzleTicks[i] - partialTick) / 4.0f, 0.0f, 1.0f);
                        if (muzzleFrac <= 0.01f) continue;

                        if (fxBuilder == null) {
                            fxBuilder = bufferSource.getBuffer(ZoltraakRenderTypes.BARRAGE_ATLAS);
                            anyMuzzle = true;
                        }

                        Vec3 local = FernBarrageSpell.BARRAGE_CIRCLE_OFFSETS[i];
                        float baseR = FernBarrageSpell.BARRAGE_CIRCLE_SCALES[i];
                        double fwd = isFirstPerson ? Math.max(1.15, local.z + 1.25) : local.z;
                        double xPos = isFirstPerson ? local.x * 1.30 : local.x;
                        double yPos = isFirstPerson ? local.y * 1.15 : local.y;

                        Vec3 circlePos = eyePos
                                .add(right.scale(xPos))
                                .add(worldUp.scale(yPos))
                                .add(flatLook.scale(fwd));

                        float aimDist = 48.0f;
                        float pitchTilt = (float) Math.toDegrees(Math.atan2(-yPos, aimDist));
                        float yawTilt = (float) Math.toDegrees(Math.atan2(xPos, aimDist));

                        poseStack.pushPose();
                        poseStack.translate(circlePos.x - camPos.x, circlePos.y - camPos.y, circlePos.z - camPos.z);
                        poseStack.mulPose(Axis.YP.rotationDegrees(-yRot + yawTilt));
                        poseStack.mulPose(Axis.XP.rotationDegrees(xRot + pitchTilt));

                        float currentRadius = baseR * scaleEase;
                        float shockRadius = currentRadius * (0.95f + (1.0f - muzzleFrac) * 0.75f);
                        int fxAlpha = (int) (210 * muzzleFrac * scaleEase);
                        renderDisc(poseStack, fxBuilder, shockRadius, fxAlpha, theme.ringR, theme.ringG, theme.ringB);
                        poseStack.popPose();
                    }
                }
                if (anyMuzzle) {
                    ZoltraakRenderPass.endBatchWithBloom(bufferSource, ZoltraakRenderTypes.BARRAGE_ATLAS,
                            ZoltraakCinematicClientConfig.getBarrageGlow());
                }
            }
        }
    }

    private static void renderDisc(PoseStack poseStack, VertexConsumer builder, float radius, int alpha, int r, int g, int b) {
        Matrix4f mat = poseStack.last().pose();

        renderVertex(builder, mat, -radius, -radius, 0, 0.0f, 1.0f, r, g, b, alpha);
        renderVertex(builder, mat,  radius, -radius, 0, 1.0f, 1.0f, r, g, b, alpha);
        renderVertex(builder, mat,  radius,  radius, 0, 1.0f, 0.0f, r, g, b, alpha);
        renderVertex(builder, mat, -radius,  radius, 0, 0.0f, 0.0f, r, g, b, alpha);

        renderVertex(builder, mat, -radius,  radius, 0, 0.0f, 0.0f, r, g, b, alpha);
        renderVertex(builder, mat,  radius,  radius, 0, 1.0f, 0.0f, r, g, b, alpha);
        renderVertex(builder, mat,  radius, -radius, 0, 1.0f, 1.0f, r, g, b, alpha);
        renderVertex(builder, mat, -radius, -radius, 0, 0.0f, 1.0f, r, g, b, alpha);
    }

    private static void renderTexturedPlane(PoseStack poseStack, VertexConsumer builder, float halfWidth, float halfHeight,
                                            float u0, float v0, float u1, float v1,
                                            int r, int g, int b, int a) {
        Matrix4f mat = poseStack.last().pose();

        renderVertex(builder, mat, -halfWidth, -halfHeight, 0, u0, v1, r, g, b, a);
        renderVertex(builder, mat,  halfWidth, -halfHeight, 0, u1, v1, r, g, b, a);
        renderVertex(builder, mat,  halfWidth,  halfHeight, 0, u1, v0, r, g, b, a);
        renderVertex(builder, mat, -halfWidth,  halfHeight, 0, u0, v0, r, g, b, a);

        renderVertex(builder, mat, -halfWidth,  halfHeight, 0, u0, v0, r, g, b, a);
        renderVertex(builder, mat,  halfWidth,  halfHeight, 0, u1, v0, r, g, b, a);
        renderVertex(builder, mat,  halfWidth, -halfHeight, 0, u1, v1, r, g, b, a);
        renderVertex(builder, mat, -halfWidth, -halfHeight, 0, u0, v1, r, g, b, a);
    }

    private static void renderVertex(VertexConsumer builder, Matrix4f mat,
                                     float x, float y, float z, float u, float v,
                                     int r, int g, int b, int a) {
        builder.addVertex(mat, x, y, z)
                .setUv(u, v)
                .setColor(r, g, b, a);
    }
}
