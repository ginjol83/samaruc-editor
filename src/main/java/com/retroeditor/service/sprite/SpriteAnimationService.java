package com.retroeditor.service.sprite;

import java.util.ArrayList;
import java.util.List;

import com.retroeditor.model.SpriteAnimation;
import com.retroeditor.model.SpriteAnimationStep;

/**
 * Lógica de las secuencias de animación: validación, avance respetando el
 * tramo de bucle y mantenimiento de los índices de frame al editar el
 * filmstrip.
 */
public class SpriteAnimationService {

    public static final int MIN_OFFSET = -128;
    public static final int MAX_OFFSET = 127;

    public void normalize(SpriteAnimation animation, int frameCount) {
        if (animation == null) return;
        List<SpriteAnimationStep> normalized = new ArrayList<>();
        if (animation.getSteps() != null) {
            for (SpriteAnimationStep step : animation.getSteps()) {
                if (step == null) continue;
                if (frameCount <= 0 || step.getFrameIndex() < 0 || step.getFrameIndex() >= frameCount) continue;
                step.setOffsetX(clamp(step.getOffsetX(), MIN_OFFSET, MAX_OFFSET));
                step.setOffsetY(clamp(step.getOffsetY(), MIN_OFFSET, MAX_OFFSET));
                normalized.add(step);
            }
        }
        animation.setSteps(normalized);

        int lastStep = Math.max(0, normalized.size() - 1);
        animation.setLoopStart(clamp(animation.getLoopStart(), 0, lastStep));
        int loopEnd = animation.getLoopEnd() < 0 ? lastStep : clamp(animation.getLoopEnd(), 0, lastStep);
        if (animation.getLoopStart() > loopEnd) {
            int swap = animation.getLoopStart();
            animation.setLoopStart(loopEnd);
            loopEnd = swap;
        }
        animation.setLoopEnd(loopEnd);
    }

    public int nextStepIndex(SpriteAnimation animation, int currentStep) {
        if (animation == null || animation.getSteps() == null || animation.getSteps().isEmpty()) return -1;
        int stepCount = animation.getSteps().size();
        if (!animation.isLoop()) {
            return currentStep < stepCount - 1 ? currentStep + 1 : -1;
        }
        int loopStart = clamp(animation.getLoopStart(), 0, stepCount - 1);
        int loopEnd = animation.getLoopEnd() < 0
                ? stepCount - 1
                : clamp(animation.getLoopEnd(), 0, stepCount - 1);
        if (loopStart > loopEnd) {
            int swap = loopStart;
            loopStart = loopEnd;
            loopEnd = swap;
        }
        if (currentStep < 0) return 0;
        if (currentStep < loopStart) return currentStep + 1;
        if (currentStep >= loopEnd) return loopStart;
        return currentStep + 1;
    }

    public void insertFrameAt(List<SpriteAnimation> animations, int insertedIndex) {
        if (animations == null || insertedIndex < 0) return;
        for (SpriteAnimation animation : animations) {
            if (animation == null || animation.getSteps() == null) continue;
            for (SpriteAnimationStep step : animation.getSteps()) {
                if (step != null && step.getFrameIndex() >= insertedIndex) {
                    step.setFrameIndex(step.getFrameIndex() + 1);
                }
            }
        }
    }

    public void removeFrameAt(List<SpriteAnimation> animations, int removedIndex, int newFrameCount) {
        if (animations == null || removedIndex < 0) return;
        for (SpriteAnimation animation : animations) {
            if (animation == null || animation.getSteps() == null) continue;
            List<SpriteAnimationStep> kept = new ArrayList<>();
            for (SpriteAnimationStep step : animation.getSteps()) {
                if (step == null || step.getFrameIndex() == removedIndex) continue;
                if (step.getFrameIndex() > removedIndex) {
                    step.setFrameIndex(step.getFrameIndex() - 1);
                }
                kept.add(step);
            }
            animation.setSteps(kept);
            normalize(animation, newFrameCount);
        }
    }

    public void remapAfterFrameMove(List<SpriteAnimation> animations, int fromIndex, int toIndex) {
        if (animations == null || fromIndex == toIndex || fromIndex < 0 || toIndex < 0) return;
        for (SpriteAnimation animation : animations) {
            if (animation == null || animation.getSteps() == null) continue;
            for (SpriteAnimationStep step : animation.getSteps()) {
                if (step == null) continue;
                int oldIndex = step.getFrameIndex();
                if (oldIndex == fromIndex) {
                    step.setFrameIndex(toIndex);
                } else if (fromIndex < toIndex && oldIndex > fromIndex && oldIndex <= toIndex) {
                    step.setFrameIndex(oldIndex - 1);
                } else if (fromIndex > toIndex && oldIndex >= toIndex && oldIndex < fromIndex) {
                    step.setFrameIndex(oldIndex + 1);
                }
            }
        }
    }

    public List<SpriteAnimation> copyAll(List<SpriteAnimation> source) {
        List<SpriteAnimation> copy = new ArrayList<>();
        if (source == null) return copy;
        for (SpriteAnimation animation : source) {
            if (animation != null) copy.add(copy(animation));
        }
        return copy;
    }

    public SpriteAnimation copy(SpriteAnimation source) {
        SpriteAnimation copy = new SpriteAnimation(source.getName());
        copy.setLoop(source.isLoop());
        copy.setLoopStart(source.getLoopStart());
        copy.setLoopEnd(source.getLoopEnd());
        List<SpriteAnimationStep> steps = new ArrayList<>();
        if (source.getSteps() != null) {
            for (SpriteAnimationStep step : source.getSteps()) {
                if (step != null) {
                    steps.add(new SpriteAnimationStep(step.getFrameIndex(), step.getOffsetX(), step.getOffsetY()));
                }
            }
        }
        copy.setSteps(steps);
        return copy;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
