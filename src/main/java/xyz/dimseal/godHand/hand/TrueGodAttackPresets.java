package xyz.dimseal.godHand.hand;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import xyz.dimseal.godHand.hand.animation.EasingCurve;

/** Preconfigured attacks that inherit the user-selected main Hand settings. */
public final class TrueGodAttackPresets {

    private TrueGodAttackPresets() {}

    public static ParticleHand prepare(HandManager manager, Player target, MainHandSettings settings) {
        return prepare(manager, target, settings, true);
    }

    public static ParticleHand prepare(HandManager manager, Player target, MainHandSettings settings, boolean announcePresence) {
        if (settings == null) throw new IllegalArgumentException("Main hand settings cannot be null.");
        ParticleHand hand = manager.getHand();
        if (hand != null && hand.getWorld() != null && target.getWorld() != null
                && !hand.getWorld().equals(target.getWorld())) {
            throw new IllegalArgumentException("Cross-dimensional Hand movement is disabled. Despawn the current Hand before using GodHand in another world.");
        }
        boolean summoned = hand == null || hand.getWorld() == null || hand.isDismissing() || hand.isRemovalRequested();

        if (summoned) {
            hand = manager.createHand(spawnPoint(target), settings.getScale());
            if (announcePresence) TrueGodEffects.presence(target);
        } else {
            hand.cancelAction();
        }
        configurePreparedHand(hand, settings);
        manager.refreshVisuals();
        return hand;
    }

    /** Prepare an attack at a fixed location even when no player is the target. */
    public static ParticleHand prepareAt(HandManager manager, Location focus, MainHandSettings settings) {
        if (focus == null || focus.getWorld() == null) throw new IllegalArgumentException("Attack point must have a world.");
        if (settings == null) throw new IllegalArgumentException("Main hand settings cannot be null.");
        ParticleHand hand = manager.getHand();
        if (hand != null && hand.getWorld() != null && !hand.getWorld().equals(focus.getWorld())) {
            throw new IllegalArgumentException("Cross-dimensional Hand movement is disabled. Despawn the current Hand before using GodHand in another world.");
        }
        boolean summoned = hand == null || hand.getWorld() == null || hand.isDismissing() || hand.isRemovalRequested();
        if (summoned) {
            Location spawn = focus.clone().add(-5.0, Math.max(7.0, settings.getScale() * 1.8), -5.0);
            hand = manager.createHand(spawn, settings.getScale());
        } else {
            hand.cancelAction();
        }
        configurePreparedHand(hand, settings);
        manager.refreshVisuals();
        return hand;
    }

    public static ParticleHand summon(HandManager manager, Player focus, MainHandSettings settings) {
        ParticleHand existing = manager.getHand();
        if (existing != null && existing.getWorld() != null && focus.getWorld() != null
                && !existing.getWorld().equals(focus.getWorld())) {
            throw new IllegalArgumentException("Cross-dimensional Hand movement is disabled. Despawn the current Hand before summoning it in another world.");
        }
        ParticleHand hand = manager.createHand(spawnPoint(focus), settings.getScale());
        configurePreparedHand(hand, settings);
        hand.setRotation(focus.getLocation().getYaw() + 180.0, 0.0, 0.0);
        // Manual rotation disarms idle by design; summon is a persistent presence,
        // so re-arm the living idle after the final spawn orientation is set.
        hand.startIdle();
        manager.refreshVisuals();
        return hand;
    }

    private static void configurePreparedHand(ParticleHand hand, MainHandSettings settings) {
        hand.stopRotation();
        settings.applyTo(hand);
        hand.setAxesVisible(false);
        hand.setSkeletonVisible(false);
        hand.setGripDebugVisible(false);
        hand.setCombatDebugVisible(false);
    }

    public static int groundSlamApproachTicks(ParticleHand hand, Player target, double height) {
        Location staging = target.getEyeLocation().clone().add(0.0, height, 0.0);
        double distance = safeDistance(hand.getLocation(), staging);
        double seconds = clamp(0.62 + distance / 18.0, 0.72, 3.05);
        return secondsToTicks(seconds);
    }

    public static EasingCurve groundSlamApproachEasing(ParticleHand hand, Player target, double height) {
        Location staging = target.getEyeLocation().clone().add(0.0, height, 0.0);
        double distance = safeDistance(hand.getLocation(), staging);
        if (distance < 9.0) return EasingCurve.EASE_OUT;
        if (distance < 26.0) return EasingCurve.SMOOTH;
        return EasingCurve.EASE_IN_OUT;
    }

