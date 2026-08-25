package xyz.dimseal.godHand.hand.skeleton;

import org.bukkit.util.Vector;
import xyz.dimseal.godHand.hand.ParticleHand;
import xyz.dimseal.godHand.math.Matrix3;
import xyz.dimseal.godHand.model.ModelPoint;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Forward-kinematics model with dense surface shells.
 *
 * Each digit is a three-bone chain. Every child basis is parentBasis * localRotation,
 * so rotating a proximal joint automatically carries all descendants with it.
 */
public final class SkeletalHandModel {

    private static final int BONE_RINGS = 7;
    private static final int RING_SAMPLES = 12;
    private static final int DEBUG_LINE_SAMPLES = 8;

    private static final Map<HandDigit, DigitDefinition> DEFINITIONS = createDefinitions();

    /**
     * Evaluate only the four skeletal landmarks for one digit. uses
     * this to derive interaction anchors from the real articulated geometry
     * instead of guessing offsets from the hand origin.
     */
    public DigitPose evaluateDigitPose(ParticleHand hand, HandDigit digit) {
        DigitDefinition def = DEFINITIONS.get(digit);
        FingerState state = hand.getFingerState(digit);

        Vector base = new Vector(def.rootX, def.rootY, def.rootZ);
        Vector root = base.clone();
        Matrix3 basis = def.baseBasis.multiply(Matrix3.rotationX(state.getJoint(1)));

        Vector[] endpoints = new Vector[3];
        for (int segment = 0; segment < 3; segment++) {
            if (segment > 0) {
                basis = basis.multiply(Matrix3.rotationX(state.getJoint(segment + 1)));
            }
            Vector endpointOffset = basis.transform(0.0, def.lengths[segment], 0.0);
            base = add(base, endpointOffset);
            endpoints[segment] = base.clone();
        }

        return new DigitPose(
                point(root),
                point(endpoints[0]),
                point(endpoints[1]),
                point(endpoints[2])
        );
    }

    public SkeletalFrame evaluate(ParticleHand hand) {
        List<ModelPoint> surface = new ArrayList<>(2800);
        EnumMap<HandDigit, List<ModelPoint>> digitSurfaces = new EnumMap<>(HandDigit.class);
        List<ModelPoint> bones = new ArrayList<>(240);
        List<ModelPoint> joints = new ArrayList<>(24);

        for (HandDigit digit : HandDigit.values()) {
            DigitDefinition definition = DEFINITIONS.get(digit);
            FingerState state = hand.getFingerState(digit);
            List<ModelPoint> digitSurface = new ArrayList<>(560);
            evaluateDigit(definition, state, digitSurface, bones, joints);
            digitSurfaces.put(digit, List.copyOf(digitSurface));
            surface.addAll(digitSurface);
        }

        return new SkeletalFrame(List.copyOf(surface), Map.copyOf(digitSurfaces), bones, joints);
    }

    private static void evaluateDigit(
            DigitDefinition def,
            FingerState state,
            List<ModelPoint> surface,
            List<ModelPoint> bones,
            List<ModelPoint> joints
    ) {
        Vector base = new Vector(def.rootX, def.rootY, def.rootZ);
        Matrix3 basis = def.baseBasis.multiply(Matrix3.rotationX(state.getJoint(1)));

        joints.add(point(base));

        for (int segment = 0; segment < 3; segment++) {
            if (segment > 0) {
                basis = basis.multiply(Matrix3.rotationX(state.getJoint(segment + 1)));
            }

            double length = def.lengths[segment];
            double radiusStart = def.radii[segment];
            double radiusEnd = def.radii[segment + 1];

            addBoneSurface(surface, base, basis, length, radiusStart, radiusEnd);
            addBoneLine(bones, base, basis, length);

            Vector endpointOffset = basis.transform(0.0, length, 0.0);
            base = add(base, endpointOffset);
            joints.add(point(base));
        }
    }

