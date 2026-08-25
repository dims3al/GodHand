package xyz.dimseal.godHand.hand;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import xyz.dimseal.godHand.hand.render.HandPalette;

/**
 * Lightweight presentation layer.
 *
 * This intentionally does not own gameplay state. It adds small movement,
 * charge, grip and action accents around the already-existing Hand behavior so
 * attacks read better without turning the 20 Hz engine into a particle storm.
 */
public final class HandPolishRenderer {

    private int tick;

    public void render(ParticleHand hand) {
        if (hand == null || hand.getWorld() == null || hand.isDismissing()) return;
        tick++;

        renderMotionTrail(hand);
        renderGripReadability(hand);
        renderPaletteAura(hand);
        renderPhaseAccent(hand);
        renderActionAccent(hand);
    }

    public void reset() {
        tick = 0;
    }

    private void renderMotionTrail(ParticleHand hand) {
        double vx = hand.getMotionVelocityX();
        double vy = hand.getMotionVelocityY();
        double vz = hand.getMotionVelocityZ();
        double speedSquared = vx * vx + vy * vy + vz * vz;
        if (speedSquared < 0.10 || (tick & 1) != 0) return;

        Vector velocity = new Vector(vx, vy, vz);
        double speed = Math.sqrt(speedSquared);
        if (velocity.lengthSquared() < 1.0e-8) return;
        velocity.normalize();

        Location trail = hand.getLocation().clone().subtract(
                velocity.multiply(Math.min(2.0, Math.max(0.45, hand.getScale() * 0.22)))
        );
        World world = trail.getWorld();
        if (world == null) return;

        double spread = Math.max(0.08, Math.min(0.45, hand.getScale() * 0.055));
        float size = (float) Math.max(0.35, Math.min(0.92, hand.getScale() * 0.10));
        world.spawnParticle(
                Particle.DUST,
                trail,
                speed > 1.4 ? 4 : 2,
                spread, spread, spread,
                0.0,
                new Particle.DustOptions(hand.getBaseColor(), size),
                hand.isForceParticles()
        );

        if (speed > 1.55 && tick % 4 == 0) {
            world.spawnParticle(
                    Particle.CLOUD,
                    trail,
                    2,
                    spread * 0.70, spread * 0.45, spread * 0.70,
                    0.015,
                    null,
                    hand.isForceParticles()
            );
        }
    }

    private void renderGripReadability(ParticleHand hand) {
        if (!hand.hasActiveGrip() || tick % 2 != 0) return;
        Location grip = hand.getActiveGripWorldPoint();
        if (grip == null || grip.getWorld() == null) return;

        double spread = Math.max(0.10, Math.min(0.38, hand.getScale() * 0.065));
        float size = (float) Math.max(0.42, Math.min(0.85, hand.getScale() * 0.095));
        grip.getWorld().spawnParticle(
                Particle.DUST,
                grip,
                5,
                spread, spread * 0.80, spread,
                0.0,
                new Particle.DustOptions(hand.getBaseColor(), size),
                hand.isForceParticles()
        );

        if (tick % 6 == 0) {
            grip.getWorld().spawnParticle(
                    Particle.END_ROD,
                    grip,
                    1,
                    spread * 0.45, spread * 0.45, spread * 0.45,
                    0.01,
                    null,
                    hand.isForceParticles()
            );
        }
    }


    private void renderPaletteAura(ParticleHand hand) {
        Location at = hand.getLocation();
        World world = at.getWorld();
        if (world == null) return;
        double spread = Math.max(0.18, Math.min(0.72, hand.getScale() * 0.11));

        if (HandPalette.isSpectral(hand.getBaseColor())) {
            if (tick % 3 == 0) {
                Particle.DustTransition ghost = new Particle.DustTransition(
                        Color.fromRGB(150, 220, 255), Color.fromRGB(245, 252, 255), 0.72f);
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 4,
                        spread, spread * 0.85, spread, 0.005, ghost, hand.isForceParticles());
                world.spawnParticle(Particle.SOUL, at, 2,
                        spread * 0.75, spread * 0.85, spread * 0.75, 0.006, null, hand.isForceParticles());
            }
            if (tick % 7 == 0) {
                world.spawnParticle(Particle.END_ROD, at, 1,
                        spread * 0.55, spread * 0.70, spread * 0.55, 0.004, null, hand.isForceParticles());
            }
            return;
        }

