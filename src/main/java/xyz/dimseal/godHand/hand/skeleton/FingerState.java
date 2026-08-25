package xyz.dimseal.godHand.hand.skeleton;

/** Runtime bend state for one three-segment digit. */
public final class FingerState {

    private final double[] joints = new double[3];

    public double getJoint(int joint) {
        validateJoint(joint);
        return joints[joint - 1];
    }

    public void setJoint(int joint, double degrees) {
        validateJoint(joint);
        if (!Double.isFinite(degrees) || degrees < -35.0 || degrees > 125.0) {
            throw new IllegalArgumentException("Joint angle must be between -35 and 125 degrees.");
        }
        joints[joint - 1] = degrees;
    }

    public void setAngles(double first, double second, double third) {
        setJoint(1, first);
        setJoint(2, second);
        setJoint(3, third);
    }

    public void clear() {
        setAngles(0.0, 0.0, 0.0);
    }

    private static void validateJoint(int joint) {
        if (joint < 1 || joint > 3) {
            throw new IllegalArgumentException("Joint must be 1, 2, or 3.");
        }
    }
}
