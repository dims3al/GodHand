package xyz.dimseal.godHand.hand;

import xyz.dimseal.godHand.hand.render.WristStyle;
import xyz.dimseal.godHand.model.ModelPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dense parametric palm plus selectable legacy/anatomical wrist geometry. */
public final class HandBodyModel {

    private final List<ModelPoint> bodyPoints;
    private final List<ModelPoint> landmarkPoints;

    private HandBodyModel(List<ModelPoint> bodyPoints, List<ModelPoint> landmarkPoints) {
        this.bodyPoints = Collections.unmodifiableList(bodyPoints);
        this.landmarkPoints = Collections.unmodifiableList(landmarkPoints);
    }

    public static HandBodyModel create() {
        return create(WristStyle.ANATOMICAL);
    }

    public static HandBodyModel create(WristStyle wristStyle) {
        List<ModelPoint> body = new ArrayList<>(4200);
        List<ModelPoint> landmarks = new ArrayList<>();
        if (wristStyle == WristStyle.LEGACY) {
            addLegacyPalm(body);
            addLegacyWrist(body);
        } else {
            addAnatomicalPalm(body);
            addAnatomicalWrist(body);
            addKnuckleTransitions(body);
        }
        addLandmarks(landmarks);
        return new HandBodyModel(body, landmarks);
    }

    /** Original palm surface retained together with the legacy rectangular wrist. */
    private static void addLegacyPalm(List<ModelPoint> out) {
        final int ySamples = 18;
        final int faceXSamples = 15;
        final int sideZSamples = 7;
        for (int yi = 0; yi <= ySamples; yi++) {
            double t = (double) yi / ySamples;
            double y = lerp(-0.52, 0.64, t);
            double halfWidth = legacyPalmHalfWidth(t);
            double halfThickness = legacyPalmHalfThickness(t);
            for (int xi = 0; xi <= faceXSamples; xi++) {
                double x = lerp(-halfWidth, halfWidth, (double) xi / faceXSamples);
                out.add(new ModelPoint(x, y, halfThickness));
                out.add(new ModelPoint(x, y, -halfThickness));
                if (((xi + yi) & 1) == 0) {
                    out.add(new ModelPoint(x, y, halfThickness * 0.72));
                    out.add(new ModelPoint(x, y, -halfThickness * 0.72));
                }
            }
            for (int zi = 0; zi <= sideZSamples; zi++) {
                double z = lerp(-halfThickness, halfThickness, (double) zi / sideZSamples);
                out.add(new ModelPoint(-halfWidth, y, z));
                out.add(new ModelPoint(halfWidth, y, z));
            }
        }
        for (int yi = 0; yi <= 14; yi++) {
            double t = (double) yi / 14.0;
            addRoundedRectRing(out, lerp(-0.52, 0.64, t), legacyPalmHalfWidth(t), legacyPalmHalfThickness(t), 14);
        }
    }

    /**
     * anatomical palm.  Cross sections use a rounded superellipse and
     * taper continuously into both the wrist and knuckle bridge.  This removes
     * the square side corners that made the finger tubes look disconnected from
     * a box-shaped palm.
     */
    private static void addAnatomicalPalm(List<ModelPoint> out) {
        final int yRings = 26;
        final int around = 28;
        final int faceSamples = 17;
        final double exponent = 2.45;

        for (int ring = 0; ring <= yRings; ring++) {
            double t = (double) ring / yRings;
            double y = lerp(-0.57, 0.66, t);
            double halfWidth = anatomicalPalmHalfWidth(t);
            double halfDepth = anatomicalPalmHalfDepth(t);

            // Rounded outer and inset shells.
            for (int i = 0; i < around; i++) {
                double angle = Math.PI * 2.0 * i / around;
                double c = Math.cos(angle);
                double s = Math.sin(angle);
                double x = Math.copySign(halfWidth * Math.pow(Math.abs(c), 2.0 / exponent), c);
                double z = Math.copySign(halfDepth * Math.pow(Math.abs(s), 2.0 / exponent), s);
                out.add(new ModelPoint(x, y, z));
                if (((ring + i) & 1) == 0) {
                    out.add(new ModelPoint(x * 0.79, y, z * 0.76));
                }
            }

            // Filled front/back skins whose Z naturally rounds away near sides.
            for (int xi = 0; xi <= faceSamples; xi++) {
                double u = lerp(-1.0, 1.0, (double) xi / faceSamples);
                double x = u * halfWidth;
                double remaining = Math.max(0.0, 1.0 - Math.pow(Math.abs(u), exponent));
                double z = halfDepth * Math.pow(remaining, 1.0 / exponent);
                out.add(new ModelPoint(x, y, z));
                out.add(new ModelPoint(x, y, -z));
                if (((xi + ring) & 1) == 0) {
                    out.add(new ModelPoint(x * 0.985, y, z * 0.73));
                    out.add(new ModelPoint(x * 0.985, y, -z * 0.73));
                }
            }
        }

        // A soft thenar bulge connects palm into the thumb root rather than
        // leaving a right-angle side wall.
        addEllipsoidPatch(out, 0.38, 0.05, 0.0, 0.19, 0.30, 0.145, 11, 16);
    }

