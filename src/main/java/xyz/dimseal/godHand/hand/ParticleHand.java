package xyz.dimseal.godHand.hand;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import xyz.dimseal.godHand.hand.animation.EasingCurve;
import xyz.dimseal.godHand.hand.animation.HandAnimationController;
import xyz.dimseal.godHand.hand.animation.HandPoseLibrary;
import xyz.dimseal.godHand.hand.animation.JointSnapshot;
import xyz.dimseal.godHand.hand.motion.HandMotionController;
import xyz.dimseal.godHand.hand.motion.LookTarget;
import xyz.dimseal.godHand.hand.motion.TranslationMode;
import xyz.dimseal.godHand.hand.skeleton.FingerState;
import xyz.dimseal.godHand.hand.skeleton.HandDigit;
import xyz.dimseal.godHand.hand.skeleton.HandPose;
import xyz.dimseal.godHand.hand.render.HandDensity;
import xyz.dimseal.godHand.hand.render.HandPalette;
import xyz.dimseal.godHand.hand.render.WristStyle;
import xyz.dimseal.godHand.hand.render.HandRenderMode;
import xyz.dimseal.godHand.math.Rotation3D;

import java.util.EnumMap;
import java.util.Map;

/**
 * Runtime transform, articulation, joint animation,
 * world-space motion/orientation, combat/interaction state, and rendering state for the GodHand.
 *
 * Coordinate convention:
 *   +X = palm right
 *   +Y = wrist toward fingertips
 *   +Z = palm front
 *
 * Translation, look tracking, root roll/spin, and the 15 finger joints remain
 * independent control layers. All are advanced by the existing single 20 Hz
 * engine scheduler.
 */
public final class ParticleHand {

    private Location location;

    /** Palm width in world blocks. */
    private double scale;

    private double yaw;
    private double pitch;
    private double roll;

    private double yawVelocity;
    private double pitchVelocity;
    private double rollVelocity;

    private boolean axesVisible = true;
    private boolean skeletonVisible = true;
    private boolean gripDebugVisible = false;

    // visual controls. These affect rendering only and deliberately do
    // not participate in motion, orientation, articulation, or action state.
    private HandDensity density = HandDensity.ULTRA;
    private Color baseColor = HandPalette.WHITE;
    private boolean shadingEnabled = true;
    private WristStyle wristStyle = WristStyle.ANATOMICAL;
    private HandRenderMode renderMode = HandRenderMode.ITEM_DISPLAYS;

    // rendering/combat controls. Forced particles use Paper's long-
    // distance packet flag; combat debug visualizes the oriented palm volume.
    private boolean forceParticles = true;
    private boolean combatDebugVisible = false;
    private double slamDamage = 8.0;
    private double slamHorizontalKnockback = 1.35;
    private double slamVerticalKnockback = 0.65;

    private final Map<HandDigit, FingerState> fingers = new EnumMap<>(HandDigit.class);
    private final HandAnimationController animation = new HandAnimationController();
    private final HandMotionController motion = new HandMotionController();
    private final HandActionController action = new HandActionController();
    private HandPose currentPose = HandPose.OPEN;

    // post-action idle. This is deliberately not an Action so finished
    // attacks remain logically IDLE while the visible hand continues to feel
    // alive. The mode is inferred from the hand's final palm orientation.
    private boolean idleArmed;
    private boolean idlePalmDown;
    private Location idleAnchor;
    private double idlePhase;

    // HandManager consumes this after the tick. Used by one-shot attacks such
    // as SLAP that should disappear after their recovery instead of entering
    // the normal post-action idle.
    private boolean removalRequested;
    private boolean idleAfterMotion;

    // ItemDisplay dismissal transition. requestRemoval() becomes a
    // short visual lifecycle for solid Hands instead of deleting 50+ tracked
    // cuboids in the same frame. Particle-mode cleanup remains immediate.
    private boolean dismissalActive;
    private int dismissalTicksRemaining;
    private int dismissalTotalTicks;
    private Location dismissalStart;
    private double dismissalVisualScale = 1.0;

