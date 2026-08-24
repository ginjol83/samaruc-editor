package com.retroeditor.controller.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import com.techsenger.jeditermfx.core.ProcessTtyConnector;
import com.techsenger.jeditermfx.core.util.TermSize;
import java.nio.charset.Charset;

/**
 * Conector entre el proceso del shell (PtyProcess, ConPTY en Windows) y el emulador de terminal.
 */
public class SamarucPtyTtyConnector extends ProcessTtyConnector {

    private final PtyProcess process;

    public SamarucPtyTtyConnector(PtyProcess process, Charset charset) {
        super(process, charset);
        this.process = process;
    }

    @Override
    public void resize(TermSize termSize) {
        if (isConnected()) {
            process.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
        }
    }

    @Override
    public boolean isConnected() {
        return process.isAlive();
    }

    @Override
    public String getName() {
        return "Local";
    }
}
