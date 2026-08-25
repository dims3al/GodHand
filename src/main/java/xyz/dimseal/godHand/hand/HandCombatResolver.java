package xyz.dimseal.godHand.hand;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import xyz.dimseal.godHand.math.Rotation3D;

import java.util.Set;
import java.util.UUID;

/** Oriented palm-volume combat and destructive impact resolver. */
public final class HandCombatResolver {

    private HandDeathMessages.Style deathStyle = HandDeathMessages.Style.SLAM;

    public void setDeathStyle(HandDeathMessages.Style deathStyle) {
        if (deathStyle != null) this.deathStyle = deathStyle;
    }

    public static final double MIN_X = -0.60;
    public static final double MAX_X = 0.60;
    public static final double MIN_Y = -0.60;
    public static final double MAX_Y = 0.72;
    public static final double MIN_Z = -0.18;
    public static final double MAX_Z = 0.48;

    public ImpactResult resolveSlam(ParticleHand hand, double damage, double horizontalKnockback, double verticalKnockback) {
        Set<UUID> hit = new java.util.HashSet<>();
        int count = resolveSlamContact(hand, damage, horizontalKnockback, verticalKnockback, hit, 0.0);
        playImpactEffects(hand);
        return new ImpactResult(count);
    }

    /** Endpoint slam contact with optional normalized local-space padding. */
    public int resolveSlamContact(
            ParticleHand hand,
            double damage,
            double horizontalKnockback,
            double verticalKnockback,
            Set<UUID> alreadyHit,
            double padding
    ) {
        World world = hand.getWorld();
        if (world == null) return 0;
        int newHits = 0;
        Location origin = hand.getLocation();

        for (Player player : world.getPlayers()) {
            if (!valid(player)) continue;
            UUID id = player.getUniqueId();
            if (alreadyHit != null && alreadyHit.contains(id)) continue;
            if (!intersectsPlayerPadded(hand, player, padding)) continue;

            if (alreadyHit != null) alreadyHit.add(id);
            newHits++;
            if (damage > 0.0) HandDeathMessages.damage(player, damage, deathStyle);
            player.setVelocity(radialKnockback(hand, player, origin, horizontalKnockback, verticalKnockback));
        }
        return newHits;
    }

    /** Swept slam contact used by the late-correcting descent. */
    public int resolveSlamSweptContact(
            ParticleHand hand,
            Location previousOrigin,
            Location currentOrigin,
            double damage,
            double horizontalKnockback,
            double verticalKnockback,
            Set<UUID> alreadyHit,
            double padding
    ) {
        World world = hand.getWorld();
        if (!validSweepWorld(world, previousOrigin, currentOrigin)) return 0;
        Vector delta = currentOrigin.toVector().subtract(previousOrigin.toVector());
        int steps = sweepSteps(hand, delta.length());
        int newHits = 0;

        for (Player player : world.getPlayers()) {
            if (!valid(player)) continue;
            UUID id = player.getUniqueId();
            if (alreadyHit != null && alreadyHit.contains(id)) continue;
            if (!intersectsAlongSweep(hand, player, previousOrigin, delta, steps, padding)) continue;

            if (alreadyHit != null) alreadyHit.add(id);
            newHits++;
            if (damage > 0.0) HandDeathMessages.damage(player, damage, deathStyle);
            player.setVelocity(radialKnockback(hand, player, currentOrigin, horizontalKnockback, verticalKnockback));
        }
        return newHits;
    }

    public int resolveDirectionalContact(
            ParticleHand hand,
            double damage,
            Vector strikeDirection,
            double horizontalKnockback,
            double verticalKnockback,
            Set<UUID> alreadyHit
    ) {
        World world = hand.getWorld();
        if (world == null) return 0;
        Vector horizontal = directionalKnockback(hand, strikeDirection, horizontalKnockback);
        int newHits = 0;
        for (Player player : world.getPlayers()) {
            if (!valid(player)) continue;
            UUID id = player.getUniqueId();
            if (alreadyHit != null && alreadyHit.contains(id)) continue;
            if (!intersectsPlayer(hand, player)) continue;
            if (alreadyHit != null) alreadyHit.add(id);
            newHits++;
            if (damage > 0.0) HandDeathMessages.damage(player, damage, deathStyle);
            Vector velocity = horizontal.clone();
            velocity.setY(verticalKnockback);
            player.setVelocity(velocity);
        }
        return newHits;
    }

