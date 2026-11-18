// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Shared Heavy Pyroclastic & Shockwave explosion VFX renderer for Standard and Black Zoltraak.
 * Renders crater fractures, curved kinetic shards/streamers, 3D ray-marched pyroclastic smoke/fireball,
 * dual Fresnel/plasma 3D shockwave domes, and peak detonation lens flare.
 */
public final class ZoltraakImpactFx {

    public static final float IMPACT_DURATION = 26.0F;

    private ZoltraakImpactFx() {}

    /** Renders the complete multi-layer 3D explosion at the beam impact point. */
    public static void renderImpact(
            MultiBufferSource buffers,
            Matrix4f mat,
            VertexConsumer beamLines,
            Vec3 origin,
            Vec3 hitPos,
            Vec3 rawNormal,
            Vec3 fallbackAxis,
            Vec3 camRelOrigin,
            Vec3 camRight,
            Vec3 camUp,
            float time,
            float dt,
            float scale,
            boolean black,
            boolean largeMode
    ) {
        if (dt < 0.0F || dt >= IMPACT_DURATION) {
            flush(buffers, ZoltraakRenderTypes.LIGHT);
            return;
        }

        float progress = Mth.clamp(dt / IMPACT_DURATION, 0.0F, 1.0F);
        float fade = 1.0F - smoothstep(progress);
        double sizeScale = scale * (largeMode ? 1.85 : 1.0);

        // Construct guaranteed right-handed orthonormal basis around impact surface normal: axX x axY == normal
        Vec3 normal = rawNormal != null && rawNormal.lengthSqr() > 1.0E-4 ? rawNormal.normalize() : new Vec3(0.0, 1.0, 0.0);
        Vec3 axX = normal.cross(new Vec3(0.0, 1.0, 0.0));
        if (axX.lengthSqr() < 0.01) {
            axX = fallbackAxis != null && fallbackAxis.lengthSqr() > 1.0E-4
                    ? fallbackAxis.normalize()
                    : normal.cross(new Vec3(0.0, 0.0, 1.0));
        }
        axX = axX.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : axX.normalize();
        Vec3 axY = normal.cross(axX).normalize();

        Vec3 center = hitPos.subtract(origin).add(normal.scale(0.06));
        float isBlackFlag = black ? 1.0F : 0.0F;

        // 1. Kinetic shards, ballistic glowing debris & violent plasma streamers
        emitKineticShardsAndStreamers(beamLines, mat, center, axX, axY, normal, camRight, time, dt, progress, fade, sizeScale, black);
        flush(buffers, ZoltraakRenderTypes.LIGHT);

        // 2. Ground crater fracture scorch & supersonic planar shockwave rings
        RenderType impactType = black ? ZoltraakRenderTypes.BLACK_IMPACT : ZoltraakRenderTypes.ZOL_IMPACT;
        VertexConsumer impactVc = buffers.getBuffer(impactType);
        double surfRadius = (2.8 + Math.pow(progress, 0.52) * 7.2) * sizeScale;
        quad(impactVc, mat, center, axX.scale(surfRadius), axY.scale(surfRadius), progress, isBlackFlag, 1.0F, fade * 0.94F);
        flush(buffers, impactType);

        // 3. True 3D ray-marched pyroclastic fireball & billowing self-shadowed ash plume
        float pyroFade = 1.0F - smoothstep(Mth.clamp((progress - 0.70F) / 0.30F, 0.0F, 1.0F));
        if (pyroFade > 0.006F) {
            double pyroR = (2.9 + Math.pow(progress, 0.44) * 3.4) * sizeScale;
            Vec3 pyroCenter = center.add(normal.scale((0.31 + 0.35 * progress) * pyroR));
            double camDist = camRelOrigin.distanceTo(pyroCenter);
            boolean camInside = camDist <= pyroR + 0.45;
            float flashBoost = Mth.clamp(1.0F - progress * 3.2F, 0.0F, 1.0F);

            VertexConsumer pyroVc = buffers.getBuffer(ZoltraakRenderTypes.ZOL_PYROCLAST);
            emitWorldSphere(
                    pyroVc, mat, pyroCenter, pyroR, 18, 28,
                    (float) pyroR, isBlackFlag,
                    progress, flashBoost, camInside ? 1.0F : 0.0F, pyroFade
            );
            flush(buffers, ZoltraakRenderTypes.ZOL_PYROCLAST);
        }

        // 4. Shader-driven 3D plasma ignition shell + outer supersonic Fresnel shockwave dome
        RenderType domeType = black ? ZoltraakRenderTypes.BLACK_BLAST_DOME : ZoltraakRenderTypes.ZOL_BLAST_DOME;
        VertexConsumer domeVc = buffers.getBuffer(domeType);

        float innerFade = 1.0F - smoothstep(Mth.clamp(progress / 0.19F, 0.0F, 1.0F));
        if (innerFade > 0.01F) {
            double innerR = (1.7 + progress * 4.8) * sizeScale;
            emitSurfaceHemisphereDome(
                    domeVc, mat, center, axX, axY, normal, innerR, 18, 36,
                    progress, isBlackFlag, 0.5F, innerFade * 0.95F
            );
        }

        float domeAlpha = fade * (float) Math.exp(-progress * 1.85) * 0.94F;
        if (domeAlpha > 0.006F) {
            double outerR = (2.4 + Math.pow(progress, 0.48) * 10.0) * sizeScale;
            emitSurfaceHemisphereDome(
                    domeVc, mat, center, axX, axY, normal, outerR, 22, 42,
                    progress, isBlackFlag, 0.0F, domeAlpha
            );
        }
        flush(buffers, domeType);

        // 5. Peak detonation core flash & camera-aligned anamorphic lens flare
        float burst = (float) Math.exp(-dt * 0.34) * fade;
        if (burst > 0.015F) {
            RenderType glowType = black ? ZoltraakRenderTypes.BLACK_GLOW : ZoltraakRenderTypes.ZOL_GLOW;
            VertexConsumer glowVc = buffers.getBuffer(glowType);
            Vec3 flashCenter = center.add(normal.scale(0.32 * sizeScale));
            boolean lensFlares = ZoltraakCinematicClientConfig.isLensFlaresEnabled();

            if (black) {
                double rOuter = (3.4 + dt * 0.18) * sizeScale;
                double rCore = (1.4 + dt * 0.06) * sizeScale;
                quad(glowVc, mat, flashCenter, camRight.scale(rOuter), camUp.scale(rOuter), 0.24F, 0.05F, 0.42F, burst * 0.72F);
                quad(glowVc, mat, flashCenter, camRight.scale(rCore), camUp.scale(rCore), 0.72F, 0.34F, 0.96F, burst * 0.85F);
                if (lensFlares) {
                    quad(glowVc, mat, flashCenter, camRight.scale((5.6 + dt * 0.24) * sizeScale), camUp.scale(0.16 * sizeScale), 0.55F, 0.14F, 0.88F, burst * 0.85F);
                    quad(glowVc, mat, flashCenter, camRight.scale(0.10 * sizeScale), camUp.scale(3.2 * sizeScale), 0.65F, 0.24F, 0.95F, burst * 0.75F);
                }
            } else {
                double rOuter = (3.6 + dt * 0.20) * sizeScale;
                double rCore = (1.5 + dt * 0.07) * sizeScale;
                quad(glowVc, mat, flashCenter, camRight.scale(rOuter), camUp.scale(rOuter), 0.32F, 0.72F, 1.0F, burst * 0.78F);
                quad(glowVc, mat, flashCenter, camRight.scale(rCore), camUp.scale(rCore), 1.0F, 1.0F, 1.0F, burst);
                if (lensFlares) {
                    quad(glowVc, mat, flashCenter, camRight.scale((6.0 + dt * 0.26) * sizeScale), camUp.scale(0.16 * sizeScale), 0.78F, 0.96F, 1.0F, burst);
                    quad(glowVc, mat, flashCenter, camRight.scale(0.10 * sizeScale), camUp.scale(3.4 * sizeScale), 1.0F, 1.0F, 1.0F, burst);
                }
            }
            flush(buffers, glowType);
        }
    }

