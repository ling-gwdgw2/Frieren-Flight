// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.cinematic;

import com.frierenflight.zoltraakcinematic.spell.GargantuaSpell;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 🪄 GargantuaCasterPose
 * Computes elegant arcane casting postures, arm rotations, and gravitational levitation offsets
 * for entities channeling Gargantua (both local and remote multiplayer players).
 */
public final class GargantuaCasterPose {

    private static final class PoseState {
        float prevWeight;
        float weight;
    }

    private static final Map<UUID, PoseState> POSE_STATES = new HashMap<>();

    private GargantuaCasterPose() {}

    private static boolean isCastingGargantua(Minecraft mc, Player player) {
        String gargantuaId = GargantuaSpell.ID.toString();
        if (mc.player != null && mc.player.getId() == player.getId()) {
            if (ClientMagicData.isCasting() && gargantuaId.equals(ClientMagicData.getCastingSpellId())) {
                return true;
            }
        }
        SyncedSpellData synced = ClientMagicData.getSyncedSpellData(player);
        return synced != null && synced.isCasting() && gargantuaId.equals(synced.getCastingSpellId());
    }

    public static void clientTick(Minecraft mc) {
        if (mc.level == null) {
            POSE_STATES.clear();
            return;
        }

        Set<UUID> presentIds = POSE_STATES.isEmpty() ? null : new HashSet<>();
        for (Player player : mc.level.players()) {
            UUID id = player.getUUID();
            boolean casting = isCastingGargantua(mc, player);
            PoseState state = POSE_STATES.get(id);
            if (!casting && state == null) {
                continue;
            }
            if (state == null) {
                state = new PoseState();
                POSE_STATES.put(id, state);
            }
            if (presentIds != null) {
                presentIds.add(id);
            }
            state.prevWeight = state.weight;
            state.weight = casting
                    ? Math.min(1.0f, state.weight + 0.10f)
                    : Math.max(0.0f, state.weight - 0.15f);
        }

        if (!POSE_STATES.isEmpty()) {
            Iterator<Map.Entry<UUID, PoseState>> it = POSE_STATES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, PoseState> entry = it.next();
                PoseState s = entry.getValue();
                boolean disappeared = presentIds != null && !presentIds.contains(entry.getKey());
                if (disappeared || (s.weight <= 0.0f && s.prevWeight <= 0.0f)) {
                    it.remove();
                }
            }
        }
    }

    public static float getPoseWeight(LivingEntity entity) {
        if (!(entity instanceof Player player)) return 0.0f;
        PoseState state = POSE_STATES.get(player.getUUID());
        if (state == null || (state.prevWeight <= 0.0f && state.weight <= 0.0f)) {
            return 0.0f;
        }

        Minecraft mc = Minecraft.getInstance();
        float partial = 1.0f;
        if (mc.level != null && !mc.isPaused()) {
            try {
                partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
            } catch (Throwable ignored) {
                partial = 1.0f;
            }
        }

        return Mth.lerp(partial, state.prevWeight, state.weight);
    }

    /**
     * Applies the majestic dual-arm celestial channel pose to the player's model.
     */
    public static void apply(LivingEntity entity, ModelPart rightArm, ModelPart leftArm, float headPitch, float netHeadYaw) {
        float weight = getPoseWeight(entity);
        if (weight <= 0.001f) return;

        // Smooth curve
        float w = weight * weight * (3.0f - 2.0f * weight);

        // Right arm holding staff aloft: pointed forward-upward along gaze with arcane tilt
        float lookPitchRad = (float) Math.toRadians(headPitch);
        float lookYawRad = (float) Math.toRadians(netHeadYaw);

        float targetRightX = lookPitchRad - 1.35f; // Raised high with staff
        float targetRightY = lookYawRad * 0.4f - 0.25f;
        float targetRightZ = 0.35f;

        // Left arm outstretched in channeling gesture
        float targetLeftX = lookPitchRad - 0.95f;
        float targetLeftY = lookYawRad * 0.4f + 0.35f;
        float targetLeftZ = -0.45f;

        rightArm.xRot = Mth.lerp(w, rightArm.xRot, targetRightX);
        rightArm.yRot = Mth.lerp(w, rightArm.yRot, targetRightY);
        rightArm.zRot = Mth.lerp(w, rightArm.zRot, targetRightZ);

        leftArm.xRot = Mth.lerp(w, leftArm.xRot, targetLeftX);
        leftArm.yRot = Mth.lerp(w, leftArm.yRot, targetLeftY);
        leftArm.zRot = Mth.lerp(w, leftArm.zRot, targetLeftZ);
    }
}