    /** Original rectangular tapered wrist, preserved as the legacy model setting. */
    private static void addLegacyWrist(List<ModelPoint> out) {
        final int ySamples = 10;
        final int faceXSamples = 10;
        final int sideZSamples = 6;
        for (int yi = 0; yi <= ySamples; yi++) {
            double t = (double) yi / ySamples;
            double y = lerp(-1.02, -0.52, t);
            double halfWidth = lerp(0.27, 0.40, t);
            double halfThickness = lerp(0.085, 0.105, t);
            for (int xi = 0; xi <= faceXSamples; xi++) {
                double x = lerp(-halfWidth, halfWidth, (double) xi / faceXSamples);
                out.add(new ModelPoint(x, y, halfThickness));
                out.add(new ModelPoint(x, y, -halfThickness));
                if (((xi + yi) & 1) == 0) {
                    out.add(new ModelPoint(x, y, halfThickness * 0.72));
                    out.add(new ModelPoint(x, y, -halfThickness * 0.72));
                }
            }
            for (int zi = 0; zi <= sideZSamples; zi++) {
                double z = lerp(-halfThickness, halfThickness, (double) zi / sideZSamples);
                out.add(new ModelPoint(-halfWidth, y, z));
                out.add(new ModelPoint(halfWidth, y, z));
            }
        }
    }

    /**
     * anatomical wrist/forearm transition.  It now blends into the
     * palm with matching elliptical depth/width and uses more cross-sections
     * around the transition so there is no visible square lip at y≈-0.52.
     */
    private static void addAnatomicalWrist(List<ModelPoint> out) {
        final int rings = 18;
        final int ringSamples = 26;
        final double exponent = 2.20;
        for (int ring = 0; ring <= rings; ring++) {
            double t = (double) ring / rings;
            double smooth = smootherStep(t);
            double y = lerp(-1.14, -0.48, t);
            double halfWidth = lerp(0.235, 0.405, smooth);
            double halfDepth = lerp(0.095, 0.126, smooth);

            // Mild radial/ulnar asymmetry keeps it from reading as a perfect tube.
            double radialBulge = 0.018 * Math.sin(Math.PI * smooth);
            double ulnarBulge = 0.010 * Math.sin(Math.PI * (1.0 - smooth));

            for (int sample = 0; sample < ringSamples; sample++) {
                double a = Math.PI * 2.0 * sample / ringSamples;
                double c = Math.cos(a);
                double s = Math.sin(a);
                double x = Math.copySign(halfWidth * Math.pow(Math.abs(c), 2.0 / exponent), c);
                double z = Math.copySign(halfDepth * Math.pow(Math.abs(s), 2.0 / exponent), s);
                x += c >= 0.0 ? radialBulge * Math.abs(c) : -ulnarBulge * Math.abs(c);
                z *= 1.0 + 0.05 * Math.cos(a);
                out.add(new ModelPoint(x, y, z));
                if (((sample + ring) & 1) == 0) out.add(new ModelPoint(x * 0.79, y, z * 0.77));
            }
        }

        // Longitudinal anatomical rails/tendons.
        final int railSteps = 32;
        for (double side : new double[]{-1.0, 1.0}) {
            for (int i = 0; i <= railSteps; i++) {
                double t = (double) i / railSteps;
                double smooth = smootherStep(t);
                double y = lerp(-1.14, -0.48, t);
                double halfWidth = lerp(0.235, 0.405, smooth);
                double halfDepth = lerp(0.095, 0.126, smooth);
                out.add(new ModelPoint(side * halfWidth * 0.88, y, halfDepth * 0.55));
                out.add(new ModelPoint(side * halfWidth * 0.88, y, -halfDepth * 0.55));
            }
        }
        for (double xBias : new double[]{-0.12, 0.0, 0.12}) {
            for (int i = 0; i <= railSteps; i++) {
                double t = (double) i / railSteps;
                double smooth = smootherStep(t);
                double y = lerp(-1.12, -0.49, t);
                double halfWidth = lerp(0.235, 0.405, smooth);
                double halfDepth = lerp(0.095, 0.126, smooth);
                out.add(new ModelPoint(xBias * halfWidth / 0.40, y, halfDepth * 0.94));
            }
        }
    }

