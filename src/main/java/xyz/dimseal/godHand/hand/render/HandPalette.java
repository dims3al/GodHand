package xyz.dimseal.godHand.hand.render;

import org.bukkit.Color;

import java.util.Locale;

/** base-color presets and deterministic shade generation. */
public final class HandPalette {

    public static final Color WHITE = Color.fromRGB(245, 245, 255);
    public static final Color SPECTRAL = Color.fromRGB(198, 236, 255);
    public static final Color CRIMSON = Color.fromRGB(132, 8, 28);
    public static final Color VOID = Color.fromRGB(4, 2, 8);
    public static final Color EMERALD = Color.fromRGB(70, 220, 145);
    public static final Color VIOLET = Color.fromRGB(108, 38, 204);
    public static final Color GOLD = Color.fromRGB(255, 190, 55);
    public static final Color CYAN = Color.fromRGB(70, 195, 255);
    public static final Color SAND = Color.fromRGB(222, 184, 135);

    private HandPalette() {
    }

    public static Color parsePreset(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "spectral", "ghost", "spirit" -> SPECTRAL;
            case "default", "white" -> WHITE;
            case "crimson", "darkcrimson", "blood", "red" -> CRIMSON;
            case "violet", "purple", "amethyst" -> VIOLET;
            case "void", "black" -> VOID;
            case "gold" -> GOLD;
            case "emerald", "green" -> EMERALD;
            case "cyan", "blue" -> CYAN;
            case "sand", "tan", "peach" -> SAND;
            default -> throw new IllegalArgumentException("Unknown color preset '" + input + "'. Use white, spectral, crimson, violet, void, gold, emerald, cyan, or sand.");
        };
    }

    public static String presetName(Color color) {
        if (same(color, WHITE)) return "white";
        if (same(color, SPECTRAL)) return "spectral";
        if (same(color, CRIMSON)) return "crimson";
        if (same(color, VIOLET)) return "violet";
        if (same(color, VOID)) return "void";
        if (same(color, GOLD)) return "gold";
        if (same(color, EMERALD)) return "emerald";
        if (same(color, CYAN)) return "cyan";
        if (same(color, SAND)) return "sand";
        return null;
    }

    public static String describe(Color color) {
        String preset = presetName(color);
        if (preset != null) return preset;
        return "custom RGB " + color.getRed() + "," + color.getGreen() + "," + color.getBlue();
    }


    public static boolean isSpectral(Color color) {
        return same(color, SPECTRAL);
    }

    public static boolean isCrimson(Color color) {
        return same(color, CRIMSON);
    }

    public static boolean isViolet(Color color) {
        return same(color, VIOLET);
    }

    public static boolean isVoid(Color color) {
        return color != null && color.getRed() == VOID.getRed()
                && color.getGreen() == VOID.getGreen()
                && color.getBlue() == VOID.getBlue();
    }

    public static boolean isEmerald(Color color) {
        return color != null && color.getRed() == EMERALD.getRed()
                && color.getGreen() == EMERALD.getGreen()
                && color.getBlue() == EMERALD.getBlue();
    }

    private static boolean same(Color a, Color b) {
        return a != null && b != null
                && a.getRed() == b.getRed()
                && a.getGreen() == b.getGreen()
                && a.getBlue() == b.getBlue();
    }

    public static Color parseRgb(int red, int green, int blue) {
        validateChannel(red, "red");
        validateChannel(green, "green");
        validateChannel(blue, "blue");
        return Color.fromRGB(red, green, blue);
    }

    private static void validateChannel(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " must be between 0 and 255.");
        }
    }

    public static Color shade(Color base, double brightness, double whiteMix) {
        brightness = clamp(brightness, 0.0, 1.35);
        whiteMix = clamp(whiteMix, 0.0, 1.0);

        int r = shadeChannel(base.getRed(), brightness, whiteMix);
        int g = shadeChannel(base.getGreen(), brightness, whiteMix);
        int b = shadeChannel(base.getBlue(), brightness, whiteMix);
        return Color.fromRGB(r, g, b);
    }

    private static int shadeChannel(int channel, double brightness, double whiteMix) {
        double scaled = channel * brightness;
        double mixed = scaled + (255.0 - scaled) * whiteMix;
        return (int) Math.round(clamp(mixed, 0.0, 255.0));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