    /** slightly faster descent; late correction now lives in the action controller. */
    public static int groundSlamDropTicks(double height) {
        double seconds = clamp(0.25 + height / 48.0, 0.34, 0.58);
        return secondsToTicks(seconds);
    }

    public static int grabApproachTicks(ParticleHand hand, Player target, double height) {
        Location staging = target.getEyeLocation().clone().add(0.0, height, 0.0);
        double distance = safeDistance(hand.getLocation(), staging);
        return secondsToTicks(clamp(0.75 + distance / 18.0, 0.85, 3.0));
    }

    public static int grabCloseTicks(ParticleHand hand, Player target) {
        double distance = safeDistance(hand.getLocation(), target.getEyeLocation());
        return secondsToTicks(clamp(0.55 + distance / 55.0, 0.60, 1.05));
    }

    public static int surfaceJudgmentApproachTicks(ParticleHand hand, Player target, double orbitRadius) {
        double distance = safeDistance(hand.getLocation(), target.getEyeLocation()) + orbitRadius * 0.35;
        return secondsToTicks(clamp(0.85 + distance / 22.0, 1.0, 3.2));
    }

    public static int forceSlapApproachTicks(ParticleHand hand, Player target, double stageDistance) {
        Location stage = target.getEyeLocation().clone();
        Vector facing = stage.getDirection();
        facing.setY(0.0);
        if (facing.lengthSquared() < 1.0e-8) facing = new Vector(0.0, 0.0, 1.0);
        stage.add(facing.normalize().multiply(stageDistance)).add(0.0, 0.4, 0.0);
        double distance = safeDistance(hand.getLocation(), stage);
        return secondsToTicks(clamp(0.60 + distance / 19.0, 0.70, 2.55));
    }

    public static int forceSlapStrikeTicks(double stageDistance) {
        return secondsToTicks(clamp(0.30 + stageDistance / 58.0, 0.38, 0.62));
    }

    public static int punchApproachTicks(ParticleHand hand, Player target, double stageDistance) {
        double distance = safeDistance(hand.getLocation(), target.getEyeLocation());
        return secondsToTicks(clamp(0.58 + (distance + stageDistance) / 24.0, 0.72, 2.4));
    }

    public static int punchStrikeTicks(double stageDistance) {
        return secondsToTicks(clamp(0.22 + stageDistance / 70.0, 0.28, 0.48));
    }

    public static int slapApproachTicks(ParticleHand hand, Player target, double stageDistance) {
        double distance = safeDistance(hand.getLocation(), target.getEyeLocation());
        return secondsToTicks(clamp(0.48 + (distance + stageDistance) / 28.0, 0.60, 2.1));
    }

    public static int slapStrikeTicks(double stageDistance) {
        return secondsToTicks(clamp(0.18 + stageDistance / 82.0, 0.24, 0.40));
    }

    public static int cycloneApproachTicks(ParticleHand hand, Player target, double stageDistance) {
        double distance = safeDistance(hand.getLocation(), target.getEyeLocation());
        return secondsToTicks(clamp(0.48 + (distance + stageDistance) / 28.0, 0.60, 2.0));
    }

    public static int cycloneStrikeTicks(double stageDistance) {
        // charge is intentionally readable after the long orbit; no
        // more near-instant 4-7 tick pass-through.
        return secondsToTicks(clamp(0.48 + stageDistance / 70.0, 0.58, 0.82));
    }

    public static int smashApproachTicks(ParticleHand hand, Location target, double height) {
        double distance = safeDistance(hand.getLocation(), target.clone().add(0.0, height, 0.0));
        return secondsToTicks(clamp(0.65 + distance / 20.0, 0.8, 3.0));
    }

    private static Location spawnPoint(Player target) {
        Location eye = target.getEyeLocation();
        Vector horizontal = eye.getDirection();
        horizontal.setY(0.0);
        if (horizontal.lengthSquared() < 1.0e-8) horizontal = new Vector(0.0, 0.0, 1.0);
        horizontal.normalize().multiply(-4.0);
        return eye.clone().add(horizontal).add(0.0, 2.5, 0.0);
    }

    private static int secondsToTicks(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20.0));
    }

    private static double safeDistance(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) return 40.0;
        return a.distance(b);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
