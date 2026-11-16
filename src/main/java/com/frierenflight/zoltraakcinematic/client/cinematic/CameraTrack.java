// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.cinematic;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequential keyframe-phase camera timeline supporting hard anime cuts, smooth crossfades
 * across phase boundaries, priority preemption, and interruptible fade-out skipping.
 */
public final class CameraTrack {

    @FunctionalInterface
    public interface PhaseEvaluator {
        CameraTransform evaluate(CameraRigFrame rig, float partialTick, float phaseProgress);
    }

    private record Phase(int startTick, int endTick, int easeInTicks, PhaseEvaluator evaluator) {
        float normalizedProgress(float continuousTick) {
            int span = Math.max(1, endTick - startTick);
            return Mth.clamp((continuousTick - (float) startTick) / (float) span, 0.0f, 1.0f);
        }
    }

    public static final class Builder {
        private final CameraRigFrame rig;
        private final int priority;
        private final List<Phase> phases = new ArrayList<>();
        private int cursorTick = 0;
        private int fadeInTicks = 3;
        private int fadeOutTicks = 10;

        private Builder(CameraRigFrame rig, int priority) {
            this.rig = rig;
            this.priority = priority;
        }

        public Builder fadeEnvelope(int fadeInTicks, int fadeOutTicks) {
            this.fadeInTicks = Math.max(0, fadeInTicks);
            this.fadeOutTicks = Math.max(1, fadeOutTicks);
            return this;
        }

        /** Appends a phase starting with an instant anime cut. */
        public Builder addCutPhase(int durationTicks, PhaseEvaluator evaluator) {
            return addBlendedPhase(durationTicks, 0, evaluator);
        }

        /** Appends a phase that crossfades smoothly from the previous phase over {@code easeInTicks}. */
        public Builder addBlendedPhase(int durationTicks, int easeInTicks, PhaseEvaluator evaluator) {
            int dur = Math.max(1, durationTicks);
            int end = cursorTick + dur;
            phases.add(new Phase(cursorTick, end, Math.max(0, easeInTicks), evaluator));
            cursorTick = end;
            return this;
        }

        public CameraTrack build() {
            return new CameraTrack(rig, priority, Math.max(1, cursorTick), fadeInTicks, fadeOutTicks, List.copyOf(phases));
        }
    }

    public static Builder builder(CameraRigFrame rig, int priority) {
        return new Builder(rig, priority);
    }

    private static final int SKIP_FADE_TICKS = 6;

    private final CameraRigFrame rig;
    private final int priority;
    private final int durationTicks;
    private final int fadeInTicks;
    private final int fadeOutTicks;
    private final List<Phase> phases;

    private int elapsedTicks = 0;
    private boolean skipping = false;
    private int skipElapsedTicks = 0;
    private float frozenWeightAtSkip = 1.0f;
    private float frozenTickAtSkip = 0.0f;

    private CameraTrack(
            CameraRigFrame rig,
            int priority,
            int durationTicks,
            int fadeInTicks,
            int fadeOutTicks,
            List<Phase> phases
    ) {
        this.rig = rig;
        this.priority = priority;
        this.durationTicks = durationTicks;
        this.fadeInTicks = fadeInTicks;
        this.fadeOutTicks = fadeOutTicks;
        this.phases = phases;
    }

    public int priority() {
        return priority;
    }

    public boolean isComplete() {
        return elapsedTicks >= durationTicks || (skipping && skipElapsedTicks >= SKIP_FADE_TICKS);
    }

    public void requestSkip() {
        if (!skipping) {
            skipping = true;
            skipElapsedTicks = 0;
            frozenTickAtSkip = Math.min((float) elapsedTicks, (float) durationTicks);
            frozenWeightAtSkip = rawEnvelopeWeight(frozenTickAtSkip);
        }
    }

    public void advanceTick() {
        if (isComplete()) {
            return;
        }
        if (skipping) {
            skipElapsedTicks++;
        } else {
            elapsedTicks++;
        }
    }

    public float computeWeight(float partialTick) {
        if (isComplete()) {
            return 0.0f;
        }
        if (skipping) {
            float skipCont = ((float) skipElapsedTicks + partialTick) / (float) SKIP_FADE_TICKS;
            return frozenWeightAtSkip * (1.0f - CameraRigFrame.smoothHermite(skipCont));
        }
        float cont = Mth.clamp((float) elapsedTicks + partialTick, 0.0f, (float) durationTicks);
        return rawEnvelopeWeight(cont);
    }

    private float rawEnvelopeWeight(float continuousTick) {
        float wIn = fadeInTicks > 0
                ? CameraRigFrame.smoothHermite(continuousTick / (float) fadeInTicks)
                : 1.0f;
        float remaining = (float) durationTicks - continuousTick;
        float wOut = fadeOutTicks > 0
                ? CameraRigFrame.smoothHermite(remaining / (float) fadeOutTicks)
                : 1.0f;
        return Math.min(wIn, wOut);
    }

    public CameraTransform evaluateTransform(float partialTick) {
        if (phases.isEmpty()) {
            return null;
        }
        float cont = skipping
                ? frozenTickAtSkip
                : Mth.clamp((float) elapsedTicks + partialTick, 0.0f, (float) durationTicks);

        int idx = 0;
        for (int i = 0; i < phases.size(); i++) {
            Phase ph = phases.get(i);
            if (cont >= ph.startTick && (cont < ph.endTick || i == phases.size() - 1)) {
                idx = i;
                break;
            }
        }

        Phase active = phases.get(idx);
        CameraTransform currentPose = active.evaluator.evaluate(rig, partialTick, active.normalizedProgress(cont));
        if (idx > 0 && active.easeInTicks > 0) {
            float localAge = cont - (float) active.startTick;
            if (localAge < (float) active.easeInTicks) {
                Phase previous = phases.get(idx - 1);
                CameraTransform prevEndPose = previous.evaluator.evaluate(rig, partialTick, 1.0f);
                float alpha = CameraRigFrame.smoothHermite(localAge / (float) active.easeInTicks);
                return prevEndPose.blendTo(currentPose, alpha);
            }
        }
        return currentPose;
    }
}
