package xyz.dimseal.godHand.hand.motion;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import xyz.dimseal.godHand.hand.animation.EasingCurve;

import java.util.Locale;

/**
 * world-space motion + look controller.
 *
 * Translation and orientation are deliberately independent. A hand can orbit,
 * chase, or follow one target while looking at another target. finger
 * animation remains entirely separate.
 *
 * Dynamic CHASE/FOLLOW/ORBIT modes use arrival steering:
 *   desired speed <= sqrt(2 * acceleration * remaining distance)
 * This naturally accelerates when far away and brakes before the target point.
 */
public final class HandMotionController {

    private static final double POSITION_EPSILON = 1.0e-4;
    private static final double VELOCITY_EPSILON = 1.0e-4;

    private TranslationMode translationMode = TranslationMode.IDLE;

    private double velocityX;
    private double velocityY;
    private double velocityZ;

    // Deterministic travel tween.
    private Location travelStart;
    private Location travelTarget;
    private int travelDurationTicks;
    private int travelElapsedTicks;
    private EasingCurve travelEasing = EasingCurve.SMOOTH;

    // Dynamic player tracking.
    private Player translationTarget;

    // action-owned dynamic point. The action controller refreshes this
    // every tick so a grab can physically chase a moving skeletal grip target.
    private Location actionSteerTarget;
    private double maxSpeed = 0.75;
    private double acceleration = 0.05;
    private double stopDistance = 3.0;
    private double followDistance = 8.0;
    private double followHeight = 2.0;

    // Orbit state.
    private double orbitRadius = 10.0;
    private double orbitHeight = 2.0;
    private double orbitDegreesPerTick = 2.0;
    private double orbitAngleDegrees;

    // Orientation tracking. A Player target is live; a Location target is fixed.
    private Player lookPlayer;
    private Location lookPoint;
    private double lookTurnSpeed = 6.0;
    private LookMode lookMode = LookMode.PALM_FRONT;

    private String lastTranslationStopReason = "idle";
    private String lastLookStopReason = "idle";

    /**
     * Advance translation one server tick and return the next hand origin.
     * The supplied location is never mutated.
     */
    public Location tickTranslation(Location current) {
        if (translationMode == TranslationMode.IDLE) {
            return current.clone();
        }

        return switch (translationMode) {
            case TRAVEL -> tickTravel(current);
            case CHASE -> tickChase(current);
            case FOLLOW -> tickFollow(current);
            case ORBIT -> tickOrbit(current);
            case ACTION_STEER -> tickActionSteer(current);
            case IDLE -> current.clone();
        };
    }

    public LookTarget tickLook(Location current, double currentYaw, double currentPitch, double currentRoll) {
        Location target = resolveLookLocation();
        if (target == null) {
            return null;
        }

        if (!sameWorld(current, target)) {
            clearLook("look target changed worlds");
            return null;
        }

        return switch (lookMode) {
            case PALM_FRONT -> tickPalmFrontLook(current, target, currentYaw, currentPitch, currentRoll);
            case PALM_DOWN -> tickPalmDownLook(current, target, currentYaw, currentPitch, currentRoll);
            case FINGER_POINT -> tickFingerPointLook(current, target, currentYaw, currentPitch, currentRoll);
        };
    }

