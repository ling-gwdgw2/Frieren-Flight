// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.forge.client;

import com.frierenflight.zoltraakcinematic.client.cinematic.CameraTransform;
import com.frierenflight.zoltraakcinematic.client.cinematic.CinematicRigController;
import com.frierenflight.zoltraakcinematic.client.renderer.GargantuaShake;
import com.frierenflight.zoltraakcinematic.client.renderer.ZoltraakRenderPass;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Pure Forge 1.20.1 client event hooks replacing fragile GameRenderer/Camera Mixins.
 * Eliminates mixin conflicts with OptiFine, Oculus, Embeddium, Vivecraft, Immersive Portals,
 * Epic Fight, FirstPersonModel, and Sinytra Connector.
 */
public final class ForgeClientRenderHooks {

    private static boolean reflectionInitialized = false;
    private static Field cameraDetachedField;
    private static Method cameraSetPositionMethod;
    private static Method cameraSetRotationMethod;

    private ForgeClientRenderHooks() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ForgeClientRenderHooks::onRenderTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ForgeClientRenderHooks::onRenderLevelStage);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ForgeClientRenderHooks::onComputeCameraAngles);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, ForgeClientRenderHooks::onComputeFov);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ForgeClientRenderHooks::onRenderHand);
    }

    private static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ZoltraakRenderPass.begin();
        }
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            // Fires immediately after LevelRenderer.renderLevel (and Oculus finalizeLevelRendering)
            // and before GargantuaPostProcessor/ExplosionPostProcessor's NORMAL-priority AFTER_LEVEL hooks.
            ZoltraakRenderPass.finish();
        }
    }

    private static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float partial = (float) event.getPartialTick();
        Camera camera = event.getCamera();

        // 1. Cutscene orbit camera override (replaces CameraMixin)
        if (camera != null && CinematicRigController.isPlaying()) {
            CameraTransform pose = CinematicRigController.evaluateFrame(
                    camera.getPosition(), camera.getYRot(), camera.getXRot(), partial
            );
            if (pose != null) {
                applyCutsceneCamera(camera, pose);
                event.setYaw(pose.yawDeg());
                event.setPitch(pose.pitchDeg());
            }
        }

        // 2. Cutscene roll & Gargantua harmonic screen shake (replaces GameRendererMixin#bobHurt)
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

    private static void applyCutsceneCamera(Camera camera, CameraTransform pose) {
        initCameraReflection();
        try {
            if (cameraDetachedField != null) {
                cameraDetachedField.setBoolean(camera, true);
            }
            if (cameraSetRotationMethod != null) {
                cameraSetRotationMethod.invoke(camera, pose.yawDeg(), pose.pitchDeg());
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
        cameraDetachedField = findField(Camera.class, "f_90560_", "detached");
        cameraSetPositionMethod = findMethod(Camera.class, new String[]{"m_90581_", "setPosition"}, Vec3.class);
        cameraSetRotationMethod = findMethod(Camera.class, new String[]{"m_90572_", "setRotation"}, float.class, float.class);
    }

    private static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field f = owner.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> owner, String[] names, Class<?>... params) {
        for (String name : names) {
            try {
                Method m = owner.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
