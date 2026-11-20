package com.retroeditor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Secuencia nombrada de frames reutilizables. Cada paso puede desplazar su
 * frame y la animación define el tramo que se repite en bucle.
 */
public class SpriteAnimation {

    private String name = "";
    private boolean loop = true;
    private int loopStart;
    private int loopEnd = -1;
    private List<SpriteAnimationStep> steps = new ArrayList<>();

    public SpriteAnimation() {
    }

    public SpriteAnimation(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isLoop() { return loop; }
    public void setLoop(boolean loop) { this.loop = loop; }
    public int getLoopStart() { return loopStart; }
    public void setLoopStart(int loopStart) { this.loopStart = loopStart; }
    public int getLoopEnd() { return loopEnd; }
    public void setLoopEnd(int loopEnd) { this.loopEnd = loopEnd; }
    public List<SpriteAnimationStep> getSteps() { return steps; }
    public void setSteps(List<SpriteAnimationStep> steps) {
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
    }

    @Override
    public String toString() {
        return name != null ? name : "";
    }
}