    private static void emitKineticShardsAndStreamers(
            VertexConsumer lines,
            Matrix4f mat,
            Vec3 center,
            Vec3 axX,
            Vec3 axY,
            Vec3 normal,
            Vec3 camRight,
            float time,
            float dt,
            float progress,
            float fade,
            double sizeScale,
            boolean black
    ) {
        // A. 64 Ballistic ember shards with air-drag deceleration and curved parabolic trajectories
        final double drag = 0.13;
        final double grav = 0.015;
        double dragFactor = (1.0 - Math.exp(-drag * dt)) / drag;
        double dtPrev = Math.max(0.0, dt - 1.15);
        double dragFactorPrev = (1.0 - Math.exp(-drag * dtPrev)) / drag;

        for (int k = 0; k < 64; k++) {
            double azimuth = k * 2.3999632 + (k % 3) * 0.18;
            double elev = 0.12 + ((k * 37) % 100) / 100.0 * 0.88;
            double speed = (0.48 + ((k * 19) % 10) * 0.065) * sizeScale;

            Vec3 launchDir = ring(axX, axY, azimuth, Math.cos(elev)).add(normal.scale(Math.sin(elev) + 0.18)).normalize();
            Vec3 tip = center.add(launchDir.scale(speed * dragFactor)).add(0.0, -grav * dt * dt, 0.0);
            Vec3 tail = center.add(launchDir.scale(speed * dragFactorPrev)).add(0.0, -grav * dtPrev * dtPrev, 0.0);

            Vec3 seg = tip.subtract(tail);
            double minLen = (0.28 + 0.45 * (1.0 - progress)) * sizeScale;
            if (seg.lengthSqr() < minLen * minLen && seg.lengthSqr() > 1.0E-8) {
                tail = tip.subtract(seg.normalize().scale(minLen));
            }

            float shardAlpha = fade * (0.65F + 0.35F * (1.0F - progress));
            float shardW = (0.018F + (k % 3) * 0.008F) * (float) sizeScale;
            if (black) {
                ribbon(lines, mat, tail, tip, camRight, shardW, shardAlpha * 0.88F, 0.46F, 0.12F, 0.78F);
                ribbon(lines, mat, tail.lerp(tip, 0.45), tip, camRight, shardW * 0.48F, shardAlpha, 0.92F, 0.68F, 1.00F);
            } else {
                ribbon(lines, mat, tail, tip, camRight, shardW, shardAlpha * 0.88F, 0.18F, 0.78F, 1.00F);
                ribbon(lines, mat, tail.lerp(tip, 0.45), tip, camRight, shardW * 0.48F, shardAlpha, 0.96F, 0.99F, 1.00F);
            }
        }

        // B. 10 Violent erupting plasma jet tendrils surging out along the impact cone early in the blast
        float streamerLife = 1.0F - smoothstep(Mth.clamp(dt / 12.0F, 0.0F, 1.0F));
        if (streamerLife > 0.01F) {
            for (int k = 0; k < 10; k++) {
                double baseAngle = k * (Math.PI * 2.0 / 10.0) + Math.sin(k * 2.1) * 0.25;
                double reach = (1.6 + dt * 0.56 + (k % 3) * 0.5) * sizeScale;
                Vec3 prevPt = center.add(normal.scale(0.12 * sizeScale));

                for (int s = 1; s <= 5; s++) {
                    double frac = s / 5.0;
                    double swirl = baseAngle + Math.sin(frac * 4.5 - time * 0.25 + k) * 0.28;
                    double radDist = reach * frac;
                    double coneLift = Math.pow(frac, 0.85) * reach * (0.42 + (k % 4) * 0.14);
                    Vec3 nextPt = center.add(ring(axX, axY, swirl, radDist)).add(normal.scale(coneLift));

                    float segAlpha = streamerLife * (float) Math.sin(frac * Math.PI) * 0.88F;
                    float segW = (float) ((0.065 - 0.042 * frac) * sizeScale);
                    if (black) {
                        ribbon(lines, mat, prevPt, nextPt, camRight, segW, segAlpha * 0.75F, 0.42F, 0.10F, 0.72F);
                        ribbon(lines, mat, prevPt, nextPt, camRight, segW * 0.42F, segAlpha, 0.88F, 0.56F, 1.00F);
                    } else {
                        ribbon(lines, mat, prevPt, nextPt, camRight, segW, segAlpha * 0.75F, 0.22F, 0.80F, 1.00F);
                        ribbon(lines, mat, prevPt, nextPt, camRight, segW * 0.42F, segAlpha, 0.94F, 0.99F, 1.00F);
                    }
                    prevPt = nextPt;
                }
            }
        }
    }

