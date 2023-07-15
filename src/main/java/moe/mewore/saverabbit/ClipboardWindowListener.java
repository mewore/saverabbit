package moe.mewore.saverabbit;

import lombok.RequiredArgsConstructor;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

@RequiredArgsConstructor
class ClipboardWindowListener implements WindowListener {

    private final ClipboardTimerCanvas clipboardCanvas;

    @Override
    public void windowOpened(final WindowEvent e) {

    }

    @Override
    public void windowClosing(final WindowEvent e) {

    }

    @Override
    public void windowClosed(final WindowEvent e) {

    }

    @Override
    public void windowIconified(final WindowEvent e) {

    }

    @Override
    public void windowDeiconified(final WindowEvent e) {

    }

    @Override
    public void windowActivated(final WindowEvent e) {
        clipboardCanvas.refresh();
    }

    @Override
    public void windowDeactivated(final WindowEvent e) {
        clipboardCanvas.resetTimer();
    }
}
