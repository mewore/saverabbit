package moe.mewore.saverabbit;

import lombok.Getter;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Function;

public class ConstrainedTextField extends JTextField {

    private final Color backgroundValid;
    private final Color backgroundInvalid;

    private final Function<String, @Nullable String> validator;

    @Getter
    private boolean valid;

    private boolean validating = false;

    public ConstrainedTextField(final String initialValue, final Function<String, @Nullable String> validator) {
        super(initialValue);
        this.validator = validator;

        backgroundValid = getBackground();
        final float[] bgInvalidComponents = blend(backgroundValid.getComponents(null), new float[]{1f, 0f, 0f}, .3f);
        backgroundInvalid = bgInvalidComponents.length >= 4
                ? new Color(bgInvalidComponents[0], bgInvalidComponents[1], bgInvalidComponents[2],
                bgInvalidComponents[3])
                : new Color(bgInvalidComponents[0], bgInvalidComponents[1], bgInvalidComponents[2]);

        setHorizontalAlignment(SwingConstants.CENTER);
        addCaretListener(e -> validateValue());
    }

    private static float[] blend(final float[] first, final float[] second,
                                 @SuppressWarnings("SameParameterValue") final float secondAmount) {
        final int n = Math.min(first.length, second.length);
        final float firstAmount = 1f - secondAmount;
        for (int i = 0; i < n; i++) {
            first[i] = first[i] * firstAmount + second[i] * secondAmount;
        }
        return first;
    }

    private void validateValue() {
        if (validator == null) {
            return;
        }
        try {
            validating = true;
            final @Nullable String validationResult = validator.apply(getText());
            if (validationResult != null && !validationResult.isEmpty()) {
                setToolTipText(validationResult);
                valid = false;
                setBackground(backgroundInvalid);
                return;
            }
            setToolTipText(null);
            setBackground(backgroundValid);
            valid = true;
        } finally {
            validating = false;
        }
    }

    @Override
    public void setText(final String t) {
        super.setText(t);
        if (!validating) {
            validateValue();
        }
    }
}