    public int resolveDirectionalSweptContact(
            ParticleHand hand,
            Location previousOrigin,
            Location currentOrigin,
            double damage,
            Vector strikeDirection,
            double horizontalKnockback,
            double verticalKnockback,
            Set<UUID> alreadyHit
    ) {
        return resolveDirectionalSweptContact(hand, previousOrigin, currentOrigin, damage, strikeDirection,
                horizontalKnockback, verticalKnockback, alreadyHit, 0.0);
    }

    /** overload with a small normalized hit-volume padding. */
    public int resolveDirectionalSweptContact(
            ParticleHand hand,
            Location previousOrigin,
            Location currentOrigin,
            double damage,
            Vector strikeDirection,
            double horizontalKnockback,
            double verticalKnockback,
            Set<UUID> alreadyHit,
            double padding
    ) {
        World world = hand.getWorld();
        if (!validSweepWorld(world, previousOrigin, currentOrigin)) return 0;
        Vector delta = currentOrigin.toVector().subtract(previousOrigin.toVector());
        int steps = sweepSteps(hand, delta.length());
        Vector horizontal = directionalKnockback(hand, strikeDirection, horizontalKnockback);
        int newHits = 0;

        for (Player player : world.getPlayers()) {
            if (!valid(player)) continue;
            UUID id = player.getUniqueId();
            if (alreadyHit != null && alreadyHit.contains(id)) continue;
            if (!intersectsAlongSweep(hand, player, previousOrigin, delta, steps, padding)) continue;

            if (alreadyHit != null) alreadyHit.add(id);
            newHits++;
            if (damage > 0.0) HandDeathMessages.damage(player, damage, deathStyle);
            Vector velocity = horizontal.clone();
            velocity.setY(verticalKnockback);
            player.setVelocity(velocity);
        }
        return newHits;
    }

    /**
     * True-God punch: still uses normal Bukkit damage first, then guarantees a
     * minimum health loss so high-tier armor cannot turn the hit into a tap.
     */
    public int resolveHeavyDirectionalSweptContact(
            ParticleHand hand,
            Location previousOrigin,
            Location currentOrigin,
            double rawDamage,
            double minimumHealthLoss,
            Vector strikeDirection,
            double horizontalKnockback,
            double verticalKnockback,
            Set<UUID> alreadyHit,
            double padding
    ) {
        World world = hand.getWorld();
        if (!validSweepWorld(world, previousOrigin, currentOrigin)) return 0;
        Vector delta = currentOrigin.toVector().subtract(previousOrigin.toVector());
        int steps = sweepSteps(hand, delta.length());
        Vector horizontal = directionalKnockback(hand, strikeDirection, horizontalKnockback);
        int newHits = 0;

        for (Player player : world.getPlayers()) {
            if (!valid(player)) continue;
            UUID id = player.getUniqueId();
            if (alreadyHit != null && alreadyHit.contains(id)) continue;
            if (!intersectsAlongSweep(hand, player, previousOrigin, delta, steps, padding)) continue;

            if (alreadyHit != null) alreadyHit.add(id);
            newHits++;
            applyHeavyDamage(player, rawDamage, minimumHealthLoss);
            Vector velocity = horizontal.clone();
            velocity.setY(verticalKnockback);
            player.setVelocity(velocity);
        }
        return newHits;
    }