    /** Rounded knuckle/webbing patches visually merge palm into each finger root. */
    private static void addKnuckleTransitions(List<ModelPoint> out) {
        addEllipsoidPatch(out, -0.34, 0.615, 0.0, 0.115, 0.135, 0.125, 8, 13);
        addEllipsoidPatch(out, -0.12, 0.645, 0.0, 0.118, 0.145, 0.130, 8, 13);
        addEllipsoidPatch(out,  0.12, 0.665, 0.0, 0.120, 0.150, 0.132, 8, 13);
        addEllipsoidPatch(out,  0.34, 0.615, 0.0, 0.115, 0.135, 0.125, 8, 13);
    }

    private static void addEllipsoidPatch(
            List<ModelPoint> out,
            double cx, double cy, double cz,
            double rx, double ry, double rz,
            int latitudeSamples, int longitudeSamples
    ) {
        for (int lat = 0; lat <= latitudeSamples; lat++) {
            double phi = -Math.PI / 2.0 + Math.PI * lat / latitudeSamples;
            double cp = Math.cos(phi);
            double sp = Math.sin(phi);
            for (int lon = 0; lon < longitudeSamples; lon++) {
                double theta = Math.PI * 2.0 * lon / longitudeSamples;
                double x = cx + rx * cp * Math.cos(theta);
                double y = cy + ry * sp;
                double z = cz + rz * cp * Math.sin(theta);
                out.add(new ModelPoint(x, y, z));
            }
        }
    }

    private static double anatomicalPalmHalfWidth(double t) {
        // Narrow wrist insertion -> broad metacarpal body -> slight taper toward knuckles.
        if (t < 0.24) return lerp(0.405, 0.465, smootherStep(t / 0.24));
        if (t < 0.72) return lerp(0.465, 0.505, smootherStep((t - 0.24) / 0.48));
        return lerp(0.505, 0.445, smootherStep((t - 0.72) / 0.28));
    }

    private static double anatomicalPalmHalfDepth(double t) {
        double base = lerp(0.126, 0.136, smootherStep(Math.min(1.0, t / 0.25)));
        double swell = 0.026 * Math.sin(Math.PI * Math.max(0.0, Math.min(1.0, t)));
        double knuckleTaper = t > 0.76 ? (t - 0.76) / 0.24 * 0.018 : 0.0;
        return base + swell - knuckleTaper;
    }

    private static double legacyPalmHalfWidth(double t) {
        double halfWidth = lerp(0.40, 0.50, smoothStep(t));
        if (t > 0.82) halfWidth -= (t - 0.82) * 0.08;
        return halfWidth;
    }

    private static double legacyPalmHalfThickness(double t) {
        return 0.105 + Math.sin(t * Math.PI) * 0.045;
    }

    private static void addRoundedRectRing(List<ModelPoint> out, double y, double halfWidth, double halfThickness, int edgeSamples) {
        double corner = Math.min(0.10, halfWidth * 0.22);
        double innerHalfWidth = halfWidth - corner;
        for (int i = 0; i <= edgeSamples; i++) {
            double t = (double) i / edgeSamples;
            double x = lerp(-innerHalfWidth, innerHalfWidth, t);
            out.add(new ModelPoint(x, y, halfThickness));
            out.add(new ModelPoint(x, y, -halfThickness));
        }
        int arcSamples = Math.max(6, edgeSamples / 2);
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i <= arcSamples; i++) {
                double angle = -Math.PI / 2.0 + Math.PI * i / arcSamples;
                out.add(new ModelPoint(side * (innerHalfWidth + corner * Math.cos(angle)), y, halfThickness * Math.sin(angle)));
            }
        }
    }

    private static void addLandmarks(List<ModelPoint> out) {
        out.add(new ModelPoint(0.0, 0.0, 0.0));
        out.add(new ModelPoint(0.0, -0.52, 0.0));
        out.add(new ModelPoint(0.0, -1.02, 0.0));
        out.add(new ModelPoint(-0.34, 0.62, 0.0));
        out.add(new ModelPoint(-0.12, 0.65, 0.0));
        out.add(new ModelPoint(0.12, 0.67, 0.0));
        out.add(new ModelPoint(0.34, 0.62, 0.0));
        out.add(new ModelPoint(0.48, 0.10, 0.0));
    }

    private static double smoothStep(double t) { return t * t * (3.0 - 2.0 * t); }
    private static double smootherStep(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    public List<ModelPoint> bodyPoints() { return bodyPoints; }
    public List<ModelPoint> landmarkPoints() { return landmarkPoints; }
}
