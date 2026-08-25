package xyz.dimseal.godHand.hand;

import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Wither;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import xyz.dimseal.godHand.hand.animation.EasingCurve;
import xyz.dimseal.godHand.hand.render.HandPalette;
import xyz.dimseal.godHand.hand.skeleton.HandDigit;
import xyz.dimseal.godHand.hand.skeleton.HandPose;
import xyz.dimseal.godHand.model.ModelPoint;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Action/state sequencer for the articulated Hand.
 *
 * extends the action sequencer with post-grab MOVE_TO travel,
 * a high-speed CYCLONE corkscrew attack, an interior/bunker BREACH presence,
 * optimized long carry travel, and one-shot SLAP cleanup.
 */
public final class HandActionController {

    private enum Phase {
        IDLE,
        SLAM_RISE,
        SLAM_WINDUP,
        SLAM_DESCEND,
        SLAM_IMPACT_HOLD,
        SLAM_RECOIL,
        GRAB_RISE,
        GRAB_DESCEND,
        GRAB_CLOSE,
        HOLDING,
        TRANSPORT_TRAVEL,
        MOVE_TO_TRAVEL,
        TRANSPORT_RELEASE,
        RELEASING,
        THROW_OPEN,
        THROW_SWING_BACK,
        THROW_SWING_FORWARD,
        JUDGMENT_APPROACH,
        JUDGMENT_WINDUP,
        JUDGMENT_STRIKE,
        FORCE_SLAP_APPROACH,
        FORCE_SLAP_WINDUP,
        FORCE_SLAP_STRIKE,
        FORCE_SLAP_RECOVER,
        PUNCH_APPROACH,
        PUNCH_WINDUP,
        PUNCH_STRIKE,
        PUNCH_RECOVER,
        SLAP_APPROACH,
        SLAP_WINDUP,
        SLAP_STRIKE,
        SLAP_RECOVER,
        CYCLONE_APPROACH,
        CYCLONE_WINDUP,
        CYCLONE_STRIKE,
        CYCLONE_RECOVER,
        BREACH_HUNT,
        TOSS_ASCEND,
        TOSS_THROW,
        BLESS_HOLD,
        BLESS_DEPART,
        SANCTUARY_APPROACH,
        SANCTUARY_GUARD,
        SANCTUARY_DEPART,
        SPANK_ASCEND,
        SPANK_SETUP,
        SPANK_BACKSWING,
        SPANK_STRIKE,
        SPANK_PAUSE,
        SPANK_FINAL_WINDUP,
        SPANK_FINAL_STRIKE,
        SPANK_RECOVER,
        RAGE_APPROACH,
        RAGE_DASH_WINDUP,
        RAGE_DASH_STRIKE,
        RAGE_GRAB_APPROACH,
        RAGE_GRAB_CLOSE,
        RAGE_ASCEND,
        RAGE_THROW_BACK,
        RAGE_THROW_FORWARD,
        RAGE_RECOVER,
        CLAP_APPROACH,
        CLAP_WINDUP,
        CLAP_STRIKE,
        CLAP_DISMISS,
        POUND_APPROACH,
        POUND_WINDUP,
        POUND_STRIKE,
        POUND_RECOVER,
        WAVE_APPROACH,
        WAVE_ANIMATE,
        WAVE_RECOVER,
        THUMB_APPROACH,
        THUMB_HOLD,
        THUMB_RECOVER,
        GIVE_BIRD_APPROACH,
        GIVE_BIRD_WAIT,
        GIVE_BIRD_LIGHTNING,
        GIVE_BIRD_RECOVER,
        JUGGLE_ASCEND,
        JUGGLE_SETUP,
        JUGGLE_HOLD_PRIMARY,
        JUGGLE_WINDUP_PRIMARY,
        JUGGLE_FLIGHT_TO_SECONDARY,
        JUGGLE_HOLD_SECONDARY,
        JUGGLE_WINDUP_SECONDARY,
        JUGGLE_FLIGHT_TO_PRIMARY,
        GUARD_ORBIT,
        GUARD_ATTACK_APPROACH,
        GUARD_ATTACK_STRIKE,
        GUARD_COOLDOWN,
        SMASH_APPROACH,
        SMASH_WINDUP,
        SMASH_STRIKE,
        SMASH_RECOVER,
        STALK_WATCH,
        STALK_DISTANT_POINT,
        STALK_ORBIT,
        STALK_FOLLOW,
        STALK_CHASE,
        STALK_REACQUIRE,
        HOVER_CHASE
    }

    private HandActionType type = HandActionType.IDLE;
    private Phase phase = Phase.IDLE;
    private Player target;
    private int phaseTicksRemaining;

    private double actionHeight;
    private int approachTicks;
    private int secondaryTicks;
    private int recoilTicks;
    private Location lockedImpact;

    // Judgment is a surface-only index-fingertip beam. The old
    // charge-through behavior is preserved as FORCE_SLAP and reuses the
    // directional fields below.
    private double judgmentOrbitAngle;
    private int judgmentBeamTick;
    private boolean judgmentImpactPlayed;
    private Vector judgmentDirection;
    private Location judgmentPreviousOrigin;

    private Vector tossThrowDirection;
    private Location tossDestination;

    // shared visible throw swing used by manual /gh action throw and Toss.
    private Vector throwSwingDirection;
    private Location throwSwingAnchor;
    private double throwSwingForwardSpeed;
    private double throwSwingUpwardSpeed;
    private int throwSwingOpenTicks;
    private HandActionType throwSwingSource;

    // surface-only middle-finger lightning action.
    private int giveBirdLightningTick;
    private boolean giveBirdAttackMode;

    // wide two-Hand endless juggle.
    private Location jugglePrimaryAnchor;
    private Location juggleSecondaryAnchor;
    private boolean jugglePrimaryHolding;
    private int juggleCycle;
    private Location juggleWindupDestination;
    private int juggleFlightTick;
    private int juggleFlightDuration;
    private int juggleSenderRecoverDelay;

    // wolf-like guard companion.
    private Player guardOwner;
    private LivingEntity guardEnemy;
    private int guardAttackIndex;
    private int guardAttackTick;
    private double guardPreviousScale;
    private Color guardPreviousColor;

    private int blessingStage;
    private Location blessingDeparture;

    private Location sanctuaryDeparture;
    private int sanctuaryPulseTick;

    // seven-hit dual-Hand sequence. The secondary Hand is a complete
    // articulated visual instance rendered by HandManager alongside the main one.
    private ParticleHand secondaryHand;
    private Location spankHoldDestination;
    private int spankCount;
    private Vector spankLaunchDirection;

    // Rage combo: three fire-trail dash contacts totaling at most
    // 15 damage, then a physical grab/lift and articulated finger-direction throw.
    private int rageDashIndex;
    private Location ragePreviousOrigin;
    private Vector rageDashDirection;
    private Location rageLiftDestination;
    private Vector rageThrowDirection;
    private Location rageSwingAnchor;

    // two-Hand thunder clap.
    private Vector clapRight;
    private Location clapLockedTorso;
    private boolean clapImpactPlayed;

    // alternating two-Hand fist pound.
    private boolean poundPrimaryTurn;
    private int poundHitCount;
    private Location poundPrimaryRest;
    private Location poundSecondaryRest;
    private boolean poundEnding;
    private int poundWindupStep;

    // non-combat gestures.
    private int gestureTick;
    private Location gestureAnchor;
    private boolean thumbGestureUp;

    private Vector punchDirection;
    private Location punchPreviousOrigin;
    private boolean punchImpactPlayed;
    private double punchDamage;
    private double punchMinimumHealthLoss;
    private double punchHorizontalKnockback;
    private double punchVerticalKnockback;

    private Vector slapDirection;
    private Location slapPreviousOrigin;
    private boolean slapImpactPlayed;
    private double slapDamage;
    private double slapHorizontalKnockback;
    private double slapVerticalKnockback;

    private Location slamPreviousOrigin;

    private Player transportDestinationPlayer;
    private Location transportDestinationPoint;
    private boolean transportFinalApproach;
    private double transportDistanceRemaining;
    private int lastPrefetchChunkX = Integer.MIN_VALUE;
    private int lastPrefetchChunkZ = Integer.MIN_VALUE;

    private Vector cycloneDirection;
    private Location cyclonePreviousOrigin;
    private boolean cycloneImpactPlayed;

    private int breachPulseTick;
    private double adaptiveBaseScale;
    private boolean adaptiveScaleManaged;
    private int adaptiveScaleTick;

    private Player smashTargetPlayer;
    private Location smashTargetPoint;
    private float smashExplosionPower;

    private final Set<UUID> actionHitPlayers = new HashSet<>();

    private String lastStopReason = "idle";
    private int lastImpactHitCount;

    private final HandGripSolver gripSolver = new HandGripSolver();
    private final HandGripCarrier gripCarrier = new HandGripCarrier();
    private final HandCombatResolver combatResolver = new HandCombatResolver();

    public void tick(ParticleHand hand) {
        if (secondaryHand != null) {
            secondaryHand.tick();
        }

        if (type == HandActionType.IDLE) {
            return;
        }

        if (target != null && phaseRequiresLiveTarget()) {
            if (!target.isOnline() || target.isDead()) {
                cancel(hand, "target unavailable", true);
                return;
            }
            if (target.getWorld() == null || !target.getWorld().equals(hand.getWorld())) {
                cancel(hand, "target changed worlds", true);
                return;
            }
        }

        switch (phase) {
            case SLAM_RISE -> tickSlamRise(hand);
            case SLAM_WINDUP -> tickSlamWindup(hand);
            case SLAM_DESCEND -> tickSlamDescend(hand);
            case SLAM_IMPACT_HOLD -> tickSlamImpactHold(hand);
            case SLAM_RECOIL -> tickSlamRecoil(hand);
            case GRAB_RISE -> tickGrabRise(hand);
            case GRAB_DESCEND -> tickGrabDescend(hand);
            case GRAB_CLOSE -> tickGrabClose(hand);
            case HOLDING -> tickHolding(hand);
            case TRANSPORT_TRAVEL -> tickTransportTravel(hand);
            case MOVE_TO_TRAVEL -> tickMoveToTravel(hand);
            case TRANSPORT_RELEASE -> tickTransportRelease(hand);
            case RELEASING -> tickReleasing(hand);
            case THROW_OPEN -> tickThrowOpen(hand);
            case THROW_SWING_BACK -> tickThrowSwingBack(hand);
            case THROW_SWING_FORWARD -> tickThrowSwingForward(hand);
            case JUDGMENT_APPROACH -> tickJudgmentApproach(hand);
            case JUDGMENT_WINDUP -> tickJudgmentWindup(hand);
            case JUDGMENT_STRIKE -> tickJudgmentStrike(hand);
            case FORCE_SLAP_APPROACH -> tickForceSlapApproach(hand);
            case FORCE_SLAP_WINDUP -> tickForceSlapWindup(hand);
            case FORCE_SLAP_STRIKE -> tickForceSlapStrike(hand);
            case FORCE_SLAP_RECOVER -> tickForceSlapRecover(hand);
            case PUNCH_APPROACH -> tickPunchApproach(hand);
            case PUNCH_WINDUP -> tickPunchWindup(hand);
            case PUNCH_STRIKE -> tickPunchStrike(hand);
            case PUNCH_RECOVER -> tickPunchRecover(hand);
            case SLAP_APPROACH -> tickSlapApproach(hand);
            case SLAP_WINDUP -> tickSlapWindup(hand);
            case SLAP_STRIKE -> tickSlapStrike(hand);
            case SLAP_RECOVER -> tickSlapRecover(hand);
            case CYCLONE_APPROACH -> tickCycloneApproach(hand);
            case CYCLONE_WINDUP -> tickCycloneWindup(hand);
            case CYCLONE_STRIKE -> tickCycloneStrike(hand);
            case CYCLONE_RECOVER -> tickCycloneRecover(hand);
            case BREACH_HUNT -> tickBreachHunt(hand);
            case TOSS_ASCEND -> tickTossAscend(hand);
            case TOSS_THROW -> tickTossThrow(hand);
            case BLESS_HOLD -> tickBlessHold(hand);
            case BLESS_DEPART -> tickBlessDepart(hand);
            case SANCTUARY_APPROACH -> tickSanctuaryApproach(hand);
            case SANCTUARY_GUARD -> tickSanctuaryGuard(hand);
            case SANCTUARY_DEPART -> tickSanctuaryDepart(hand);
            case SPANK_ASCEND -> tickSpankAscend(hand);
            case SPANK_SETUP -> tickSpankSetup(hand);
            case SPANK_BACKSWING -> tickSpankBackswing(hand);
            case SPANK_STRIKE -> tickSpankStrike(hand);
            case SPANK_PAUSE -> tickSpankPause(hand);
            case SPANK_FINAL_WINDUP -> tickSpankFinalWindup(hand);
            case SPANK_FINAL_STRIKE -> tickSpankFinalStrike(hand);
            case SPANK_RECOVER -> tickSpankRecover(hand);
            case RAGE_APPROACH -> tickRageApproach(hand);
            case RAGE_DASH_WINDUP -> tickRageDashWindup(hand);
            case RAGE_DASH_STRIKE -> tickRageDashStrike(hand);
            case RAGE_GRAB_APPROACH -> tickRageGrabApproach(hand);
            case RAGE_GRAB_CLOSE -> tickRageGrabClose(hand);
            case RAGE_ASCEND -> tickRageAscend(hand);
            case RAGE_THROW_BACK -> tickRageThrowBack(hand);
            case RAGE_THROW_FORWARD -> tickRageThrowForward(hand);
            case RAGE_RECOVER -> tickRageRecover(hand);
            case CLAP_APPROACH -> tickClapApproach(hand);
            case CLAP_WINDUP -> tickClapWindup(hand);
            case CLAP_STRIKE -> tickClapStrike(hand);
            case CLAP_DISMISS -> tickClapDismiss(hand);
            case POUND_APPROACH -> tickPoundApproach(hand);
            case POUND_WINDUP -> tickPoundWindup(hand);
            case POUND_STRIKE -> tickPoundStrike(hand);
            case POUND_RECOVER -> tickPoundRecover(hand);
            case WAVE_APPROACH -> tickWaveApproach(hand);
            case WAVE_ANIMATE -> tickWaveAnimate(hand);
            case WAVE_RECOVER -> tickWaveRecover(hand);
            case THUMB_APPROACH -> tickThumbApproach(hand);
            case THUMB_HOLD -> tickThumbHold(hand);
            case THUMB_RECOVER -> tickThumbRecover(hand);
            case GIVE_BIRD_APPROACH -> tickGiveBirdApproach(hand);
            case GIVE_BIRD_WAIT -> tickGiveBirdWait(hand);
            case GIVE_BIRD_LIGHTNING -> tickGiveBirdLightning(hand);
            case GIVE_BIRD_RECOVER -> tickGiveBirdRecover(hand);
            case JUGGLE_ASCEND -> tickJuggleAscend(hand);
            case JUGGLE_SETUP -> tickJuggleSetup(hand);
            case JUGGLE_HOLD_PRIMARY -> tickJuggleHoldPrimary(hand);
            case JUGGLE_WINDUP_PRIMARY -> tickJuggleWindup(hand, true);
            case JUGGLE_FLIGHT_TO_SECONDARY -> tickJuggleFlight(hand, false);
            case JUGGLE_HOLD_SECONDARY -> tickJuggleHoldSecondary(hand);
            case JUGGLE_WINDUP_SECONDARY -> tickJuggleWindup(hand, false);
            case JUGGLE_FLIGHT_TO_PRIMARY -> tickJuggleFlight(hand, true);
            case GUARD_ORBIT -> tickGuardOrbit(hand);
            case GUARD_ATTACK_APPROACH -> tickGuardAttackApproach(hand);
            case GUARD_ATTACK_STRIKE -> tickGuardAttackStrike(hand);
            case GUARD_COOLDOWN -> tickGuardCooldown(hand);
            case SMASH_APPROACH -> tickSmashApproach(hand);
            case SMASH_WINDUP -> tickSmashWindup(hand);
            case SMASH_STRIKE -> tickSmashStrike(hand);
            case SMASH_RECOVER -> tickSmashRecover(hand);
            case STALK_WATCH -> tickStalkWatch(hand);
            case STALK_DISTANT_POINT -> tickStalkDistantPoint(hand);
            case STALK_ORBIT, STALK_FOLLOW -> tickStalkMotion(hand);
            case STALK_CHASE -> tickStalkChase(hand);
            case STALK_REACQUIRE -> tickStalkReacquire(hand);
            case HOVER_CHASE -> tickHoverChase(hand);
            case IDLE -> finish("idle");
        }
    }

    // ---------------------------------------------------------------------
    // Starts
    // ---------------------------------------------------------------------

    public void startSlam(ParticleHand hand, Player target, double height, int riseTicks, int dropTicks) {
        startSlam(hand, target, height, riseTicks, dropTicks, EasingCurve.EASE_IN_OUT);
    }

