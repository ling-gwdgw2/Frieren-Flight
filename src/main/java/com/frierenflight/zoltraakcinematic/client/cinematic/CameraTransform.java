// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.cinematic;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable cinematic camera transform storing world position, focal anchor,
 * Euler orientation (degrees), field of view, and Dutch roll angle.
 */
public record CameraTransform(
        Vec3 position,
        Vec3 focalPoint,
        float yawDeg,
        float pitchDeg,
        float fovDeg,
        float rollDeg
) {

    /** Constructs a transform positioned at {@code position} aimed directly at {@code lookTarget}. */
    public static CameraTransform aimAt(Vec3 position, Vec3 lookTarget, float fovDeg, float rollDeg) {
        Vec3 delta = lookTarget.subtract(position);
        double horiz = Math.hypot(delta.x, delta.z);
        float yaw = horiz > 1.0E-6 || Math.abs(delta.x) > 1.0E-6 || Math.abs(delta.z) > 1.0E-6
                ? (float) Math.toDegrees(Math.atan2(-delta.x, delta.z))
                : 0.0f;
        float pitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(delta.y, Math.max(horiz, 1.0E-5))), -89.9, 89.9);
        return new CameraTransform(position, lookTarget, Mth.wrapDegrees(yaw), pitch, fovDeg, rollDeg);
    }

    /** Constructs a transform directly from Minecraft camera Euler angles. */
    public static CameraTransform fromAngles(Vec3 position, float yawDeg, float pitchDeg, float fovDeg, float rollDeg) {
        double radYaw = Math.toRadians(yawDeg);
        double radPitch = Math.toRadians(pitchDeg);
        Vec3 forward = new Vec3(
                -Math.sin(radYaw) * Math.cos(radPitch),
                -Math.sin(radPitch),
                Math.cos(radYaw) * Math.cos(radPitch)
        );
        return new CameraTransform(position, position.add(forward.scale(16.0)), yawDeg, pitchDeg, fovDeg, rollDeg);
    }

    /** Returns a copy positioned at {@code adjustedPos}, re-aiming at {@code focalPoint} so clipped cameras stay centered on target. */
    public CameraTransform withPosition(Vec3 adjustedPos) {
        if (adjustedPos.distanceToSqr(this.position) <= 1.0E-5 || adjustedPos.distanceToSqr(this.focalPoint) <= 0.04) {
            return new CameraTransform(adjustedPos, focalPoint, yawDeg, pitchDeg, fovDeg, rollDeg);
        }
        return aimAt(adjustedPos, focalPoint, fovDeg, rollDeg);
    }

    /** Smoothly interpolates toward {@code target} along shortest angular yaw/pitch arcs. */
    public CameraTransform blendTo(CameraTransform target, float alpha) {
        float t = Mth.clamp(alpha, 0.0f, 1.0f);
        return new CameraTransform(
                this.position.lerp(target.position, t),
                this.focalPoint.lerp(target.focalPoint, t),
                Mth.rotLerp(t, this.yawDeg, target.yawDeg),
                Mth.rotLerp(t, this.pitchDeg, target.pitchDeg),
                Mth.lerp(t, this.fovDeg, target.fovDeg),
                Mth.lerp(t, this.rollDeg, target.rollDeg)
        );
    }
}
