package xyz.dimseal.godHand.hand.motion;

/**
 * Root-orientation tracking modes.
 *
 * PALM_FRONT preserves the established behavior: local +Z (palm front) aims at the target.
 * PALM_DOWN preserves a horizontal attack stance: local +Z stays world-down while
 * local +Y (wrist -> fingertips) turns toward the target in the X/Z plane.
 */
public enum LookMode {
    PALM_FRONT,
    PALM_DOWN,
    /** Local +Y (wrist -> fingertips / pointing axis) aims directly at the target, while +Z stays as downward-facing as geometry permits. */
    FINGER_POINT
}
