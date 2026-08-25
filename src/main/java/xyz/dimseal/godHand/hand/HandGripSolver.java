package xyz.dimseal.godHand.hand;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import xyz.dimseal.godHand.hand.skeleton.DigitPose;
import xyz.dimseal.godHand.hand.skeleton.HandDigit;
import xyz.dimseal.godHand.hand.skeleton.SkeletalHandModel;
import xyz.dimseal.godHand.model.ModelPoint;

/**
 * skeletal interaction anchor solver.
 *
 * The old carry point was a fixed offset from the hand origin. That
 * placed the player's feet at the grip position, which could put their head
 * through the palm. derives the actual enclosure from the currently
 * evaluated finger geometry and treats that point as an upper-torso anchor.
 */
public final class HandGripSolver {

    private static final double TORSO_HEIGHT_FRACTION_OF_EYE = 0.62;
    private static final double MIN_TORSO_HEIGHT = 0.75;
    private static final double MAX_TORSO_HEIGHT = 1.20;

    private final SkeletalHandModel skeletalModel = new SkeletalHandModel();

    /**
     * Returns a normalized hand-local point inside the curled finger cage.
     *
     * The anchor is solved from the actual curled finger enclosure and palm
     * face, not from a fixed offset. If the CLAW pose changes later, the grip
     * automatically follows the new articulated cup.
     */
    public ModelPoint localCageCenter(ParticleHand hand) {
        double proximalX = 0.0, proximalY = 0.0, proximalZ = 0.0;
        double middleX = 0.0, middleY = 0.0, middleZ = 0.0;
        double distalX = 0.0, distalY = 0.0, distalZ = 0.0;
        double weightSum = 0.0;

        for (HandDigit digit : HandDigit.values()) {
            DigitPose pose = skeletalModel.evaluateDigitPose(hand, digit);

            // The thumb closes from the side, so it should influence the cup
            // center without dragging the passenger toward the thumb root.
            double weight = digit == HandDigit.THUMB ? 0.72 : 1.0;
            proximalX += pose.proximalEnd().x() * weight;
            proximalY += pose.proximalEnd().y() * weight;
            proximalZ += pose.proximalEnd().z() * weight;
            middleX += pose.middleEnd().x() * weight;
            middleY += pose.middleEnd().y() * weight;
            middleZ += pose.middleEnd().z() * weight;
            distalX += pose.distalPad().x() * weight;
            distalY += pose.distalPad().y() * weight;
            distalZ += pose.distalPad().z() * weight;
            weightSum += weight;
        }

        proximalX /= weightSum; proximalY /= weightSum; proximalZ /= weightSum;
        middleX /= weightSum; middleY /= weightSum; middleZ /= weightSum;
        distalX /= weightSum; distalY /= weightSum; distalZ /= weightSum;

        // true cup-center solve. used the distal-pad average
        // and then pulled it DOWN toward the wrist, which could leave the rider
        // visibly below the curled fingers. The new anchor lives inside the
        // actual enclosure: palm face -> proximal bend -> middle bend -> pads.
        // Because every landmark is transformed with the Hand basis afterward,
        // this remains stable through yaw, pitch and roll changes.
        final double palmX = 0.0;
        final double palmY = 0.38;
        final double palmZ = 0.115;

        return new ModelPoint(
                proximalX * 0.23 + middleX * 0.34 + distalX * 0.20 + palmX * 0.23,
                proximalY * 0.23 + middleY * 0.34 + distalY * 0.20 + palmY * 0.23,
                proximalZ * 0.23 + middleZ * 0.34 + distalZ * 0.20 + palmZ * 0.23
        );
    }

    /** World-space upper-torso center represented by the local cage center. */
    public Location worldCageCenter(ParticleHand hand) {
        ModelPoint local = localCageCenter(hand);
        Vector offset = hand.transformLocalPoint(local.x(), local.y(), local.z());
        return hand.getLocation().add(offset);
    }

    /**
     * World-space player feet location that places the upper torso at the
     * skeletal cage center. Player yaw/pitch are preserved by the caller.
     */
    public Location playerFeetLocation(ParticleHand hand, Player player) {
        Location cage = worldCageCenter(hand);
        double torsoHeight = gripBodyHeight(hand, player);

        Location feet = player.getLocation().clone();
        feet.setX(cage.getX());
        feet.setY(cage.getY() - torsoHeight);
        feet.setZ(cage.getZ());
        return feet;
    }

