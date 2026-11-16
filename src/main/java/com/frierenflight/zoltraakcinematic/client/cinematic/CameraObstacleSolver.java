// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.cinematic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Spatial collision and terrain clearance solver for cinematic camera positions.
 * Keeps outdoor shots above terrain heightmaps while raycasting enclosed cave/room shots
 * from the active subject so the camera never clips through roofs or walls.
 */
public final class CameraObstacleSolver {

    private static final double OUTDOOR_GROUND_MARGIN = 1.2;
    private static final double WALL_BACKOFF = 0.55;
    private static final double MIN_SAFE_DIST = 0.35;

    private CameraObstacleSolver() {}

    public static Vec3 solve(Minecraft mc, Vec3 desiredPos, Vec3 focalTarget) {
        ClientLevel level = mc.level;
        if (level == null) {
            return desiredPos;
        }

        Vec3 playerEye = mc.player != null ? mc.player.getEyePosition(1.0f) : focalTarget;
        boolean closeToPlayer = desiredPos.distanceToSqr(playerEye) <= 256.0;
        Vec3 rayOrigin = closeToPlayer ? playerEye : focalTarget;

        boolean openSky = isAboveMotionSurface(level, rayOrigin, 2.0)
                && isAboveMotionSurface(level, playerEye, 2.0);

        Vec3 resolved = desiredPos;
        if (openSky) {
            resolved = liftAboveSurface(level, resolved, OUTDOOR_GROUND_MARGIN);
        } else if (mc.player != null) {
            BlockHitResult hit = level.clip(new ClipContext(
                    rayOrigin, resolved,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player
            ));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 span = resolved.subtract(rayOrigin);
                double len = span.length();
                if (len > 1.0E-4) {
                    double safeLen = Math.max(MIN_SAFE_DIST, hit.getLocation().distanceTo(rayOrigin) - WALL_BACKOFF);
                    resolved = rayOrigin.add(span.scale(safeLen / len));
                }
            }
        }

        return escapeSolidBlock(level, resolved);
    }

    private static boolean isAboveMotionSurface(ClientLevel level, Vec3 point, double tolerance) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(point.x), Mth.floor(point.z));
        return point.y >= (double) surfaceY - tolerance;
    }

    private static Vec3 liftAboveSurface(ClientLevel level, Vec3 point, double margin) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(point.x), Mth.floor(point.z));
        if (surfaceY <= level.getMinBuildHeight() + 1) {
            return point;
        }
        double minY = (double) surfaceY + margin;
        return point.y < minY ? new Vec3(point.x, minY, point.z) : point;
    }

    private static Vec3 escapeSolidBlock(ClientLevel level, Vec3 pos) {
        if (!isOpaqueSolid(level, pos)) {
            return pos;
        }
        for (int dy = 1; dy <= 8; dy++) {
            Vec3 downCandidate = pos.subtract(0.0, dy, 0.0);
            if (!isOpaqueSolid(level, downCandidate)) {
                return downCandidate;
            }
            Vec3 upCandidate = pos.add(0.0, dy, 0.0);
            if (!isOpaqueSolid(level, upCandidate)) {
                return upCandidate;
            }
        }
        return pos;
    }

    private static boolean isOpaqueSolid(ClientLevel level, Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos);
        return level.getBlockState(blockPos).isSolidRender(level, blockPos);
    }
}