    /** Destructive admin smash: heavy entity damage plus a block-breaking explosion. */
    public int resolveDestructiveSmash(ParticleHand hand, Location impact, float explosionPower, Player primaryTarget) {
        World world = impact == null ? null : impact.getWorld();
        if (world == null) return 0;

        double radius = Math.max(5.0, explosionPower * 1.8);
        double radiusSq = radius * radius;
        int hitCount = 0;
        for (Player player : world.getPlayers()) {
            if (!valid(player) || player.getLocation().distanceSquared(impact) > radiusSq) continue;
            hitCount++;
            boolean primary = primaryTarget != null && primaryTarget.getUniqueId().equals(player.getUniqueId());
            applyHeavyDamage(player, primary ? 220.0 : 100.0, primary ? 18.0 : 8.0);

            Vector push = player.getLocation().toVector().subtract(impact.toVector());
            if (push.lengthSquared() < 1.0e-8) push = new Vector(1.0, 0.0, 0.0);
            push.normalize().multiply(primary ? 4.2 : 3.0);
            push.setY(primary ? 1.45 : 1.05);
            player.setVelocity(push);
        }

        // Paper 26.2 supports the Location/power/setFire/breakBlocks/source overload.
        world.createExplosion(impact, explosionPower, false, true, null);
        playDestructiveEffects(hand, impact, explosionPower);
        return hitCount;
    }

    private void applyHeavyDamage(Player player, double rawDamage, double minimumHealthLoss) {
        double before = player.getHealth();
        if (rawDamage > 0.0) {
            HandDeathMessages.markDirect(player, deathStyle);
            player.damage(rawDamage);
        }
        if (player.isDead() || minimumHealthLoss <= 0.0) return;

        double guaranteedCeiling = Math.max(0.0, before - minimumHealthLoss);
        if (player.getHealth() > guaranteedCeiling) {
            player.setHealth(guaranteedCeiling);
        }
        if (!player.isDead()) HandDeathMessages.clear(player);
    }

    private Vector radialKnockback(ParticleHand hand, Player player, Location origin, double horizontal, double vertical) {
        Vector push = player.getLocation().toVector().subtract(origin.toVector());
        push.setY(0.0);
        if (push.lengthSquared() < 1.0e-8) {
            push = hand.transformLocalPoint(0.0, 1.0, 0.0);
            push.setY(0.0);
        }
        if (push.lengthSquared() < 1.0e-8) push = new Vector(1.0, 0.0, 0.0);
        push.normalize().multiply(horizontal);
        push.setY(vertical);
        return push;
    }

    private Vector directionalKnockback(ParticleHand hand, Vector strikeDirection, double horizontalKnockback) {
        Vector horizontal = strikeDirection == null ? new Vector(0.0, 0.0, 0.0) : strikeDirection.clone();
        horizontal.setY(0.0);
        if (horizontal.lengthSquared() < 1.0e-8) {
            horizontal = hand.transformLocalPoint(0.0, 0.0, 1.0);
            horizontal.setY(0.0);
        }
        if (horizontal.lengthSquared() < 1.0e-8) horizontal = new Vector(1.0, 0.0, 0.0);
        return horizontal.normalize().multiply(horizontalKnockback);
    }

