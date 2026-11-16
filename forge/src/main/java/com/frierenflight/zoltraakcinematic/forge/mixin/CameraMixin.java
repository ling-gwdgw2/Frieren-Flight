// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.forge.mixin;

import com.frierenflight.zoltraakcinematic.client.cinematic.CameraTransform;
import com.frierenflight.zoltraakcinematic.client.cinematic.CinematicRigController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class)
public abstract class CameraMixin {

    @Shadow
    private boolean detached;

    @Shadow
    private Vec3 position;

    @Shadow
    private float xRot;

    @Shadow
    private float yRot;

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void zoltraak$cinematicCamera(BlockGetter level, Entity entity, boolean detachedIn, boolean mirrored, float partialTick, CallbackInfo ci) {
        CameraTransform pose = CinematicRigController.evaluateFrame(this.position, this.yRot, this.xRot, partialTick);
        if (pose == null) {
            return;
        }

        this.detached = true;
        this.setRotation(pose.yawDeg(), pose.pitchDeg());
        this.setPosition(pose.position());
    }
}

