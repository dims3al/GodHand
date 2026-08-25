package xyz.dimseal.godHand.hand;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import xyz.dimseal.godHand.hand.render.DisplayMaterialPalette;
import xyz.dimseal.godHand.hand.render.HandDensity;
import xyz.dimseal.godHand.hand.render.HandPalette;
import xyz.dimseal.godHand.hand.render.WristStyle;
import xyz.dimseal.godHand.hand.skeleton.DigitPose;
import xyz.dimseal.godHand.hand.skeleton.HandDigit;
import xyz.dimseal.godHand.hand.skeleton.SkeletalHandModel;
import xyz.dimseal.godHand.math.Rotation3D;
import xyz.dimseal.godHand.model.ModelPoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * solid ItemDisplay renderer.
 *
 * This is not a particle-cloud approximation. It authors a
 * small set of overlapping cuboid primitives: broad palm blocks, tapered wrist
 * blocks, knuckle fillers and articulated phalanx blocks.  The result is meant
 * to read as one cubey hand with only small seams, while keeping the persistent
 * display count in the tens rather than the hundreds/thousands.
 */
public final class ItemDisplayHandRenderer {

    public static final String DISPLAY_TAG = "godhand_item_display";
    private static final Set<UUID> KNOWN_DISPLAY_IDS = ConcurrentHashMap.newKeySet();

    private final SkeletalHandModel skeletalModel = new SkeletalHandModel();
    private final List<ItemDisplay> displays = new ArrayList<>();
    private final List<Quat> lastRotations = new ArrayList<>();

    private World displayWorld;
    private HandDensity configuredDensity;
    private Location lastRootLocation;
    private boolean precisionMotionMode;

    public void render(ParticleHand hand) {
        render(hand, false, null);
    }

    /**
     * @param precisionMotion when true, use a one-tick carry profile that matches
     *                        the velocity-tethered grip carrier, so a mounted
     *                        player and visible palm share the same motion cadence.
     */
    public void render(ParticleHand hand, boolean precisionMotion) {
        render(hand, precisionMotion, null);
    }

