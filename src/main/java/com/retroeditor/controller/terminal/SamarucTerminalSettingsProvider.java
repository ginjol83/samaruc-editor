package com.retroeditor.controller.terminal;

import com.techsenger.jeditermfx.core.TerminalColor;
import com.techsenger.jeditermfx.ui.settings.DefaultSettingsProvider;
import javafx.scene.text.Font;

/**
 * Ajustes del emulador de terminal con tema oscuro, coherente con la interfaz de Samaruc.
 */
public class SamarucTerminalSettingsProvider extends DefaultSettingsProvider {

    private static final TerminalColor BACKGROUND = new TerminalColor(0x0F, 0x11, 0x18);
    private static final TerminalColor FOREGROUND = new TerminalColor(0xD8, 0xDE, 0xE9);

    @Override
    public TerminalColor getDefaultBackground() {
        return BACKGROUND;
    }

    @Override
    public TerminalColor getDefaultForeground() {
        return FOREGROUND;
    }

    @Override
    public Font getTerminalFont() {
        return Font.font("Consolas", getTerminalFontSize());
    }

    @Override
    public float getTerminalFontSize() {
        return 13.0f;
    }

    @Override
    public boolean useAntialiasing() {
        return true;
    }

    @Override
    public int maxRefreshRate() {
        return 60;
    }

    @Override
    public int getBufferMaxLinesCount() {
        return 5000;
    }
}
