// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.ZoltraakCinematicMod;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import com.frierenflight.zoltraakcinematic.entity.GargantuaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Entity renderer for Gargantua black holes.
 * Main visuals run in {@link GargantuaPostProcessor}; emits a lightweight glow proxy when Iris/Oculus is active.
 */
public class GargantuaRenderer extends EntityRenderer<GargantuaEntity> {

    private static final ResourceLocation DUMMY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ZoltraakCinematicMod.MODID, "textures/effect/noise_tile.png");

    private record DeferredGargantuaIrisDraw(
            GargantuaRenderer renderer,
            GargantuaEntity entity,
            float partialTick,
            PoseStack pose,
            Matrix4f modelView,
            Matrix4f projection
    ) {}

    private static final List<DeferredGargantuaIrisDraw> DEFERRED_IRIS_DRAWS = new ArrayList<>();

    public static boolean hasDeferredIrisDraw() {
        return !DEFERRED_IRIS_DRAWS.isEmpty();
    }

    public static void clearDeferredIris() {
        DEFERRED_IRIS_DRAWS.clear();
    }

    public static void renderIrisPhase1(MultiBufferSource.BufferSource bufferSource, Matrix4fStack modelViewStack) {
        if (DEFERRED_IRIS_DRAWS.isEmpty()) return;
        float bloomScale = ZoltraakCinematicClientConfig.getGargantuaGlow();
        for (DeferredGargantuaIrisDraw draw : DEFERRED_IRIS_DRAWS) {
            if (draw.entity.isRemoved()) continue;
            modelViewStack.set(draw.modelView);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(draw.projection, VertexSorting.DISTANCE_TO_ORIGIN);
            ZoltraakRenderPass.renderIrisScaled(bufferSource, bloomScale,
                    () -> draw.renderer.emitIrisAccretionProxy(draw.entity, draw.partialTick, draw.pose, bufferSource));
        }
        DEFERRED_IRIS_DRAWS.clear();
    }

    public GargantuaRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(GargantuaEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Safeguard registration for the 4-pass RGBA16F post-processing pipeline (runs in both Vanilla and Iris/Oculus)
        GargantuaPostProcessor.registerClientInstance(entity);
    }

    private void emitIrisAccretionProxy(GargantuaEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer) {
        float rg = entity.gravitationalRadius(partialTick);
        float open = entity.opened(partialTick);
        float brightness = entity.brightness(partialTick);
        if (rg < 0.05f || open <= 0.01f || brightness <= 0.01f) {
            return;
        }

        Vec3 entPos = new Vec3(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ())
        );
        Vec3 c = entity.centre(partialTick).subtract(entPos);
        Vec3 axis = entity.spinAxis().normalize();
        Vec3 diskU = Math.abs(axis.y) < 0.99 ? axis.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : axis.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
        Vec3 diskV = axis.cross(diskU).normalize();

        Matrix4f mat = poseStack.last().pose();

        float vis = Mth.clamp(open * brightness, 0.0f, 1.0f);
        float gg = ZoltraakRenderPass.irisFadeGuard(vis);

        Minecraft mc = Minecraft.getInstance();
        Vec3 camRight = new Vec3(new Vector3f(1.0f, 0.0f, 0.0f).rotate(mc.getEntityRenderDispatcher().cameraOrientation()));
        Vec3 camUp = new Vec3(new Vector3f(0.0f, 1.0f, 0.0f).rotate(mc.getEntityRenderDispatcher().cameraOrientation()));

        // 0. Pitch-black central Event Horizon core disc to occlude background sky/terrain in Iris
        VertexConsumer vcBlack = buffer.getBuffer(ZoltraakRenderTypes.magicTranslucent(ZoltraakRenderTypes.TEX_WHITE));
        ZoltraakRenderPass.emitFeatheredRing(
                vcBlack, mat, c, camRight, camUp,
                0.0, rg * 1.48 * open, rg * 1.50 * open, 48,
                0.0f, 0.0f, 0.0f, 1.0f
        );
        ZoltraakRenderTypes.endIrisBatch(buffer, ZoltraakRenderTypes.magicTranslucent(ZoltraakRenderTypes.TEX_WHITE));

        // 1. Equatorial Accretion Ring & Tapered Champagne Knife-Blade Wings (outer arches clear at >= 1.85 * rg)
        VertexConsumer vc = buffer.getBuffer(ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
        Vec3 projWing = diskU.scale(diskU.dot(camRight)).add(diskV.scale(diskV.dot(camRight)));
        Vec3 wingDir = projWing.lengthSqr() > 1.0e-4 ? projWing.normalize() : camRight;
        double outerWingR = rg * 6.40 * open;
        double midWingR = rg * 3.40 * open;

        // Horizontal foreground champagne blade spine + warm honey outer wing ribbons for Iris HDR Bloom
        ZoltraakRenderPass.emitFeatheredBeamSegment(
                vc, mat,
                c.subtract(wingDir.scale(outerWingR)),
                c.add(wingDir.scale(outerWingR)),
                camUp,
                rg * 0.03 * open, rg * 0.14 * open,
                1.00f * gg, 0.90f * gg, 0.70f * gg, Mth.clamp(vis * 0.68f, 0.0f, 0.85f),
                0.96f * gg, 0.68f * gg, 0.34f * gg, Mth.clamp(vis * 0.34f, 0.0f, 0.55f)
        );
        ZoltraakRenderPass.emitFeatheredBeamSegment(
                vc, mat,
                c.subtract(wingDir.scale(midWingR)),
                c.add(wingDir.scale(midWingR)),
                camUp,
                rg * 0.02 * open, rg * 0.08 * open,
                1.00f * gg, 0.96f * gg, 0.86f * gg, Mth.clamp(vis * 0.84f, 0.0f, 0.92f),
                1.00f * gg, 0.82f * gg, 0.54f * gg, Mth.clamp(vis * 0.45f, 0.0f, 0.65f)
        );
        ZoltraakRenderPass.emitFeatheredRing(
                vc, mat, c, diskU, diskV,
                rg * 1.86 * open, rg * 3.25 * open, outerWingR, 48,
                1.00f * gg, 0.84f * gg, 0.56f * gg, Mth.clamp(vis * 0.58f, 0.0f, 0.78f)
        );

        // 2. Razor-thin n=2 Hairline Photon Sub-Ring & Primary Champagne Lensed Arches (dark annular gap preserved!)
        ZoltraakRenderPass.emitFeatheredRing(
                vc, mat, c, camRight, camUp,
                rg * 1.56 * open, rg * 1.63 * open, rg * 1.72 * open, 48,
                1.00f * gg, 0.78f * gg, 0.46f * gg, Mth.clamp(vis * 0.74f, 0.0f, 0.86f)
        );
        ZoltraakRenderPass.emitFeatheredRing(
                vc, mat, c, camRight, camUp,
                rg * 1.88 * open, rg * 2.55 * open, rg * 3.70 * open, 48,
                1.00f * gg, 0.88f * gg, 0.66f * gg, Mth.clamp(vis * 0.64f, 0.0f, 0.82f)
        );
        ZoltraakRenderPass.emitFeatheredRing(
                vc, mat, c, camRight, camUp,
                rg * 1.85 * open, rg * 2.22 * open, rg * 2.85 * open, 48,
                1.00f * gg, 0.95f * gg, 0.82f * gg, Mth.clamp(vis * 0.72f, 0.0f, 0.86f)
        );

        ZoltraakRenderTypes.endIrisBatch(buffer, ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
    }

    @Override
    public ResourceLocation getTextureLocation(GargantuaEntity entity) {
        return DUMMY_TEXTURE;
    }

    @Override
    public boolean shouldRender(GargantuaEntity entity, Frustum camera, double camX, double camY, double camZ) {
        return true;
    }
}
