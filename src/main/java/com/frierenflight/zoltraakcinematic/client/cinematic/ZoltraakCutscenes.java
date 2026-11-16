// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.cinematic;

import com.frierenflight.zoltraakcinematic.entity.ZoltraakCinematicBeamEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Keyframe timeline choreography for Great Zoltraak (4 cinematic phases spanning 105 ticks).
 */
public final class ZoltraakCutscenes {

    private ZoltraakCutscenes() {}

    /** Builds and submits the 4-phase Great Zoltraak camera sequence to {@link CinematicRigController}. */
    public static void playGreatZoltraak(LivingEntity caster, ZoltraakCinematicBeamEntity beam) {
        CameraRigFrame rig = CameraRigFrame.custom(
                pt -> caster != null ? caster.getEyePosition(pt) : beam.position().add(0.0, 1.62, 0.0),
                pt -> caster != null ? caster.getPosition(pt) : beam.position(),
                pt -> {
                    Vec3 dir = beam.visualDirection(1.0f);
                    Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
                    return flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
                },
                pt -> beam.end()
        );
        CinematicRigController.submit(greatZoltraakTrack(rig));
    }

    public static CameraTrack greatZoltraakTrack(CameraRigFrame rig) {
        return CameraTrack.builder(rig, 15)
                .fadeEnvelope(2, 10)
                // Phase 1 (Ticks 0..20): Incantation Over-The-Shoulder
                .addCutPhase(20, (r, pt, k) -> {
                    Vec3 eye = r.eye(pt);
                    Vec3 camPos = r.offsetLocal(eye, pt, Mth.lerp(k, -1.8, -1.3), 0.35, Mth.lerp(k, 1.1, 0.85));
                    Vec3 lookTarget = r.target(pt).add(0.0, 1.2, 0.0);
                    return CameraTransform.aimAt(camPos, lookTarget, Mth.lerp(k, 70.0f, 56.0f), Mth.lerp(k, 0.0f, -1.5f));
                })
                // Phase 2 (Ticks 20..48): Instant-Cut Low-Angle Discharge Tracking
                .addCutPhase(28, (r, pt, k) -> {
                    Vec3 feet = r.feet(pt);
                    Vec3 camPos = r.offsetLocal(feet, pt, -2.2, -0.4, -1.2);
                    Vec3 toEnd = r.target(pt).subtract(feet);
                    Vec3 aimDir = toEnd.lengthSqr() > 0.001 ? toEnd.normalize() : r.flatForward(pt);
                    Vec3 lookTarget = feet.add(aimDir.scale(48.0));
                    return CameraTransform.aimAt(camPos, lookTarget, Mth.lerp(k, 82.0f, 76.0f), Mth.lerp(k, 2.0f, 0.5f));
                })
                // Phase 3 (Ticks 48..85): Orbital Impact & Plasma Plume Panorama
                .addBlendedPhase(37, 8, (r, pt, k) -> {
                    Vec3 impact = r.target(pt);
                    double angle = (double) k * 0.95 + 0.35;
                    double dist = Mth.lerp(k, 56.0, 72.0);
                    double height = Mth.lerp(k, 18.0, 28.0);
                    Vec3 camPos = impact.add(Math.cos(angle) * dist, height, Math.sin(angle) * dist);
                    return CameraTransform.aimAt(camPos, impact.add(0.0, 4.5, 0.0), 68.0f, -0.8f);
                })
                // Phase 4 (Ticks 85..105): High-Crane Pullback & Return
                .addBlendedPhase(20, 10, (r, pt, k) -> {
                    Vec3 eye = r.eye(pt);
                    Vec3 camPos = r.offsetLocal(eye, pt, -4.5, Mth.lerp(k, 2.5, 0.6), 0.0);
                    return CameraTransform.aimAt(camPos, r.target(pt), Mth.lerp(k, 68.0f, 70.0f), 0.0f);
                })
                .build();
    }
}
