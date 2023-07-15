package moe.mewore.saverabbit;

import lombok.RequiredArgsConstructor;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

@RequiredArgsConstructor
class CanvasMouseListener implements MouseListener {
    private final Runnable save;
    private final Runnable refresh;

    @Override
    public void mouseClicked(final MouseEvent e) {
        save.run();
    }

    @Override
    public void mousePressed(final MouseEvent e) {
    }

    @Override
    public void mouseReleased(final MouseEvent e) {
    }

    @Override
    public void mouseEntered(final MouseEvent e) {
        refresh.run();
    }

    @Override
    public void mouseExited(final MouseEvent e) {
        refresh.run();
    }
}
