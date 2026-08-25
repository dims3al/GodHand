package xyz.dimseal.godHand.hand.animation;

import xyz.dimseal.godHand.hand.skeleton.HandDigit;
import xyz.dimseal.godHand.hand.skeleton.HandPose;

/** Central pose/curl definitions shared by snapping and animation. */
public final class HandPoseLibrary {

    private HandPoseLibrary() {}

    public static JointSnapshot pose(HandPose pose) {
        JointSnapshot out = new JointSnapshot();
        switch (pose) {
            case OPEN -> {
                out.setAngles(HandDigit.THUMB, 0, 0, 0);
                out.setAngles(HandDigit.INDEX, 0, 0, 0);
                out.setAngles(HandDigit.MIDDLE, 0, 0, 0);
                out.setAngles(HandDigit.RING, 0, 0, 0);
                out.setAngles(HandDigit.PINKY, 0, 0, 0);
            }
            case RELAXED -> {
                out.setAngles(HandDigit.THUMB, 8, 12, 8);
                out.setAngles(HandDigit.INDEX, 8, 12, 8);
                out.setAngles(HandDigit.MIDDLE, 10, 15, 10);
                out.setAngles(HandDigit.RING, 13, 19, 12);
                out.setAngles(HandDigit.PINKY, 17, 23, 15);
            }
            case FIST -> {
                out.setAngles(HandDigit.THUMB, 42, 62, 48);
                out.setAngles(HandDigit.INDEX, 72, 94, 74);
                out.setAngles(HandDigit.MIDDLE, 74, 96, 76);
                out.setAngles(HandDigit.RING, 76, 98, 78);
                out.setAngles(HandDigit.PINKY, 78, 100, 80);
            }
            case POINT -> {
                out.setAngles(HandDigit.THUMB, 30, 46, 34);
                out.setAngles(HandDigit.INDEX, 0, 0, 0);
                out.setAngles(HandDigit.MIDDLE, 76, 98, 78);
                out.setAngles(HandDigit.RING, 78, 100, 80);
                out.setAngles(HandDigit.PINKY, 80, 102, 82);
            }
            case BIRD -> {
                // Middle-finger gesture: every digit fully curled except MIDDLE.
                out.setAngles(HandDigit.THUMB, 42, 62, 48);
                out.setAngles(HandDigit.INDEX, 72, 94, 74);
                out.setAngles(HandDigit.MIDDLE, 0, 0, 0);
                out.setAngles(HandDigit.RING, 76, 98, 78);
                out.setAngles(HandDigit.PINKY, 78, 100, 80);
            }
            case THUMBS_UP, THUMBS_DOWN -> {
                // Thumb extended while the other four digits are tightly curled.
                // Up/down is controlled by the root orientation of the action.
                out.setAngles(HandDigit.THUMB, 0, 4, 2);
                out.setAngles(HandDigit.INDEX, 74, 96, 76);
                out.setAngles(HandDigit.MIDDLE, 76, 98, 78);
                out.setAngles(HandDigit.RING, 78, 100, 80);
                out.setAngles(HandDigit.PINKY, 80, 102, 82);
            }
            case CLAW -> {
                // closes farther around the cage center so held players
                // visually sit under the distal fingers instead of in front of them.
                out.setAngles(HandDigit.THUMB, 28, 50, 44);
                out.setAngles(HandDigit.INDEX, 40, 78, 66);
                out.setAngles(HandDigit.MIDDLE, 42, 82, 68);
                out.setAngles(HandDigit.RING, 44, 84, 70);
                out.setAngles(HandDigit.PINKY, 46, 86, 72);
            }
        }
        return out;
    }

    public static void applyCurl(JointSnapshot snapshot, HandDigit digit, double percent) {
        if (!Double.isFinite(percent) || percent < 0.0 || percent > 100.0) {
            throw new IllegalArgumentException("Finger curl must be between 0 and 100 percent.");
        }
        double t = percent / 100.0;
        if (digit == HandDigit.THUMB) {
            snapshot.setAngles(digit, 42.0 * t, 62.0 * t, 48.0 * t);
        } else {
            snapshot.setAngles(digit, 72.0 * t, 94.0 * t, 74.0 * t);
        }
    }
}