    private static void addBoneSurface(
            List<ModelPoint> out,
            Vector base,
            Matrix3 basis,
            double length,
            double radiusStart,
            double radiusEnd
    ) {
        for (int ring = 0; ring <= BONE_RINGS; ring++) {
            double t = (double) ring / BONE_RINGS;
            double y = length * t;
            double radius = lerp(radiusStart, radiusEnd, t);

            // Slight knuckle swell near each segment's base makes the chain read
            // as a finger instead of three disconnected tubes.
            double swell = 1.0 + 0.12 * Math.sin(Math.PI * t);
            radius *= swell;

            for (int sample = 0; sample < RING_SAMPLES; sample++) {
                double angle = (Math.PI * 2.0 * sample) / RING_SAMPLES;
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double x = cos * radius;
                double z = sin * radius;
                Vector transformed = basis.transform(x, y, z);
                out.add(point(add(base, transformed)));

                // inset shell: every other angular sample also receives
                // a slightly smaller-radius point. This adds visual thickness
                // without turning the finger into an expensive filled volume.
                if (((ring + sample) & 1) == 0) {
                    double innerRadius = radius * 0.76;
                    Vector inner = basis.transform(cos * innerRadius, y, sin * innerRadius);
                    out.add(point(add(base, inner)));
                }
            }
        }

        // Add a thin dorsal/ventral rail along each bone to improve readability
        // while the dust rings are fading between 10 Hz renders.
        int railSamples = BONE_RINGS * 2;
        for (int i = 0; i <= railSamples; i++) {
            double t = (double) i / railSamples;
            double radius = lerp(radiusStart, radiusEnd, t);
            double y = length * t;
            Vector front = basis.transform(0.0, y, radius);
            Vector back = basis.transform(0.0, y, -radius);
            out.add(point(add(base, front)));
            out.add(point(add(base, back)));
        }
    }

    private static void addBoneLine(List<ModelPoint> out, Vector base, Matrix3 basis, double length) {
        for (int i = 0; i <= DEBUG_LINE_SAMPLES; i++) {
            double t = (double) i / DEBUG_LINE_SAMPLES;
            Vector transformed = basis.transform(0.0, length * t, 0.0);
            out.add(point(add(base, transformed)));
        }
    }

    private static Map<HandDigit, DigitDefinition> createDefinitions() {
        EnumMap<HandDigit, DigitDefinition> map = new EnumMap<>(HandDigit.class);

        // Four fingers start on the knuckle landmarks. Small Z rotations
        // splay them naturally in the palm plane.
        map.put(HandDigit.PINKY, new DigitDefinition(
                -0.34, 0.62, 0.0,
                Matrix3.rotationZ(7.0),
                new double[]{0.31, 0.22, 0.17},
                new double[]{0.062, 0.058, 0.050, 0.038}
        ));
        map.put(HandDigit.RING, new DigitDefinition(
                -0.12, 0.65, 0.0,
                Matrix3.rotationZ(2.5),
                new double[]{0.37, 0.26, 0.19},
                new double[]{0.069, 0.064, 0.054, 0.040}
        ));
        map.put(HandDigit.MIDDLE, new DigitDefinition(
                0.12, 0.67, 0.0,
                Matrix3.rotationZ(-1.0),
                new double[]{0.41, 0.29, 0.21},
                new double[]{0.072, 0.066, 0.055, 0.041}
        ));
        map.put(HandDigit.INDEX, new DigitDefinition(
                0.34, 0.62, 0.0,
                Matrix3.rotationZ(-5.0),
                new double[]{0.38, 0.27, 0.20},
                new double[]{0.070, 0.064, 0.053, 0.040}
        ));

        // Thumb begins on the +X landmark, rotated sideways and slightly
        // toward the palm front. Its subsequent joints still inherit normally.
        Matrix3 thumbBasis = Matrix3.rotationZ(-58.0)
                .multiply(Matrix3.rotationX(18.0))
                .multiply(Matrix3.rotationY(-8.0));
        map.put(HandDigit.THUMB, new DigitDefinition(
                0.48, 0.10, 0.0,
                thumbBasis,
                new double[]{0.30, 0.23, 0.17},
                new double[]{0.080, 0.071, 0.058, 0.043}
        ));

        return map;
    }

    private static Vector add(Vector a, Vector b) {
        return new Vector(a.getX() + b.getX(), a.getY() + b.getY(), a.getZ() + b.getZ());
    }

    private static ModelPoint point(Vector v) {
        return new ModelPoint(v.getX(), v.getY(), v.getZ());
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private record DigitDefinition(
            double rootX,
            double rootY,
            double rootZ,
            Matrix3 baseBasis,
            double[] lengths,
            double[] radii
    ) {
    }
}
