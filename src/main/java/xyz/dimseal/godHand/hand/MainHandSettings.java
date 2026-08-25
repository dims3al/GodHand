package xyz.dimseal.godHand.hand;

import org.bukkit.Color;
import xyz.dimseal.godHand.hand.render.HandDensity;
import xyz.dimseal.godHand.hand.render.HandPalette;
import xyz.dimseal.godHand.hand.render.HandRenderMode;
import xyz.dimseal.godHand.hand.render.WristStyle;

import java.util.Locale;

/** Runtime defaults for the primary Hand and all preconfigured actions. */
public final class MainHandSettings {

    public enum Preset {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high"),
        CUSTOM("custom");

        private final String configName;

        Preset(String configName) {
            this.configName = configName;
        }

        public String configName() {
            return configName;
        }

        public static Preset parse(String value) {
            if (value == null) return HIGH;
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "low" -> LOW;
                case "medium", "med" -> MEDIUM;
                case "high" -> HIGH;
                case "custom" -> CUSTOM;
                default -> throw new IllegalArgumentException("Preset must be low, medium, high, or custom.");
            };
        }
    }

    public static final double DEFAULT_SCALE = 3.0;
    public static final HandDensity DEFAULT_DENSITY = HandDensity.ULTRA;
    public static final Color DEFAULT_COLOR = HandPalette.WHITE;
    public static final boolean DEFAULT_SHADING = true;
    public static final boolean DEFAULT_FORCE_PARTICLES = true;
    public static final WristStyle DEFAULT_MODEL = WristStyle.ANATOMICAL;
    public static final HandRenderMode DEFAULT_RENDER_MODE = HandRenderMode.ITEM_DISPLAYS;

    private double scale;
    private HandDensity density;
    private Color color;
    private boolean shading;
    private boolean forceParticles;
    private WristStyle wristStyle;
    private HandRenderMode renderMode;
    private Preset preset;

    public MainHandSettings() {
        reset();
    }

    /** The built-in default is the high preset. */
    public void reset() {
        applyPreset(Preset.HIGH);
    }

    public void applyPreset(Preset preset) {
        if (preset == null || preset == Preset.CUSTOM) {
            throw new IllegalArgumentException("Choose low, medium, or high for a preset.");
        }
        this.preset = preset;
        this.scale = DEFAULT_SCALE;
        this.color = DEFAULT_COLOR;
        this.renderMode = HandRenderMode.ITEM_DISPLAYS;
        switch (preset) {
            case LOW -> {
                this.density = HandDensity.LOW;
                this.shading = false;
                this.forceParticles = false;
                this.wristStyle = WristStyle.LEGACY;
            }
            case MEDIUM -> {
                this.density = HandDensity.NORMAL;
                this.shading = true;
                this.forceParticles = false;
                this.wristStyle = WristStyle.ANATOMICAL;
            }
            case HIGH -> {
                this.density = HandDensity.ULTRA;
                this.shading = true;
                this.forceParticles = true;
                this.wristStyle = WristStyle.ANATOMICAL;
            }
            default -> throw new IllegalStateException("Unexpected preset " + preset);
        }
    }

    public void loadCustom(
            double scale,
            HandDensity density,
            Color color,
            boolean shading,
            boolean forceParticles,
            WristStyle model,
            HandRenderMode renderMode
    ) {
        validateScale(scale);
        if (density == null || color == null || model == null || renderMode == null) {
            throw new IllegalArgumentException("Custom settings cannot contain null values.");
        }
        this.scale = scale;
        this.density = density;
        this.color = color;
        this.shading = shading;
        this.forceParticles = forceParticles;
        this.wristStyle = model;
        this.renderMode = renderMode;
        this.preset = Preset.CUSTOM;
    }

    public void applyTo(ParticleHand hand) {
        if (hand == null) return;
        hand.setScale(scale);
        hand.setDensity(density);
        hand.setBaseColor(color);
        hand.setShadingEnabled(shading);
        hand.setForceParticles(forceParticles);
        hand.setWristStyle(wristStyle);
        hand.setRenderMode(renderMode);
    }

    public Preset getPreset() {
        return preset;
    }

    private void markCustom() {
        preset = Preset.CUSTOM;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        validateScale(scale);
        this.scale = scale;
        markCustom();
    }

    private static void validateScale(double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0 || scale > 64.0) {
            throw new IllegalArgumentException("Scale must be greater than 0 and at most 64 blocks.");
        }
    }

    public HandDensity getDensity() {
        return density;
    }

    public void setDensity(HandDensity density) {
        if (density == null) throw new IllegalArgumentException("Density cannot be null.");
        this.density = density;
        markCustom();
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        if (color == null) throw new IllegalArgumentException("Color cannot be null.");
        this.color = color;
        markCustom();
    }

    public boolean isShading() {
        return shading;
    }

    public void setShading(boolean shading) {
        this.shading = shading;
        markCustom();
    }

    public boolean isForceParticles() {
        return forceParticles;
    }

    public void setForceParticles(boolean forceParticles) {
        this.forceParticles = forceParticles;
        markCustom();
    }

    public WristStyle getWristStyle() {
        return wristStyle;
    }

    public void setWristStyle(WristStyle wristStyle) {
        if (wristStyle == null) throw new IllegalArgumentException("Model cannot be null.");
        this.wristStyle = wristStyle;
        markCustom();
    }

    public HandRenderMode getRenderMode() {
        return renderMode;
    }

    public void setRenderMode(HandRenderMode renderMode) {
        if (renderMode == null) throw new IllegalArgumentException("Render mode cannot be null.");
        this.renderMode = renderMode;
        markCustom();
    }
}
