package xyz.dimseal.godHand.hand;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import xyz.dimseal.godHand.hand.render.HandPalette;
import xyz.dimseal.godHand.hand.render.RenderMotionState;
import xyz.dimseal.godHand.hand.render.WristStyle;
import xyz.dimseal.godHand.hand.skeleton.HandDigit;
import xyz.dimseal.godHand.hand.skeleton.SkeletalFrame;
import xyz.dimseal.godHand.hand.skeleton.SkeletalHandModel;
import xyz.dimseal.godHand.model.ModelPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * particle renderer: full-density ULTRA surfaces, deterministic
 * shading/material effects, forced delivery, motion deghosting, selectable
 * wrist geometry, and explicit exposure-state cleanup.
 */
public final class ParticleHandRenderer {

    private static final double NORMAL_VIEW_DISTANCE = 160.0;
    private static final double FORCED_VIEW_DISTANCE = 512.0;

    private static final Particle.DustOptions LANDMARK_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 210, 80), 1.05f);
    private static final Particle.DustOptions BONE_DUST =
            new Particle.DustOptions(Color.fromRGB(95, 235, 255), 0.72f);
    private static final Particle.DustOptions JOINT_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 145, 55), 1.15f);
    private static final Particle.DustOptions X_AXIS_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 70, 70), 0.9f);
    private static final Particle.DustOptions Y_AXIS_DUST =
            new Particle.DustOptions(Color.fromRGB(80, 255, 80), 0.9f);
    private static final Particle.DustOptions Z_AXIS_DUST =
            new Particle.DustOptions(Color.fromRGB(80, 130, 255), 0.9f);
    private static final Particle.DustOptions GRIP_CENTER_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 70, 230), 1.35f);
    private static final Particle.DustOptions HELD_FEET_DUST =
            new Particle.DustOptions(Color.fromRGB(120, 255, 120), 1.15f);
    private static final Particle.DustOptions COMBAT_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 55, 55), 0.90f);

    private final HandBodyModel anatomicalBodyModel = HandBodyModel.create(WristStyle.ANATOMICAL);
    private final HandBodyModel legacyBodyModel = HandBodyModel.create(WristStyle.LEGACY);
    private final SkeletalHandModel skeletalModel = new SkeletalHandModel();

    private final Map<UUID, Integer> exposureFrames = new HashMap<>();
    private RenderSnapshot previousSnapshot;
    private long renderFrame;

    public void resetExposure() {
        exposureFrames.clear();
        previousSnapshot = null;
        renderFrame = 0L;
    }

    public void render(ParticleHand hand) {
        Location origin = hand.getLocation();
        World world = origin.getWorld();
        if (world == null) return;

        List<Player> viewers = collectViewers(world, origin, hand.isForceParticles());
        if (viewers.isEmpty()) {
            exposureFrames.clear();
            previousSnapshot = RenderSnapshot.capture(hand, origin);
            return;
        }

        HandBodyModel bodyModel = hand.getWristStyle() == WristStyle.LEGACY ? legacyBodyModel : anatomicalBodyModel;
        SkeletalFrame skeletalFrame = skeletalModel.evaluate(hand);
        RenderSnapshot currentSnapshot = RenderSnapshot.capture(hand, origin);
        RenderMotionState motionState = classifyMotion(hand, currentSnapshot);
        previousSnapshot = currentSnapshot;
        renderFrame++;

        RenderTuning tuning = tuning(motionState);
        Particle.DustOptions[] palette = createPalette(hand, tuning.particleSizeMultiplier());

        boolean skipMainSurface = false;

        Set<UUID> seen = new HashSet<>();
        for (Player viewer : viewers) {
            seen.add(viewer.getUniqueId());
            exposureFrames.merge(viewer.getUniqueId(), 1, Integer::sum);
            if (skipMainSurface) continue;

            double lod = distanceLod(viewer, origin);
            double requestedFraction = hand.getDensity().sampleFraction()
                    * tuning.sampleMultiplier()
                    * lod
                    * hand.getActionRenderFractionMultiplier();

            // restores the original ULTRA surface counts. Exposure
            // state is still tracked and explicitly cleared on hand removal /
            // recreation so the stale-state fix remains intact, but
            // exposure duration no longer reduces requested density.
            double fraction = requestedFraction;

            renderSurface(viewer, hand, origin, bodyModel.bodyPoints(), palette, fraction, 0x13579BDF, 0.00);
            for (HandDigit digit : HandDigit.values()) {
                renderSurface(
                        viewer, hand, origin, skeletalFrame.digitSurfacePoints().get(digit), palette, fraction,
                        0x2468ACE1 ^ (digit.ordinal() * 0x45D9F3B), digitShadeBias(digit)
                );
            }
        }
        exposureFrames.keySet().removeIf(uuid -> !seen.contains(uuid));

        if (hand.isAxesVisible()) {
            renderAxes(hand, origin, viewers);
            renderDebugPoints(viewers, hand, origin, bodyModel.landmarkPoints(), LANDMARK_DUST);
        }
        if (hand.isSkeletonVisible()) {
            renderDebugPoints(viewers, hand, origin, skeletalFrame.bonePoints(), BONE_DUST);
            renderDebugPoints(viewers, hand, origin, skeletalFrame.jointPoints(), JOINT_DUST);
        }
        if (hand.isGripDebugVisible()) {
            renderMarker(viewers, hand, hand.getGripWorldPoint(), GRIP_CENTER_DUST, 0.22);
            Player held = hand.getHeldPlayer();
            if (held != null && held.isOnline()) renderMarker(viewers, hand, held.getLocation(), HELD_FEET_DUST, 0.16);
        }
        if (hand.isCombatDebugVisible()) renderCombatVolume(viewers, hand, origin);
    }

    private static void renderSurface(
            Player viewer,
            ParticleHand hand,
            Location origin,
            List<ModelPoint> points,
            Particle.DustOptions[] palette,
            double fraction,
            int seed,
            double shadeBias
    ) {
        fraction = clamp(fraction, 0.015, 1.0);

        for (int i = 0; i < points.size(); i++) {
            if (!selected(i, seed, fraction)) {
                continue;
            }

            ModelPoint point = points.get(i);
            Vector offset = hand.transformLocalPoint(point.x(), point.y(), point.z());
            int shade = hand.isShadingEnabled() ? shadeIndex(point, shadeBias) : 3;
            Particle.DustOptions dust = chooseSurfaceDust(hand, palette, shade, i, seed);

            viewer.spawnParticle(
                    Particle.DUST,
                    origin.getX() + offset.getX(),
                    origin.getY() + offset.getY(),
                    origin.getZ() + offset.getZ(),
                    1,
                    0.0, 0.0, 0.0,
                    0.0,
                    dust,
                    hand.isForceParticles()
            );
        }
    }

    /**
     * Local-space pseudo-lighting. The color bands stay attached to the hand,
     * giving the model readable front/side/back separation even without a real
     * surface-normal lighting system.
     */
    static int shadeIndex(ModelPoint point) {
        return shadeIndex(point, 0.0);
    }

    private static int shadeIndex(ModelPoint point, double partBias) {
        double score = point.z() * 2.9 + point.x() * 0.34 + point.y() * 0.07 + partBias;
        if (score < -0.30) return 0;
        if (score < -0.10) return 1;
        if (score < 0.10) return 2;
        if (score < 0.30) return 3;
        return 4;
    }

    private static double digitShadeBias(HandDigit digit) {
        return switch (digit) {
            case THUMB -> -0.10;
            case INDEX -> 0.08;
            case MIDDLE -> 0.13;
            case RING -> -0.01;
            case PINKY -> -0.11;
        };
    }

    private static Particle.DustOptions[] createPalette(ParticleHand hand, double sizeMultiplier) {
        Color base = hand.getBaseColor();
        float baseSize = switch (hand.getDensity()) {
            case LOW -> 0.58f;
            case NORMAL -> 0.62f;
            case HIGH -> 0.67f;
            case ULTRA -> 0.71f;
        };
        float size = (float) clamp(baseSize * sizeMultiplier, 0.36, 1.20);

        if (HandPalette.isVoid(base)) {
            // VOID particle body: black -> obsidian-violet -> purple,
            // with rare white stellar points. No cyan/blue body tones.
            return new Particle.DustOptions[]{
                    new Particle.DustOptions(Color.fromRGB(1, 0, 3), size),
                    new Particle.DustOptions(Color.fromRGB(5, 1, 9), size),
                    new Particle.DustOptions(Color.fromRGB(10, 2, 18), size),
                    new Particle.DustOptions(Color.fromRGB(18, 4, 31), size),
                    new Particle.DustOptions(Color.fromRGB(30, 7, 52), size),
                    new Particle.DustOptions(Color.fromRGB(72, 15, 118), size * 0.96f),
                    new Particle.DustOptions(Color.fromRGB(130, 36, 205), size * 0.88f),
                    new Particle.DustOptions(Color.fromRGB(252, 252, 255), size * 0.68f)
            };
        }

        if (HandPalette.isSpectral(base)) {
            return new Particle.DustOptions[]{
                    new Particle.DustOptions(Color.fromRGB(82, 145, 180), size),
                    new Particle.DustOptions(Color.fromRGB(118, 188, 220), size),
                    new Particle.DustOptions(Color.fromRGB(164, 220, 242), size),
                    new Particle.DustOptions(Color.fromRGB(210, 242, 255), size),
                    new Particle.DustOptions(Color.fromRGB(248, 253, 255), size * 0.90f)
            };
        }

        if (HandPalette.isViolet(base)) {
            return new Particle.DustOptions[]{
                    new Particle.DustOptions(Color.fromRGB(38, 7, 72), size),
                    new Particle.DustOptions(Color.fromRGB(66, 14, 122), size),
                    new Particle.DustOptions(Color.fromRGB(92, 26, 172), size),
                    new Particle.DustOptions(Color.fromRGB(122, 42, 214), size),
                    new Particle.DustOptions(Color.fromRGB(170, 92, 242), size * 0.94f)
            };
        }

        if (HandPalette.isCrimson(base)) {
            return new Particle.DustOptions[]{
                    new Particle.DustOptions(Color.fromRGB(6, 0, 2), size),
                    new Particle.DustOptions(Color.fromRGB(35, 1, 7), size),
                    new Particle.DustOptions(Color.fromRGB(76, 3, 14), size),
                    new Particle.DustOptions(Color.fromRGB(132, 8, 28), size),
                    new Particle.DustOptions(Color.fromRGB(205, 26, 42), size * 0.95f)
            };
        }

        return new Particle.DustOptions[]{
                new Particle.DustOptions(HandPalette.shade(base, 0.43, 0.00), size),
                new Particle.DustOptions(HandPalette.shade(base, 0.62, 0.00), size),
                new Particle.DustOptions(HandPalette.shade(base, 0.80, 0.00), size),
                new Particle.DustOptions(HandPalette.shade(base, 1.00, 0.03), size),
                new Particle.DustOptions(HandPalette.shade(base, 1.10, 0.20), size)
        };
    }

    private static Particle.DustOptions chooseSurfaceDust(
            ParticleHand hand, Particle.DustOptions[] palette, int shade, int index, int seed
    ) {
        if (!HandPalette.isVoid(hand.getBaseColor())) {
            return palette[Math.max(0, Math.min(4, shade))];
        }
        int h = index ^ seed;
        h ^= h >>> 16;
        h *= 0x7feb352d;
        h ^= h >>> 15;
        h *= 0x846ca68b;
        h ^= h >>> 16;
        int bucket = h & 0xFF;
        if (bucket < 7) return palette[7];          // ~2.7% white stars
        if (bucket < 42) return palette[6];         // ~13.7% vivid violet noise
        if (bucket < 78) return palette[5];         // ~14.1% deep violet interior
        return palette[Math.max(0, Math.min(4, shade))];
    }

    private RenderMotionState classifyMotion(ParticleHand hand, RenderSnapshot current) {
        if (previousSnapshot == null || !previousSnapshot.sameWorld(current)) {
            return RenderMotionState.STATIC;
        }

        double dx = current.x - previousSnapshot.x;
        double dy = current.y - previousSnapshot.y;
        double dz = current.z - previousSnapshot.z;
        double translation = Math.sqrt(dx * dx + dy * dy + dz * dz);

        double rootDegrees = Math.max(
                Math.abs(angleDelta(current.yaw, previousSnapshot.yaw)),
                Math.max(
                        Math.abs(angleDelta(current.pitch, previousSnapshot.pitch)),
                        Math.abs(angleDelta(current.roll, previousSnapshot.roll))
                )
        );
        double rootArc = Math.toRadians(rootDegrees) * hand.getScale() * 0.52;

        double maxJointDegrees = 0.0;
        for (int i = 0; i < current.joints.length; i++) {
            maxJointDegrees = Math.max(maxJointDegrees, Math.abs(current.joints[i] - previousSnapshot.joints[i]));
        }
        double jointArc = Math.toRadians(maxJointDegrees) * hand.getScale() * 0.18;

        double score = translation + rootArc + jointArc;
        if (score >= 0.75) {
            return RenderMotionState.FAST;
        }
        if (score >= 0.10) {
            return RenderMotionState.MOVING;
        }
        return RenderMotionState.STATIC;
    }

    /** Fixed reduced-smoothing tuning. This is intentionally not user configurable. */
    private static RenderTuning tuning(RenderMotionState motionState) {
        return switch (motionState) {
            case STATIC -> new RenderTuning(1.0, 1.0);
            case MOVING -> new RenderTuning(0.70, 0.82);
            case FAST -> new RenderTuning(0.46, 0.68);
        };
    }

    /** extends LOD into the forced-particle range. */
    private static double distanceLod(Player viewer, Location origin) {
        double distanceSquared = viewer.getLocation().distanceSquared(origin);
        if (distanceSquared <= 50.0 * 50.0) return 1.0;
        if (distanceSquared <= 100.0 * 100.0) return 0.72;
        if (distanceSquared <= 160.0 * 160.0) return 0.46;
        if (distanceSquared <= 320.0 * 320.0) return 0.22;
        return 0.10;
    }

    private static boolean selected(int index, int seed, double fraction) {
        if (fraction >= 0.9999) {
            return true;
        }
        int x = index ^ seed;
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        double unit = (x & 0x7fffffff) / (double) Integer.MAX_VALUE;
        return unit <= fraction;
    }

    private static List<Player> collectViewers(World world, Location origin, boolean force) {
        double range = force ? FORCED_VIEW_DISTANCE : NORMAL_VIEW_DISTANCE;
        double rangeSquared = range * range;
        List<Player> viewers = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(origin) <= rangeSquared) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    private static void renderDebugPoints(
            List<Player> viewers,
            ParticleHand hand,
            Location origin,
            List<ModelPoint> points,
            Particle.DustOptions dust
    ) {
        for (ModelPoint point : points) {
            Vector offset = hand.transformLocalPoint(point.x(), point.y(), point.z());
            double x = origin.getX() + offset.getX();
            double y = origin.getY() + offset.getY();
            double z = origin.getZ() + offset.getZ();
            for (Player viewer : viewers) {
                spawnDust(viewer, hand, x, y, z, dust);
            }
        }
    }

    private static void renderMarker(
            List<Player> viewers,
            ParticleHand hand,
            Location center,
            Particle.DustOptions dust,
            double radius
    ) {
        double[][] offsets = {
                {0, 0, 0},
                {radius, 0, 0}, {-radius, 0, 0},
                {0, radius, 0}, {0, -radius, 0},
                {0, 0, radius}, {0, 0, -radius}
        };
        for (double[] offset : offsets) {
            double x = center.getX() + offset[0];
            double y = center.getY() + offset[1];
            double z = center.getZ() + offset[2];
            for (Player viewer : viewers) {
                spawnDust(viewer, hand, x, y, z, dust);
            }
        }
    }

    private void renderAxes(ParticleHand hand, Location origin, List<Player> viewers) {
        renderAxis(hand, origin, viewers, 0.70, 0.0, 0.0, 18, X_AXIS_DUST);
        renderAxis(hand, origin, viewers, 0.0, 0.90, 0.0, 18, Y_AXIS_DUST);
        renderAxis(hand, origin, viewers, 0.0, 0.0, 0.45, 14, Z_AXIS_DUST);
    }

    private static void renderAxis(
            ParticleHand hand,
            Location origin,
            List<Player> viewers,
            double endX,
            double endY,
            double endZ,
            int segments,
            Particle.DustOptions dust
    ) {
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            Vector offset = hand.transformLocalPoint(endX * t, endY * t, endZ * t);
            double x = origin.getX() + offset.getX();
            double y = origin.getY() + offset.getY();
            double z = origin.getZ() + offset.getZ();

            for (Player viewer : viewers) {
                spawnDust(viewer, hand, x, y, z, dust);
            }
        }
    }

    private static void renderCombatVolume(List<Player> viewers, ParticleHand hand, Location origin) {
        double minX = HandCombatResolver.MIN_X;
        double maxX = HandCombatResolver.MAX_X;
        double minY = HandCombatResolver.MIN_Y;
        double maxY = HandCombatResolver.MAX_Y;
        double minZ = HandCombatResolver.MIN_Z;
        double maxZ = HandCombatResolver.MAX_Z;

        // 12 oriented box edges in normalized hand-local space.
        for (double y : new double[]{minY, maxY}) {
            for (double z : new double[]{minZ, maxZ}) {
                renderLocalLine(viewers, hand, origin, minX, y, z, maxX, y, z, COMBAT_DUST);
            }
        }
        for (double x : new double[]{minX, maxX}) {
            for (double z : new double[]{minZ, maxZ}) {
                renderLocalLine(viewers, hand, origin, x, minY, z, x, maxY, z, COMBAT_DUST);
            }
        }
        for (double x : new double[]{minX, maxX}) {
            for (double y : new double[]{minY, maxY}) {
                renderLocalLine(viewers, hand, origin, x, y, minZ, x, y, maxZ, COMBAT_DUST);
            }
        }
    }

    private static void renderLocalLine(
            List<Player> viewers, ParticleHand hand, Location origin,
            double ax, double ay, double az, double bx, double by, double bz,
            Particle.DustOptions dust
    ) {
        int segments = 12;
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            double x = ax + (bx - ax) * t;
            double y = ay + (by - ay) * t;
            double z = az + (bz - az) * t;
            Vector offset = hand.transformLocalPoint(x, y, z);
            double wx = origin.getX() + offset.getX();
            double wy = origin.getY() + offset.getY();
            double wz = origin.getZ() + offset.getZ();
            for (Player viewer : viewers) {
                spawnDust(viewer, hand, wx, wy, wz, dust);
            }
        }
    }

    private static void spawnDust(
            Player viewer, ParticleHand hand, double x, double y, double z, Particle.DustOptions dust
    ) {
        viewer.spawnParticle(
                Particle.DUST, x, y, z, 1,
                0.0, 0.0, 0.0, 0.0, dust, hand.isForceParticles()
        );
    }

    private static double angleDelta(double a, double b) {
        double delta = (a - b) % 360.0;
        if (delta > 180.0) delta -= 360.0;
        if (delta < -180.0) delta += 360.0;
        return delta;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record RenderTuning(double sampleMultiplier, double particleSizeMultiplier) {
    }

    private record RenderSnapshot(
            World world,
            double x,
            double y,
            double z,
            double yaw,
            double pitch,
            double roll,
            double[] joints
    ) {
        static RenderSnapshot capture(ParticleHand hand, Location location) {
            double[] joints = new double[HandDigit.values().length * 3];
            int index = 0;
            for (HandDigit digit : HandDigit.values()) {
                for (int joint = 1; joint <= 3; joint++) {
                    joints[index++] = hand.getFingerState(digit).getJoint(joint);
                }
            }
            return new RenderSnapshot(
                    location.getWorld(),
                    location.getX(), location.getY(), location.getZ(),
                    hand.getYaw(), hand.getPitch(), hand.getRoll(),
                    joints
            );
        }

        boolean sameWorld(RenderSnapshot other) {
            return world == other.world;
        }
    }
}
