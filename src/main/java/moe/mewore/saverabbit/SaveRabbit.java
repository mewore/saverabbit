package moe.mewore.saverabbit;

import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
public class SaveRabbit {

    private static final String ARG_DIRECTORY = "--directory";
    private static final String ARG_TYPE = "--type";

    private static final Set<String> argSet = Set.of(ARG_DIRECTORY, ARG_TYPE);
    private static final Map<String, String> argAliasMap = new HashMap<>();

    static {
        argAliasMap.put("-d", ARG_DIRECTORY);
        argAliasMap.put("--dir", ARG_DIRECTORY);
        argAliasMap.put("-t", ARG_TYPE);
    }

    private static final Color SUCCESS_COLOR = new Color(.1f, .4f, .1f);

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private final AtomicReference<LocalDate> drawingDate = new AtomicReference<>(LocalDate.now());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final JTextField dateInput = new ConstrainedTextField(drawingDate.toString(), this::validateDate);

    private final JLabel statusLabel = new JLabel("hi");

    private final AtomicInteger drawingHour = new AtomicInteger(LocalTime.now().getHour());
    private final JTextField hourInput = new ConstrainedTextField(String.valueOf(drawingHour.get()),
            this::validateHour);

    private final File directory;
    private final String drawingType;

    public static void main(final String[] args) {
        final Map<String, String> argMap = new HashMap<>();
        @NonNull String lastArg = "";
        for (final String arg : args) {
            if (argSet.contains(lastArg)) {
                argMap.put(lastArg, arg);
                lastArg = "";
                continue;
            }
            lastArg = argAliasMap.getOrDefault(arg, arg);
        }
        new SaveRabbit(new File(argMap.getOrDefault(ARG_DIRECTORY, ".")), argMap.getOrDefault(ARG_TYPE, "rabbit")).show();
    }

    private void show() {
        final JFrame frame = new JFrame("Save " + drawingType);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        final JPanel northPanel = new JPanel();
        northPanel.setLayout(new GridLayout(2, 3));
        final Runnable decrementDate = () -> {
            drawingDate.getAndUpdate(date -> date.minusDays(1L));
            dateInput.setText(drawingDate.toString());
        };
        northPanel.add(makeButton("<", decrementDate));
        northPanel.add(dateInput);
        northPanel.add(makeButton(">", this::incrementDate));

        final Runnable decrementIndex = () -> {
            final int newIndex = drawingHour.updateAndGet(index -> {
                if (index <= 0) {
                    decrementDate.run();
                    return 23;
                }
                return index - 1;
            });
            hourInput.setText(String.valueOf(newIndex));
        };
        northPanel.add(makeButton("<", decrementIndex));
        hourInput.setPreferredSize(new Dimension(100, 32));
        northPanel.add(hourInput);
        northPanel.add(makeButton(">", this::incrementIndex));
        frame.getContentPane().add(northPanel, BorderLayout.NORTH);

        final var southPanel = new JPanel();
        southPanel.add(statusLabel);
        frame.getContentPane().add(southPanel, BorderLayout.SOUTH);

        final var clipboardCanvas = new ClipboardTimerCanvas(SaveRabbit::getImageFromClipboard, statusLabel);
        clipboardCanvas.resetTimer();
        frame.getContentPane().add(clipboardCanvas, BorderLayout.CENTER);

        executorService.scheduleAtFixedRate(clipboardCanvas::refresh, 0L, 10L, TimeUnit.SECONDS);
        frame.setVisible(true);

        final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.addFlavorListener(e -> {
            final Transferable transferable = clipboard.getContents(null);
            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                clipboardCanvas.resetTimer();
                clipboardCanvas.update();
            }
        });
        clipboardCanvas.addMouseListener(new CanvasMouseListener(this::saveImage, clipboardCanvas::refresh));

        frame.addWindowListener(new ClipboardWindowListener(clipboardCanvas));
    }

    private void incrementIndex() {
        final int newIndex = drawingHour.updateAndGet(index -> {
            if (index >= 23) {
                incrementDate();
                return 0;
            }
            return index + 1;
        });
        hourInput.setText(String.valueOf(newIndex));
    }

    private void incrementDate() {
        drawingDate.getAndUpdate(date -> date.plusDays(1L));
        dateInput.setText(drawingDate.toString());
    }

    private void saveImage() {
        final @Nullable BufferedImage clipboardImage = getImageFromClipboard();
        if (clipboardImage == null) {
            return;
        }
        final String filename = String.format("%s-%s-%s.png", drawingType, dateInput.getText(), hourInput.getText());
        System.out.println("Saving as: " + filename);
        try {
            ImageIO.write(clipboardImage, "png", directory.toPath().resolve(filename).toFile());
            statusLabel.setForeground(SUCCESS_COLOR);
            statusLabel.setText("Saved to: " + filename);
            incrementIndex();
        } catch (final IOException ex) {
            statusLabel.setForeground(Color.RED);
            statusLabel.setText("(" + ex.getMessage() + ") Failed to save to: " + filename);
            System.err.println("Failed to save to: " + filename);
            ex.printStackTrace();
        }
    }

    private static JButton makeButton(final String label, final Runnable action) {
        final JButton button = new JButton(label);
        button.setMaximumSize(new Dimension(16, 32));
        button.addActionListener(e -> action.run());
        return button;
    }

    /**
     * Get an image off the system clipboard.
     *
     * @return Returns an Image if successful; otherwise returns null.
     */
    public static BufferedImage getImageFromClipboard() {
        final Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
        if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            try {
                return (BufferedImage) transferable.getTransferData(DataFlavor.imageFlavor);
            } catch (final UnsupportedFlavorException | IOException | ClassCastException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    private String validateDate(final String value) {
        try {
            drawingDate.set(LocalDate.parse(value.trim(), DATE_FORMATTER));
            return null;
        } catch (final DateTimeParseException e) {
            return "The date must be in the form yyyy-MM-dd - year, month and day (" + e.getMessage() + ")";
        }
    }

    private String validateHour(final String value) {
        final int intValue;
        try {
            intValue = Integer.parseInt(value.trim());
        } catch (final NumberFormatException e) {
            return "The value must be an integer";
        }
        if (intValue < 0 || intValue >= 24) {
            return "The hour of the day must be from 0 to 23 (inclusive)";
        }
        drawingHour.set(intValue);
        return null;
    }

}