    private boolean intersectsAlongSweep(ParticleHand hand, Player player, Location start, Vector delta, int steps, double padding) {
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Location sampleOrigin = start.clone().add(delta.clone().multiply(t));
            if (intersectsPlayerAtOrigin(hand, player, sampleOrigin, padding)) return true;
        }
        return false;
    }

    private int sweepSteps(ParticleHand hand, double distance) {
        double spacing = Math.max(0.16, hand.getScale() * 0.10);
        return Math.max(1, (int) Math.ceil(distance / spacing));
    }

    private boolean validSweepWorld(World world, Location previousOrigin, Location currentOrigin) {
        return world != null && previousOrigin != null && currentOrigin != null
                && previousOrigin.getWorld() != null && previousOrigin.getWorld().equals(world)
                && currentOrigin.getWorld() != null && currentOrigin.getWorld().equals(world);
    }

    private static boolean valid(Player player) {
        return player != null && player.isOnline() && !player.isDead();
    }

    private boolean intersectsPlayerAtOrigin(ParticleHand hand, Player player, Location rootOrigin, double padding) {
        Location feet = player.getLocation();
        Location eye = player.getEyeLocation();
        double eyeHeight = eye.getY() - feet.getY();
        if (!Double.isFinite(eyeHeight) || eyeHeight <= 0.1) eyeHeight = 1.62;
        return containsAtOrigin(hand, rootOrigin, feet, padding)
                || containsAtOrigin(hand, rootOrigin, feet.clone().add(0.0, eyeHeight * 0.35, 0.0), padding)
                || containsAtOrigin(hand, rootOrigin, feet.clone().add(0.0, eyeHeight * 0.68, 0.0), padding)
                || containsAtOrigin(hand, rootOrigin, eye, padding);
    }

    private boolean containsAtOrigin(ParticleHand hand, Location rootOrigin, Location worldPoint, double padding) {
        Vector delta = worldPoint.toVector().subtract(rootOrigin.toVector());
        Vector localScaled = Rotation3D.inverseRotate(
                delta.getX(), delta.getY(), delta.getZ(),
                hand.getYaw(), hand.getPitch(), hand.getRoll()
        );
        Vector local = localScaled.multiply(1.0 / hand.getScale());
        return local.getX() >= MIN_X - padding && local.getX() <= MAX_X + padding
                && local.getY() >= MIN_Y - padding && local.getY() <= MAX_Y + padding
                && local.getZ() >= MIN_Z - padding && local.getZ() <= MAX_Z + padding;
    }

    public boolean intersectsPlayer(ParticleHand hand, Player player) {
        return intersectsPlayerPadded(hand, player, 0.0);
    }

    public boolean intersectsPlayerPadded(ParticleHand hand, Player player, double padding) {
        Location feet = player.getLocation();
        Location eye = player.getEyeLocation();
        double eyeHeight = eye.getY() - feet.getY();
        if (!Double.isFinite(eyeHeight) || eyeHeight <= 0.1) eyeHeight = 1.62;
        return contains(hand, feet, padding)
                || contains(hand, feet.clone().add(0.0, eyeHeight * 0.35, 0.0), padding)
                || contains(hand, feet.clone().add(0.0, eyeHeight * 0.68, 0.0), padding)
                || contains(hand, eye, padding);
    }

    public boolean contains(ParticleHand hand, Location worldPoint) { return contains(hand, worldPoint, 0.0); }

    private boolean contains(ParticleHand hand, Location worldPoint, double padding) {
        Vector local = hand.inverseTransformWorldPoint(worldPoint);
        return local.getX() >= MIN_X - padding && local.getX() <= MAX_X + padding
                && local.getY() >= MIN_Y - padding && local.getY() <= MAX_Y + padding
                && local.getZ() >= MIN_Z - padding && local.getZ() <= MAX_Z + padding;
    }

    public void playImpactEffects(ParticleHand hand) {
        Location impact = hand.getLocation();
        World world = impact.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.EXPLOSION, impact, 10,
                Math.max(0.3, hand.getScale() * 0.28), 0.20,
                Math.max(0.3, hand.getScale() * 0.28), 0.0, null, hand.isForceParticles());
        world.spawnParticle(Particle.CLOUD, impact, 36,
                Math.max(0.5, hand.getScale() * 0.38), 0.25,
                Math.max(0.5, hand.getScale() * 0.38), 0.08, null, hand.isForceParticles());
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 2.2f, 0.72f);
    }

    public void playHeavyImpactEffects(ParticleHand hand) {
        Location impact = hand.getLocation();
        World world = impact.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.EXPLOSION, impact, 18,
                Math.max(0.5, hand.getScale() * 0.34), 0.28,
                Math.max(0.5, hand.getScale() * 0.34), 0.0, null, hand.isForceParticles());
        world.spawnParticle(Particle.CLOUD, impact, 54,
                Math.max(0.7, hand.getScale() * 0.45), 0.34,
                Math.max(0.7, hand.getScale() * 0.45), 0.11, null, hand.isForceParticles());
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.48f);
    }

    private void playDestructiveEffects(ParticleHand hand, Location impact, float power) {
        World world = impact.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.EXPLOSION, impact, Math.max(20, (int) (power * 6.0f)),
                Math.max(1.0, power * 0.55), Math.max(0.5, power * 0.25),
                Math.max(1.0, power * 0.55), 0.0, null, hand.isForceParticles());
        world.spawnParticle(Particle.CLOUD, impact, Math.max(80, (int) (power * 18.0f)),
                Math.max(1.4, power * 0.8), 0.65,
                Math.max(1.4, power * 0.8), 0.18, null, hand.isForceParticles());
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 5.0f, 0.34f);
    }

    public record ImpactResult(int hitCount) {}
}
