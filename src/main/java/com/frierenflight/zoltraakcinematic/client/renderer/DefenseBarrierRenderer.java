// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import com.frierenflight.zoltraakcinematic.entity.DefenseBarrierEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class DefenseBarrierRenderer extends EntityRenderer<DefenseBarrierEntity> {
    private static final ResourceLocation TEX_GROUND_CIRCLE =
            ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "textures/spell/defense_ground_circle.png");

    private static final int DOME_SHELL_SLICES = 32;
    private static final double[] DOME_SHELL_COS = new double[DOME_SHELL_SLICES + 1];
    private static final double[] DOME_SHELL_SIN = new double[DOME_SHELL_SLICES + 1];
    private static final double[] HEX_COS = new double[7];
    private static final double[] HEX_SIN = new double[7];

    static {
        for (int i = 0; i <= DOME_SHELL_SLICES; i++) {
            double theta = (i * Math.PI * 2.0) / DOME_SHELL_SLICES;
            DOME_SHELL_COS[i] = Math.cos(theta);
            DOME_SHELL_SIN[i] = Math.sin(theta);
        }
        for (int k = 0; k <= 6; k++) {
            double a = (Math.PI / 6.0) + k * Math.PI / 3.0;
            HEX_COS[k] = Math.cos(a);
            HEX_SIN[k] = Math.sin(a);
        }
    }

    private static final List<DeferredBarrierDraw> DEFERRED_DRAWS = new ArrayList<>();

    public record DeferredBarrierDraw(
            DefenseBarrierRenderer renderer,
            DefenseBarrierEntity entity,
            float yaw,
            float partial,
            PoseStack pose,
            Matrix4f modelView,
            Matrix4f projection,
            int light
    ) {}

    public static boolean hasDeferredDraw() {
        return !DEFERRED_DRAWS.isEmpty();
    }

    public static void clearDeferred() {
        DEFERRED_DRAWS.clear();
    }

    public static void renderIrisPhase1(MultiBufferSource.BufferSource bufferSource, Matrix4fStack modelViewStack) {
        if (DEFERRED_DRAWS.isEmpty()) return;
        float bloomScale = ZoltraakCinematicClientConfig.getBarrierGlow();
        for (DeferredBarrierDraw draw : DEFERRED_DRAWS) {
            if (draw.entity.isRemoved()) continue;
            float fade = draw.entity.fade(draw.partial);
            if (fade <= 0.0F) continue;
            modelViewStack.set(draw.modelView);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(draw.projection, VertexSorting.DISTANCE_TO_ORIGIN);
            ZoltraakRenderPass.renderIrisScaled(bufferSource, bloomScale,
                    () -> draw.renderer.emitIrisEmissiveProxy(draw.entity, draw.partial, draw.pose, bufferSource, fade));
        }
    }

    public static void renderDeferred(MultiBufferSource.BufferSource bufferSource, Matrix4fStack modelViewStack) {
        if (DEFERRED_DRAWS.isEmpty()) return;
        for (DeferredBarrierDraw draw : DEFERRED_DRAWS) {
            if (draw.entity.isRemoved()) continue;
            modelViewStack.set(draw.modelView);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(draw.projection, VertexSorting.DISTANCE_TO_ORIGIN);
            draw.renderer.renderInternal(draw.entity, draw.yaw, draw.partial, draw.pose, bufferSource, draw.light);
        }
        DEFERRED_DRAWS.clear();
    }

    public DefenseBarrierRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(DefenseBarrierEntity entity) {
        return TEX_GROUND_CIRCLE;
    }

    @Override
    public void render(DefenseBarrierEntity entity, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        float fade = entity.fade(partial);
        if (fade <= 0.0F) {
            return;
        }

        if (!ZoltraakRenderPass.isReplaying() && ShaderCompatibility.useFullDetailPass()) {
            if (ShaderCompatibility.isShadowPass()) {
                return;
            }
            PoseStack copy = new PoseStack();
            copy.last().pose().set(stack.last().pose());
            copy.last().normal().set(stack.last().normal());
            DEFERRED_DRAWS.add(new DeferredBarrierDraw(
                    this,
                    entity,
                    yaw,
                    partial,
                    copy,
                    new Matrix4f(RenderSystem.getModelViewMatrix()),
                    new Matrix4f(RenderSystem.getProjectionMatrix()),
                    light
            ));
            return;
        }

        renderInternal(entity, yaw, partial, stack, buffers, light);
    }

    private record FirstPersonView(
            boolean active,
            boolean isDome,
            String mode,
            float opacity,
            Vec3 camForward,
            Vec3 centerFromCam
    ) {
        static final FirstPersonView INACTIVE = new FirstPersonView(false, false, "FULL", 1.0F, Vec3.ZERO, Vec3.ZERO);

        float alignment(double lx, double ly, double lz) {
            double dx = centerFromCam.x + lx;
            double dy = centerFromCam.y + ly;
            double dz = centerFromCam.z + lz;
            double lenSqr = dx * dx + dy * dy + dz * dz;
            if (lenSqr < 1.0E-6) {
                return 0.0F;
            }
            double dot = dx * camForward.x + dy * camForward.y + dz * camForward.z;
            return (float) (dot / Math.sqrt(lenSqr));
        }

        float irisWidthScale() {
            if (!active) {
                return 1.0F;
            }
            return switch (mode) {
                case "MINIMAL" -> Mth.lerp(opacity, 0.28F, 0.50F);
                case "EDGE_ONLY" -> Mth.lerp(opacity, 0.36F, 0.65F);
                default -> Mth.lerp(opacity, 0.30F, 0.56F);
            };
        }

        float phase2WidthScale() {
            return active ? Mth.lerp(opacity, 0.48F, 0.80F) : 1.0F;
        }

        boolean shouldRenderDirectionalCell(Vec3 cell, float cellHitBoost) {
            if (!active) {
                return true;
            }
            if ("EDGE_ONLY".equals(mode)) {
                return DefenseBarrierEntity.isPerimeterCell(cell) || cellHitBoost > 0.10F;
            }
            return opacity > 0.005F || cellHitBoost > 0.05F;
        }

        float factor(Vec3 localPoint, float hitBoost, boolean isSurface) {
            return factor(localPoint.x, localPoint.y, localPoint.z, hitBoost, isSurface);
        }

        float factor(double lx, double ly, double lz, float hitBoost, boolean isSurface) {
            if (!active) {
                return 1.0F;
            }
            float align = alignment(lx, ly, lz);
            float base;
            switch (mode) {
                case "MINIMAL" -> base = isSurface ? opacity * 0.14F : opacity * 0.85F;
                case "EDGE_ONLY" -> {
                    float peripheral = isDome ? Mth.clamp((0.80F - align) / 0.28F, 0.0F, 1.0F) : 1.0F;
                    base = opacity * peripheral * (isSurface ? 0.22F : 1.0F);
                }
                default -> {
                    float t = Mth.clamp((align - 0.66F) / 0.22F, 0.0F, 1.0F);
                    float centerMask = t * t * (3.0F - 2.0F * t);
                    float outerScale = isSurface ? 0.24F : 1.0F;
                    float innerScale = isSurface ? 0.02F : 0.14F;
                    base = opacity * Mth.lerp(centerMask, outerScale, innerScale);
                }
            }
            if (hitBoost > 0.01F) {
                float hitScale = isSurface ? 0.42F : 0.78F;
                float hitFloor = hitBoost * hitScale * (0.25F + 0.75F * opacity);
                base = Math.max(base, hitFloor);
            }
            return Mth.clamp(base, 0.0F, 1.0F);
        }
    }

    private FirstPersonView resolveFirstPersonView(DefenseBarrierEntity entity, DefenseBarrierEntity.VisualPose pose) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || !mc.options.getCameraType().isFirstPerson()) {
            return FirstPersonView.INACTIVE;
        }
        if (mc.player == null || mc.getCameraEntity() != mc.player) {
            return FirstPersonView.INACTIVE;
        }
        String mode = ZoltraakCinematicClientConfig.getBarrierView();
        if ("FULL".equals(mode)) {
            return FirstPersonView.INACTIVE;
        }
        Vec3 camPos = this.entityRenderDispatcher.camera.getPosition();
        boolean isDome = entity.mode() == DefenseBarrierEntity.MODE_DOME;
        boolean inside;
        if (isDome) {
            double maxR = DefenseBarrierEntity.DOME_RADIUS + 0.35;
            inside = camPos.distanceToSqr(pose.center()) <= maxR * maxR;
        } else {
            inside = entity.owner() == mc.player;
        }
        if (!inside) {
            return FirstPersonView.INACTIVE;
        }
        Vec3 camRight = new Vec3(new Vector3f(1.0F, 0.0F, 0.0F).rotate(this.entityRenderDispatcher.cameraOrientation()));
        Vec3 camUp = new Vec3(new Vector3f(0.0F, 1.0F, 0.0F).rotate(this.entityRenderDispatcher.cameraOrientation()));
        Vec3 camForward = camUp.cross(camRight).normalize();
        float opacity = ZoltraakCinematicClientConfig.getBarrierOpacity();
        return new FirstPersonView(true, isDome, mode, opacity, camForward, pose.center().subtract(camPos));
    }

    private void emitIrisEmissiveProxy(DefenseBarrierEntity entity, float partial, PoseStack stack, MultiBufferSource buffers, float fade) {
        float age = entity.age(partial);
        float life = Mth.clamp(entity.capacity() / entity.maximum(), 0.0F, 1.0F);
        float hitAge = entity.hitAge(partial);
        boolean hasHit = hitAge >= 0.0F && hitAge < 14.0F;
        if (life <= 0.0F && !hasHit) {
            return;
        }

        DefenseBarrierEntity.VisualPose pose = entity.visualPose(partial);
        FirstPersonView fpView = resolveFirstPersonView(entity, pose);
        Vec3 currentPos = new Vec3(
                Mth.lerp(partial, entity.xOld, entity.getX()),
                Mth.lerp(partial, entity.yOld, entity.getY()),
                Mth.lerp(partial, entity.zOld, entity.getZ())
        );
        Vec3 offset = pose.center().subtract(currentPos);
        Vec3 hit = entity.visualHit(pose);

        Vec3 camRight = new Vec3(new Vector3f(1.0F, 0.0F, 0.0F).rotate(this.entityRenderDispatcher.cameraOrientation()));
        Vec3 camUp = new Vec3(new Vector3f(0.0F, 1.0F, 0.0F).rotate(this.entityRenderDispatcher.cameraOrientation()));

        stack.pushPose();
        stack.translate(offset.x, offset.y, offset.z);
        Matrix4f m = stack.last().pose();
        VertexConsumer vc = buffers.getBuffer(ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);

        float hitBoost = hasHit ? (float) Math.exp(-hitAge * 0.22) * 0.35F : 0.0F;

        if (entity.mode() == DefenseBarrierEntity.MODE_DOME) {
            float deploy = Mth.clamp(age / 8.0F, 0.0F, 1.0F);
            float grow = 0.2F + 0.8F * (float) Math.sin(deploy * Math.PI * 0.5);
            double R = DefenseBarrierEntity.DOME_RADIUS * grow * 1.0008;
            float baseVis = fade * grow;
            float guard = ZoltraakRenderPass.irisFadeGuard(baseVis);
            float coreA = Mth.clamp(baseVis * (0.76F + hitBoost), 0.0F, 0.88F);
            float haloA = Mth.clamp(baseVis * (0.44F + hitBoost * 0.65F), 0.0F, 0.62F);

            if (life > 0.0F && coreA > 0.01F) {
                float coreR = 0.42F * guard;
                float coreG = 0.94F * guard;
                float coreB = 1.00F * guard;

                float haloR = 0.16F * guard;
                float haloG = 0.72F * guard;
                float haloB = 1.00F * guard;

                float widthScale = fpView.irisWidthScale();
                for (GeodesicDomeData.Edge edge : GeodesicDomeData.EDGES) {
                    Vec3 ep1 = edge.p1();
                    Vec3 ep2 = edge.p2();
                    double p1x = ep1.x * R, p1y = ep1.y * R, p1z = ep1.z * R;
                    double p2x = ep2.x * R, p2y = ep2.y * R, p2z = ep2.z * R;
                    float edgeAttn = fpView.factor((p1x + p2x) * 0.5, (p1y + p2y) * 0.5, (p1z + p2z) * 0.5, hitBoost, false);
                    if (edgeAttn <= 0.015F) continue;
                    Vec3 en = edge.normal();
                    double dx = p2x - p1x, dy = p2y - p1y, dz = p2z - p1z;
                    double sx = dy * en.z - dz * en.y;
                    double sy = dz * en.x - dx * en.z;
                    double sz = dx * en.y - dy * en.x;
                    double sLenSqr = sx * sx + sy * sy + sz * sz;
                    if (sLenSqr < 1.0E-8) continue;
                    double invSLen = 1.0 / Math.sqrt(sLenSqr);
                    float rgbAttn = fpView.active() ? Mth.sqrt(edgeAttn) : 1.0F;
                    ZoltraakRenderPass.emitFeatheredBeamSegment(
                            vc, m, p1x, p1y, p1z, p2x, p2y, p2z,
                            sx * invSLen, sy * invSLen, sz * invSLen,
                            0.014 * widthScale, 0.095 * widthScale,
                            coreR * rgbAttn, coreG * rgbAttn, coreB * rgbAttn, coreA * edgeAttn,
                            haloR * rgbAttn, haloG * rgbAttn, haloB * rgbAttn, haloA * edgeAttn
                    );
                }

                // Subtle localized sweeping electric S-arc matching Phase 2 renderElectricArc cleanly
                double arcAngleBase = age * 0.04;
                int arcSteps = 32;
                double arcRad = R + 0.02;
                for (int i = 0; i < arcSteps; i++) {
                    double frac1 = (double) i / arcSteps;
                    double frac2 = (double) (i + 1) / arcSteps;
                    float arcWeight = (float) Math.pow(Math.sin(((frac1 + frac2) * 0.5) * Math.PI), 1.5);
                    if (arcWeight <= 0.03F) continue;
                    double phi1 = Math.toRadians(-40.0 + 80.0 * Math.sin(frac1 * Math.PI));
                    double phi2 = Math.toRadians(-40.0 + 80.0 * Math.sin(frac2 * Math.PI));
                    double th1 = arcAngleBase + frac1 * Math.PI * 0.75;
                    double th2 = arcAngleBase + frac2 * Math.PI * 0.75;
                    double cPhi1 = Math.cos(phi1) * arcRad, cPhi2 = Math.cos(phi2) * arcRad;
                    double x1 = Math.cos(th1) * cPhi1, y1 = Math.sin(phi1) * arcRad, z1 = Math.sin(th1) * cPhi1;
                    double x2 = Math.cos(th2) * cPhi2, y2 = Math.sin(phi2) * arcRad, z2 = Math.sin(th2) * cPhi2;
                    float arcAttn = fpView.factor((x1 + x2) * 0.5, (y1 + y2) * 0.5, (z1 + z2) * 0.5, hitBoost, false);
                    if (arcAttn <= 0.015F) continue;
                    double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
                    double tx = dy * z1 - dz * y1;
                    double ty = dz * x1 - dx * z1;
                    double tz = dx * y1 - dy * x1;
                    double tLenSqr = tx * tx + ty * ty + tz * tz;
                    if (tLenSqr < 1.0E-8) continue;
                    double invTLen = 1.0 / Math.sqrt(tLenSqr);
                    float segA = Mth.clamp(arcWeight * baseVis * 0.50F * arcAttn, 0.0F, 0.58F);
                    float segG = ZoltraakRenderPass.irisFadeGuard(segA) * (fpView.active() ? Mth.sqrt(arcAttn) : 1.0F);
                    double arcWidth = arcWeight * fpView.irisWidthScale();
                    ZoltraakRenderPass.emitFeatheredBeamSegment(
                            vc, m, x1, y1, z1, x2, y2, z2,
                            tx * invTLen, ty * invTLen, tz * invTLen,
                            0.014 * arcWidth, 0.085 * arcWidth,
                            0.55F * segG, 0.96F * segG, 1.00F * segG, segA,
                            0.18F * segG, 0.78F * segG, 1.00F * segG, segA * 0.55F
                    );
                }
            }

            if (hasHit) {
                float impactFade = Mth.clamp((float) Math.exp(-hitAge * 0.22) * fade * 0.95F, 0.0F, 0.96F);
                if (impactFade > 0.02F) {
                    float ig = ZoltraakRenderPass.irisFadeGuard(impactFade);
                    Vec3 hitNormal = hit.lengthSqr() > 1.0E-4 ? hit.normalize() : new Vec3(0, 1, 0);
                    Vec3 sparkCenter = hit.add(hitNormal.scale(0.10));
                    Vec3 iu = hitNormal.cross(new Vec3(1.0, 0.0, 0.0));
                    iu = iu.lengthSqr() < 0.01 ? hitNormal.cross(new Vec3(0.0, 0.0, 1.0)).normalize() : iu.normalize();
                    Vec3 iv = iu.cross(hitNormal).normalize();

                    double ringR = 0.45 + hitAge * 0.24;
                    ZoltraakRenderPass.emitFeatheredRing(
                            vc, m, sparkCenter, iu, iv,
                            Math.max(0.05, ringR - 0.50), ringR, ringR + 0.65, 24,
                            0.68F * ig, 0.96F * ig, 1.00F * ig, impactFade
                    );
                    ZoltraakRenderPass.emitFeatheredStarBurst(
                            vc, m, sparkCenter, camRight, camUp, 1.40 + hitAge * 0.16,
                            0.60F * ig, 0.94F * ig, 1.00F * ig, Mth.clamp(impactFade * 0.88F, 0.0F, 0.92F)
                    );
                }
            }
        } else {
            float deploy = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
            float grow = 0.2F + 0.8F * (float) Math.sin(deploy * Math.PI * 0.5);
            double size = DefenseBarrierEntity.CELL_RADIUS * grow;
            Vec3 u = pose.right();
            Vec3 v = pose.up();
            Vec3 normal = pose.normal();
            float baseVis = fade * grow;
            float guard = ZoltraakRenderPass.irisFadeGuard(baseVis);
            float coreA = Mth.clamp(baseVis * (0.90F + hitBoost), 0.0F, 0.96F);
            float haloA = Mth.clamp(baseVis * (0.60F + hitBoost * 0.70F), 0.0F, 0.76F);

            if (life > 0.0F && coreA > 0.01F) {
                float coreR = 0.45F * guard;
                float coreG = 0.94F * guard;
                float coreB = 1.00F * guard;
                float haloR = 0.16F * guard;
                float haloG = 0.72F * guard;
                float haloB = 1.00F * guard;

                float widthScale = fpView.irisWidthScale();
                for (Vec3 cell : DefenseBarrierEntity.cells()) {
                    if (!fpView.shouldRenderDirectionalCell(cell, hitBoost)) continue;
                    double cx = u.x * cell.x * grow + v.x * cell.y * grow;
                    double cy = u.y * cell.x * grow + v.y * cell.y * grow;
                    double cz = u.z * cell.x * grow + v.z * cell.y * grow;
                    float cellAttn = fpView.factor(cx, cy, cz, hitBoost, false);
                    if (cellAttn <= 0.015F) continue;
                    float rgbAttn = fpView.active() ? Mth.sqrt(cellAttn) : 1.0F;
                    for (int k = 0; k < 6; k++) {
                        double c0 = HEX_COS[k] * size, s0 = HEX_SIN[k] * size;
                        double c1 = HEX_COS[k + 1] * size, s1 = HEX_SIN[k + 1] * size;
                        double p1x = cx + u.x * c0 + v.x * s0, p1y = cy + u.y * c0 + v.y * s0, p1z = cz + u.z * c0 + v.z * s0;
                        double p2x = cx + u.x * c1 + v.x * s1, p2y = cy + u.y * c1 + v.y * s1, p2z = cz + u.z * c1 + v.z * s1;
                        double dx = p2x - p1x, dy = p2y - p1y, dz = p2z - p1z;
                        double sx = dy * normal.z - dz * normal.y;
                        double sy = dz * normal.x - dx * normal.z;
                        double sz = dx * normal.y - dy * normal.x;
                        double sLenSqr = sx * sx + sy * sy + sz * sz;
                        if (sLenSqr < 1.0E-8) continue;
                        double invSLen = 1.0 / Math.sqrt(sLenSqr);
                        ZoltraakRenderPass.emitFeatheredBeamSegment(
                                vc, m, p1x, p1y, p1z, p2x, p2y, p2z,
                                sx * invSLen, sy * invSLen, sz * invSLen,
                                0.024 * widthScale, 0.155 * widthScale,
                                coreR * rgbAttn, coreG * rgbAttn, coreB * rgbAttn, coreA * cellAttn,
                                haloR * rgbAttn, haloG * rgbAttn, haloB * rgbAttn, haloA * cellAttn
                        );
                    }
                }
            }

            if (hasHit) {
                float impactFade = Mth.clamp((float) Math.exp(-hitAge * 0.22) * fade * 0.95F, 0.0F, 0.96F);
                if (impactFade > 0.02F) {
                    float ig = ZoltraakRenderPass.irisFadeGuard(impactFade);
                    Vec3 sparkCenter = hit.add(normal.scale(0.08));
                    double ringR = 0.40 + hitAge * 0.20;
                    ZoltraakRenderPass.emitFeatheredRing(
                            vc, m, sparkCenter, u, v,
                            Math.max(0.05, ringR - 0.45), ringR, ringR + 0.60, 24,
                            0.68F * ig, 0.96F * ig, 1.00F * ig, impactFade
                    );
                    ZoltraakRenderPass.emitFeatheredStarBurst(
                            vc, m, sparkCenter, camRight, camUp, 1.25 + hitAge * 0.14,
                            0.60F * ig, 0.94F * ig, 1.00F * ig, Mth.clamp(impactFade * 0.88F, 0.0F, 0.92F)
                    );
                }
            }
        }

        ZoltraakRenderTypes.endIrisBatch(buffers, ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
        stack.popPose();
    }

    private void renderInternal(DefenseBarrierEntity entity, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        float age = entity.age(partial);
        float fade = entity.fade(partial);
        float life = Mth.clamp(entity.capacity() / entity.maximum(), 0.0F, 1.0F);

        if (fade <= 0.0F) {
            return;
        }

        DefenseBarrierEntity.VisualPose pose = entity.visualPose(partial);
        FirstPersonView fpView = resolveFirstPersonView(entity, pose);
        Vec3 u = pose.right();
        Vec3 v = pose.up();
        Vec3 normal = pose.normal();
        Vec3 hit = entity.visualHit(pose);

        Vec3 currentPos = new Vec3(
                Mth.lerp(partial, entity.xOld, entity.getX()),
                Mth.lerp(partial, entity.yOld, entity.getY()),
                Mth.lerp(partial, entity.zOld, entity.getZ())
        );
        Vec3 offset = pose.center().subtract(currentPos);

        Vec3 camRight = new Vec3(new Vector3f(1.0F, 0.0F, 0.0F).rotate(this.entityRenderDispatcher.cameraOrientation()));
        Vec3 camUp = new Vec3(new Vector3f(0.0F, 1.0F, 0.0F).rotate(this.entityRenderDispatcher.cameraOrientation()));

        stack.pushPose();
        stack.translate(offset.x, offset.y, offset.z);
        Matrix4f matrix = stack.last().pose();

        if (entity.mode() == DefenseBarrierEntity.MODE_DOME) {
            renderDome(entity, matrix, buffers, age, fade, life, partial, pose, hit, camRight, camUp, fpView);
        } else {
            renderDirectional(entity, matrix, buffers, age, fade, life, partial, pose, hit, u, v, normal, camRight, camUp, fpView);
        }

        stack.popPose();
    }

    // 1. Dome Barrier (MODE_DOME)

    private void renderDome(DefenseBarrierEntity entity, Matrix4f m, MultiBufferSource buffers,
                            float age, float fade, float life, float partial,
                            DefenseBarrierEntity.VisualPose pose, Vec3 hit,
                            Vec3 camRight, Vec3 camUp, FirstPersonView fpView) {
        double R = DefenseBarrierEntity.DOME_RADIUS;

        // Layer A: Translucent dome body
        if (life > 0.0F) {
            renderEnergyDomeShell(m, buffers, R * 0.985, age, fade, life, partial, hit, entity.hitAge(partial), fpView);
        }

        // Layer B: Hex tiles and glowing seams
        renderGeodesicHoneycomb(m, buffers, R, age, fade, life, partial, hit, entity.hitAge(partial), fpView);

        // Layer C: Sweeping electric arc ribbon
        if (life > 0.0F) {
            renderElectricArc(m, buffers, R, age, fade, fpView);
        }

        // Layer D: Impact hit flash and sparks
        float impact = entity.hitAge(partial);
        if (impact < 14.0F) {
            float impactProgress = Mth.clamp(impact / 14.0F, 0.0F, 1.0F);
            float impactFade = (float) Math.exp(-impact * 0.25) * fade * 0.95F;
            double sparkSize = 0.95 + impact * 0.16;

            VertexConsumer impactConsumer = buffers.getBuffer(ZoltraakRenderTypes.ZOL_IMPACT);
            Vec3 hitNormal = hit.lengthSqr() > 1.0E-4 ? hit.normalize() : new Vec3(0, 1, 0);
            Vec3 sparkCenter = hit.add(hitNormal.scale(0.12));

            // Impact flash and sparks
            quadImpact(impactConsumer, m, sparkCenter, camRight.scale(sparkSize), camUp.scale(sparkSize),
                    impactProgress, false, false, impactFade);
            flush(buffers, ZoltraakRenderTypes.ZOL_IMPACT);
        }

        // Layer E: Shatter burst
        if (life == 0.0F && fade > 0.03F) {
            float shatterProgress = Mth.clamp(1.0F - fade, 0.0F, 1.0F);
            float shatterFade = fade * 0.90F;
            double centerBurst = 2.4 + shatterProgress * 1.6;

            VertexConsumer impactConsumer = buffers.getBuffer(ZoltraakRenderTypes.ZOL_IMPACT);
            // Rupture burst at center
            quadImpact(impactConsumer, m, Vec3.ZERO, camRight.scale(centerBurst), camUp.scale(centerBurst),
                    shatterProgress, false, false, shatterFade);
            // Rupture spark at contact point
            if (hit.lengthSqr() > 1.0E-4) {
                double breachSize = 1.6 + shatterProgress * 1.2;
                quadImpact(impactConsumer, m, hit, camRight.scale(breachSize), camUp.scale(breachSize),
                        shatterProgress, false, false, shatterFade * 0.85F);
            }
            flush(buffers, ZoltraakRenderTypes.ZOL_IMPACT);
        }
    }

    private void renderEnergyDomeShell(Matrix4f m, MultiBufferSource buffers, double r,
                                       float age, float fade, float life, float partial,
                                       Vec3 hit, float hitAge, FirstPersonView fpView) {
        VertexConsumer surface = buffers.getBuffer(ZoltraakRenderTypes.DEFENSE_SURFACE);
        int stacks = 14;
        float shellHit = hitAge >= 0.0F && hitAge < 14.0F ? (float) Math.exp(-hitAge * 0.25) * 0.45F : 0.0F;

        for (int j = -stacks; j < stacks; j++) {
            double phi1 = (j * Math.PI * 0.5) / stacks;
            double phi2 = ((j + 1) * Math.PI * 0.5) / stacks;

            double y1 = Math.sin(phi1) * r;
            double y2 = Math.sin(phi2) * r;
            double r1 = Math.cos(phi1) * r;
            double r2 = Math.cos(phi2) * r;

            // Electric blue gradient from equator to poles
            float t1 = (float) Math.abs(j) / stacks;
            float t2 = (float) Math.abs(j + 1) / stacks;

            float red1 = 0.01F + 0.03F * t1;
            float green1 = 0.05F + 0.12F * t1;
            float blue1 = 0.22F + 0.15F * t1;
            float alpha1 = (0.05F + 0.08F * (1.0F - t1)) * fade; // subtle deep-space backing

            float red2 = 0.01F + 0.03F * t2;
            float green2 = 0.05F + 0.12F * t2;
            float blue2 = 0.22F + 0.15F * t2;
            float alpha2 = (0.05F + 0.08F * (1.0F - t2)) * fade;

            for (int i = 0; i < DOME_SHELL_SLICES; i++) {
                double c1 = DOME_SHELL_COS[i], s1 = DOME_SHELL_SIN[i];
                double c2 = DOME_SHELL_COS[i + 1], s2 = DOME_SHELL_SIN[i + 1];

                double p1x = c1 * r1, p1z = s1 * r1;
                double p2x = c2 * r1, p2z = s2 * r1;
                double p3x = c2 * r2, p3z = s2 * r2;
                double p4x = c1 * r2, p4z = s1 * r2;

                float shellAttn = fpView.factor(
                        (p1x + p2x + p3x + p4x) * 0.25,
                        (y1 + y2) * 0.5,
                        (p1z + p2z + p3z + p4z) * 0.25,
                        shellHit,
                        true
                );
                if (shellAttn > 0.015F) {
                    tri(surface, m, p1x, y1, p1z, p2x, y1, p2z, p3x, y2, p3z, red1, green1, blue1, alpha1 * shellAttn);
                    tri(surface, m, p1x, y1, p1z, p3x, y2, p3z, p4x, y2, p4z, red2, green2, blue2, alpha2 * shellAttn);
                }
            }
        }

        flush(buffers, ZoltraakRenderTypes.DEFENSE_SURFACE);
    }

    private void renderGeodesicHoneycomb(Matrix4f m, MultiBufferSource buffers, double radius,
                                         float age, float fade, float life, float partial,
                                         Vec3 hit, float hitAge, FirstPersonView fpView) {
        int index = 0;
        float deploy = Mth.clamp(age / 8.0F, 0.0F, 1.0F);
        float grow = 0.2F + 0.8F * (float) Math.sin(deploy * Math.PI * 0.5);
        double scaledRadius = radius * grow;
        double hitAgeExpSurface = Math.exp(-hitAge * 0.32);

        double[] curX = new double[8];
        double[] curY = new double[8];
        double[] curZ = new double[8];
        double[] inX = new double[8];
        double[] inY = new double[8];
        double[] inZ = new double[8];

        // --- Pass 1: Honeycomb Plate Faces & Beveled Facets (DEFENSE_SURFACE) ---
        VertexConsumer surface = buffers.getBuffer(ZoltraakRenderTypes.DEFENSE_SURFACE);
        for (GeodesicDomeData.Cell cell : GeodesicDomeData.CELLS) {
            Vec3 norm = cell.center();
            Vec3[] verts = cell.vertices();
            int vCount = verts.length;

            double shX = 0.0, shY = 0.0, shZ = 0.0;
            if (life == 0.0F) {
                float shatter = 1.0F - fade;
                double shScale = shatter * (3.0 + Math.sin(index * 2.3) * 2.2);
                shX = norm.x * shScale;
                shY = norm.y * shScale + shatter * 2.5;
                shZ = norm.z * shScale;
            }

            double cx = norm.x * scaledRadius + shX;
            double cy = norm.y * scaledRadius + shY;
            double cz = norm.z * scaledRadius + shZ;

            // Impact glow feedback
            double hdx = cx - hit.x, hdy = cy - hit.y, hdz = cz - hit.z;
            double distToHit = hdx * hdx + hdy * hdy + hdz * hdz;
            double hitGlow = hitAgeExpSurface * Math.exp(-distToHit * 0.40);

            // Active random cell pulsing (like energetic power tiles)
            boolean isPulseCell = (index + (int) (age * 0.20)) % 6 == 0;
            float pulse = isPulseCell ? 0.45F : 0.0F;

            float r = (float) Mth.clamp(0.02F + 0.90F * hitGlow, 0.0F, 1.0F);
            float g = (float) Mth.clamp(0.12F + 0.65F * hitGlow + 0.25F * pulse, 0.0F, 1.0F);
            float b = (float) Mth.clamp(0.50F + 0.50F * hitGlow, 0.0F, 1.0F);
            float faceAlpha = (float) Mth.clamp((0.36F + 0.45F * hitGlow + 0.20F * pulse) * fade * grow, 0.0F, 1.0F);
            faceAlpha *= fpView.factor(cx, cy, cz, (float) hitGlow, true);
            if (faceAlpha <= 0.004F) {
                index++;
                continue;
            }

            for (int k = 0; k < vCount; k++) {
                Vec3 vp = verts[k];
                double px = vp.x * scaledRadius + shX;
                double py = vp.y * scaledRadius + shY;
                double pz = vp.z * scaledRadius + shZ;
                curX[k] = px;
                curY[k] = py;
                curZ[k] = pz;
                inX[k] = px * 0.88 + cx * 0.12;
                inY[k] = py * 0.88 + cy * 0.12;
                inZ[k] = pz * 0.88 + cz * 0.12;
            }

            // 1. Draw Inset Hexagonal Plate Face
            for (int k = 0; k < vCount; k++) {
                int next = (k + 1) % vCount;
                tri(surface, m, cx, cy, cz, inX[k], inY[k], inZ[k], inX[next], inY[next], inZ[next], r, g, b, faceAlpha);
            }

            // 2. Draw Beveled Edge Facets (slight shade variation creating 3D depth)
            float bevelAlpha = faceAlpha * 0.85F;
            float br = r * 0.8F;
            float bg = g * 0.85F;
            for (int k = 0; k < vCount; k++) {
                int next = (k + 1) % vCount;
                tri(surface, m, curX[k], curY[k], curZ[k], curX[next], curY[next], curZ[next], inX[next], inY[next], inZ[next], br, bg, b, bevelAlpha);
                tri(surface, m, curX[k], curY[k], curZ[k], inX[next], inY[next], inZ[next], inX[k], inY[k], inZ[k], br, bg, b, bevelAlpha);
            }

            index++;
        }
        flush(buffers, ZoltraakRenderTypes.DEFENSE_SURFACE);

        // --- Pass 2: Glowing Neon Cyan Seams & Stress Cracks (LIGHT) ---
        VertexConsumer lines = buffers.getBuffer(ZoltraakRenderTypes.LIGHT);
        if (life > 0.0F) {
            double edgeR = scaledRadius * 1.0008;
            double hitAgeExpEdge = Math.exp(-hitAge * 0.28);
            double lineWidthScale = fpView.phase2WidthScale();
            for (GeodesicDomeData.Edge edge : GeodesicDomeData.EDGES) {
                Vec3 ep1 = edge.p1();
                Vec3 ep2 = edge.p2();
                Vec3 norm = edge.normal();
                double p1x = ep1.x * edgeR, p1y = ep1.y * edgeR, p1z = ep1.z * edgeR;
                double p2x = ep2.x * edgeR, p2y = ep2.y * edgeR, p2z = ep2.z * edgeR;

                double hdx = p1x - hit.x, hdy = p1y - hit.y, hdz = p1z - hit.z;
                double distToHit = hdx * hdx + hdy * hdy + hdz * hdz;
                float flash = (float) (hitAgeExpEdge * Math.exp(-distToHit * 0.35));
                float linePower = fade * grow * (0.85F + 0.55F * flash)
                        * fpView.factor((p1x + p2x) * 0.5, (p1y + p2y) * 0.5, (p1z + p2z) * 0.5, flash, false);
                if (linePower <= 0.01F) continue;

                // Outer cyan glow line (width 0.012)
                line(lines, m, p1x, p1y, p1z, p2x, p2y, p2z, norm.x, norm.y, norm.z, 0.012 * lineWidthScale, linePower * 0.28F, 0.0F, 0.82F, 1.0F);
                // Core neon line (width 0.005)
                line(lines, m, p1x, p1y, p1z, p2x, p2y, p2z, norm.x, norm.y, norm.z, 0.005 * lineWidthScale, linePower * 0.90F, 0.20F, 0.95F, 1.0F);
                // Center crisp highlight (width 0.002)
                line(lines, m, p1x, p1y, p1z, p2x, p2y, p2z, norm.x, norm.y, norm.z, 0.002 * lineWidthScale, linePower * 1.0F, 0.85F, 0.98F, 1.0F);
            }

            // Stress Cracks if capacity < 40%
            if (life < 0.4F) {
                int cIdx = 0;
                double crackR = scaledRadius * 1.001;
                for (GeodesicDomeData.Cell cell : GeodesicDomeData.CELLS) {
                    if (cIdx % 4 == 0) {
                        Vec3 norm = cell.center();
                        double cx = norm.x * crackR, cy = norm.y * crackR, cz = norm.z * crackR;
                        Vec3[] verts = cell.vertices();
                        if (verts.length >= 4) {
                            float power = fade * grow * 0.9F * fpView.factor(cx, cy, cz, 0.35F, false);
                            if (power > 0.01F) {
                                Vec3 v0 = verts[0];
                                Vec3 v3 = verts[3];
                                double p1x = (v0.x * 0.8 + norm.x * 0.2) * crackR;
                                double p1y = (v0.y * 0.8 + norm.y * 0.2) * crackR;
                                double p1z = (v0.z * 0.8 + norm.z * 0.2) * crackR;
                                double p2x = cx + 0.04, p2y = cy - 0.02, p2z = cz + 0.03;
                                double p3x = (v3.x * 0.7 + norm.x * 0.3) * crackR;
                                double p3y = (v3.y * 0.7 + norm.y * 0.3) * crackR;
                                double p3z = (v3.z * 0.7 + norm.z * 0.3) * crackR;
                                line(lines, m, p1x, p1y, p1z, p2x, p2y, p2z, norm.x, norm.y, norm.z, 0.004 * lineWidthScale, power, 0.95F, 0.98F, 1.0F);
                                line(lines, m, p2x, p2y, p2z, p3x, p3y, p3z, norm.x, norm.y, norm.z, 0.003 * lineWidthScale, power, 0.95F, 0.98F, 1.0F);
                            }
                        }
                    }
                    cIdx++;
                }
            }
        } else {
            // Shattered: render borders with flying fragments
            int sIdx = 0;
            double lineWidthScale = fpView.phase2WidthScale();
            for (GeodesicDomeData.Cell cell : GeodesicDomeData.CELLS) {
                Vec3 norm = cell.center();
                Vec3[] verts = cell.vertices();
                int vCount = verts.length;

                float shatter = 1.0F - fade;
                double shScale = shatter * (3.0 + Math.sin(sIdx * 2.3) * 2.2);
                double shX = norm.x * shScale;
                double shY = norm.y * shScale + shatter * 2.5;
                double shZ = norm.z * shScale;
                double cx = norm.x * scaledRadius + shX;
                double cy = norm.y * scaledRadius + shY;
                double cz = norm.z * scaledRadius + shZ;

                float linePower = fade * grow * 0.85F * fpView.factor(cx, cy, cz, 0.35F, false);
                if (linePower <= 0.01F) {
                    sIdx++;
                    continue;
                }
                for (int k = 0; k < vCount; k++) {
                    Vec3 vp = verts[k];
                    curX[k] = vp.x * scaledRadius + shX;
                    curY[k] = vp.y * scaledRadius + shY;
                    curZ[k] = vp.z * scaledRadius + shZ;
                }

                for (int k = 0; k < vCount; k++) {
                    int next = (k + 1) % vCount;
                    line(lines, m, curX[k], curY[k], curZ[k], curX[next], curY[next], curZ[next],
                            norm.x, norm.y, norm.z, 0.006 * lineWidthScale, linePower * 0.85F, 0.20F, 0.95F, 1.0F);
                }
                sIdx++;
            }
        }
        flush(buffers, ZoltraakRenderTypes.LIGHT);
    }

    private void renderElectricArc(Matrix4f m, MultiBufferSource buffers, double radius, float age, float fade, FirstPersonView fpView) {
        VertexConsumer lines = buffers.getBuffer(ZoltraakRenderTypes.LIGHT);

        // Sweeping Electric Arc Ribbon (smooth surface-tangent orientation + tapered tips)
        double arcAngleBase = age * 0.04;
        int arcSteps = 48;
        double arcRad = radius + 0.025;
        for (int i = 0; i < arcSteps; i++) {
            double frac1 = (double) i / arcSteps;
            double frac2 = (double) (i + 1) / arcSteps;
            float arcWeight = (float) Math.pow(Math.sin(((frac1 + frac2) * 0.5) * Math.PI), 1.5);
            if (arcWeight <= 0.01F) continue;

            double phi1 = Math.toRadians(-40.0 + 80.0 * Math.sin(frac1 * Math.PI));
            double phi2 = Math.toRadians(-40.0 + 80.0 * Math.sin(frac2 * Math.PI));

            double th1 = arcAngleBase + frac1 * Math.PI * 0.75;
            double th2 = arcAngleBase + frac2 * Math.PI * 0.75;

            double arcR1 = Math.cos(phi1) * arcRad;
            double arcY1 = Math.sin(phi1) * arcRad;
            double arcR2 = Math.cos(phi2) * arcRad;
            double arcY2 = Math.sin(phi2) * arcRad;

            double x1 = Math.cos(th1) * arcR1, z1 = Math.sin(th1) * arcR1;
            double x2 = Math.cos(th2) * arcR2, z2 = Math.sin(th2) * arcR2;

            float arcAlpha = arcWeight * fade * 0.62F * fpView.factor((x1 + x2) * 0.5, (arcY1 + arcY2) * 0.5, (z1 + z2) * 0.5, 0.10F, false);
            if (arcAlpha <= 0.01F) continue;

            double dx = x2 - x1, dy = arcY2 - arcY1, dz = z2 - z1;
            double tx = dy * z1 - dz * arcY1;
            double ty = dz * x1 - dx * z1;
            double tz = dx * arcY1 - dy * x1;
            double tLenSqr = tx * tx + ty * ty + tz * tz;
            double rx, ry, rz;
            if (tLenSqr > 1.0E-8) {
                double invT = 1.0 / Math.sqrt(tLenSqr);
                rx = tx * invT;
                ry = ty * invT;
                rz = tz * invT;
            } else {
                double invR = 1.0 / Math.max(arcRad, 1.0E-4);
                rx = x1 * invR;
                ry = arcY1 * invR;
                rz = z1 * invR;
            }

            double arcWidthScale = arcWeight * fpView.phase2WidthScale();
            line(lines, m, x1, arcY1, z1, x2, arcY2, z2, rx, ry, rz, 0.024 * arcWidthScale, arcAlpha * 0.28F, 0.0F, 0.85F, 1.0F);
            line(lines, m, x1, arcY1, z1, x2, arcY2, z2, rx, ry, rz, 0.008 * arcWidthScale, arcAlpha, 0.7F, 0.98F, 1.0F);
        }

        flush(buffers, ZoltraakRenderTypes.LIGHT);
    }

    // =========================================================================
    // 2. DIRECTIONAL AEGIS SHIELD (MODE_DIRECTIONAL)
    // =========================================================================

    private void renderDirectional(DefenseBarrierEntity entity, Matrix4f m, MultiBufferSource buffers,
                                   float age, float fade, float life, float partial,
                                   DefenseBarrierEntity.VisualPose pose, Vec3 hit,
                                   Vec3 u, Vec3 v, Vec3 normal,
                                   Vec3 camRight, Vec3 camUp, FirstPersonView fpView) {
        float deploy = Mth.clamp(age / 6.0F, 0.0F, 1.0F);
        float grow = 0.2F + 0.8F * (float) Math.sin(deploy * Math.PI * 0.5);
        double size = DefenseBarrierEntity.CELL_RADIUS * grow;
        double insetSize = size * 0.88;
        float hitAgeVal = entity.hitAge(partial);
        double hitExpSurface = Math.exp(-hitAgeVal * 0.35);
        double hitExpEdge = Math.exp(-hitAgeVal * 0.28);

        // --- Pass 1: Inset Hexagonal Plate Faces & Bevel Facets (DEFENSE_SURFACE) ---
        VertexConsumer surface = buffers.getBuffer(ZoltraakRenderTypes.DEFENSE_SURFACE);
        int index = 0;
        for (Vec3 cell : DefenseBarrierEntity.cells()) {
            double cx = u.x * cell.x * grow + v.x * cell.y * grow;
            double cy = u.y * cell.x * grow + v.y * cell.y * grow;
            double cz = u.z * cell.x * grow + v.z * cell.y * grow;

            if (life == 0.0F) {
                float shatterTime = 1.0F - fade;
                double shU = cell.x * shatterTime * 2.5;
                double shV = cell.y * shatterTime * 2.5;
                double shN = Math.sin(index * 2.4) * shatterTime * 3.0;
                cx += u.x * shU + v.x * shV + normal.x * shN;
                cy += u.y * shU + v.y * shV + normal.y * shN;
                cz += u.z * shU + v.z * shV + normal.z * shN;
            }

            double hdx = cx - hit.x, hdy = cy - hit.y, hdz = cz - hit.z;
            double distToHit = hdx * hdx + hdy * hdy + hdz * hdz;
            double hitGlow = hitExpSurface * Math.exp(-distToHit * 0.55);

            boolean isPulse = (index + (int) (age * 0.25)) % 5 == 0;
            float pulse = isPulse ? 0.35F : 0.0F;

            float r = (float) Mth.clamp(0.04F + 0.85F * hitGlow + 0.15F * pulse, 0.0F, 1.0F);
            float g = (float) Mth.clamp(0.18F + 0.55F * hitGlow + 0.3F * pulse, 0.0F, 1.0F);
            float b = 1.0F;
            float cellAttn = fpView.shouldRenderDirectionalCell(cell, (float) hitGlow)
                    ? fpView.factor(cx, cy, cz, (float) hitGlow, true)
                    : 0.0F;
            float alpha = (float) Mth.clamp((0.38F + 0.45F * hitGlow + 0.25F * pulse) * fade * grow * cellAttn, 0.0F, 1.0F);
            if (alpha > 0.004F) {
                float bevelAlpha = alpha * 0.85F;
                float br = r * 0.8F;
                float bg = g * 0.85F;
                for (int k = 0; k < 6; k++) {
                    double c0 = HEX_COS[k], s0 = HEX_SIN[k];
                    double c1 = HEX_COS[k + 1], s1 = HEX_SIN[k + 1];
                    double r0x = u.x * c0 + v.x * s0, r0y = u.y * c0 + v.y * s0, r0z = u.z * c0 + v.z * s0;
                    double r1x = u.x * c1 + v.x * s1, r1y = u.y * c1 + v.y * s1, r1z = u.z * c1 + v.z * s1;

                    double ip1x = cx + r0x * insetSize, ip1y = cy + r0y * insetSize, ip1z = cz + r0z * insetSize;
                    double ip2x = cx + r1x * insetSize, ip2y = cy + r1y * insetSize, ip2z = cz + r1z * insetSize;
                    double p1x = cx + r0x * size, p1y = cy + r0y * size, p1z = cz + r0z * size;
                    double p2x = cx + r1x * size, p2y = cy + r1y * size, p2z = cz + r1z * size;

                    // Inset plate face
                    tri(surface, m, cx, cy, cz, ip1x, ip1y, ip1z, ip2x, ip2y, ip2z, r, g, b, alpha);
                    // Bevel facet
                    tri(surface, m, p1x, p1y, p1z, p2x, p2y, p2z, ip2x, ip2y, ip2z, br, bg, b, bevelAlpha);
                    tri(surface, m, p1x, p1y, p1z, ip2x, ip2y, ip2z, ip1x, ip1y, ip1z, br, bg, b, bevelAlpha);
                }
            }

            index++;
        }
        flush(buffers, ZoltraakRenderTypes.DEFENSE_SURFACE);

        // --- Pass 2: Neon Borders & Stress Cracks (LIGHT) ---
        VertexConsumer lines = buffers.getBuffer(ZoltraakRenderTypes.LIGHT);
        index = 0;
        double lineWidthScale = fpView.phase2WidthScale();
        for (Vec3 cell : DefenseBarrierEntity.cells()) {
            double cx = u.x * cell.x * grow + v.x * cell.y * grow;
            double cy = u.y * cell.x * grow + v.y * cell.y * grow;
            double cz = u.z * cell.x * grow + v.z * cell.y * grow;

            if (life == 0.0F) {
                float shatterTime = 1.0F - fade;
                double shU = cell.x * shatterTime * 2.5;
                double shV = cell.y * shatterTime * 2.5;
                double shN = Math.sin(index * 2.4) * shatterTime * 3.0;
                cx += u.x * shU + v.x * shV + normal.x * shN;
                cy += u.y * shU + v.y * shV + normal.y * shN;
                cz += u.z * shU + v.z * shV + normal.z * shN;
            }

            double hdx = cx - hit.x, hdy = cy - hit.y, hdz = cz - hit.z;
            double distToHit = hdx * hdx + hdy * hdy + hdz * hdz;
            float flash = (float) (hitExpEdge * Math.exp(-distToHit * 0.40));
            float cellAttn = fpView.shouldRenderDirectionalCell(cell, flash)
                    ? fpView.factor(cx, cy, cz, flash, false)
                    : 0.0F;
            float power = fade * grow * (0.85F + 0.5F * flash) * cellAttn;
            if (power > 0.01F) {
                for (int k = 0; k < 6; k++) {
                    double c0 = HEX_COS[k] * size, s0 = HEX_SIN[k] * size;
                    double c1 = HEX_COS[k + 1] * size, s1 = HEX_SIN[k + 1] * size;
                    double p1x = cx + u.x * c0 + v.x * s0, p1y = cy + u.y * c0 + v.y * s0, p1z = cz + u.z * c0 + v.z * s0;
                    double p2x = cx + u.x * c1 + v.x * s1, p2y = cy + u.y * c1 + v.y * s1, p2z = cz + u.z * c1 + v.z * s1;
                    line(lines, m, p1x, p1y, p1z, p2x, p2y, p2z, normal.x, normal.y, normal.z, 0.014 * lineWidthScale, power * 0.28F, 0.0F, 0.85F, 1.0F);
                    line(lines, m, p1x, p1y, p1z, p2x, p2y, p2z, normal.x, normal.y, normal.z, 0.006 * lineWidthScale, power * 0.90F, 0.22F, 0.95F, 1.0F);
                    line(lines, m, p1x, p1y, p1z, p2x, p2y, p2z, normal.x, normal.y, normal.z, 0.0025 * lineWidthScale, power * 1.0F, 0.85F, 0.98F, 1.0F);
                }

                if (life < 0.4F && index % 3 == 0) {
                    double p1x = cx - u.x * (size * 0.5), p1y = cy - u.y * (size * 0.5), p1z = cz - u.z * (size * 0.5);
                    double p2x = cx + v.x * (size * 0.17), p2y = cy + v.y * (size * 0.17), p2z = cz + v.z * (size * 0.17);
                    double p3x = cx + u.x * (size * 0.3) - v.x * (size * 0.55);
                    double p3y = cy + u.y * (size * 0.3) - v.y * (size * 0.55);
                    double p3z = cz + u.z * (size * 0.3) - v.z * (size * 0.55);
                    line(lines, m, p1x, p1y, p1z, p2x, p2y, p2z, normal.x, normal.y, normal.z, 0.005 * lineWidthScale, power * 0.85F, 0.95F, 0.98F, 1.0F);
                    line(lines, m, p2x, p2y, p2z, p3x, p3y, p3z, normal.x, normal.y, normal.z, 0.004 * lineWidthScale, power * 0.85F, 0.95F, 0.98F, 1.0F);
                }
            }

            index++;
        }
        flush(buffers, ZoltraakRenderTypes.LIGHT);

        // Pass 3: Impact flash and sparks
        float impact = entity.hitAge(partial);
        if (impact < 14.0F) {
            float impactProgress = Mth.clamp(impact / 14.0F, 0.0F, 1.0F);
            float impactFade = (float) Math.exp(-impact * 0.28) * fade * 0.95F;
            double sparkSize = 0.85 + impact * 0.15;

            VertexConsumer impactConsumer = buffers.getBuffer(ZoltraakRenderTypes.ZOL_IMPACT);
            Vec3 sparkCenter = hit.add(normal.scale(0.10));

            // Impact flash and sparks
            quadImpact(impactConsumer, m, sparkCenter, camRight.scale(sparkSize), camUp.scale(sparkSize),
                    impactProgress, false, false, impactFade);
            flush(buffers, ZoltraakRenderTypes.ZOL_IMPACT);
        }

        // Pass 4: Shatter burst
        if (life == 0.0F && fade > 0.03F) {
            float shatterProgress = Mth.clamp(1.0F - fade, 0.0F, 1.0F);
            float shatterFade = fade * 0.90F;
            double centerBurst = 2.0 + shatterProgress * 1.5;

            VertexConsumer impactConsumer = buffers.getBuffer(ZoltraakRenderTypes.ZOL_IMPACT);
            // Rupture at aegis center
            quadImpact(impactConsumer, m, Vec3.ZERO, camRight.scale(centerBurst), camUp.scale(centerBurst),
                    shatterProgress, false, false, shatterFade);
            // Rupture at breach point
            if (hit.lengthSqr() > 1.0E-4) {
                double breachSize = 1.4 + shatterProgress * 1.1;
                quadImpact(impactConsumer, m, hit, camRight.scale(breachSize), camUp.scale(breachSize),
                        shatterProgress, false, false, shatterFade * 0.85F);
            }
            flush(buffers, ZoltraakRenderTypes.ZOL_IMPACT);
        }
    }

    // =========================================================================
    // HELPER PRIMITIVES
    // =========================================================================

    private static Vec3 radial(Vec3 u, Vec3 v, int k, double size) {
        double c = HEX_COS[k] * size;
        double s = HEX_SIN[k] * size;
        return new Vec3(u.x * c + v.x * s, u.y * c + v.y * s, u.z * c + v.z * s);
    }

    private static void line(VertexConsumer b, Matrix4f m, Vec3 a, Vec3 c, Vec3 normal, double width, float alpha, float r, float g, float blue) {
        line(b, m, a.x, a.y, a.z, c.x, c.y, c.z, normal.x, normal.y, normal.z, width, alpha, r, g, blue);
    }

    private static void line(VertexConsumer b, Matrix4f m,
                             double ax, double ay, double az,
                             double cx, double cy, double cz,
                             double nx, double ny, double nz,
                             double width, float alpha, float r, float g, float blue) {
        double dx = cx - ax, dy = cy - ay, dz = cz - az;
        double sx = dy * nz - dz * ny;
        double sy = dz * nx - dx * nz;
        double sz = dx * ny - dy * nx;
        double lenSqr = sx * sx + sy * sy + sz * sz;
        if (lenSqr < 1.0E-12) {
            return;
        }
        double scale = width / Math.sqrt(lenSqr);
        sx *= scale;
        sy *= scale;
        sz *= scale;
        tri(b, m, ax - sx, ay - sy, az - sz, ax + sx, ay + sy, az + sz, cx + sx, cy + sy, cz + sz, r, g, blue, alpha);
        tri(b, m, ax - sx, ay - sy, az - sz, cx + sx, cy + sy, cz + sz, cx - sx, cy - sy, cz - sz, r, g, blue, alpha);
    }

    private static void tri(VertexConsumer b, Matrix4f m, Vec3 a, Vec3 c, Vec3 d, float r, float g, float blue, float alpha) {
        tri(b, m, a.x, a.y, a.z, c.x, c.y, c.z, d.x, d.y, d.z, r, g, blue, alpha);
    }

    private static void tri(VertexConsumer b, Matrix4f m,
                            double ax, double ay, double az,
                            double cx, double cy, double cz,
                            double dx, double dy, double dz,
                            float r, float g, float blue, float alpha) {
        float rc = Mth.clamp(r, 0.0F, 1.0F);
        float gc = Mth.clamp(g, 0.0F, 1.0F);
        float bc = Mth.clamp(blue, 0.0F, 1.0F);
        float ac = Mth.clamp(alpha, 0.0F, 1.0F);
        b.addVertex(m, (float) ax, (float) ay, (float) az).setColor(rc, gc, bc, ac);
        b.addVertex(m, (float) cx, (float) cy, (float) cz).setColor(rc, gc, bc, ac);
        b.addVertex(m, (float) dx, (float) dy, (float) dz).setColor(rc, gc, bc, ac);
    }

    private static void quadImpact(VertexConsumer b, Matrix4f m, Vec3 c, Vec3 u, Vec3 v,
                                   float progress, boolean isBlack, boolean isSurface, float alpha) {
        float r = progress;
        float g = isBlack ? 1.0F : 0.0F;
        float bl = isSurface ? 1.0F : 0.0F;

        b.addVertex(m, (float)(c.x - u.x - v.x), (float)(c.y - u.y - v.y), (float)(c.z - u.z - v.z))
                .setUv(0.0F, 1.0F).setColor(r, g, bl, alpha);
        b.addVertex(m, (float)(c.x + u.x - v.x), (float)(c.y + u.y - v.y), (float)(c.z + u.z - v.z))
                .setUv(1.0F, 1.0F).setColor(r, g, bl, alpha);
        b.addVertex(m, (float)(c.x + u.x + v.x), (float)(c.y + u.y + v.y), (float)(c.z + u.z + v.z))
                .setUv(1.0F, 0.0F).setColor(r, g, bl, alpha);
        b.addVertex(m, (float)(c.x - u.x + v.x), (float)(c.y - u.y + v.y), (float)(c.z - u.z + v.z))
                .setUv(0.0F, 0.0F).setColor(r, g, bl, alpha);
    }

    private static void flush(MultiBufferSource b, RenderType t) {
        float bloomScale = ZoltraakCinematicClientConfig.getBarrierGlow();
        float minAlphaFloor = (t == ZoltraakRenderTypes.DEFENSE_SURFACE) ? 0.22F : 0.0F;
        ZoltraakRenderPass.endBatchWithBloom(b, t, bloomScale, minAlphaFloor);
    }
}
