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

    @Shadow(aliases = "f_90560_")
    private boolean detached;

    @Shadow(aliases = "f_90552_")
    private Vec3 position;

    @Shadow(aliases = "f_90557_")
    private float xRot;

    @Shadow(aliases = "f_90558_")
    private float yRot;

    @Shadow(aliases = "m_90581_")
    protected abstract void setPosition(Vec3 pos);

    @Shadow(aliases = "m_90572_")
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

