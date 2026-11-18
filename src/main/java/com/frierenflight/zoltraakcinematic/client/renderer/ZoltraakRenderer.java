// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;

import com.mojang.blaze3d.vertex.PoseStack;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.MultiBufferSource;

import net.minecraft.client.renderer.RenderType;

import net.minecraft.client.renderer.MultiBufferSource.BufferSource;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;

import net.minecraft.resources.ResourceLocation;

import net.minecraft.util.Mth;

import net.minecraft.world.entity.Entity;

import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

import org.joml.Vector3f;

public final class ZoltraakRenderer<T extends Entity & IZoltraakVisualEntity> extends EntityRenderer<T> {

   private final BlackZoltraakRenderer<T> blackRenderer;

   private final GreatZoltraakRenderer<T> greatRenderer;

   private final RapidZoltraakRenderer<T> rapidRenderer;

   public ZoltraakRenderer(Context c) {

      super(c);

      this.shadowRadius = 0.0F;

      this.blackRenderer = new BlackZoltraakRenderer<>(c);

      this.greatRenderer = new GreatZoltraakRenderer<>(c);

      this.rapidRenderer = new RapidZoltraakRenderer<>(c);

   }

   public ResourceLocation getTextureLocation(T e) {

      return ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "textures/spell/zoltraak_magic_circle.png");

   }

   private static float ease(float v) {

      v = Mth.clamp(v, 0.0F, 1.0F);

      return v * v * (3.0F - 2.0F * v);

   }

   private static Vec3 ring(Vec3 u, Vec3 v, double angle, double radius) {

      return u.scale(Math.cos(angle) * radius).add(v.scale(Math.sin(angle) * radius));

   }

   @Override
   public boolean shouldRender(T e, Frustum frustum, double camX, double camY, double camZ) {
      return e.shouldRender(camX, camY, camZ);
   }

   public void render(T e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {

      if (!ZoltraakRenderPass.defer(this, e, yaw, partial, pose, light)) {

         if (e.mode() == ZoltraakMode.LARGE) {

            this.greatRenderer.render(e, yaw, partial, pose, buffers, light);

         } else if (e.mode() == ZoltraakMode.RAPID) {

            this.rapidRenderer.render(e, yaw, partial, pose, buffers, light);

         } else if (e.black()) {

            this.blackRenderer.render(e, yaw, partial, pose, buffers, light);

         } else {

            if (com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getZoltraakGlow() <= 0.004F) {
               return;
            }

            float t = e.age(partial);

            float shot = t - 14.0F;

            Minecraft mc = Minecraft.getInstance();

            boolean first = mc.player != null

               && e.casterId() == mc.player.getId()

               && mc.getCameraEntity() == mc.player

               && mc.options.getCameraType().isFirstPerson();

            Vec3 origin = e.visualOrigin(partial);

            Vec3 dir = e.visualDirection(partial);

            Vec3 u = dir.cross(new Vec3(0.0, 1.0, 0.0));

            if (u.lengthSqr() < 0.01) {

               u = new Vec3(1.0, 0.0, 0.0);

            } else {

               u = u.normalize();

            }

            Vec3 v = u.cross(dir).normalize();

            Vec3 offset = origin.subtract(e.getPosition(partial));

            pose.pushPose();

            pose.translate(offset.x, offset.y, offset.z);

            Matrix4f mat = pose.last().pose();

            Vec3 camera = this.entityRenderDispatcher.camera.getPosition().subtract(origin);

            Vec3 side = dir.cross(camera);

            if (side.lengthSqr() < 1.0E-4) {

               side = u;

            } else {

               side = side.normalize();

            }

            Vec3 camRight = new Vec3(new Vector3f(1.0F, 0.0F, 0.0F).rotate(this.entityRenderDispatcher.cameraOrientation()));

            Vec3 camUp = new Vec3(new Vector3f(0.0F, 1.0F, 0.0F).rotate(this.entityRenderDispatcher.cameraOrientation()));

            float circleAlpha = ease(t / 4.0F) * (1.0F - ease((shot - 8.0F) / 12.0F));

            if (e.mode() == ZoltraakMode.RAPID) {

               circleAlpha = 0.72F * ease(t / 4.0F) * (1.0F - ease((shot - 2.0F) / 8.0F));

            }

            double radius = (e.mode() == ZoltraakMode.LARGE ? 6.2 : (e.mode() == ZoltraakMode.RAPID ? 1.15 : 1.65 * e.scale())) * (0.2 + 0.8 * ease(t / 11.0F));
            ZoltraakRenderPass.FirstPersonCircleView fpCircle = ZoltraakRenderPass.FirstPersonCircleView.resolve(e);

            if (first && e.mode() == ZoltraakMode.SINGLE) {

               radius *= fpCircle.radiusScale();

            }

            if (first && e.mode() == ZoltraakMode.RAPID) {

               radius *= 0.6;

            }

            float formation = Mth.clamp(t / 4.5F, 0.0F, 1.0F);

            Vec3 primaryCenter = dir.scale(first && e.mode() == ZoltraakMode.SINGLE ? fpCircle.offsetForward() : 0.03);

            float charge = ease(t / 14.0F);

            float energy = e.fired() && shot >= 0.0F ? (1.0F - ease((shot - 7.0F) / 11.0F)) : 0.0F;

            float muzzle = shot < 0.0F ? charge * 0.55F : Math.max((float)Math.exp(-Math.max(0.0F, shot) * 0.24), energy * 0.85F);

            double beamHead = (e.fired() && shot >= 0.0F) ? Math.min(fpCircle.maxBeamReach(e.length()), shot * 24.0) : 0.0;

            boolean hasBeam = e.fired() && shot >= 0.0F && beamHead > 0.0 && energy > 0.0F;

            if (hasBeam) {

               Vec3 a = primaryCenter;

               Vec3 b = a.add(dir.scale(beamHead));

               // Camera-Facing Volumetric Radiance Quad (rendered before ZOL_CIRCLE so additive circle never dims)

               VertexConsumer beam = buffers.getBuffer(ZoltraakRenderTypes.ZOL_BEAM);

               double beamHalfWidth = (first ? 1.15 : 1.60) * e.scale();

               beamQuad(beam, mat, a, b, side.scale(beamHalfWidth), t / 64.0F, energy, (float)beamHead, (float)beamHalfWidth);

               flush(buffers, ZoltraakRenderTypes.ZOL_BEAM);

            }

            float drawnCircleAlpha = circleAlpha * (e.mode() == ZoltraakMode.SINGLE ? fpCircle.phase2CircleAlphaScale() : 1.0F);

            if (drawnCircleAlpha > 0.001F) {

               RenderType circleType = e.black() ? ZoltraakRenderTypes.BLACK_CIRCLE : ZoltraakRenderTypes.ZOL_CIRCLE;

               VertexConsumer b = buffers.getBuffer(circleType);

               // Synced CPU rotation matching Iris Dual-Pass Phase 1
               double rot = t * 0.045;

               Vec3 ru = u.scale(Math.cos(rot)).add(v.scale(Math.sin(rot))).scale(radius);

               Vec3 rv = v.scale(Math.cos(rot)).subtract(u.scale(Math.sin(rot))).scale(radius);

               quad(b, mat, primaryCenter, ru, rv, formation, fpCircle.shaderCenterKeep(), fpCircle.shaderInnerCut(), drawnCircleAlpha);

               flush(buffers, circleType);

            }

            RenderType glowType = e.black() ? ZoltraakRenderTypes.BLACK_GLOW : ZoltraakRenderTypes.ZOL_GLOW;

            VertexConsumer glow = buffers.getBuffer(glowType);

            double muzzleSizeScale = e.mode() == ZoltraakMode.SINGLE ? fpCircle.phase2MuzzleSizeScale() : (first ? 0.72 : 1.0);

            muzzle *= e.mode() == ZoltraakMode.SINGLE ? fpCircle.phase2MuzzleAlphaScale() : (first ? 0.75F : 1.0F);

            if (muzzle > 0.003F && muzzleSizeScale > 0.01) {

               double size = (0.17 + charge * 0.35 + (shot >= 0.0F ? 0.6 * Math.exp(-shot * 0.22) : 0.0)) * muzzleSizeScale * e.scale();

               boolean lensFlares = com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isLensFlaresEnabled();

               if (e.black()) {

                  if (shot >= 0.0F) {

                     quad(glow, mat, primaryCenter, camRight.scale(size * 2.5), camUp.scale(size * 2.5), 0.008F, 0.006F, 0.014F, muzzle * 0.55F);

                     quad(glow, mat, primaryCenter.add(dir.scale(0.045)), camRight.scale(size), camUp.scale(size), 0.008F, 0.006F, 0.014F, muzzle);

                     if (lensFlares) {
                        quad(glow, mat, primaryCenter, camRight.scale(size * 4.0), camUp.scale(size * 0.12), 0.008F, 0.006F, 0.014F, muzzle);
                        quad(glow, mat, primaryCenter, camRight.scale(size * 0.1), camUp.scale(size * 2.0), 0.008F, 0.006F, 0.014F, muzzle * 0.7F);
                     }

                  } else {

                     quad(glow, mat, primaryCenter.add(dir.scale(0.02)), camRight.scale(size * 0.4), camUp.scale(size * 0.4), 0.25F, 0.12F, 0.35F, muzzle * 0.35F);

                  }

               } else {

                  if (shot >= 0.0F) {

                     quad(glow, mat, primaryCenter, u.scale(size * 0.90), v.scale(size * 0.90), 0.36F, 0.62F, 1.0F, muzzle * 0.35F);

                     quad(glow, mat, primaryCenter.add(dir.scale(0.045)), u.scale(size * 0.48), v.scale(size * 0.48), 1.0F, 1.0F, 1.0F, muzzle * 0.55F);

                  } else {

                     quad(glow, mat, primaryCenter.add(dir.scale(0.02)), camRight.scale(size * 0.4), camUp.scale(size * 0.4), 0.65F, 0.88F, 1.0F, muzzle * 0.35F);

                  }

               }

            }

            flush(buffers, glowType);

            VertexConsumer lines = buffers.getBuffer(ZoltraakRenderTypes.LIGHT);

            if (shot < 0.0F) {

               emitChargeConvergenceSpirals(
                  lines, mat, primaryCenter, dir, u, v, side, t, charge, e.scale(), fpCircle, first, e.mode(),
                  0.8F, 0.94F, 1.0F, 0.48F, 0.8F, 1.0F
               );

            }

            if (e.fired() && shot >= 0.0F) {

               if (hasBeam) {

                  Vec3 a = primaryCenter;

                  // Kinetic Electric Discharges and Ionized Filaments along perimeter

                  double coreRadius = (first ? 0.34 : 0.48) * e.scale() * energy;

                  for (int k = 0; k < 8; k++) {

                     double z = (shot * 8.5F + k * 5.43) % Math.max(1.0, beamHead);

                     double r = (coreRadius * 1.10 + 0.06 * Math.sin(t * 0.9 + k));

                     Vec3 radial = ring(u, v, k * 2.1 + t * 0.15, r);

                     ribbon(

                        lines,

                        mat,

                        a.add(dir.scale(Math.max(0.0, z - 2.8))).add(radial),

                        a.add(dir.scale(z)).add(radial),

                        side,

                        0.010F * e.scale(),

                        energy * 0.38F,

                        0.42F,

                        0.64F,

                        1.0F

                     );

                  }

               }

               double spineStart = fpCircle.reduceVisuals() ? Math.min(beamHead * 0.5, 0.95) : 0.2;
               if (shot > 5.0F && shot < 18.0F && beamHead > spineStart + 0.05) {

                  float aRibbon = (1.0F - ease((shot - 5.0F) / 13.0F)) * 0.20F * energy;

                  ribbon(lines, mat, primaryCenter.add(dir.scale(spineStart)), primaryCenter.add(dir.scale(beamHead)), side, 0.016F * e.scale(), aRibbon, 0.36F, 0.56F, 1.0F);

               }

            }

            float dt = t - e.impactAge();
            if (e.fired() && e.impact() && dt >= 0.0F && dt < ZoltraakImpactFx.IMPACT_DURATION) {
               ZoltraakImpactFx.renderImpact(
                  buffers, mat, lines, origin, e.end(), e.normal(), u, camera, camRight, camUp,
                  t, dt, e.scale(), e.black(), e.mode() == ZoltraakMode.LARGE
               );
            } else {
               flush(buffers, ZoltraakRenderTypes.LIGHT);
            }

            pose.popPose();

         }

      }

   }

   private static void arc(

      VertexConsumer b, Matrix4f m, Vec3 center, Vec3 u, Vec3 v, double r, float width, float alpha, float red, float green, float blue, double sweep

   ) {

      for (int i = 0; i < 96; i++) {

         double a = i * sweep / 96.0;

         double c = (i + 1) * sweep / 96.0;

         Vec3 p = center.add(ring(u, v, a, r));

         Vec3 q = center.add(ring(u, v, c, r));

         ribbon(b, m, p, q, ring(u, v, (a + c) / 2.0, 1.0), width, alpha, red, green, blue);

      }

   }

   /** Emits charge-up convergence spirals and inward streams anchored to the first-person circle when active. */
   static void emitChargeConvergenceSpirals(
      VertexConsumer lines, Matrix4f mat, Vec3 primaryCenter, Vec3 dir, Vec3 u, Vec3 v, Vec3 side,
      float t, float charge, float scale, ZoltraakRenderPass.FirstPersonCircleView fpCircle,
      boolean first, ZoltraakMode mode,
      float spiralR, float spiralG, float spiralB,
      float streamR, float streamG, float streamB
   ) {
      boolean singleFp = first && mode == ZoltraakMode.SINGLE;
      float alphaScale = (!singleFp || !fpCircle.reduceVisuals()) ? 1.0F
         : ("EDGE_ONLY".equals(fpCircle.mode()) ? 0.0F : Mth.clamp(fpCircle.opacity() * ("MINIMAL".equals(fpCircle.mode()) ? 0.55F : 0.85F), 0.0F, 1.0F));
      if (alphaScale <= 0.004F) {
         return;
      }

      Vec3 anchor = singleFp ? primaryCenter : Vec3.ZERO;
      double radiusScale = singleFp ? fpCircle.radiusScale() : 1.0;

      for (int k = 0; k < 5; k++) {
         for (int j = 0; j < 18; j++) {
            double p = j / 18.0;
            double q = (j + 1) / 18.0;
            double a = k * Math.PI * 2.0 / 5.0 - t * 0.1;
            double collapse = 0.75 * scale * radiusScale * (1.0 - charge * 0.5);
            Vec3 pa = anchor.add(ring(u, v, a + p * 1.4, collapse * (1.0 - p) + 0.05 * radiusScale));
            Vec3 pb = anchor.add(ring(u, v, a + q * 1.4, collapse * (1.0 - q) + 0.05 * radiusScale));
            ribbon(
               lines, mat, pa, pb, ring(u, v, a + p * 1.4, 1.0),
               (float) (0.025 * radiusScale * Math.sin(p * Math.PI)),
               charge * 0.65F * alphaScale, spiralR, spiralG, spiralB
            );
         }
      }

      for (int k = 0; k < 26; k++) {
         double p = (t * 0.065 + k * 0.618) % 1.0;
         double a = k * 2.399;
         double r = ((1.0 - p) * 1.8 + 0.08) * scale * radiusScale;
         double rTail = r + (0.08 + 0.25 * p) * radiusScale;
         Vec3 head = anchor.add(ring(u, v, a, r)).add(dir.scale(-0.35 * radiusScale * (1.0 - p)));
         Vec3 tail = anchor.add(ring(u, v, a, rTail)).add(dir.scale(-0.4 * radiusScale * (1.0 - p)));
         ribbon(
            lines, mat, tail, head, side,
            (float) (0.012 * radiusScale),
            (float) (Math.sin(p * Math.PI) * charge * 0.65) * alphaScale,
            streamR, streamG, streamB
         );
      }
   }

   private static void ribbon(VertexConsumer b, Matrix4f m, Vec3 a, Vec3 c, Vec3 side, float width, float alpha, float r, float g, float blue) {

      Vec3 w = side.scale(width);

      tri(b, m, a.subtract(w), a.add(w), c.add(w), r, g, blue, alpha);

      tri(b, m, a.subtract(w), c.add(w), c.subtract(w), r, g, blue, alpha);

   }

   private static void tri(VertexConsumer b, Matrix4f m, Vec3 a, Vec3 c, Vec3 d, float r, float g, float blue, float alpha) {
      float rc = Mth.clamp(r, 0.0F, 1.0F);
      float gc = Mth.clamp(g, 0.0F, 1.0F);
      float bc = Mth.clamp(blue, 0.0F, 1.0F);
      float ac = Mth.clamp(alpha, 0.0F, 1.0F);
      b.addVertex(m, (float)a.x, (float)a.y, (float)a.z).setColor(rc, gc, bc, ac);
      b.addVertex(m, (float)c.x, (float)c.y, (float)c.z).setColor(rc, gc, bc, ac);
      b.addVertex(m, (float)d.x, (float)d.y, (float)d.z).setColor(rc, gc, bc, ac);
   }

   private static void quad(VertexConsumer b, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, float r, float g, float blue, float a) {

      tex(b, m, c.subtract(u).subtract(v), 0.0F, 1.0F, r, g, blue, a);

      tex(b, m, c.add(u).subtract(v), 1.0F, 1.0F, r, g, blue, a);

      tex(b, m, c.add(u).add(v), 1.0F, 0.0F, r, g, blue, a);

      tex(b, m, c.subtract(u).add(v), 0.0F, 0.0F, r, g, blue, a);

   }

   private static void beamQuad(VertexConsumer b, Matrix4f m, Vec3 a, Vec3 c, Vec3 w, float age, float alpha, float span, float halfWidth) {
      float ageNorm = Mth.clamp(age, 0.0F, 1.0F);
      float spanNorm = Mth.clamp(span / 256.0F, 0.0F, 1.0F);
      float widthNorm = Mth.clamp(halfWidth / 16.0F, 0.0F, 1.0F);

      tex(b, m, a.subtract(w), 0.0F, 0.0F, ageNorm, spanNorm, widthNorm, alpha);
      tex(b, m, c.subtract(w), 1.0F, 0.0F, ageNorm, spanNorm, widthNorm, alpha);
      tex(b, m, c.add(w), 1.0F, 1.0F, ageNorm, spanNorm, widthNorm, alpha);
      tex(b, m, a.add(w), 0.0F, 1.0F, ageNorm, spanNorm, widthNorm, alpha);
   }

   private static void tex(VertexConsumer b, Matrix4f m, Vec3 p, float u, float v, float r, float g, float blue, float a) {
      b.addVertex(m, (float)p.x, (float)p.y, (float)p.z)
         .setUv(u, v)
         .setColor(Mth.clamp(r, 0.0F, 1.0F), Mth.clamp(g, 0.0F, 1.0F), Mth.clamp(blue, 0.0F, 1.0F), Mth.clamp(a, 0.0F, 1.0F));
   }

   private static void flush(MultiBufferSource b, RenderType t) {

      ZoltraakRenderPass.endBatchWithBloom(b, t,
         com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.getZoltraakGlow());

   }

}

