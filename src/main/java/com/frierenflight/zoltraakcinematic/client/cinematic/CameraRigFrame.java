// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.cinematic;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Spatial reference rig supplying interpolated caster eye/foot coordinates, horizontal look basis,
 * focus target, and easing utilities for timeline camera sequences.
 */
public final class CameraRigFrame {

    @FunctionalInterface
    public interface PointSampler {
        Vec3 sample(float partialTick);
    }

    private static final Vec3 WORLD_UP = new Vec3(0.0, 1.0, 0.0);

    private final PointSampler eyeSampler;
    private final PointSampler feetSampler;
    private final PointSampler forwardSampler;
    private final PointSampler targetSampler;

    private CameraRigFrame(
            PointSampler eyeSampler,
            PointSampler feetSampler,
            PointSampler forwardSampler,
            PointSampler targetSampler
    ) {
        this.eyeSampler = eyeSampler;
        this.feetSampler = feetSampler;
        this.forwardSampler = forwardSampler;
        this.targetSampler = targetSampler;
    }

    /** Builds a rig anchored to a casting entity and a dynamically sampled world focus point. */
    public static CameraRigFrame tracking(LivingEntity caster, PointSampler targetSampler) {
        return new CameraRigFrame(
                pt -> caster != null ? caster.getEyePosition(pt) : targetSampler.sample(pt).add(0.0, 1.62, 0.0),
                pt -> caster != null ? caster.getPosition(pt) : targetSampler.sample(pt),
                pt -> {
                    if (caster == null) return new Vec3(0.0, 0.0, 1.0);
                    Vec3 view = caster.getViewVector(pt);
                    Vec3 flat = new Vec3(view.x, 0.0, view.z);
                    return flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
                },
                targetSampler
        );
    }

    /** Builds a rig with explicit sampler lambdas for custom projectiles/beams. */
    public static CameraRigFrame custom(
            PointSampler eyeSampler,
            PointSampler feetSampler,
            PointSampler forwardSampler,
            PointSampler targetSampler
    ) {
        return new CameraRigFrame(eyeSampler, feetSampler, forwardSampler, targetSampler);
    }

    public Vec3 eye(float partialTick) {
        return eyeSampler.sample(partialTick);
    }

    public Vec3 feet(float partialTick) {
        return feetSampler.sample(partialTick);
    }

    public Vec3 flatForward(float partialTick) {
        Vec3 dir = forwardSampler.sample(partialTick);
        Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
    }

    public Vec3 flatRight(float partialTick) {
        return flatForward(partialTick).cross(WORLD_UP).normalize();
    }

    public Vec3 target(float partialTick) {
        return targetSampler.sample(partialTick);
    }

    /** Computes a world-space point offset from {@code anchor} in local (forward, up, right) axes. */
    public Vec3 offsetLocal(Vec3 anchor, float partialTick, double forwardDist, double upDist, double rightDist) {
        Vec3 fw = flatForward(partialTick);
        Vec3 rt = fw.cross(WORLD_UP);
        return anchor.add(
                fw.x * forwardDist + rt.x * rightDist,
                upDist,
                fw.z * forwardDist + rt.z * rightDist
        );
    }

    public static float smoothHermite(float x) {
        float c = Mth.clamp(x, 0.0f, 1.0f);
        return c * c * (3.0f - 2.0f * c);
    }
}