    /** established behavior: aim the local +Z palm-front axis directly at the target. */
    private LookTarget tickPalmFrontLook(
            Location current, Location target,
            double currentYaw, double currentPitch, double currentRoll
    ) {
        double dx = target.getX() - current.getX();
        double dy = target.getY() - current.getY();
        double dz = target.getZ() - current.getZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (length < POSITION_EPSILON) {
            return new LookTarget(currentYaw, currentPitch, currentRoll, false);
        }

        // Rotation3D applies Rz(roll) * Rx(pitch) * Ry(yaw). To keep the local
        // +Z palm-front exactly on target even while roll is changing, first
        // undo the current roll from the desired direction, then solve yaw and
        // pitch for that pre-roll vector.
        double tx = dx / length;
        double ty = dy / length;
        double tz = dz / length;
        double r = Math.toRadians(currentRoll);
        double c = Math.cos(r);
        double sin = Math.sin(r);
        double preX = tx * c + ty * sin;
        double preY = -tx * sin + ty * c;
        double preZ = tz;

        double yawA = Math.toDegrees(Math.asin(clamp(-preX, -1.0, 1.0)));
        double cosYawA = Math.cos(Math.toRadians(yawA));
        double pitchA;
        double yawB;
        double pitchB;

        if (Math.abs(cosYawA) < 1.0e-9) {
            pitchA = currentPitch;
            yawB = yawA;
            pitchB = currentPitch;
        } else {
            pitchA = Math.toDegrees(Math.atan2(-preY, preZ));
            yawB = normalizeAngle(180.0 - yawA);
            pitchB = normalizeAngle(Math.toDegrees(Math.atan2(preY, -preZ)));
        }

        double costA = Math.abs(shortestAngleDelta(currentYaw, yawA))
                + Math.abs(shortestAngleDelta(currentPitch, pitchA));
        double costB = Math.abs(shortestAngleDelta(currentYaw, yawB))
                + Math.abs(shortestAngleDelta(currentPitch, pitchB));
        double targetYaw = costA <= costB ? yawA : yawB;
        double targetPitch = costA <= costB ? pitchA : pitchB;

        double nextYaw = approachAngle(currentYaw, targetYaw, lookTurnSpeed);
        double nextPitch = approachAngle(currentPitch, targetPitch, lookTurnSpeed);
        return new LookTarget(nextYaw, nextPitch, currentRoll, false);
    }

    /**
     * attack orientation. The palm front (+Z) is constrained to world
     * down (0,-1,0), while local +Y (wrist -> fingertips) tracks the target's
     * horizontal bearing.
     *
     * With the project's fixed Euler order Rz * Rx * Ry there are two useful
     * equivalent face-down branches:
     *   A: yaw=+90, roll=+90, pitch=atan2(hz,-hx)
     *   B: yaw=-90, roll=-90, pitch=atan2(hz, hx)
     * We choose whichever branch requires less angular travel from the current
     * root orientation, preventing gratuitous 180-degree flips.
     */
    private LookTarget tickPalmDownLook(
            Location current, Location target,
            double currentYaw, double currentPitch, double currentRoll
    ) {
        double hx = target.getX() - current.getX();
        double hz = target.getZ() - current.getZ();
        double horizontalLength = Math.sqrt(hx * hx + hz * hz);

        if (horizontalLength < POSITION_EPSILON) {
            // Directly above/below the target has no horizontal bearing. Recover
            // the hand's current fingertip direction and keep that heading while
            // still flattening the palm.
            double[] heading = currentFingerHeading(currentYaw, currentPitch, currentRoll);
            hx = heading[0];
            hz = heading[1];
            horizontalLength = Math.sqrt(hx * hx + hz * hz);
            if (horizontalLength < POSITION_EPSILON) {
                hx = 0.0;
                hz = 1.0;
                horizontalLength = 1.0;
            }
        }

        hx /= horizontalLength;
        hz /= horizontalLength;

        double yawA = 90.0;
        double rollA = 90.0;
        double pitchA = Math.toDegrees(Math.atan2(hz, -hx));

        double yawB = -90.0;
        double rollB = -90.0;
        double pitchB = Math.toDegrees(Math.atan2(hz, hx));

        double costA = angularCost(currentYaw, currentPitch, currentRoll, yawA, pitchA, rollA);
        double costB = angularCost(currentYaw, currentPitch, currentRoll, yawB, pitchB, rollB);

        double targetYaw = costA <= costB ? yawA : yawB;
        double targetPitch = costA <= costB ? pitchA : pitchB;
        double targetRoll = costA <= costB ? rollA : rollB;

        return new LookTarget(
                approachAngle(currentYaw, targetYaw, lookTurnSpeed),
                approachAngle(currentPitch, targetPitch, lookTurnSpeed),
                approachAngle(currentRoll, targetRoll, lookTurnSpeed),
                true
        );
    }