    /** Emits a full world-axis-aligned unit sphere around center for the 3D ray-marched pyroclast volume. */
    private static void emitWorldSphere(
            VertexConsumer vc,
            Matrix4f mat,
            Vec3 center,
            double radius,
            int rings,
            int sectors,
            float uCoord,
            float vCoord,
            float r,
            float g,
            float b,
            float a
    ) {
        // Canonical right-handed world basis: (1,0,0) x (0,0,-1) == (0,1,0)
        for (int ri = 0; ri < rings; ri++) {
            double phi1 = -Math.PI * 0.5 + (ri / (double) rings) * Math.PI;
            double phi2 = -Math.PI * 0.5 + ((ri + 1) / (double) rings) * Math.PI;
            double cp1 = Math.cos(phi1), sp1 = Math.sin(phi1);
            double cp2 = Math.cos(phi2), sp2 = Math.sin(phi2);

            for (int si = 0; si < sectors; si++) {
                double th1 = (si / (double) sectors) * Math.PI * 2.0;
                double th2 = ((si + 1) / (double) sectors) * Math.PI * 2.0;
                float ct1 = (float) Math.cos(th1), st1 = (float) Math.sin(th1);
                float ct2 = (float) Math.cos(th2), st2 = (float) Math.sin(th2);

                float nx00 = (float) (cp1 * ct1), ny00 = (float) sp1, nz00 = (float) (-cp1 * st1);
                float nx10 = (float) (cp1 * ct2), ny10 = (float) sp1, nz10 = (float) (-cp1 * st2);
                float nx11 = (float) (cp2 * ct2), ny11 = (float) sp2, nz11 = (float) (-cp2 * st2);
                float nx01 = (float) (cp2 * ct1), ny01 = (float) sp2, nz01 = (float) (-cp2 * st1);

                if (ri < rings - 1) {
                    emitSphereVertex(vc, mat, center, radius, nx00, ny00, nz00, uCoord, vCoord, r, g, b, a);
                    emitSphereVertex(vc, mat, center, radius, nx10, ny10, nz10, uCoord, vCoord, r, g, b, a);
                    emitSphereVertex(vc, mat, center, radius, nx11, ny11, nz11, uCoord, vCoord, r, g, b, a);
                }
                if (ri > 0) {
                    emitSphereVertex(vc, mat, center, radius, nx00, ny00, nz00, uCoord, vCoord, r, g, b, a);
                    emitSphereVertex(vc, mat, center, radius, nx11, ny11, nz11, uCoord, vCoord, r, g, b, a);
                    emitSphereVertex(vc, mat, center, radius, nx01, ny01, nz01, uCoord, vCoord, r, g, b, a);
                }
            }
        }
    }

