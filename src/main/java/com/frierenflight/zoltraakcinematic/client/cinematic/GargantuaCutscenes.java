// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.cinematic;

import com.frierenflight.zoltraakcinematic.entity.GargantuaEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Five-act keyframe camera timeline choreography for the Gargantua black hole spell:
 * Act 0: Incantation Dolly Pull-Back
 * Act 1: Spacetime Rupture
 * Act 2: 288-Degree Accretion Orbit
 * Act 3: High-Altitude Vortex Ascent
 * Act 4: Smooth Handoff Back to Caster
 */
public final class GargantuaCutscenes {

    private GargantuaCutscenes() {}

    /** Plays Act 0 incantation dolly pullback while channeling Gargantua. */
    public static void playIncantation(LivingEntity caster, int castTicks) {
        Vec3 initialTarget = caster.getEyePosition().add(caster.getViewVector(1.0f).scale(64.0));
        CameraRigFrame rig = CameraRigFrame.tracking(caster, pt -> initialTarget);
        CinematicRigController.submit(incantationTrack(rig, castTicks));
    }

    /** Plays Acts 1 through 4 over the spawned Gargantua singularity lifetime (280 ticks). */
    public static void playSingularity(LivingEntity caster, GargantuaEntity singularity) {
        CameraRigFrame rig = CameraRigFrame.tracking(caster, singularity::centre);
        CinematicRigController.submit(singularityTrack(rig, singularity));
    }

    public static CameraTrack incantationTrack(CameraRigFrame rig, int castTicks) {
        return CameraTrack.builder(rig, 10)
                .fadeEnvelope(2, 10)
                .addCutPhase(Math.max(1, castTicks), (r, pt, k) -> {
                    Vec3 eye = r.eye(pt);
                    Vec3 forward = r.flatForward(pt);
                    Vec3 right = r.flatRight(pt);

                    double distance = Mth.lerp((double) k, 2.4, 5.2);
                    double heightOffset = Mth.lerp((double) k, 0.45, 0.95);
                    double sideOffset = Mth.lerp((double) k, 1.25, 1.65);

                    Vec3 camPos = eye.subtract(forward.scale(distance))
                            .add(right.scale(sideOffset))
                            .add(0.0, heightOffset, 0.0);
                    Vec3 focalPoint = eye.add(forward.scale(64.0)).add(0.0, 1.2, 0.0);
                    return CameraTransform.aimAt(camPos, focalPoint, Mth.lerp(k, 70.0f, 58.0f), Mth.lerp(k, 0.0f, -2.0f));
                })
                .build();
    }

    public static CameraTrack singularityTrack(CameraRigFrame rig, GargantuaEntity gargantua) {
        final double startAngle = 0.549;
        final double orbitTotalAngle = Math.PI * 1.6;
        final double act2EndAngle = startAngle + orbitTotalAngle;
        final double act3OrbitAngle = 0.35;
        final double act3EndAngle = act2EndAngle + act3OrbitAngle;

        return CameraTrack.builder(rig, 20)
                .fadeEnvelope(2, 10)
                // Act 1 (0..60): Spacetime Rupture & Opening Jolt
                .addCutPhase(60, (r, pt, k) -> {
                    Vec3 center = gargantua.centre(pt);
                    double dist = Mth.lerp((double) k, 75.0, 105.0);
                    double height = Mth.lerp((double) k, 10.0, 22.0);
                    Vec3 camPos = center.add(Math.cos(startAngle) * dist, height, Math.sin(startAngle) * dist);
                    float roll = (float) Math.sin(k * Math.PI) * -3.5f;
                    return CameraTransform.aimAt(camPos, center, Mth.lerp(k, 74.0f, 65.0f), roll);
                })
                // Act 2 (60..180): Grand Accretion Orbit
                .addBlendedPhase(120, 12, (r, pt, k) -> {
                    Vec3 center = gargantua.centre(pt);
                    double angle = startAngle + (double) k * orbitTotalAngle;
                    double dist = 105.0 + Math.sin(angle * 2.0) * 8.0;
                    double height = 22.0 + Math.cos(angle * 2.5) * 6.0;
                    Vec3 camPos = center.add(Math.cos(angle) * dist, height, Math.sin(angle) * dist);
                    float roll = (float) Math.sin(angle * 2.0) * 1.6f;
                    return CameraTransform.aimAt(camPos, center, 65.0f, roll);
                })
                // Act 3 (180..240): High-Altitude Vortex Ascent
                .addBlendedPhase(60, 8, (r, pt, k) -> {
                    Vec3 center = gargantua.centre(pt);
                    double dist = Mth.lerp((double) k, 105.0, 130.0);
                    double height = Mth.lerp((double) k, 22.0, 36.0);
                    double angle = act2EndAngle + (double) k * act3OrbitAngle;
                    Vec3 camPos = center.add(Math.cos(angle) * dist, height, Math.sin(angle) * dist);
                    return CameraTransform.aimAt(camPos, center, Mth.lerp(k, 65.0f, 72.0f), Mth.lerp(k, 1.2f, -1.0f));
                })
                // Act 4 (240..280): Seamless Release & Handoff Back to Caster
                .addBlendedPhase(40, 10, (r, pt, k) -> {
                    Vec3 center = gargantua.centre(pt);
                    Vec3 playerEye = r.eye(pt);
                    Vec3 cinematicPos = center.add(Math.cos(act3EndAngle) * 130.0, 36.0, Math.sin(act3EndAngle) * 130.0);
                    Vec3 camPos = cinematicPos.lerp(playerEye, (double) k * 0.75);
                    return CameraTransform.aimAt(camPos, center, Mth.lerp(k, 72.0f, 70.0f), 0.0f);
                })
                .build();
    }
}
