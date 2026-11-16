// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import com.frierenflight.zoltraakcinematic.entity.ZoltraakBarrageProjectileEntity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class ZoltraakRenderPass {
   public static final ResourceLocation PROXY_TEX = ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "textures/spell/zoltraak_glow.png");
   private static final Map<Integer, ZoltraakRenderPass.Draw> DRAWS = new LinkedHashMap<>();
   private static final BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
   private static boolean replaying;
   private static float activeIrisBloomScale = 1.0F;

   public static boolean isReplaying() {
      return replaying;
   }

   public static float getSpellBloomScale(IZoltraakVisualEntity visual) {
      if (visual == null) {
         return 1.0F;
      }
      return switch (visual.mode()) {
         case LARGE -> ZoltraakCinematicClientConfig.getGreatZoltraakGlow();
         case RAPID -> ZoltraakCinematicClientConfig.getBarrageGlow();
         default -> ZoltraakCinematicClientConfig.getZoltraakGlow();
      };
   }

   public static float[] beginBloomColorScale(float scale) {
      return beginBloomColorScale(scale, 0.0F);
   }

   public static float[] beginBloomColorScale(float scale, float minAlphaFloor) {
      float[] prev = RenderSystem.getShaderColor().clone();
      float clamped = Mth.clamp(scale, 0.0F, 2.0F);
      if (Math.abs(clamped - 1.0F) > 0.002F) {
         float rgbMod = clamped > 1.0F ? clamped : 1.0F;
         float alphaMod = clamped >= 1.0F ? 1.0F : Mth.clamp(Mth.lerp(clamped, minAlphaFloor, 1.0F), 0.0F, 1.0F);
         RenderSystem.setShaderColor(prev[0] * rgbMod, prev[1] * rgbMod, prev[2] * rgbMod, prev[3] * alphaMod);
      }
      return prev;
   }

   public static void restoreShaderColor(float[] prev) {
      if (prev != null && prev.length >= 4) {
         RenderSystem.setShaderColor(prev[0], prev[1], prev[2], prev[3]);
      }
   }

   public static void endBatchWithBloom(MultiBufferSource buffers, RenderType type, float bloomScale) {
      endBatchWithBloom(buffers, type, bloomScale, 0.0F);
   }

   public static void endBatchWithBloom(MultiBufferSource buffers, RenderType type, float bloomScale, float minAlphaFloor) {
      if (buffers instanceof BufferSource bufferSource) {
         float[] prev = beginBloomColorScale(bloomScale, minAlphaFloor);
         try {
            bufferSource.endBatch(type);
         } finally {
            restoreShaderColor(prev);
         }
      }
   }

   public static void renderIrisScaled(BufferSource buffers, float bloomScale, Runnable emitCall) {
      float clamped = Mth.clamp(bloomScale, 0.0F, 2.0F);
      if (clamped <= 0.004F) {
         return;
      }
      activeIrisBloomScale = Math.min(clamped, 1.0F);
      try {
         emitCall.run();
         buffers.endBatch();
         if (clamped > 1.005F) {
            // Second additive pass boosts glow brightness above 100% under shaderpacks.
            activeIrisBloomScale = clamped - 1.0F;
            emitCall.run();
            buffers.endBatch();
         }
      } finally {
         activeIrisBloomScale = 1.0F;
      }
   }

   public static void begin() {
      DRAWS.clear();
      ZoltraakBarrageArrayRenderer.clearDeferred();
      QualSealingStoneRenderer.clearDeferred();
      GargantuaPreviewRenderer.clearDeferred();
      DefenseBarrierRenderer.clearDeferred();
      QualBossRenderer.clearDeferred();
      GargantuaRenderer.clearDeferredIris();
      GargantuaPostProcessor.clearDeferredIrisPass();
   }

   @SuppressWarnings("unchecked")
   public static <T extends Entity & IZoltraakVisualEntity> boolean defer(
      EntityRenderer<T> renderer, T entity, float yaw, float partial, PoseStack pose, int light
   ) {
      if (replaying || !ShaderCompatibility.useFullDetailPass()) {
         return false;
      }

      if (ShaderCompatibility.isShadowPass()) {
         return true;
      }

      // Record draw transforms; Iris glow pass runs at AFTER_WEATHER after sky and water.
      PoseStack copy = new PoseStack();
      copy.last().pose().set(pose.last().pose());
      copy.last().normal().set(pose.last().normal());
      DRAWS.put(
         entity.getId(),
         new ZoltraakRenderPass.Draw(
            (EntityRenderer)renderer, entity, yaw, partial, copy, new Matrix4f(RenderSystem.getModelViewMatrix()), new Matrix4f(RenderSystem.getProjectionMatrix()), light
         )
      );
      return true;
   }

   public static void onRenderLevelStage(RenderLevelStageEvent event) {
      if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
         return;
      }
      if (!ShaderCompatibility.useFullDetailPass()
         || ShaderCompatibility.isShadowPass()
         || !ZoltraakCinematicClientConfig.isWorldGlowActive(true)) {
         return;
      }

      boolean hasBarrage = ZoltraakBarrageArrayRenderer.hasDeferredDraw();
      boolean hasSealing = QualSealingStoneRenderer.hasDeferredDraw();
      boolean hasPreview = GargantuaPreviewRenderer.hasDeferredDraw();
      boolean hasBarrier = DefenseBarrierRenderer.hasDeferredDraw();
      boolean hasQual = QualBossRenderer.hasDeferredDraw();
      boolean hasGargantua = GargantuaRenderer.hasDeferredIrisDraw();
      if (DRAWS.isEmpty() && !hasBarrage && !hasSealing && !hasPreview && !hasBarrier && !hasQual && !hasGargantua) {
         return;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }

      ShaderInstance previousShader = RenderSystem.getShader();
      float[] color = (float[])RenderSystem.getShaderColor().clone();
      RenderSystem.backupProjectionMatrix();
      Matrix4fStack modelView = RenderSystem.getModelViewStack();
      modelView.pushMatrix();

      int prevReadFbo = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
      int prevDrawFbo = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
      boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
      boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

      try {
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(false);
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

         for (ZoltraakRenderPass.Draw draw : DRAWS.values()) {
            if (!draw.entity.isRemoved()) {
               modelView.set(draw.modelView);
               RenderSystem.applyModelViewMatrix();
               RenderSystem.setProjectionMatrix(draw.projection, VertexSorting.DISTANCE_TO_ORIGIN);
               float bloomScale = draw.entity instanceof IZoltraakVisualEntity visual
                  ? getSpellBloomScale(visual)
                  : 1.0F;
               renderIrisScaled(BUFFERS, bloomScale, () -> emitIrisEmissiveProxy(draw.entity, draw.partial, draw.pose, BUFFERS));
            }
         }

         if (hasBarrage) {
            ZoltraakBarrageArrayRenderer.renderIrisPhase1(BUFFERS, modelView);
            BUFFERS.endBatch();
         }
         if (hasSealing) {
            QualSealingStoneRenderer.renderIrisPhase1(BUFFERS, modelView);
            BUFFERS.endBatch();
         }
         if (hasPreview) {
            GargantuaPreviewRenderer.renderIrisPhase1(BUFFERS, modelView);
            BUFFERS.endBatch();
         }
         if (hasBarrier) {
            DefenseBarrierRenderer.renderIrisPhase1(BUFFERS, modelView);
            BUFFERS.endBatch();
         }
         if (hasQual) {
            QualBossRenderer.renderIrisPhase1(BUFFERS, modelView);
            BUFFERS.endBatch();
         }
         if (hasGargantua) {
            GargantuaRenderer.renderIrisPhase1(BUFFERS, modelView);
            BUFFERS.endBatch();
         }
      } finally {
         GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
         GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
         modelView.popMatrix();
         RenderSystem.applyModelViewMatrix();
         RenderSystem.restoreProjectionMatrix();
         RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
         RenderSystem.setShader(() -> previousShader);
         if (prevDepthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
         RenderSystem.depthMask(prevDepthMask);
         RenderSystem.enableCull();
         RenderSystem.disableBlend();
         RenderSystem.defaultBlendFunc();
      }
   }

   @SuppressWarnings("unchecked")
   private static <T extends Entity & IZoltraakVisualEntity> void emitIrisEmissiveProxy(
      Entity rawEntity, float partial, PoseStack pose, MultiBufferSource.BufferSource buffers
   ) {
      T entity = (T) rawEntity;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;
      Vec3 entPos = entity.getPosition(partial);
      Vec3 origin = entity.visualOrigin(partial);
      Vec3 dir = entity.visualDirection(partial);
      if (dir.lengthSqr() < 1.0E-6) {
         dir = new Vec3(0.0, 0.0, 1.0);
      } else {
         dir = dir.normalize();
      }

      Vec3 u = dir.cross(new Vec3(0.0, 1.0, 0.0));
      u = u.lengthSqr() < 0.01 ? new Vec3(1.0, 0.0, 0.0) : u.normalize();
      Vec3 v = u.cross(dir).normalize();

      Vec3 relOrigin = origin.subtract(entPos);
      Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
      Vec3 camVec = camPos.subtract(origin);
      Vec3 side = dir.cross(camVec);
      side = side.lengthSqr() < 1.0E-5 ? u : side.normalize();

      Vec3 camRight = new Vec3(new Vector3f(1.0F, 0.0F, 0.0F).rotate(mc.getEntityRenderDispatcher().cameraOrientation()));
      Vec3 camUp = new Vec3(new Vector3f(0.0F, 1.0F, 0.0F).rotate(mc.getEntityRenderDispatcher().cameraOrientation()));

      pose.pushPose();
      pose.translate(relOrigin.x, relOrigin.y, relOrigin.z);
      Matrix4f mat = pose.last().pose();

      try {
         if (entity.mode() == ZoltraakMode.LARGE) {
            emitLargeIris(entity, partial, mat, dir, u, v, side, camVec, camRight, camUp, origin, buffers);
         } else if (entity.mode() == ZoltraakMode.RAPID) {
            emitRapidIris(entity, partial, mat, dir, u, v, origin, camPos, camRight, camUp, buffers);
         } else {
            emitSingleOrBlackIris(entity, partial, mc, mat, dir, u, v, side, camRight, camUp, origin, buffers);
         }
         ZoltraakRenderTypes.endIrisBatch(buffers, ZoltraakRenderTypes.irisEmissiveTextured(ZoltraakRenderTypes.TEX_MAGIC_CIRCLE));
         ZoltraakRenderTypes.endIrisBatch(buffers, ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
      } finally {
         pose.popPose();
      }
   }

   private static <T extends Entity & IZoltraakVisualEntity> void emitSingleOrBlackIris(
      T entity, float partial, Minecraft mc, Matrix4f mat,
      Vec3 dir, Vec3 u, Vec3 v, Vec3 side, Vec3 camRight, Vec3 camUp,
      Vec3 origin, MultiBufferSource.BufferSource buffers
   ) {
      float t = entity.age(partial);
      if (t < 0.0F) return;

      float shot = t - 14.0F;
      boolean first = mc.player != null
         && entity.casterId() == mc.player.getId()
         && mc.getCameraEntity() == mc.player
         && mc.options.getCameraType().isFirstPerson();
      FirstPersonCircleView fpCircle = FirstPersonCircleView.resolve(entity);

      float circleAlpha = ease(t / 4.0F) * (1.0F - ease((shot - 8.0F) / 12.0F));
      double radius = 1.65 * entity.scale() * (0.2 + 0.8 * ease(t / 11.0F));
      if (first && entity.mode() == ZoltraakMode.SINGLE) {
         radius *= fpCircle.radiusScale();
      }
      Vec3 primaryCenter = dir.scale(first && entity.mode() == ZoltraakMode.SINGLE ? fpCircle.offsetForward() : 0.03);

      boolean black = entity.black();
      float cr = black ? 0.78F : 0.54F;
      float cg = black ? 0.28F : 0.84F;
      float cb = 1.00F;

      if (circleAlpha > 0.015F) {
         double rot = black ? -t * 0.045 : t * 0.045;
         Vec3 ru = u.scale(Math.cos(rot)).add(v.scale(Math.sin(rot))).scale(radius);
         Vec3 rv = v.scale(Math.cos(rot)).subtract(u.scale(Math.sin(rot))).scale(radius);
         emitIrisMagicCircleWithAura(buffers, mat, primaryCenter, ru, rv, cr, cg, cb, circleAlpha, black, 2, false, fpCircle);
      }

      float charge = ease(t / 14.0F);
      float energy = entity.fired() && shot >= 0.0F ? (1.0F - ease((shot - 7.0F) / 11.0F)) : 0.0F;
      float muzzle = shot < 0.0F ? charge * 0.55F : Math.max((float) Math.exp(-Math.max(0.0F, shot) * 0.24), energy * 0.85F);

      double head = (entity.fired() && shot >= 0.0F) ? Math.min(fpCircle.maxBeamReach(entity.length()), shot * 24.0) : 0.0;
      double beamStartOffset = fpCircle.reduceVisuals()
         ? (black ? Math.min(0.24, head * 0.35) : 0.0)
         : ((black && first) ? 0.45 : (black ? 0.10 : 0.0));
      boolean hasBeam = entity.fired() && shot >= 0.0F && head > beamStartOffset + 0.08 && energy > 0.01F;
      float irisMuzzleScale = entity.mode() == ZoltraakMode.SINGLE ? fpCircle.irisMuzzleAlphaScale() : 1.0F;
      boolean hasMuzzle = muzzle > 0.02F && irisMuzzleScale > 0.005F;
      float impactDt = t - entity.impactAge();
      boolean hasImpact = entity.fired() && entity.impact() && impactDt >= 0.0F && impactDt < 26.0F;

      if (!hasBeam && !hasMuzzle && !hasImpact) return;

      VertexConsumer quadVc = buffers.getBuffer(ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);

      if (hasMuzzle) {
         double mSizeScale = entity.mode() == ZoltraakMode.SINGLE
            ? fpCircle.irisMuzzleSizeScale()
            : (first ? 0.78 : 1.0);
         double mSize = (0.55 + charge * 0.85 + (shot >= 0.0F ? 1.35 * Math.exp(-shot * 0.22) : 0.0)) * mSizeScale * entity.scale();
         float mg = irisFadeGuard(muzzle);
         float ma = Mth.clamp(muzzle * irisMuzzleScale * (black && shot >= 0.0F ? 0.62F : 0.88F), 0.0F, 0.95F);
         if (black) {
            emitFeatheredStarBurst(quadVc, mat, primaryCenter, camRight, camUp, mSize * 1.25, 0.56F * mg, 0.14F * mg, 0.96F * mg, ma * 0.72F);
            emitFeatheredStarBurst(quadVc, mat, primaryCenter, camRight, camUp, mSize * 0.52, 0.78F * mg, 0.28F * mg, 1.00F * mg, ma * 0.78F);
         } else {
            emitFeatheredStarBurst(quadVc, mat, primaryCenter, camRight, camUp, mSize * 0.48, 0.36F * mg, 0.60F * mg, 1.00F * mg, ma * 0.32F);
            emitFeatheredStarBurst(quadVc, mat, primaryCenter, camRight, camUp, mSize * 0.28, 0.88F * mg, 0.96F * mg, 1.00F * mg, ma * 0.48F);
         }
      }

      if (hasBeam) {
         Vec3 beamStart = primaryCenter.add(dir.scale(beamStartOffset));
         Vec3 beamEnd = primaryCenter.add(dir.scale(head));
         double beamHalfWidth = (first ? 1.15 : (black ? 1.45 : 1.60)) * entity.scale();
         float eg = irisFadeGuard(energy);

         if (black) {
            // Hollow abyssal center flanked by blazing violet plasma sheaths so Complementary blooms purple around the dark beam
            float sheathOuterA = Mth.clamp(energy * 0.74F, 0.0F, 0.84F);
            float sheathInnerA = Mth.clamp(energy * 0.86F, 0.0F, 0.92F);
            float voidCoreA = Mth.clamp(energy * 0.12F, 0.0F, 0.16F);
            double outerCoreRatio = fpCircle.reduceVisuals() ? 0.36 : 0.42;
            double outerHaloRatio = fpCircle.reduceVisuals() ? 1.55 : 2.85;
            double innerCoreRatio = fpCircle.reduceVisuals() ? 0.32 : 0.38;
            double innerHaloRatio = fpCircle.reduceVisuals() ? 0.96 : 1.55;
            emitTaperedIrisBeamSegment(
               quadVc, mat, beamStart, beamEnd, side, fpCircle,
               beamHalfWidth * outerCoreRatio, beamHalfWidth * outerHaloRatio,
               0.06F * eg, 0.01F * eg, 0.14F * eg, voidCoreA,
               0.58F * eg, 0.12F * eg, 0.96F * eg, sheathOuterA
            );
            emitTaperedIrisBeamSegment(
               quadVc, mat, beamStart, beamEnd, side, fpCircle,
               beamHalfWidth * innerCoreRatio, beamHalfWidth * innerHaloRatio,
               0.08F * eg, 0.02F * eg, 0.18F * eg, voidCoreA,
               0.78F * eg, 0.24F * eg, 1.00F * eg, sheathInnerA
            );
         } else {
            emitIrisAnimeBeam(quadVc, mat, beamStart, beamEnd, side, fpCircle, beamHalfWidth, energy, eg, 0.28, 0.46, 0.72, 1.00F);
         }
      }

      if (hasImpact) {
         float fade = 1.0F - ease(impactDt / 26.0F);
         float flash = (float) Math.exp(-impactDt * 0.22) * fade;
         if (flash > 0.02F) {
            Vec3 center = entity.end().subtract(origin).add(entity.normal().scale(0.08));
            Vec3 normal = entity.normal().lengthSqr() > 1.0E-4 ? entity.normal().normalize() : new Vec3(0.0, 1.0, 0.0);
            Vec3 iu = normal.cross(new Vec3(1.0, 0.0, 0.0));
            iu = iu.lengthSqr() < 0.01 ? normal.cross(new Vec3(0.0, 0.0, 1.0)).normalize() : iu.normalize();
            Vec3 iv = iu.cross(normal).normalize();

            float fg = irisFadeGuard(flash);
            double ringR = (1.2 + impactDt * 0.28) * entity.scale();
            emitFeatheredRing(
               quadVc, mat, center, iu, iv,
               Math.max(0.15, ringR - 0.85 * entity.scale()), ringR, ringR + 1.35 * entity.scale(), 24,
               cr * fg, cg * fg, cb * fg, Mth.clamp(flash * 0.84F, 0.0F, 0.92F)
            );
            emitFeatheredStarBurst(
               quadVc, mat, center, camRight, camUp, (2.6 + impactDt * 0.14) * entity.scale(),
               cr * fg, cg * fg, cb * fg, Mth.clamp(flash * 0.80F, 0.0F, 0.90F)
            );
            emitFeatheredStarBurst(
               quadVc, mat, center, camRight, camUp, (1.3 + impactDt * 0.06) * entity.scale(),
               (black ? 0.86F : 0.80F) * fg, (black ? 0.36F : 0.98F) * fg, 1.00F * fg, Mth.clamp(flash * 0.90F, 0.0F, 0.95F)
            );
         }
      }
   }

   private static <T extends Entity & IZoltraakVisualEntity> void emitLargeIris(
      T entity, float partial, Matrix4f mat,
      Vec3 dir, Vec3 u, Vec3 v, Vec3 side, Vec3 camVec, Vec3 camRight, Vec3 camUp,
      Vec3 origin, MultiBufferSource.BufferSource buffers
   ) {
      float t = entity.rawAge(partial);
      float release = t - 20.0F;
      float fade = 1.0F - ease((t - 100.0F) / 26.0F);
      if (t < 0.0F || fade <= 0.01F) return;

      boolean black = entity.black();
      float cr = black ? 0.78F : 0.40F;
      float cg = black ? 0.28F : 0.94F;
      float cb = 1.00F;

      float sealAlpha = ease(t / 3.0F) * fade;
      if (sealAlpha > 0.015F) {
         double sealRadius = 9.3 * (0.65 + 0.35 * ease(t / 13.0F));
         double rot = t * 0.03;
         Vec3 ru = u.scale(Math.cos(rot)).add(v.scale(Math.sin(rot))).scale(sealRadius);
         Vec3 rv = v.scale(Math.cos(rot)).subtract(u.scale(Math.sin(rot))).scale(sealRadius);
         emitIrisMagicCircleWithAura(buffers, mat, dir.scale(0.05), ru, rv, cr, cg, cb, sealAlpha, black, 2, false);
      }

      double inflate = ease((t - 10.0F) / 10.0F);
      double front = entity.fired() ? entity.beamFront(partial) : 0.025;
      double tail = release > 80.0F ? (release - 80.0F) * 2.5 : 0.0;
      float bodyFade = 1.0F - ease((release - 80.0F) / 12.0F);
      float beamPower = bodyFade * fade;

      boolean hasBeam = inflate > 0.01 && front > tail && beamPower > 0.01F;
      boolean hasImpact = entity.impact() && entity.largeImpactTime() >= 0.0F && (t - entity.largeImpactTime()) >= 0.0F;
      if (!hasBeam && !hasImpact) return;

      VertexConsumer quadVc = buffers.getBuffer(ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);

      if (hasBeam) {
         Vec3 a = dir.scale(tail);
         Vec3 b = dir.scale(Math.max(tail + 0.51, front));
         double rimHalfW = 22.5 * inflate * 0.65;
         float bg = irisFadeGuard(beamPower);
         if (black) {
            float voidCoreA = Mth.clamp(beamPower * 0.14F, 0.0F, 0.18F);
            emitFeatheredBeamSegment(
               quadVc, mat, a, b, side,
               rimHalfW * 0.44, rimHalfW * 1.58,
               0.06F * bg, 0.01F * bg, 0.14F * bg, voidCoreA,
               0.58F * bg, 0.12F * bg, 0.96F * bg, Mth.clamp(beamPower * 0.74F, 0.0F, 0.84F)
            );
            emitFeatheredBeamSegment(
               quadVc, mat, a, b, side,
               rimHalfW * 0.36, rimHalfW * 0.98,
               0.08F * bg, 0.02F * bg, 0.18F * bg, voidCoreA,
               0.78F * bg, 0.24F * bg, 1.00F * bg, Mth.clamp(beamPower * 0.84F, 0.0F, 0.92F)
            );
         } else {
            emitIrisAnimeBeam(quadVc, mat, a, b, side, rimHalfW, beamPower, bg, 0.22, 0.42, 0.72, 0.96F);
         }
      }

      if (hasImpact) {
         float impactAge = t - entity.largeImpactTime();
         float flash = (float) Math.exp(-impactAge * 0.15) * fade;
         if (flash > 0.02F) {
            Vec3 center = entity.end().subtract(origin);
            Vec3 normal = entity.normal().lengthSqr() > 1.0E-4 ? entity.normal().normalize() : new Vec3(0.0, 1.0, 0.0);
            Vec3 iu = normal.cross(new Vec3(1.0, 0.0, 0.0));
            iu = iu.lengthSqr() < 0.01 ? normal.cross(new Vec3(0.0, 0.0, 1.0)).normalize() : iu.normalize();
            Vec3 iv = iu.cross(normal).normalize();

            float spread = ease(impactAge / 13.0F);
            double ringMid = 3.0 + 10.5 * spread;
            float fg = irisFadeGuard(flash);
            emitFeatheredRing(
               quadVc, mat, center.add(normal.scale(0.08)), iu, iv,
               Math.max(0.2, ringMid - 2.6), ringMid, ringMid + 5.2, 28,
               cr * fg, cg * fg, cb * fg, Mth.clamp(flash * 0.88F, 0.0F, 0.94F)
            );
            emitFeatheredStarBurst(
               quadVc, mat, center, camRight, camUp, 15.5,
               cr * fg, cg * fg, cb * fg, Mth.clamp(flash * 0.78F, 0.0F, 0.88F)
            );
            emitFeatheredStarBurst(
               quadVc, mat, center, camRight, camUp, 8.2,
               (black ? 0.92F : 0.80F) * fg, (black ? 0.56F : 0.98F) * fg, 1.00F * fg, Mth.clamp(flash * 0.92F, 0.0F, 0.96F)
            );
         }
      }
   }

   private static <T extends Entity & IZoltraakVisualEntity> void emitRapidIris(
      T entity, float partial, Matrix4f mat,
      Vec3 dir, Vec3 u, Vec3 v, Vec3 origin, Vec3 camPos, Vec3 camRight, Vec3 camUp,
      MultiBufferSource.BufferSource buffers
   ) {
      float raw = entity.rawAge(partial);
      if (raw < 0.0F) return;

      boolean black = entity.black();
      float cr = black ? 0.78F : 0.40F;
      float cg = black ? 0.28F : 0.94F;
      float cb = 1.00F;

      if (!(entity instanceof ZoltraakBarrageProjectileEntity)) {
         float t = entity.age(partial);
         float shot = t - 14.0F;
         float circleAlpha = 0.88F * ease(t / 4.0F) * (1.0F - ease((shot - 2.0F) / 8.0F));
         if (circleAlpha > 0.015F) {
            double sealRadius = 1.15 * (0.3 + 0.7 * Mth.clamp(raw / 9.0F, 0.0F, 1.0F));
            double rot = raw * 0.06;
            Vec3 ru = u.scale(Math.cos(rot)).add(v.scale(Math.sin(rot))).scale(sealRadius);
            Vec3 rv = v.scale(Math.cos(rot)).subtract(u.scale(Math.sin(rot))).scale(sealRadius);
            emitIrisMagicCircleWithAura(buffers, mat, dir.scale(0.03), ru, rv, cr, cg, cb, circleAlpha, black, 2, false);
         }
      }

      float fade = entity.guidedFade(partial);
      List<Vec3> path = entity.guidedPath(partial);
      if (fade <= 0.015F || path == null || path.size() < 2) return;

      int n = path.size();
      Vec3[] rawPoints = new Vec3[n];
      float[] cumDist = new float[n];
      for (int i = 0; i < n; i++) {
         rawPoints[i] = path.get(i).subtract(origin);
         if (i > 0) {
            cumDist[i] = cumDist[i - 1] + (float) rawPoints[i].distanceTo(rawPoints[i - 1]);
         }
      }

      float totalPathLen = cumDist[n - 1];
      if (totalPathLen <= 0.001F) return;

      float targetNeedleLen = 28.0F * (entity.scale() / 0.6F);
      if (entity.impact()) {
         float dt = Math.max(0.0F, raw - entity.impactAge());
         float collapse = Mth.clamp(1.0F - (dt / 6.0F), 0.0F, 1.0F);
         targetNeedleLen = Math.max(0.10F, targetNeedleLen * collapse);
      }

      float effectiveNeedleLen = Math.min(totalPathLen, targetNeedleLen);
      float startDist = Math.max(0.0F, totalPathLen - effectiveNeedleLen);

      List<Vec3> needlePts = new ArrayList<>();
      List<Float> needleDistances = new ArrayList<>();
      int startIdx = 0;
      while (startIdx < n - 1 && cumDist[startIdx + 1] <= startDist) {
         startIdx++;
      }
      if (startIdx < n - 1 && cumDist[startIdx + 1] > startDist) {
         float segLen = cumDist[startIdx + 1] - cumDist[startIdx];
         float frac = segLen > 0.0001F ? (startDist - cumDist[startIdx]) / segLen : 0.0F;
         needlePts.add(rawPoints[startIdx].lerp(rawPoints[startIdx + 1], frac));
         needleDistances.add(0.0F);
      } else {
         needlePts.add(rawPoints[0]);
         needleDistances.add(0.0F);
      }
      for (int i = startIdx + 1; i < n; i++) {
         needlePts.add(rawPoints[i]);
         needleDistances.add(cumDist[i] - startDist);
      }

      if (needlePts.size() < 2) return;

      Vec3[] rawArr = needlePts.toArray(new Vec3[0]);
      float[] rawDists = new float[needlePts.size()];
      for (int i = 0; i < rawArr.length; i++) rawDists[i] = needleDistances.get(i);
      float actualNeedleLen = Math.max(0.001F, rawDists[rawArr.length - 1]);

      int samples = 7;
      Vec3[] pts = new Vec3[samples];
      for (int s = 0; s < samples; s++) {
         pts[s] = samplePathAtDistance(rawArr, rawDists, (actualNeedleLen * s) / (float) (samples - 1));
      }

      Vec3 relCam = camPos.subtract(origin);
      double baseWidth = (black ? 0.22 : 0.20) * Math.sqrt(fade) * (entity.scale() / 0.6F);
      float rg = irisFadeGuard(fade);
      float coreA = Mth.clamp(fade * 0.92F, 0.0F, 0.96F);
      float haloA = Mth.clamp(fade * 0.64F, 0.0F, 0.72F);

      VertexConsumer quadVc = buffers.getBuffer(ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
      for (int i = 0; i < samples - 1; i++) {
         Vec3 segDir = pts[i + 1].subtract(pts[i]);
         if (segDir.lengthSqr() < 1.0E-6) continue;
         Vec3 segSide = segDir.cross(relCam.subtract(pts[i]));
         segSide = segSide.lengthSqr() < 1.0E-5 ? u : segSide.normalize();
         emitFeatheredBeamSegment(
            quadVc, mat, pts[i], pts[i + 1], segSide,
            baseWidth * 0.75, baseWidth * 2.80,
            (black ? 0.90F : 0.76F) * rg, (black ? 0.52F : 0.98F) * rg, 1.00F * rg, coreA,
            cr * rg, cg * rg, cb * rg, haloA
         );
      }

      if (entity.impact()) {
         float dt = Math.max(0.0F, raw - entity.impactAge());
         float flash = (float) Math.exp(-dt * 0.32) * fade;
         if (flash > 0.02F) {
            Vec3 endRel = entity.end().subtract(origin);
            float fg = irisFadeGuard(flash);
            emitFeatheredStarBurst(
               quadVc, mat, endRel, camRight, camUp, 2.15 * entity.scale(),
               cr * fg, cg * fg, cb * fg, Mth.clamp(flash * 0.88F, 0.0F, 0.95F)
            );
         }
      }
   }

   private static Vec3 samplePathAtDistance(Vec3[] points, float[] cumDist, float targetDist) {
      int n = points.length;
      if (targetDist <= cumDist[0]) return points[0];
      if (targetDist >= cumDist[n - 1]) return points[n - 1];
      for (int i = 0; i < n - 1; i++) {
         if (cumDist[i + 1] >= targetDist) {
            float seg = cumDist[i + 1] - cumDist[i];
            float f = seg > 0.0001F ? (targetDist - cumDist[i]) / seg : 0.0F;
            return points[i].lerp(points[i + 1], Mth.clamp(f, 0.0F, 1.0F));
         }
      }
      return points[n - 1];
   }

   private static float ease(double a) {
      float x = Mth.clamp((float) a, 0.0F, 1.0F);
      return x * x * (3.0F - 2.0F * x);
   }

   public static void emitTexturedDisc(VertexConsumer vc, Matrix4f mat, Vec3 center, Vec3 uAxis, Vec3 vAxis, float r, float g, float b, float a) {
      Vec3 n = uAxis.cross(vAxis);
      if (n.lengthSqr() < 1.0E-8) {
         n = new Vec3(0.0, 1.0, 0.0);
      } else {
         n = n.normalize();
      }
      float nx = (float) n.x;
      float ny = (float) n.y;
      float nz = (float) n.z;
      addProxyVertex(vc, mat, center.subtract(uAxis).subtract(vAxis), 0.0F, 1.0F, r, g, b, a, nx, ny, nz);
      addProxyVertex(vc, mat, center.add(uAxis).subtract(vAxis), 1.0F, 1.0F, r, g, b, a, nx, ny, nz);
      addProxyVertex(vc, mat, center.add(uAxis).add(vAxis), 1.0F, 0.0F, r, g, b, a, nx, ny, nz);
      addProxyVertex(vc, mat, center.subtract(uAxis).add(vAxis), 0.0F, 0.0F, r, g, b, a, nx, ny, nz);
   }

   /** Emits a smooth 2-pass anime beam proxy (soft royal-indigo tent glow + tight white-hot core) without flat rectangular cutoffs. */
   private static void emitIrisAnimeBeam(
      VertexConsumer quadVc, Matrix4f mat, Vec3 a, Vec3 b, Vec3 side,
      double halfWidth, float power, float guard,
      double coreRatio, double innerHaloRatio, double outerTentRatio, float intensityScale
   ) {
      emitIrisAnimeBeam(quadVc, mat, a, b, side, FirstPersonCircleView.INACTIVE, halfWidth, power, guard, coreRatio, innerHaloRatio, outerTentRatio, intensityScale);
   }

   private static void emitIrisAnimeBeam(
      VertexConsumer quadVc, Matrix4f mat, Vec3 a, Vec3 b, Vec3 side, FirstPersonCircleView fpCircle,
      double halfWidth, float power, float guard,
      double coreRatio, double innerHaloRatio, double outerTentRatio, float intensityScale
   ) {
      float outerTentA = Mth.clamp(power * 0.48F * intensityScale, 0.0F, 0.56F);
      float coreA = Mth.clamp(power * 0.90F * intensityScale, 0.0F, 0.96F);
      float innerHaloA = Mth.clamp(power * 0.44F * intensityScale, 0.0F, 0.52F);

      emitTaperedIrisBeamSegment(
         quadVc, mat, a, b, side, fpCircle,
         0.0, halfWidth * outerTentRatio,
         0.24F * guard, 0.38F * guard, 0.98F * guard, outerTentA,
         0.24F * guard, 0.38F * guard, 0.98F * guard, 0.0F
      );
      emitTaperedIrisBeamSegment(
         quadVc, mat, a, b, side, fpCircle,
         halfWidth * coreRatio, halfWidth * innerHaloRatio,
         0.96F * guard, 0.98F * guard, 1.00F * guard, coreA,
         0.36F * guard, 0.60F * guard, 1.00F * guard, innerHaloA
      );
   }

   /** Tapers the launch section near the first-person magic circle so Iris bloom leaves the crosshair clear while keeping full bloom downrange. */
   private static void emitTaperedIrisBeamSegment(
      VertexConsumer vc, Matrix4f mat, Vec3 a, Vec3 b, Vec3 sideUnit, FirstPersonCircleView fpCircle,
      double coreHalfWidth, double haloHalfWidth,
      float coreR, float coreG, float coreB, float coreA,
      float haloR, float haloG, float haloB, float haloA
   ) {
      if (!fpCircle.reduceVisuals()) {
         emitFeatheredBeamSegment(vc, mat, a, b, sideUnit, coreHalfWidth, haloHalfWidth, coreR, coreG, coreB, coreA, haloR, haloG, haloB, haloA);
         return;
      }
      Vec3 delta = b.subtract(a);
      double span = delta.length();
      if (span <= 0.30) {
         float s = fpCircle.irisBeamStartAlphaScale();
         double ws = fpCircle.irisBeamStartWidthScale();
         emitFeatheredBeamSegment(vc, mat, a, b, sideUnit, coreHalfWidth * ws, haloHalfWidth * ws, coreR, coreG, coreB, coreA * s, haloR, haloG, haloB, haloA * s);
         return;
      }

      double taperLen = Math.min(1.45, span * 0.45);
      Vec3 beamDir = delta.scale(1.0 / span);
      Vec3 pMid = a.add(beamDir.scale(taperLen * 0.45));
      Vec3 pFull = a.add(beamDir.scale(taperLen));

      float a0 = fpCircle.irisBeamStartAlphaScale();
      double w0 = fpCircle.irisBeamStartWidthScale();
      float a1 = 0.56F;
      double w1 = 0.78;

      emitFeatheredBeamSegment(
         vc, mat, a, pMid, sideUnit,
         coreHalfWidth * w0, haloHalfWidth * w0, coreHalfWidth * w1, haloHalfWidth * w1,
         coreR, coreG, coreB, coreA * a0, haloR, haloG, haloB, haloA * a0,
         coreR, coreG, coreB, coreA * a1, haloR, haloG, haloB, haloA * a1
      );
      emitFeatheredBeamSegment(
         vc, mat, pMid, pFull, sideUnit,
         coreHalfWidth * w1, haloHalfWidth * w1, coreHalfWidth, haloHalfWidth,
         coreR, coreG, coreB, coreA * a1, haloR, haloG, haloB, haloA * a1,
         coreR, coreG, coreB, coreA, haloR, haloG, haloB, haloA
      );
      if (span - taperLen > 0.04) {
         emitFeatheredBeamSegment(vc, mat, pFull, b, sideUnit, coreHalfWidth, haloHalfWidth, coreR, coreG, coreB, coreA, haloR, haloG, haloB, haloA);
      }
   }

   public static void emitFeatheredBeamSegment(
      VertexConsumer vc, Matrix4f mat, Vec3 a, Vec3 b, Vec3 sideUnit,
      double coreHalfWidth, double haloHalfWidth,
      float coreR, float coreG, float coreB, float coreA,
      float haloR, float haloG, float haloB, float haloA
   ) {
      emitFeatheredBeamSegment(
         vc, mat, a, b, sideUnit,
         coreHalfWidth, haloHalfWidth, coreHalfWidth, haloHalfWidth,
         coreR, coreG, coreB, coreA, haloR, haloG, haloB, haloA,
         coreR, coreG, coreB, coreA, haloR, haloG, haloB, haloA
      );
   }

   private static void emitFeatheredBeamSegment(
      VertexConsumer vc, Matrix4f mat, Vec3 a, Vec3 b, Vec3 sideUnit,
      double coreHalfWidthA, double haloHalfWidthA, double coreHalfWidthB, double haloHalfWidthB,
      float coreRA, float coreGA, float coreBA, float coreAA,
      float haloRA, float haloGA, float haloBA, float haloAA,
      float coreRB, float coreGB, float coreBB, float coreAB,
      float haloRB, float haloGB, float haloBB, float haloAB
   ) {
      Vec3 haloSideA = sideUnit.scale(haloHalfWidthA);
      Vec3 haloSideB = sideUnit.scale(haloHalfWidthB);
      if (Math.max(coreHalfWidthA, coreHalfWidthB) > 1.0E-5 && Math.min(haloHalfWidthA - coreHalfWidthA, haloHalfWidthB - coreHalfWidthB) > 1.0E-5) {
         Vec3 coreSideA = sideUnit.scale(coreHalfWidthA);
         Vec3 coreSideB = sideUnit.scale(coreHalfWidthB);
         // Left outer feather (-halo -> -core)
         addColorVertex(vc, mat, a.subtract(haloSideA), 0.0F, 0.0F, 0.0F, 0.0F);
         addColorVertex(vc, mat, a.subtract(coreSideA), haloRA, haloGA, haloBA, haloAA);
         addColorVertex(vc, mat, b.subtract(coreSideB), haloRB, haloGB, haloBB, haloAB);
         addColorVertex(vc, mat, b.subtract(haloSideB), 0.0F, 0.0F, 0.0F, 0.0F);
         // Core ribbon (-core -> +core)
         addColorVertex(vc, mat, a.subtract(coreSideA), coreRA, coreGA, coreBA, coreAA);
         addColorVertex(vc, mat, a.add(coreSideA), coreRA, coreGA, coreBA, coreAA);
         addColorVertex(vc, mat, b.add(coreSideB), coreRB, coreGB, coreBB, coreAB);
         addColorVertex(vc, mat, b.subtract(coreSideB), coreRB, coreGB, coreBB, coreAB);
         // Right outer feather (+core -> +halo)
         addColorVertex(vc, mat, a.add(coreSideA), haloRA, haloGA, haloBA, haloAA);
         addColorVertex(vc, mat, a.add(haloSideA), 0.0F, 0.0F, 0.0F, 0.0F);
         addColorVertex(vc, mat, b.add(haloSideB), 0.0F, 0.0F, 0.0F, 0.0F);
         addColorVertex(vc, mat, b.add(coreSideB), haloRB, haloGB, haloBB, haloAB);
      } else {
         // 2-strip tent fade (-halo -> spine -> +halo) for crisp hairlines/seams
         addColorVertex(vc, mat, a.subtract(haloSideA), 0.0F, 0.0F, 0.0F, 0.0F);
         addColorVertex(vc, mat, a, coreRA, coreGA, coreBA, coreAA);
         addColorVertex(vc, mat, b, coreRB, coreGB, coreBB, coreAB);
         addColorVertex(vc, mat, b.subtract(haloSideB), 0.0F, 0.0F, 0.0F, 0.0F);

         addColorVertex(vc, mat, a, coreRA, coreGA, coreBA, coreAA);
         addColorVertex(vc, mat, a.add(haloSideA), 0.0F, 0.0F, 0.0F, 0.0F);
         addColorVertex(vc, mat, b.add(haloSideB), 0.0F, 0.0F, 0.0F, 0.0F);
         addColorVertex(vc, mat, b, coreRB, coreGB, coreBB, coreAB);
      }
   }

   public static void emitFeatheredRing(
      VertexConsumer vc, Matrix4f mat, Vec3 center, Vec3 uAxis, Vec3 vAxis,
      double innerR, double midR, double outerR, int segments,
      float r, float g, float b, float a
   ) {
      boolean hasInner = midR > innerR + 1.0E-4;
      boolean hasOuter = outerR > midR + 1.0E-4;
      for (int i = 0; i < segments; i++) {
         double th1 = (i * Math.PI * 2.0) / segments;
         double th2 = ((i + 1) * Math.PI * 2.0) / segments;
         Vec3 d1 = uAxis.scale(Math.cos(th1)).add(vAxis.scale(Math.sin(th1)));
         Vec3 d2 = uAxis.scale(Math.cos(th2)).add(vAxis.scale(Math.sin(th2)));
         Vec3 m1 = center.add(d1.scale(midR));
         Vec3 m2 = center.add(d2.scale(midR));

         if (hasInner) {
            Vec3 i1 = center.add(d1.scale(innerR));
            Vec3 i2 = center.add(d2.scale(innerR));
            addColorVertex(vc, mat, i1, 0.0F, 0.0F, 0.0F, 0.0F);
            addColorVertex(vc, mat, m1, r, g, b, a);
            addColorVertex(vc, mat, m2, r, g, b, a);
            addColorVertex(vc, mat, i2, 0.0F, 0.0F, 0.0F, 0.0F);
         }
         if (hasOuter) {
            Vec3 o1 = center.add(d1.scale(outerR));
            Vec3 o2 = center.add(d2.scale(outerR));
            addColorVertex(vc, mat, m1, r, g, b, a);
            addColorVertex(vc, mat, o1, 0.0F, 0.0F, 0.0F, 0.0F);
            addColorVertex(vc, mat, o2, 0.0F, 0.0F, 0.0F, 0.0F);
            addColorVertex(vc, mat, m2, r, g, b, a);
         }
      }
   }

   public static void emitFeatheredStarBurst(
      VertexConsumer vc, Matrix4f mat, Vec3 center, Vec3 right, Vec3 up,
      double radius, float r, float g, float b, float a
   ) {
      int segments = 12;
      for (int i = 0; i < segments; i++) {
         double th1 = (i * Math.PI * 2.0) / segments;
         double th2 = ((i + 1) * Math.PI * 2.0) / segments;
         Vec3 p1 = center.add(right.scale(Math.cos(th1) * radius)).add(up.scale(Math.sin(th1) * radius));
         Vec3 p2 = center.add(right.scale(Math.cos(th2) * radius)).add(up.scale(Math.sin(th2) * radius));
         addColorVertex(vc, mat, center, r, g, b, a);
         addColorVertex(vc, mat, p1, 0.0F, 0.0F, 0.0F, 0.0F);
         addColorVertex(vc, mat, p2, 0.0F, 0.0F, 0.0F, 0.0F);
         addColorVertex(vc, mat, center, r, g, b, a);
      }
   }

   public static float irisFadeGuard(float fade) {
      return Mth.clamp(fade * 5.0F, 0.0F, 1.0F);
   }

   private static final float[] POLAR_CIRCLE_BANDS = {0.0F, 0.28F, 0.54F, 0.74F, 1.01F};

   /** First-person framing, fade, and bloom profile for Standard and Black Zoltraak (SINGLE mode). */
   public record FirstPersonCircleView(
      boolean reduceVisuals,
      boolean beamClamped,
      String mode,
      float opacity,
      double offsetForward,
      double radiusScale,
      float irisDiscAlphaScale,
      float irisHaloAlphaScale,
      float irisMuzzleAlphaScale,
      double irisMuzzleSizeScale,
      float phase2CircleAlphaScale,
      float phase2MuzzleAlphaScale,
      double phase2MuzzleSizeScale,
      float shaderCenterKeep,
      float shaderInnerCut
   ) {
      public static final FirstPersonCircleView INACTIVE = new FirstPersonCircleView(
         false, false, "FULL", 1.0F, 0.03, 1.0,
         1.0F, 1.0F, 1.0F, 1.0,
         1.0F, 1.0F, 1.0,
         1.0F, 1.0F
      );

      public static FirstPersonCircleView resolve(IZoltraakVisualEntity entity) {
         Minecraft mc = Minecraft.getInstance();
         boolean first = mc.player != null
            && mc.options != null
            && entity.casterId() == mc.player.getId()
            && mc.getCameraEntity() == mc.player
            && mc.options.getCameraType().isFirstPerson();
         if (!first || entity.mode() != ZoltraakMode.SINGLE) {
            return INACTIVE;
         }

         String viewMode = ZoltraakCinematicClientConfig.getCircleView();
         double safeLength = Math.max(0.25, entity.length());
         if ("FULL".equals(viewMode)) {
            return new FirstPersonCircleView(
               false, true, "FULL", 1.0F, Math.min(0.70, Math.max(0.15, safeLength * 0.45)), 0.75,
               1.0F, 1.0F, 1.0F, 0.78,
               1.0F, 0.75F, 0.72,
               1.0F, 1.0F
            );
         }

         float op = ZoltraakCinematicClientConfig.getCircleOpacity();
         double forward = Math.min(1.15, Math.max(0.22, safeLength * 0.50));

         return switch (viewMode) {
            case "MINIMAL" -> new FirstPersonCircleView(
               true, true, viewMode, op, forward, 0.46,
               op * 0.22F, op * 0.12F, op * 0.14F, 0.36,
               Mth.clamp(op * 0.58F, 0.0F, 1.0F), op * 0.25F, 0.40,
               0.35F, 1.0F
            );
            case "EDGE_ONLY" -> new FirstPersonCircleView(
               true, true, viewMode, op, forward, 0.55,
               op * 0.42F, op * 0.24F, 0.0F, 0.0,
               Mth.clamp(op * 1.25F, 0.0F, 1.0F), 0.0F, 0.0,
               0.0F, 0.56F
            );
            default -> new FirstPersonCircleView(
               true, true, "CENTER_FADE", op, forward, 0.50,
               op * 0.38F, op * 0.20F, op * 0.22F, 0.42,
               Mth.clamp(op * 1.15F, 0.0F, 1.0F), op * 0.40F, 0.45,
               Mth.clamp(0.10F + op * 0.25F, 0.0F, 1.0F), 1.0F
            );
         };
      }

      public double maxBeamReach(double entityLength) {
         return beamClamped ? Math.max(0.12, entityLength - offsetForward) : entityLength;
      }

      float irisBeamStartAlphaScale() {
         if (!reduceVisuals) {
            return 1.0F;
         }
         return switch (mode) {
            case "EDGE_ONLY" -> 0.10F;
            case "MINIMAL" -> 0.10F + 0.06F * opacity;
            default -> 0.12F + 0.12F * opacity;
         };
      }

      double irisBeamStartWidthScale() {
         if (!reduceVisuals) {
            return 1.0;
         }
         return switch (mode) {
            case "EDGE_ONLY" -> 0.42;
            case "MINIMAL" -> 0.46;
            default -> 0.54;
         };
      }

      float radialIrisWeight(float rNorm) {
         if (!reduceVisuals) {
            return 1.0F;
         }
         return switch (mode) {
            case "EDGE_ONLY" -> {
               float t = Mth.clamp((rNorm - 0.54F) / 0.24F, 0.0F, 1.0F);
               yield t * t * (3.0F - 2.0F * t);
            }
            case "MINIMAL" -> Mth.lerp(Mth.clamp(rNorm, 0.0F, 1.0F), 0.45F, 1.00F);
            default -> {
               float t = Mth.clamp((rNorm - 0.14F) / 0.62F, 0.0F, 1.0F);
               yield Mth.lerp(t * t * (3.0F - 2.0F * t), 0.05F, 1.00F);
            }
         };
      }
   }

   /** Emits a polar ring mesh with per-ring alpha attenuation so Iris Bloom clears the crosshair. */
   private static void emitTexturedPolarCircle(
      VertexConsumer vc, Matrix4f mat, Vec3 center, Vec3 uAxis, Vec3 vAxis,
      float r, float g, float b, float a, FirstPersonCircleView fpCircle
   ) {
      Vec3 n = uAxis.cross(vAxis);
      if (n.lengthSqr() < 1.0E-8) {
         n = new Vec3(0.0, 1.0, 0.0);
      } else {
         n = n.normalize();
      }
      float nx = (float) n.x;
      float ny = (float) n.y;
      float nz = (float) n.z;
      int segments = 28;

      for (int bi = 0; bi < POLAR_CIRCLE_BANDS.length - 1; bi++) {
         float r0 = POLAR_CIRCLE_BANDS[bi];
         float r1 = POLAR_CIRCLE_BANDS[bi + 1];
         float w0 = fpCircle.radialIrisWeight(r0);
         float w1 = fpCircle.radialIrisWeight(r1);
         if (w0 <= 0.002F && w1 <= 0.002F) {
            continue;
         }
         float cScale0 = 0.35F + 0.65F * w0;
         float cScale1 = 0.35F + 0.65F * w1;
         float a0 = a * w0;
         float a1 = a * w1;

         for (int i = 0; i < segments; i++) {
            double aAng0 = i * (Math.PI * 2.0) / segments;
            double aAng1 = (i + 1) * (Math.PI * 2.0) / segments;
            float cos0 = (float) Math.cos(aAng0);
            float sin0 = (float) Math.sin(aAng0);
            float cos1 = (float) Math.cos(aAng1);
            float sin1 = (float) Math.sin(aAng1);

            float dx00 = r0 * cos0;
            float dy00 = r0 * sin0;
            float dx10 = r1 * cos0;
            float dy10 = r1 * sin0;
            float dx11 = r1 * cos1;
            float dy11 = r1 * sin1;
            float dx01 = r0 * cos1;
            float dy01 = r0 * sin1;

            addProxyVertex(vc, mat, center.add(uAxis.scale(dx00)).add(vAxis.scale(dy00)),
               0.5F + 0.5F * dx00, 0.5F - 0.5F * dy00, r * cScale0, g * cScale0, b * cScale0, a0, nx, ny, nz);
            addProxyVertex(vc, mat, center.add(uAxis.scale(dx10)).add(vAxis.scale(dy10)),
               0.5F + 0.5F * dx10, 0.5F - 0.5F * dy10, r * cScale1, g * cScale1, b * cScale1, a1, nx, ny, nz);
            addProxyVertex(vc, mat, center.add(uAxis.scale(dx11)).add(vAxis.scale(dy11)),
               0.5F + 0.5F * dx11, 0.5F - 0.5F * dy11, r * cScale1, g * cScale1, b * cScale1, a1, nx, ny, nz);
            addProxyVertex(vc, mat, center.add(uAxis.scale(dx01)).add(vAxis.scale(dy01)),
               0.5F + 0.5F * dx01, 0.5F - 0.5F * dy01, r * cScale0, g * cScale0, b * cScale0, a0, nx, ny, nz);
         }
      }
   }

   /** Emits stacked rune discs plus a soft outer feathered ring and radial corona for HDR Iris bloom. */
   public static void emitIrisMagicCircleWithAura(
      MultiBufferSource buffers, Matrix4f mat, Vec3 center, Vec3 ru, Vec3 rv,
      float r, float g, float b, float alpha, boolean black, int discStacks, boolean flushBatches
   ) {
      emitIrisMagicCircleWithAura(buffers, mat, center, ru, rv, r, g, b, alpha, black, discStacks, flushBatches, FirstPersonCircleView.INACTIVE);
   }

   public static void emitIrisMagicCircleWithAura(
      MultiBufferSource buffers, Matrix4f mat, Vec3 center, Vec3 ru, Vec3 rv,
      float r, float g, float b, float alpha, boolean black, int discStacks, boolean flushBatches,
      FirstPersonCircleView fpCircle
   ) {
      if (alpha <= 0.015F) return;
      float guard = irisFadeGuard(alpha);
      float aDisc = Mth.clamp(alpha * 0.92F * fpCircle.irisDiscAlphaScale(), 0.0F, 0.95F);
      if (aDisc > 0.004F) {
         RenderType circleType = ZoltraakRenderTypes.irisEmissiveTextured(ZoltraakRenderTypes.TEX_MAGIC_CIRCLE);
         VertexConsumer circleVc = buffers.getBuffer(circleType);
         int effectiveStacks = fpCircle.reduceVisuals() ? 1 : discStacks;
         for (int i = 0; i < effectiveStacks; i++) {
            if (fpCircle.reduceVisuals()) {
               emitTexturedPolarCircle(circleVc, mat, center, ru, rv, r * guard, g * guard, b * guard, aDisc, fpCircle);
            } else {
               emitTexturedDisc(circleVc, mat, center, ru, rv, r * guard, g * guard, b * guard, aDisc);
            }
         }
         if (flushBatches) {
            ZoltraakRenderTypes.endIrisBatch(buffers, circleType);
         }
      }

      double rad = ru.length();
      float haloAlpha = Mth.clamp(alpha * 0.36F * fpCircle.irisHaloAlphaScale(), 0.0F, 0.44F);
      if (rad > 0.01 && haloAlpha > 0.004F) {
         Vec3 uUnit = ru.scale(1.0 / rad);
         Vec3 vUnit = rv.lengthSqr() > 1.0E-8 ? rv.normalize() : uUnit;
         float haloR = (black ? 0.62F : 0.24F) * guard;
         float haloG = (black ? 0.16F : 0.52F) * guard;
         float haloB = 1.00F * guard;
         double innerFactor = fpCircle.reduceVisuals() ? 0.82 : 0.68;
         double outerFactor = fpCircle.reduceVisuals() ? 1.16 : 1.38;
         VertexConsumer quadVc = buffers.getBuffer(ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
         emitFeatheredRing(
            quadVc, mat, center, uUnit, vUnit,
            rad * innerFactor, rad * 1.00, rad * outerFactor, 28,
            haloR, haloG, haloB, haloAlpha
         );
         if (flushBatches) {
            ZoltraakRenderTypes.endIrisBatch(buffers, ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
         }
      }
   }

   public static void addColorVertex(VertexConsumer vc, Matrix4f mat, Vec3 p, float r, float g, float b, float a) {
      float scaledA = Mth.clamp(a * activeIrisBloomScale, 0.0F, 1.0F);
      vc.addVertex(mat, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, scaledA).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
   }

   public static void addProxyVertex(VertexConsumer vc, Matrix4f mat, Vec3 p, float u, float v, float r, float g, float b, float a) {
      addProxyVertex(vc, mat, p, u, v, r, g, b, a, 0.0F, 1.0F, 0.0F);
   }

   public static void addProxyVertex(VertexConsumer vc, Matrix4f mat, Vec3 p, float u, float v, float r, float g, float b, float a, float nx, float ny, float nz) {
      float scaledA = Mth.clamp(a * activeIrisBloomScale, 0.0F, 1.0F);
      vc.addVertex(mat, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, scaledA).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(nx, ny, nz);
   }

   public static void finish() {
      // Composite Iris-deferred Gargantua AFTER Volumetric Clouds/Fog & Iris final tonemapping,
      // but BEFORE deferred spell renders so barriers/beams always layer cleanly on top of the black hole.
      GargantuaPostProcessor.renderDeferredIrisPass();

      boolean hasBarrageDeferred = ZoltraakBarrageArrayRenderer.hasDeferredDraw();
      boolean hasSealingDeferred = QualSealingStoneRenderer.hasDeferredDraw();
      boolean hasGargantuaPreviewDeferred = GargantuaPreviewRenderer.hasDeferredDraw();
      boolean hasBarrierDeferred = DefenseBarrierRenderer.hasDeferredDraw();
      boolean hasQualVfxDeferred = QualBossRenderer.hasDeferredDraw();
      if (!DRAWS.isEmpty() || hasBarrageDeferred || hasSealingDeferred || hasGargantuaPreviewDeferred || hasBarrierDeferred || hasQualVfxDeferred) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            DRAWS.clear();
            ZoltraakBarrageArrayRenderer.clearDeferred();
            QualSealingStoneRenderer.clearDeferred();
            GargantuaPreviewRenderer.clearDeferred();
            DefenseBarrierRenderer.clearDeferred();
            QualBossRenderer.clearDeferred();
            GargantuaRenderer.clearDeferredIris();
         } else {
            ShaderInstance previousShader = RenderSystem.getShader();
            float[] color = (float[])RenderSystem.getShaderColor().clone();
            RenderSystem.backupProjectionMatrix();
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();

            int prevReadFbo = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int prevDrawFbo = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean prevDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

            try {
               replaying = true;
               int targetFbo = (prevDrawFbo != 0) ? prevDrawFbo : mc.getMainRenderTarget().frameBufferId;
               GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFbo);
               RenderSystem.enableDepthTest();
               RenderSystem.depthMask(false);
               RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

               for (ZoltraakRenderPass.Draw draw : DRAWS.values()) {
                  if (!draw.entity.isRemoved()) {
                     modelView.set(draw.modelView);
                     RenderSystem.applyModelViewMatrix();
                     RenderSystem.setProjectionMatrix(draw.projection, VertexSorting.DISTANCE_TO_ORIGIN);
                     draw.renderer.render(draw.entity, draw.yaw, draw.partial, draw.pose, BUFFERS, draw.light);
                  }
               }

               if (hasBarrageDeferred) {
                  ZoltraakBarrageArrayRenderer.renderDeferred(BUFFERS, modelView);
               }

               if (hasSealingDeferred) {
                  QualSealingStoneRenderer.renderDeferred(BUFFERS, modelView);
               }

               if (hasGargantuaPreviewDeferred) {
                  GargantuaPreviewRenderer.renderDeferred(BUFFERS, modelView);
               }

               if (hasBarrierDeferred) {
                  DefenseBarrierRenderer.renderDeferred(BUFFERS, modelView);
               }

               if (hasQualVfxDeferred) {
                  QualBossRenderer.renderDeferred(BUFFERS, modelView);
               }

               BUFFERS.endBatch();
            } finally {
               replaying = false;
               DRAWS.clear();
               ZoltraakBarrageArrayRenderer.clearDeferred();
               QualSealingStoneRenderer.clearDeferred();
               GargantuaPreviewRenderer.clearDeferred();
               DefenseBarrierRenderer.clearDeferred();
               QualBossRenderer.clearDeferred();
               GargantuaRenderer.clearDeferredIris();
               GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDrawFbo);
               GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevReadFbo);
               modelView.popMatrix();
               RenderSystem.applyModelViewMatrix();
               RenderSystem.restoreProjectionMatrix();
               RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
               RenderSystem.setShader(() -> previousShader);
               if (prevDepthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
               RenderSystem.depthMask(prevDepthMask);
               RenderSystem.enableCull();
               RenderSystem.disableBlend();
               RenderSystem.defaultBlendFunc();
            }
         }
      }
   }

   private ZoltraakRenderPass() {}

   private record Draw(
      EntityRenderer renderer, Entity entity, float yaw, float partial, PoseStack pose, Matrix4f modelView, Matrix4f projection, int light
   ) {}
}