    /** Emits a high-resolution surface-aligned 3D hemisphere dome using guaranteed right-handed winding. */
    public static void emitSurfaceHemisphereDome(
            VertexConsumer vc,
            Matrix4f mat,
            Vec3 center,
            Vec3 axX,
            Vec3 axY,
            Vec3 axZ,
            double radius,
            int rings,
            int sectors,
            float r,
            float g,
            float b,
            float a
    ) {
        for (int ri = 0; ri < rings; ri++) {
            float tf1 = ri / (float) rings;
            float tf2 = (ri + 1) / (float) rings;
            double phi1 = tf1 * (Math.PI * 0.5);
            double phi2 = tf2 * (Math.PI * 0.5);
            double cp1 = Math.cos(phi1), sp1 = Math.sin(phi1);
            double cp2 = Math.cos(phi2), sp2 = Math.sin(phi2);

            for (int si = 0; si < sectors; si++) {
                float sf1 = si / (float) sectors;
                float sf2 = (si + 1) / (float) sectors;
                double th1 = sf1 * Math.PI * 2.0;
                double th2 = sf2 * Math.PI * 2.0;
                double ct1 = Math.cos(th1), st1 = Math.sin(th1);
                double ct2 = Math.cos(th2), st2 = Math.sin(th2);

                Vec3 n00 = axX.scale(cp1 * ct1).add(axY.scale(cp1 * st1)).add(axZ.scale(sp1));
                Vec3 n10 = axX.scale(cp1 * ct2).add(axY.scale(cp1 * st2)).add(axZ.scale(sp1));
                Vec3 n11 = axX.scale(cp2 * ct2).add(axY.scale(cp2 * st2)).add(axZ.scale(sp2));
                Vec3 n01 = axX.scale(cp2 * ct1).add(axY.scale(cp2 * st1)).add(axZ.scale(sp2));

                if (ri < rings - 1) {
                    emitSphereVertex(vc, mat, center, radius, (float) n00.x, (float) n00.y, (float) n00.z, sf1, tf1, r, g, b, a);
                    emitSphereVertex(vc, mat, center, radius, (float) n10.x, (float) n10.y, (float) n10.z, sf2, tf1, r, g, b, a);
                    emitSphereVertex(vc, mat, center, radius, (float) n11.x, (float) n11.y, (float) n11.z, sf2, tf2, r, g, b, a);
                }
                emitSphereVertex(vc, mat, center, radius, (float) n00.x, (float) n00.y, (float) n00.z, sf1, tf1, r, g, b, a);
                emitSphereVertex(vc, mat, center, radius, (float) n11.x, (float) n11.y, (float) n11.z, sf2, tf2, r, g, b, a);
                emitSphereVertex(vc, mat, center, radius, (float) n01.x, (float) n01.y, (float) n01.z, sf1, tf2, r, g, b, a);
            }
        }
    }