    /** Current world-space upper-torso point that the claw should physically reach. */
    public Location playerTorsoLocation(Player player) {
        Location feet = player.getLocation();
        return feet.clone().add(0.0, torsoHeight(player), 0.0);
    }

    /**
     * Calculates the hand ROOT location required to place the current articulated
     * cage center directly on the player's upper torso. The local cage position
     * changes while CLAW animates, so recomputes this every tick.
     */
    public Location handOriginForPlayerTorso(ParticleHand hand, Player player) {
        Location torso = player.getLocation().clone().add(0.0, gripBodyHeight(hand, player), 0.0);
        ModelPoint local = localCageCenter(hand);
        Vector offset = hand.transformLocalPoint(local.x(), local.y(), local.z());
        return torso.clone().subtract(offset);
    }

    public double cageToTorsoDistance(ParticleHand hand, Player player) {
        Location gripBody = player.getLocation().clone().add(0.0, gripBodyHeight(hand, player), 0.0);
        return worldCageCenter(hand).distance(gripBody);
    }

    /**
     * Calculates the hand root required for the held player's FEET to arrive at
     * an arbitrary destination while preserving the current articulated cage.
     * transport recomputes this every tick, so live player destinations
     * and root-orientation changes remain stable.
     */
    public Location handOriginForPlayerFeetDestination(ParticleHand hand, Player held, Location desiredFeet) {
        if (desiredFeet == null || desiredFeet.getWorld() == null || !desiredFeet.getWorld().equals(hand.getWorld())) {
            throw new IllegalArgumentException("Destination must be in the hand's current world.");
        }
        Location desiredTorso = desiredFeet.clone().add(0.0, gripBodyHeight(hand, held), 0.0);
        ModelPoint local = localCageCenter(hand);
        Vector offset = hand.transformLocalPoint(local.x(), local.y(), local.z());
        return desiredTorso.subtract(offset);
    }


    /**
     * The player model stays world-upright while the Hand can freely pitch and
     * roll. When the palm plane is horizontal, centering the old upper-chest
     * point in the claw makes the seated model look too low/high depending on
     * orientation. Blend toward a lower body anchor as local palm +Z becomes
     * vertical so the visible seated torso remains inside the cup.
     */
    private static double gripBodyHeight(ParticleHand hand, Player player) {
        double upperTorso = torsoHeight(player);
        Vector palmNormal = hand.transformLocalPoint(0.0, 0.0, 1.0);
        double vertical = 0.0;
        if (palmNormal.lengthSquared() > 1.0e-10) {
            vertical = Math.abs(palmNormal.normalize().dot(new Vector(0.0, 1.0, 0.0)));
        }
        double horizontalPalmHeight = clamp(upperTorso * 0.80, 0.70, 0.90);
        return upperTorso + (horizontalPalmHeight - upperTorso) * vertical;
    }

    private static double torsoHeight(Player player) {
        Location current = player.getLocation();
        double eyeHeight = Math.abs(player.getEyeLocation().getY() - current.getY());
        if (!Double.isFinite(eyeHeight) || eyeHeight < 0.1) {
            eyeHeight = 1.62;
        }
        return clamp(
                eyeHeight * TORSO_HEIGHT_FRACTION_OF_EYE,
                MIN_TORSO_HEIGHT,
                MAX_TORSO_HEIGHT
        );
    }


    /** World-space fingertip for beam/origin effects. */
    public Location worldFingerTip(ParticleHand hand, HandDigit digit) {
        DigitPose pose = skeletalModel.evaluateDigitPose(hand, digit);
        ModelPoint tip = pose.tip();
        Vector offset = hand.transformLocalPoint(tip.x(), tip.y(), tip.z());
        return hand.getLocation().add(offset);
    }

    /** Local +Y is the wrist -> fingertips / throw direction. */
    public Vector worldFingerDirection(ParticleHand hand) {
        Vector direction = hand.transformLocalPoint(0.0, 1.0, 0.0);
        if (direction.lengthSquared() < 1.0e-12) {
            return new Vector(0.0, 0.0, 1.0);
        }
        return direction.normalize();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