    /**
     * pointing orientation. The local +Y axis is the index/finger direction,
     * so this solver aims +Y directly at the target. Local +Z is chosen as the
     * projection of world-down onto the plane perpendicular to +Y, keeping the
     * palm as downward-facing as possible without sacrificing exact pointing.
     */
    private LookTarget tickFingerPointLook(
            Location current, Location target,
            double currentYaw, double currentPitch, double currentRoll
    ) {
        Vector yAxis = target.toVector().subtract(current.toVector());
        if (yAxis.lengthSquared() < POSITION_EPSILON * POSITION_EPSILON) {
            return new LookTarget(currentYaw, currentPitch, currentRoll, false);
        }
        yAxis.normalize();

        Vector down = new Vector(0.0, -1.0, 0.0);
        Vector zAxis = down.clone().subtract(yAxis.clone().multiply(down.dot(yAxis)));
        if (zAxis.lengthSquared() < 1.0e-8) {
            // Pointing almost vertically: preserve the current palm-normal plane
            // instead of allowing an arbitrary 180-degree roll flip.
            Vector currentPalm = xyz.dimseal.godHand.math.Rotation3D.rotate(
                    0.0, 0.0, 1.0, currentYaw, currentPitch, currentRoll);
            zAxis = currentPalm.subtract(yAxis.clone().multiply(currentPalm.dot(yAxis)));
            if (zAxis.lengthSquared() < 1.0e-8) zAxis = new Vector(1.0, 0.0, 0.0);
        }
        zAxis.normalize();
        Vector xAxis = yAxis.clone().crossProduct(zAxis).normalize();
        // Re-orthogonalize Z after the cross product to remove accumulated error.
        zAxis = xAxis.clone().crossProduct(yAxis).normalize();

        // Desired rotation matrix columns are local X/Y/Z in world space.
        double sinPitch = clamp(yAxis.getZ(), -1.0, 1.0);
        double targetPitch = Math.toDegrees(Math.asin(sinPitch));
        double cosPitch = Math.cos(Math.toRadians(targetPitch));
        double targetYaw;
        double targetRoll;

        if (Math.abs(cosPitch) > 1.0e-7) {
            targetYaw = Math.toDegrees(Math.atan2(xAxis.getZ(), zAxis.getZ()));
            targetRoll = Math.toDegrees(Math.atan2(-yAxis.getX(), yAxis.getY()));
        } else {
            // Gimbal branch for +Y pointing almost exactly world +/-Z. Keep the
            // current roll and solve the coupled yaw/roll term from the X axis.
            double theta = Math.toDegrees(Math.atan2(xAxis.getY(), xAxis.getX()));
            targetRoll = currentRoll;
            targetYaw = targetPitch > 0.0
                    ? targetRoll - theta
                    : theta - targetRoll;
        }

        return new LookTarget(
                approachAngle(currentYaw, normalizeAngle(targetYaw), lookTurnSpeed),
                approachAngle(currentPitch, normalizeAngle(targetPitch), lookTurnSpeed),
                approachAngle(currentRoll, normalizeAngle(targetRoll), lookTurnSpeed),
                true
        );
    }

    private static double[] currentFingerHeading(double yaw, double pitch, double roll) {
        // Inline Rotation3D.rotate(0,1,0,...) to keep this controller independent
        // of Bukkit Vector allocation in the hot path.
        double p = Math.toRadians(pitch);
        double r = Math.toRadians(roll);
        double cp = Math.cos(p);
        double sp = Math.sin(p);
        double cr = Math.cos(r);
        double sr = Math.sin(r);
        // Local +Y is unaffected by yaw before the X/Z rotations.
        double x = -sr * cp;
        double z = sp;
        return new double[]{x, z};
    }

    private static double angularCost(
            double currentYaw, double currentPitch, double currentRoll,
            double targetYaw, double targetPitch, double targetRoll
    ) {
        return Math.abs(shortestAngleDelta(currentYaw, targetYaw))
                + Math.abs(shortestAngleDelta(currentPitch, targetPitch))
                + Math.abs(shortestAngleDelta(currentRoll, targetRoll));
    }

    // ---------------------------------------------------------------------
    // Deterministic travel
    // ---------------------------------------------------------------------

    public void startTravel(Location current, Location target, int durationTicks, EasingCurve easing) {
        requireSameWorld(current, target, "Travel target must be in the hand's current world.");
        if (durationTicks < 1 || durationTicks > 20 * 300) {
            throw new IllegalArgumentException("Travel duration must be between 1 tick and 300 seconds.");
        }
        if (easing == null) {
            throw new IllegalArgumentException("Travel easing cannot be null.");
        }

        translationMode = TranslationMode.TRAVEL;
        travelStart = current.clone();
        travelTarget = target.clone();
        travelDurationTicks = durationTicks;
        travelElapsedTicks = 0;
        travelEasing = easing;
        zeroVelocity();
        translationTarget = null;
        lastTranslationStopReason = "travel active";
    }

