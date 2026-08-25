package xyz.dimseal.godHand.math;

import org.bukkit.util.Vector;

/**
 * Tiny immutable 3x3 rotation matrix used for parent-child bone transforms.
 * Matrices operate on column vectors. A.multiply(B) means B is applied first,
 * then A.
 */
public final class Matrix3 {

    private final double m00, m01, m02;
    private final double m10, m11, m12;
    private final double m20, m21, m22;

    private Matrix3(
            double m00, double m01, double m02,
            double m10, double m11, double m12,
            double m20, double m21, double m22
    ) {
        this.m00 = m00; this.m01 = m01; this.m02 = m02;
        this.m10 = m10; this.m11 = m11; this.m12 = m12;
        this.m20 = m20; this.m21 = m21; this.m22 = m22;
    }

    public static Matrix3 identity() {
        return new Matrix3(1, 0, 0, 0, 1, 0, 0, 0, 1);
    }

    public static Matrix3 rotationX(double degrees) {
        double r = Math.toRadians(degrees);
        double c = Math.cos(r);
        double s = Math.sin(r);
        return new Matrix3(1, 0, 0, 0, c, -s, 0, s, c);
    }

    public static Matrix3 rotationY(double degrees) {
        double r = Math.toRadians(degrees);
        double c = Math.cos(r);
        double s = Math.sin(r);
        return new Matrix3(c, 0, -s, 0, 1, 0, s, 0, c);
    }

    public static Matrix3 rotationZ(double degrees) {
        double r = Math.toRadians(degrees);
        double c = Math.cos(r);
        double s = Math.sin(r);
        return new Matrix3(c, -s, 0, s, c, 0, 0, 0, 1);
    }

    public Matrix3 multiply(Matrix3 rhs) {
        return new Matrix3(
                m00 * rhs.m00 + m01 * rhs.m10 + m02 * rhs.m20,
                m00 * rhs.m01 + m01 * rhs.m11 + m02 * rhs.m21,
                m00 * rhs.m02 + m01 * rhs.m12 + m02 * rhs.m22,

                m10 * rhs.m00 + m11 * rhs.m10 + m12 * rhs.m20,
                m10 * rhs.m01 + m11 * rhs.m11 + m12 * rhs.m21,
                m10 * rhs.m02 + m11 * rhs.m12 + m12 * rhs.m22,

                m20 * rhs.m00 + m21 * rhs.m10 + m22 * rhs.m20,
                m20 * rhs.m01 + m21 * rhs.m11 + m22 * rhs.m21,
                m20 * rhs.m02 + m21 * rhs.m12 + m22 * rhs.m22
        );
    }

    public Vector transform(double x, double y, double z) {
        return new Vector(
                m00 * x + m01 * y + m02 * z,
                m10 * x + m11 * y + m12 * z,
                m20 * x + m21 * y + m22 * z
        );
    }
}