    public ParticleHand(Location location, double scale) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Hand location must have a world.");
        }
        this.location = location.clone();
        setScale(scale);
        for (HandDigit digit : HandDigit.values()) {
            fingers.put(digit, new FingerState());
        }
        applyPose(HandPose.OPEN);
    }

    public void tick() {
        boolean actionWasActive = action.isActive();
        if (actionWasActive) {
            disarmIdle();
        }

        // translation first, because orientation should evaluate from the
        // hand's newly calculated origin on this tick.
        location = motion.tickTranslation(location);

        LookTarget lookTarget = motion.tickLook(location, yaw, pitch, roll);
        if (lookTarget != null) {
            yaw = normalizeAngle(lookTarget.yaw());
            pitch = normalizeAngle(lookTarget.pitch());
            if (lookTarget.controlsRoll()) {
                // PALM_DOWN owns roll because roll is part of the
                // constrained face-down basis.
                roll = normalizeAngle(lookTarget.roll());
            } else {
                // Preserve established behavior: palm-front look can coexist with
                // independent roll/banking.
                roll = normalizeAngle(roll + rollVelocity);
            }
        } else {
            yaw = normalizeAngle(yaw + yawVelocity);
            pitch = normalizeAngle(pitch + pitchVelocity);
            roll = normalizeAngle(roll + rollVelocity);
        }

        if (animation.isActive()) {
            boolean completed = animation.tick(fingers);
            if (completed) {
                currentPose = animation.getTargetPose();
            }
        }

        // The action layer observes the already-evaluated lower layers and advances its
        // state machine. A phase transition started here takes effect next tick,
        // keeping all mutation on the same single server-thread scheduler.
        action.tick(this);

        boolean actionNowActive = action.isActive();
        if (dismissalActive) {
            tickDismissal();
            return;
        }
        if (actionWasActive && !actionNowActive && !removalRequested) {
            armPostActionIdle();
        }
        if (idleAfterMotion && !actionNowActive && !motion.isTranslationActive() && !removalRequested) {
            idleAfterMotion = false;
            armPostActionIdle();
        }
        if (!actionNowActive && idleArmed && !removalRequested) {
            tickPostActionIdle();
        }
    }

    public Location getLocation() {
        return location.clone();
    }

    /** renderer root. Carry synchronization is handled by matched one-tick motion, not visual root correction. */
    public Location getRenderLocation() {
        Location corrected = action.getVisualRenderOrigin(this, this);
        return corrected == null ? location.clone() : corrected;
    }

    /** Renderer root accessor for temporary secondary Hands that share the primary action controller. */
    public Location getVisualRenderLocationFor(ParticleHand queriedHand) {
        Location corrected = action.getVisualRenderOrigin(this, queriedHand);
        return corrected == null ? queriedHand.getLocation() : corrected;
    }

    public World getWorld() {
        return location.getWorld();
    }

    /** Manual teleport: cancel only translation, not look/finger animation. */
    public void setLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Hand location must have a world.");
        }
        if (this.location != null && this.location.getWorld() != null && !this.location.getWorld().equals(location.getWorld())) {
            throw new IllegalArgumentException("Cross-dimensional Hand movement is disabled.");
        }
        manualOverride(HandControlLayer.TRANSLATION);
        motion.stopTranslation();
        this.location = location.clone();
    }

    /** Manual offset: cancel only translation, not look/finger animation. */
    public void translate(double x, double y, double z) {
        manualOverride(HandControlLayer.TRANSLATION);
        motion.stopTranslation();
        location.add(x, y, z);
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0 || scale > 64.0) {
            throw new IllegalArgumentException("Hand palm width must be greater than 0 and at most 64 blocks.");
        }
        this.scale = scale;
    }

    public double getYaw() {
        return yaw;
    }

    /** Manual yaw overrides look tracking. */
    public void setYaw(double yaw) {
        manualOverride(HandControlLayer.ORIENTATION);
        motion.clearLook();
        this.yaw = normalizeAngle(yaw);
    }

    /** Internal setter used when initializing while preserving no controller state. */
    private void setYawInternal(double yaw) {
        this.yaw = normalizeAngle(yaw);
    }

    public double getPitch() {
        return pitch;
    }

    /** Manual pitch overrides look tracking. */
    public void setPitch(double pitch) {
        manualOverride(HandControlLayer.ORIENTATION);
        motion.clearLook();
        this.pitch = normalizeAngle(pitch);
    }

    private void setPitchInternal(double pitch) {
        this.pitch = normalizeAngle(pitch);
    }

    public double getRoll() {
        return roll;
    }

    public void setRoll(double roll) {
        manualOverride(HandControlLayer.ORIENTATION);
        this.roll = normalizeAngle(roll);
    }

    /** Manual full rotation overrides look tracking. */
    public void setRotation(double yaw, double pitch, double roll) {
        manualOverride(HandControlLayer.ORIENTATION);
        motion.clearLook();
        setYawInternal(yaw);
        setPitchInternal(pitch);
        this.roll = normalizeAngle(roll);
    }

    public double getYawVelocity() {
        return yawVelocity;
    }

    public double getPitchVelocity() {
        return pitchVelocity;
    }

    public double getRollVelocity() {
        return rollVelocity;
    }

    /**
     * Yaw/pitch spin conflicts with look-at, so any non-zero yaw or pitch spin
     * explicitly returns orientation control to manual root rotation. Roll-only
     * spin can coexist with look-at.
     */
    public void setAngularVelocity(double yawVelocity, double pitchVelocity, double rollVelocity) {
        manualOverride(HandControlLayer.ORIENTATION);
        yawVelocity = requireFinite(yawVelocity, "yaw velocity");
        pitchVelocity = requireFinite(pitchVelocity, "pitch velocity");
        rollVelocity = requireFinite(rollVelocity, "roll velocity");
        if (Math.abs(yawVelocity) > 1.0e-12 || Math.abs(pitchVelocity) > 1.0e-12) {
            motion.clearLook();
        }
        this.yawVelocity = yawVelocity;
        this.pitchVelocity = pitchVelocity;
        this.rollVelocity = rollVelocity;
    }

    public void stopRotation() {
        manualOverride(HandControlLayer.ORIENTATION);
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        rollVelocity = 0.0;
    }

    public boolean isAxesVisible() {
        return axesVisible;
    }

    public void setAxesVisible(boolean axesVisible) {
        this.axesVisible = axesVisible;
    }

    public boolean isSkeletonVisible() {
        return skeletonVisible;
    }

    public void setSkeletonVisible(boolean skeletonVisible) {
        this.skeletonVisible = skeletonVisible;
    }

    public boolean isGripDebugVisible() {
        return gripDebugVisible;
    }

    public void setGripDebugVisible(boolean gripDebugVisible) {
        this.gripDebugVisible = gripDebugVisible;
    }

    public HandDensity getDensity() {
        return density;
    }

    public void setDensity(HandDensity density) {
        if (density == null) {
            throw new IllegalArgumentException("Hand density cannot be null.");
        }
        this.density = density;
    }

    public Color getBaseColor() {
        return baseColor;
    }

    public void setBaseColor(Color baseColor) {
        if (baseColor == null) {
            throw new IllegalArgumentException("Hand color cannot be null.");
        }
        this.baseColor = baseColor;
    }

    public boolean isShadingEnabled() {
        return shadingEnabled;
    }

    public void setShadingEnabled(boolean shadingEnabled) {
        this.shadingEnabled = shadingEnabled;
    }

    public WristStyle getWristStyle() {
        return wristStyle;
    }

    public void setWristStyle(WristStyle wristStyle) {
        if (wristStyle == null) throw new IllegalArgumentException("Wrist style cannot be null.");
        this.wristStyle = wristStyle;
    }

    public HandRenderMode getRenderMode() {
        return renderMode;
    }

    public void setRenderMode(HandRenderMode renderMode) {
        if (renderMode == null) throw new IllegalArgumentException("Render mode cannot be null.");
        this.renderMode = renderMode;
    }

    public void resetVisuals() {
        density = HandDensity.ULTRA;
        baseColor = HandPalette.WHITE;
        shadingEnabled = true;
        forceParticles = true;
        wristStyle = WristStyle.ANATOMICAL;
        renderMode = HandRenderMode.ITEM_DISPLAYS;
    }

    public boolean isForceParticles() {
        return forceParticles;
    }

    public void setForceParticles(boolean forceParticles) {
        this.forceParticles = forceParticles;
    }

    public boolean isCombatDebugVisible() {
        return combatDebugVisible;
    }

    public void setCombatDebugVisible(boolean combatDebugVisible) {
        this.combatDebugVisible = combatDebugVisible;
    }

    public double getSlamDamage() {
        return slamDamage;
    }

    public void setSlamDamage(double slamDamage) {
        if (!Double.isFinite(slamDamage) || slamDamage < 0.0 || slamDamage > 100.0) {
            throw new IllegalArgumentException("Slam damage must be between 0 and 100.");
        }
        this.slamDamage = slamDamage;
    }

    public double getSlamHorizontalKnockback() {
        return slamHorizontalKnockback;
    }

    public void setSlamHorizontalKnockback(double slamHorizontalKnockback) {
        if (!Double.isFinite(slamHorizontalKnockback) || slamHorizontalKnockback < 0.0 || slamHorizontalKnockback > 5.0) {
            throw new IllegalArgumentException("Slam horizontal knockback must be between 0 and 5 blocks/tick.");
        }
        this.slamHorizontalKnockback = slamHorizontalKnockback;
    }

    public double getSlamVerticalKnockback() {
        return slamVerticalKnockback;
    }

    public void setSlamVerticalKnockback(double slamVerticalKnockback) {
        if (!Double.isFinite(slamVerticalKnockback) || slamVerticalKnockback < -2.0 || slamVerticalKnockback > 3.0) {
            throw new IllegalArgumentException("Slam vertical knockback must be between -2 and 3 blocks/tick.");
        }
        this.slamVerticalKnockback = slamVerticalKnockback;
    }

    public int getLastImpactHitCount() {
        return action.getLastImpactHitCount();
    }

    public FingerState getFingerState(HandDigit digit) {
        return fingers.get(digit);
    }

    public HandPose getCurrentPose() {
        return currentPose;
    }

    public String getPoseName() {
        if (animation.isActive() && animation.getTargetPose() != null) {
            return "transitioning->" + animation.getTargetPose().commandName();
        }
        if (idleArmed) {
            return idlePalmDown ? "idle-down" : "idle-upright";
        }
        return currentPose == null ? "custom" : currentPose.commandName();
    }

    // ---------------------------------------------------------------------
    // world-space translation/orientation
    // ---------------------------------------------------------------------

    public void travelTo(Location target, int durationTicks, EasingCurve easing) {
        requireSameWorld(target, "Cross-dimensional Hand movement is disabled.");
        manualOverride(HandControlLayer.TRANSLATION);
        motion.startTravel(location, target, durationTicks, easing);
    }

    /** Main-interface move: travel normally, then automatically return to living idle. */
    public void travelToAndIdle(Location target, int durationTicks, EasingCurve easing) {
        requireSameWorld(target, "Cross-dimensional Hand movement is disabled.");
        manualOverride(HandControlLayer.TRANSLATION);
        motion.startTravel(location, target, durationTicks, easing);
        idleAfterMotion = true;
    }

    public void chase(Player player, double stopDistance, double maxSpeed, double acceleration) {
        requireSameWorld(player == null ? null : player.getLocation(), "Cross-dimensional Hand movement is disabled.");
        manualOverride(HandControlLayer.TRANSLATION);
        motion.startChase(player, stopDistance, maxSpeed, acceleration);
    }

    public void follow(Player player, double distance, double height, double maxSpeed, double acceleration) {
        requireSameWorld(player == null ? null : player.getLocation(), "Cross-dimensional Hand movement is disabled.");
        manualOverride(HandControlLayer.TRANSLATION);
        motion.startFollow(player, distance, height, maxSpeed, acceleration);
    }

    public void orbit(Player player, double radius, double degreesPerTick, double height,
                      double maxSpeed, double acceleration) {
        requireSameWorld(player == null ? null : player.getLocation(), "Cross-dimensional Hand movement is disabled.");
        manualOverride(HandControlLayer.TRANSLATION);
        motion.startOrbit(player, location, radius, degreesPerTick, height, maxSpeed, acceleration);
    }

    public boolean stopMotion() {
        boolean actionCancelled = manualOverride(HandControlLayer.TRANSLATION);
        return actionCancelled || motion.stopTranslation();
    }

    /** established behavior: point local +Z (palm front) at the target. */
    public void lookAt(Player player, double turnSpeed) {
        requireSameWorld(player == null ? null : player.getLocation(), "Cross-dimensional Hand look targets are disabled.");
        manualOverride(HandControlLayer.ORIENTATION);
        motion.lookAt(player, turnSpeed);
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
    }

    public void lookAt(Location point, double turnSpeed) {
        requireSameWorld(point, "Look target must be in the hand's current world.");
        manualOverride(HandControlLayer.ORIENTATION);
        motion.lookAt(point, turnSpeed);
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
    }

    /** attack behavior: palm stays down; local +Y fingers track horizontally. */
    public void downLookAt(Player player, double turnSpeed) {
        requireSameWorld(player == null ? null : player.getLocation(), "Cross-dimensional Hand look targets are disabled.");
        manualOverride(HandControlLayer.ORIENTATION);
        motion.downLookAt(player, turnSpeed);
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        rollVelocity = 0.0;
    }

    public void downLookAt(Location point, double turnSpeed) {
        requireSameWorld(point, "Down-look target must be in the hand's current world.");
        manualOverride(HandControlLayer.ORIENTATION);
        motion.downLookAt(point, turnSpeed);
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        rollVelocity = 0.0;
    }

    public boolean stopLooking() {
        boolean actionCancelled = manualOverride(HandControlLayer.ORIENTATION);
        return actionCancelled || motion.clearLook();
    }

    public TranslationMode getTranslationMode() {
        return motion.getTranslationMode();
    }

    public boolean isMoving() {
        return motion.isTranslationActive();
    }

    public String getMotionDescription() {
        return motion.getTranslationDescription();
    }

    public double getMotionSpeed() {
        return motion.getSpeed();
    }

    public double getMotionVelocityX() {
        return motion.getVelocityX();
    }

    public double getMotionVelocityY() {
        return motion.getVelocityY();
    }

    public double getMotionVelocityZ() {
        return motion.getVelocityZ();
    }

    public double getMotionMaxSpeed() {
        return motion.getMaxSpeed();
    }

    public double getMotionAcceleration() {
        return motion.getAcceleration();
    }

    public double getTravelProgress() {
        return motion.getTravelProgress();
    }

    public int getTravelElapsedTicks() {
        return motion.getTravelElapsedTicks();
    }

    public int getTravelDurationTicks() {
        return motion.getTravelDurationTicks();
    }

    public EasingCurve getTravelEasing() {
        return motion.getTravelEasing();
    }

    public boolean isLooking() {
        return motion.hasLookTarget();
    }

    public String getLookDescription() {
        return motion.getLookDescription();
    }

    public double getLookTurnSpeed() {
        return motion.getLookTurnSpeed();
    }

    public String getLookModeName() {
        return motion.getLookModeName();
    }

    // ---------------------------------------------------------------------
    // constrained attack orientation + behavior actions
    // ---------------------------------------------------------------------

    public void slam(Player target, double height, int riseTicks, int dropTicks) {
        action.startSlam(this, target, height, riseTicks, dropTicks);
    }

    public void slam(Player target, double height, int riseTicks, int dropTicks, EasingCurve approachEasing) {
        action.startSlam(this, target, height, riseTicks, dropTicks, approachEasing);
    }

    public void grab(Player target, double height, int approachTicks, int closeTicks) {
        action.startGrab(this, target, height, approachTicks, closeTicks);
    }

    public void judgment(Player target, double stageDistance, int approachTicks, int strikeTicks) {
        action.startJudgment(this, target, stageDistance, approachTicks, strikeTicks);
    }

    /** preserves the former Judgment charge as ForceSlap. */
    public void forceSlap(Player target, double stageDistance, int approachTicks, int strikeTicks) {
        action.startForceSlap(this, target, stageDistance, approachTicks, strikeTicks);
    }

    /** heavy palm-down fist strike. */
    public void punch(Player target, double stageDistance, int approachTicks, int strikeTicks,
                      double damage, double minimumHealthLoss, double horizontalKnockback, double verticalKnockback) {
        action.startPunch(this, target, stageDistance, approachTicks, strikeTicks,
                damage, minimumHealthLoss, horizontalKnockback, verticalKnockback);
    }

    /** low-damage, extreme-knockback open-palm sweep. */
    public void slap(Player target, double stageDistance, int approachTicks, int strikeTicks,
                     double damage, double horizontalKnockback, double verticalKnockback) {
        action.startSlap(this, target, stageDistance, approachTicks, strikeTicks,
                damage, horizontalKnockback, verticalKnockback);
    }

    /** grab -> live player destination transport. */
    public void transport(Player target, Player destination, double height, int approachTicks, int closeTicks) {
        action.startTransport(this, target, destination, height, approachTicks, closeTicks);
    }

    /** grab -> fixed world destination transport. */
    public void transport(Player target, Location destination, double height, int approachTicks, int closeTicks) {
        action.startTransport(this, target, destination, height, approachTicks, closeTicks);
    }

    /** move an already-held player and keep holding on arrival. */
    public void moveHeldTo(Player destination) {
        action.startMoveTo(this, destination);
    }

    public void moveHeldTo(Location destination) {
        action.startMoveTo(this, destination);
    }

    /** fast corkscrew charge. */
    public void cyclone(Player target, double stageDistance, int approachTicks, int strikeTicks) {
        action.startCyclone(this, target, stageDistance, approachTicks, strikeTicks);
    }

    /** bunker/interior manifestation with ceiling-aware placement. */
    public void breach(Player target, int durationTicks) {
        action.startBreach(this, target, durationTicks);
    }

    /** grab, lift above the surface canopy, then toss the target far. */
    public void toss(Player target, double height, int approachTicks, int closeTicks) {
        action.startToss(this, target, height, approachTicks, closeTicks);
    }

    /** benevolent five-second held blessing. */
    public void bless(Player target, double height, int approachTicks, int closeTicks) {
        action.startBless(this, target, height, approachTicks, closeTicks);
    }

    /** short protective/healing field. */
    public void sanctuary(Player target, int guardTicks) {
        action.startSanctuary(this, target, guardTicks);
    }

    /** seven-hit dual-Hand slap sequence. */
    public void spank(Player target, double height, int approachTicks, int closeTicks) {
        action.startSpank(this, target, height, approachTicks, closeTicks);
    }

    /** rage combo: three dash impacts, physical grab, aerial swing throw. */
    public void rage(Player target) {
        action.startRage(this, target);
    }

    /** dual-Hand horizontal thunder clap. */
    public void clap(Player target) {
        action.startClap(this, target);
    }

    /** alternating two-Hand fist pounds to a three-heart mercy threshold. */
    public void pound(Player target) {
        action.startPound(this, target);
    }

    /** animated open-palm wave in front of a player. */
    public void wave(Player target) {
        action.startWave(this, target);
    }

    /** thumbs-up pose in front of a player. */
    public void thumbsUp(Player target) {
        action.startThumb(this, target, true);
    }

    /** thumbs-down pose in front of a player. */
    public void thumbsDown(Player target) {
        action.startThumb(this, target, false);
    }

    /** surface-only middle-finger lightning humiliation. */
    /** Non-combat middle-finger presentation gesture. */
    public void bird(Player target) {
        action.startBird(this, target);
    }

    public void giveBird(Player target) {
        action.startGiveBird(this, target);
    }

    /** persistent two-Hand aerial juggling. */
    public void juggle(Player target, double height, int approachTicks, int closeTicks) {
        action.startJuggle(this, target, height, approachTicks, closeTicks);
    }

    /** emerald scale-1 wolf-like guardian companion. */
    public void guard(Player owner) {
        action.startGuard(this, owner);
    }

    public boolean isGuarding() { return action.isGuarding(); }
    public Player getGuardOwner() { return action.getGuardOwner(); }
    public void guardAggro(org.bukkit.entity.LivingEntity enemy) { action.guardAggro(this, enemy); }

    /** destructive live-player smash. */
    public void smash(Player target, double height, int approachTicks, float explosionPower) {
        action.startSmash(this, target, height, approachTicks, explosionPower);
    }

    /** destructive fixed-point smash. */
    public void smash(Location target, double height, int approachTicks, float explosionPower) {
        action.startSmash(this, target, height, approachTicks, explosionPower);
    }

    /** quieter autonomous presence behavior. */
    public void stalk(Player target) {
        action.startStalk(this, target);
    }

    /** persistent hovering palm-down claw chase. */
    public void hoverChase(Player target) {
        action.startHoverChase(this, target);
    }

    public boolean isStalking() {
        return action.isStalking();
    }

    public boolean isHoverChasing() {
        return action.isHoverChasing();
    }

    public boolean isPostActionIdle() {
        return idleArmed && !action.isActive();
    }

    /** Long-running presence behaviors use a lower visual refresh rate to avoid sustained packet pressure. */
    public boolean isPersistentPresence() {
        HandActionType type = action.getType();
        return type == HandActionType.STALK || type == HandActionType.CHASE || type == HandActionType.GUARD || type == HandActionType.JUGGLE;
    }

    public boolean releaseGrab(int releaseTicks) {
        return action.release(this, releaseTicks);
    }

    public boolean throwHeldPlayer(double forwardSpeed, double upwardSpeed, int openTicks) {
        return action.throwHeld(this, forwardSpeed, upwardSpeed, openTicks);
    }

    public boolean cancelAction() {
        boolean cancelled = action.cancel(this, "cancelled by command", true);
        if (cancelled && !removalRequested) armPostActionIdle();
        return cancelled;
    }

    public boolean isActionActive() {
        return action.isActive();
    }

    public boolean isHoldingPlayer() {
        return action.isHolding();
    }

    /** True whenever a player is physically mounted inside this action's grip. */
    public boolean hasActiveGrip() {
        return action.hasActiveGrip();
    }

    public HandActionType getActionType() {
        return action.getType();
    }

    public Player getActionTarget() {
        return action.getTargetPlayer();
    }

    public Location getActiveGripWorldPoint() {
        return action.getActiveGripWorldPoint(this);
    }

    public boolean isTransporting(Player player) {
        return action.isTransporting(player);
    }

    public boolean isProtectedCarryTravel(Player player) {
        return action.isProtectedCarryTravel(player);
    }

    public boolean isLongCarryCruise() {
        return action.isLongCarryCruise();
    }

    public double getActionRenderFractionMultiplier() {
        return action.getRenderFractionMultiplier();
    }

    public String getActionDescription() {
        return action.getDescription();
    }

    public String getActionPhaseName() {
        return action.phaseName();
    }

    public Player getHeldPlayer() {
        return action.getHeldPlayer();
    }

    public ParticleHand getSecondaryHand() {
        return action.getSecondaryHand();
    }

    public xyz.dimseal.godHand.model.ModelPoint getGripLocalPoint() {
        return action.getGripLocalPoint(this);
    }

    public Location getGripWorldPoint() {
        return action.getGripWorldPoint(this);
    }

    /** Cleanup transient entities before the hand is removed/replaced. */
    public void dispose() {
        disarmIdle();
        action.dispose(this);
    }

    public void requestRemoval() {
        idleAfterMotion = false;
        disarmIdle();

        if (renderMode != HandRenderMode.ITEM_DISPLAYS) {
            removalRequested = true;
            dismissalActive = false;
            dismissalVisualScale = 1.0;
            return;
        }

        if (dismissalActive || removalRequested) return;
        motion.stopTranslation();
        motion.clearLook();
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        rollVelocity = 0.0;
        dismissalActive = true;
        dismissalTotalTicks = 18;
        dismissalTicksRemaining = dismissalTotalTicks;
        dismissalStart = location.clone();
        dismissalVisualScale = 1.0;
    }

    private void tickDismissal() {
        if (!dismissalActive) return;
        int elapsed = dismissalTotalTicks - dismissalTicksRemaining + 1;
        double t = Math.max(0.0, Math.min(1.0, elapsed / (double) dismissalTotalTicks));
        double smooth = t * t * (3.0 - 2.0 * t);

        // Dismissal is a clean dissolve, not a corkscrew. Keep the
        // root and orientation frozen while the solid model contracts into dust.
        if (dismissalStart != null) {
            location.setX(dismissalStart.getX());
            location.setY(dismissalStart.getY());
            location.setZ(dismissalStart.getZ());
        }
        dismissalVisualScale = Math.max(0.035, 1.0 - smooth * 0.965);

        dismissalTicksRemaining--;
        if (dismissalTicksRemaining <= 0) {
            dismissalActive = false;
            removalRequested = true;
            dismissalVisualScale = 0.035;
        }
    }

    public boolean isDismissing() { return dismissalActive; }

    public double getDismissVisualScale() { return dismissalVisualScale; }

    public boolean isRemovalRequested() {
        return removalRequested;
    }

    private boolean manualOverride(HandControlLayer layer) {
        disarmIdle();
        idleAfterMotion = false;
        if (!action.isActive() || action.allowsManualOverride(layer)) {
            return false;
        }
        return action.cancel(this, "manual " + layer.name().toLowerCase(java.util.Locale.ROOT) + " override", true);
    }

    private void requireSameWorld(Location point, String message) {
        if (point == null || point.getWorld() == null || !point.getWorld().equals(location.getWorld())) {
            throw new IllegalArgumentException(message);
        }
    }

    // Internal calls bypass manual-override arbitration.
    void actionTravelTo(Location target, int durationTicks, EasingCurve easing) {
        motion.startTravel(location, target, durationTicks, easing);
    }

    void actionSteerTo(Location target, double maxSpeed, double acceleration) {
        motion.startActionSteer(location, target, maxSpeed, acceleration);
    }

    void actionChase(Player player, double stopDistance, double maxSpeed, double acceleration) {
        motion.startChase(player, stopDistance, maxSpeed, acceleration);
    }

    void actionFollow(Player player, double distance, double height, double maxSpeed, double acceleration) {
        motion.startFollow(player, distance, height, maxSpeed, acceleration);
    }

    void actionOrbit(Player player, double radius, double degreesPerTick, double height,
                     double maxSpeed, double acceleration) {
        motion.startOrbit(player, location, radius, degreesPerTick, height, maxSpeed, acceleration);
    }

    void actionLookAt(Player player, double turnSpeed) {
        motion.lookAt(player, turnSpeed);
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
    }

    void actionLookAt(Location point, double turnSpeed) {
        motion.lookAt(point, turnSpeed);
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
    }

    void actionPointAt(Player player, double turnSpeed) {
        motion.pointAt(player, turnSpeed);
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        rollVelocity = 0.0;
    }

    void actionPointAt(Location point, double turnSpeed) {
        motion.pointAt(point, turnSpeed);
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        rollVelocity = 0.0;
    }

    void actionDownLookAt(Player player, double turnSpeed) {
        motion.downLookAt(player, turnSpeed);
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        rollVelocity = 0.0;
    }

    void actionDownLookAt(Location point, double turnSpeed) {
        motion.downLookAt(point, turnSpeed);
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        rollVelocity = 0.0;
    }

    /** Action-owned exact orientation setter; bypasses manual override arbitration. */
    void actionSetRotation(double yaw, double pitch, double roll) {
        motion.clearLook();
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        rollVelocity = 0.0;
        this.yaw = normalizeAngle(yaw);
        this.pitch = normalizeAngle(pitch);
        this.roll = normalizeAngle(roll);
    }

    void actionAnimatePose(HandPose pose, int durationTicks, EasingCurve easing) {
        startAnimation(HandPoseLibrary.pose(pose), durationTicks, easing, pose, "action pose " + pose.commandName());
    }

    void actionStopMotion() {
        motion.stopTranslation();
    }

    void actionStopLooking() {
        motion.clearLook();
    }

    void actionSetRollVelocity(double rollVelocity) {
        this.rollVelocity = requireFinite(rollVelocity, "action roll velocity");
    }

    void actionStopRootSpin() {
        this.yawVelocity = 0.0;
        this.pitchVelocity = 0.0;
        this.rollVelocity = 0.0;
    }

    void actionTeleport(Location target) {
        requireSameWorld(target, "Action teleport target must be in the hand's current world.");
        motion.stopTranslation();
        this.location = target.clone();
    }

    boolean actionCancelAnimation() {
        if (!animation.isActive()) {
            return false;
        }
        animation.cancel();
        currentPose = null;
        return true;
    }

    // ---------------------------------------------------------------------
    // immediate controls (preserved as exact/snap operations)
    // ---------------------------------------------------------------------

    public void setFingerCurl(HandDigit digit, double percent) {
        manualOverride(HandControlLayer.ARTICULATION);
        actionCancelAnimation();
        JointSnapshot target = captureJointSnapshot();
        HandPoseLibrary.applyCurl(target, digit, percent);
        applySnapshot(target);
        currentPose = null;
    }

    public void setAllFingerCurl(double percent) {
        manualOverride(HandControlLayer.ARTICULATION);
        actionCancelAnimation();
        JointSnapshot target = captureJointSnapshot();
        for (HandDigit digit : HandDigit.values()) {
            HandPoseLibrary.applyCurl(target, digit, percent);
        }
        applySnapshot(target);
        currentPose = null;
    }

    public void setJointAngle(HandDigit digit, int joint, double degrees) {
        manualOverride(HandControlLayer.ARTICULATION);
        actionCancelAnimation();
        fingers.get(digit).setJoint(joint, degrees);
        currentPose = null;
    }

    public void applyPose(HandPose pose) {
        manualOverride(HandControlLayer.ARTICULATION);
        actionCancelAnimation();
        applySnapshot(HandPoseLibrary.pose(pose));
        currentPose = pose;
    }

    // ---------------------------------------------------------------------
    // animated controls
    // ---------------------------------------------------------------------

    public void animatePose(HandPose pose, int durationTicks, EasingCurve easing) {
        manualOverride(HandControlLayer.ARTICULATION);
        startAnimation(
                HandPoseLibrary.pose(pose),
                durationTicks,
                easing,
                pose,
                "pose " + pose.commandName()
        );
    }

    public void animateFingerCurl(HandDigit digit, double percent, int durationTicks, EasingCurve easing) {
        manualOverride(HandControlLayer.ARTICULATION);
        JointSnapshot target = captureJointSnapshot();
        HandPoseLibrary.applyCurl(target, digit, percent);
        startAnimation(target, durationTicks, easing, null,
                digit.commandName() + " curl " + formatPercent(percent));
    }

    public void animateAllFingerCurl(double percent, int durationTicks, EasingCurve easing) {
        manualOverride(HandControlLayer.ARTICULATION);
        JointSnapshot target = captureJointSnapshot();
        for (HandDigit digit : HandDigit.values()) {
            HandPoseLibrary.applyCurl(target, digit, percent);
        }
        startAnimation(target, durationTicks, easing, null, "all curl " + formatPercent(percent));
    }

    public void animateJointAngle(HandDigit digit, int joint, double degrees, int durationTicks, EasingCurve easing) {
        manualOverride(HandControlLayer.ARTICULATION);
        JointSnapshot target = captureJointSnapshot();
        target.set(digit, joint, degrees);
        startAnimation(target, durationTicks, easing, null,
                digit.commandName() + " joint " + joint + " -> " + degrees + "deg");
    }

    private void startAnimation(
            JointSnapshot target,
            int durationTicks,
            EasingCurve easing,
            HandPose targetPose,
            String description
    ) {
        if (durationTicks < 1 || durationTicks > 20 * 120) {
            throw new IllegalArgumentException("Animation duration must be between 1 tick and 120 seconds.");
        }
        JointSnapshot current = captureJointSnapshot();
        animation.start(current, target, durationTicks, easing, targetPose, description);
        currentPose = null;
    }

    /** Cancel the active transition and hold the exact currently evaluated pose. */
    public boolean cancelAnimation() {
        boolean actionCancelled = manualOverride(HandControlLayer.ARTICULATION);
        return actionCancelled || actionCancelAnimation();
    }

    public boolean isAnimating() {
        return animation.isActive();
    }

    public double getAnimationProgress() {
        return animation.getProgress();
    }

    public int getAnimationElapsedTicks() {
        return animation.getElapsedTicks();
    }

    public int getAnimationDurationTicks() {
        return animation.getDurationTicks();
    }

    public EasingCurve getAnimationEasing() {
        return animation.getEasing();
    }

    public String getAnimationDescription() {
        return animation.getDescription();
    }

    public JointSnapshot captureJointSnapshot() {
        return JointSnapshot.capture(fingers);
    }

    private void applySnapshot(JointSnapshot snapshot) {
        for (HandDigit digit : HandDigit.values()) {
            FingerState state = fingers.get(digit);
            state.setAngles(
                    snapshot.get(digit, 1),
                    snapshot.get(digit, 2),
                    snapshot.get(digit, 3)
            );
        }
    }

    public Vector transformLocalPoint(double localX, double localY, double localZ) {
        return Rotation3D.rotate(
                localX * scale,
                localY * scale,
                localZ * scale,
                yaw,
                pitch,
                roll
        );
    }

    /**
     * Converts a world point back into normalized hand-local coordinates.
     * This is the collision bridge: attack volumes can be authored once
     * in model space and automatically follow root rotation and scale.
     */
    public Vector inverseTransformWorldPoint(Location worldPoint) {
        requireSameWorld(worldPoint, "World point must be in the hand's current world.");
        Vector delta = worldPoint.toVector().subtract(location.toVector());
        Vector localScaled = Rotation3D.inverseRotate(
                delta.getX(), delta.getY(), delta.getZ(),
                yaw, pitch, roll
        );
        return localScaled.multiply(1.0 / scale);
    }

    private void armPostActionIdle() {
        idleArmed = true;
        idleAnchor = location.clone();
        idlePhase = 0.0;

        Vector palmFront = transformLocalPoint(0.0, 0.0, 1.0);
        if (palmFront.lengthSquared() < 1.0e-12) {
            idlePalmDown = false;
        } else {
            palmFront.normalize();
            idlePalmDown = palmFront.dot(new Vector(0.0, -1.0, 0.0)) >= 0.62;
        }

        // Blend every completed action into a natural relaxed hand rather than
        // leaving a perfectly frozen fist/open pose.
        if (!animation.isActive()) {
            actionAnimatePose(HandPose.RELAXED, 18, EasingCurve.SMOOTH);
        }
    }

    /** Explicitly enter the living idle state used after summon and persistent action completion. */
    public void startIdle() {
        if (removalRequested || action.isActive()) return;
        armPostActionIdle();
    }

    private void disarmIdle() {
        idleArmed = false;
        idleAnchor = null;
        idlePhase = 0.0;
    }

    private void tickPostActionIdle() {
        if (idleAnchor == null || idleAnchor.getWorld() == null || !idleAnchor.getWorld().equals(location.getWorld())) {
            idleAnchor = location.clone();
        }

        // If a developer starts manual travel without an action, don't fight it.
        if (motion.isTranslationActive()) {
            idleAnchor = location.clone();
            return;
        }

        idlePhase += idlePalmDown ? 0.072 : 0.064;
        double hoverAmplitude = Math.min(idlePalmDown ? 0.46 : 0.38, Math.max(0.15, scale * 0.082));
        double lateralAmplitude = Math.min(0.18, scale * 0.026);
        location.setY(idleAnchor.getY() + Math.sin(idlePhase) * hoverAmplitude);
        location.setX(idleAnchor.getX() + Math.sin(idlePhase * 0.63) * lateralAmplitude);
        location.setZ(idleAnchor.getZ() + Math.cos(idlePhase * 0.57) * lateralAmplitude);

        if (animation.isActive()) return;

        // Every phalanx has its own compound cadence. The MCP,
        // PIP and DIP joints use different frequencies and amplitudes, producing
        // visible tendon-like flexion instead of a barely moving rigid silhouette.
        JointSnapshot relaxed = HandPoseLibrary.pose(HandPose.RELAXED);
        for (HandDigit digit : HandDigit.values()) {
            double d = idlePhase + digit.ordinal() * 0.91;
            double thumb = digit == HandDigit.THUMB ? 0.86 : 1.0;
            double down = idlePalmDown ? 1.24 : 1.0;
            // Every phalanx gets a visibly different cadence. A slow whole-finger
            // tendon wave is layered with faster PIP/DIP articulation so the hand
            // reads as alive even at large scales and from a distance.
            double tendon = Math.sin(d * 0.66) + 0.34 * Math.sin(d * 1.47 + digit.ordinal() * 0.27);
            double middle = Math.sin(d * 1.04 + 0.72) + 0.42 * Math.cos(d * 1.93 + digit.ordinal() * 0.31);
            double distal = Math.sin(d * 1.38 + 1.26) + 0.36 * Math.sin(d * 2.41 + 0.45);
            double pulse = 0.5 + 0.5 * Math.sin(idlePhase * 0.31 + digit.ordinal() * 1.17);
            FingerState state = fingers.get(digit);
            state.setAngles(
                    relaxed.get(digit, 1) + tendon * (8.0 + 3.0 * pulse) * thumb * down,
                    relaxed.get(digit, 2) + middle * (12.0 + 4.5 * pulse) * thumb * down,
                    relaxed.get(digit, 3) + distal * (15.0 + 5.5 * pulse) * thumb * down
            );
        }
        currentPose = null;
    }

    private static double normalizeAngle(double angle) {
        if (!Double.isFinite(angle)) {
            throw new IllegalArgumentException("Angle must be finite.");
        }

        angle %= 360.0;
        if (angle >= 180.0) {
            angle -= 360.0;
        } else if (angle < -180.0) {
            angle += 360.0;
        }
        return angle;
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite.");
        }
        return value;
    }

    private static String formatPercent(double percent) {
        return String.format(java.util.Locale.US, "%.1f%%", percent);
    }
}
