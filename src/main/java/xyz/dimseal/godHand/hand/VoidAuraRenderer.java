package xyz.dimseal.godHand.hand;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import xyz.dimseal.godHand.hand.render.HandPalette;

/**
 * ambient VOID aura shared by both visual backends.
 * The solid hand stays block-based, but a low-cost portal haze gives VOID the
 * noisy Enderman/cosmic emission requested without converting the whole model
 * back into particles.
 */
public final class VoidAuraRenderer {

    private static final Particle.DustTransition ABYSS = new Particle.DustTransition(
            Color.fromRGB(2, 0, 5), Color.fromRGB(74, 12, 126), 1.02f);
    private static final Particle.DustTransition VIOLET = new Particle.DustTransition(
            Color.fromRGB(34, 0, 64), Color.fromRGB(125, 30, 205), 0.90f);
    private static final Particle.DustTransition RARE_PINK = new Particle.DustTransition(
            Color.fromRGB(96, 14, 155), Color.fromRGB(230, 48, 255), 0.72f);

    private static final double NORMAL_RANGE = 160.0;
    private static final double FORCED_RANGE = 320.0;

    public void render(ParticleHand hand) {
        if (hand == null || !HandPalette.isVoid(hand.getBaseColor())) return;
        Location origin = hand.getLocation();
        World world = origin.getWorld();
        if (world == null) return;

        double range = hand.isForceParticles() ? FORCED_RANGE : NORMAL_RANGE;
        double rangeSquared = range * range;
        double scale = hand.getScale();
        double ox = Math.max(0.55, scale * 0.43);
        double oy = Math.max(0.75, scale * 0.62);
        double oz = Math.max(0.40, scale * 0.30);

        for (Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(origin) > rangeSquared) continue;

            // Layered cosmic haze. These are batched spawn calls rather than
            // individual per-point loops, so VOID gets dramatically richer without
            // multiplying the server-side ItemDisplay count.
            viewer.spawnParticle(Particle.PORTAL, origin, 28,
                    ox, oy, oz, 0.13, null, hand.isForceParticles());
            viewer.spawnParticle(Particle.REVERSE_PORTAL, origin, 14,
                    ox * 0.88, oy * 0.88, oz * 0.88, 0.052, null, hand.isForceParticles());
            viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION, origin, 12,
                    ox * 0.74, oy * 0.72, oz * 0.72, 0.010, ABYSS, hand.isForceParticles());
            viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION, origin, 8,
                    ox * 0.58, oy * 0.62, oz * 0.58, 0.008, VIOLET, hand.isForceParticles());
            viewer.spawnParticle(Particle.DUST_COLOR_TRANSITION, origin, 2,
                    ox * 0.44, oy * 0.50, oz * 0.44, 0.004, RARE_PINK, hand.isForceParticles());

            // White stellar points are the only bright non-purple component.
            viewer.spawnParticle(Particle.END_ROD, origin, 5,
                    ox * 0.78, oy * 0.78, oz * 0.78, 0.006, null, hand.isForceParticles());
        }
    }
}
