package xyz.dimseal.godHand.hand.animation;

import xyz.dimseal.godHand.hand.skeleton.FingerState;
import xyz.dimseal.godHand.hand.skeleton.HandDigit;
import xyz.dimseal.godHand.hand.skeleton.HandPose;

import java.util.Map;

/**
 * Interpolates all 15 joints from one captured state to one target state.
 * A new transition can replace an old one at any tick without popping because
 * callers capture the currently evaluated hand state before re-targeting.
 */
public final class HandAnimationController {

    private JointSnapshot start;
    private JointSnapshot target;
    private int durationTicks;
    private int elapsedTicks;
    private EasingCurve easing = EasingCurve.SMOOTH;
    private HandPose targetPose;
    private String description = "idle";
    private boolean active;

    public void start(
            JointSnapshot start,
            JointSnapshot target,
            int durationTicks,
            EasingCurve easing,
            HandPose targetPose,
            String description
    ) {
        if (durationTicks < 1) {
            throw new IllegalArgumentException("Animation duration must be at least 1 tick.");
        }
        this.start = start.copy();
        this.target = target.copy();
        this.durationTicks = durationTicks;
        this.elapsedTicks = 0;
        this.easing = easing;
        this.targetPose = targetPose;
        this.description = description;
        this.active = true;
    }

    /** Returns true only on the tick that the transition completes. */
    public boolean tick(Map<HandDigit, FingerState> fingers) {
        if (!active) {
            return false;
        }

        elapsedTicks++;
        double rawT = Math.min(1.0, elapsedTicks / (double) durationTicks);
        double t = easing.apply(rawT);

        for (HandDigit digit : HandDigit.values()) {
            FingerState state = fingers.get(digit);
            for (int joint = 1; joint <= 3; joint++) {
                double a = start.get(digit, joint);
                double b = target.get(digit, joint);
                state.setJoint(joint, lerp(a, b, t));
            }
        }

        if (elapsedTicks >= durationTicks) {
            active = false;
            return true;
        }
        return false;
    }

    public void cancel() {
        active = false;
        description = "idle";
        targetPose = null;
        elapsedTicks = 0;
        durationTicks = 0;
    }

    public boolean isActive() {
        return active;
    }

    public double getProgress() {
        if (!active || durationTicks <= 0) {
            return active ? 0.0 : 1.0;
        }
        return Math.min(1.0, elapsedTicks / (double) durationTicks);
    }

    public int getElapsedTicks() {
        return elapsedTicks;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public EasingCurve getEasing() {
        return easing;
    }

    public HandPose getTargetPose() {
        return targetPose;
    }

    public String getDescription() {
        return description;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