    private Location tickTravel(Location current) {
        if (travelStart == null || travelTarget == null || !sameWorld(travelStart, travelTarget)) {
            stopTranslation("invalid travel state");
            return current.clone();
        }

        Location previous = current.clone();
        travelElapsedTicks++;
        double raw = Math.min(1.0, travelElapsedTicks / (double) travelDurationTicks);
        double t = travelEasing.apply(raw);

        Location next = lerp(travelStart, travelTarget, t);
        velocityX = next.getX() - previous.getX();
        velocityY = next.getY() - previous.getY();
        velocityZ = next.getZ() - previous.getZ();

        if (raw >= 1.0) {
            next = travelTarget.clone();
            stopTranslation("travel complete");
        }
        return next;
    }

    // ---------------------------------------------------------------------
    // Dynamic player steering
    // ---------------------------------------------------------------------

    public void startChase(Player player, double stopDistance, double maxSpeed, double acceleration) {
        validatePlayer(player);
        validateSteering(maxSpeed, acceleration);
        if (!Double.isFinite(stopDistance) || stopDistance < 0.0 || stopDistance > 128.0) {
            throw new IllegalArgumentException("Chase stop distance must be between 0 and 128 blocks.");
        }

        translationMode = TranslationMode.CHASE;
        translationTarget = player;
        this.stopDistance = stopDistance;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        clearTravelState();
        lastTranslationStopReason = "chase active";
    }

    public void startFollow(Player player, double distance, double height, double maxSpeed, double acceleration) {
        validatePlayer(player);
        validateSteering(maxSpeed, acceleration);
        if (!Double.isFinite(distance) || distance < 0.0 || distance > 128.0) {
            throw new IllegalArgumentException("Follow distance must be between 0 and 128 blocks.");
        }
        if (!Double.isFinite(height) || Math.abs(height) > 128.0) {
            throw new IllegalArgumentException("Follow height must be between -128 and 128 blocks.");
        }

        translationMode = TranslationMode.FOLLOW;
        translationTarget = player;
        followDistance = distance;
        followHeight = height;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        clearTravelState();
        lastTranslationStopReason = "follow active";
    }

    public void startOrbit(Player player, Location current, double radius, double degreesPerTick, double height,
                           double maxSpeed, double acceleration) {
        validatePlayer(player);
        validateSteering(maxSpeed, acceleration);
        if (!Double.isFinite(radius) || radius <= 0.0 || radius > 256.0) {
            throw new IllegalArgumentException("Orbit radius must be greater than 0 and at most 256 blocks.");
        }
        if (!Double.isFinite(degreesPerTick) || Math.abs(degreesPerTick) > 45.0) {
            throw new IllegalArgumentException("Orbit angular speed must be between -45 and 45 degrees/tick.");
        }
        if (!Double.isFinite(height) || Math.abs(height) > 128.0) {
            throw new IllegalArgumentException("Orbit height must be between -128 and 128 blocks.");
        }

        Location center = player.getEyeLocation();
        if (!sameWorld(current, center)) {
            throw new IllegalArgumentException("Orbit target must be in the hand's current world.");
        }

        translationMode = TranslationMode.ORBIT;
        translationTarget = player;
        orbitRadius = radius;
        orbitDegreesPerTick = degreesPerTick;
        orbitHeight = height;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;

        // Begin at the hand's current polar angle around the target to avoid a
        // gratuitous 180-degree correction on the first orbit tick.
        orbitAngleDegrees = Math.toDegrees(Math.atan2(
                current.getZ() - center.getZ(),
                current.getX() - center.getX()
        ));
        clearTravelState();
        lastTranslationStopReason = "orbit active";
    }

    private Location tickChase(Location current) {
        Location target = resolveTranslationPlayerLocation();
        if (target == null) {
            return current.clone();
        }
        if (!sameWorld(current, target)) {
            stopTranslation("chase target changed worlds");
            return current.clone();
        }

        double dx = current.getX() - target.getX();
        double dy = current.getY() - target.getY();
        double dz = current.getZ() - target.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        Location desired;
        if (distance <= POSITION_EPSILON || stopDistance <= POSITION_EPSILON) {
            desired = target;
        } else {
            double keep = stopDistance;
            double inv = 1.0 / distance;
            desired = new Location(
                    target.getWorld(),
                    target.getX() + dx * inv * keep,
                    target.getY() + dy * inv * keep,
                    target.getZ() + dz * inv * keep
            );
        }
        return steerToward(current, desired);
    }

