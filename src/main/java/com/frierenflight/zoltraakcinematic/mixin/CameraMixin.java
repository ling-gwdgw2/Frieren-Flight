// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.mixin;

import com.frierenflight.zoltraakcinematic.client.NeoForgeClientRenderHooks;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lightweight, zero-@Shadow tail hook on Camera#setup for cutscene camera takeover.
 * Avoids declaring @Shadow fields/methods on Camera to prevent attribute/visibility
 * conflicts with Bosses'Rise and other camera mods.
 */
@Mixin(value = Camera.class)
public abstract class CameraMixin {

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void zoltraak$cinematicCamera(BlockGetter level, Entity entity, boolean detachedIn, boolean mirrored, float partialTick, CallbackInfo ci) {
        NeoForgeClientRenderHooks.applyCutsceneCamera((Camera) (Object) this, partialTick);
    }
}
