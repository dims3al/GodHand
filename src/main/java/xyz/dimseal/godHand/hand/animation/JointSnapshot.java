package xyz.dimseal.godHand.hand.animation;

import xyz.dimseal.godHand.hand.skeleton.FingerState;
import xyz.dimseal.godHand.hand.skeleton.HandDigit;

import java.util.EnumMap;
import java.util.Map;

/** Immutable-by-convention snapshot of all 15 hand joint angles. */
public final class JointSnapshot {

    private final EnumMap<HandDigit, double[]> angles = new EnumMap<>(HandDigit.class);

    public JointSnapshot() {
        for (HandDigit digit : HandDigit.values()) {
            angles.put(digit, new double[3]);
        }
    }

    public JointSnapshot(JointSnapshot other) {
        for (HandDigit digit : HandDigit.values()) {
            angles.put(digit, other.angles.get(digit).clone());
        }
    }

    public static JointSnapshot capture(Map<HandDigit, FingerState> fingers) {
        JointSnapshot snapshot = new JointSnapshot();
        for (HandDigit digit : HandDigit.values()) {
            FingerState state = fingers.get(digit);
            snapshot.set(digit, 1, state.getJoint(1));
            snapshot.set(digit, 2, state.getJoint(2));
            snapshot.set(digit, 3, state.getJoint(3));
        }
        return snapshot;
    }

    public double get(HandDigit digit, int joint) {
        validateJoint(joint);
        return angles.get(digit)[joint - 1];
    }

    public JointSnapshot set(HandDigit digit, int joint, double degrees) {
        validateJoint(joint);
        if (!Double.isFinite(degrees) || degrees < -35.0 || degrees > 125.0) {
            throw new IllegalArgumentException("Joint angle must be between -35 and 125 degrees.");
        }
        angles.get(digit)[joint - 1] = degrees;
        return this;
    }

    public JointSnapshot setAngles(HandDigit digit, double first, double second, double third) {
        return set(digit, 1, first).set(digit, 2, second).set(digit, 3, third);
    }

    public JointSnapshot copy() {
        return new JointSnapshot(this);
    }

    private static void validateJoint(int joint) {
        if (joint < 1 || joint > 3) {
            throw new IllegalArgumentException("Joint must be 1, 2, or 3.");
        }
    }
}
