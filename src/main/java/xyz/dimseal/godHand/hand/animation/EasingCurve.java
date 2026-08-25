package xyz.dimseal.godHand.hand.animation;

import java.util.Locale;

/** Time-domain easing curves for joint transitions. */
public enum EasingCurve {
    LINEAR,
    SMOOTH,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT;

    public double apply(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return switch (this) {
            case LINEAR -> t;
            case SMOOTH -> t * t * (3.0 - 2.0 * t);
            case EASE_IN -> t * t * t;
            case EASE_OUT -> 1.0 - Math.pow(1.0 - t, 3.0);
            case EASE_IN_OUT -> t < 0.5
                    ? 4.0 * t * t * t
                    : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
        };
    }

    public static EasingCurve parse(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return switch (normalized) {
            case "linear" -> LINEAR;
            case "smooth", "smoothstep" -> SMOOTH;
            case "easein", "in" -> EASE_IN;
            case "easeout", "out" -> EASE_OUT;
            case "easeinout", "inout" -> EASE_IN_OUT;
            default -> throw new IllegalArgumentException(
                    "Unknown easing '" + input + "'. Use linear, smooth, easein, easeout, or easeinout."
            );
        };
    }

    public String commandName() {
        return switch (this) {
            case LINEAR -> "linear";
            case SMOOTH -> "smooth";
            case EASE_IN -> "easein";
            case EASE_OUT -> "easeout";
            case EASE_IN_OUT -> "easeinout";
        };
    }
}