    public void startSlam(ParticleHand hand, Player target, double height, int riseTicks, int dropTicks, EasingCurve approachEasing) {
        validateTarget(hand, target);
        validateHeight(height);
        validateTicks(riseTicks, "slam rise");
        validateTicks(dropTicks, "slam drop");
        cancel(hand, "replaced by slam", true);
        combatResolver.setDeathStyle(HandDeathMessages.Style.SLAM);

        this.type = HandActionType.SLAM;
        this.phase = Phase.SLAM_RISE;
        this.target = target;
        this.actionHeight = height;
        this.approachTicks = riseTicks;
        this.secondaryTicks = dropTicks;
        this.recoilTicks = Math.max(10, Math.min(40, riseTicks));
        this.lockedImpact = null;
        this.slamPreviousOrigin = null;
        this.actionHitPlayers.clear();
        this.lastImpactHitCount = 0;
        this.lastStopReason = "slam active";

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.OPEN, Math.max(4, Math.min(14, riseTicks)), EasingCurve.EASE_OUT);
        hand.actionDownLookAt(target, 14.0);
        hand.actionTravelTo(stagingAbove(target, height), riseTicks, approachEasing == null ? EasingCurve.EASE_IN_OUT : approachEasing);
        TrueGodEffects.slamStart(target);
    }

    public void startGrab(ParticleHand hand, Player target, double height, int approachTicks, int closeTicks) {
        startGrabLike(hand, target, height, approachTicks, closeTicks, HandActionType.GRAB, null, null);
    }

    public void startTransport(ParticleHand hand, Player target, Player destination, double height, int approachTicks, int closeTicks) {
        validateTarget(hand, target);
        validateTarget(hand, destination);
        if (target.getUniqueId().equals(destination.getUniqueId())) {
            throw new IllegalArgumentException("Transport destination player must be different from the carried player.");
        }
        startGrabLike(hand, target, height, approachTicks, closeTicks, HandActionType.TRANSPORT, destination, null);
    }

    public void startTransport(ParticleHand hand, Player target, Location destination, double height, int approachTicks, int closeTicks) {
        validateTarget(hand, target);
        validateLocation(hand, destination, "Transport destination must be in the hand's current world.");
        startGrabLike(hand, target, height, approachTicks, closeTicks, HandActionType.TRANSPORT, null, destination);
    }

    /** grab -> ascend above surface canopy -> violent long toss. */
    public void startToss(ParticleHand hand, Player target, double height, int approachTicks, int closeTicks) {
        validateTarget(hand, target);
        Vector look = target.getEyeLocation().getDirection();
        look.setY(0.0);
        if (look.lengthSquared() < 1.0e-8) look = new Vector(0.0, 0.0, 1.0);
        Vector throwDirection = look.normalize();
        startGrabLike(hand, target, height, approachTicks, closeTicks, HandActionType.TOSS, null, null);
        tossThrowDirection = throwDirection;
        TrueGodEffects.tossStart(target);
    }

    /** benevolent grab: hold for five seconds, grant one five-minute boon per second, then depart. */
    public void startBless(ParticleHand hand, Player target, double height, int approachTicks, int closeTicks) {
        validateTarget(hand, target);
        // Bless is a canonical benevolent manifestation: emerald regardless of
        // the configured combat/presence palette. The Hand dismisses after the
        // ritual, so the user's persistent MainHandSettings remain untouched.
        hand.setBaseColor(HandPalette.EMERALD);
        startGrabLike(hand, target, height, approachTicks, closeTicks, HandActionType.BLESS, null, null);
        blessingStage = 0;
        blessingDeparture = null;
        TrueGodEffects.blessStart(target);
    }

    /** benevolent shielding/healing presence, distinct from Bless. */
    public void startSanctuary(ParticleHand hand, Player target, int guardTicks) {
        validateTarget(hand, target);
        validateTicks(guardTicks, "sanctuary guard");
        cancel(hand, "replaced by sanctuary", true);

        type = HandActionType.SANCTUARY;
        phase = Phase.SANCTUARY_APPROACH;
        this.target = target;
        this.phaseTicksRemaining = guardTicks;
        this.sanctuaryPulseTick = 0;
        this.sanctuaryDeparture = null;
        lastStopReason = "sanctuary approaching";

        // Gold is a temporary benevolent manifestation just like Bless's emerald.
        hand.setBaseColor(HandPalette.GOLD);
        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionStopRootSpin();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.OPEN, 18, EasingCurve.EASE_IN_OUT);
        hand.actionDownLookAt(target, 16.0);
        Location shield = target.getEyeLocation().clone().add(0.0, Math.max(4.5, hand.getScale() * 1.20), 0.0);
        hand.actionTravelTo(shield, 30, EasingCurve.EASE_IN_OUT);
        TrueGodEffects.sanctuaryStart(target);
    }

    /** grab/lift + temporary second Hand + seven physical slap beats. */
    public void startSpank(ParticleHand hand, Player target, double height, int approachTicks, int closeTicks) {
        validateTarget(hand, target);
        Vector launch = target.getEyeLocation().getDirection();
        launch.setY(0.0);
        if (launch.lengthSquared() < 1.0e-8) launch = new Vector(0.0, 0.0, 1.0);
        launch.normalize();
        // startGrabLike performs the standard cancellation/reset first. Store the
        // sequence-specific state afterwards so it survives that reset.
        startGrabLike(hand, target, height, approachTicks, closeTicks, HandActionType.SPANK, null, null);
        spankLaunchDirection = launch;
        spankCount = 0;
        spankHoldDestination = null;
        TrueGodEffects.spankStart(target);
    }

    /** rage combo: three fire-trail dash hits (3 + 5 + 7 max), then grab/lift/throw. */
    public void startRage(ParticleHand hand, Player target) {
        validateTarget(hand, target);
        cancel(hand, "replaced by rage", true);
        combatResolver.setDeathStyle(HandDeathMessages.Style.RAGE);

        this.type = HandActionType.RAGE;
        this.phase = Phase.RAGE_APPROACH;
        this.target = target;
        this.rageDashIndex = 0;
        this.ragePreviousOrigin = null;
        this.rageDashDirection = null;
        this.rageLiftDestination = null;
        this.rageSwingAnchor = null;
        this.rageThrowDirection = horizontalFacing(target);
        this.actionHitPlayers.clear();
        this.lastImpactHitCount = 0;
        this.lastStopReason = "rage staging";

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionStopRootSpin();
        hand.actionCancelAnimation();
        prepareRageDashPose(hand, 0);
        hand.actionLookAt(target, 24.0);
        hand.actionTravelTo(rageWindupPoint(target, 0), 18, EasingCurve.EASE_IN_OUT);
        TrueGodEffects.rageStart(target);
    }

    /** dual-Hand thunder clap. Both open palms converge horizontally around the target. */
    public void startClap(ParticleHand hand, Player target) {
        validateTarget(hand, target);
        cancel(hand, "replaced by clap", true);

        this.type = HandActionType.CLAP;
        this.phase = Phase.CLAP_APPROACH;
        this.target = target;
        this.clapRight = targetRight(target);
        this.clapLockedTorso = null;
        this.clapImpactPlayed = false;
        this.actionHitPlayers.clear();
        this.lastImpactHitCount = 0;
        this.lastStopReason = "clap staging";

        Location torso = upperTorso(target);
        Location primaryStage = torso.clone().add(clapRight.clone().multiply(7.8));
        Location secondaryStage = torso.clone().subtract(clapRight.clone().multiply(7.8));

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionStopRootSpin();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.OPEN, 14, EasingCurve.EASE_IN_OUT);
        orientClapHorizontal(hand, torso, horizontalFacing(target));
        hand.actionTravelTo(primaryStage, 24, EasingCurve.EASE_IN_OUT);

        secondaryHand = cloneVisualHand(hand, secondaryStage);
        secondaryHand.applyPose(HandPose.OPEN);
        orientClapHorizontal(secondaryHand, torso, horizontalFacing(target));
        secondaryHand.startIdle();
        secondaryHand.travelTo(secondaryStage, 1, EasingCurve.LINEAR);
        phaseTicksRemaining = 28;
        TrueGodEffects.clapStart(target);
    }

    /** alternating two-Hand fist pounds until the victim reaches three hearts or less. */
    public void startPound(ParticleHand hand, Player target) {
        validateTarget(hand, target);
        cancel(hand, "replaced by pound", true);

        type = HandActionType.POUND;
        phase = Phase.POUND_APPROACH;
        this.target = target;
        poundPrimaryTurn = true;
        poundHitCount = 0;
        poundEnding = false;
        poundWindupStep = 0;
        lastImpactHitCount = 0;
        lastStopReason = "pound staging";

        Location torso = upperTorso(target);
        Vector right = targetRight(target);
        poundPrimaryRest = torso.clone().add(right.clone().multiply(3.8)).add(0.0, 6.2, 0.0);
        poundSecondaryRest = torso.clone().subtract(right.clone().multiply(3.8)).add(0.0, 6.2, 0.0);

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionStopRootSpin();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.FIST, 4, EasingCurve.EASE_IN_OUT);
        orientPoundFist(hand);
        hand.actionTravelTo(poundPrimaryRest, 6, EasingCurve.EASE_IN_OUT);

        secondaryHand = cloneVisualHand(hand, poundSecondaryRest);
        secondaryHand.applyPose(HandPose.FIST);
        orientPoundFist(secondaryHand);
        secondaryHand.travelTo(poundSecondaryRest, 1, EasingCurve.LINEAR);
        phaseTicksRemaining = 6;
        TrueGodEffects.poundStart(target);
    }

    /** friendly open-palm wave in the air in front of the target. */
    public void startWave(ParticleHand hand, Player target) {
        validateTarget(hand, target);
        cancel(hand, "replaced by wave", true);
        type = HandActionType.WAVE;
        phase = Phase.WAVE_APPROACH;
        this.target = target;
        gestureTick = 0;
        gestureAnchor = gestureFrontPoint(target, hand.getScale(), 0.85);
        lastStopReason = "wave staging";

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionStopRootSpin();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.OPEN, 12, EasingCurve.EASE_IN_OUT);
        orientWave(hand, 0.0);
        hand.actionTravelTo(gestureAnchor, 20, EasingCurve.EASE_IN_OUT);
        TrueGodEffects.waveStart(target);
    }

    /** thumbs-up / thumbs-down pose held in front of the target. */
    public void startThumb(ParticleHand hand, Player target, boolean up) {
        validateTarget(hand, target);
        cancel(hand, "replaced by thumbs " + (up ? "up" : "down"), true);
        type = up ? HandActionType.THUMBS_UP : HandActionType.THUMBS_DOWN;
        phase = Phase.THUMB_APPROACH;
        this.target = target;
        thumbGestureUp = up;
        gestureTick = 0;
        gestureAnchor = gestureFrontPoint(target, hand.getScale(), up ? 0.50 : 1.35);
        lastStopReason = up ? "thumbs-up staging" : "thumbs-down staging";

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionStopRootSpin();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(up ? HandPose.THUMBS_UP : HandPose.THUMBS_DOWN, 14, EasingCurve.EASE_IN_OUT);
        orientThumb(hand, up);
        hand.actionTravelTo(gestureAnchor, 20, EasingCurve.EASE_IN_OUT);
        TrueGodEffects.thumbStart(target, up);
    }

    /**
     * retained-grab travel. Unlike TRANSPORT this requires an existing
     * HOLDING state and keeps the passenger mounted when the destination is reached.
     */
    public void startMoveTo(ParticleHand hand, Player destination) {
        if (!isHolding() || !gripCarrier.isActive() || target == null) {
            throw new IllegalArgumentException("MOVE_TO requires the Hand to already be holding a player.");
        }
        validateTarget(hand, destination);
        transportDestinationPlayer = destination;
        transportDestinationPoint = null;
        transportFinalApproach = false;
        transportDistanceRemaining = Double.POSITIVE_INFINITY;
        type = HandActionType.MOVE_TO;
        phase = Phase.MOVE_TO_TRAVEL;
        lastStopReason = "moveto carrying";
        tickMoveToTravel(hand);
    }

    public void startMoveTo(ParticleHand hand, Location destination) {
        if (!isHolding() || !gripCarrier.isActive() || target == null) {
            throw new IllegalArgumentException("MOVE_TO requires the Hand to already be holding a player.");
        }
        validateLocation(hand, destination, "MOVE_TO destination must be in the hand's current world.");
        transportDestinationPlayer = null;
        transportDestinationPoint = destination.clone();
        transportFinalApproach = false;
        transportDistanceRemaining = Double.POSITIVE_INFINITY;
        type = HandActionType.MOVE_TO;
        phase = Phase.MOVE_TO_TRAVEL;
        lastStopReason = "moveto carrying";
        tickMoveToTravel(hand);
    }


    /** Displays the middle-finger pose without damage, lightning, or target chat. */
    public void startBird(ParticleHand hand, Player target) {
        validateTarget(hand, target);
        cancel(hand, "replaced by bird gesture", true);
        type = HandActionType.BIRD;
        phase = Phase.GIVE_BIRD_APPROACH;
        this.target = target;
        giveBirdAttackMode = false;
        giveBirdLightningTick = 0;
        lastStopReason = "bird gesture staging";

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionStopRootSpin();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.BIRD, 14, EasingCurve.EASE_IN_OUT);
        hand.actionTravelTo(giveBirdFrontPoint(target, hand.getScale()), 18, EasingCurve.EASE_IN_OUT);
        orientGiveBird(hand);
    }

    public void startGiveBird(ParticleHand hand, Player target) {
        validateTarget(hand, target);
        if (!isSurfacePlayer(target)) {
            throw new IllegalArgumentException("GiveBird requires the target to be visible on the surface.");
        }
        cancel(hand, "replaced by givebird", true);

        type = HandActionType.GIVE_BIRD;
        phase = Phase.GIVE_BIRD_APPROACH;
        this.target = target;
        giveBirdAttackMode = true;
        giveBirdLightningTick = 0;
        lastStopReason = "givebird staging";

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionStopRootSpin();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.BIRD, 18, EasingCurve.EASE_IN_OUT);
        Location stage = giveBirdFrontPoint(target, hand.getScale());
        hand.actionTravelTo(stage, 24, EasingCurve.EASE_IN_OUT);
        orientGiveBird(hand);
    }

    /** endless wide aerial two-Hand juggle. */
    public void startJuggle(ParticleHand hand, Player target, double height, int approachTicks, int closeTicks) {
        validateTarget(hand, target);
        startGrabLike(hand, target, height, approachTicks, closeTicks, HandActionType.JUGGLE, null, null);
        jugglePrimaryAnchor = null;
        juggleSecondaryAnchor = null;
        jugglePrimaryHolding = true;
        juggleCycle = 0;
        juggleWindupDestination = null;
        juggleFlightTick = 0;
        juggleFlightDuration = 0;
        juggleSenderRecoverDelay = 0;
    }

    /** scale-1 emerald wolf-like guardian. */
    public void startGuard(ParticleHand hand, Player owner) {
        validateTarget(hand, owner);
        cancel(hand, "replaced by guard", true);

        type = HandActionType.GUARD;
        phase = Phase.GUARD_ORBIT;
        target = owner;
        guardOwner = owner;
        guardEnemy = null;
        guardAttackIndex = 0;
        guardAttackTick = 0;
        guardPreviousScale = 0.0;
        guardPreviousColor = null;
        guardPreviousScale = hand.getScale();
        guardPreviousColor = hand.getBaseColor();
        lastStopReason = "guard orbiting " + owner.getName();

        hand.setScale(1.0);
        hand.setBaseColor(HandPalette.EMERALD);
        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionStopRootSpin();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.RELAXED, 12, EasingCurve.EASE_IN_OUT);
        hand.actionOrbit(owner, 2.8, 4.2, 1.7, 0.62, 0.075);
        hand.actionLookAt(owner, 14.0);
        TrueGodEffects.guardStart(owner);
    }

    private void startGrabLike(
            ParticleHand hand,
            Player target,
            double height,
            int approachTicks,
            int closeTicks,
            HandActionType requestedType,
            Player destinationPlayer,
            Location destinationPoint
    ) {
        validateTarget(hand, target);
        validateHeight(height);
        validateTicks(approachTicks, "grab approach");
        validateTicks(closeTicks, "grab close");
        cancel(hand, "replaced by " + requestedType.commandName(), true);

        this.type = requestedType;
        this.phase = Phase.GRAB_RISE;
        this.target = target;
        this.actionHeight = height;
        this.approachTicks = approachTicks;
        this.secondaryTicks = closeTicks;
        this.transportDestinationPlayer = destinationPlayer;
        this.transportDestinationPoint = destinationPoint == null ? null : destinationPoint.clone();
        this.transportFinalApproach = false;
        this.transportDistanceRemaining = Double.POSITIVE_INFINITY;
        this.lockedImpact = null;
        this.lastStopReason = requestedType == HandActionType.TRANSPORT ? "transport acquiring target"
                : (requestedType == HandActionType.TOSS ? "toss acquiring target"
                : (requestedType == HandActionType.BLESS ? "bless acquiring target"
                : (requestedType == HandActionType.SPANK ? "spank acquiring target"
                : (requestedType == HandActionType.JUGGLE ? "juggle acquiring target" : "grab active"))));

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.OPEN, Math.max(4, Math.min(14, approachTicks)), EasingCurve.EASE_OUT);
        hand.actionDownLookAt(target, 14.0);
        hand.actionTravelTo(stagingAbove(target, height), approachTicks, EasingCurve.EASE_IN_OUT);
        if (requestedType == HandActionType.TRANSPORT) TrueGodEffects.transportStart(target);
        else if (requestedType != HandActionType.TOSS && requestedType != HandActionType.BLESS
                && requestedType != HandActionType.SPANK && requestedType != HandActionType.JUGGLE) TrueGodEffects.grabStart(target);
    }

    /**
     * Judgment: surface-only aerial pointing/orbit beam. It continues
     * until the target dies or ceases to be on the surface.
     */
    public void startJudgment(ParticleHand hand, Player target, double orbitRadius, int approachTicks, int ignoredStrikeTicks) {
        validateTarget(hand, target);
        if (!isSurfacePlayer(target)) {
            throw new IllegalArgumentException("Judgment requires the target to be on the surface.");
        }
        if (!Double.isFinite(orbitRadius) || orbitRadius < 8.0 || orbitRadius > 40.0) {
            throw new IllegalArgumentException("Judgment orbit radius must be between 8 and 40 blocks.");
        }
        validateTicks(approachTicks, "judgment approach");
        cancel(hand, "replaced by judgment", true);

        type = HandActionType.JUDGMENT;
        phase = Phase.JUDGMENT_APPROACH;
        this.target = target;
        actionHeight = orbitRadius;
        this.approachTicks = approachTicks;
        judgmentOrbitAngle = Math.toRadians(target.getLocation().getYaw() + 135.0);
        judgmentBeamTick = 0;
        judgmentImpactPlayed = false;
        lastImpactHitCount = 0;
        lastStopReason = "surface judgment active";

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionStopRootSpin();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.POINT, Math.max(10, Math.min(24, approachTicks)), EasingCurve.EASE_IN_OUT);
        hand.actionPointAt(target, 16.0);
        hand.actionTravelTo(judgmentOrbitPoint(hand, target, judgmentOrbitAngle, orbitRadius), approachTicks, EasingCurve.EASE_IN_OUT);
        TrueGodEffects.judgmentStart(target);
    }

    /** Force-slap charge sequence, preserved as a dedicated attack. */
    public void startForceSlap(ParticleHand hand, Player target, double stageDistance, int approachTicks, int strikeTicks) {
        validateTarget(hand, target);
        if (!Double.isFinite(stageDistance) || stageDistance < 4.0 || stageDistance > 48.0) {
            throw new IllegalArgumentException("ForceSlap stage distance must be between 4 and 48 blocks.");
        }
        validateTicks(approachTicks, "forceslap approach");
        validateTicks(strikeTicks, "forceslap strike");
        cancel(hand, "replaced by forceslap", true);
        combatResolver.setDeathStyle(HandDeathMessages.Style.FORCE_SLAP);

        type = HandActionType.FORCE_SLAP;
        phase = Phase.FORCE_SLAP_APPROACH;
        this.target = target;
        actionHeight = stageDistance;
        this.approachTicks = approachTicks;
        secondaryTicks = strikeTicks;
        recoilTicks = Math.max(10, Math.min(30, approachTicks / 2 + 6));
        lockedImpact = null;
        judgmentOrbitAngle = 0.0;
        judgmentBeamTick = 0;
        judgmentDirection = null;
        judgmentPreviousOrigin = null;
        judgmentImpactPlayed = false;
        actionHitPlayers.clear();
        lastImpactHitCount = 0;
        lastStopReason = "forceslap active";

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.OPEN, Math.max(6, Math.min(16, approachTicks)), EasingCurve.EASE_IN_OUT);
        hand.actionLookAt(target, 16.0);
        hand.actionTravelTo(judgmentStaging(target, stageDistance), approachTicks, EasingCurve.EASE_IN_OUT);
        TrueGodEffects.forceSlapStart(target);
    }

    public void startPunch(
            ParticleHand hand,
            Player target,
            double stageDistance,
            int approachTicks,
            int strikeTicks,
            double damage,
            double minimumHealthLoss,
            double horizontalKnockback,
            double verticalKnockback
    ) {
        validateTarget(hand, target);
        if (!Double.isFinite(stageDistance) || stageDistance < 4.0 || stageDistance > 40.0) {
            throw new IllegalArgumentException("Punch stage distance must be between 4 and 40 blocks.");
        }
        validateTicks(approachTicks, "punch approach");
        validateTicks(strikeTicks, "punch strike");
        if (!Double.isFinite(damage) || damage < 0.0 || damage > 500.0) {
            throw new IllegalArgumentException("Punch damage must be between 0 and 500.");
        }
        if (!Double.isFinite(minimumHealthLoss) || minimumHealthLoss < 0.0 || minimumHealthLoss > 100.0) {
            throw new IllegalArgumentException("Punch minimum health loss must be between 0 and 100.");
        }
        if (!Double.isFinite(horizontalKnockback) || horizontalKnockback < 0.0 || horizontalKnockback > 8.0) {
            throw new IllegalArgumentException("Punch knockback must be between 0 and 8 blocks/tick.");
        }

        cancel(hand, "replaced by punch", true);
        combatResolver.setDeathStyle(HandDeathMessages.Style.PUNCH);
        this.type = HandActionType.PUNCH;
        this.phase = Phase.PUNCH_APPROACH;
        this.target = target;
        this.actionHeight = stageDistance;
        this.approachTicks = approachTicks;
        this.secondaryTicks = strikeTicks;
        this.recoilTicks = Math.max(10, Math.min(26, approachTicks / 2 + 6));
        this.punchDamage = damage;
        this.punchMinimumHealthLoss = minimumHealthLoss;
        this.punchHorizontalKnockback = horizontalKnockback;
        this.punchVerticalKnockback = verticalKnockback;
        this.punchDirection = null;
        this.punchPreviousOrigin = null;
        this.punchImpactPlayed = false;
        this.actionHitPlayers.clear();
        this.lastImpactHitCount = 0;
        this.lastStopReason = "punch active";

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.FIST, Math.max(6, Math.min(14, approachTicks)), EasingCurve.EASE_IN_OUT);
        hand.actionDownLookAt(target, 18.0);
        hand.actionTravelTo(punchStaging(hand, target, stageDistance), approachTicks, EasingCurve.EASE_IN_OUT);
        TrueGodEffects.punchStart(target);
    }

    /** low-damage, extreme-knockback open-palm sweep. */
    public void startSlap(
            ParticleHand hand,
            Player target,
            double stageDistance,
            int approachTicks,
            int strikeTicks,
            double damage,
            double horizontalKnockback,
            double verticalKnockback
    ) {
        validateTarget(hand, target);
        if (!Double.isFinite(stageDistance) || stageDistance < 3.0 || stageDistance > 32.0) {
            throw new IllegalArgumentException("Slap stage distance must be between 3 and 32 blocks.");
        }
        validateTicks(approachTicks, "slap approach");
        validateTicks(strikeTicks, "slap strike");
        if (!Double.isFinite(damage) || damage < 0.0 || damage > 20.0) {
            throw new IllegalArgumentException("Slap damage must be between 0 and 20.");
        }
        if (!Double.isFinite(horizontalKnockback) || horizontalKnockback < 0.0 || horizontalKnockback > 8.0) {
            throw new IllegalArgumentException("Slap knockback must be between 0 and 8 blocks/tick.");
        }
        if (!Double.isFinite(verticalKnockback) || verticalKnockback < -2.0 || verticalKnockback > 3.0) {
            throw new IllegalArgumentException("Slap vertical knockback must be between -2 and 3 blocks/tick.");
        }

        cancel(hand, "replaced by slap", true);
        combatResolver.setDeathStyle(HandDeathMessages.Style.SLAP);
        this.type = HandActionType.SLAP;
        this.phase = Phase.SLAP_APPROACH;
        this.target = target;
        this.actionHeight = stageDistance;
        this.approachTicks = approachTicks;
        this.secondaryTicks = strikeTicks;
        this.recoilTicks = Math.max(8, Math.min(20, approachTicks / 2 + 4));
        this.slapDamage = damage;
        this.slapHorizontalKnockback = horizontalKnockback;
        this.slapVerticalKnockback = verticalKnockback;
        this.slapDirection = null;
        this.slapPreviousOrigin = null;
        this.slapImpactPlayed = false;
        this.actionHitPlayers.clear();
        this.lastImpactHitCount = 0;
        this.lastStopReason = "slap active";

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.OPEN, Math.max(5, Math.min(12, approachTicks)), EasingCurve.EASE_OUT);
        hand.actionLookAt(target, 18.0);
        hand.actionTravelTo(slapStaging(target, stageDistance), approachTicks, EasingCurve.EASE_IN_OUT);
    }

    /** frightening high-speed roll/corkscrew charge. */
    public void startCyclone(ParticleHand hand, Player target, double stageDistance, int approachTicks, int strikeTicks) {
        validateTarget(hand, target);
        if (!Double.isFinite(stageDistance) || stageDistance < 5.0 || stageDistance > 48.0) {
            throw new IllegalArgumentException("Cyclone stage distance must be between 5 and 48 blocks.");
        }
        validateTicks(approachTicks, "cyclone approach");
        validateTicks(strikeTicks, "cyclone strike");
        cancel(hand, "replaced by cyclone", true);
        combatResolver.setDeathStyle(HandDeathMessages.Style.CYCLONE);

        type = HandActionType.CYCLONE;
        phase = Phase.CYCLONE_APPROACH;
        this.target = target;
        actionHeight = stageDistance;
        this.approachTicks = approachTicks;
        secondaryTicks = strikeTicks;
        recoilTicks = Math.max(10, Math.min(24, approachTicks / 2 + 5));
        cycloneDirection = null;
        cyclonePreviousOrigin = null;
        cycloneImpactPlayed = false;
        actionHitPlayers.clear();
        lastImpactHitCount = 0;
        lastStopReason = "cyclone active";

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionStopRootSpin();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.FIST, Math.max(6, Math.min(14, approachTicks)), EasingCurve.EASE_IN_OUT);
        hand.actionLookAt(target, 20.0);
        hand.actionTravelTo(judgmentStaging(target, stageDistance), approachTicks, EasingCurve.EASE_IN_OUT);
        TrueGodEffects.cycloneStart(target);
    }

    /**
     * Breach: an interior-aware hunting form. Instead of hovering above
     * the victim and clipping into low cave ceilings, the Hand shrinks only as
     * much as the local room requires and manifests in visible airspace in front
     * of/alongside the player. It periodically backswing-lunges with its claw.
     */
    public void startBreach(ParticleHand hand, Player target, int durationTicks) {
        validateTarget(hand, target);
        validateTicks(durationTicks, "breach");
        cancel(hand, "replaced by breach", true);

        type = HandActionType.BREACH;
        phase = Phase.BREACH_HUNT;
        this.target = target;
        phaseTicksRemaining = durationTicks;
        breachPulseTick = 0;
        lastStopReason = "breach active";
        beginAdaptiveScale(hand);
        updateAdaptivePresenceScale(hand, target, 1.45, Math.min(2.20, adaptiveBaseScale), true);

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionStopRootSpin();
        hand.actionCancelAnimation();
        hand.actionTeleport(bunkerManifestPoint(hand, target));
        hand.actionLookAt(target, 22.0);
        hand.actionAnimatePose(HandPose.CLAW, 10, EasingCurve.EASE_IN_OUT);
        TrueGodEffects.breachStart(target);
    }

    public void startSmash(ParticleHand hand, Player target, double height, int approachTicks, float explosionPower) {
        validateTarget(hand, target);
        startSmashInternal(hand, target, null, height, approachTicks, explosionPower);
    }

    public void startSmash(ParticleHand hand, Location targetPoint, double height, int approachTicks, float explosionPower) {
        validateLocation(hand, targetPoint, "Smash point must be in the hand's current world.");
        startSmashInternal(hand, null, targetPoint, height, approachTicks, explosionPower);
    }

    private void startSmashInternal(ParticleHand hand, Player targetPlayer, Location targetPoint, double height, int approachTicks, float explosionPower) {
        validateHeight(height);
        validateTicks(approachTicks, "smash approach");
        if (!Float.isFinite(explosionPower) || explosionPower < 0.0f || explosionPower > 20.0f) {
            throw new IllegalArgumentException("Smash explosion power must be between 0 and 20.");
        }
        cancel(hand, "replaced by smash", true);
        combatResolver.setDeathStyle(HandDeathMessages.Style.SMASH);

        this.type = HandActionType.SMASH;
        this.phase = Phase.SMASH_APPROACH;
        this.target = targetPlayer;
        this.smashTargetPlayer = targetPlayer;
        this.smashTargetPoint = targetPoint == null ? null : targetPoint.clone();
        this.smashExplosionPower = explosionPower;
        this.actionHeight = height;
        this.approachTicks = approachTicks;
        this.phaseTicksRemaining = approachTicks;
        this.recoilTicks = 18;
        this.lastImpactHitCount = 0;
        this.lastStopReason = "smash tracking";

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.FIST, 12, EasingCurve.EASE_IN_OUT);
        Location aim = currentSmashPoint();
        if (targetPlayer != null) hand.actionDownLookAt(targetPlayer, 20.0);
        else hand.actionDownLookAt(aim, 20.0);
        hand.actionSteerTo(smashStaging(aim, height), 2.25, 0.20);
        TrueGodEffects.smashStart(targetPlayer, aim);
    }

    /**
     * persistent hover chase: a CLAW remains above/behind the target,
     * palm-down and continuously reacquiring no matter how far the player runs.
     */
    public void startHoverChase(ParticleHand hand, Player target) {
        validateTarget(hand, target);
        cancel(hand, "replaced by chase", true);

        this.type = HandActionType.CHASE;
        this.phase = Phase.HOVER_CHASE;
        this.target = target;
        this.actionHitPlayers.clear();
        this.lastImpactHitCount = 0;
        this.lastStopReason = "hover chase active";
        beginAdaptiveScale(hand);
        updateAdaptivePresenceScale(hand, target, 1.60, adaptiveBaseScale, true);

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.CLAW, 14, EasingCurve.EASE_IN_OUT);
        hand.actionDownLookAt(target, 12.0);
        tickHoverChase(hand);
        TrueGodEffects.chaseStart(target);
    }

    public void startStalk(ParticleHand hand, Player target) {
        validateTarget(hand, target);
        cancel(hand, "replaced by stalk", true);

        this.type = HandActionType.STALK;
        this.target = target;
        this.lockedImpact = null;
        this.actionHitPlayers.clear();
        this.lastImpactHitCount = 0;
        this.lastStopReason = "stalking";
        beginAdaptiveScale(hand);
        updateAdaptivePresenceScale(hand, target, 1.60, adaptiveBaseScale, true);

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.RELAXED, 18, EasingCurve.EASE_IN_OUT);
        enterStalkWatch(hand, true);
        TrueGodEffects.stalkStart(target);
    }

    // ---------------------------------------------------------------------
    // Release / throw / lifecycle
    // ---------------------------------------------------------------------

    public boolean release(ParticleHand hand, int releaseTicks) {
        validateTicks(releaseTicks, "release");
        if (type == HandActionType.IDLE) {
            return false;
        }

        Player releasedTarget = target;
        restoreAdaptiveScale(hand);
        releaseCarrier(hand);
        if (releasedTarget != null) TrueGodEffects.release(releasedTarget);
        target = null;
        clearDestinationState();
        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.OPEN, releaseTicks, EasingCurve.EASE_OUT);
        type = HandActionType.RELEASING;
        phase = Phase.RELEASING;
        phaseTicksRemaining = releaseTicks;
        lastStopReason = "releasing";
        return true;
    }

    public boolean throwHeld(ParticleHand hand, double forwardSpeed, double upwardSpeed, int openTicks) {
        if (type != HandActionType.HOLDING || target == null || !target.isOnline()) return false;
        if (!Double.isFinite(forwardSpeed) || forwardSpeed < 0.0 || forwardSpeed > 5.0) {
            throw new IllegalArgumentException("Throw forward speed must be between 0 and 5 blocks/tick.");
        }
        if (!Double.isFinite(upwardSpeed) || upwardSpeed < -2.0 || upwardSpeed > 3.0) {
            throw new IllegalArgumentException("Throw upward speed must be between -2 and 3 blocks/tick.");
        }
        validateTicks(openTicks, "throw open");
        type = HandActionType.THROWING;
        beginVisibleThrowSwing(hand, gripSolver.worldFingerDirection(hand), forwardSpeed, upwardSpeed, openTicks, HandActionType.THROWING);
        return true;
    }

    public boolean cancel(ParticleHand hand, String reason, boolean stopOwnedControllers) {
        boolean wasActive = type != HandActionType.IDLE;
        if (type == HandActionType.GUARD) restoreGuardAppearance(hand);
        restoreAdaptiveScale(hand);
        releaseCarrier(hand);
        if (stopOwnedControllers && wasActive) {
            hand.actionStopMotion();
            hand.actionStopLooking();
            hand.actionCancelAnimation();
        }
        resetTransientState();
        type = HandActionType.IDLE;
        phase = Phase.IDLE;
        phaseTicksRemaining = 0;
        lastStopReason = reason;
        return wasActive;
    }

    public void dispose(ParticleHand hand) {
        if (type == HandActionType.GUARD) restoreGuardAppearance(hand);
        restoreAdaptiveScale(hand);
        releaseCarrier(hand);
        gripCarrier.cleanup();
        resetTransientState();
        type = HandActionType.IDLE;
        phase = Phase.IDLE;
        phaseTicksRemaining = 0;
        lastStopReason = "disposed";
    }

    public boolean allowsManualOverride(HandControlLayer layer) {
        return type == HandActionType.HOLDING
                && (layer == HandControlLayer.TRANSLATION || layer == HandControlLayer.ORIENTATION);
    }

    public boolean isActive() { return type != HandActionType.IDLE; }
    public boolean isHolding() {
        return type == HandActionType.HOLDING
                || ((type == HandActionType.TRANSPORT || type == HandActionType.MOVE_TO || type == HandActionType.TOSS
                || type == HandActionType.SPANK || type == HandActionType.JUGGLE) && gripCarrier.isActive());
    }

    /**
     * True for every phase in which the target is physically mounted to the
     * invisible grip carrier. This is deliberately broader than isHolding(),
     * whose semantics are used by manual HOLDING/MOVE_TO commands.
     */
    public boolean hasActiveGrip() {
        return gripCarrier.isActive() && target != null && target.isOnline() && !target.isDead();
    }

    public boolean hasActiveGrip(Player player) {
        return hasActiveGrip() && player != null && target.getUniqueId().equals(player.getUniqueId());
    }
    public boolean isStalking() { return type == HandActionType.STALK; }
    public boolean isHoverChasing() { return type == HandActionType.CHASE; }
    public boolean isTransporting(Player player) {
        return type == HandActionType.TRANSPORT && gripCarrier.isActive() && target != null && player != null
                && target.getUniqueId().equals(player.getUniqueId());
    }
    public boolean isProtectedCarryTravel(Player player) {
        // Collision/fall protection follows the physical carrier rather than a
        // hand-maintained action whitelist. This also covers Bless, Rage and the
        // visible throw backswing, all of which genuinely mount the target.
        return hasActiveGrip(player);
    }
    public boolean isLongCarryCruise() {
        return (type == HandActionType.TRANSPORT || type == HandActionType.MOVE_TO)
                && gripCarrier.isActive() && transportDistanceRemaining > 24.0;
    }
    public double getRenderFractionMultiplier() {
        if (!isLongCarryCruise()) return 1.0;
        if (transportDistanceRemaining > 120.0) return 0.32;
        if (transportDistanceRemaining > 60.0) return 0.40;
        return 0.52;
    }
    public HandActionType getType() { return type; }
    public Player getTargetPlayer() { return target; }
    public int getLastImpactHitCount() { return lastImpactHitCount; }

    public String getDescription() {
        if (type == HandActionType.IDLE) return "idle (" + lastStopReason + ")";
        String targetName = target == null ? "" : " -> " + target.getName();
        if (type == HandActionType.TRANSPORT || type == HandActionType.MOVE_TO) {
            return type.commandName() + targetName + " => " + transportDestinationDescription() + " [" + phaseName() + "]";
        }
        if (type == HandActionType.SMASH && target == null && smashTargetPoint != null) {
            return "smash -> " + formatLocation(smashTargetPoint) + " [" + phaseName() + "]";
        }
        return type.commandName() + targetName + " [" + phaseName() + "]";
    }

    public String phaseName() { return phase.name().toLowerCase(Locale.ROOT).replace('_', '-'); }
    public String getTargetName() { return target == null ? "none" : target.getName(); }

    // ---------------------------------------------------------------------
    // Groundslam - late correction + swept contact
    // ---------------------------------------------------------------------

    private void tickSlamRise(ParticleHand hand) {
        if (hand.isMoving()) return;
        phase = Phase.SLAM_WINDUP;
        phaseTicksRemaining = 8;
        hand.actionAnimatePose(HandPose.OPEN, 7, EasingCurve.SMOOTH);
        TrueGodEffects.slamWindup(target);
    }

    private void tickSlamWindup(ParticleHand hand) {
        if (--phaseTicksRemaining > 0) return;
        phase = Phase.SLAM_DESCEND;
        phaseTicksRemaining = secondaryTicks;
        lockedImpact = null;
        slamPreviousOrigin = hand.getLocation();
        actionHitPlayers.clear();
        hand.actionStopMotion();
    }

    private void tickSlamDescend(ParticleHand hand) {
        Location currentOrigin = hand.getLocation();
        Location previousOrigin = slamPreviousOrigin == null ? currentOrigin : slamPreviousOrigin;
        int newHits = combatResolver.resolveSlamSweptContact(
                hand,
                previousOrigin,
                currentOrigin,
                hand.getSlamDamage(),
                hand.getSlamHorizontalKnockback(),
                hand.getSlamVerticalKnockback(),
                actionHitPlayers,
                0.10
        );
        slamPreviousOrigin = currentOrigin;
        lastImpactHitCount = actionHitPlayers.size();
        if (newHits > 0) {
            hand.actionStopMotion();
            completeSlamImpact(hand);
            return;
        }

        int commitTicks = Math.max(2, Math.min(4, secondaryTicks / 3));
        if (phaseTicksRemaining > commitTicks) {
            Location desired = liveSlamImpact(hand, target);
            double distance = hand.getLocation().distance(desired);
            hand.actionSteerTo(desired,
                    clamp(1.35 + distance * 0.075, 1.55, 3.25),
                    clamp(0.22 + distance * 0.018, 0.24, 0.52));
            phaseTicksRemaining--;
            return;
        }

        if (lockedImpact == null) {
            lockedImpact = liveSlamImpact(hand, target);
            hand.actionTravelTo(lockedImpact, Math.max(1, phaseTicksRemaining), EasingCurve.EASE_IN);
            phaseTicksRemaining = 0;
            return;
        }

        if (hand.isMoving()) return;
        combatResolver.resolveSlamContact(
                hand,
                hand.getSlamDamage(),
                hand.getSlamHorizontalKnockback(),
                hand.getSlamVerticalKnockback(),
                actionHitPlayers,
                0.10
        );
        lastImpactHitCount = actionHitPlayers.size();
        completeSlamImpact(hand);
    }

    private void completeSlamImpact(ParticleHand hand) {
        boolean targetHit = target != null && actionHitPlayers.contains(target.getUniqueId());
        combatResolver.playImpactEffects(hand);
        TrueGodEffects.slamImpact(target, targetHit);
        phase = Phase.SLAM_IMPACT_HOLD;
        phaseTicksRemaining = 5;
    }

    private void tickSlamImpactHold(ParticleHand hand) {
        if (--phaseTicksRemaining > 0) return;
        Location base = lockedImpact != null ? lockedImpact : hand.getLocation();
        Location recoil = base.clone().add(0.0, Math.max(3.0, actionHeight * 0.65), 0.0);
        hand.actionTravelTo(recoil, recoilTicks, EasingCurve.EASE_OUT);
        phase = Phase.SLAM_RECOIL;
    }

    private void tickSlamRecoil(ParticleHand hand) {
        if (hand.isMoving()) return;
        finish("slam complete; hits=" + lastImpactHitCount);
    }

    // ---------------------------------------------------------------------
    // Grab / transport
    // ---------------------------------------------------------------------

    private void tickGrabRise(ParticleHand hand) {
        if (hand.isMoving()) return;
        Location eye = target.getEyeLocation();
        double centerClearance = Math.max(1.25, hand.getScale() * 0.32);
        Location gripApproach = new Location(eye.getWorld(), eye.getX(), eye.getY() + centerClearance, eye.getZ());
        hand.actionTravelTo(gripApproach, Math.max(6, approachTicks / 2), EasingCurve.EASE_IN);
        phase = Phase.GRAB_DESCEND;
    }

    private void tickGrabDescend(ParticleHand hand) {
        if (hand.isMoving()) return;
        hand.actionAnimatePose(HandPose.CLAW, secondaryTicks, EasingCurve.EASE_IN_OUT);
        phase = Phase.GRAB_CLOSE;
        TrueGodEffects.grabClosing(target);
    }

    private void tickGrabClose(ParticleHand hand) {
        Location desiredOrigin = gripSolver.handOriginForPlayerTorso(hand, target);
        double distance = hand.getLocation().distance(desiredOrigin);
        double maxSpeed = clamp(0.78 + distance * 0.055, 0.82, 1.65);
        double acceleration = clamp(0.08 + distance * 0.008, 0.08, 0.18);
        hand.actionSteerTo(desiredOrigin, maxSpeed, acceleration);

        if (hand.isAnimating()) return;
        double captureTolerance = clamp(hand.getScale() * 0.12, 0.30, 0.62);
        if (gripSolver.cageToTorsoDistance(hand, target) > captureTolerance) return;

        hand.actionStopMotion();
        Location hold = gripSolver.playerFeetLocation(hand, target);
        gripCarrier.attach(target, hold);

        if (type == HandActionType.BLESS) {
            hand.actionStopLooking();
            phase = Phase.BLESS_HOLD;
            phaseTicksRemaining = 100;
            blessingStage = 0;
            lastStopReason = "blessing target";
            TrueGodEffects.blessCaptured(target);
            tickBlessHold(hand);
            return;
        }

        if (type == HandActionType.TOSS) {
            int topY = hand.getWorld().getHighestBlockYAt(target.getLocation(), HeightMap.MOTION_BLOCKING);
            double launchY = Math.max(target.getLocation().getY() + 12.0, topY + Math.max(18.0, hand.getScale() * 2.0));
            tossDestination = new Location(hand.getWorld(), target.getLocation().getX(), launchY, target.getLocation().getZ());
            phase = Phase.TOSS_ASCEND;
            lastStopReason = "toss carrying upward";
            hand.actionStopLooking();
            tickTossAscend(hand);
            return;
        }

        if (type == HandActionType.SPANK) {
            int topY = hand.getWorld().getHighestBlockYAt(target.getLocation(), HeightMap.MOTION_BLOCKING);
            double holdY = Math.max(target.getLocation().getY() + 12.0,
                    topY + Math.max(14.0, hand.getScale() * 2.1));
            spankHoldDestination = new Location(hand.getWorld(),
                    target.getLocation().getX(), holdY, target.getLocation().getZ());
            phase = Phase.SPANK_ASCEND;
            lastStopReason = "spank lifting target";
            hand.actionStopLooking();
            tickSpankAscend(hand);
            return;
        }

        if (type == HandActionType.JUGGLE) {
            int topY = hand.getWorld().getHighestBlockYAt(target.getLocation(), HeightMap.MOTION_BLOCKING);
            double y = Math.max(target.getLocation().getY() + 18.0, topY + 22.0);
            Vector right = targetRight(target);
            double halfSpan = Math.max(20.0, hand.getScale() * 6.0); // deliberately much wider than prior dual-Hand attacks
            Location center = new Location(hand.getWorld(), target.getLocation().getX(), y, target.getLocation().getZ());
            jugglePrimaryAnchor = center.clone().subtract(right.clone().multiply(halfSpan));
            juggleSecondaryAnchor = center.clone().add(right.clone().multiply(halfSpan));
            jugglePrimaryHolding = true;
            phase = Phase.JUGGLE_ASCEND;
            lastStopReason = "juggle lifting to wide aerial lane";
            hand.actionStopLooking();
            tickJuggleAscend(hand);
            return;
        }

        if (type == HandActionType.TRANSPORT) {
            phase = Phase.TRANSPORT_TRAVEL;
            lastStopReason = "transport carrying";
            TrueGodEffects.transportTaken(target);
            tickTransportTravel(hand);
            return;
        }

        hand.actionStopLooking();
        type = HandActionType.HOLDING;
        phase = Phase.HOLDING;
        lastStopReason = "holding player (physical claw catch + free-look carrier)";
        TrueGodEffects.grabbed(target);
        tickHolding(hand);
    }

    private void tickHolding(ParticleHand hand) {
        if (target == null || !target.isOnline() || target.isDead()) {
            cancel(hand, "held player unavailable", false);
            return;
        }
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
    }

    private void tickTransportTravel(ParticleHand hand) {
        tickCarryTravel(hand, true);
    }

    private void tickMoveToTravel(ParticleHand hand) {
        tickCarryTravel(hand, false);
    }

    private void tickCarryTravel(ParticleHand hand, boolean releaseAtArrival) {
        if (target == null || !target.isOnline() || target.isDead()) {
            cancel(hand, "carried player unavailable", true);
            return;
        }

        Location destination = resolveTransportDestination(hand);
        if (destination == null) {
            cancel(hand, "carry destination unavailable", true);
            return;
        }

        Location currentFeet = gripSolver.playerFeetLocation(hand, target);
        double feetDistance = currentFeet.distance(destination);
        transportDistanceRemaining = feetDistance;

        // Face the destination through the cruise, then freeze orientation for
        // the last few blocks. Hysteresis prevents a moving-player destination
        // from producing a near-target orientation/position feedback orbit.
        if (!transportFinalApproach && feetDistance <= 5.0) {
            transportFinalApproach = true;
            hand.actionStopLooking();
        } else if (transportFinalApproach && feetDistance > 8.0) {
            transportFinalApproach = false;
        }
        if (!transportFinalApproach) {
            if (transportDestinationPlayer != null) hand.actionLookAt(transportDestinationPlayer, 12.0);
            else hand.actionLookAt(destination, 12.0);
        }

        Location desiredOrigin = gripSolver.handOriginForPlayerFeetDestination(hand, target, destination);
        double distance = hand.getLocation().distance(desiredOrigin);

        // Long cruises move faster so the 20 Hz carrier/controller
        // spends fewer total ticks traversing hundreds of blocks. Rendering is
        // independently budgeted down by HandManager/ParticleHandRenderer.
        double maxSpeed = clamp(0.72 + distance * 0.050, 0.80, 3.20);
        double acceleration = clamp(0.065 + distance * 0.007, 0.075, 0.30);

        // Long player travel is often dominated by chunk loading rather than
        // hand math. Prefetch the chunk roughly 1.5 chunks ahead using Paper's
        // asynchronous chunk API. If it is not ready yet, temporarily cap
        // cruise speed so ArmorStand/passenger teleport does not force a large
        // synchronous chunk load on the current tick.
        boolean pathReady = prefetchCarryChunk(hand.getWorld(), hand.getLocation(), desiredOrigin, distance);
        if (!pathReady) {
            maxSpeed = Math.min(maxSpeed, 1.05);
            acceleration = Math.min(acceleration, 0.12);
        }
        hand.actionSteerTo(desiredOrigin, maxSpeed, acceleration);

        // One grip solve/update per tick. The passenger remains mounted, so this
        // does not alter camera yaw/pitch.
        currentFeet = gripSolver.playerFeetLocation(hand, target);
        gripCarrier.update(target, currentFeet);

        double arrivalTolerance = clamp(0.70 + hand.getScale() * 0.10, 0.90, 1.55);
        if (currentFeet.distance(destination) > arrivalTolerance) return;

        hand.actionStopMotion();
        hand.actionStopLooking();
        transportDistanceRemaining = 0.0;

        if (!releaseAtArrival) {
            clearDestinationState();
            type = HandActionType.HOLDING;
            phase = Phase.HOLDING;
            lastStopReason = "moveto arrived; still holding";
            tickHolding(hand);
            return;
        }

        Location release = destination.clone();
        Location currentPlayer = target.getLocation();
        release.setYaw(currentPlayer.getYaw());
        release.setPitch(currentPlayer.getPitch());
        Player delivered = target;
        gripCarrier.release(delivered, release);
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.OPEN, 10, EasingCurve.EASE_OUT);
        target = null;
        clearDestinationState();
        phase = Phase.TRANSPORT_RELEASE;
        phaseTicksRemaining = 10;
        lastStopReason = "transport delivered";
        TrueGodEffects.transportArrive(delivered);
    }

    private void tickTransportRelease(ParticleHand hand) {
        if (hand.isAnimating()) return;
        finish("transport complete");
    }

    // ---------------------------------------------------------------------
    // Bless - five-second held blessing, release, visible departure
    // ---------------------------------------------------------------------

    private void tickBlessHold(ParticleHand hand) {
        if (target == null || !target.isOnline() || target.isDead()) {
            cancel(hand, "bless target unavailable", true);
            return;
        }
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));

        phaseTicksRemaining--;
        int elapsed = 100 - Math.max(0, phaseTicksRemaining);
        while (blessingStage < 5 && elapsed >= (blessingStage + 1) * 20) {
            applyBlessingEffect(target, blessingStage);
            TrueGodEffects.blessPulse(target, blessingStage);
            blessingStage++;
        }

        if (phaseTicksRemaining > 0) return;

        Player blessed = target;
        Location releaseFeet = gripSolver.playerFeetLocation(hand, blessed);
        gripCarrier.release(blessed, releaseFeet);
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.OPEN, 14, EasingCurve.EASE_OUT);
        TrueGodEffects.blessComplete(blessed);

        Vector leave = hand.transformLocalPoint(-0.20, -2.7, -0.45);
        blessingDeparture = hand.getLocation().clone().add(leave).add(0.0, Math.max(7.0, hand.getScale() * 1.8), 0.0);
        hand.actionTravelTo(blessingDeparture, 38, EasingCurve.EASE_IN);
        phase = Phase.BLESS_DEPART;
        phaseTicksRemaining = 46;
        lastStopReason = "blessing departing";
        target = null;
    }

    private void tickBlessDepart(ParticleHand hand) {
        if (hand.isMoving() || hand.isAnimating()) return;
        hand.requestRemoval();
        finish("bless complete; hand departed");
    }

    private static void applyBlessingEffect(Player player, int stage) {
        final int fiveMinutes = 20 * 60 * 5;
        PotionEffect effect = switch (stage) {
            case 0 -> new PotionEffect(PotionEffectType.REGENERATION, fiveMinutes, 0, false, true, true);
            case 1 -> new PotionEffect(PotionEffectType.RESISTANCE, fiveMinutes, 0, false, true, true);
            case 2 -> new PotionEffect(PotionEffectType.STRENGTH, fiveMinutes, 0, false, true, true);
            case 3 -> new PotionEffect(PotionEffectType.SPEED, fiveMinutes, 0, false, true, true);
            default -> new PotionEffect(PotionEffectType.ABSORPTION, fiveMinutes, 1, false, true, true);
        };
        player.addPotionEffect(effect);
    }

    // ---------------------------------------------------------------------
    // Sanctuary - short protective/healing presence
    // ---------------------------------------------------------------------

    private void tickSanctuaryApproach(ParticleHand hand) {
        if (hand.isMoving()) return;
        phase = Phase.SANCTUARY_GUARD;
        // phaseTicksRemaining already contains the requested guard duration.
        lastStopReason = "sanctuary guarding";
        TrueGodEffects.sanctuaryArrived(target);
        tickSanctuaryGuard(hand);
    }

    private void tickSanctuaryGuard(ParticleHand hand) {
        if (target == null || !target.isOnline() || target.isDead()) {
            cancel(hand, "sanctuary target unavailable", true);
            return;
        }

        Location shield = target.getEyeLocation().clone().add(0.0, Math.max(4.5, hand.getScale() * 1.20), 0.0);
        double distance = hand.getLocation().distance(shield);
        hand.actionSteerTo(shield,
                clamp(0.65 + distance * 0.05, 0.75, 1.65),
                clamp(0.08 + distance * 0.008, 0.09, 0.22));
        hand.actionDownLookAt(target, 14.0);

        sanctuaryPulseTick++;
        if ((sanctuaryPulseTick % 10) == 0) {
            double maxHealth = 20.0;
            if (target.getAttribute(Attribute.MAX_HEALTH) != null) {
                maxHealth = target.getAttribute(Attribute.MAX_HEALTH).getValue();
            }
            target.setHealth(Math.min(maxHealth, target.getHealth() + 1.25));
            target.setFireTicks(0);
            target.setFoodLevel(Math.min(20, target.getFoodLevel() + 1));
            target.setSaturation(Math.min(20.0f, target.getSaturation() + 0.7f));
            TrueGodEffects.sanctuaryPulse(target);
        }
        if ((sanctuaryPulseTick % 20) == 1) {
            // Continuously refreshed short effects: the benefit is tied to being
            // under the Hand, unlike Bless's five-minute long-term boons.
            target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 35, 2, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 35, 1, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 45, 0, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 45, 1, false, true, true));
        }

        if (--phaseTicksRemaining > 0) return;

        TrueGodEffects.sanctuaryComplete(target);
        hand.actionStopLooking();
        hand.actionAnimatePose(HandPose.RELAXED, 14, EasingCurve.EASE_OUT);
        sanctuaryDeparture = hand.getLocation().clone().add(0.0, Math.max(8.0, hand.getScale() * 2.0), 0.0);
        hand.actionTravelTo(sanctuaryDeparture, 34, EasingCurve.EASE_IN);
        target = null;
        phase = Phase.SANCTUARY_DEPART;
        lastStopReason = "sanctuary departing";
    }

    private void tickSanctuaryDepart(ParticleHand hand) {
        if (hand.isMoving() || hand.isAnimating()) return;
        hand.requestRemoval();
        finish("sanctuary complete; hand departed");
    }

    // ---------------------------------------------------------------------
    // seven-hit dual-Hand slap sequence
    // ---------------------------------------------------------------------

    private void tickSpankAscend(ParticleHand hand) {
        if (target == null || !target.isOnline() || target.isDead() || spankHoldDestination == null) {
            cancel(hand, "spank target unavailable", true);
            return;
        }

        Location desiredOrigin = gripSolver.handOriginForPlayerFeetDestination(hand, target, spankHoldDestination);
        double distance = hand.getLocation().distance(desiredOrigin);
        hand.actionSteerTo(desiredOrigin,
                clamp(0.9 + distance * 0.06, 1.0, 2.7),
                clamp(0.10 + distance * 0.008, 0.11, 0.28));
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));

        if (gripSolver.playerFeetLocation(hand, target).distance(spankHoldDestination) > 0.75) return;

        hand.actionStopMotion();
        Location under = spankRestPoint(hand, target);
        secondaryHand = cloneVisualHand(hand, under);
        secondaryHand.applyPose(HandPose.OPEN);
        orientSpankPalm(false);
        secondaryHand.startIdle();
        // This sequence owns the second hand, so don't let its idle anchor fight
        // the first explicit slap travel. Manual travel disarms that idle.
        secondaryHand.travelTo(under, 1, EasingCurve.LINEAR);

        spankCount = 0;
        phase = Phase.SPANK_SETUP;
        phaseTicksRemaining = 14;
        lastStopReason = "spank second hand manifested";
        TrueGodEffects.spankManifest(target);
    }

    private void tickSpankSetup(ParticleHand hand) {
        if (target == null || !target.isOnline() || target.isDead()) {
            cancel(hand, "spank target unavailable", true);
            return;
        }
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        if (--phaseTicksRemaining > 0) return;

        phase = Phase.SPANK_BACKSWING;
        Location rest = spankRestPoint(hand, target);
        orientSpankPalm(false);
        secondaryHand.travelTo(rest, 6, EasingCurve.EASE_IN_OUT);
    }

    private void tickSpankBackswing(ParticleHand hand) {
        if (!spankTargetAvailable(hand)) return;
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        orientSpankPalm(false);
        if (secondaryHand.isMoving()) return;

        phase = Phase.SPANK_STRIKE;
        secondaryHand.travelTo(spankStrikePoint(hand, target), 4, EasingCurve.EASE_OUT);
    }

    private void tickSpankStrike(ParticleHand hand) {
        if (!spankTargetAvailable(hand)) return;
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        orientSpankPalm(false);
        if (secondaryHand.isMoving()) return;

        HandDeathMessages.damage(target, 3.0, HandDeathMessages.Style.SPANK);
        spankCount++;
        TrueGodEffects.spankHit(target, spankCount, false);

        if (spankCount < 6) {
            phase = Phase.SPANK_BACKSWING;
            secondaryHand.travelTo(spankRestPoint(hand, target), 7, EasingCurve.EASE_IN_OUT);
            return;
        }

        // Short dramatic pause before the seventh angled release slap.
        phase = Phase.SPANK_PAUSE;
        phaseTicksRemaining = 14;
        secondaryHand.travelTo(spankRestPoint(hand, target), 8, EasingCurve.EASE_OUT);
    }

    private void tickSpankPause(ParticleHand hand) {
        if (!spankTargetAvailable(hand)) return;
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        if (--phaseTicksRemaining > 0 || secondaryHand.isMoving()) return;

        phase = Phase.SPANK_FINAL_WINDUP;
        // Only the seventh slap is allowed to tilt. It still uses the same
        // palm-to-target solver; the aim point is merely offset sideways/up.
        orientSpankPalm(true);
        Location windup = spankRestPoint(hand, target)
                .add(targetRight(target).multiply(hand.getScale() * 0.42))
                .add(0.0, -0.45, 0.0);
        secondaryHand.travelTo(windup, 10, EasingCurve.EASE_IN_OUT);
    }

    private void tickSpankFinalWindup(ParticleHand hand) {
        if (!spankTargetAvailable(hand)) return;
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        orientSpankPalm(true);
        if (secondaryHand.isMoving()) return;
        phase = Phase.SPANK_FINAL_STRIKE;
        secondaryHand.travelTo(spankStrikePoint(hand, target).add(0.0, 0.25, 0.0), 5, EasingCurve.EASE_IN);
    }

    private void tickSpankFinalStrike(ParticleHand hand) {
        if (!spankTargetAvailable(hand)) return;
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        if (secondaryHand.isMoving()) return;

        Player launched = target;
        orientSpankPalm(true);
        HandDeathMessages.damage(launched, 3.0, HandDeathMessages.Style.SPANK);
        TrueGodEffects.spankHit(launched, 7, true);

        Location releaseFeet = gripSolver.playerFeetLocation(hand, launched);
        gripCarrier.release(launched, releaseFeet);

        Vector launch = spankLaunchDirection == null ? new Vector(0.0, 0.0, 1.0) : spankLaunchDirection.clone();
        launch.setY(0.0);
        if (launch.lengthSquared() < 1.0e-8) launch = new Vector(0.0, 0.0, 1.0);
        launch.normalize().multiply(2.85).setY(0.95);
        launched.setVelocity(launch);
        HandDeathMessages.markThrow(launched, HandDeathMessages.Style.SPANK);

        if (secondaryHand != null) secondaryHand.requestRemoval();
        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionAnimatePose(HandPose.RELAXED, 16, EasingCurve.EASE_OUT);
        target = null;
        phase = Phase.SPANK_RECOVER;
        phaseTicksRemaining = 18;
        lastStopReason = "spank final launch";
    }

    private void tickSpankRecover(ParticleHand hand) {
        if (secondaryHand != null) {
            if (!secondaryHand.isRemovalRequested()) return;
            disposeSecondary();
        }
        if (--phaseTicksRemaining > 0 || hand.isAnimating()) return;
        finish("spank complete; primary hand remains");
    }

    private boolean spankTargetAvailable(ParticleHand hand) {
        if (target != null && target.isOnline() && !target.isDead() && target.getWorld().equals(hand.getWorld())
                && secondaryHand != null) return true;
        cancel(hand, "spank target unavailable", true);
        return false;
    }

    private ParticleHand cloneVisualHand(ParticleHand source, Location location) {
        ParticleHand clone = new ParticleHand(location, source.getScale());
        clone.setDensity(source.getDensity());
        clone.setBaseColor(source.getBaseColor());
        clone.setShadingEnabled(source.isShadingEnabled());
        clone.setForceParticles(source.isForceParticles());
        clone.setWristStyle(source.getWristStyle());
        clone.setRenderMode(source.getRenderMode());
        clone.setAxesVisible(false);
        clone.setSkeletonVisible(false);
        clone.setGripDebugVisible(false);
        clone.setCombatDebugVisible(false);
        return clone;
    }

    private void orientSpankPalm(boolean finalTilt) {
        if (secondaryHand == null || target == null) return;
        Location aim = upperTorso(target);
        if (finalTilt) {
            // Slightly offset the seventh aim so the palm banks into the launch,
            // while still keeping its front (+Z) pointed at the victim.
            aim.add(targetRight(target).multiply(1.15)).add(0.0, 0.45, 0.0);
        }
        secondaryHand.actionLookAt(aim, 180.0);
    }

    private Location spankRestPoint(ParticleHand main, Player player) {
        return player.getLocation().clone().add(0.0, -Math.max(3.0, main.getScale() * 0.92), 0.0);
    }

    private Location spankStrikePoint(ParticleHand main, Player player) {
        return player.getLocation().clone().add(0.0, -Math.max(1.05, main.getScale() * 0.30), 0.0);
    }

    private void disposeSecondary() {
        if (secondaryHand != null) {
            secondaryHand.dispose();
            secondaryHand = null;
        }
    }

    // ---------------------------------------------------------------------
    // Rage - three fire-trail dash contacts, grab, high swing throw
    // ---------------------------------------------------------------------

    private void tickRageApproach(ParticleHand hand) {
        if (hand.isMoving()) {
            updateRageOrientation(hand);
            spawnRageTrail(hand, 5);
            return;
        }
        phase = Phase.RAGE_DASH_WINDUP;
        phaseTicksRemaining = 7;
        updateRageOrientation(hand);
        TrueGodEffects.rageRoar(target, rageDashIndex + 1);
    }

    private void tickRageDashWindup(ParticleHand hand) {
        updateRageOrientation(hand);
        if (--phaseTicksRemaining > 0) return;

        Location strike = rageStrikePoint(target, rageDashIndex);
        Vector dir = strike.toVector().subtract(hand.getLocation().toVector());
        if (dir.lengthSquared() < 1.0e-8) dir = horizontalFacing(target);
        rageDashDirection = dir.normalize();
        ragePreviousOrigin = hand.getLocation();
        actionHitPlayers.clear();
        phase = Phase.RAGE_DASH_STRIKE;
        int ticks = rageDashIndex == 2 ? 6 : 5;
        hand.actionTravelTo(strike, ticks, EasingCurve.EASE_IN);
        TrueGodEffects.rageDash(target, rageDashIndex + 1);
    }

    private void tickRageDashStrike(ParticleHand hand) {
        spawnRageTrail(hand, 16);
        Location current = hand.getLocation();
        Location previous = ragePreviousOrigin == null ? current : ragePreviousOrigin;
        double[] damages = {3.0, 5.0, 7.0};
        double[] horizontalKb = {0.52, 0.72, 0.92};
        double[] verticalKb = {0.10, 0.16, 0.24};
        int newHits = combatResolver.resolveDirectionalSweptContact(
                hand, previous, current,
                damages[rageDashIndex], rageDashDirection,
                horizontalKb[rageDashIndex], verticalKb[rageDashIndex],
                actionHitPlayers, 0.12
        );
        ragePreviousOrigin = current;
        if (newHits > 0) {
            lastImpactHitCount += newHits;
            TrueGodEffects.rageImpact(target, rageDashIndex + 1);
        }
        if (hand.isMoving()) return;

        if (rageDashIndex < 2) {
            rageDashIndex++;
            actionHitPlayers.clear();
            prepareRageDashPose(hand, rageDashIndex);
            updateRageOrientation(hand);
            hand.actionTravelTo(rageWindupPoint(target, rageDashIndex), 11, EasingCurve.EASE_OUT);
            phase = Phase.RAGE_APPROACH;
            lastStopReason = "rage dash " + (rageDashIndex + 1) + " staging";
            return;
        }

        // The combo finishes with a real physical grab rather than a scripted
        // teleport. The target can still move while the claw hunts their torso.
        actionHitPlayers.clear();
        hand.actionStopMotion();
        hand.actionDownLookAt(target, 22.0);
        hand.actionAnimatePose(HandPose.OPEN, 8, EasingCurve.EASE_OUT);
        phase = Phase.RAGE_GRAB_APPROACH;
        lastStopReason = "rage grab approach";
    }

    private void tickRageGrabApproach(ParticleHand hand) {
        hand.actionDownLookAt(target, 22.0);
        Location desired = stagingAbove(target, 6.5);
        double distance = hand.getLocation().distance(desired);
        hand.actionSteerTo(desired, clamp(1.4 + distance * 0.07, 1.6, 3.8), 0.28);
        spawnRageTrail(hand, 6);
        if (distance > 1.25) return;

        hand.actionStopMotion();
        hand.actionAnimatePose(HandPose.CLAW, 12, EasingCurve.EASE_IN_OUT);
        phase = Phase.RAGE_GRAB_CLOSE;
        TrueGodEffects.rageRoar(target, 4);
    }

    private void tickRageGrabClose(ParticleHand hand) {
        Location desiredOrigin = gripSolver.handOriginForPlayerTorso(hand, target);
        double distance = hand.getLocation().distance(desiredOrigin);
        hand.actionSteerTo(desiredOrigin,
                clamp(1.10 + distance * 0.07, 1.25, 2.4),
                clamp(0.12 + distance * 0.010, 0.14, 0.30));
        hand.actionDownLookAt(target, 24.0);
        spawnRageTrail(hand, 5);

        if (hand.isAnimating()) return;
        double tolerance = clamp(hand.getScale() * 0.12, 0.32, 0.66);
        if (gripSolver.cageToTorsoDistance(hand, target) > tolerance) return;

        hand.actionStopMotion();
        gripCarrier.attach(target, gripSolver.playerFeetLocation(hand, target));
        int topY = hand.getWorld().getHighestBlockYAt(target.getLocation(), HeightMap.MOTION_BLOCKING);
        double liftY = Math.max(target.getLocation().getY() + 18.0,
                topY + Math.max(26.0, hand.getScale() * 2.9));
        rageLiftDestination = new Location(hand.getWorld(), target.getLocation().getX(), liftY, target.getLocation().getZ());
        rageThrowDirection = horizontalFacing(target);
        phase = Phase.RAGE_ASCEND;
        hand.actionStopLooking();
        lastStopReason = "rage lifting target";
        TrueGodEffects.rageGrabbed(target);
    }

    private void tickRageAscend(ParticleHand hand) {
        Location desiredOrigin = gripSolver.handOriginForPlayerFeetDestination(hand, target, rageLiftDestination);
        double distance = hand.getLocation().distance(desiredOrigin);
        hand.actionSteerTo(desiredOrigin,
                clamp(1.6 + distance * 0.055, 1.8, 4.0),
                clamp(0.18 + distance * 0.007, 0.20, 0.44));
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        spawnRageTrail(hand, 7);
        if (gripSolver.playerFeetLocation(hand, target).distance(rageLiftDestination) > 1.15) return;

        hand.actionStopMotion();
        rageSwingAnchor = hand.getLocation();
        phase = Phase.RAGE_THROW_BACK;
        phaseTicksRemaining = 12;
        Location backAim = rageSwingAnchor.clone()
                .subtract(rageThrowDirection.clone().multiply(12.0))
                .add(0.0, -4.0, 0.0);
        hand.actionPointAt(backAim, 38.0);
        hand.actionTravelTo(rageSwingAnchor.clone()
                .subtract(rageThrowDirection.clone().multiply(2.2))
                .add(0.0, -1.0, 0.0), 10, EasingCurve.EASE_OUT);
        TrueGodEffects.rageThrowWindup(target);
    }

    private void tickRageThrowBack(ParticleHand hand) {
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        if (--phaseTicksRemaining > 0 || hand.isMoving()) return;

        phase = Phase.RAGE_THROW_FORWARD;
        phaseTicksRemaining = 10;
        Location forwardAim = rageSwingAnchor.clone()
                .add(rageThrowDirection.clone().multiply(18.0))
                .add(0.0, 6.0, 0.0);
        hand.actionPointAt(forwardAim, 60.0);
        hand.actionTravelTo(rageSwingAnchor.clone()
                .add(rageThrowDirection.clone().multiply(3.2))
                .add(0.0, 0.8, 0.0), 10, EasingCurve.EASE_IN);
    }

    private void tickRageThrowForward(ParticleHand hand) {
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        spawnRageTrail(hand, 10);
        if (--phaseTicksRemaining > 0 || hand.isMoving()) return;

        Player thrown = target;
        Location releaseFeet = gripSolver.playerFeetLocation(hand, thrown);
        gripCarrier.release(thrown, releaseFeet);
        Vector velocity = gripSolver.worldFingerDirection(hand);
        if (velocity.lengthSquared() < 1.0e-8) velocity = rageThrowDirection == null ? new Vector(0.0, 0.0, 1.0) : rageThrowDirection.clone();
        velocity.normalize().multiply(3.85);
        if (velocity.getY() < 0.50) velocity.setY(0.50);
        thrown.setVelocity(velocity);
        HandDeathMessages.markThrow(thrown, HandDeathMessages.Style.RAGE);
        TrueGodEffects.rageThrow(thrown);

        target = null;
        hand.actionStopLooking();
        hand.actionAnimatePose(HandPose.OPEN, 10, EasingCurve.EASE_OUT);
        hand.actionTravelTo(hand.getLocation().clone().add(velocity.clone().normalize().multiply(2.0)).add(0.0, 2.0, 0.0),
                14, EasingCurve.EASE_OUT);
        phase = Phase.RAGE_RECOVER;
        phaseTicksRemaining = 18;
        lastStopReason = "rage throw complete";
    }

    private void tickRageRecover(ParticleHand hand) {
        if (hand.isMoving() || hand.isAnimating() || --phaseTicksRemaining > 0) return;
        finish("rage complete; dash damage capped at 15 before throw");
    }

    private void prepareRageDashPose(ParticleHand hand, int index) {
        HandPose pose = index == 0 ? HandPose.OPEN : HandPose.FIST;
        hand.actionAnimatePose(pose, 8, EasingCurve.EASE_IN_OUT);
    }

    private void updateRageOrientation(ParticleHand hand) {
        if (rageDashIndex == 2) hand.actionDownLookAt(target, 28.0);
        else hand.actionLookAt(target, 28.0);
    }

    private void spawnRageTrail(ParticleHand hand, int count) {
        World world = hand.getWorld();
        if (world == null) return;
        Location at = hand.getLocation();
        world.spawnParticle(Particle.FLAME, at, count,
                Math.max(0.18, hand.getScale() * 0.18),
                Math.max(0.18, hand.getScale() * 0.18),
                Math.max(0.18, hand.getScale() * 0.18),
                0.025, null, hand.isForceParticles());
    }

    // ---------------------------------------------------------------------
    // Clap - two open palms converge around the target
    // ---------------------------------------------------------------------

    private void tickClapApproach(ParticleHand hand) {
        if (secondaryHand == null) {
            cancel(hand, "clap secondary hand unavailable", true);
            return;
        }
        Location clapTorso = upperTorso(target);
        Vector clapFingers = horizontalFacing(target);
        orientClapHorizontal(hand, clapTorso, clapFingers);
        orientClapHorizontal(secondaryHand, clapTorso, clapFingers);
        if (hand.isMoving() || --phaseTicksRemaining > 0) return;

        Location torso = upperTorso(target);
        Location primaryOut = torso.clone().add(clapRight.clone().multiply(10.2));
        Location secondaryOut = torso.clone().subtract(clapRight.clone().multiply(10.2));
        hand.actionTravelTo(primaryOut, 10, EasingCurve.EASE_OUT);
        secondaryHand.travelTo(secondaryOut, 10, EasingCurve.EASE_OUT);
        phase = Phase.CLAP_WINDUP;
        phaseTicksRemaining = 14;
        TrueGodEffects.clapCharge(target);
    }

    private void tickClapWindup(ParticleHand hand) {
        if (secondaryHand == null) {
            cancel(hand, "clap secondary hand unavailable", true);
            return;
        }
        Location clapTorso = upperTorso(target);
        Vector clapFingers = horizontalFacing(target);
        orientClapHorizontal(hand, clapTorso, clapFingers);
        orientClapHorizontal(secondaryHand, clapTorso, clapFingers);
        playClapChargeParticles(hand, clapTorso);
        if (hand.isMoving() || secondaryHand.isMoving() || --phaseTicksRemaining > 0) return;

        clapLockedTorso = upperTorso(target);
        double gap = Math.max(0.20, hand.getScale() * 0.075);
        Location primaryStrike = clapLockedTorso.clone().add(clapRight.clone().multiply(gap));
        Location secondaryStrike = clapLockedTorso.clone().subtract(clapRight.clone().multiply(gap));
        orientClapHorizontal(hand, clapLockedTorso, horizontalFacing(target));
        orientClapHorizontal(secondaryHand, clapLockedTorso, horizontalFacing(target));
        hand.actionTravelTo(primaryStrike, 5, EasingCurve.EASE_IN);
        secondaryHand.travelTo(secondaryStrike, 5, EasingCurve.EASE_IN);
        phase = Phase.CLAP_STRIKE;
    }

    private void tickClapStrike(ParticleHand hand) {
        if (secondaryHand == null) {
            cancel(hand, "clap secondary hand unavailable", true);
            return;
        }
        if (clapLockedTorso != null) {
            Vector clapFingers = target == null ? new Vector(0.0, 0.0, 1.0) : horizontalFacing(target);
            orientClapHorizontal(hand, clapLockedTorso, clapFingers);
            orientClapHorizontal(secondaryHand, clapLockedTorso, clapFingers);
        }
        if (hand.isMoving() || secondaryHand.isMoving()) return;
        if (clapImpactPlayed) return;
        clapImpactPlayed = true;

        if (target != null && target.isOnline() && !target.isDead()) {
            // Clap is a true finisher now. Normal damage is armor/Resistance
            // dependent, so explicitly attribute the kill and collapse health.
            HandDeathMessages.markDirect(target, HandDeathMessages.Style.CLAP);
            lastImpactHitCount = 1;
            playClapEffects(hand, clapLockedTorso == null ? upperTorso(target) : clapLockedTorso);
            TrueGodEffects.clapImpact(target);
            target.setHealth(0.0);
        }

        target = null;
        secondaryHand.requestRemoval();
        hand.requestRemoval();
        phase = Phase.CLAP_DISMISS;
        lastStopReason = "clap impact; both hands dismissing";
    }

    private void tickClapDismiss(ParticleHand hand) {
        if (secondaryHand != null && secondaryHand.isRemovalRequested()) {
            disposeSecondary();
        }
        // The primary HandManager consumes hand.isRemovalRequested() after the
        // animated dismissal completes, so no explicit finish() is needed here.
    }

    private void playClapChargeParticles(ParticleHand hand, Location center) {
        if (center == null || center.getWorld() == null || phaseTicksRemaining % 2 != 0) return;
        World world = center.getWorld();
        double spread = Math.max(0.45, hand.getScale() * 0.22);
        world.spawnParticle(Particle.END_ROD, center, 6, spread, spread * 0.72, spread, 0.025, null, hand.isForceParticles());
        world.spawnParticle(Particle.DUST, center, 10, spread * 1.18, spread * 0.78, spread * 1.18, 0.015,
                new Particle.DustOptions(Color.fromRGB(235, 245, 255), 0.90f), hand.isForceParticles());
    }

    private void playClapEffects(ParticleHand hand, Location impact) {
        if (impact == null || impact.getWorld() == null) return;
        World world = impact.getWorld();
        double s = Math.max(0.8, hand.getScale() * 0.42);
        world.spawnParticle(Particle.CLOUD, impact, 190, s * 1.20, s * 0.72, s * 1.20, 0.24, null, hand.isForceParticles());
        world.spawnParticle(Particle.EXPLOSION, impact, 34, s * 0.72, s * 0.46, s * 0.72, 0.0, null, hand.isForceParticles());
        world.spawnParticle(Particle.SWEEP_ATTACK, impact, 26, s * 0.82, s * 0.44, s * 0.82, 0.0, null, hand.isForceParticles());
        world.spawnParticle(Particle.END_ROD, impact, 62, s * 0.95, s * 0.68, s * 0.95, 0.065, null, hand.isForceParticles());
        world.spawnParticle(Particle.DUST, impact, 70, s * 0.78, s * 0.62, s * 0.78, 0.02,
                new Particle.DustOptions(Color.fromRGB(225, 240, 255), 1.15f), hand.isForceParticles());

        // Effect-only lightning + expanding cloud ring makes the lethal clap
        // read as a true thunderous finisher without creating block damage.
        world.strikeLightningEffect(impact);
        double ringRadius = Math.max(2.4, hand.getScale() * 0.78);
        for (int i = 0; i < 28; i++) {
            double angle = Math.PI * 2.0 * i / 28.0;
            Location ring = impact.clone().add(Math.cos(angle) * ringRadius, 0.10, Math.sin(angle) * ringRadius);
            world.spawnParticle(Particle.CLOUD, ring, 3, 0.10, 0.05, 0.10, 0.07, null, hand.isForceParticles());
        }
    }


    // ---------------------------------------------------------------------
    // Pound - alternating two-Hand fist slams
    // ---------------------------------------------------------------------

    private void tickPoundApproach(ParticleHand hand) {
        if (secondaryHand == null) {
            cancel(hand, "pound secondary hand unavailable", true);
            return;
        }
        orientPoundFist(hand);
        orientPoundFist(secondaryHand);
        if (hand.isMoving() || secondaryHand.isMoving() || hand.isAnimating() || --phaseTicksRemaining > 0) return;
        if (target.getHealth() <= 6.0) {
            finishPound(hand);
            return;
        }
        beginPoundWindup(hand);
    }

    private void beginPoundWindup(ParticleHand hand) {
        if (secondaryHand == null || target == null) return;
        ParticleHand striker = poundPrimaryTurn ? hand : secondaryHand;
        striker.actionStopMotion();
        striker.actionCancelAnimation();
        striker.actionAnimatePose(HandPose.FIST, 2, EasingCurve.EASE_IN_OUT);
        orientPoundFist(striker);

        // Two-piece backswing: first climb/outward to a crest, then sweep
        // rearward into the cocked position. This produces a visible arcing
        // windup before every downward slam instead of one diagonal travel.
        poundWindupStep = 0;
        striker.actionTravelTo(poundBackswingCrestPoint(target, poundPrimaryTurn), 2, EasingCurve.EASE_OUT);
        phase = Phase.POUND_WINDUP;
        phaseTicksRemaining = 0;
        lastStopReason = "pound backswing crest " + (poundPrimaryTurn ? "primary" : "secondary");
        TrueGodEffects.poundWindup(target, poundHitCount + 1);
    }

    private void tickPoundWindup(ParticleHand hand) {
        if (secondaryHand == null) {
            cancel(hand, "pound secondary hand unavailable", true);
            return;
        }
        orientPoundFist(hand);
        orientPoundFist(secondaryHand);
        ParticleHand striker = poundPrimaryTurn ? hand : secondaryHand;
        if (striker.isMoving() || striker.isAnimating()) return;

        if (poundWindupStep == 0) {
            poundWindupStep = 1;
            striker.actionTravelTo(poundWindupPoint(target, poundPrimaryTurn), 2, EasingCurve.EASE_IN_OUT);
            phaseTicksRemaining = 0; // No dead pause; immediately chain into the slam
            lastStopReason = "pound backswing cocked " + (poundPrimaryTurn ? "primary" : "secondary");
            return;
        }

        if (phaseTicksRemaining-- > 0) return;
        // HandMotionController caps steering at 8 blocks/tick and
        // 2 blocks/tick^2. Run Pound at the legal ceiling instead of throwing
        // every server tick with the old 12.4 / 2.56 request.
        striker.actionSteerTo(poundStrikePoint(striker, target), 8.0, 2.0);
        phase = Phase.POUND_STRIKE;
        lastStopReason = "pound slam " + (poundHitCount + 1);
    }

    private void tickPoundStrike(ParticleHand hand) {
        if (secondaryHand == null) {
            cancel(hand, "pound secondary hand unavailable", true);
            return;
        }
        orientPoundFist(hand);
        orientPoundFist(secondaryHand);
        ParticleHand striker = poundPrimaryTurn ? hand : secondaryHand;
        Location liveStrike = poundStrikePoint(striker, target);
        double strikeDistance = striker.getLocation().distance(liveStrike);
        if (strikeDistance > 0.58) {
            // Keep target tracking just as aggressive as the initial slam, but never
            // exceed the motion controller's validated steering envelope.
            striker.actionSteerTo(liveStrike, clamp(6.8 + strikeDistance * 0.42, 7.2, 8.0), 2.0);
            return;
        }
        striker.actionStopMotion();

        if (target != null && target.isOnline() && !target.isDead() && target.getHealth() > 6.0) {
            target.setNoDamageTicks(0);
            target.setHealth(Math.max(0.0, target.getHealth() - 3.0));
            target.setVelocity(new Vector(0.0, -0.18, 0.0));
            target.setFallDistance(0.0f);
            poundHitCount++;
            lastImpactHitCount = poundHitCount;
            playPoundImpact(hand, upperTorso(target), poundHitCount);
            TrueGodEffects.poundImpact(target, poundHitCount);
        }

        if (target == null || !target.isOnline() || target.isDead() || target.getHealth() <= 6.0) {
            finishPound(hand);
            return;
        }

        striker.actionTravelTo(poundRestPoint(target, poundPrimaryTurn), 2, EasingCurve.EASE_OUT);
        poundPrimaryTurn = !poundPrimaryTurn;
        poundEnding = false;
        phase = Phase.POUND_RECOVER;
        phaseTicksRemaining = 1;
    }

    private void tickPoundRecover(ParticleHand hand) {
        if (poundEnding) {
            if (secondaryHand != null) {
                if (!secondaryHand.isRemovalRequested()) return;
                disposeSecondary();
            }
            if (hand.isMoving() || hand.isAnimating()) return;
            finish("pound complete at three-heart mercy threshold after " + poundHitCount + " slams");
            return;
        }

        if (secondaryHand == null) {
            cancel(hand, "pound secondary hand unavailable", true);
            return;
        }
        ParticleHand recovering = poundPrimaryTurn ? secondaryHand : hand;
        if (recovering.isMoving() || --phaseTicksRemaining > 0) return;
        beginPoundWindup(hand);
    }

    private void finishPound(ParticleHand hand) {
        poundEnding = true;
        hand.actionStopMotion();
        hand.actionAnimatePose(HandPose.RELAXED, 12, EasingCurve.EASE_OUT);
        if (target != null && target.isOnline() && !target.isDead()) {
            hand.actionTravelTo(poundRestPoint(target, true), 10, EasingCurve.EASE_OUT);
        }
        if (secondaryHand != null) {
            secondaryHand.actionStopMotion();
            secondaryHand.actionAnimatePose(HandPose.RELAXED, 10, EasingCurve.EASE_OUT);
            secondaryHand.requestRemoval();
        }
        phase = Phase.POUND_RECOVER;
        phaseTicksRemaining = 0;
        lastStopReason = "pound mercy threshold reached";
        TrueGodEffects.poundComplete(target, poundHitCount);
    }

    private void playPoundImpact(ParticleHand hand, Location impact, int hit) {
        if (impact == null || impact.getWorld() == null) return;
        double s = Math.max(0.55, hand.getScale() * 0.24);
        World world = impact.getWorld();
        world.spawnParticle(Particle.EXPLOSION, impact, 10, s, s * 0.45, s, 0.0, null, hand.isForceParticles());
        world.spawnParticle(Particle.CLOUD, impact, 48, s * 0.90, s * 0.35, s * 0.90, 0.12, null, hand.isForceParticles());
        world.spawnParticle(Particle.SWEEP_ATTACK, impact, 8, s * 0.55, s * 0.30, s * 0.55, 0.0, null, hand.isForceParticles());
        double ringRadius = Math.max(1.15, hand.getScale() * 0.34);
        for (int i = 0; i < 14; i++) {
            double angle = Math.PI * 2.0 * i / 14.0;
            Location ring = impact.clone().add(Math.cos(angle) * ringRadius, 0.05, Math.sin(angle) * ringRadius);
            world.spawnParticle(Particle.CLOUD, ring, 2, 0.06, 0.03, 0.06, 0.04, null, hand.isForceParticles());
        }
    }

    // ---------------------------------------------------------------------
    // Wave / thumbs gestures
    // ---------------------------------------------------------------------

    private void tickWaveApproach(ParticleHand hand) {
        gestureAnchor = gestureFrontPoint(target, hand.getScale(), 0.85);
        orientWave(hand, 0.0);
        if (hand.isMoving() || hand.isAnimating()) return;
        phase = Phase.WAVE_ANIMATE;
        phaseTicksRemaining = 76;
        gestureTick = 0;
        lastStopReason = "waving";
    }

    private void tickWaveAnimate(ParticleHand hand) {
        gestureAnchor = gestureFrontPoint(target, hand.getScale(), 0.85);
        double distance = hand.getLocation().distance(gestureAnchor);
        if (distance > 0.32) hand.actionSteerTo(gestureAnchor, Math.min(1.25, 0.45 + distance * 0.12), 0.14);
        else hand.actionStopMotion();

        double angle = Math.sin(gestureTick * 0.42) * 31.0;
        orientWave(hand, angle);
        if ((gestureTick % 12) == 0) TrueGodEffects.waveBeat(target);
        gestureTick++;
        if (--phaseTicksRemaining > 0) return;

        hand.actionStopMotion();
        hand.actionAnimatePose(HandPose.RELAXED, 12, EasingCurve.EASE_OUT);
        hand.actionTravelTo(hand.getLocation().clone().add(0.0, 1.0, 0.0), 10, EasingCurve.EASE_OUT);
        phase = Phase.WAVE_RECOVER;
    }

    private void tickWaveRecover(ParticleHand hand) {
        if (hand.isMoving() || hand.isAnimating()) return;
        finish("wave complete");
    }

    private void tickThumbApproach(ParticleHand hand) {
        gestureAnchor = gestureFrontPoint(target, hand.getScale(), thumbGestureUp ? 0.50 : 1.35);
        orientThumb(hand, thumbGestureUp);
        if (hand.isMoving() || hand.isAnimating()) return;
        phase = Phase.THUMB_HOLD;
        phaseTicksRemaining = 60;
        gestureTick = 0;
        TrueGodEffects.thumbDisplayed(target, thumbGestureUp);
    }

    private void tickThumbHold(ParticleHand hand) {
        gestureAnchor = gestureFrontPoint(target, hand.getScale(), thumbGestureUp ? 0.50 : 1.35);
        double distance = hand.getLocation().distance(gestureAnchor);
        if (distance > 0.34) hand.actionSteerTo(gestureAnchor, Math.min(1.10, 0.42 + distance * 0.10), 0.12);
        else hand.actionStopMotion();
        orientThumb(hand, thumbGestureUp);
        gestureTick++;
        if (--phaseTicksRemaining > 0) return;

        hand.actionStopMotion();
        hand.actionAnimatePose(HandPose.RELAXED, 12, EasingCurve.EASE_OUT);
        phase = Phase.THUMB_RECOVER;
        phaseTicksRemaining = 10;
    }

    private void tickThumbRecover(ParticleHand hand) {
        if (hand.isAnimating() || --phaseTicksRemaining > 0) return;
        finish(thumbGestureUp ? "thumbs-up complete" : "thumbs-down complete");
    }


    // ---------------------------------------------------------------------
    // GiveBird - surface-only gesture then controlled lightning
    // ---------------------------------------------------------------------

    private void tickGiveBirdApproach(ParticleHand hand) {
        if (giveBirdAttackMode && !isSurfacePlayer(target)) {
            stopGiveBird(hand, "givebird stopped: target left surface");
            return;
        }
        Location desired = giveBirdFrontPoint(target, hand.getScale());
        if (hand.getLocation().distance(desired) > 0.45) {
            hand.actionSteerTo(desired, 1.35, 0.12);
        } else {
            hand.actionStopMotion();
        }
        orientGiveBird(hand);
        if (hand.isMoving() || hand.isAnimating()) return;
        phase = Phase.GIVE_BIRD_WAIT;
        phaseTicksRemaining = 60; // three full seconds of simply displaying the gesture
        lastStopReason = "givebird displayed";
    }

    private void tickGiveBirdWait(ParticleHand hand) {
        if (giveBirdAttackMode && !isSurfacePlayer(target)) {
            stopGiveBird(hand, "givebird stopped: target left surface");
            return;
        }
        Location desired = giveBirdFrontPoint(target, hand.getScale());
        double distance = hand.getLocation().distance(desired);
        if (distance > 0.55) hand.actionSteerTo(desired, Math.min(1.45, 0.65 + distance * 0.08), 0.12);
        else hand.actionStopMotion();
        orientGiveBird(hand);
        if (--phaseTicksRemaining > 0) return;

        if (!giveBirdAttackMode) {
            hand.actionStopMotion();
            hand.actionAnimatePose(HandPose.RELAXED, 12, EasingCurve.EASE_OUT);
            phase = Phase.GIVE_BIRD_RECOVER;
            phaseTicksRemaining = 10;
            lastStopReason = "bird gesture complete";
            return;
        }

        phase = Phase.GIVE_BIRD_LIGHTNING;
        giveBirdLightningTick = 0;
        lastStopReason = "givebird lightning active";
        TrueGodEffects.giveBirdMessage(target);
    }

    private void tickGiveBirdLightning(ParticleHand hand) {
        if (!isSurfacePlayer(target)) {
            stopGiveBird(hand, "givebird stopped: target left surface");
            return;
        }
        Location desired = giveBirdFrontPoint(target, hand.getScale());
        double distance = hand.getLocation().distance(desired);
        if (distance > 0.55) hand.actionSteerTo(desired, Math.min(1.65, 0.72 + distance * 0.09), 0.14);
        else hand.actionStopMotion();
        orientGiveBird(hand);

        if (target.getHealth() <= 6.05) {
            hand.actionStopMotion();
            hand.actionStopLooking();
            hand.actionAnimatePose(HandPose.RELAXED, 18, EasingCurve.EASE_OUT);
            phase = Phase.GIVE_BIRD_RECOVER;
            phaseTicksRemaining = 20;
            lastStopReason = "givebird mercy threshold reached";
            return;
        }

        giveBirdLightningTick++;
        if (giveBirdLightningTick % 5 == 0) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.setNoDamageTicks(0);
            double amount = Math.min(3.0, Math.max(0.0, target.getHealth() - 6.0));
            if (amount > 0.0) target.damage(amount);
            TrueGodEffects.giveBirdLightning(target);
        }
    }

    private void tickGiveBirdRecover(ParticleHand hand) {
        if (hand.isAnimating() || --phaseTicksRemaining > 0) return;
        finish("givebird complete at three-heart mercy threshold");
    }

    private void stopGiveBird(ParticleHand hand, String reason) {
        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionAnimatePose(HandPose.RELAXED, 12, EasingCurve.EASE_OUT);
        finish(reason);
    }

    // ---------------------------------------------------------------------
    // Toss - wide endless two-Hand aerial parabola/catch loop
    // ---------------------------------------------------------------------

    private void tickJuggleAscend(ParticleHand hand) {
        if (!juggleTargetAvailable(hand) || jugglePrimaryAnchor == null || juggleSecondaryAnchor == null) return;
        Location desiredOrigin = gripSolver.handOriginForPlayerFeetDestination(hand, target, jugglePrimaryAnchor);
        double distance = hand.getLocation().distance(desiredOrigin);
        hand.actionSteerTo(desiredOrigin, clamp(1.2 + distance * 0.055, 1.35, 3.6), clamp(0.13 + distance * 0.006, 0.15, 0.36));
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        target.setFallDistance(0.0f);
        if (gripSolver.playerFeetLocation(hand, target).distance(jugglePrimaryAnchor) > 1.0) return;

        hand.actionStopMotion();
        secondaryHand = cloneVisualHand(hand, juggleSecondaryAnchor);
        secondaryHand.applyPose(HandPose.OPEN);
        secondaryHand.actionLookAt(target, 180.0);
        hand.actionPointAt(juggleSecondaryAnchor, 180.0);
        phase = Phase.JUGGLE_SETUP;
        phaseTicksRemaining = 18;
        lastStopReason = "juggle wide lane established";
    }

    private void tickJuggleSetup(ParticleHand hand) {
        if (!juggleTargetAvailable(hand)) return;
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        target.setFallDistance(0.0f);
        orientFingerToward(hand, juggleSecondaryAnchor);
        secondaryHand.actionLookAt(target, 180.0);
        if (--phaseTicksRemaining > 0) return;
        phase = Phase.JUGGLE_HOLD_PRIMARY;
        phaseTicksRemaining = 8;
    }

    private void tickJuggleHoldPrimary(ParticleHand hand) {
        if (!juggleTargetAvailable(hand)) return;
        Location desiredOrigin = gripSolver.handOriginForPlayerFeetDestination(hand, target, jugglePrimaryAnchor);
        if (hand.getLocation().distance(desiredOrigin) > 0.50) hand.actionSteerTo(desiredOrigin, 1.6, 0.18);
        else hand.actionStopMotion();
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        target.setVelocity(new Vector());
        target.setFallDistance(0.0f);
        orientFingerToward(hand, juggleSecondaryAnchor);

        // The receiver waits visibly open while the current hand secures the grab.
        secondaryHand.actionAnimatePose(HandPose.OPEN, 6, EasingCurve.EASE_IN_OUT);
        secondaryHand.actionLookAt(target, 180.0);
        if (--phaseTicksRemaining > 0 || hand.isMoving()) return;
        beginTossWindup(hand, true);
    }

    private void tickJuggleHoldSecondary(ParticleHand hand) {
        if (!juggleTargetAvailable(hand)) return;
        Location desiredOrigin = gripSolver.handOriginForPlayerFeetDestination(secondaryHand, target, juggleSecondaryAnchor);
        if (secondaryHand.getLocation().distance(desiredOrigin) > 0.50) secondaryHand.actionSteerTo(desiredOrigin, 1.6, 0.18);
        else secondaryHand.actionStopMotion();
        gripCarrier.update(target, gripSolver.playerFeetLocation(secondaryHand, target));
        target.setVelocity(new Vector());
        target.setFallDistance(0.0f);
        orientFingerToward(secondaryHand, jugglePrimaryAnchor);

        hand.actionAnimatePose(HandPose.OPEN, 6, EasingCurve.EASE_IN_OUT);
        hand.actionLookAt(target, 180.0);
        if (--phaseTicksRemaining > 0 || secondaryHand.isMoving()) return;
        beginTossWindup(hand, false);
    }

    /**
     * Pulls the grabbing hand backward before every exchange. This makes each
     * catch read as a real grab -> windup -> throw instead of instantly opening
     * the claw and launching the player from a static pose.
     */
    private void beginTossWindup(ParticleHand main, boolean fromPrimary) {
        ParticleHand sender = fromPrimary ? main : secondaryHand;
        Location senderAnchor = fromPrimary ? jugglePrimaryAnchor : juggleSecondaryAnchor;
        Location receiverAnchor = fromPrimary ? juggleSecondaryAnchor : jugglePrimaryAnchor;
        if (sender == null || senderAnchor == null || receiverAnchor == null || target == null) return;

        Vector away = senderAnchor.toVector().subtract(receiverAnchor.toVector());
        if (away.lengthSquared() < 1.0e-8) away = new Vector(fromPrimary ? -1.0 : 1.0, 0.0, 0.0);
        away.setY(0.0).normalize();
        double windupDistance = Math.max(2.6, sender.getScale() * 0.72);
        juggleWindupDestination = sender.getLocation().clone()
                .add(away.multiply(windupDistance))
                .add(0.0, -Math.max(0.45, sender.getScale() * 0.12), 0.0);

        sender.actionAnimatePose(HandPose.CLAW, 5, EasingCurve.EASE_IN_OUT);
        sender.actionSteerTo(juggleWindupDestination, 1.15, 0.22);
        phase = fromPrimary ? Phase.JUGGLE_WINDUP_PRIMARY : Phase.JUGGLE_WINDUP_SECONDARY;
        phaseTicksRemaining = 8;
        lastStopReason = fromPrimary ? "juggle primary hand winding up" : "juggle secondary hand winding up";
    }

    private void tickJuggleWindup(ParticleHand main, boolean fromPrimary) {
        if (!juggleTargetAvailable(main)) return;
        ParticleHand sender = fromPrimary ? main : secondaryHand;
        ParticleHand receiver = fromPrimary ? secondaryHand : main;
        Location receiverAnchor = fromPrimary ? juggleSecondaryAnchor : jugglePrimaryAnchor;
        if (sender == null || receiver == null || juggleWindupDestination == null || receiverAnchor == null) return;

        sender.actionSteerTo(juggleWindupDestination, 1.15, 0.22);
        gripCarrier.update(target, gripSolver.playerFeetLocation(sender, target));
        target.setVelocity(new Vector());
        target.setFallDistance(0.0f);
        orientFingerToward(sender, receiverAnchor);
        receiver.actionAnimatePose(HandPose.OPEN, 5, EasingCurve.EASE_IN_OUT);
        receiver.actionLookAt(target, 180.0);

        if (--phaseTicksRemaining > 0 && sender.getLocation().distance(juggleWindupDestination) > 0.35) return;
        beginTossThrow(main, fromPrimary);
    }

    private void beginTossThrow(ParticleHand main, boolean fromPrimary) {
        ParticleHand sender = fromPrimary ? main : secondaryHand;
        ParticleHand receiver = fromPrimary ? secondaryHand : main;
        Location senderAnchor = fromPrimary ? jugglePrimaryAnchor : juggleSecondaryAnchor;
        Location destination = fromPrimary ? juggleSecondaryAnchor : jugglePrimaryAnchor;
        if (sender == null || receiver == null || senderAnchor == null || destination == null || target == null) return;

        orientFingerToward(sender, destination);
        sender.actionAnimatePose(HandPose.OPEN, 5, EasingCurve.EASE_OUT);
        receiver.actionAnimatePose(HandPose.OPEN, 5, EasingCurve.EASE_IN_OUT);

        Player tossed = target;
        Location releaseFeet = gripSolver.playerFeetLocation(sender, tossed);
        gripCarrier.release(tossed, releaseFeet);

        Vector delta = destination.toVector().subtract(tossed.getLocation().toVector());
        double horizontalDistance = Math.sqrt(delta.getX() * delta.getX() + delta.getZ() * delta.getZ());

        // A 40-ish block lane needs a materially faster crossing than the old
        // 0.92-block/tick calculation; otherwise vanilla gravity drops the player
        // below the receiving hand before the catch window ever becomes true.
        juggleFlightDuration = (int) Math.round(clamp(horizontalDistance / 1.55, 20.0, 29.0));
        juggleFlightTick = 0;
        juggleSenderRecoverDelay = 6;

        // Approximate vanilla player gravity/drag so the arc comes back down near
        // the same elevation at the planned catch tick instead of undershooting.
        double verticalVelocity = Math.max(0.78, 0.0455 * juggleFlightDuration - 0.115)
                + delta.getY() / juggleFlightDuration;
        Vector velocity = new Vector(
                delta.getX() / juggleFlightDuration,
                verticalVelocity,
                delta.getZ() / juggleFlightDuration
        );
        tossed.setVelocity(velocity);
        tossed.setFallDistance(0.0f);

        // Follow-through: the throwing hand drives a few blocks toward the other
        // hand while opening, then returns to its own anchor during flight.
        Vector follow = destination.toVector().subtract(senderAnchor.toVector());
        if (follow.lengthSquared() > 1.0e-8) {
            follow.setY(0.0).normalize();
            Location followThrough = sender.getLocation().clone()
                    .add(follow.multiply(Math.max(3.0, sender.getScale() * 0.82)))
                    .add(0.0, Math.max(0.55, sender.getScale() * 0.14), 0.0);
            sender.actionSteerTo(followThrough, 1.85, 0.34);
        }

        HandDeathMessages.markThrow(tossed, HandDeathMessages.Style.JUGGLE);
        TrueGodEffects.juggleThrow(tossed, ++juggleCycle);

        juggleWindupDestination = null;
        jugglePrimaryHolding = !fromPrimary;
        phase = fromPrimary ? Phase.JUGGLE_FLIGHT_TO_SECONDARY : Phase.JUGGLE_FLIGHT_TO_PRIMARY;
        lastStopReason = fromPrimary ? "juggle flight to second hand" : "juggle flight to primary hand";
    }

    private void tickJuggleFlight(ParticleHand main, boolean receiverIsPrimary) {
        if (!juggleTargetAvailable(main)) return;
        ParticleHand receiver = receiverIsPrimary ? main : secondaryHand;
        ParticleHand sender = receiverIsPrimary ? secondaryHand : main;
        Location receiverAnchor = receiverIsPrimary ? jugglePrimaryAnchor : juggleSecondaryAnchor;
        Location senderAnchor = receiverIsPrimary ? juggleSecondaryAnchor : jugglePrimaryAnchor;
        if (receiver == null || sender == null || receiverAnchor == null || senderAnchor == null) return;

        juggleFlightTick++;
        target.setFallDistance(0.0f);

        // Keep the airborne target magnetically committed to the receiving lane.
        // The Y component stays ballistic so the juggle still has a visible arc,
        // while X/Z are corrected every tick so player air-control cannot dodge
        // sideways out of the catch.
        Vector toCatch = receiverAnchor.toVector().subtract(target.getLocation().toVector());
        int ticksLeft = Math.max(3, juggleFlightDuration - juggleFlightTick);
        Vector current = target.getVelocity();
        double guideStrength = juggleFlightTick < juggleFlightDuration / 2 ? 0.42 : 0.78;
        double desiredX = toCatch.getX() / ticksLeft;
        double desiredZ = toCatch.getZ() / ticksLeft;
        current.setX(current.getX() + (desiredX - current.getX()) * guideStrength);
        current.setZ(current.getZ() + (desiredZ - current.getZ()) * guideStrength);
        target.setVelocity(current);

        // Keep the receiver centered on its side of the lane. It can make a small
        // visual reach toward the real trajectory, but it no longer has to chase
        // a dodging player indefinitely.
        Location desiredReceiver = gripSolver.handOriginForPlayerFeetDestination(receiver, target, receiverAnchor);
        double anchorDistance = receiver.getLocation().distance(desiredReceiver);
        receiver.actionSteerTo(desiredReceiver, clamp(1.15 + anchorDistance * 0.08, 1.35, 2.9),
                clamp(0.13 + anchorDistance * 0.008, 0.15, 0.32));
        receiver.actionLookAt(target, 180.0);
        receiver.actionAnimatePose(HandPose.OPEN, 4, EasingCurve.EASE_IN_OUT);

        if (juggleSenderRecoverDelay > 0) {
            juggleSenderRecoverDelay--;
        } else if (sender.getLocation().distance(senderAnchor) > 0.55) {
            sender.actionSteerTo(senderAnchor, 1.55, 0.22);
        } else {
            sender.actionStopMotion();
        }
        orientFingerToward(sender, receiverAnchor);

        double torsoToAnchor = gripSolver.playerTorsoLocation(target).distance(receiverAnchor.clone().add(0.0, 1.0, 0.0));
        double cageDistance = gripSolver.cageToTorsoDistance(receiver, target);
        double naturalCatchRadius = Math.max(1.25, receiver.getScale() * 0.30);
        boolean naturalCatch = juggleFlightTick >= 8 && (cageDistance <= naturalCatchRadius || torsoToAnchor <= 1.55);
        boolean forcedCatch = juggleFlightTick >= juggleFlightDuration;
        if (!naturalCatch && !forcedCatch) return;

        receiver.actionStopMotion();
        receiver.actionAnimatePose(HandPose.CLAW, 5, EasingCurve.EASE_IN_OUT);
        target.setVelocity(new Vector());
        Location caughtFeet = gripSolver.playerFeetLocation(receiver, target);

        // Guaranteed catch: keep the receiving Hand on its symmetric lane anchor
        // and magnetically snap the player into its cage only if trajectory/client
        // drift prevented the normal proximity catch. Player yaw/pitch are retained
        // because playerFeetLocation starts from the player's current Location.
        if (forcedCatch && cageDistance > naturalCatchRadius) {
            target.teleport(caughtFeet);
        }

        gripCarrier.attach(target, caughtFeet);
        gripCarrier.update(target, caughtFeet);
        target.setFallDistance(0.0f);

        jugglePrimaryHolding = receiverIsPrimary;
        phase = receiverIsPrimary ? Phase.JUGGLE_HOLD_PRIMARY : Phase.JUGGLE_HOLD_SECONDARY;
        phaseTicksRemaining = 10;
        juggleFlightTick = 0;
        juggleFlightDuration = 0;
        juggleSenderRecoverDelay = 0;
        TrueGodEffects.juggleCatch(target, juggleCycle);
    }

    private boolean juggleTargetAvailable(ParticleHand hand) {
        if (target != null && target.isOnline() && !target.isDead() && target.getWorld().equals(hand.getWorld())
                && (secondaryHand != null || phase == Phase.JUGGLE_ASCEND)) return true;
        cancel(hand, "juggle target unavailable", true);
        return false;
    }

    // ---------------------------------------------------------------------
    // Guard - scale-1 emerald companion with persistent wolf-like aggro
    // ---------------------------------------------------------------------

    public boolean isGuarding() { return type == HandActionType.GUARD && guardOwner != null; }
    public Player getGuardOwner() { return isGuarding() ? guardOwner : null; }

    public void guardAggro(ParticleHand hand, LivingEntity enemy) {
        if (!isGuarding() || !isGuardEligible(enemy) || enemy.getWorld() == null || !enemy.getWorld().equals(hand.getWorld())) return;
        if (guardOwner != null && enemy.getUniqueId().equals(guardOwner.getUniqueId())) return;
        if (guardEnemy != null && isGuardEligible(guardEnemy) && !guardEnemy.isDead() && guardEnemy.isValid()) {
            // Like a wolf, stay committed to the current threat until it dies.
            return;
        }
        guardEnemy = enemy;
        guardAttackIndex = 0;
        beginGuardAttack(hand);
    }

    private void tickGuardOrbit(ParticleHand hand) {
        if (guardOwner == null || !guardOwner.isOnline() || guardOwner.isDead()
                || guardOwner.getWorld() == null || !guardOwner.getWorld().equals(hand.getWorld())) {
            cancel(hand, "guard owner unavailable", true);
            return;
        }
        if (guardEnemy != null && isGuardEligible(guardEnemy) && !guardEnemy.isDead() && guardEnemy.isValid()) {
            beginGuardAttack(hand);
            return;
        }
        guardEnemy = null;
        hand.setBaseColor(HandPalette.EMERALD);
        hand.actionAnimatePose(HandPose.RELAXED, 8, EasingCurve.EASE_IN_OUT);
        if (hand.getTranslationMode() != xyz.dimseal.godHand.hand.motion.TranslationMode.ORBIT) {
            hand.actionOrbit(guardOwner, 2.8, 4.2, 1.7, 0.62, 0.075);
        }
        hand.actionLookAt(guardOwner, 14.0);
    }

    private void beginGuardAttack(ParticleHand hand) {
        if (!isGuardEligible(guardEnemy)) {
            guardEnemy = null;
            phase = Phase.GUARD_ORBIT;
            return;
        }
        phase = Phase.GUARD_ATTACK_APPROACH;
        guardAttackTick = 0;
        hand.setBaseColor(HandPalette.CRIMSON);
        hand.actionStopMotion();
        hand.actionStopLooking();
        int mode = guardAttackIndex % 3;
        Location enemy = guardEnemy.getLocation().clone().add(0.0, Math.max(0.55, guardEnemy.getHeight() * 0.55), 0.0);
        if (mode == 0) {
            hand.actionAnimatePose(HandPose.OPEN, 5, EasingCurve.EASE_OUT);
            Vector side = horizontalPerpendicular(guardOwner.getLocation().toVector().subtract(enemy.toVector()));
            hand.actionTravelTo(enemy.clone().add(side.multiply(2.4)).add(0.0, 0.2, 0.0), 7, EasingCurve.EASE_OUT);
            hand.actionLookAt(enemy, 40.0);
        } else if (mode == 1) {
            hand.actionAnimatePose(HandPose.FIST, 5, EasingCurve.EASE_IN_OUT);
            Vector away = enemy.toVector().subtract(guardOwner.getLocation().toVector());
            away.setY(0.0);
            if (away.lengthSquared() < 1.0e-8) away = new Vector(0.0, 0.0, 1.0);
            hand.actionTravelTo(enemy.clone().subtract(away.normalize().multiply(2.8)).add(0.0, 0.4, 0.0), 8, EasingCurve.EASE_OUT);
            hand.actionLookAt(enemy, 40.0);
        } else {
            hand.actionAnimatePose(HandPose.FIST, 5, EasingCurve.EASE_IN_OUT);
            hand.actionTravelTo(enemy.clone().add(0.0, 3.6, 0.0), 8, EasingCurve.EASE_OUT);
            hand.actionDownLookAt(enemy, 40.0);
        }
        lastStopReason = "guard engaging " + guardEnemy.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    private void tickGuardAttackApproach(ParticleHand hand) {
        if (!guardEnemyStillValid(hand)) return;
        Location enemy = guardEnemy.getLocation().clone().add(0.0, Math.max(0.55, guardEnemy.getHeight() * 0.55), 0.0);
        int mode = guardAttackIndex % 3;
        if (mode == 2) hand.actionDownLookAt(enemy, 50.0);
        else hand.actionLookAt(enemy, 50.0);
        if (hand.isMoving() || hand.isAnimating()) return;

        phase = Phase.GUARD_ATTACK_STRIKE;
        guardAttackTick = 0;
        if (mode == 0) {
            Vector through = enemy.toVector().subtract(hand.getLocation().toVector());
            through.setY(0.0);
            if (through.lengthSquared() < 1.0e-8) through = new Vector(1.0, 0.0, 0.0);
            hand.actionTravelTo(enemy.clone().add(through.normalize().multiply(1.6)), 4, EasingCurve.EASE_IN);
        } else if (mode == 1) {
            hand.actionTravelTo(enemy.clone(), 4, EasingCurve.EASE_IN);
        } else {
            hand.actionTravelTo(enemy.clone().add(0.0, 0.25, 0.0), 5, EasingCurve.EASE_IN);
        }
    }

    private void tickGuardAttackStrike(ParticleHand hand) {
        if (!guardEnemyStillValid(hand)) return;
        if (hand.isMoving()) return;
        int mode = guardAttackIndex % 3;
        double damage = mode == 0 ? 2.5 : mode == 1 ? 4.0 : 5.0;
        damageGuardEnemy(guardEnemy, damage);

        Vector push = guardEnemy.getLocation().toVector().subtract(hand.getLocation().toVector());
        push.setY(0.0);
        if (push.lengthSquared() < 1.0e-8) push = new Vector(1.0, 0.0, 0.0);
        push.normalize().multiply(mode == 0 ? 0.95 : mode == 1 ? 0.60 : 0.35);
        push.setY(mode == 2 ? 0.25 : 0.35);
        guardEnemy.setVelocity(push);

        if (mode == 2) playGuardMiniSmash(hand, guardEnemy.getLocation());
        else TrueGodEffects.guardHit(guardOwner, mode);

        guardAttackIndex++;
        if (guardEnemy.isDead() || !guardEnemy.isValid()) {
            guardEnemy = null;
            hand.setBaseColor(HandPalette.EMERALD);
            phase = Phase.GUARD_ORBIT;
            hand.actionStopMotion();
            return;
        }

        hand.setBaseColor(HandPalette.EMERALD);
        hand.actionAnimatePose(HandPose.RELAXED, 6, EasingCurve.EASE_OUT);
        phase = Phase.GUARD_COOLDOWN;
        phaseTicksRemaining = 9;
    }

    private void tickGuardCooldown(ParticleHand hand) {
        if (guardEnemy == null || guardEnemy.isDead() || !guardEnemy.isValid()) {
            guardEnemy = null;
            phase = Phase.GUARD_ORBIT;
            return;
        }
        if (--phaseTicksRemaining > 0) {
            hand.actionLookAt(guardEnemy.getLocation(), 30.0);
            return;
        }
        beginGuardAttack(hand);
    }

    private boolean guardEnemyStillValid(ParticleHand hand) {
        if (guardEnemy != null && isGuardEligible(guardEnemy) && !guardEnemy.isDead() && guardEnemy.isValid()
                && guardEnemy.getWorld() != null && guardEnemy.getWorld().equals(hand.getWorld())) return true;
        guardEnemy = null;
        hand.setBaseColor(HandPalette.EMERALD);
        hand.actionStopMotion();
        phase = Phase.GUARD_ORBIT;
        return false;
    }

    private static boolean isGuardEligible(LivingEntity enemy) {
        return enemy != null && !(enemy instanceof Wither) && !(enemy instanceof EnderDragon);
    }

    private static Vector horizontalPerpendicular(Vector vector) {
        Vector flat = vector == null ? new Vector(0.0, 0.0, 1.0) : vector.clone();
        flat.setY(0.0);
        if (flat.lengthSquared() < 1.0e-8) flat = new Vector(0.0, 0.0, 1.0);
        flat.normalize();
        return new Vector(-flat.getZ(), 0.0, flat.getX()).normalize();
    }

    private void damageGuardEnemy(LivingEntity enemy, double damage) {
        if (enemy instanceof Player player) HandDeathMessages.damage(player, damage, HandDeathMessages.Style.GUARD);
        else enemy.damage(damage);
    }

    private void playGuardMiniSmash(ParticleHand hand, Location impact) {
        if (impact == null || impact.getWorld() == null) return;
        impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 4, 0.45, 0.25, 0.45, 0.0, null, hand.isForceParticles());
        impact.getWorld().spawnParticle(Particle.CLOUD, impact, 28, 0.65, 0.28, 0.65, 0.08, null, hand.isForceParticles());
        impact.getWorld().playSound(impact, "minecraft:entity.generic.explode", 0.85f, 1.28f);
        TrueGodEffects.guardHit(guardOwner, 2);
    }

    // ---------------------------------------------------------------------
    // Launch - grab, lift above canopy, throw far
    // ---------------------------------------------------------------------

    private void tickTossAscend(ParticleHand hand) {
        if (target == null || !target.isOnline() || target.isDead() || tossDestination == null) {
            cancel(hand, "toss target unavailable", true);
            return;
        }
        Location desiredOrigin = gripSolver.handOriginForPlayerFeetDestination(hand, target, tossDestination);
        double distance = hand.getLocation().distance(desiredOrigin);
        hand.actionSteerTo(desiredOrigin, clamp(1.20 + distance * 0.055, 1.35, 3.3), clamp(0.12 + distance * 0.006, 0.14, 0.34));
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        if (gripSolver.playerFeetLocation(hand, target).distance(tossDestination) > 1.25) return;

        hand.actionStopMotion();
        beginVisibleThrowSwing(hand,
                tossThrowDirection == null ? gripSolver.worldFingerDirection(hand) : tossThrowDirection,
                3.25, 1.20, 8, HandActionType.TOSS);
        phase = Phase.THROW_SWING_BACK;
    }

    // Retained for backwards state compatibility; routes Launch into
    // the shared THROW_SWING_BACK/FORWARD animation instead.
    private void tickTossThrow(ParticleHand hand) {
        beginVisibleThrowSwing(hand,
                tossThrowDirection == null ? gripSolver.worldFingerDirection(hand) : tossThrowDirection,
                3.25, 1.20, 8, HandActionType.TOSS);
    }

    // ---------------------------------------------------------------------
    // Judgment - surface-only aerial index laser
    // ---------------------------------------------------------------------

    private void tickJudgmentApproach(ParticleHand hand) {
        if (!isSurfacePlayer(target)) {
            stopJudgmentForSurfaceLoss(hand);
            return;
        }
        hand.actionPointAt(target, 18.0);
        if (hand.isMoving() || hand.isAnimating()) return;
        phase = Phase.JUDGMENT_WINDUP;
        phaseTicksRemaining = 18;
        TrueGodEffects.judgmentCharge(target);
    }

    private void tickJudgmentWindup(ParticleHand hand) {
        if (!isSurfacePlayer(target)) {
            stopJudgmentForSurfaceLoss(hand);
            return;
        }
        hand.actionPointAt(target, 24.0);
        if (--phaseTicksRemaining > 0) return;
        phase = Phase.JUDGMENT_STRIKE;
        judgmentBeamTick = 0;
        lastStopReason = "judgment beam active";
    }

    private void tickJudgmentStrike(ParticleHand hand) {
        if (target == null || !target.isOnline() || target.isDead()) {
            hand.actionStopMotion();
            hand.actionStopLooking();
            finish("judgment target dead");
            return;
        }
        if (!isSurfacePlayer(target)) {
            stopJudgmentForSurfaceLoss(hand);
            return;
        }

        // Circle high around the moving player. The candidate Y is corrected
        // against a foliage-inclusive heightmap so the visible Hand does not
        // cut through hills, roofs or tree canopies while the beam tracks.
        judgmentOrbitAngle += Math.toRadians(2.35);
        Location desired = judgmentOrbitPoint(hand, target, judgmentOrbitAngle, actionHeight);
        double distance = hand.getLocation().distance(desired);
        hand.actionSteerTo(desired, clamp(1.05 + distance * 0.055, 1.20, 2.80), clamp(0.09 + distance * 0.006, 0.11, 0.28));
        hand.actionPointAt(target, 20.0);

        Location start = gripSolver.worldFingerTip(hand, HandDigit.INDEX);
        Location end = upperTorso(target);
        renderJudgmentLaser(hand, start, end);

        judgmentBeamTick++;
        if (judgmentBeamTick % 5 == 0) {
            target.setNoDamageTicks(0);
            DamageSource source = DamageSource.builder(DamageType.MAGIC)
                    .withDamageLocation(start)
                    .build();
            HandDeathMessages.damage(target, 3.5, source, HandDeathMessages.Style.JUDGMENT);
            lastImpactHitCount++;
            if (!judgmentImpactPlayed) {
                judgmentImpactPlayed = true;
                TrueGodEffects.judgmentBeamHit(target);
            }
        }
    }

    private void stopJudgmentForSurfaceLoss(ParticleHand hand) {
        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.RELAXED, 14, EasingCurve.EASE_OUT);
        finish("judgment stopped: target left surface");
    }

    // ---------------------------------------------------------------------
    // ForceSlap - former swept Judgment charge
    // ---------------------------------------------------------------------

    private void tickForceSlapApproach(ParticleHand hand) {
        if (hand.isMoving()) return;
        phase = Phase.FORCE_SLAP_WINDUP;
        phaseTicksRemaining = 10;
        hand.actionLookAt(target, 180.0);
        TrueGodEffects.forceSlapCharge(target);
    }

    private void tickForceSlapWindup(ParticleHand hand) {
        if (--phaseTicksRemaining > 0) return;
        lockedImpact = upperTorso(target);
        Vector direction = lockedImpact.toVector().subtract(hand.getLocation().toVector());
        if (direction.lengthSquared() < 1.0e-8) direction = new Vector(0.0, 0.0, 1.0);
        judgmentDirection = direction.normalize();

        hand.actionStopLooking();
        hand.actionAnimatePose(HandPose.OPEN, Math.max(4, Math.min(8, secondaryTicks)), EasingCurve.EASE_OUT);
        double passThrough = Math.max(6.0, hand.getScale() * 1.75);
        Location end = lockedImpact.clone().add(judgmentDirection.clone().multiply(passThrough));
        judgmentPreviousOrigin = hand.getLocation();
        hand.actionTravelTo(end, secondaryTicks, EasingCurve.EASE_IN);
        phase = Phase.FORCE_SLAP_STRIKE;
    }

    private void tickForceSlapStrike(ParticleHand hand) {
        Location currentOrigin = hand.getLocation();
        Location previousOrigin = judgmentPreviousOrigin == null ? currentOrigin : judgmentPreviousOrigin;
        int newHits = combatResolver.resolveDirectionalSweptContact(
                hand, previousOrigin, currentOrigin,
                Math.max(1.0, hand.getSlamDamage() * 0.85),
                judgmentDirection,
                Math.max(0.6, hand.getSlamHorizontalKnockback() * 1.15),
                Math.max(0.15, hand.getSlamVerticalKnockback() * 0.45),
                actionHitPlayers, 0.09
        );
        judgmentPreviousOrigin = currentOrigin;
        lastImpactHitCount = actionHitPlayers.size();
        if (newHits > 0 && !judgmentImpactPlayed) {
            judgmentImpactPlayed = true;
            combatResolver.playImpactEffects(hand);
        }
        if (hand.isMoving()) return;

        boolean targetHit = target != null && actionHitPlayers.contains(target.getUniqueId());
        TrueGodEffects.forceSlapImpact(target, targetHit);
        Location recover = hand.getLocation().clone()
                .subtract(judgmentDirection.clone().multiply(Math.max(2.5, hand.getScale() * 0.8)))
                .add(0.0, 1.5, 0.0);
        hand.actionTravelTo(recover, recoilTicks, EasingCurve.EASE_OUT);
        phase = Phase.FORCE_SLAP_RECOVER;
    }

    private void tickForceSlapRecover(ParticleHand hand) {
        if (hand.isMoving()) return;
        finish("forceslap complete; hits=" + lastImpactHitCount);
    }

    // ---------------------------------------------------------------------
    // Punch - palm-down fist, heavy damage + huge knockback
    // ---------------------------------------------------------------------

    private void tickPunchApproach(ParticleHand hand) {
        if (hand.isMoving()) return;
        phase = Phase.PUNCH_WINDUP;
        phaseTicksRemaining = 8;
        hand.actionDownLookAt(target, 180.0);
        hand.actionAnimatePose(HandPose.FIST, 5, EasingCurve.EASE_IN);
        TrueGodEffects.punchCharge(target);
    }

    private void tickPunchWindup(ParticleHand hand) {
        if (--phaseTicksRemaining > 0) return;
        lockedImpact = upperTorso(target);
        Vector direction = lockedImpact.toVector().subtract(hand.getLocation().toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() < 1.0e-8) {
            direction = gripSolver.worldFingerDirection(hand);
            direction.setY(0.0);
        }
        if (direction.lengthSquared() < 1.0e-8) direction = new Vector(1.0, 0.0, 0.0);
        punchDirection = direction.normalize();

        hand.actionStopLooking();
        double passThrough = Math.max(5.0, hand.getScale() * 1.40);
        Location end = lockedImpact.clone().add(punchDirection.clone().multiply(passThrough));
        punchPreviousOrigin = hand.getLocation();
        hand.actionTravelTo(end, secondaryTicks, EasingCurve.EASE_IN);
        phase = Phase.PUNCH_STRIKE;
    }

    private void tickPunchStrike(ParticleHand hand) {
        Location current = hand.getLocation();
        Location previous = punchPreviousOrigin == null ? current : punchPreviousOrigin;
        int newHits = combatResolver.resolveHeavyDirectionalSweptContact(
                hand,
                previous,
                current,
                punchDamage,
                punchMinimumHealthLoss,
                punchDirection,
                punchHorizontalKnockback,
                punchVerticalKnockback,
                actionHitPlayers,
                0.11
        );
        punchPreviousOrigin = current;
        lastImpactHitCount = actionHitPlayers.size();
        if (newHits > 0 && !punchImpactPlayed) {
            punchImpactPlayed = true;
            combatResolver.playHeavyImpactEffects(hand);
            TrueGodEffects.punchImpact(target, target != null && actionHitPlayers.contains(target.getUniqueId()));
        }
        if (hand.isMoving()) return;

        if (!punchImpactPlayed) {
            TrueGodEffects.punchImpact(target, target != null && actionHitPlayers.contains(target.getUniqueId()));
        }
        Location recover = hand.getLocation().clone()
                .subtract(punchDirection.clone().multiply(Math.max(3.0, hand.getScale())))
                .add(0.0, 1.0, 0.0);
        hand.actionTravelTo(recover, recoilTicks, EasingCurve.EASE_OUT);
        phase = Phase.PUNCH_RECOVER;
    }

    private void tickPunchRecover(ParticleHand hand) {
        if (hand.isMoving()) return;
        finish("punch complete; hits=" + lastImpactHitCount);
    }

    // ---------------------------------------------------------------------
    // Slap - open palm, deliberately low damage + extreme lateral knockback
    // ---------------------------------------------------------------------

    private void tickSlapApproach(ParticleHand hand) {
        if (hand.isMoving()) return;
        phase = Phase.SLAP_WINDUP;
        phaseTicksRemaining = 7;
        hand.actionLookAt(target, 180.0);
        hand.actionAnimatePose(HandPose.OPEN, 5, EasingCurve.EASE_OUT);
    }

    private void tickSlapWindup(ParticleHand hand) {
        if (--phaseTicksRemaining > 0) return;

        Location torso = upperTorso(target);
        Vector right = targetRight(target);
        slapDirection = right.multiply(-1.0).normalize();
        hand.actionStopLooking();
        slapPreviousOrigin = hand.getLocation();

        double passThrough = Math.max(actionHeight + 5.0, hand.getScale() * 2.2);
        Location end = torso.clone().add(slapDirection.clone().multiply(passThrough));
        hand.actionTravelTo(end, secondaryTicks, EasingCurve.EASE_IN_OUT);
        phase = Phase.SLAP_STRIKE;
    }

    private void tickSlapStrike(ParticleHand hand) {
        Location current = hand.getLocation();
        Location previous = slapPreviousOrigin == null ? current : slapPreviousOrigin;
        int newHits = combatResolver.resolveDirectionalSweptContact(
                hand, previous, current, slapDamage, slapDirection,
                slapHorizontalKnockback, slapVerticalKnockback,
                actionHitPlayers, 0.10
        );
        slapPreviousOrigin = current;
        lastImpactHitCount = actionHitPlayers.size();

        if (newHits > 0 && !slapImpactPlayed) {
            slapImpactPlayed = true;
            combatResolver.playImpactEffects(hand);
            TrueGodEffects.slapImpact(target, target != null && actionHitPlayers.contains(target.getUniqueId()));
        }
        if (hand.isMoving()) return;

        Location recover = hand.getLocation().clone()
                .subtract(slapDirection.clone().multiply(Math.max(2.5, hand.getScale() * 0.8)))
                .add(0.0, 0.8, 0.0);
        hand.actionTravelTo(recover, recoilTicks, EasingCurve.EASE_OUT);
        phase = Phase.SLAP_RECOVER;
    }

    private void tickSlapRecover(ParticleHand hand) {
        if (hand.isMoving()) return;
        hand.requestRemoval();
        finish("slap complete; hits=" + lastImpactHitCount + "; hand dismissed");
    }

    // ---------------------------------------------------------------------
    // Cyclone - long visible orbit + extreme spin + charge
    // ---------------------------------------------------------------------

    private void tickCycloneApproach(ParticleHand hand) {
        if (hand.isMoving()) return;
        phase = Phase.CYCLONE_WINDUP;
        phaseTicksRemaining = 90; // 4.5 seconds showcasing the orbit/spin
        hand.actionOrbit(target, Math.max(7.0, actionHeight * 0.82), 3.25, 2.6, 1.35, 0.11);
        hand.actionLookAt(target, 18.0);
        hand.actionSetRollVelocity(58.0); // 1160 degrees/second at 20 TPS
        TrueGodEffects.cycloneCharge(target);
    }

    private void tickCycloneWindup(ParticleHand hand) {
        // Keep the fist visibly orbiting while its roll is already extreme.
        hand.actionLookAt(target, 18.0);
        if (--phaseTicksRemaining > 0) return;

        hand.actionStopMotion();
        Location torso = upperTorso(target);
        Vector direction = torso.toVector().subtract(hand.getLocation().toVector());
        if (direction.lengthSquared() < 1.0e-8) direction = new Vector(0.0, 0.0, 1.0);
        cycloneDirection = direction.normalize();
        cyclonePreviousOrigin = hand.getLocation();
        hand.actionStopLooking();
        hand.actionSetRollVelocity(72.0);

        double passThrough = Math.max(10.0, hand.getScale() * 3.0);
        Location end = torso.clone().add(cycloneDirection.clone().multiply(passThrough));
        hand.actionTravelTo(end, secondaryTicks, EasingCurve.EASE_IN_OUT);
        phase = Phase.CYCLONE_STRIKE;
    }

    private void tickCycloneStrike(ParticleHand hand) {
        Location current = hand.getLocation();
        Location previous = cyclonePreviousOrigin == null ? current : cyclonePreviousOrigin;
        int newHits = combatResolver.resolveDirectionalSweptContact(
                hand, previous, current, 18.0, cycloneDirection,
                2.85, 0.78, actionHitPlayers, 0.14
        );
        cyclonePreviousOrigin = current;
        lastImpactHitCount = actionHitPlayers.size();
        if (newHits > 0 && !cycloneImpactPlayed) {
            cycloneImpactPlayed = true;
            combatResolver.playImpactEffects(hand);
            TrueGodEffects.cycloneImpact(target);
        }
        if (hand.isMoving()) return;

        hand.actionSetRollVelocity(28.0);
        Location recover = hand.getLocation().clone()
                .subtract(cycloneDirection.clone().multiply(Math.max(3.0, hand.getScale())))
                .add(0.0, 1.2, 0.0);
        hand.actionTravelTo(recover, recoilTicks, EasingCurve.EASE_OUT);
        phase = Phase.CYCLONE_RECOVER;
    }

    private void tickCycloneRecover(ParticleHand hand) {
        if (hand.isMoving()) return;
        hand.actionStopRootSpin();
        finish("cyclone complete; hits=" + lastImpactHitCount);
    }

    // ---------------------------------------------------------------------
    // Breach - interior manifestation for bunker/underground targets
    // ---------------------------------------------------------------------

    private void tickBreachHunt(ParticleHand hand) {
        updateAdaptivePresenceScale(hand, target, 1.45, Math.min(2.20, adaptiveBaseScale), false);

        Location rest = bunkerManifestPoint(hand, target);
        Location torso = upperTorso(target);
        Vector outward = rest.toVector().subtract(torso.toVector());
        if (outward.lengthSquared() < 1.0e-8) outward = horizontalFacing(target);
        outward.normalize();

        int cycle = breachPulseTick % 42;
        Location desired;
        if (cycle < 10) {
            // Visible backswing before each cave strike.
            desired = rest.clone().add(outward.clone().multiply(0.85)).add(0.0, 0.30, 0.0);
            if (cycle == 0 && !hand.isAnimating()) {
                hand.actionAnimatePose(HandPose.RELAXED, 5, EasingCurve.EASE_OUT);
            }
        } else if (cycle < 19) {
            // Quick forward claw lunge. Keep the root outside the player's body;
            // the articulated fingers are the striking surface.
            desired = rest.clone().subtract(outward.clone().multiply(1.15));
            if (cycle == 10) {
                hand.actionAnimatePose(HandPose.CLAW, 5, EasingCurve.EASE_IN);
            }
        } else {
            desired = rest;
        }

        double distance = hand.getLocation().distance(desired);
        double maxSpeed = cycle >= 10 && cycle < 19 ? 2.35 : 1.30;
        double acceleration = cycle >= 10 && cycle < 19 ? 0.42 : 0.16;
        hand.actionSteerTo(desired,
                clamp(maxSpeed + distance * 0.055, maxSpeed, 3.25),
                clamp(acceleration + distance * 0.008, acceleration, 0.56));
        hand.actionLookAt(target, 20.0);

        // One physical-looking hit per lunge. No block/world explosion calls.
        if (cycle == 18 && target != null && target.isOnline() && !target.isDead()) {
            target.setNoDamageTicks(0);
            HandDeathMessages.damage(target, 3.0, HandDeathMessages.Style.BREACH);
            Location hit = upperTorso(target);
            World world = hit.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.SWEEP_ATTACK, hit, 6, 0.45, 0.35, 0.45, 0.0, null, hand.isForceParticles());
                world.spawnParticle(Particle.CLOUD, hit, 18, 0.50, 0.28, 0.50, 0.08, null, hand.isForceParticles());
                world.playSound(hit, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.35f, 0.72f);
                world.playSound(hit, org.bukkit.Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.55f, 0.85f);
            }
        }

        breachPulseTick++;
        if (--phaseTicksRemaining > 0) return;

        restoreAdaptiveScale(hand);
        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionAnimatePose(HandPose.RELAXED, 12, EasingCurve.EASE_OUT);
        finish("breach complete");
    }

    // ---------------------------------------------------------------------
    // Destructive Smash - live tracked player or fixed point
    // ---------------------------------------------------------------------

    private void tickSmashApproach(ParticleHand hand) {
        Location impact = currentSmashPoint();
        if (impact == null) {
            cancel(hand, "smash target unavailable", true);
            return;
        }
        if (smashTargetPlayer != null) hand.actionDownLookAt(smashTargetPlayer, 24.0);
        else hand.actionDownLookAt(impact, 24.0);

        Location staging = smashStaging(impact, actionHeight);
        double distance = hand.getLocation().distance(staging);
        hand.actionSteerTo(staging,
                clamp(1.15 + distance * 0.045, 1.25, 2.6),
                clamp(0.11 + distance * 0.008, 0.12, 0.28));

        phaseTicksRemaining--;
        if (distance > Math.max(0.8, hand.getScale() * 0.16) && phaseTicksRemaining > 0) return;

        hand.actionStopMotion();
        phase = Phase.SMASH_WINDUP;
        phaseTicksRemaining = 10;
        hand.actionAnimatePose(HandPose.FIST, 7, EasingCurve.EASE_IN);
        TrueGodEffects.smashCharge(smashTargetPlayer, impact);
    }

    private void tickSmashWindup(ParticleHand hand) {
        Location impact = currentSmashPoint();
        if (impact == null) {
            cancel(hand, "smash target unavailable", true);
            return;
        }
        // Even during the tell, a player target can keep moving; the hand keeps
        // its overhead alignment rather than locking early.
        Location staging = smashStaging(impact, actionHeight);
        hand.actionSteerTo(staging, 1.8, 0.22);
        if (--phaseTicksRemaining > 0) return;

        hand.actionStopMotion();
        phase = Phase.SMASH_STRIKE;
        phaseTicksRemaining = 34;
    }

    private void tickSmashStrike(ParticleHand hand) {
        Location impact = currentSmashPoint();
        if (impact == null) {
            cancel(hand, "smash target unavailable", true);
            return;
        }
        if (smashTargetPlayer != null) hand.actionDownLookAt(smashTargetPlayer, 35.0);
        else hand.actionDownLookAt(impact, 35.0);

        Location desiredRoot = smashStrikeOrigin(hand, impact);
        double distance = hand.getLocation().distance(desiredRoot);
        hand.actionSteerTo(desiredRoot,
                clamp(2.8 + distance * 0.08, 3.0, 5.4),
                clamp(0.48 + distance * 0.025, 0.52, 1.05));

        boolean targetContact = smashTargetPlayer != null && combatResolver.intersectsPlayerPadded(hand, smashTargetPlayer, 0.12);
        if (!targetContact && distance > Math.max(0.45, hand.getScale() * 0.10) && --phaseTicksRemaining > 0) return;

        hand.actionStopMotion();
        hand.actionStopLooking();
        int hitCount = combatResolver.resolveDestructiveSmash(hand, impact, smashExplosionPower, smashTargetPlayer);
        lastImpactHitCount = hitCount;
        TrueGodEffects.smashImpact(smashTargetPlayer, impact);

        Location recoil = hand.getLocation().clone().add(0.0, Math.max(5.0, actionHeight * 0.65), 0.0);
        hand.actionTravelTo(recoil, recoilTicks, EasingCurve.EASE_OUT);
        phase = Phase.SMASH_RECOVER;
    }

    private void tickSmashRecover(ParticleHand hand) {
        if (hand.isMoving()) return;
        finish("smash complete; hits=" + lastImpactHitCount);
    }

    // ---------------------------------------------------------------------
    // stalking: persistent range reacquisition + true finger pointing
    // ---------------------------------------------------------------------

    private void tickStalkWatch(ParticleHand hand) {
        updateAdaptivePresenceScale(hand, target, 1.60, adaptiveBaseScale, false);
        if (stalkNeedsReacquire(hand)) {
            enterStalkReacquire(hand);
            return;
        }
        if (--phaseTicksRemaining > 0) return;
        chooseNextStalkState(hand);
    }

    private void tickStalkDistantPoint(ParticleHand hand) {
        updateAdaptivePresenceScale(hand, target, 1.60, adaptiveBaseScale, false);
        if (stalkNeedsReacquire(hand)) {
            enterStalkReacquire(hand);
            return;
        }
        // FINGER_POINT is live, so even while the victim moves the index remains
        // aimed at their actual body rather than merely facing the palm at them.
        hand.actionPointAt(target, 8.0);
        if (--phaseTicksRemaining > 0) return;
        enterStalkWatch(hand, false);
    }

    private void tickStalkMotion(ParticleHand hand) {
        updateAdaptivePresenceScale(hand, target, 1.60, adaptiveBaseScale, false);
        if (stalkNeedsReacquire(hand)) {
            enterStalkReacquire(hand);
            return;
        }
        if (--phaseTicksRemaining > 0) return;
        enterStalkWatch(hand, false);
    }

    private void tickStalkChase(ParticleHand hand) {
        updateAdaptivePresenceScale(hand, target, 1.55, adaptiveBaseScale, false);
        // The stalk chase is now the same hovering, palm-down claw behavior as
        // /gh attack chase, only time-limited before returning to WATCH.
        steerHoverClaw(hand, target, 5.2, 2.4, 1.45, 0.14);
        if (--phaseTicksRemaining > 0) return;
        enterStalkWatch(hand, false);
    }

    private void tickStalkReacquire(ParticleHand hand) {
        updateAdaptivePresenceScale(hand, target, 1.60, adaptiveBaseScale, false);
        Location desired = stalkReacquirePoint(target);
        double distance = hand.getLocation().distance(desired);
        hand.actionSteerTo(desired,
                clamp(1.8 + distance * 0.045, 2.2, 4.6),
                clamp(0.18 + distance * 0.006, 0.22, 0.62));
        hand.actionDownLookAt(target, 14.0);
        if (distance <= 7.0 || --phaseTicksRemaining <= 0) {
            enterStalkWatch(hand, false);
        }
    }

    private void tickHoverChase(ParticleHand hand) {
        updateAdaptivePresenceScale(hand, target, 1.55, adaptiveBaseScale, false);
        double confined = hand.getScale() < Math.max(2.25, adaptiveBaseScale * 0.84) ? 1.0 : 0.0;
        steerHoverClaw(hand, target,
                confined > 0.0 ? 2.35 : 5.0,
                confined > 0.0 ? 2.55 : 2.2,
                confined > 0.0 ? 1.35 : 1.70,
                confined > 0.0 ? 0.13 : 0.16);
    }

    private void chooseNextStalkState(ParticleHand hand) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int roll = random.nextInt(100);
        if (roll < 32) enterStalkWatch(hand, false);
        else if (roll < 56) enterStalkDistantPoint(hand, random);
        else if (roll < 75) enterStalkOrbit(hand, random);
        else if (roll < 87) enterStalkFollow(hand, random);
        else enterStalkChase(hand, random);
    }

    private void enterStalkWatch(ParticleHand hand, boolean initial) {
        hand.actionStopMotion();
        hand.actionLookAt(target, initial ? 10.0 : 5.5);
        hand.actionAnimatePose(HandPose.RELAXED, 20, EasingCurve.SMOOTH);
        phase = Phase.STALK_WATCH;
        phaseTicksRemaining = ThreadLocalRandom.current().nextInt(initial ? 120 : 150, initial ? 241 : 321);
    }

    private void enterStalkDistantPoint(ParticleHand hand, ThreadLocalRandom random) {
        Location far = distantStalkPoint(target, random);
        int travelTicks = random.nextInt(34, 66);
        hand.actionTravelTo(far, travelTicks, EasingCurve.EASE_IN_OUT);
        hand.actionPointAt(target, 8.0);
        hand.actionAnimatePose(HandPose.POINT, 24, EasingCurve.EASE_IN_OUT);
        phase = Phase.STALK_DISTANT_POINT;
        phaseTicksRemaining = random.nextInt(180, 341);
    }

    private void enterStalkOrbit(ParticleHand hand, ThreadLocalRandom random) {
        double radius = random.nextDouble(4.2, 7.2);
        double degrees = random.nextDouble(0.38, 0.82) * (random.nextBoolean() ? 1.0 : -1.0);
        double height = random.nextDouble(1.2, 4.2);
        hand.actionOrbit(target, radius, degrees, height, random.nextDouble(0.48, 0.76), random.nextDouble(0.035, 0.065));
        hand.actionLookAt(target, 6.5);
        hand.actionAnimatePose(HandPose.RELAXED, 18, EasingCurve.SMOOTH);
        phase = Phase.STALK_ORBIT;
        phaseTicksRemaining = random.nextInt(160, 301);
    }

    private void enterStalkFollow(ParticleHand hand, ThreadLocalRandom random) {
        double distance = random.nextDouble(7.5, 13.5);
        double height = random.nextDouble(2.0, 6.0);
        hand.actionFollow(target, distance, height, random.nextDouble(0.48, 0.74), random.nextDouble(0.035, 0.065));
        hand.actionLookAt(target, 7.0);
        hand.actionAnimatePose(HandPose.OPEN, 22, EasingCurve.EASE_IN_OUT);
        phase = Phase.STALK_FOLLOW;
        phaseTicksRemaining = random.nextInt(140, 261);
    }

    private void enterStalkChase(ParticleHand hand, ThreadLocalRandom random) {
        hand.actionStopMotion();
        hand.actionDownLookAt(target, 12.0);
        hand.actionAnimatePose(HandPose.CLAW, 14, EasingCurve.EASE_IN);
        phase = Phase.STALK_CHASE;
        phaseTicksRemaining = random.nextInt(62, 112);
        TrueGodEffects.stalkChase(target);
        tickStalkChase(hand);
    }

    private void enterStalkReacquire(ParticleHand hand) {
        hand.actionStopMotion();
        hand.actionDownLookAt(target, 14.0);
        hand.actionAnimatePose(HandPose.RELAXED, 12, EasingCurve.EASE_OUT);
        phase = Phase.STALK_REACQUIRE;
        phaseTicksRemaining = 160;
        tickStalkReacquire(hand);
    }

    private boolean stalkNeedsReacquire(ParticleHand hand) {
        return hand.getLocation().distanceSquared(target.getLocation()) > 100.0 * 100.0;
    }

    private void steerHoverClaw(ParticleHand hand, Player target, double height, double behind,
                                double baseMaxSpeed, double baseAcceleration) {
        Location desired = hoverChasePoint(target, height, behind);
        double distance = hand.getLocation().distance(desired);
        hand.actionSteerTo(desired,
                clamp(baseMaxSpeed + distance * 0.045, baseMaxSpeed, 4.2),
                clamp(baseAcceleration + distance * 0.005, baseAcceleration, 0.55));
        hand.actionDownLookAt(target, 13.0);
    }

    private void tickReleasing(ParticleHand hand) {
        if (hand.isAnimating()) return;
        finish("release complete");
    }

    private void beginVisibleThrowSwing(
            ParticleHand hand,
            Vector desiredDirection,
            double forwardSpeed,
            double upwardSpeed,
            int openTicks,
            HandActionType source
    ) {
        if (target == null || !target.isOnline() || !gripCarrier.isActive()) return;
        Vector direction = desiredDirection == null ? gripSolver.worldFingerDirection(hand) : desiredDirection.clone();
        if (direction.lengthSquared() < 1.0e-8) direction = new Vector(0.0, 0.0, 1.0);
        direction.normalize();

        throwSwingDirection = direction;
        throwSwingForwardSpeed = forwardSpeed;
        throwSwingUpwardSpeed = upwardSpeed;
        throwSwingOpenTicks = openTicks;
        throwSwingSource = source;
        throwSwingAnchor = hand.getLocation();

        hand.actionStopMotion();
        hand.actionStopLooking();
        hand.actionCancelAnimation();
        hand.actionAnimatePose(HandPose.CLAW, 5, EasingCurve.EASE_IN_OUT);
        Location backAim = throwSwingAnchor.clone().subtract(direction.clone().multiply(14.0)).add(0.0, -3.0, 0.0);
        hand.actionPointAt(backAim, 48.0);
        hand.actionTravelTo(throwSwingAnchor.clone().subtract(direction.clone().multiply(2.2)).add(0.0, -0.9, 0.0),
                10, EasingCurve.EASE_OUT);
        phase = Phase.THROW_SWING_BACK;
        phaseTicksRemaining = 10;
        lastStopReason = source == HandActionType.TOSS ? "toss throw backswing" : "throw backswing";
    }

    private void tickThrowSwingBack(ParticleHand hand) {
        if (target == null || !target.isOnline() || target.isDead()) {
            cancel(hand, "throw target unavailable", true);
            return;
        }
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        if (hand.isMoving() || --phaseTicksRemaining > 0) return;

        Vector direction = throwSwingDirection == null ? gripSolver.worldFingerDirection(hand) : throwSwingDirection.clone();
        if (direction.lengthSquared() < 1.0e-8) direction = new Vector(0.0, 0.0, 1.0);
        direction.normalize();
        Location forwardAim = throwSwingAnchor.clone().add(direction.clone().multiply(20.0)).add(0.0, 5.0, 0.0);
        hand.actionPointAt(forwardAim, 75.0);
        hand.actionTravelTo(throwSwingAnchor.clone().add(direction.clone().multiply(3.0)).add(0.0, 0.85, 0.0),
                9, EasingCurve.EASE_IN);
        phase = Phase.THROW_SWING_FORWARD;
        phaseTicksRemaining = 9;
        lastStopReason = "throw forward swing";
    }

    private void tickThrowSwingForward(ParticleHand hand) {
        if (target == null || !target.isOnline() || target.isDead()) {
            cancel(hand, "throw target unavailable", true);
            return;
        }
        gripCarrier.update(target, gripSolver.playerFeetLocation(hand, target));
        if (hand.isMoving() || --phaseTicksRemaining > 0) return;

        Player thrown = target;
        Location releaseFeet = gripSolver.playerFeetLocation(hand, thrown);
        gripCarrier.release(thrown, releaseFeet);

        // Throw in the direction the fingers are visibly facing at release.
        Vector direction = gripSolver.worldFingerDirection(hand);
        if (direction.lengthSquared() < 1.0e-8) direction = throwSwingDirection == null ? new Vector(0.0, 0.0, 1.0) : throwSwingDirection.clone();
        direction.normalize().multiply(throwSwingForwardSpeed);
        direction.add(new Vector(0.0, throwSwingUpwardSpeed, 0.0));
        thrown.setVelocity(direction);
        HandDeathMessages.markThrow(thrown,
                throwSwingSource == HandActionType.TOSS ? HandDeathMessages.Style.TOSS : HandDeathMessages.Style.THROW);
        if (throwSwingSource == HandActionType.TOSS) TrueGodEffects.tossThrow(thrown);
        else TrueGodEffects.release(thrown);

        target = null;
        tossDestination = null;
        hand.actionStopLooking();
        hand.actionAnimatePose(HandPose.OPEN, throwSwingOpenTicks, EasingCurve.EASE_OUT);
        phase = Phase.THROW_OPEN;
        phaseTicksRemaining = throwSwingOpenTicks;
        lastStopReason = throwSwingSource == HandActionType.TOSS ? "toss released" : "throw released";
    }

    private void tickThrowOpen(ParticleHand hand) {
        if (hand.isAnimating()) return;
        String reason = throwSwingSource == HandActionType.TOSS ? "toss complete" : "throw complete";
        finish(reason);
    }

    // ---------------------------------------------------------------------
    // Grip/debug getters
    // ---------------------------------------------------------------------

    public Player getHeldPlayer() {
        return isHolding() ? target : null;
    }

    public ParticleHand getSecondaryHand() {
        return secondaryHand;
    }

    public ModelPoint getGripLocalPoint(ParticleHand hand) { return gripSolver.localCageCenter(hand); }
    public Location getGripWorldPoint(ParticleHand hand) { return gripSolver.worldCageCenter(hand); }

    /** World-space cage center of whichever Hand is currently carrying the target. */
    public Location getActiveGripWorldPoint(ParticleHand primary) {
        if (!hasActiveGrip()) return null;
        ParticleHand holder = primary;
        if (type == HandActionType.JUGGLE && !jugglePrimaryHolding && secondaryHand != null) {
            holder = secondaryHand;
        }
        return gripSolver.worldCageCenter(holder);
    }

    /**
     * Render from the same logical root used by the action controller.
     *
     * tried shifting the visual Hand toward the rider's
     * instantaneous server position. That creates a feedback wobble because a
     * mounted entity and an ItemDisplay interpolate on different packet paths.
     * The carrier now follows exactly one grip displacement per tick, while the
     * ItemDisplay receives the same one-tick target, so an additional visual
     * correction is both unnecessary and harmful to smoothness.
     */
    public Location getVisualRenderOrigin(ParticleHand primary, ParticleHand queriedHand) {
        if (queriedHand == null) return null;

        // carry sync: while a player is physically mounted, render the
        // gripping hand from the live carrier anchor instead of the logical
        // motion-controller target. The controller can be one server-physics
        // step ahead of an ArmorStand moved by velocity; drawing from that
        // future target is what made the player visibly lag behind the fingers.
        if (hasActiveGrip()) {
            ParticleHand holder = primary;
            if (type == HandActionType.JUGGLE && !jugglePrimaryHolding && secondaryHand != null) {
                holder = secondaryHand;
            }
            if (queriedHand == holder) {
                Location visualFeet = gripCarrier.getVisualFeetAnchor();
                if (visualFeet != null && target != null && visualFeet.getWorld() != null
                        && visualFeet.getWorld().equals(holder.getWorld())) {
                    try {
                        return gripSolver.handOriginForPlayerFeetDestination(holder, target, visualFeet);
                    } catch (IllegalArgumentException ignored) {
                        // Fall through to the logical root if the world changed
                        // during the same tick.
                    }
                }
            }
        }
        return queriedHand.getLocation();
    }

    private void releaseCarrier(ParticleHand hand) {
        if (!gripCarrier.isActive()) return;
        Player held = target;
        Location releaseFeet = null;
        if (held != null && held.isOnline() && !held.isDead()) {
            if (held.getWorld() != null && held.getWorld().equals(hand.getWorld())) releaseFeet = gripSolver.playerFeetLocation(hand, held);
            else releaseFeet = held.getLocation();
        }
        gripCarrier.release(held, releaseFeet);
    }

    private void finish(String reason) {
        resetTransientState();
        type = HandActionType.IDLE;
        phase = Phase.IDLE;
        phaseTicksRemaining = 0;
        lastStopReason = reason;
    }

    private void resetTransientState() {
        target = null;
        lockedImpact = null;
        judgmentOrbitAngle = 0.0;
        judgmentBeamTick = 0;
        judgmentDirection = null;
        judgmentPreviousOrigin = null;
        judgmentImpactPlayed = false;
        punchDirection = null;
        punchPreviousOrigin = null;
        punchImpactPlayed = false;
        slapDirection = null;
        slapPreviousOrigin = null;
        slapImpactPlayed = false;
        cycloneDirection = null;
        cyclonePreviousOrigin = null;
        cycloneImpactPlayed = false;
        breachPulseTick = 0;
        adaptiveBaseScale = 0.0;
        adaptiveScaleManaged = false;
        adaptiveScaleTick = 0;
        tossThrowDirection = null;
        tossDestination = null;
        throwSwingDirection = null;
        throwSwingAnchor = null;
        throwSwingForwardSpeed = 0.0;
        throwSwingUpwardSpeed = 0.0;
        throwSwingOpenTicks = 0;
        throwSwingSource = null;
        giveBirdLightningTick = 0;
        giveBirdAttackMode = false;
        jugglePrimaryAnchor = null;
        juggleSecondaryAnchor = null;
        jugglePrimaryHolding = false;
        juggleCycle = 0;
        juggleWindupDestination = null;
        juggleFlightTick = 0;
        juggleFlightDuration = 0;
        juggleSenderRecoverDelay = 0;
        guardOwner = null;
        guardEnemy = null;
        guardAttackIndex = 0;
        guardAttackTick = 0;
        guardPreviousScale = 0.0;
        guardPreviousColor = null;
        blessingStage = 0;
        blessingDeparture = null;
        sanctuaryDeparture = null;
        sanctuaryPulseTick = 0;
        spankHoldDestination = null;
        spankCount = 0;
        spankLaunchDirection = null;
        rageDashIndex = 0;
        ragePreviousOrigin = null;
        rageDashDirection = null;
        rageLiftDestination = null;
        rageThrowDirection = null;
        rageSwingAnchor = null;
        clapRight = null;
        clapLockedTorso = null;
        clapImpactPlayed = false;
        poundPrimaryTurn = true;
        poundHitCount = 0;
        poundPrimaryRest = null;
        poundSecondaryRest = null;
        poundEnding = false;
        poundWindupStep = 0;
        gestureTick = 0;
        gestureAnchor = null;
        thumbGestureUp = false;
        disposeSecondary();
        slamPreviousOrigin = null;
        smashTargetPlayer = null;
        smashTargetPoint = null;
        actionHitPlayers.clear();
        clearDestinationState();
    }

    private void restoreGuardAppearance(ParticleHand hand) {
        if (hand == null) return;
        if (guardPreviousScale > 0.0 && Double.isFinite(guardPreviousScale)) hand.setScale(guardPreviousScale);
        if (guardPreviousColor != null) hand.setBaseColor(guardPreviousColor);
    }

    private void clearDestinationState() {
        transportDestinationPlayer = null;
        transportDestinationPoint = null;
        transportFinalApproach = false;
        transportDistanceRemaining = 0.0;
        lastPrefetchChunkX = Integer.MIN_VALUE;
        lastPrefetchChunkZ = Integer.MIN_VALUE;
    }

    private boolean prefetchCarryChunk(World world, Location from, Location toward, double remainingDistance) {
        if (world == null || from == null || toward == null || remainingDistance <= 28.0) {
            return true;
        }

        Vector direction = toward.toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 1.0e-8) return true;
        direction.normalize();

        double lookAhead = Math.min(28.0, remainingDistance);
        double x = from.getX() + direction.getX() * lookAhead;
        double z = from.getZ() + direction.getZ() * lookAhead;
        int chunkX = ((int) Math.floor(x)) >> 4;
        int chunkZ = ((int) Math.floor(z)) >> 4;

        if (world.isChunkLoaded(chunkX, chunkZ)) {
            return true;
        }

        if (chunkX != lastPrefetchChunkX || chunkZ != lastPrefetchChunkZ) {
            lastPrefetchChunkX = chunkX;
            lastPrefetchChunkZ = chunkZ;
            world.getChunkAtAsync(chunkX, chunkZ, true, false);
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // surface Judgment helpers
    // ---------------------------------------------------------------------

    public static boolean isSurfacePlayer(Player player) {
        if (player == null || player.getWorld() == null || !player.isOnline() || player.isDead()) return false;
        int top = player.getWorld().getHighestBlockYAt(player.getLocation(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
        // Leaves are ignored so standing under a tree still counts as surface,
        // but a roof/mountain above the player makes the test fail.
        return player.getLocation().getY() >= top - 1.25;
    }

    private static Location judgmentOrbitPoint(ParticleHand hand, Player target, double angle, double radius) {
        Location center = target.getLocation();
        World world = center.getWorld();
        double x = center.getX() + Math.cos(angle) * radius;
        double z = center.getZ() + Math.sin(angle) * radius;
        double clearance = Math.max(5.0, hand.getScale() * 0.95);

        // Sample both the destination column and the midpoint from the Hand's
        // current position. This prevents a fast-running surface target from
        // making the orbit controller cut diagonally through a ridge/tree line
        // while it catches up to the new circle center.
        Location destinationColumn = new Location(world, x, center.getY(), z);
        Location current = hand.getLocation();
        Location midpoint = new Location(world,
                (current.getX() + x) * 0.5,
                center.getY(),
                (current.getZ() + z) * 0.5);
        int destinationTop = world.getHighestBlockYAt(destinationColumn, HeightMap.MOTION_BLOCKING);
        int midpointTop = world.getHighestBlockYAt(midpoint, HeightMap.MOTION_BLOCKING);
        double y = Math.max(center.getY() + 11.0,
                Math.max(destinationTop + clearance, midpointTop + clearance));
        return new Location(world, x, y, z);
    }

    private static void renderJudgmentLaser(ParticleHand hand, Location start, Location end) {
        if (start == null || end == null || start.getWorld() == null || !start.getWorld().equals(end.getWorld())) return;
        Vector delta = end.toVector().subtract(start.toVector());
        double distance = delta.length();
        if (distance < 1.0e-6) return;
        Vector step = delta.multiply(1.0 / distance);
        int samples = Math.max(8, Math.min(72, (int) Math.ceil(distance / 0.58)));
        Particle.DustTransition beam = new Particle.DustTransition(
                Color.fromRGB(70, 225, 255), Color.fromRGB(180, 80, 255), 0.82f);
        World world = start.getWorld();
        double rangeSquared = 224.0 * 224.0;
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            double x = start.getX() + step.getX() * distance * t;
            double y = start.getY() + step.getY() * distance * t;
            double z = start.getZ() + step.getZ() * distance * t;
            for (Player viewer : world.getPlayers()) {
                if (viewer.getLocation().distanceSquared(start) > rangeSquared
                        && viewer.getLocation().distanceSquared(end) > rangeSquared) continue;
                viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, 1,
                        0.0, 0.0, 0.0, 0.0, beam, hand.isForceParticles());
            }
        }
    }

    // ---------------------------------------------------------------------
    // Geometry helpers
    // ---------------------------------------------------------------------


    /**
     * Exact root orientation from desired local +Y/+Z world axes. This is the
     * action-side counterpart to the finger-point solver and avoids
     * letting Euler-roll defaults make dual-hand poses vertical by accident.
     */
    private static void orientBasis(ParticleHand hand, Vector desiredY, Vector desiredZ) {
        Vector yAxis = desiredY.clone();
        if (yAxis.lengthSquared() < 1.0e-10) yAxis = new Vector(0.0, 1.0, 0.0);
        yAxis.normalize();

        Vector zAxis = desiredZ.clone().subtract(yAxis.clone().multiply(desiredZ.dot(yAxis)));
        if (zAxis.lengthSquared() < 1.0e-10) {
            Vector fallback = Math.abs(yAxis.getY()) < 0.92 ? new Vector(0.0, 1.0, 0.0) : new Vector(1.0, 0.0, 0.0);
            zAxis = fallback.subtract(yAxis.clone().multiply(fallback.dot(yAxis)));
        }
        zAxis.normalize();
        Vector xAxis = yAxis.clone().crossProduct(zAxis).normalize();
        zAxis = xAxis.clone().crossProduct(yAxis).normalize();

        double sinPitch = clamp(yAxis.getZ(), -1.0, 1.0);
        double pitch = Math.toDegrees(Math.asin(sinPitch));
        double cosPitch = Math.cos(Math.toRadians(pitch));
        double yaw;
        double roll;
        if (Math.abs(cosPitch) > 1.0e-7) {
            yaw = Math.toDegrees(Math.atan2(xAxis.getZ(), zAxis.getZ()));
            roll = Math.toDegrees(Math.atan2(-yAxis.getX(), yAxis.getY()));
        } else {
            // Gimbal branch: choose a zero-roll representation and solve yaw
            // from the remaining horizontal basis.
            roll = 0.0;
            yaw = Math.toDegrees(Math.atan2(-zAxis.getX(), zAxis.getZ()));
        }
        hand.actionSetRotation(normalizeDegrees(yaw), normalizeDegrees(pitch), normalizeDegrees(roll));
    }

    private static void orientClapHorizontal(ParticleHand hand, Location target, Vector horizontalFingerHint) {
        Vector zAxis = target.toVector().subtract(hand.getLocation().toVector());
        if (zAxis.lengthSquared() < 1.0e-10) zAxis = new Vector(1.0, 0.0, 0.0);
        zAxis.normalize(); // palm +Z faces the player/other palm

        Vector yAxis = horizontalFingerHint == null ? new Vector(0.0, 0.0, 1.0) : horizontalFingerHint.clone();
        yAxis.setY(0.0);
        if (yAxis.lengthSquared() < 1.0e-10) yAxis = new Vector(0.0, 0.0, 1.0);
        yAxis = yAxis.subtract(zAxis.clone().multiply(yAxis.dot(zAxis)));
        if (yAxis.lengthSquared() < 1.0e-10) yAxis = new Vector(-zAxis.getZ(), 0.0, zAxis.getX());
        orientBasis(hand, yAxis.normalize(), zAxis);
    }

    private static void orientFingerToward(ParticleHand hand, Location target) {
        Vector yAxis = target.toVector().subtract(hand.getLocation().toVector());
        if (yAxis.lengthSquared() < 1.0e-10) yAxis = new Vector(0.0, 1.0, 0.0);
        yAxis.normalize();
        Vector down = new Vector(0.0, -1.0, 0.0);
        Vector zAxis = down.clone().subtract(yAxis.clone().multiply(down.dot(yAxis)));
        if (zAxis.lengthSquared() < 1.0e-10) zAxis = new Vector(0.0, 0.0, 1.0);
        orientBasis(hand, yAxis, zAxis);
    }

    private static Location poundRestPoint(Player target, boolean primary) {
        Location torso = upperTorso(target);
        Vector side = targetRight(target).multiply(primary ? 3.8 : -3.8);
        return torso.add(side).add(0.0, 6.2, 0.0);
    }

    private static Location poundBackswingCrestPoint(Player target, boolean primary) {
        Location torso = upperTorso(target);
        Vector side = targetRight(target).multiply(primary ? 5.2 : -5.2);
        Vector back = horizontalFacing(target).multiply(-0.8);
        return torso.add(side).add(back).add(0.0, 7.4, 0.0);
    }

    private static Location poundWindupPoint(Player target, boolean primary) {
        Location torso = upperTorso(target);
        Vector side = targetRight(target).multiply(primary ? 4.6 : -4.6);
        Vector back = horizontalFacing(target).multiply(-3.3);
        return torso.add(side).add(back).add(0.0, 8.2, 0.0);
    }

    private static Location poundStrikePoint(ParticleHand striker, Player target) {
        // The root must stop above the victim because a FIST extends from the
        // wrist/root toward local +Y. Pound points that knuckle axis downward,
        // so this scale-aware clearance lets the actual fist reach the player
        // instead of driving the Hand's palm/root through their body.
        double clearance = Math.max(1.55, striker.getScale() * 0.58);
        return upperTorso(target).add(0.0, clearance, 0.0);
    }

    private void orientPoundFist(ParticleHand hand) {
        // Local +Y runs wrist -> fingers/knuckles. Aim it straight down so the
        // closed knuckles are the leading surface of the slam. Keep the palm
        // plane facing the target horizontally for a stable, readable fist.
        Vector knucklesDown = new Vector(0.0, -1.0, 0.0);
        Vector palmFacing = target == null
                ? new Vector(0.0, 0.0, 1.0)
                : upperTorso(target).toVector().subtract(hand.getLocation().toVector());
        palmFacing.setY(0.0);
        if (palmFacing.lengthSquared() < 1.0e-10) palmFacing = new Vector(0.0, 0.0, 1.0);
        orientBasis(hand, knucklesDown, palmFacing.normalize());
    }

    private static Location gestureFrontPoint(Player target, double scale, double extraHeight) {
        Location eye = target.getEyeLocation();
        Vector forward = horizontalFacing(target);
        return eye.clone().add(forward.multiply(Math.max(5.2, 4.0 + scale * 0.58))).add(0.0, extraHeight, 0.0);
    }

    private void orientWave(ParticleHand hand, double angleDegrees) {
        if (target == null) return;
        Vector zAxis = upperTorso(target).toVector().subtract(hand.getLocation().toVector());
        if (zAxis.lengthSquared() < 1.0e-10) zAxis = horizontalFacing(target).multiply(-1.0);
        zAxis.normalize(); // palm faces the target

        Vector up = new Vector(0.0, 1.0, 0.0);
        Vector side = zAxis.clone().crossProduct(up);
        if (side.lengthSquared() < 1.0e-10) side = targetRight(target);
        side.normalize();
        double radians = Math.toRadians(angleDegrees);
        Vector yAxis = up.multiply(Math.cos(radians)).add(side.multiply(Math.sin(radians)));
        orientBasis(hand, yAxis, zAxis);
    }

    private void orientThumb(ParticleHand hand, boolean up) {
        if (target == null) return;
        Vector zAxis = upperTorso(target).toVector().subtract(hand.getLocation().toVector());
        if (zAxis.lengthSquared() < 1.0e-10) zAxis = horizontalFacing(target).multiply(-1.0);
        zAxis.normalize(); // palm faces the target

        // The modeled thumb leaves the palm mostly along local +X. Choose a
        // root basis whose local +X is world-up/world-down, which makes the
        // gesture read like a real thumbs-up/down instead of a sideways fist.
        Vector desiredX = new Vector(0.0, up ? 1.0 : -1.0, 0.0);
        Vector desiredY = zAxis.clone().crossProduct(desiredX);
        if (desiredY.lengthSquared() < 1.0e-10) desiredY = targetRight(target);
        orientBasis(hand, desiredY.normalize(), zAxis);
    }

    private static Location giveBirdFrontPoint(Player target, double scale) {
        Location eye = target.getEyeLocation();
        Vector forward = eye.getDirection();
        forward.setY(0.0);
        if (forward.lengthSquared() < 1.0e-10) forward = new Vector(0.0, 0.0, 1.0);
        forward.normalize();
        return eye.clone().add(forward.multiply(Math.max(5.0, 3.8 + scale * 0.55))).add(0.0, 0.65, 0.0);
    }

    private void orientGiveBird(ParticleHand hand) {
        if (target == null) return;
        Vector zAxis = hand.getLocation().toVector().subtract(upperTorso(target).toVector());
        if (zAxis.lengthSquared() < 1.0e-10) zAxis = horizontalFacing(target);
        zAxis.normalize(); // palm front points AWAY from the target
        Vector yAxis = new Vector(0.0, 1.0, 0.0); // middle finger visibly upright
        orientBasis(hand, yAxis, zAxis);
    }

    private static double normalizeDegrees(double angle) {
        double out = angle % 360.0;
        if (out >= 180.0) out -= 360.0;
        if (out < -180.0) out += 360.0;
        return out;
    }

    private static Location stagingAbove(Player target, double height) {
        Location eye = target.getEyeLocation();
        return new Location(eye.getWorld(), eye.getX(), eye.getY() + height, eye.getZ());
    }

    private static Location liveSlamImpact(ParticleHand hand, Player target) {
        Location eye = target.getEyeLocation();
        double centerClearance = Math.max(0.65, hand.getScale() * 0.14);
        return new Location(eye.getWorld(), eye.getX(), eye.getY() + 0.10 + centerClearance, eye.getZ());
    }

    private static Location upperTorso(Player player) {
        Location feet = player.getLocation();
        double eyeHeight = Math.abs(player.getEyeLocation().getY() - feet.getY());
        if (!Double.isFinite(eyeHeight) || eyeHeight < 0.1) eyeHeight = 1.62;
        return feet.clone().add(0.0, eyeHeight * 0.60, 0.0);
    }

    private static Location judgmentStaging(Player target, double distance) {
        Location eye = target.getEyeLocation();
        Vector facing = eye.getDirection();
        facing.setY(0.0);
        if (facing.lengthSquared() < 1.0e-8) facing = new Vector(0.0, 0.0, 1.0);
        return eye.clone().add(facing.normalize().multiply(distance)).add(0.0, 0.4, 0.0);
    }

    private static Location punchStaging(ParticleHand hand, Player target, double distance) {
        Location torso = upperTorso(target);
        Vector away = hand.getLocation().toVector().subtract(torso.toVector());
        away.setY(0.0);
        if (away.lengthSquared() < 1.0e-8) {
            away = target.getEyeLocation().getDirection();
            away.setY(0.0);
            away.multiply(-1.0);
        }
        if (away.lengthSquared() < 1.0e-8) away = new Vector(0.0, 0.0, -1.0);
        return torso.clone().add(away.normalize().multiply(distance)).add(0.0, 0.25, 0.0);
    }

    private static Vector horizontalFacing(Player target) {
        Vector forward = target.getEyeLocation().getDirection();
        forward.setY(0.0);
        if (forward.lengthSquared() < 1.0e-8) forward = new Vector(0.0, 0.0, 1.0);
        return forward.normalize();
    }

    private static Location rageWindupPoint(Player target, int index) {
        Location torso = upperTorso(target);
        Vector right = targetRight(target);
        Vector forward = horizontalFacing(target);
        return switch (index) {
            case 0 -> torso.clone().add(right.clone().multiply(5.8)).add(0.0, 0.8, 0.0);
            case 1 -> torso.clone().subtract(right.clone().multiply(5.8)).add(0.0, 1.0, 0.0);
            default -> torso.clone().subtract(forward.clone().multiply(1.5)).add(0.0, 7.2, 0.0);
        };
    }

    private static Location rageStrikePoint(Player target, int index) {
        Location torso = upperTorso(target);
        Vector right = targetRight(target);
        return switch (index) {
            case 0 -> torso.clone().subtract(right.clone().multiply(3.8));
            case 1 -> torso.clone().add(right.clone().multiply(3.8));
            default -> torso.clone().add(0.0, 0.25, 0.0);
        };
    }

    private static Vector targetRight(Player target) {
        Vector forward = target.getEyeLocation().getDirection();
        forward.setY(0.0);
        if (forward.lengthSquared() < 1.0e-8) forward = new Vector(0.0, 0.0, 1.0);
        forward.normalize();
        return new Vector(-forward.getZ(), 0.0, forward.getX()).normalize();
    }

    private static Location slapStaging(Player target, double distance) {
        return upperTorso(target).add(targetRight(target).multiply(distance)).add(0.0, 0.25, 0.0);
    }

    private void beginAdaptiveScale(ParticleHand hand) {
        if (hand == null) return;
        adaptiveBaseScale = hand.getScale();
        adaptiveScaleManaged = true;
        adaptiveScaleTick = 0;
    }

    private void restoreAdaptiveScale(ParticleHand hand) {
        if (!adaptiveScaleManaged || hand == null) return;
        if (adaptiveBaseScale > 0.0 && Double.isFinite(adaptiveBaseScale)) {
            hand.setScale(adaptiveBaseScale);
        }
        adaptiveScaleManaged = false;
        adaptiveBaseScale = 0.0;
        adaptiveScaleTick = 0;
    }

    /**
     * Shrinks persistent/close-range presence actions in cramped rooms while
     * retaining the configured scale outdoors. Scaling is eased and sampled
     * every few ticks so walking under a low arch does not make the hand pulse.
     */
    private void updateAdaptivePresenceScale(ParticleHand hand, Player player,
                                             double minimum, double preferredMaximum,
                                             boolean immediate) {
        if (hand == null || player == null || !adaptiveScaleManaged) return;
        if (!immediate && (++adaptiveScaleTick % 4) != 0) return;

        double preferred = Math.min(adaptiveBaseScale, preferredMaximum);
        double effectiveMinimum = Math.min(minimum, preferred);
        double desired = adaptiveInteriorScale(player, effectiveMinimum, preferred);
        double current = hand.getScale();
        double next = immediate ? desired : current + (desired - current) * 0.34;
        if (Math.abs(next - desired) < 0.025) next = desired;
        hand.setScale(clamp(next, effectiveMinimum, Math.max(effectiveMinimum, adaptiveBaseScale)));
    }

    private static double adaptiveInteriorScale(Player player, double minimum, double preferred) {
        if (player == null || player.getWorld() == null) return preferred;
        Location eye = player.getEyeLocation();
        World world = player.getWorld();

        double ceilingGap = 7.0;
        for (int i = 1; i <= 7; i++) {
            Location probe = eye.clone().add(0.0, i * 0.72, 0.0);
            if (!world.getBlockAt(probe).isPassable()) {
                ceilingGap = Math.max(0.4, probe.getY() - eye.getY());
                break;
            }
        }

        Vector[] dirs = {
                new Vector(1, 0, 0), new Vector(-1, 0, 0),
                new Vector(0, 0, 1), new Vector(0, 0, -1)
        };
        double minHorizontal = 5.0;
        for (Vector dir : dirs) {
            double free = 5.0;
            for (int step = 1; step <= 5; step++) {
                Location low = eye.clone().add(dir.clone().multiply(step * 0.72)).add(0.0, -0.45, 0.0);
                Location high = low.clone().add(0.0, 1.0, 0.0);
                if (!world.getBlockAt(low).isPassable() || !world.getBlockAt(high).isPassable()) {
                    free = step * 0.72;
                    break;
                }
            }
            minHorizontal = Math.min(minHorizontal, free);
        }

        boolean confined = ceilingGap < 4.8 || minHorizontal < 3.1;
        if (!confined) return preferred;

        double ceilingScale = minimum + Math.max(0.0, ceilingGap - 0.7) * 0.28;
        double widthScale = minimum + Math.max(0.0, minHorizontal - 0.6) * 0.24;
        return clamp(Math.min(preferred, Math.min(ceilingScale, widthScale)), minimum, preferred);
    }

    private static Location bunkerManifestPoint(ParticleHand hand, Player target) {
        Location eye = target.getEyeLocation();
        Vector forward = horizontalFacing(target);
        Vector right = targetRight(target);
        double distance = Math.max(1.85, 1.25 + hand.getScale() * 0.58);

        // Prefer the space the player is actually looking at, then diagonal/side
        // alternatives. This keeps Breach visible in caves instead of parking
        // the root in the ceiling directly above their head.
        Vector[] directions = {
                forward.clone(),
                forward.clone().multiply(0.72).add(right.clone().multiply(0.72)).normalize(),
                forward.clone().multiply(0.72).subtract(right.clone().multiply(0.72)).normalize(),
                right.clone(),
                right.clone().multiply(-1.0)
        };

        for (Vector dir : directions) {
            Location candidate = eye.clone().add(dir.multiply(distance)).add(0.0, 0.10, 0.0);
            if (breachPointFits(candidate, hand.getScale())) return candidate;
        }

        // Tightest corridors: remain close and visible rather than escaping to
        // the roof. Adaptive scale will already be near its 1.45 minimum here.
        return eye.clone().add(forward.multiply(Math.max(1.25, hand.getScale() * 0.70))).add(0.0, 0.05, 0.0);
    }

    private static boolean breachPointFits(Location center, double scale) {
        if (center == null || center.getWorld() == null) return false;
        World world = center.getWorld();
        double r = Math.max(0.35, Math.min(0.80, scale * 0.26));
        double[][] probes = {
                {0, 0, 0}, {r, 0, 0}, {-r, 0, 0}, {0, 0, r}, {0, 0, -r},
                {0, r * 0.65, 0}, {0, -r * 0.65, 0}
        };
        for (double[] p : probes) {
            if (!world.getBlockAt(center.clone().add(p[0], p[1], p[2])).isPassable()) return false;
        }
        return true;
    }

    private static Location hoverChasePoint(Player target, double height, double behind) {
        Location eye = target.getEyeLocation();
        Vector facing = eye.getDirection();
        facing.setY(0.0);
        if (facing.lengthSquared() < 1.0e-8) facing = new Vector(0.0, 0.0, 1.0);
        facing.normalize();
        return eye.clone().subtract(facing.multiply(behind)).add(0.0, height, 0.0);
    }

    private static Location stalkReacquirePoint(Player target) {
        return hoverChasePoint(target, 9.0, 7.0);
    }

    private Location resolveTransportDestination(ParticleHand hand) {
        if (transportDestinationPlayer != null) {
            if (!transportDestinationPlayer.isOnline() || transportDestinationPlayer.isDead()) return null;
            if (transportDestinationPlayer.getWorld() == null || !transportDestinationPlayer.getWorld().equals(hand.getWorld())) return null;
            return transportDestinationPlayer.getLocation();
        }
        if (transportDestinationPoint == null || transportDestinationPoint.getWorld() == null
                || !transportDestinationPoint.getWorld().equals(hand.getWorld())) return null;
        return transportDestinationPoint.clone();
    }

    private String transportDestinationDescription() {
        if (transportDestinationPlayer != null) return transportDestinationPlayer.getName();
        if (transportDestinationPoint != null) return formatLocation(transportDestinationPoint);
        return "?";
    }

    private Location currentSmashPoint() {
        if (smashTargetPlayer != null) {
            if (!smashTargetPlayer.isOnline() || smashTargetPlayer.isDead()) return null;
            return smashTargetPlayer.getLocation();
        }
        return smashTargetPoint == null ? null : smashTargetPoint.clone();
    }

    private static Location smashStaging(Location impact, double height) {
        return impact.clone().add(0.0, height, 0.0);
    }

    private static Location smashStrikeOrigin(ParticleHand hand, Location impact) {
        return impact.clone().add(0.0, Math.max(0.70, hand.getScale() * 0.16), 0.0);
    }

    private static Location distantStalkPoint(Player target, ThreadLocalRandom random) {
        Location base = target.getEyeLocation();
        double angle = random.nextDouble(0.0, Math.PI * 2.0);
        double radius = random.nextDouble(22.0, 42.0);
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;
        return base.clone().add(x, random.nextDouble(12.0, 25.0), z);
    }

    private static String formatLocation(Location location) {
        return String.format(Locale.US, "%.1f %.1f %.1f", location.getX(), location.getY(), location.getZ());
    }

    private boolean phaseRequiresLiveTarget() {
        return switch (phase) {
            case SLAM_IMPACT_HOLD, SLAM_RECOIL,
                    FORCE_SLAP_STRIKE, FORCE_SLAP_RECOVER,
                    PUNCH_STRIKE, PUNCH_RECOVER,
                    SLAP_STRIKE, SLAP_RECOVER,
                    RAGE_RECOVER, CLAP_DISMISS,
                    SMASH_RECOVER, BLESS_DEPART, SANCTUARY_DEPART, SPANK_RECOVER -> false;
            default -> true;
        };
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    private static void validateTarget(ParticleHand hand, Player player) {
        if (player == null || !player.isOnline() || player.isDead()) throw new IllegalArgumentException("Action target must be alive and online.");
        if (player.getWorld() == null || !player.getWorld().equals(hand.getWorld())) {
            throw new IllegalArgumentException("Action target must be in the hand's current world.");
        }
    }

    private static void validateLocation(ParticleHand hand, Location location, String message) {
        if (location == null || location.getWorld() == null || !location.getWorld().equals(hand.getWorld())) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void validateHeight(double height) {
        if (!Double.isFinite(height) || height < 2.0 || height > 128.0) {
            throw new IllegalArgumentException("Action height must be between 2 and 128 blocks.");
        }
    }

    private static void validateTicks(int ticks, String name) {
        if (ticks < 1 || ticks > 20 * 120) {
            throw new IllegalArgumentException(name + " duration must be between 1 tick and 120 seconds.");
        }
    }
}
