// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import com.frierenflight.zoltraakcinematic.entity.GargantuaEntity;
import com.frierenflight.zoltraakcinematic.item.GargantuaScrollItem;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicSpells;
import com.frierenflight.zoltraakcinematic.spell.GargantuaSpell;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.item.CastingItem;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

/**
 * Draws the 3D aiming ring and sphere preview before casting Gargantua.
 */
public final class GargantuaPreviewRenderer {

    private static int fadeTicks = 0;
    private static final int MAX_FADE_TICKS = 8;

    private static boolean wasCastingGargantua = false;
    private static float maxCastCompletionSeen = 0.0f;
    private static boolean suppressedAfterCast = false;

    private static int lastHotbarSlot = -1;
    private static int lastSpellSelectionIndex = -999;
    private static boolean wasSelectedLastTick = false;

    // Smoothly interpolated preview coordinates to eliminate frame jitter
    private static Vec3 smoothedTargetPos = null;
    private static Vec3 smoothedSpinAxis = null;

    // Deferred state for Iris / Oculus shaderpack compatibility pass
    private static PoseStack deferredPose;
    private static Matrix4f deferredModelView;
    private static Matrix4f deferredProjection;
    private static Camera deferredCamera;
    private static float deferredPartial;

    private GargantuaPreviewRenderer() {}

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            resetState();
            return;
        }

        Player player = mc.player;
        boolean isCastingGargantua = isCurrentlyCastingGargantua();
        boolean hasActiveBlackHole = !GargantuaPostProcessor.getActiveClientEntities(mc).isEmpty();

        // Detect when a Gargantua cast finishes -> immediately latch suppression off!
        if (isCastingGargantua) {
            if (!wasCastingGargantua) {
                suppressedAfterCast = false;
                maxCastCompletionSeen = 0.0f;
            }
            wasCastingGargantua = true;
            maxCastCompletionSeen = Math.max(maxCastCompletionSeen, ClientMagicData.getCastCompletionPercent());
        } else {
            if (wasCastingGargantua) {
                wasCastingGargantua = false;
                if (maxCastCompletionSeen >= 0.80f || hasActiveBlackHole) {
                    suppressedAfterCast = true;
                    fadeTicks = 0;
                }
                maxCastCompletionSeen = 0.0f;
            }
        }

        // Also suppress immediately if a GargantuaEntity is active in the level
        if (hasActiveBlackHole) {
            suppressedAfterCast = true;
            fadeTicks = 0;
        }

        // Check if player changed hotbar slot or spell wheel selection to re-arm preview
        int currentHotbarSlot = player.getInventory().selected;
        int currentSpellIndex = getCurrentSpellSelectionIndex();
        boolean isSelectedNow = isGargantuaSelectedAndReady(player);

        if (currentHotbarSlot != lastHotbarSlot || currentSpellIndex != lastSpellSelectionIndex || (!wasSelectedLastTick && isSelectedNow)) {
            if (!hasActiveBlackHole) {
                suppressedAfterCast = false;
            }
        }
        lastHotbarSlot = currentHotbarSlot;
        lastSpellSelectionIndex = currentSpellIndex;
        wasSelectedLastTick = isSelectedNow;

        boolean shouldDisplay = (isSelectedNow || isCastingGargantua) && !suppressedAfterCast && !hasActiveBlackHole && !isGargantuaOnCooldown();

        if (shouldDisplay) {
            if (fadeTicks < MAX_FADE_TICKS) {
                fadeTicks++;
            }
        } else {
            if (fadeTicks > 0) {
                fadeTicks = Math.max(0, fadeTicks - 2);
            }
            if (fadeTicks == 0) {
                smoothedTargetPos = null;
                smoothedSpinAxis = null;
            }
        }
    }

    public static void resetState() {
        fadeTicks = 0;
        wasCastingGargantua = false;
        maxCastCompletionSeen = 0.0f;
        suppressedAfterCast = false;
        lastHotbarSlot = -1;
        lastSpellSelectionIndex = -999;
        wasSelectedLastTick = false;
        smoothedTargetPos = null;
        smoothedSpinAxis = null;
        clearDeferred();
    }

    private static boolean isCurrentlyCastingGargantua() {
        if (!ClientMagicData.isCasting()) return false;
        String spellId = ClientMagicData.getCastingSpellId();
        return spellId != null && (spellId.equals(GargantuaSpell.ID.toString()) || spellId.endsWith(":gargantua"));
    }

    private static boolean isGargantuaOnCooldown() {
        try {
            AbstractSpell spell = ModCinematicSpells.GARGANTUA.get();
            if (spell != null && ClientMagicData.getCooldowns() != null) {
                return ClientMagicData.getCooldowns().getCooldownPercent(spell) > 0.0f;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static int getCurrentSpellSelectionIndex() {
        try {
            SpellSelectionManager ssm = ClientMagicData.getSpellSelectionManager();
            if (ssm != null) {
                return ssm.getGlobalSelectionIndex();
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static boolean isGargantuaSelectedAndReady(Player player) {
        if (player == null || player.isSpectator()) return false;

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        // 1. Direct Gargantua Scroll in Mainhand or Offhand
        if (isStackGargantuaScroll(mainHand) || isStackGargantuaScroll(offHand)) {
            return true;
        }

        // If holding a Scroll of a DIFFERENT spell in mainhand, right-click will cast that scroll instead
        if (mainHand.getItem() instanceof Scroll && !isStackContainingGargantua(mainHand)) {
            return false;
        }

        boolean holdingMagicFocus = isMagicFocus(mainHand) || isMagicFocus(offHand);
        if (!holdingMagicFocus) {
            return false;
        }

        // 2. Check Iron's Spells SpellSelectionManager
        boolean selectedInManager = false;
        try {
            SpellSelectionManager ssm = ClientMagicData.getSpellSelectionManager();
            if (ssm != null) {
                SpellSelectionManager.SelectionOption option = ssm.getSelection();
                if (option != null && option.spellData != null && option.spellData != SpellData.EMPTY) {
                    AbstractSpell spell = option.spellData.getSpell();
                    if (spell != null && GargantuaSpell.ID.equals(spell.getSpellResource())) {
                        selectedInManager = true;
                    }
                }
                if (!selectedInManager) {
                    SpellData selectedData = ssm.getSelectedSpellData();
                    if (selectedData != null && selectedData != SpellData.EMPTY && selectedData.getSpell() != null) {
                        if (GargantuaSpell.ID.equals(selectedData.getSpell().getSpellResource())) {
                            selectedInManager = true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (selectedInManager) {
            return true;
        }

        // 3. Held Spellbook in hand that has Gargantua inscribed
        if (mainHand.getItem() instanceof SpellBook && isStackContainingGargantua(mainHand)) {
            return true;
        }
        if (offHand.getItem() instanceof SpellBook && isStackContainingGargantua(offHand)) {
            return true;
        }

        return false;
    }

    private static boolean isMagicFocus(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof CastingItem) return true;
        if (stack.getItem() instanceof SpellBook) return true;
        if (stack.getItem() instanceof Scroll) return true;
        try {
            return ISpellContainer.isSpellContainer(stack);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isStackGargantuaScroll(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() instanceof GargantuaScrollItem) return true;
        if (stack.getItem() instanceof Scroll) {
            return isStackContainingGargantua(stack);
        }
        return false;
    }

    private static boolean isStackContainingGargantua(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            if (ISpellContainer.isSpellContainer(stack)) {
                ISpellContainer container = ISpellContainer.get(stack);
                if (container != null) {
                    for (SpellSlot slot : container.getActiveSpells()) {
                        if (slot != null && slot.spellData() != null && slot.spellData().getSpell() != null) {
                            if (GargantuaSpell.ID.equals(slot.spellData().getSpell().getSpellResource())) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean hasDeferredDraw() {
        return deferredPose != null && deferredCamera != null;
    }

    public static void clearDeferred() {
        deferredPose = null;
        deferredModelView = null;
        deferredProjection = null;
        deferredCamera = null;
    }

    public static void renderIrisPhase1(MultiBufferSource.BufferSource bufferSource, Matrix4fStack modelView) {
        if (!hasDeferredDraw() || !ZoltraakCinematicClientConfig.isWorldGlowActive(true)) return;
        modelView.set(deferredModelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(deferredProjection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);

        float bloomScale = ZoltraakCinematicClientConfig.getGargantuaGlow();
        ZoltraakRenderPass.renderIrisScaled(bufferSource, bloomScale,
                () -> emitIrisPreviewEmissive(deferredPose, bufferSource, deferredCamera, deferredPartial));
    }

    public static void renderDeferred(MultiBufferSource.BufferSource bufferSource, Matrix4fStack modelView) {
        if (!hasDeferredDraw()) return;
        modelView.set(deferredModelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(deferredProjection, com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);

        renderPreviewGeometry(deferredPose, bufferSource, deferredCamera, deferredPartial);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || fadeTicks <= 0 || suppressedAfterCast) {
            clearDeferred();
            return;
        }

        if (!GargantuaPostProcessor.getActiveClientEntities(mc).isEmpty()) {
            clearDeferred();
            return;
        }

        float partialTick = (float) event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        PoseStack poseStack = event.getPoseStack();

        if (ShaderCompatibility.useFullDetailPass()) {
            if (ShaderCompatibility.isShadowPass()) return;

            deferredPose = new PoseStack();
            deferredPose.last().pose().set(poseStack.last().pose());
            deferredPose.last().normal().set(poseStack.last().normal());
            deferredModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            deferredProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            deferredCamera = camera;
            deferredPartial = partialTick;
            return;
        }

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        renderPreviewGeometry(poseStack, bufferSource, camera, partialTick);
    }

    private static void renderPreviewGeometry(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                              Camera camera, float partialTick) {
        float bloomScale = ZoltraakCinematicClientConfig.getGargantuaGlow();
        if (bloomScale <= 0.004f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Compute exact 1:1 raycast target position and 3D spinAxis shared with GargantuaSpell.onCast
        Vec3 rawTargetPos = GargantuaSpell.computeTargetPosition(mc.level, mc.player, partialTick);
        Vec3 rawSpinAxis = GargantuaSpell.computeSpinAxis(mc.player, rawTargetPos, partialTick);

        if (smoothedTargetPos == null || smoothedTargetPos.distanceToSqr(rawTargetPos) > 64.0d) {
            smoothedTargetPos = rawTargetPos;
            smoothedSpinAxis = rawSpinAxis;
        } else {
            smoothedTargetPos = smoothedTargetPos.lerp(rawTargetPos, 0.40d);
            smoothedSpinAxis = smoothedSpinAxis.lerp(rawSpinAxis, 0.40d).normalize();
        }

        Vec3 targetPos = smoothedTargetPos;

        boolean isCasting = isCurrentlyCastingGargantua();
        float castProgress = isCasting ? Mth.clamp(ClientMagicData.getCastCompletionPercent(), 0.0f, 1.0f) : 0.0f;

        float fade = Mth.clamp((fadeTicks + (isCasting ? partialTick : 0.0f)) / (float) MAX_FADE_TICKS, 0.0f, 1.0f);
        float smoothFade = fade * fade * (3.0f - 2.0f * fade);

        float gameTime = (mc.player.tickCount + partialTick);
        float pulse = 0.88f + 0.12f * Mth.sin(gameTime * (isCasting ? 0.45f : 0.18f));

        Vec3 camPos = camera.getPosition();
        Vec3 relCenter = targetPos.subtract(camPos);

        poseStack.pushPose();
        poseStack.translate(relCenter.x, relCenter.y, relCenter.z);
        Matrix4f m = poseStack.last().pose();

        VertexConsumer lines = buffers.getBuffer(ZoltraakRenderTypes.LIGHT);

        // Find the ground position directly below the black hole center
        Vec3 downStart = targetPos;
        Vec3 downEnd = targetPos.subtract(0.0d, 128.0d, 0.0d);
        HitResult groundHit = mc.level.clip(new ClipContext(
                downStart, downEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));

        Vec3 groundTarget;
        double groundDrop;
        if (groundHit.getType() != HitResult.Type.MISS) {
            groundTarget = groundHit.getLocation();
            groundDrop = targetPos.y - groundTarget.y;
        } else {
            groundDrop = 16.0d;
            groundTarget = targetPos.subtract(0.0d, groundDrop, 0.0d);
        }

        // =====================================================================
        // 1. SLEEK VERTICAL LANDING BEAM DOWN THE MIDDLE
        // =====================================================================
        Vec3 topPoint = new Vec3(0.0d, 0.8d, 0.0d);
        Vec3 bottomPoint = new Vec3(0.0d, -groundDrop, 0.0d);

        // Outer warm golden aura ribbon
        float outerWidth = (float) (0.08d + 0.04d * castProgress);
        billboardLine(lines, m, relCenter, topPoint, bottomPoint, outerWidth,
                0.55f * smoothFade * pulse, 1.0f, 0.76f, 0.22f);

        // Inner laser-sharp white-gold beam core
        float coreWidth = (float) (0.025d + 0.015d * castProgress);
        billboardLine(lines, m, relCenter, topPoint, bottomPoint, coreWidth,
                0.95f * smoothFade, 1.0f, 0.98f, 0.92f);

        // Cascading energy beads trickling down the beam to the landing spot
        int beadCount = Math.max(3, (int) (groundDrop / 3.0d));
        for (int i = 0; i < beadCount; i++) {
            float phase = (float) ((gameTime * 0.08d + (double) i / beadCount) % 1.0d);
            double yBead = topPoint.y - phase * (topPoint.y - bottomPoint.y);
            Vec3 beadStart = new Vec3(0.0d, yBead + 0.45d, 0.0d);
            Vec3 beadEnd = new Vec3(0.0d, yBead - 0.45d, 0.0d);
            billboardLine(lines, m, relCenter, beadStart, beadEnd, 0.05d,
                    0.80f * smoothFade, 1.0f, 0.92f, 0.60f);
        }

        // =====================================================================
        // 2. MINIMAL SINGULARITY EPICENTER RETICLE (in the air at target center)
        // =====================================================================
        // Small, subtle floating focal ring (radius 1.0 block)
        double centerRingR = 1.0d + 0.1d * Math.sin(gameTime * 0.25d);
        drawHorizontalCircle(lines, m, relCenter, new Vec3(0, 0, 0), centerRingR, 32,
                0.035d, 0.85f * smoothFade * pulse, 1.0f, 0.85f, 0.35f);

        // Subtle 3D focal diamond pointers
        double dSize = 0.55d;
        billboardLine(lines, m, relCenter, new Vec3(-dSize, 0, 0), new Vec3(dSize, 0, 0), 0.025d, 0.70f * smoothFade, 1.0f, 0.95f, 0.80f);
        billboardLine(lines, m, relCenter, new Vec3(0, 0, -dSize), new Vec3(0, 0, dSize), 0.025d, 0.70f * smoothFade, 1.0f, 0.95f, 0.80f);

        // =====================================================================
        // 3. GROUND LANDING TARGET RETICLE (on the terrain where skill lands)
        // =====================================================================
        Vec3 groundLocal = new Vec3(0.0d, -groundDrop + 0.06d, 0.0d);
        double reticleR = 2.6d + 0.25d * Math.sin(gameTime * 0.20d);

        // Outer landing circle
        drawHorizontalCircle(lines, m, relCenter, groundLocal, reticleR, 40,
                0.045d, 0.80f * smoothFade * pulse, 1.0f, 0.78f, 0.25f);

        // Inner landing bullseye ring
        drawHorizontalCircle(lines, m, relCenter, groundLocal, 0.9d, 24,
                0.030d, 0.90f * smoothFade, 1.0f, 0.95f, 0.85f);

        // 4 ground cardinal pointers
        double tickInner = reticleR - 0.45d;
        double tickOuter = reticleR + 0.45d;
        billboardLine(lines, m, relCenter, groundLocal.add(-tickOuter, 0, 0), groundLocal.add(-tickInner, 0, 0), 0.035d, 0.85f * smoothFade, 1.0f, 0.88f, 0.45f);
        billboardLine(lines, m, relCenter, groundLocal.add(tickInner, 0, 0), groundLocal.add(tickOuter, 0, 0), 0.035d, 0.85f * smoothFade, 1.0f, 0.88f, 0.45f);
        billboardLine(lines, m, relCenter, groundLocal.add(0, 0, -tickOuter), groundLocal.add(0, 0, -tickInner), 0.035d, 0.85f * smoothFade, 1.0f, 0.88f, 0.45f);
        billboardLine(lines, m, relCenter, groundLocal.add(0, 0, tickInner), groundLocal.add(0, 0, tickOuter), 0.035d, 0.85f * smoothFade, 1.0f, 0.88f, 0.45f);

        ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.LIGHT, bloomScale);

        // 4. HOLOGRAPHIC KEPLERIAN ACCRETION DISK RETICLE ON GROUND (gargantua_disk_preview shader)
        VertexConsumer diskVc = buffers.getBuffer(ZoltraakRenderTypes.GARGANTUA_DISK_PREVIEW);
        float diskHalf = (float) (reticleR / 0.72d);
        float gy = (float) groundLocal.y;
        float diskFade = smoothFade * 0.72f;
        diskVc.addVertex(m, -diskHalf, gy, -diskHalf).setUv(0.0f, 0.0f).setColor(castProgress, pulse, 0.0f, diskFade);
        diskVc.addVertex(m,  diskHalf, gy, -diskHalf).setUv(1.0f, 0.0f).setColor(castProgress, pulse, 0.0f, diskFade);
        diskVc.addVertex(m,  diskHalf, gy,  diskHalf).setUv(1.0f, 1.0f).setColor(castProgress, pulse, 0.0f, diskFade);
        diskVc.addVertex(m, -diskHalf, gy,  diskHalf).setUv(0.0f, 1.0f).setColor(castProgress, pulse, 0.0f, diskFade);
        ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.GARGANTUA_DISK_PREVIEW, bloomScale);

        // 5. KERR SPACETIME FRAME-DRAGGING METRIC SPHERE AT EPICENTER (gargantua_sphere_preview shader)
        VertexConsumer sphereVc = buffers.getBuffer(ZoltraakRenderTypes.GARGANTUA_SPHERE_PREVIEW);
        drawLocalSphere(sphereVc, m, (float) (centerRingR * 0.92d), 16, 24, castProgress, pulse, smoothFade * 0.65f);
        ZoltraakRenderPass.endBatchWithBloom(buffers, ZoltraakRenderTypes.GARGANTUA_SPHERE_PREVIEW, bloomScale);

        poseStack.popPose();
    }

    private static void drawLocalSphere(VertexConsumer vc, Matrix4f m, float radius, int rings, int sectors,
                                        float castProgress, float pulse, float fade) {
        Vector3f normVec = new Vector3f();
        for (int r = 0; r < rings; r++) {
            double phi1 = Math.PI * ((double) r / rings - 0.5d);
            double phi2 = Math.PI * ((double) (r + 1) / rings - 0.5d);
            float ny1 = (float) Math.sin(phi1);
            float ny2 = (float) Math.sin(phi2);
            double c1 = Math.cos(phi1);
            double c2 = Math.cos(phi2);
            float v1 = (float) r / rings;
            float v2 = (float) (r + 1) / rings;

            for (int s = 0; s < sectors; s++) {
                double th1 = (2.0d * Math.PI * s) / sectors;
                double th2 = (2.0d * Math.PI * (s + 1)) / sectors;
                float u1 = (float) s / sectors;
                float u2 = (float) (s + 1) / sectors;
                float uMid = 0.5f * (u1 + u2);

                float nx11 = (float) (c1 * Math.cos(th1));
                float nz11 = (float) (c1 * Math.sin(th1));
                float nx12 = (float) (c1 * Math.cos(th2));
                float nz12 = (float) (c1 * Math.sin(th2));
                float nx21 = (float) (c2 * Math.cos(th1));
                float nz21 = (float) (c2 * Math.sin(th1));
                float nx22 = (float) (c2 * Math.cos(th2));
                float nz22 = (float) (c2 * Math.sin(th2));

                if (r < rings - 1) {
                    emitSphereVertex(vc, m, normVec, nx11, ny1, nz11, radius, r == 0 ? uMid : u1, v1, castProgress, pulse, fade);
                    emitSphereVertex(vc, m, normVec, nx21, ny2, nz21, radius, u1, v2, castProgress, pulse, fade);
                    emitSphereVertex(vc, m, normVec, nx22, ny2, nz22, radius, u2, v2, castProgress, pulse, fade);
                }

                if (r > 0) {
                    emitSphereVertex(vc, m, normVec, nx11, ny1, nz11, radius, u1, v1, castProgress, pulse, fade);
                    emitSphereVertex(vc, m, normVec, nx22, ny2, nz22, radius, r == rings - 1 ? uMid : u2, v2, castProgress, pulse, fade);
                    emitSphereVertex(vc, m, normVec, nx12, ny1, nz12, radius, u2, v1, castProgress, pulse, fade);
                }
            }
        }
    }

    private static void emitSphereVertex(VertexConsumer vc, Matrix4f m, Vector3f normVec,
                                         float nx, float ny, float nz, float radius,
                                         float u, float v, float castProgress, float pulse, float fade) {
        m.transformDirection(nx, ny, nz, normVec);
        if (normVec.lengthSquared() > 1.0e-8f) {
            normVec.normalize();
        } else {
            normVec.set(nx, ny, nz);
        }
        vc.addVertex(m, nx * radius, ny * radius, nz * radius).setUv(u, v).setColor(castProgress, pulse, 0.0f, fade).setNormal(normVec.x, normVec.y, normVec.z);
    }

    private static void emitIrisPreviewEmissive(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                                Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 targetPos = smoothedTargetPos != null
                ? smoothedTargetPos
                : GargantuaSpell.computeTargetPosition(mc.level, mc.player, partialTick);
        boolean isCasting = isCurrentlyCastingGargantua();
        float castProgress = isCasting ? Mth.clamp(ClientMagicData.getCastCompletionPercent(), 0.0f, 1.0f) : 0.0f;
        float fade = Mth.clamp((fadeTicks + (isCasting ? partialTick : 0.0f)) / (float) MAX_FADE_TICKS, 0.0f, 1.0f);
        float smoothFade = fade * fade * (3.0f - 2.0f * fade);
        if (smoothFade <= 0.015f) return;

        float gameTime = mc.player.tickCount + partialTick;
        float pulse = 0.88f + 0.12f * Mth.sin(gameTime * (isCasting ? 0.45f : 0.18f));
        Vec3 camPos = camera.getPosition();
        Vec3 relCenter = targetPos.subtract(camPos);

        HitResult groundHit = mc.level.clip(new ClipContext(
                targetPos, targetPos.subtract(0.0d, 128.0d, 0.0d),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        double groundDrop = groundHit.getType() != HitResult.Type.MISS ? (targetPos.y - groundHit.getLocation().y) : 16.0d;

        poseStack.pushPose();
        poseStack.translate(relCenter.x, relCenter.y, relCenter.z);
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffers.getBuffer(ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);

        Vec3 topPoint = new Vec3(0.0d, 0.8d, 0.0d);
        Vec3 bottomPoint = new Vec3(0.0d, -groundDrop, 0.0d);
        Vec3 side = new Vec3(0.0d, 1.0d, 0.0d).cross(relCenter);
        side = side.lengthSqr() < 1.0e-5d ? new Vec3(1.0d, 0.0d, 0.0d) : side.normalize();

        float power = Mth.clamp(smoothFade * pulse * 0.76f, 0.0f, 0.80f);
        float pg = ZoltraakRenderPass.irisFadeGuard(power);
        double outerHalf = 0.09d + 0.04d * castProgress;
        ZoltraakRenderPass.emitFeatheredBeamSegment(
                vc, m, topPoint, bottomPoint, side,
                outerHalf * 0.55d, outerHalf * 2.50d,
                1.00f * pg, 0.78f * pg, 0.36f * pg, power,
                0.86f * pg, 0.52f * pg, 0.16f * pg, Mth.clamp(power * 0.68f, 0.0f, 0.56f)
        );

        Vec3 worldX = new Vec3(1.0d, 0.0d, 0.0d);
        Vec3 worldZ = new Vec3(0.0d, 0.0d, 1.0d);
        double centerRingR = 1.0d + 0.1d * Math.sin(gameTime * 0.25d);
        ZoltraakRenderPass.emitFeatheredRing(
                vc, m, Vec3.ZERO, worldX, worldZ,
                Math.max(0.1d, centerRingR - 0.24d), centerRingR, centerRingR + 0.34d, 32,
                1.00f * pg, 0.78f * pg, 0.34f * pg, Mth.clamp(power * 0.90f, 0.0f, 0.70f)
        );

        Vec3 groundLocal = new Vec3(0.0d, -groundDrop + 0.06d, 0.0d);
        double reticleR = 2.6d + 0.25d * Math.sin(gameTime * 0.20d);
        ZoltraakRenderPass.emitFeatheredRing(
                vc, m, groundLocal, worldX, worldZ,
                Math.max(0.2d, reticleR - 0.30d), reticleR, reticleR + 0.42d, 36,
                1.00f * pg, 0.74f * pg, 0.28f * pg, Mth.clamp(power * 0.90f, 0.0f, 0.70f)
        );
        ZoltraakRenderPass.emitFeatheredRing(
                vc, m, groundLocal, worldX, worldZ,
                0.55d, 0.90d, 1.35d, 24,
                1.00f * pg, 0.90f * pg, 0.68f * pg, Mth.clamp(power * 0.84f, 0.0f, 0.66f)
        );

        buffers.endBatch(ZoltraakRenderTypes.IRIS_EMISSIVE_QUAD);
        poseStack.popPose();
    }

    private static void drawHorizontalCircle(VertexConsumer b, Matrix4f m, Vec3 relCenter,
                                             Vec3 center, double radius, int segments,
                                             double width, float alpha, float r, float g, float blue) {
        Vec3 worldX = new Vec3(1.0d, 0.0d, 0.0d);
        Vec3 worldZ = new Vec3(0.0d, 0.0d, 1.0d);
        for (int i = 0; i < segments; i++) {
            double a1 = (i * Math.PI * 2.0d) / segments;
            double a2 = ((i + 1) * Math.PI * 2.0d) / segments;
            Vec3 p1 = center.add(worldX.scale(Math.cos(a1) * radius)).add(worldZ.scale(Math.sin(a1) * radius));
            Vec3 p2 = center.add(worldX.scale(Math.cos(a2) * radius)).add(worldZ.scale(Math.sin(a2) * radius));
            billboardLine(b, m, relCenter, p1, p2, width, alpha, r, g, blue);
        }
    }

    private static void billboardLine(VertexConsumer b, Matrix4f m, Vec3 relCenter,
                                      Vec3 a, Vec3 c, double width,
                                      float alpha, float r, float g, float blue) {
        Vec3 viewVec = a.add(relCenter);
        Vec3 cross = c.subtract(a).cross(viewVec);
        if (cross.lengthSqr() < 1.0e-7d) {
            cross = new Vec3(0.0d, 1.0d, 0.0d);
        } else {
            cross = cross.normalize();
        }
        Vec3 side = cross.scale(width);
        tri(b, m, a.subtract(side), a.add(side), c.add(side), r, g, blue, alpha);
        tri(b, m, a.subtract(side), c.add(side), c.subtract(side), r, g, blue, alpha);
    }

    private static void tri(VertexConsumer b, Matrix4f m, Vec3 a, Vec3 c, Vec3 d,
                            float r, float g, float blue, float alpha) {
        b.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, blue, alpha);
        b.addVertex(m, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, blue, alpha);
        b.addVertex(m, (float) d.x, (float) d.y, (float) d.z).setColor(r, g, blue, alpha);
    }
}