    private Location tickFollow(Location current) {
        Location eye = resolveTranslationPlayerLocation();
        if (eye == null) {
            return current.clone();
        }
        if (!sameWorld(current, eye)) {
            stopTranslation("follow target changed worlds");
            return current.clone();
        }

        Vector direction = translationTarget.getEyeLocation().getDirection();
        double hx = direction.getX();
        double hz = direction.getZ();
        double horizontalLength = Math.sqrt(hx * hx + hz * hz);
        if (horizontalLength < POSITION_EPSILON) {
            hx = 0.0;
            hz = 1.0;
            horizontalLength = 1.0;
        }
        hx /= horizontalLength;
        hz /= horizontalLength;

        Location desired = new Location(
                eye.getWorld(),
                eye.getX() - hx * followDistance,
                eye.getY() + followHeight,
                eye.getZ() - hz * followDistance
        );
        return steerToward(current, desired);
    }

    private Location tickOrbit(Location current) {
        Location center = resolveTranslationPlayerLocation();
        if (center == null) {
            return current.clone();
        }
        if (!sameWorld(current, center)) {
            stopTranslation("orbit target changed worlds");
            return current.clone();
        }

        orbitAngleDegrees = normalizeAngle(orbitAngleDegrees + orbitDegreesPerTick);
        double radians = Math.toRadians(orbitAngleDegrees);
        Location desired = new Location(
                center.getWorld(),
                center.getX() + Math.cos(radians) * orbitRadius,
                center.getY() + orbitHeight,
                center.getZ() + Math.sin(radians) * orbitRadius
        );
        return steerToward(current, desired);
    }

    private Location steerToward(Location current, Location desired) {
        double dx = desired.getX() - current.getX();
        double dy = desired.getY() - current.getY();
        double dz = desired.getZ() - current.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distance < POSITION_EPSILON) {
            approachVelocity(0.0, 0.0, 0.0);
            if (velocityLength() < VELOCITY_EPSILON) {
                zeroVelocity();
            }
            return current.clone();
        }

        // Arrival speed ensures enough braking room even if the desired point
        // abruptly stops. This is what gives acceleration/deceleration.
        double arrivalSpeed = Math.sqrt(2.0 * acceleration * distance);
        double desiredSpeed = Math.min(maxSpeed, arrivalSpeed);
        double invDistance = 1.0 / distance;
        approachVelocity(
                dx * invDistance * desiredSpeed,
                dy * invDistance * desiredSpeed,
                dz * invDistance * desiredSpeed
        );

        double speed = velocityLength();
        if (speed > distance) {
            // Never step through the dynamic target point in a single tick.
            velocityX = dx;
            velocityY = dy;
            velocityZ = dz;
        }

