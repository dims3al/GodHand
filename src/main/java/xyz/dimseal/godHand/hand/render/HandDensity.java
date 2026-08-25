package xyz.dimseal.godHand.hand.render;

import java.util.Locale;

/** visual surface-density presets. */
public enum HandDensity {
    LOW("low", 0.32),
    NORMAL("normal", 0.50),
    HIGH("high", 0.68),
    ULTRA("ultra", 1.00);

    private final String commandName;
    private final double sampleFraction;

    HandDensity(String commandName, double sampleFraction) {
        this.commandName = commandName;
        this.sampleFraction = sampleFraction;
    }

    public String commandName() {
        return commandName;
    }

    public double sampleFraction() {
        return sampleFraction;
    }

    public static HandDensity parse(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        for (HandDensity value : values()) {
            if (value.commandName.equals(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown density '" + input + "'. Use low, normal, high, or ultra.");
    }
}
