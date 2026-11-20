package com.retroeditor.service.sprite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.retroeditor.model.SpriteAnimation;
import com.retroeditor.model.SpriteAnimationStep;

class SpriteAnimationServiceTest {

    private final SpriteAnimationService service = new SpriteAnimationService();

    private SpriteAnimation animation(int loopStart, int loopEnd, int... frameIndices) {
        SpriteAnimation animation = new SpriteAnimation("walk");
        List<SpriteAnimationStep> steps = new ArrayList<>();
        for (int frame : frameIndices) {
            steps.add(new SpriteAnimationStep(frame, 0, 0));
        }
        animation.setSteps(steps);
        animation.setLoopStart(loopStart);
        animation.setLoopEnd(loopEnd);
        return animation;
    }

    @Test
    void normalizeRemovesInvalidStepsAndClampsOffsets() {
        SpriteAnimation animation = animation(0, -1, 0, 4, -1, 2);
        animation.getSteps().get(0).setOffsetX(200);
        animation.getSteps().get(0).setOffsetY(-200);

        service.normalize(animation, 3);

        assertEquals(2, animation.getSteps().size());
        assertEquals(0, animation.getSteps().get(0).getFrameIndex());
        assertEquals(SpriteAnimationService.MAX_OFFSET, animation.getSteps().get(0).getOffsetX());
        assertEquals(SpriteAnimationService.MIN_OFFSET, animation.getSteps().get(0).getOffsetY());
        assertEquals(1, animation.getLoopEnd());
    }

    @Test
    void normalizeSwapsInvertedLoopMarkers() {
        SpriteAnimation animation = animation(4, 1, 0, 1, 2, 3, 4);

        service.normalize(animation, 5);

        assertEquals(1, animation.getLoopStart());
        assertEquals(4, animation.getLoopEnd());
    }

    @Test
    void nextStepWrapsAtLoopEndAndReturnsToLoopStart() {
        SpriteAnimation animation = animation(1, 3, 0, 1, 2, 3, 4);

        assertEquals(1, service.nextStepIndex(animation, 0));
        assertEquals(2, service.nextStepIndex(animation, 1));
        assertEquals(3, service.nextStepIndex(animation, 2));
        assertEquals(1, service.nextStepIndex(animation, 3));
        assertEquals(1, service.nextStepIndex(animation, 4));
    }

    @Test
    void nonLoopingAnimationStopsAtLastStep() {
        SpriteAnimation animation = animation(0, 1, 0, 1);
        animation.setLoop(false);

        assertEquals(1, service.nextStepIndex(animation, 0));
        assertEquals(-1, service.nextStepIndex(animation, 1));
    }

    @Test
    void insertingFrameShiftsAnimationReferences() {
        SpriteAnimation animation = animation(0, 1, 0, 2);

        service.insertFrameAt(List.of(animation), 1);

        assertEquals(0, animation.getSteps().get(0).getFrameIndex());
        assertEquals(3, animation.getSteps().get(1).getFrameIndex());
    }

    @Test
    void removingFrameDropsItsStepsAndShiftsLaterReferences() {
        SpriteAnimation animation = animation(0, 1, 0, 1, 2);

        service.removeFrameAt(List.of(animation), 1, 2);

        assertEquals(2, animation.getSteps().size());
        assertEquals(0, animation.getSteps().get(0).getFrameIndex());
        assertEquals(1, animation.getSteps().get(1).getFrameIndex());
        assertEquals(1, animation.getLoopEnd());
    }

    @Test
    void movingFrameRemapsReferencesInBothDirections() {
        SpriteAnimation forward = animation(0, 2, 0, 1, 2, 3);
        service.remapAfterFrameMove(List.of(forward), 1, 3);
        assertEquals(List.of(0, 3, 1, 2), frameIndices(forward));

        SpriteAnimation backward = animation(0, 2, 0, 1, 2, 3);
        service.remapAfterFrameMove(List.of(backward), 3, 1);
        assertEquals(List.of(0, 2, 3, 1), frameIndices(backward));
    }

    @Test
    void copyIsIndependentFromOriginal() {
        SpriteAnimation original = animation(0, 1, 0, 1);
        original.setLoop(false);
        original.getSteps().get(0).setOffsetX(3);

        SpriteAnimation copy = service.copy(original);

        assertNotSame(original, copy);
        assertNotSame(original.getSteps().get(0), copy.getSteps().get(0));
        assertFalse(copy.isLoop());
        assertEquals(3, copy.getSteps().get(0).getOffsetX());
        copy.getSteps().get(0).setOffsetX(1);
        assertEquals(3, original.getSteps().get(0).getOffsetX());
    }

    private List<Integer> frameIndices(SpriteAnimation animation) {
        List<Integer> result = new ArrayList<>();
        for (SpriteAnimationStep step : animation.getSteps()) {
            assertTrue(step != null);
            result.add(step.getFrameIndex());
        }
        return result;
    }
}