    public void render(ParticleHand hand, boolean precisionMotion, Location rootOverride) {
        Location origin = rootOverride == null ? hand.getRenderLocation() : rootOverride.clone();
        World world = origin.getWorld();
        if (world == null) {
            clear();
            return;
        }

        List<DisplayPiece> pieces = buildPieces(hand);

        // bounded self-healing lifecycle.
        //
        // Older builds rebuilt the *entire* hand whenever one ItemDisplay briefly
        // reported invalid (chunk/tracking transitions can do that). Worse, clear()
        // skipped invalid entities, so an edge case could strand old displays and
        // spawn a complete replacement set every tick. That is how a single hand
        // could snowball into tens of thousands of ItemDisplays.
        //
        // The renderer is now strictly bounded: world/piece-count changes rebuild
        // once, while an unhealthy individual slot is replaced individually.
        if (displayWorld != world || displays.size() != pieces.size()) {
            clear();
            displayWorld = world;
            configuredDensity = hand.getDensity();
            precisionMotionMode = precisionMotion;
            spawnDisplays(world, origin, pieces.size(), precisionMotionMode);
        } else {
            configuredDensity = hand.getDensity();
            if (precisionMotionMode != precisionMotion) {
                precisionMotionMode = precisionMotion;
                applyMotionProfile(precisionMotionMode);
            }
            repairBrokenDisplays(world, origin, pieces.size(), precisionMotionMode);
        }
        lastRootLocation = origin.clone();

        Quat rootRotation = rootRotation(hand);
        double scale = hand.getScale() * hand.getDismissVisualScale();

        for (int i = 0; i < pieces.size(); i++) {
            ItemDisplay display = displays.get(i);
            if (!display.isValid()) continue;

            DisplayPiece piece = pieces.get(i);

            // ItemDisplay colors are represented with real Minecraft
            // block families. Named palettes get hand-picked shade materials;
            // arbitrary RGB settings fall back to the nearest concrete color.
            int tone = toneFor(piece);
            Material material = DisplayMaterialPalette.materialFor(
                    hand.getBaseColor(), hand.isShadingEnabled(), tone, pieceSalt(piece, i), piece.palmMass);
            ItemStack current = display.getItemStack();
            if (current == null || current.getType() != material) {
                display.setItemStack(new ItemStack(material));
            }

            // VOID is intentionally supernatural: every solid cuboid is rendered
            // full-bright so the cosmic material remains readable underground or
            // at night without applying the entity glowing-outline effect.
            display.setBrightness(HandPalette.isVoid(hand.getBaseColor())
                    ? new Display.Brightness(15, 15)
                    : null);

            Vector worldOffset = hand.transformLocalPoint(piece.cx, piece.cy, piece.cz);
            Location target = origin.clone().add(worldOffset);

            // Display entities have their own entity yaw/pitch in addition to the
            // transformation matrix. Never inherit yaw/pitch from the hand's Location:
            // doing so double-rotates the solid model on summon and after some actions.
            target.setYaw(0.0f);
            target.setPitch(0.0f);
            display.teleport(target);

            // Compose the exact same root basis used by Rotation3D with the piece-local
            // basis. Keep quaternion hemisphere continuity so client interpolation never
            // chooses the equivalent-but-opposite quaternion and visually flips a cuboid.
            Quat worldRotation = rootRotation.mul(piece.localRotation);
            Quat previous = i < lastRotations.size() ? lastRotations.get(i) : null;
            worldRotation = worldRotation.nearestEquivalent(previous);
            while (lastRotations.size() <= i) lastRotations.add(null);
            lastRotations.set(i, worldRotation);

            display.setTransformation(new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    worldRotation.toJoml(),
                    new Vector3f(
                            (float) Math.max(0.035, piece.sx * scale),
                            (float) Math.max(0.035, piece.sy * scale),
                            (float) Math.max(0.035, piece.sz * scale)
                    ),
                    new Quaternionf()
            ));
        }
    }

    public int getActiveDisplayCount() {
        return displays.size();
    }

    public void clear() {
        for (ItemDisplay display : displays) {
            // remove() is intentionally attempted even when Bukkit currently
            // reports the entity invalid. Skipping invalid references was the
            // old orphan path during chunk/tracking transitions.
            if (display != null) {
                try {
                    display.remove();
                } catch (Throwable ignored) {
                    // The periodic tagged-orphan sweep is the final fallback.
                } finally {
                    KNOWN_DISPLAY_IDS.remove(display.getUniqueId());
                }
            }
        }
        displays.clear();
        lastRotations.clear();
        displayWorld = null;
        configuredDensity = null;
        lastRootLocation = null;
        precisionMotionMode = false;
    }

    private void spawnDisplays(World world, Location origin, int count, boolean precisionMotion) {
        Location spawnLocation = origin.clone();
        spawnLocation.setYaw(0.0f);
        spawnLocation.setPitch(0.0f);

        for (int i = 0; i < count; i++) {
            displays.add(spawnDisplay(world, spawnLocation, precisionMotion));
        }
    }

    private ItemDisplay spawnDisplay(World world, Location spawnLocation, boolean precisionMotion) {
        ItemStack item = new ItemStack(Material.QUARTZ_BLOCK);
        ItemDisplay display = world.spawn(spawnLocation, ItemDisplay.class, entity -> {
            entity.setItemStack(item);
            // NONE avoids the extra vanilla "fixed item" shrink transform;
            // our Transformation scale is the complete model scale.
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setTeleportDuration(1);
            entity.setInterpolationDuration(precisionMotion ? 1 : 2);
            entity.setInterpolationDelay(0);
            entity.setViewRange(8.0f);
            entity.setShadowRadius(0.0f);
            entity.setShadowStrength(0.0f);
            entity.setPersistent(false);
            entity.addScoreboardTag(DISPLAY_TAG);
        });
        KNOWN_DISPLAY_IDS.add(display.getUniqueId());
        return display;
    }

    private void repairBrokenDisplays(World world, Location origin, int expectedCount, boolean precisionMotion) {
        if (displays.size() != expectedCount) return;
        int chunkX = origin.getBlockX() >> 4;
        int chunkZ = origin.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) return;
        Location spawnLocation = origin.clone();
        spawnLocation.setYaw(0.0f);
        spawnLocation.setPitch(0.0f);

        for (int i = 0; i < displays.size(); i++) {
            ItemDisplay display = displays.get(i);
            boolean healthy = display != null
                    && display.isValid()
                    && !display.isDead()
                    && display.getWorld() == world;
            if (healthy) continue;

            if (display != null) {
                try { display.remove(); } catch (Throwable ignored) {}
                KNOWN_DISPLAY_IDS.remove(display.getUniqueId());
            }
            displays.set(i, spawnDisplay(world, spawnLocation, precisionMotion));
            if (i < lastRotations.size()) lastRotations.set(i, null);
        }
    }

    /** UUIDs that this renderer currently owns; used by the orphan watchdog. */
    public Set<UUID> getTrackedDisplayIds() {
        Set<UUID> ids = new HashSet<>();
        for (ItemDisplay display : displays) {
            if (display != null) ids.add(display.getUniqueId());
        }
        return ids;
    }


    /**
     * Removes known GodHand displays that are no longer owned by a live renderer.
     * Uses UUID lookup instead of scanning every entity in every world.
     */
    public static int cleanupKnownOrphans(Set<UUID> keep) {
        int removed = 0;
        for (UUID id : new HashSet<>(KNOWN_DISPLAY_IDS)) {
            if (keep != null && keep.contains(id)) continue;
            org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(id);
            if (entity == null) {
                KNOWN_DISPLAY_IDS.remove(id);
                continue;
            }
            if (entity instanceof ItemDisplay display
                    && display.getScoreboardTags().contains(DISPLAY_TAG)) {
                try {
                    display.remove();
                    removed++;
                } catch (Throwable ignored) {}
            }
            KNOWN_DISPLAY_IDS.remove(id);
        }
        return removed;
    }


    public static int getKnownDisplayCount() {
        return KNOWN_DISPLAY_IDS.size();
    }

    /** Expensive server-wide count intended only for explicit debug/status checks. */
    public static int countTaggedDisplaysServerWide() {
        int count = 0;
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getScoreboardTags().contains(DISPLAY_TAG)) count++;
            }
        }
        return count;
    }

    /**
     * Last-resort cleanup for a renderer runaway. Unlike the normal UUID orphan
     * cleanup, this deliberately scans loaded worlds so even displays that escaped
     * the registry are removed. This is only used by the emergency watchdog/debug command.
     */
    public static int purgeTaggedDisplaysServerWide() {
        int removed = 0;
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (!display.getScoreboardTags().contains(DISPLAY_TAG)) continue;
                try {
                    display.remove();
                    removed++;
                } catch (Throwable ignored) { }
                KNOWN_DISPLAY_IDS.remove(display.getUniqueId());
            }
        }
        KNOWN_DISPLAY_IDS.removeIf(id -> org.bukkit.Bukkit.getEntity(id) == null);
        return removed;
    }

    private void applyMotionProfile(boolean precisionMotion) {
        int teleportDuration = 1;
        int interpolationDuration = precisionMotion ? 1 : 2;
        for (ItemDisplay display : displays) {
            if (display == null || !display.isValid()) continue;
            display.setTeleportDuration(teleportDuration);
            display.setInterpolationDuration(interpolationDuration);
            display.setInterpolationDelay(0);
        }
    }

    private List<DisplayPiece> buildPieces(ParticleHand hand) {
        List<DisplayPiece> out = new ArrayList<>(64);

        if (hand.getWristStyle() == WristStyle.LEGACY) {
            // Legacy body mirrors the original rectangular palm/wrist read.
            addBox(out, 0.00, 0.06, 0.00, 0.96, 1.12, 0.235);
            addBox(out, 0.00, -0.70, 0.00, 0.72, 0.46, 0.205);
            addBox(out, 0.00, -0.98, 0.00, 0.52, 0.26, 0.180);
        } else {
            // --- Anatomical palm: overlapping broad solids rather than sampled dots. ---
            // The pieces overlap by design so viewing from an oblique angle does not
            // reveal a hollow shell.
            addBox(out, 0.00, 0.10, 0.00, 0.72, 0.90, 0.27);
            addBox(out, 0.00, -0.31, 0.00, 0.76, 0.38, 0.25);
            addBox(out, -0.385, 0.08, 0.00, 0.20, 0.88, 0.245);
            addBox(out,  0.385, 0.08, 0.00, 0.20, 0.88, 0.245);
            addBox(out, 0.00, 0.535, 0.00, 0.72, 0.24, 0.255);

            // Rounded cubey shoulders into the four fingers.
            addBox(out, -0.34, 0.600, 0.00, 0.205, 0.235, 0.245);
            addBox(out, -0.12, 0.625, 0.00, 0.205, 0.235, 0.250);
            addBox(out,  0.12, 0.645, 0.00, 0.205, 0.235, 0.250);
            addBox(out,  0.34, 0.600, 0.00, 0.205, 0.235, 0.245);
            addBox(out,  0.445, 0.105, 0.00, 0.205, 0.34, 0.245);

            // --- Wrist: overlapping tapered stair-step solids. ---
            addBox(out, 0.00, -0.565, 0.00, 0.72, 0.22, 0.245);
            addBox(out, 0.00, -0.735, 0.00, 0.62, 0.24, 0.225);
            addBox(out, 0.00, -0.915, 0.00, 0.53, 0.24, 0.205);
            addBox(out, 0.00, -1.075, 0.00, 0.45, 0.18, 0.190);

            // Higher densities add a few broad bevel fillers, not more tiny grains.
            if (hand.getDensity() == HandDensity.HIGH || hand.getDensity() == HandDensity.ULTRA) {
                addBox(out, -0.315, -0.52, 0.00, 0.18, 0.28, 0.215);
                addBox(out,  0.315, -0.52, 0.00, 0.18, 0.28, 0.215);
                addBox(out, -0.265, -0.72, 0.00, 0.16, 0.24, 0.205);
                addBox(out,  0.265, -0.72, 0.00, 0.16, 0.24, 0.205);
            }
        }

        // --- Articulated fingers. ---
        for (HandDigit digit : HandDigit.values()) {
            DigitPose pose = skeletalModel.evaluateDigitPose(hand, digit);
            ModelPoint[] points = {pose.root(), pose.proximalEnd(), pose.middleEnd(), pose.tip()};

            for (int segment = 0; segment < 3; segment++) {
                double diameter = segmentDiameter(digit, segment);
                addBone(out, points[segment], points[segment + 1], diameter);
            }

            // Cubic joint fillers overlap neighboring phalanges and hide cracks.
            if (hand.getDensity() != HandDensity.LOW) {
                addJoint(out, pose.root(), jointDiameter(digit, 0));
                addJoint(out, pose.proximalEnd(), jointDiameter(digit, 1));
                addJoint(out, pose.middleEnd(), jointDiameter(digit, 2));
            }
            if (hand.getDensity() == HandDensity.ULTRA) {
                addJoint(out, pose.tip(), jointDiameter(digit, 3) * 0.92);
            }
        }

        return out;
    }

    private static void addBox(List<DisplayPiece> out, double x, double y, double z, double sx, double sy, double sz) {
        out.add(new DisplayPiece(x, y, z, sx, sy, sz, Quat.IDENTITY, true));
    }

    private static void addBone(List<DisplayPiece> out, ModelPoint a, ModelPoint b, double diameter) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double dz = b.z() - a.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-8) return;

        Quat rotation = boneRotation(dx / length, dy / length, dz / length);

        out.add(new DisplayPiece(
                (a.x() + b.x()) * 0.5,
                (a.y() + b.y()) * 0.5,
                (a.z() + b.z()) * 0.5,
                diameter,
                length * 1.12, // overlap into the joint fillers
                diameter,
                rotation,
                false
        ));
    }

    private static void addJoint(List<DisplayPiece> out, ModelPoint p, double diameter) {
        out.add(new DisplayPiece(p.x(), p.y(), p.z(), diameter, diameter, diameter, Quat.IDENTITY, false));
    }

    private static double segmentDiameter(HandDigit digit, int segment) {
        double[] base = switch (digit) {
            case THUMB -> new double[]{0.165, 0.145, 0.120};
            case INDEX -> new double[]{0.145, 0.128, 0.105};
            case MIDDLE -> new double[]{0.150, 0.132, 0.108};
            case RING -> new double[]{0.143, 0.126, 0.104};
            case PINKY -> new double[]{0.130, 0.116, 0.097};
        };
        return base[Math.max(0, Math.min(2, segment))];
    }

    private static double jointDiameter(HandDigit digit, int joint) {
        double base = switch (digit) {
            case THUMB -> 0.175;
            case MIDDLE -> 0.158;
            case INDEX, RING -> 0.153;
            case PINKY -> 0.140;
        };
        return base * (1.0 - Math.min(3, joint) * 0.075);
    }

    /**
     * Builds the display root quaternion from the exact basis produced by Rotation3D.
     *
     * This intentionally does NOT rebuild the rotation with JOML Euler helpers.
     * Rotation3D's Minecraft yaw convention uses the opposite sign from a standard
     * right-handed +Y JOML rotation. Deriving from the transformed basis guarantees
     * that display orientation and display position can never disagree again.
     */
    private static Quat rootRotation(ParticleHand hand) {
        Vector xAxis = Rotation3D.rotate(1.0, 0.0, 0.0,
                hand.getYaw(), hand.getPitch(), hand.getRoll());
        Vector yAxis = Rotation3D.rotate(0.0, 1.0, 0.0,
                hand.getYaw(), hand.getPitch(), hand.getRoll());
        Vector zAxis = Rotation3D.rotate(0.0, 0.0, 1.0,
                hand.getYaw(), hand.getPitch(), hand.getRoll());
        return Quat.fromBasis(xAxis, yAxis, zAxis);
    }

    /**
     * Stable local basis for a phalanx whose cuboid +Y axis follows the bone.
     * Projecting palm-local +X onto the bone-normal plane avoids rotationTo's
     * arbitrary twist/flip behavior near opposite directions.
     */
    private static Quat boneRotation(double yx, double yy, double yz) {
        Vector yAxis = new Vector(yx, yy, yz).normalize();

        Vector xReference = new Vector(1.0, 0.0, 0.0);
        Vector xAxis = xReference.clone().subtract(yAxis.clone().multiply(xReference.dot(yAxis)));

        if (xAxis.lengthSquared() < 1.0e-8) {
            Vector zReference = new Vector(0.0, 0.0, 1.0);
            xAxis = zReference.clone().subtract(yAxis.clone().multiply(zReference.dot(yAxis)));
        }

        xAxis.normalize();
        Vector zAxis = xAxis.clone().crossProduct(yAxis).normalize();
        return Quat.fromBasis(xAxis, yAxis, zAxis);
    }

    private static int toneFor(DisplayPiece piece) {
        // Coarse solid shading: left/underside pieces are darker, right/upper
        // pieces brighter, and the central mass remains the base material.
        // This keeps the cubey renderer readable without multiplying entities.
        if (piece.cx < -0.22 || piece.cz < -0.12) return DisplayMaterialPalette.SHADOW;
        if (piece.cx > 0.22 || piece.cy > 0.62) return DisplayMaterialPalette.HIGHLIGHT;
        return DisplayMaterialPalette.PRIMARY;
    }

    private static int pieceSalt(DisplayPiece piece, int index) {
        long x = Math.round(piece.cx * 1000.0);
        long y = Math.round(piece.cy * 1000.0);
        long z = Math.round(piece.cz * 1000.0);
        long mixed = x * 73428767L ^ y * 912931L ^ z * 19349663L ^ index * 83492791L;
        return (int) (mixed ^ (mixed >>> 32));
    }

    private record DisplayPiece(
            double cx, double cy, double cz,
            double sx, double sy, double sz,
            Quat localRotation,
            boolean palmMass
    ) {}

    /**
     * Tiny quaternion value type used so the renderer's math is independent of
     * JOML Euler/multiplication conventions. It also lets us preserve quaternion
     * hemisphere continuity between frames before handing values to Paper.
     */
    private record Quat(double x, double y, double z, double w) {

        private static final Quat IDENTITY = new Quat(0.0, 0.0, 0.0, 1.0);

        static Quat fromBasis(Vector xAxis, Vector yAxis, Vector zAxis) {
            // Matrix columns are the transformed local X/Y/Z basis vectors.
            double m00 = xAxis.getX(), m01 = yAxis.getX(), m02 = zAxis.getX();
            double m10 = xAxis.getY(), m11 = yAxis.getY(), m12 = zAxis.getY();
            double m20 = xAxis.getZ(), m21 = yAxis.getZ(), m22 = zAxis.getZ();

            double x, y, z, w;
            double trace = m00 + m11 + m22;

            if (trace > 0.0) {
                double s = Math.sqrt(trace + 1.0) * 2.0;
                w = 0.25 * s;
                x = (m21 - m12) / s;
                y = (m02 - m20) / s;
                z = (m10 - m01) / s;
            } else if (m00 > m11 && m00 > m22) {
                double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2.0;
                w = (m21 - m12) / s;
                x = 0.25 * s;
                y = (m01 + m10) / s;
                z = (m02 + m20) / s;
            } else if (m11 > m22) {
                double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2.0;
                w = (m02 - m20) / s;
                x = (m01 + m10) / s;
                y = 0.25 * s;
                z = (m12 + m21) / s;
            } else {
                double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2.0;
                w = (m10 - m01) / s;
                x = (m02 + m20) / s;
                y = (m12 + m21) / s;
                z = 0.25 * s;
            }

            return new Quat(x, y, z, w).normalized();
        }

        Quat mul(Quat other) {
            return new Quat(
                    w * other.x + x * other.w + y * other.z - z * other.y,
                    w * other.y - x * other.z + y * other.w + z * other.x,
                    w * other.z + x * other.y - y * other.x + z * other.w,
                    w * other.w - x * other.x - y * other.y - z * other.z
            ).normalized();
        }

        Quat normalized() {
            double len = Math.sqrt(x * x + y * y + z * z + w * w);
            if (len < 1.0e-12) return IDENTITY;
            return new Quat(x / len, y / len, z / len, w / len);
        }

        double dot(Quat other) {
            return x * other.x + y * other.y + z * other.z + w * other.w;
        }

        Quat nearestEquivalent(Quat previous) {
            if (previous == null || dot(previous) >= 0.0) return this;
            return new Quat(-x, -y, -z, -w);
        }

        Quaternionf toJoml() {
            return new Quaternionf((float) x, (float) y, (float) z, (float) w);
        }
    }
}