        if (HandPalette.isViolet(hand.getBaseColor())) {
            if (tick % 4 == 0) {
                Particle.DustTransition violet = new Particle.DustTransition(
                        Color.fromRGB(42, 6, 84), Color.fromRGB(154, 62, 238), 0.70f);
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 4,
                        spread, spread * 0.80, spread, 0.003, violet, hand.isForceParticles());
            }
            return;
        }

        if (HandPalette.isCrimson(hand.getBaseColor())) {
            if (tick % 3 == 0) {
                Particle.DustTransition blood = new Particle.DustTransition(
                        Color.fromRGB(2, 0, 0), Color.fromRGB(175, 4, 24), 0.82f);
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 6,
                        spread, spread * 0.85, spread, 0.004, blood, hand.isForceParticles());
            }
            if (tick % 5 == 0) {
                world.spawnParticle(Particle.ASH, at, 3,
                        spread * 0.82, spread * 0.72, spread * 0.82, 0.008, null, hand.isForceParticles());
            }
        }
    }

    private void renderPhaseAccent(ParticleHand hand) {
        String phase = hand.getActionPhaseName();
        if (phase == null || phase.isEmpty()) return;

        if ((phase.contains("windup") || phase.contains("close") || phase.contains("stage")) && tick % 3 == 0) {
            Location at = hand.getLocation();
            double spread = Math.max(0.14, Math.min(0.65, hand.getScale() * 0.095));
            at.getWorld().spawnParticle(
                    Particle.DUST,
                    at,
                    3,
                    spread, spread, spread,
                    0.0,
                    new Particle.DustOptions(hand.getBaseColor(), 0.62f),
                    hand.isForceParticles()
            );
        }

        if ((phase.contains("strike") || phase.contains("descend") || phase.contains("dash")) && tick % 2 == 0) {
            Location at = hand.getLocation();
            double spread = Math.max(0.08, Math.min(0.38, hand.getScale() * 0.05));
            at.getWorld().spawnParticle(
                    Particle.CLOUD,
                    at,
                    2,
                    spread, spread, spread,
                    0.02,
                    null,
                    hand.isForceParticles()
            );
        }
    }

    private void renderActionAccent(ParticleHand hand) {
        HandActionType type = hand.getActionType();
        if (type == null || type == HandActionType.IDLE) return;

        switch (type) {
            case JUGGLE -> renderJuggleFlight(hand);
            case BLESS, SANCTUARY -> renderBenevolentAccent(hand);
            case GUARD -> renderGuardAccent(hand);
            case RAGE, CYCLONE, SMASH, SLAM, PUNCH, FORCE_SLAP, SLAP, SPANK, CLAP, POUND -> renderCombatAccent(hand);
            case JUDGMENT, BREACH, STALK, CHASE, BIRD, GIVE_BIRD, WAVE, THUMBS_UP, THUMBS_DOWN -> renderPresenceAccent(hand);
            default -> {
                // Grab/transport/move/toss/throw/release are already covered by
                // the movement + physical grip accents above.
            }
        }
    }

    private void renderJuggleFlight(ParticleHand hand) {
        if (hand.hasActiveGrip() || tick % 2 != 0) return;
        Player target = hand.getActionTarget();
        if (target == null || !target.isOnline() || target.isDead()) return;

        Location at = target.getLocation().clone().add(0.0, 0.9, 0.0);
        at.getWorld().spawnParticle(
                Particle.CLOUD,
                at,
                2,
                0.20, 0.28, 0.20,
                0.01,
                null,
                hand.isForceParticles()
        );
        if (tick % 4 == 0) {
            at.getWorld().spawnParticle(
                    Particle.END_ROD,
                    at,
                    1,
                    0.22, 0.32, 0.22,
                    0.01,
                    null,
                    hand.isForceParticles()
            );
        }
    }

    private void renderBenevolentAccent(ParticleHand hand) {
        if (tick % 4 != 0) return;
        Location at = hand.hasActiveGrip() ? hand.getActiveGripWorldPoint() : hand.getLocation();
        if (at == null || at.getWorld() == null) return;
        double spread = Math.max(0.18, Math.min(0.70, hand.getScale() * 0.10));
        at.getWorld().spawnParticle(
                Particle.END_ROD,
                at,
                2,
                spread, spread, spread,
                0.012,
                null,
                hand.isForceParticles()
        );
    }

    private void renderGuardAccent(ParticleHand hand) {
        if (tick % 5 != 0) return;
        Location at = hand.getLocation();
        at.getWorld().spawnParticle(
                Particle.DUST,
                at,
                3,
                0.14, 0.18, 0.14,
                0.0,
                new Particle.DustOptions(hand.getBaseColor(), 0.48f),
                hand.isForceParticles()
        );
    }

    private void renderCombatAccent(ParticleHand hand) {
        if (tick % 4 != 0) return;
        Location at = hand.getLocation();
        double spread = Math.max(0.12, Math.min(0.55, hand.getScale() * 0.075));
        at.getWorld().spawnParticle(
                Particle.DUST,
                at,
                2,
                spread, spread, spread,
                0.0,
                new Particle.DustOptions(hand.getBaseColor(), 0.58f),
                hand.isForceParticles()
        );
    }

    private void renderPresenceAccent(ParticleHand hand) {
        if (tick % 7 != 0) return;
        Location at = hand.getLocation();
        double spread = Math.max(0.12, Math.min(0.45, hand.getScale() * 0.065));
        at.getWorld().spawnParticle(
                Particle.CLOUD,
                at,
                1,
                spread, spread * 0.65, spread,
                0.005,
                null,
                hand.isForceParticles()
        );
    }
}
