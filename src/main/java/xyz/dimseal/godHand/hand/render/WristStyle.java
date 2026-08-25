package xyz.dimseal.godHand.hand.render;

import java.util.Locale;

/** Selects the wrist/forearm particle geometry. */
public enum WristStyle {
    ANATOMICAL,
    LEGACY;

    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WristStyle parse(String value) {
        if (value == null) throw new IllegalArgumentException("Wrist style cannot be null.");
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "anatomical", "detailed", "new" -> ANATOMICAL;
            case "legacy", "classic", "box", "old" -> LEGACY;
            default -> throw new IllegalArgumentException("Wrist style must be anatomical or legacy.");
        };
    }
}
