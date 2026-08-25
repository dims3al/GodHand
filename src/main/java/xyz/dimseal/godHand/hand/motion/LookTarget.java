package xyz.dimseal.godHand.hand.motion;

/**
 * Immutable root orientation returned by the look controller.
 * controlsRoll=false preserves roll independence; PALM_DOWN sets it true
 * because roll is part of the constrained face-down basis.
 */
public record LookTarget(double yaw, double pitch, double roll, boolean controlsRoll) {
}
