// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.cinematic;

import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Global client-side playback and camera takeover controller for spell camera tracks.
 */
public final class CinematicRigController {

    private static boolean runtimeEnabled = true;
    private static CameraTrack activeTrack = null;
    private static CameraTrack queuedTrack = null;

    private static float lastWeight = 0.0f;
    private static float lastRollDeg = 0.0f;
    private static float lastFovDeg = 70.0f;

    private CinematicRigController() {}

    public static boolean isEnabled() {
        return runtimeEnabled && ZoltraakCinematicClientConfig.isCutsceneCameraEnabled();
    }

    public static void setEnabled(boolean value) {
        runtimeEnabled = value;
    }

    public static boolean isPlaying() {
        return isEnabled() && activeTrack != null && !activeTrack.isComplete();
    }

    public static float rigWeight() {
        return isPlaying() ? lastWeight : 0.0f;
    }

    public static float rollDegrees() {
        return isPlaying() ? lastRollDeg * lastWeight : 0.0f;
    }

    public static float blendFov(float vanillaFov) {
        if (!isPlaying()) {
            return vanillaFov;
        }
        return Mth.lerp(lastWeight, vanillaFov, lastFovDeg);
    }

    /** Submits a new camera track according to priority arbitration rules. */
    public static void submit(CameraTrack track) {
        if (!isEnabled() || track == null) {
            return;
        }
        if (activeTrack != null && !activeTrack.isComplete()) {
            if (track.priority() > activeTrack.priority()) {
                activeTrack = track;
                queuedTrack = null;
                lastWeight = 0.0f;
            } else if (track.priority() == activeTrack.priority()) {
                return;
            } else if (queuedTrack == null || track.priority() > queuedTrack.priority()) {
                queuedTrack = track;
            }
            return;
        }
        activeTrack = track;
        lastWeight = 0.0f;
    }

    public static void cancelActive() {
        if (activeTrack != null) {
            activeTrack.requestSkip();
        }
    }

    public static void reset() {
        activeTrack = null;
        queuedTrack = null;
        lastWeight = 0.0f;
        lastRollDeg = 0.0f;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        if (activeTrack != null) {
            activeTrack.advanceTick();
            if (activeTrack.isComplete()) {
                activeTrack = queuedTrack;
                queuedTrack = null;
                if (activeTrack == null) {
                    lastWeight = 0.0f;
                    lastRollDeg = 0.0f;
                }
            }
        } else if (queuedTrack != null) {
            activeTrack = queuedTrack;
            queuedTrack = null;
            activeTrack.advanceTick();
        } else {
            lastWeight = 0.0f;
            lastRollDeg = 0.0f;
        }
    }

    /** Evaluates the current blended, collision-cleared camera transform for this frame, or null when idle. */
    public static CameraTransform evaluateFrame(Vec3 playerCamPos, float playerYaw, float playerPitch, float partialTick) {
        if (!isEnabled() || activeTrack == null || activeTrack.isComplete()) {
            lastWeight = Math.max(0.0f, lastWeight - 0.1f);
            return null;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return null;
        }

        float weight = activeTrack.computeWeight(partialTick);
        lastWeight = weight;
        if (weight <= 0.001f) {
            return null;
        }

        CameraTransform target = activeTrack.evaluateTransform(partialTick);
        if (target == null) {
            return null;
        }

        Vec3 safePos = CameraObstacleSolver.solve(mc, target.position(), target.focalPoint());
        target = target.withPosition(safePos);

        lastRollDeg = target.rollDeg();
        lastFovDeg = target.fovDeg();

        CameraTransform base = CameraTransform.fromAngles(playerCamPos, playerYaw, playerPitch, target.fovDeg(), 0.0f);
        return base.blendTo(target, weight);
    }
}
