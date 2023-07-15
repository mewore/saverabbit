package moe.mewore.saverabbit;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

class ClipboardTimerCanvas extends Canvas {

    private static final Color TIMER_COLOR = new Color(1f, .2f, .8f, .5f);

    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private int lastSecondsY = 0;
    private int seconds = 0;
    private boolean needsToRedrawImage = false;

    private final Supplier<BufferedImage> clipboardProvider;
    private final JLabel statusLabel;
    private @Nullable BufferedImage lastClipboard = null;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    private final Cursor availableCursor = new Cursor(Cursor.HAND_CURSOR);
    private final Cursor unavailableCursor = new Cursor(Cursor.WAIT_CURSOR);

    public ClipboardTimerCanvas(final Supplier<BufferedImage> clipboardProvider, final JLabel statusLabel) {
        this.clipboardProvider = clipboardProvider;
        this.statusLabel = statusLabel;
        setCursor(unavailableCursor);
    }

    public void update() {
        final Graphics graphics = getGraphics();
        if (graphics != null) {
            update(graphics);
        }
    }

    public void refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            if (updateClipboardImage()) {
                needsToRedrawImage = true;
                statusLabel.setForeground(Color.DARK_GRAY);
                statusLabel.setText("[Not saved]");
            }
        } finally {
            if (lastClipboard == null) {
                statusLabel.setForeground(Color.DARK_GRAY);
                statusLabel.setText("[No image]");
            }
            refreshing.set(false);
        }
    }

    @Override
    public void paint(final Graphics graphics) {
        paint(graphics, true);
    }

    private void paint(final Graphics graphics, final boolean automatic) {
        if (automatic) {
            needsToRedrawImage = true;
        }
        final int width = getWidth();
        final int height = getHeight();

        if (needsToRedrawImage) {
            drawClipboardImage(width, height, graphics);
            needsToRedrawImage = false;
        }

        paintTimer(graphics);
    }

    private void clear(final int width, final int height, final @Nullable Graphics graphics) {
        lastSecondsY = -1;
        if (graphics != null) {
            graphics.clearRect(0, 0, width, height);
            needsToRedrawImage = true;
        }
    }

    private void drawClipboardImage(final int width, final int height, final Graphics graphics) {
        lastSecondsY = -1;
        clear(width, height, graphics);
        if (lastClipboard == null) {
            return;
        }
        final int originalWidth = lastClipboard.getWidth();
        final int originalHeight = lastClipboard.getHeight();
        final int newWidth;
        final int newHeight;
        if (originalWidth * height >= width * originalHeight) {
            newWidth = width;
            newHeight = (originalHeight * width) / originalWidth;
        } else {
            newWidth = (originalWidth * height) / originalHeight;
            newHeight = height;
        }
        final Image scaled = lastClipboard.getScaledInstance(newWidth, newHeight, 0);
        graphics.drawImage(scaled, (width - newWidth) / 2, (height - newHeight) / 2, null);
    }

    private boolean updateClipboardImage() {
        final long from = System.nanoTime();
        final BufferedImage newClipboard = clipboardProvider.get();
        if ((lastClipboard == null) != (newClipboard == null) || (lastClipboard != null && imagesAreDifferent(
                lastClipboard, newClipboard))) {
            lastClipboard = newClipboard;
            setCursor(lastClipboard != null ? availableCursor : unavailableCursor);
            System.out.println("Get clipboard time: " + (System.nanoTime() - from) / 1000000L + " ms");
            return true;
        }
        return false;
    }

    private static boolean imagesAreDifferent(final @NonNull BufferedImage first, final @NonNull BufferedImage second) {
        if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
            return true;
        }
        for (int x = 0; x < first.getWidth(); x++) {
            for (int y = 0; y < first.getHeight(); y++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void incrementTimer() {
        seconds = (seconds + 1) % 60;
        final Graphics graphics = getGraphics();
        if (seconds == 0) {
            clear(getWidth(), getHeight(), graphics);
            if (graphics != null) {
                update(graphics);
            }
            return;
        }
        if (graphics != null) {
            paint(graphics, false);
        }
    }

    public void resetTimer() {
        seconds = 0;
        needsToRedrawImage = true;
        executorService.shutdown();
        executorService = Executors.newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(this::incrementTimer, 1L, 1L, TimeUnit.SECONDS);
        final Graphics graphics = getGraphics();
        if (graphics != null) {
            update(graphics);
        }
    }

    private void paintTimer(final Graphics graphics) {
        graphics.setColor(TIMER_COLOR);
        final int newSecondsY = (getHeight() * seconds) / 60 - 1;
        graphics.fillRect(0, lastSecondsY + 1, getWidth(), (newSecondsY - lastSecondsY));
        lastSecondsY = newSecondsY;
    }
}
