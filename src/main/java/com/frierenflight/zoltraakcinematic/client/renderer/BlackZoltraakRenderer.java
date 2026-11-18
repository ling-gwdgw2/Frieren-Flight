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

public final class BlackZoltraakRenderer<T extends Entity & IZoltraakVisualEntity> extends EntityRenderer<T> {
   public BlackZoltraakRenderer(Context c) {
      super(c);
      this.shadowRadius = 0.0F;
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
      if (ZoltraakRenderPass.getSpellBloomScale(e) <= 0.004F) {
         return;
      }
      float t = e.age(partial);
      float shot = t - 14.0F;
      Minecraft mc = Minecraft.getInstance();
      boolean first = mc.player != null && e.casterId() == mc.player.getId() && mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson();
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

      double radius = (e.mode() == ZoltraakMode.LARGE ? 6.2 : (e.mode() == ZoltraakMode.RAPID ? 1.15 : 1.85 * e.scale())) * (0.2 + 0.8 * ease(t / 11.0F));
      ZoltraakRenderPass.FirstPersonCircleView fpCircle = ZoltraakRenderPass.FirstPersonCircleView.resolve(e);
      if (first && e.mode() == ZoltraakMode.SINGLE) {
         radius *= fpCircle.radiusScale();
      }

      if (first && e.mode() == ZoltraakMode.RAPID) {
         radius *= 0.6;
      }

      float formation = Mth.clamp(t / 4.5F, 0.0F, 1.0F);
      Vec3 primaryCenter = dir.scale(first && e.mode() == ZoltraakMode.SINGLE ? fpCircle.offsetForward() : 0.03);
      float drawnCircleAlpha = circleAlpha * (e.mode() == ZoltraakMode.SINGLE ? fpCircle.phase2CircleAlphaScale() : 1.0F);
      if (drawnCircleAlpha > 0.001F) {
         double rot = -t * 0.045;
         Vec3 ru = u.scale(Math.cos(rot)).add(v.scale(Math.sin(rot))).scale(radius);
         Vec3 rv = v.scale(Math.cos(rot)).subtract(u.scale(Math.sin(rot))).scale(radius);
         VertexConsumer b = buffers.getBuffer(ZoltraakRenderTypes.BLACK_CIRCLE);
         quad(b, mat, primaryCenter, ru, rv, formation, fpCircle.shaderCenterKeep(), fpCircle.shaderInnerCut(), drawnCircleAlpha);
         flush(buffers, ZoltraakRenderTypes.BLACK_CIRCLE);
      }

      VertexConsumer glow = buffers.getBuffer(ZoltraakRenderTypes.BLACK_GLOW);
      float charge = ease(t / 14.0F);
      float energy = e.fired() && shot >= 0.0F ? (1.0F - ease((shot - 7.0F) / 11.0F)) : 0.0F;
      float muzzle = shot < 0.0F ? charge : Math.max((float)Math.exp(-Math.max(0.0F, shot) * 0.24), energy * 0.85F);
      double muzzleSizeScale = e.mode() == ZoltraakMode.SINGLE ? fpCircle.phase2MuzzleSizeScale() : (first ? 0.72 : 1.0);
      muzzle *= e.mode() == ZoltraakMode.SINGLE ? fpCircle.phase2MuzzleAlphaScale() : (first ? 0.75F : 1.0F);
      if (muzzle > 0.003F && muzzleSizeScale > 0.01) {
         double size = (0.17 + charge * 0.35 + (shot >= 0.0F ? 0.6 * Math.exp(-shot * 0.22) : 0.0)) * muzzleSizeScale * e.scale();

         if (shot >= 0.0F) {
            quad(glow, mat, primaryCenter, camRight.scale(size * 2.2), camUp.scale(size * 2.2), 0.34F, 0.09F, 0.58F, muzzle * 0.42F);
            quad(glow, mat, primaryCenter.add(dir.scale(0.045)), camRight.scale(size * 0.9), camUp.scale(size * 0.9), 0.58F, 0.22F, 0.86F, muzzle * 0.55F);
            if (com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig.isLensFlaresEnabled()) {
               quad(glow, mat, primaryCenter, camRight.scale(size * 3.2), camUp.scale(size * 0.12), 0.46F, 0.14F, 0.72F, muzzle * 0.52F);
               quad(glow, mat, primaryCenter, camRight.scale(size * 0.1), camUp.scale(size * 1.8), 0.46F, 0.14F, 0.72F, muzzle * 0.42F);
            }
         } else {
            quad(glow, mat, primaryCenter.add(dir.scale(0.02)), camRight.scale(size * 0.4), camUp.scale(size * 0.4), 0.25F, 0.12F, 0.35F, muzzle * 0.35F);
         }
      }

      flush(buffers, ZoltraakRenderTypes.BLACK_GLOW);
      VertexConsumer lines = buffers.getBuffer(ZoltraakRenderTypes.LIGHT);

      if (shot < 0.0F) {
         ZoltraakRenderer.emitChargeConvergenceSpirals(
            lines, mat, primaryCenter, dir, u, v, side, t, charge, e.scale(), fpCircle, first, e.mode(),
            0.28F, 0.15F, 0.38F, 0.28F, 0.15F, 0.38F
         );
      }

      if (e.fired() && shot >= 0.0F) {
         double head = Math.min(fpCircle.maxBeamReach(e.length()), shot * 24.0);
         double tail = 0.0;
         if (head > tail && energy > 0.0F) {
            Vec3 a = primaryCenter;
            Vec3 b = a.add(dir.scale(head));
            flush(buffers, ZoltraakRenderTypes.LIGHT);
            double halfWidth = (first ? 1.05 : 1.45) * e.scale();
            double startAdvance = fpCircle.reduceVisuals() ? Math.min(0.24, head * 0.35) : (first ? 0.50 : 0.06);
            if (head > startAdvance + 0.05) {
               VertexConsumer beam = buffers.getBuffer(ZoltraakRenderTypes.BLACK_BEAM);
               beamQuad(
                  beam,
                  mat,
                  a.add(dir.scale(startAdvance)),
                  b.add(dir.scale(halfWidth * 0.2)),
                  side.scale(halfWidth),
                  t / 64.0F,
                  energy,
                  (float)Math.max(0.1, head - startAdvance),
                  (float)halfWidth
               );
               flush(buffers, ZoltraakRenderTypes.BLACK_BEAM);
            }
            lines = buffers.getBuffer(ZoltraakRenderTypes.LIGHT);

            for (int k = 0; k < 9; k++) {
               double z = (shot * 7.0F + k * 6.37) % Math.max(1.0, head);
               double r = (0.35 + 0.12 * Math.sin(t * 0.7 + k)) * e.scale();
               Vec3 radial = ring(u, v, k * 2.4, r);
               ribbon(
                  lines, mat, a.add(dir.scale(Math.max(0.0, z - 2.2))).add(radial), a.add(dir.scale(z)).add(radial), side, 0.008F, energy * 0.65F, 0.28F, 0.15F, 0.38F
               );
            }

            for (int k = 0; k < 3; k++) {
               double z = shot * 3.2 - k * 6;
               if (!(z < 0.0) && !(z > head)) {
                  double r = (0.35 + Math.max(0.0F, shot - k * 2) * 0.07) * e.scale();
                  arc(lines, mat, a.add(dir.scale(z)), u, v, r, 0.015F, energy * 0.35F, 0.28F, 0.15F, 0.38F, Math.PI * 2);
               }
            }
         }

         double spineStart = fpCircle.reduceVisuals() ? Math.min(head * 0.5, 0.95) : 0.2;
         if (shot > 5.0F && shot < 18.0F && head > spineStart + 0.05) {
            float aRibbon = (1.0F - ease((shot - 5.0F) / 13.0F)) * 0.22F * energy;
            ribbon(lines, mat, primaryCenter.add(dir.scale(spineStart)), primaryCenter.add(dir.scale(head)), side, 0.018F * e.scale(), aRibbon, 0.28F, 0.15F, 0.38F);
         }
      }

      float dt = t - e.impactAge();
      if (e.fired() && e.impact() && dt >= 0.0F && dt < ZoltraakImpactFx.IMPACT_DURATION) {
         ZoltraakImpactFx.renderImpact(
            buffers, mat, lines, origin, e.end(), e.normal(), u, camera, camRight, camUp,
            t, dt, e.scale(), true, e.mode() == ZoltraakMode.LARGE
         );
      } else {
         flush(buffers, ZoltraakRenderTypes.LIGHT);
      }

      pose.popPose();
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

   private static void beamQuad(VertexConsumer b, Matrix4f m, Vec3 a, Vec3 c, Vec3 w, float age, float alpha, float length, float halfWidth) {
      float extent = halfWidth / Math.max(length, 0.001F);
      float ageNorm = Mth.clamp(age, 0.0F, 1.0F);
      float spanNorm = Mth.clamp(length / 256.0F, 0.0F, 1.0F);
      float widthNorm = Mth.clamp(halfWidth / 16.0F, 0.0F, 1.0F);
      tex(b, m, a.subtract(w), -extent, 0.0F, ageNorm, spanNorm, widthNorm, alpha);
      tex(b, m, c.subtract(w), 1.0F + extent, 0.0F, ageNorm, spanNorm, widthNorm, alpha);
      tex(b, m, c.add(w), 1.0F + extent, 1.0F, ageNorm, spanNorm, widthNorm, alpha);
      tex(b, m, a.add(w), -extent, 1.0F, ageNorm, spanNorm, widthNorm, alpha);
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
