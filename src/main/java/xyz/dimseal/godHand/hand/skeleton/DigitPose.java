package xyz.dimseal.godHand.hand.skeleton;

import xyz.dimseal.godHand.model.ModelPoint;

/**
 * Evaluated local-space joint chain for one articulated digit.
 *
 * root -> proximalEnd -> middleEnd -> tip
 *
 * The points use the same normalized hand-local coordinate system as the palm
 * and particle geometry. They are useful for interaction anchors
 * without coupling gameplay logic to rendered particle samples.
 */
public record DigitPose(
        ModelPoint root,
        ModelPoint proximalEnd,
        ModelPoint middleEnd,
        ModelPoint tip
) {
    /** Midpoint of the distal phalanx, a useful approximation of the finger pad. */
    public ModelPoint distalPad() {
        return midpoint(middleEnd, tip);
    }

    private static ModelPoint midpoint(ModelPoint a, ModelPoint b) {
        return new ModelPoint(
                (a.x() + b.x()) * 0.5,
                (a.y() + b.y()) * 0.5,
                (a.z() + b.z()) * 0.5
        );
    }
}
