package xyz.dimseal.godHand.hand.skeleton;

import java.util.Locale;

public enum HandPose {
    OPEN,
    RELAXED,
    FIST,
    POINT,
    BIRD,
    THUMBS_UP,
    THUMBS_DOWN,
    CLAW;

    public static HandPose parse(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("thumbsup") || normalized.equals("thumbup")) normalized = "thumbs_up";
        if (normalized.equals("thumbsdown") || normalized.equals("thumbdown")) normalized = "thumbs_down";
        try {
            return valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown pose '" + input + "'. Use open, relaxed, fist, point, bird, thumbs_up, thumbs_down, or claw.");
        }
    }

    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
