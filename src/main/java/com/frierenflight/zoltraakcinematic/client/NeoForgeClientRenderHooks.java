// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client;

import com.frierenflight.zoltraakcinematic.client.cinematic.CameraTransform;
import com.frierenflight.zoltraakcinematic.client.cinematic.CinematicRigController;
import com.frierenflight.zoltraakcinematic.client.renderer.GargantuaShake;
import com.frierenflight.zoltraakcinematic.client.renderer.ZoltraakRenderPass;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Pure NeoForge 1.21.1 client event hooks replacing GameRendererMixin and removing all
 * @Shadow declarations from CameraMixin for maximum compatibility with Bosses'Rise,
 * Iris, Sodium, Vivecraft, FirstPersonModel, Epic Fight, and other camera/render mods.
 */
public final class NeoForgeClientRenderHooks {

    private static boolean reflectionInitialized = false;
    private static Field cameraDetachedField;
    private static Method cameraSetPositionMethod;
    private static Method cameraSetRotation3Method;
    private static Method cameraSetRotation2Method;

    private NeoForgeClientRenderHooks() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, NeoForgeClientRenderHooks::onRenderFramePre);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, NeoForgeClientRenderHooks::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, NeoForgeClientRenderHooks::onComputeCameraAngles);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, NeoForgeClientRenderHooks::onComputeFov);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, NeoForgeClientRenderHooks::onRenderHand);
    }

    private static void onRenderFramePre(RenderFrameEvent.Pre event) {
        ZoltraakRenderPass.begin();
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            // Fires immediately after LevelRenderer.renderLevel (and Iris finalizeLevelRendering)
            // and before GargantuaPostProcessor/ExplosionPostProcessor's NORMAL-priority AFTER_LEVEL hooks.
            ZoltraakRenderPass.finish();
        }
    }

    private static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float partial = (float) event.getPartialTick();

        float cinematicRoll = CinematicRigController.rollDegrees();
        if (Math.abs(cinematicRoll) > 0.001f) {
            event.setRoll(event.getRoll() + cinematicRoll);
        }

        float shake = GargantuaShake.currentShake(partial);
        if (shake > 0.001f) {
            float t = (float) (System.nanoTime() % 100000000000L) / 1.0E9f;
            float yaw   = (float) (Math.sin(t * 37.0) * 0.6 + Math.sin(t * 61.0) * 0.4) * 2.6f * shake;
            float pitch = (float) (Math.sin(t * 43.0 + 1.3) * 0.6 + Math.sin(t * 71.0) * 0.4) * 2.2f * shake;
            float roll  = (float) Math.sin(t * 29.0 + 0.7) * 3.0f * shake;

            event.setRoll(event.getRoll() + roll);
            event.setPitch(event.getPitch() + pitch);
            event.setYaw(event.getYaw() + yaw);
        }
    }

    private static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov() && CinematicRigController.isPlaying()) {
            event.setFOV(CinematicRigController.blendFov((float) event.getFOV()));
        }
    }

    private static void onRenderHand(RenderHandEvent event) {
        if (CinematicRigController.isPlaying()) {
            event.setCanceled(true);
        }
    }

    /**
     * Applies cutscene camera takeover at the end of Camera.setup without requiring any
     * @Shadow fields or methods on Camera, avoiding visibility/attribute conflicts with
     * Bosses'Rise and other cinematic camera mods.
     */
    public static void applyCutsceneCamera(Camera camera, float partialTick) {
        if (camera == null || !CinematicRigController.isPlaying()) {
            return;
        }
        CameraTransform pose = CinematicRigController.evaluateFrame(
                camera.getPosition(), camera.getYRot(), camera.getXRot(), partialTick
        );
        if (pose == null) {
            return;
        }

        float yaw = pose.yawDeg();
        float pitch = pose.pitchDeg();
        float roll = camera.getRoll();

        float shake = GargantuaShake.currentShake(partialTick);
        if (shake > 0.001f) {
            float t = (float) (System.nanoTime() % 100000000000L) / 1.0E9f;
            yaw   += (float) (Math.sin(t * 37.0) * 0.6 + Math.sin(t * 61.0) * 0.4) * 2.6f * shake;
            pitch += (float) (Math.sin(t * 43.0 + 1.3) * 0.6 + Math.sin(t * 71.0) * 0.4) * 2.2f * shake;
        }

        initCameraReflection();
        try {
            if (cameraDetachedField != null) {
                cameraDetachedField.setBoolean(camera, true);
            }
            if (cameraSetRotation3Method != null) {
                cameraSetRotation3Method.invoke(camera, yaw, pitch, roll);
            } else if (cameraSetRotation2Method != null) {
                cameraSetRotation2Method.invoke(camera, yaw, pitch);
            }
            if (cameraSetPositionMethod != null) {
                cameraSetPositionMethod.invoke(camera, pose.position());
            }
        } catch (Throwable ignored) {
        }
    }

    private static void initCameraReflection() {
        if (reflectionInitialized) {
            return;
        }
        reflectionInitialized = true;
        cameraDetachedField = findField(Camera.class, "detached");
        cameraSetPositionMethod = findMethod(Camera.class, "setPosition", Vec3.class);
        cameraSetRotation3Method = findMethod(Camera.class, "setRotation", float.class, float.class, float.class);
        cameraSetRotation2Method = findMethod(Camera.class, "setRotation", float.class, float.class);
    }

    private static Field findField(Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            Method m = owner.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
