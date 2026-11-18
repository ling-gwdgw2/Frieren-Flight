// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.cinematic;

import com.frierenflight.zoltraakcinematic.client.renderer.GargantuaPostProcessor;
import com.frierenflight.zoltraakcinematic.config.ZoltraakCinematicClientConfig;
import com.frierenflight.zoltraakcinematic.entity.GargantuaEntity;
import com.frierenflight.zoltraakcinematic.registry.ModCinematicSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 🌌 GargantuaScreenFx
 * Handles full-screen post-processing overlays and acoustic drama:
 * - Act 1: Spacetime rupture flash & chromatic edge dispersion
 * - Act 3: Acoustic vacuum silence & atmospheric darkness vignette
 * - Act 4: Supernova blinding white flash, proximity blast & tinnitus
 */
public final class GargantuaScreenFx {

    private static int supernovaFlashTicks = 0;
    private static int maxFlashTicks = 0;
    private static float flashIntensity = 0.0f;

    private static final Set<Integer> BLAST_OBSERVED_ENTITIES = new HashSet<>();
    private static final Set<Integer> TEAR_OBSERVED_ENTITIES = new HashSet<>();

    private GargantuaScreenFx() {}

    public static void clientTick(Minecraft mc) {
        if (supernovaFlashTicks > 0) {
            supernovaFlashTicks--;
            if (supernovaFlashTicks == 0) {
                flashIntensity = 0.0f;
                maxFlashTicks = 0;
            }
        }

        if (mc.level == null || mc.player == null) {
            BLAST_OBSERVED_ENTITIES.clear();
            TEAR_OBSERVED_ENTITIES.clear();
            return;
        }

        List<GargantuaEntity> active = GargantuaPostProcessor.getActiveClientEntities(mc);

        for (GargantuaEntity g : active) {
            if (!g.isAlive()) continue;

            int id = g.getId();
            float visualAge = g.getVisualAgeTicks(0.0f);
            int blastTick = g.getBlastTick();
            double dist = g.distanceTo(mc.player);

            // Act 1: Brief Rupture Glint on spawn
            if (visualAge >= 0.0f && !TEAR_OBSERVED_ENTITIES.contains(id)) {
                TEAR_OBSERVED_ENTITIES.add(id);
                if (visualAge <= 15.0f && dist <= 150.0d) {
                    float distFactor = (float) Mth.clamp(1.0 - dist / 150.0d, 0.2, 1.0);
                    triggerFlash(6, 0.25f * distFactor);
                }
            }

            // Act 3 vignette darkening is now rendered smoothly & radially inside gargantua_composite.fsh
            // (eliminates flat HUD rectangle edges over the horizon)

            // Act 4: Supernova Detonation (Crisp 5-tick Impact Glint + Acoustic Tinnitus — full explosion remains visible in HDR shader)
            if (g.isBlasting(0.0f) && !BLAST_OBSERVED_ENTITIES.contains(id)) {
                BLAST_OBSERVED_ENTITIES.add(id);
                if (visualAge <= (float) blastTick + 15.0f && dist <= 240.0d) {
                    float distFactor = (float) Mth.clamp(1.0 - dist / 240.0d, 0.15, 1.0);
                    triggerFlash(5, 0.16f * distFactor);

                    // Play explosion ear ringing sound
                    if (ZoltraakCinematicClientConfig.isEarRingingEnabled()) {
                        float vol = ZoltraakCinematicClientConfig.getEarRingingVolume();
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(
                                ModCinematicSounds.TINNITUS.get(),
                                1.0f,
                                0.85f * vol * distFactor
                        ));
                    }
                }
            }
        }

        // Clean up dead entity IDs
        BLAST_OBSERVED_ENTITIES.removeIf(id -> mc.level.getEntity(id) == null);
        TEAR_OBSERVED_ENTITIES.removeIf(id -> mc.level.getEntity(id) == null);
    }

    public static void triggerFlash(int duration, float intensity) {
        if (!ZoltraakCinematicClientConfig.isScreenFlashEnabled()) return;
        supernovaFlashTicks = duration;
        maxFlashTicks = duration;
        flashIntensity = intensity * ZoltraakCinematicClientConfig.getFlashStrength();
    }

    public static void renderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Supernova Brief Optical Impact Accent (fast cubic falloff so it never blocks the 4-pass explosion)
        if (supernovaFlashTicks > 0 && maxFlashTicks > 0) {
            GuiGraphics graphics = event.getGuiGraphics();
            int w = graphics.guiWidth();
            int h = graphics.guiHeight();

            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float tNorm = Mth.clamp(Math.max(0.0f, (float) supernovaFlashTicks - partial) / (float) maxFlashTicks, 0.0f, 1.0f);
            float progress = tNorm * tNorm * tNorm * flashIntensity;
            int alpha = (int) (Mth.clamp(progress, 0.0f, 0.35f) * 255.0f);

            if (alpha > 0) {
                int flashColor = (alpha << 24) | 0xFFF4E6;
                graphics.fill(0, 0, w, h, flashColor);
            }
        }
    }
}
