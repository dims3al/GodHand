package xyz.dimseal.godHand.hand.skeleton;

import java.util.Locale;

public enum HandDigit {
    THUMB,
    INDEX,
    MIDDLE,
    RING,
    PINKY;

    public static HandDigit parse(String input) {
        try {
            return valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown finger '" + input + "'. Use thumb, index, middle, ring, or pinky.");
        }
    }

    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