    private static void emitSphereVertex(
            VertexConsumer vc, Matrix4f mat, Vec3 center, double radius,
            float nx, float ny, float nz,
            float u, float v, float r, float g, float b, float a
    ) {
        float px = (float) (center.x + nx * radius);
        float py = (float) (center.y + ny * radius);
        float pz = (float) (center.z + nz * radius);
        vc.addVertex(mat, px, py, pz).setUv(u, v).setColor(Mth.clamp(r, 0.0F, 1.0F), Mth.clamp(g, 0.0F, 1.0F), Mth.clamp(b, 0.0F, 1.0F), Mth.clamp(a, 0.0F, 1.0F)).setNormal(nx, ny, nz);
    }

    private static void ribbon(VertexConsumer b, Matrix4f m, Vec3 start, Vec3 end, Vec3 side, float width, float alpha, float r, float g, float blue) {
        Vec3 w = side.scale(width);
        tri(b, m, start.subtract(w), start.add(w), end.add(w), r, g, blue, alpha);
        tri(b, m, start.subtract(w), end.add(w), end.subtract(w), r, g, blue, alpha);
    }

    private static void tri(VertexConsumer b, Matrix4f m, Vec3 a, Vec3 c, Vec3 d, float r, float g, float blue, float alpha) {
        float rc = Mth.clamp(r, 0.0F, 1.0F);
        float gc = Mth.clamp(g, 0.0F, 1.0F);
        float bc = Mth.clamp(blue, 0.0F, 1.0F);
        float ac = Mth.clamp(alpha, 0.0F, 1.0F);
        b.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(rc, gc, bc, ac);
        b.addVertex(m, (float) c.x, (float) c.y, (float) c.z).setColor(rc, gc, bc, ac);
        b.addVertex(m, (float) d.x, (float) d.y, (float) d.z).setColor(rc, gc, bc, ac);
    }

    private static void quad(VertexConsumer b, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, float r, float g, float blue, float a) {
        tex(b, m, c.subtract(u).subtract(v), 0.0F, 1.0F, r, g, blue, a);
        tex(b, m, c.add(u).subtract(v), 1.0F, 1.0F, r, g, blue, a);
        tex(b, m, c.add(u).add(v), 1.0F, 0.0F, r, g, blue, a);
        tex(b, m, c.subtract(u).add(v), 0.0F, 0.0F, r, g, blue, a);
    }

    private static void tex(VertexConsumer b, Matrix4f m, Vec3 p, float u, float v, float r, float g, float blue, float a) {
        b.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(Mth.clamp(r, 0.0F, 1.0F), Mth.clamp(g, 0.0F, 1.0F), Mth.clamp(blue, 0.0F, 1.0F), Mth.clamp(a, 0.0F, 1.0F));
    }

    private static Vec3 ring(Vec3 u, Vec3 v, double angle, double radius) {
        return u.scale(Math.cos(angle) * radius).add(v.scale(Math.sin(angle) * radius));
    }

    private static float smoothstep(float x) {
        float t = Mth.clamp(x, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static void flush(MultiBufferSource b, RenderType t) {
        ZoltraakRenderPass.endBatchWithBloom(b, t, ZoltraakCinematicClientConfig.getZoltraakGlow());
    }
}
