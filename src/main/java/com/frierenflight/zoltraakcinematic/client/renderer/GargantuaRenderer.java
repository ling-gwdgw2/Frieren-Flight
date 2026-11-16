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
        // Safeguard registration for the level post pass
        GargantuaPostProcessor.registerClientInstance(entity);

        // Defer Iris glow proxy to AFTER_WEATHER so sky and water stay behind bloom
        if (ShaderCompatibility.useFullDetailPass()
                && !ShaderCompatibility.isShadowPass()
                && ZoltraakCinematicClientConfig.isWorldGlowActive(true)) {
            PoseStack copy = new PoseStack();
            copy.last().pose().set(poseStack.last().pose());
            copy.last().normal().set(poseStack.last().normal());
            DEFERRED_IRIS_DRAWS.add(new DeferredGargantuaIrisDraw(
                    this,
                    entity,
                    partialTick,
                    copy,
                    new Matrix4f(RenderSystem.getModelViewMatrix()),
                    new Matrix4f(RenderSystem.getProjectionMatrix())
            ));
        }
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

        VertexConsumer vc = buffer.getBuffer(ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
        Matrix4f mat = poseStack.last().pose();

        float vis = Mth.clamp(open * brightness, 0.0f, 1.0f);
        float gg = ZoltraakRenderPass.irisFadeGuard(vis);

        // 1. Equatorial Accretion Ring (warm outer bloom band + incandescent inner core, shadow clear at > 1.35 * rg)
        ZoltraakRenderPass.emitFeatheredRing(
                vc, mat, c, diskU, diskV,
                rg * 1.35 * open, rg * 2.75 * open, rg * 5.20 * open, 40,
                1.00f * gg, 0.62f * gg, 0.20f * gg, Mth.clamp(vis * 0.86f, 0.0f, 0.94f)
        );
        ZoltraakRenderPass.emitFeatheredRing(
                vc, mat, c, diskU, diskV,
                rg * 1.35 * open, rg * 2.05 * open, rg * 3.25 * open, 40,
                1.00f * gg, 0.84f * gg, 0.46f * gg, Mth.clamp(vis * 0.92f, 0.0f, 0.96f)
        );

        // 2. Camera-aligned Einstein Photon Ring (circumscribing the Schwarzschild shadow cleanly without blocking it)
        Minecraft mc = Minecraft.getInstance();
        Vec3 camRight = new Vec3(new Vector3f(1.0f, 0.0f, 0.0f).rotate(mc.getEntityRenderDispatcher().cameraOrientation()));
        Vec3 camUp = new Vec3(new Vector3f(0.0f, 1.0f, 0.0f).rotate(mc.getEntityRenderDispatcher().cameraOrientation()));
        ZoltraakRenderPass.emitFeatheredRing(
                vc, mat, c, camRight, camUp,
                rg * 1.08 * open, rg * 1.52 * open, rg * 2.45 * open, 40,
                1.00f * gg, 0.78f * gg, 0.44f * gg, Mth.clamp(vis * 0.88f, 0.0f, 0.95f)
        );
        ZoltraakRenderPass.emitFeatheredRing(
                vc, mat, c, camRight, camUp,
                rg * 1.06 * open, rg * 1.32 * open, rg * 1.72 * open, 40,
                1.00f * gg, 0.94f * gg, 0.76f * gg, Mth.clamp(vis * 0.84f, 0.0f, 0.92f)
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
