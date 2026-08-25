package xyz.dimseal.godHand.hand.render;

import org.bukkit.Color;
import org.bukkit.Material;

/**
 * block-material approximation for the solid ItemDisplay renderer.
 * Particle colors can be arbitrary RGB; vanilla block items cannot, so custom
 * RGB colors are mapped to the nearest concrete color while named GodHand
 * palettes use hand-picked material families with shadow/highlight variants.
 */
public final class DisplayMaterialPalette {

    public static final int SHADOW = -1;
    public static final int PRIMARY = 0;
    public static final int HIGHLIGHT = 1;

    private DisplayMaterialPalette() {}

    public static Material materialFor(Color color, boolean shading, int tone, int salt) {
        return materialFor(color, shading, tone, salt, false);
    }

    public static Material materialFor(Color color, boolean shading, int tone, int salt, boolean palmMass) {
        if (color == null) return Material.QUARTZ_BLOCK;

        if (HandPalette.isVoid(color)) {
            // VOID: keep the solid model abyssal rather than blue/cyan.
            // Obsidian + crying obsidian carry most of the hand, with purple
            // concrete as the supernatural violet body and black as depth.
            // White is reserved for the particle aura instead of solid blocks.
            int bucket = Math.floorMod(mix(salt), 1000);
            if (bucket >= 765) return Material.PURPLE_CONCRETE;
            if (bucket >= 455) return Material.CRYING_OBSIDIAN;
            if (bucket >= 160) return Material.OBSIDIAN;
            return Material.BLACK_CONCRETE;
        }

        MaterialFamily family;
        if (same(color, HandPalette.WHITE)) {
            // White is genuinely white rather than using the sandy palette.
            family = new MaterialFamily(Material.CALCITE, Material.QUARTZ_BLOCK, Material.WHITE_CONCRETE);
        } else if (same(color, HandPalette.SAND)) {
            family = new MaterialFamily(Material.SANDSTONE, Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE);
        } else if (same(color, HandPalette.SPECTRAL)) {
            // spectral: a translucent frozen palm with denser icy
            // fingers and a pale supernatural highlight. This reads more like
            // an apparition than the old cyan/sea-lantern-heavy hand.
            family = palmMass
                    ? new MaterialFamily(Material.PACKED_ICE, Material.ICE, Material.PEARLESCENT_FROGLIGHT)
                    : new MaterialFamily(Material.BLUE_ICE, Material.PACKED_ICE, Material.PEARLESCENT_FROGLIGHT);
        } else if (same(color, HandPalette.CRIMSON)) {
            family = new MaterialFamily(Material.RED_NETHER_BRICKS, Material.RED_CONCRETE, Material.REDSTONE_BLOCK);
        } else if (same(color, HandPalette.VIOLET)) {
            // violet: the broad palm is now purpur instead of concrete;
            // the former amethyst highlight is replaced by purple concrete.
            // Crying obsidian remains the dark joint/shadow material.
            family = palmMass
                    ? new MaterialFamily(Material.CRYING_OBSIDIAN, Material.PURPUR_BLOCK, Material.PURPLE_CONCRETE)
                    : new MaterialFamily(Material.CRYING_OBSIDIAN, Material.PURPLE_CONCRETE, Material.PURPLE_CONCRETE);
        } else if (same(color, HandPalette.GOLD)) {
            family = new MaterialFamily(Material.RAW_GOLD_BLOCK, Material.GOLD_BLOCK, Material.YELLOW_CONCRETE);
        } else if (same(color, HandPalette.EMERALD)) {
            family = new MaterialFamily(Material.GREEN_CONCRETE, Material.EMERALD_BLOCK, Material.LIME_CONCRETE);
        } else if (same(color, HandPalette.CYAN)) {
            family = new MaterialFamily(Material.DARK_PRISMARINE, Material.CYAN_CONCRETE, Material.SEA_LANTERN);
        } else {
            Material nearest = nearestConcrete(color);
            family = new MaterialFamily(nearest, nearest, nearest);
        }

        if (!shading) return family.primary();
        return tone < 0 ? family.shadow() : tone > 0 ? family.highlight() : family.primary();
    }

    private static boolean same(Color a, Color b) {
        return a.getRed() == b.getRed() && a.getGreen() == b.getGreen() && a.getBlue() == b.getBlue();
    }

    private static int mix(int value) {
        int x = value * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        return (x >>> 16) ^ x;
    }

    private static Material nearestConcrete(Color color) {
        Swatch best = CONCRETE[0];
        long bestDistance = Long.MAX_VALUE;
        for (Swatch swatch : CONCRETE) {
            long dr = color.getRed() - swatch.r();
            long dg = color.getGreen() - swatch.g();
            long db = color.getBlue() - swatch.b();
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = swatch;
            }
        }
        return best.material();
    }

    // Approximate rendered dye colors. Exact resource-pack colors can differ;
    // this is intentionally a stable vanilla-family approximation for custom RGB colors.
    private static final Swatch[] CONCRETE = {
            new Swatch(Material.WHITE_CONCRETE, 207, 213, 214),
            new Swatch(Material.LIGHT_GRAY_CONCRETE, 125, 125, 115),
            new Swatch(Material.GRAY_CONCRETE, 55, 58, 62),
            new Swatch(Material.BLACK_CONCRETE, 8, 10, 15),
            new Swatch(Material.BROWN_CONCRETE, 96, 59, 31),
            new Swatch(Material.RED_CONCRETE, 142, 32, 32),
            new Swatch(Material.ORANGE_CONCRETE, 224, 97, 0),
            new Swatch(Material.YELLOW_CONCRETE, 241, 175, 21),
            new Swatch(Material.LIME_CONCRETE, 94, 169, 24),
            new Swatch(Material.GREEN_CONCRETE, 73, 91, 36),
            new Swatch(Material.CYAN_CONCRETE, 21, 119, 136),
            new Swatch(Material.LIGHT_BLUE_CONCRETE, 36, 137, 199),
            new Swatch(Material.BLUE_CONCRETE, 44, 46, 143),
            new Swatch(Material.PURPLE_CONCRETE, 100, 31, 156),
            new Swatch(Material.MAGENTA_CONCRETE, 169, 48, 159),
            new Swatch(Material.PINK_CONCRETE, 214, 101, 143)
    };

    private record MaterialFamily(Material shadow, Material primary, Material highlight) {}
    private record Swatch(Material material, int r, int g, int b) {}
}
