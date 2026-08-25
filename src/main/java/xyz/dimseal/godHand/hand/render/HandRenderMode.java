package xyz.dimseal.godHand.hand.render;

import java.util.Locale;

/** Selects the visual backend used for the main Hand. */
public enum HandRenderMode {
    PARTICLES("particles"),
    ITEM_DISPLAYS("itemdisplays");

    private final String commandName;

    HandRenderMode(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
    }

    public static HandRenderMode parse(String input) {
        String normalized = input.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return switch (normalized) {
            case "particle", "particles", "dust" -> PARTICLES;
            case "itemdisplay", "itemdisplays", "display", "displays", "solid", "blocks" -> ITEM_DISPLAYS;
            default -> throw new IllegalArgumentException(
                    "Unknown renderer '" + input + "'. Use particles or itemdisplays."
            );
        };
    }
}
