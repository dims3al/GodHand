package xyz.dimseal.godHand.math;

import org.bukkit.util.Vector;

/**
 * Small rigid-body rotation utility used by every future GodHand model.
 *
 * Rotation order:
 *  1. yaw   around local/world Y
 *  2. pitch around X
 *  3. roll  around Z
 */
public final class Rotation3D {

    private Rotation3D() {
    }

    public static Vector rotate(
            double x,
            double y,
            double z,
            double yawDegrees,
            double pitchDegrees,
            double rollDegrees
    ) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double roll = Math.toRadians(rollDegrees);

        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double cosRoll = Math.cos(roll);
        double sinRoll = Math.sin(roll);

        // Yaw around Y.
        double x1 = x * cosYaw - z * sinYaw;
        double y1 = y;
        double z1 = x * sinYaw + z * cosYaw;

        // Pitch around X.
        double x2 = x1;
        double y2 = y1 * cosPitch - z1 * sinPitch;
        double z2 = y1 * sinPitch + z1 * cosPitch;

        // Roll around Z.
        double x3 = x2 * cosRoll - y2 * sinRoll;
        double y3 = x2 * sinRoll + y2 * cosRoll;

        return new Vector(x3, y3, z2);
    }
    /**
     * Inverse of {@link #rotate(double, double, double, double, double, double)}.
     * Applies inverse roll, then inverse pitch, then inverse yaw.
     */
    public static Vector inverseRotate(
            double x,
            double y,
            double z,
            double yawDegrees,
            double pitchDegrees,
            double rollDegrees
    ) {
        double yaw = Math.toRadians(-yawDegrees);
        double pitch = Math.toRadians(-pitchDegrees);
        double roll = Math.toRadians(-rollDegrees);

        // Inverse roll around Z.
        double cosRoll = Math.cos(roll);
        double sinRoll = Math.sin(roll);
        double x1 = x * cosRoll - y * sinRoll;
        double y1 = x * sinRoll + y * cosRoll;
        double z1 = z;

        // Inverse pitch around X.
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double x2 = x1;
        double y2 = y1 * cosPitch - z1 * sinPitch;
        double z2 = y1 * sinPitch + z1 * cosPitch;

        // Inverse yaw around Y.
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double x3 = x2 * cosYaw - z2 * sinYaw;
        double z3 = x2 * sinYaw + z2 * cosYaw;

        return new Vector(x3, y2, z3);
    }

}