        return current.clone().add(velocityX, velocityY, velocityZ);
    }

    private void approachVelocity(double targetX, double targetY, double targetZ) {
        double dx = targetX - velocityX;
        double dy = targetY - velocityY;
        double dz = targetZ - velocityZ;
        double delta = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (delta <= acceleration || delta < VELOCITY_EPSILON) {
            velocityX = targetX;
            velocityY = targetY;
            velocityZ = targetZ;
            return;
        }

        double scale = acceleration / delta;
        velocityX += dx * scale;
        velocityY += dy * scale;
        velocityZ += dz * scale;
    }


    /**
     * action-only steering toward a dynamic world point. Repeated calls
     * update the desired point without resetting velocity, which is what lets a
     * closing claw chase a running player instead of snapping them into the hand.
     */
    public void startActionSteer(Location current, Location desired, double maxSpeed, double acceleration) {
        requireSameWorld(current, desired, "Action steer target must be in the hand's current world.");
        validateSteering(maxSpeed, acceleration);
        translationMode = TranslationMode.ACTION_STEER;
        translationTarget = null;
        actionSteerTarget = desired.clone();
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        clearTravelState();
        lastTranslationStopReason = "action steer active";
    }

    private Location tickActionSteer(Location current) {
        if (actionSteerTarget == null || !sameWorld(current, actionSteerTarget)) {
            stopTranslation("invalid action steer target");
            return current.clone();
        }
        return steerToward(current, actionSteerTarget);
    }

    // ---------------------------------------------------------------------
    // Look tracking
    // ---------------------------------------------------------------------

    public void lookAt(Player player, double turnSpeed) {
        setLookPlayer(player, turnSpeed, LookMode.PALM_FRONT);
    }

    public void lookAt(Location point, double turnSpeed) {
        setLookPoint(point, turnSpeed, LookMode.PALM_FRONT);
    }

    public void downLookAt(Player player, double turnSpeed) {
        setLookPlayer(player, turnSpeed, LookMode.PALM_DOWN);
    }

    public void downLookAt(Location point, double turnSpeed) {
        setLookPoint(point, turnSpeed, LookMode.PALM_DOWN);
    }

    public void pointAt(Player player, double turnSpeed) {
        setLookPlayer(player, turnSpeed, LookMode.FINGER_POINT);
    }

    public void pointAt(Location point, double turnSpeed) {
        setLookPoint(point, turnSpeed, LookMode.FINGER_POINT);
    }

    private void setLookPlayer(Player player, double turnSpeed, LookMode mode) {
        validatePlayer(player);
        validateTurnSpeed(turnSpeed);
        lookPlayer = player;
        lookPoint = null;
        lookTurnSpeed = turnSpeed;
        lookMode = mode;
        lastLookStopReason = switch (mode) {
            case PALM_DOWN -> "down-tracking player";
            case FINGER_POINT -> "finger-pointing at player";
            case PALM_FRONT -> "tracking player";
        };
    }

    private void setLookPoint(Location point, double turnSpeed, LookMode mode) {
        if (point == null || point.getWorld() == null) {
            throw new IllegalArgumentException("Look target location must have a world.");
        }
        validateTurnSpeed(turnSpeed);
        lookPlayer = null;
        lookPoint = point.clone();
        lookTurnSpeed = turnSpeed;
        lookMode = mode;
        lastLookStopReason = switch (mode) {
            case PALM_DOWN -> "down-tracking point";
            case FINGER_POINT -> "finger-pointing at point";
            case PALM_FRONT -> "tracking point";
        };
    }

    public boolean clearLook() {
        return clearLook("stopped by command");
    }

    private boolean clearLook(String reason) {
        boolean wasActive = hasLookTarget();
        lookPlayer = null;
        lookPoint = null;
        lastLookStopReason = reason;
        return wasActive;
    }

    public boolean hasLookTarget() {
        return lookPlayer != null || lookPoint != null;
    }

    public String getLookDescription() {
        if (lookPlayer != null) {
            return "player " + lookPlayer.getName();
        }
        if (lookPoint != null) {
            return String.format(Locale.US, "point %.2f, %.2f, %.2f", lookPoint.getX(), lookPoint.getY(), lookPoint.getZ());
        }
        return "idle (" + lastLookStopReason + ")";
    }

    public double getLookTurnSpeed() {
        return lookTurnSpeed;
    }

    public LookMode getLookMode() {
        return lookMode;
    }

    public String getLookModeName() {
        return switch (lookMode) {
            case PALM_FRONT -> "palm-front";
            case PALM_DOWN -> "palm-down / fingers-track";
            case FINGER_POINT -> "finger-point / palm-down bias";
        };
    }

    private Location resolveLookLocation() {
        if (lookPlayer != null) {
            if (!lookPlayer.isOnline()) {
                clearLook("look target disconnected");
                return null;
            }
            return lookPlayer.getEyeLocation();
        }
        return lookPoint == null ? null : lookPoint.clone();
    }

    // ---------------------------------------------------------------------
    // State / diagnostics
    // ---------------------------------------------------------------------

    public boolean stopTranslation() {
        return stopTranslation("stopped by command");
    }

    private boolean stopTranslation(String reason) {
        boolean wasActive = translationMode != TranslationMode.IDLE || velocityLength() > VELOCITY_EPSILON;
        translationMode = TranslationMode.IDLE;
        translationTarget = null;
        actionSteerTarget = null;
        clearTravelState();
        zeroVelocity();
        lastTranslationStopReason = reason;
        return wasActive;
    }

    public TranslationMode getTranslationMode() {
        return translationMode;
    }

    public boolean isTranslationActive() {
        return translationMode != TranslationMode.IDLE;
    }

    public String getTranslationDescription() {
        return switch (translationMode) {
            case IDLE -> "idle (" + lastTranslationStopReason + ")";
            case TRAVEL -> String.format(Locale.US, "travel %.1f%%", getTravelProgress() * 100.0);
            case CHASE -> "chase " + safePlayerName(translationTarget);
            case FOLLOW -> "follow " + safePlayerName(translationTarget);
            case ORBIT -> "orbit " + safePlayerName(translationTarget);
            case ACTION_STEER -> "action steer";
        };
    }

    public double getVelocityX() {
        return velocityX;
    }

    public double getVelocityY() {
        return velocityY;
    }

    public double getVelocityZ() {
        return velocityZ;
    }

    public double getSpeed() {
        return velocityLength();
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public double getAcceleration() {
        return acceleration;
    }

    public double getStopDistance() {
        return stopDistance;
    }

    public double getFollowDistance() {
        return followDistance;
    }

    public double getFollowHeight() {
        return followHeight;
    }

    public double getOrbitRadius() {
        return orbitRadius;
    }

    public double getOrbitHeight() {
        return orbitHeight;
    }

    public double getOrbitDegreesPerTick() {
        return orbitDegreesPerTick;
    }

    public double getTravelProgress() {
        if (translationMode != TranslationMode.TRAVEL || travelDurationTicks <= 0) {
            return 0.0;
        }
        return Math.min(1.0, travelElapsedTicks / (double) travelDurationTicks);
    }

    public int getTravelElapsedTicks() {
        return travelElapsedTicks;
    }

    public int getTravelDurationTicks() {
        return travelDurationTicks;
    }

    public EasingCurve getTravelEasing() {
        return travelEasing;
    }

    private Location resolveTranslationPlayerLocation() {
        if (translationTarget == null) {
            stopTranslation("tracking target missing");
            return null;
        }
        if (!translationTarget.isOnline()) {
            stopTranslation("tracking target disconnected");
            return null;
        }
        return translationTarget.getEyeLocation();
    }

    private void clearTravelState() {
        travelStart = null;
        travelTarget = null;
        travelDurationTicks = 0;
        travelElapsedTicks = 0;
    }

    private void zeroVelocity() {
        velocityX = 0.0;
        velocityY = 0.0;
        velocityZ = 0.0;
    }

    private double velocityLength() {
        return Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
    }

    private static Location lerp(Location a, Location b, double t) {
        return new Location(
                a.getWorld(),
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t,
                a.getZ() + (b.getZ() - a.getZ()) * t
        );
    }

    private static double approachAngle(double current, double target, double maxDelta) {
        double delta = shortestAngleDelta(current, target);
        if (Math.abs(delta) <= maxDelta) {
            return normalizeAngle(target);
        }
        return normalizeAngle(current + Math.copySign(maxDelta, delta));
    }

    private static double shortestAngleDelta(double current, double target) {
        return normalizeAngle(target - current);
    }

    private static double normalizeAngle(double angle) {
        angle %= 360.0;
        if (angle >= 180.0) {
            angle -= 360.0;
        } else if (angle < -180.0) {
            angle += 360.0;
        }
        return angle;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean sameWorld(Location a, Location b) {
        World aw = a == null ? null : a.getWorld();
        World bw = b == null ? null : b.getWorld();
        return aw != null && aw.equals(bw);
    }

    private static void requireSameWorld(Location a, Location b, String message) {
        if (!sameWorld(a, b)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void validatePlayer(Player player) {
        if (player == null || !player.isOnline()) {
            throw new IllegalArgumentException("Target player must be online.");
        }
    }

    private static void validateSteering(double maxSpeed, double acceleration) {
        if (!Double.isFinite(maxSpeed) || maxSpeed <= 0.0 || maxSpeed > 8.0) {
            throw new IllegalArgumentException("Max speed must be greater than 0 and at most 8 blocks/tick.");
        }
        if (!Double.isFinite(acceleration) || acceleration <= 0.0 || acceleration > 2.0) {
            throw new IllegalArgumentException("Acceleration must be greater than 0 and at most 2 blocks/tick².");
        }
    }

    private static void validateTurnSpeed(double turnSpeed) {
        if (!Double.isFinite(turnSpeed) || turnSpeed <= 0.0 || turnSpeed > 180.0) {
            throw new IllegalArgumentException("Look turn speed must be greater than 0 and at most 180 degrees/tick.");
        }
    }

    private static String safePlayerName(Player player) {
        return player == null ? "?" : player.getName();
    }
}
