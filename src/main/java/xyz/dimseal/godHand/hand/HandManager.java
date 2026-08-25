package xyz.dimseal.godHand.hand;

import org.bukkit.Location;
import org.bukkit.Particle;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import xyz.dimseal.godHand.hand.render.HandRenderMode;

/**
 * Owns the main Hand plus the optional temporary secondary Hand.
 *
 * ItemDisplays update at the engine's full 20 Hz and use client interpolation;
 * particle rendering keeps the older cadence/budget controls. This removes the
 * 5/10 Hz visual stepping that made the solid renderer appear janky without
 * increasing the entity count.
 */
public final class HandManager {

    private static final int NORMAL_PARTICLE_RENDER_INTERVAL_TICKS = 2;
    private static final int LONG_CARRY_PARTICLE_RENDER_INTERVAL_TICKS = 4;
    private static final int PERSISTENT_PARTICLE_RENDER_INTERVAL_TICKS = 4;
    private static final int DISPLAY_RUNAWAY_LIMIT = 256;

    private final ParticleHandRenderer particleRenderer = new ParticleHandRenderer();
    private final ItemDisplayHandRenderer itemDisplayRenderer = new ItemDisplayHandRenderer();
    private final ParticleHandRenderer secondaryParticleRenderer = new ParticleHandRenderer();
    private final ItemDisplayHandRenderer secondaryItemDisplayRenderer = new ItemDisplayHandRenderer();
    private final VoidAuraRenderer voidAuraRenderer = new VoidAuraRenderer();
    private final HandPolishRenderer polishRenderer = new HandPolishRenderer();

    private HandRenderMode lastRenderMode = HandRenderMode.ITEM_DISPLAYS;
    private HandRenderMode lastSecondaryRenderMode = HandRenderMode.ITEM_DISPLAYS;
    private ParticleHand hand;
    private int particleRenderTick;
    private int secondaryParticleRenderTick;
    private int auraTick;
    private int displayMaintenanceTick;

    public void tick() {
        ParticleHand current = hand;
        if (current == null) {
            clearSecondaryVisuals();
            polishRenderer.reset();
            if (++displayMaintenanceTick >= 40) {
                displayMaintenanceTick = 0;
                cleanupTaggedDisplayOrphans(Set.of());

                // With no live Hand, every tagged display is stale. The normal UUID
                // cleanup is cheap, while this infrequent loaded-world scan catches
                // any entity that somehow escaped the renderer registry entirely.
                int tagged = ItemDisplayHandRenderer.countTaggedDisplaysServerWide();
                if (tagged > 0) {
                    int removed = ItemDisplayHandRenderer.purgeTaggedDisplaysServerWide();
                    org.bukkit.Bukkit.getLogger().warning("[GodHand] Removed " + removed
                            + " stale tagged ItemDisplay" + (removed == 1 ? "" : "s") + " while no Hand was active.");
                }
            }
            return;
        }

        current.tick();

        // Hard safety net. A legitimate one/two-Hand model is only tens of displays.
        // If the renderer registry ever grows into the hundreds, purge immediately
        // before another render tick can compound the failure.
        if (ItemDisplayHandRenderer.getKnownDisplayCount() > DISPLAY_RUNAWAY_LIMIT
                || itemDisplayRenderer.getActiveDisplayCount() > DISPLAY_RUNAWAY_LIMIT
                || secondaryItemDisplayRenderer.getActiveDisplayCount() > DISPLAY_RUNAWAY_LIMIT) {
            int removed = emergencyPurgeItemDisplays(false);
            org.bukkit.Bukkit.getLogger().severe("[GodHand] ItemDisplay runaway watchdog tripped; purged " + removed + " tagged displays.");
        }

        if (current.isRemovalRequested()) {
            removeHand();
            return;
        }

        renderPrimary(current);
        renderSecondary(current, current.getSecondaryHand(), current.hasActiveGrip());
        polishRenderer.render(current);

        if (current.isDismissing()) renderDismissDust(current);
        ParticleHand secondaryDismiss = current.getSecondaryHand();
        if (secondaryDismiss != null && secondaryDismiss.isDismissing()) renderDismissDust(secondaryDismiss);

        auraTick++;
        if ((auraTick & 1) == 0) {
            voidAuraRenderer.render(current);
            ParticleHand secondary = current.getSecondaryHand();
            if (secondary != null) voidAuraRenderer.render(secondary);
        }

        // Orphan/runaway maintenance. Every two seconds, prune registry-owned
        // orphans and perform one loaded-world tagged-count sanity check. This stays
        // outside the normal 20 Hz render path while still bounding catastrophic leaks.
        if (++displayMaintenanceTick >= 40) {
            displayMaintenanceTick = 0;
            cleanupTaggedDisplayOrphans(activeDisplayIds());

            // Registry-independent failsafe. If a future renderer regression leaks
            // tagged displays without keeping their UUIDs, the regular watchdog above
            // cannot see them. A loaded-world count every two seconds keeps that class
            // of failure bounded instead of allowing tens of thousands of entities.
            int tagged = ItemDisplayHandRenderer.countTaggedDisplaysServerWide();
            if (tagged > DISPLAY_RUNAWAY_LIMIT) {
                int removed = emergencyPurgeItemDisplays(true);
                org.bukkit.Bukkit.getLogger().severe("[GodHand] Server-wide ItemDisplay runaway detected ("
                        + tagged + " tagged); purged " + removed + " and rebuilt the live Hand visuals.");
            }
        }
    }

    private void renderPrimary(ParticleHand current) {
        HandRenderMode mode = current.getRenderMode();
        if (mode != lastRenderMode) {
            particleRenderer.resetExposure();
            itemDisplayRenderer.clear();
            lastRenderMode = mode;
            particleRenderTick = 0;
        }

        if (mode == HandRenderMode.ITEM_DISPLAYS) {
            // Full 20 Hz entity targets + 2/3 tick client interpolation is much
            // smoother than the old 5/10 Hz teleport stepping.
            itemDisplayRenderer.render(current, current.hasActiveGrip(), current.getVisualRenderLocationFor(current));
            return;
        }

        particleRenderTick++;
        int interval = current.isLongCarryCruise()
                ? LONG_CARRY_PARTICLE_RENDER_INTERVAL_TICKS
                : (current.isPersistentPresence()
                    ? PERSISTENT_PARTICLE_RENDER_INTERVAL_TICKS
                    : NORMAL_PARTICLE_RENDER_INTERVAL_TICKS);
        if (particleRenderTick >= interval) {
            particleRenderTick = 0;
            particleRenderer.render(current);
        }
    }

    private void renderSecondary(ParticleHand primary, ParticleHand secondary, boolean primaryActionHasGrip) {
        if (secondary == null) {
            clearSecondaryVisuals();
            return;
        }

        HandRenderMode mode = secondary.getRenderMode();
        if (mode != lastSecondaryRenderMode) {
            secondaryParticleRenderer.resetExposure();
            secondaryItemDisplayRenderer.clear();
            lastSecondaryRenderMode = mode;
            secondaryParticleRenderTick = 0;
        }

        if (mode == HandRenderMode.ITEM_DISPLAYS) {
            // Toss/Rage/Spank secondary visuals share the primary action
            // controller. If that controller owns a mounted player, keep the
            // temporary Hand on the same low-latency root profile as the carrier.
            secondaryItemDisplayRenderer.render(secondary, primaryActionHasGrip, primary.getVisualRenderLocationFor(secondary));
            return;
        }

        secondaryParticleRenderTick++;
        if (secondaryParticleRenderTick >= NORMAL_PARTICLE_RENDER_INTERVAL_TICKS) {
            secondaryParticleRenderTick = 0;
            secondaryParticleRenderer.render(secondary);
        }
    }

    private void renderDismissDust(ParticleHand dissolving) {
        if ((auraTick & 1) != 0 || dissolving.getWorld() == null) return;
        double spread = Math.max(0.22, dissolving.getScale() * 0.24);
        float size = (float) Math.max(0.45, Math.min(1.55, dissolving.getScale() * 0.18));
        dissolving.getWorld().spawnParticle(
                Particle.DUST,
                dissolving.getLocation(),
                34,
                spread, spread * 0.75, spread,
                0.025,
                new Particle.DustOptions(dissolving.getBaseColor(), size),
                dissolving.isForceParticles()
        );
    }

    public ParticleHand createHand(Location location, double palmWidth) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Hand spawn location must have a world.");
        }
        if (hand != null && hand.getWorld() != null && !hand.getWorld().equals(location.getWorld())) {
            throw new IllegalArgumentException("Cross-dimensional Hand movement is disabled. Despawn the current Hand before creating it in another world.");
        }
        if (hand != null) {
            hand.dispose();
        }
        hand = new ParticleHand(location, palmWidth);
        particleRenderer.resetExposure();
        itemDisplayRenderer.clear();
        clearSecondaryVisuals();
        lastRenderMode = hand.getRenderMode();
        hand.startIdle();
        particleRenderTick = 0;
        auraTick = 0;
        displayMaintenanceTick = 0;
        polishRenderer.reset();
        return hand;
    }


    /** user-facing despawn: animate ItemDisplay Hands before hard cleanup. */
    public boolean despawnHand() {
        if (hand == null) return false;
        hand.cancelAction();
        hand.requestRemoval();
        return true;
    }

    public boolean removeHand() {
        if (hand == null) {
            clearSecondaryVisuals();
            return false;
        }

        hand.dispose();
        hand = null;
        particleRenderer.resetExposure();
        itemDisplayRenderer.clear();
        clearSecondaryVisuals();
        cleanupTaggedDisplayOrphans(Set.of());
        particleRenderTick = 0;
        auraTick = 0;
        displayMaintenanceTick = 0;
        polishRenderer.reset();
        return true;
    }

    private void clearSecondaryVisuals() {
        secondaryParticleRenderer.resetExposure();
        secondaryItemDisplayRenderer.clear();
        secondaryParticleRenderTick = 0;
    }


    /**
     * Forces the solid backend to be rebuilt immediately. Main commands call
     * this before starting a new visible behavior so a logical Hand can never
     * continue attacking with a stale/missing ItemDisplay set. Particle mode
     * only resets its viewer exposure bookkeeping.
     */
    public void refreshVisuals() {
        ParticleHand current = hand;
        particleRenderer.resetExposure();
        itemDisplayRenderer.clear();
        clearSecondaryVisuals();
        particleRenderTick = 0;
        auraTick = 0;
        if (current != null && current.getRenderMode() == HandRenderMode.ITEM_DISPLAYS) {
            itemDisplayRenderer.render(current, current.hasActiveGrip(), current.getVisualRenderLocationFor(current));
        }
    }

    public ParticleHand getHand() {
        return hand;
    }

    public int getPrimaryItemDisplayCount() {
        return itemDisplayRenderer.getActiveDisplayCount();
    }

    public int getSecondaryItemDisplayCount() {
        return secondaryItemDisplayRenderer.getActiveDisplayCount();
    }

    public boolean hasHand() {
        return hand != null;
    }

    public void clear() {
        if (hand != null) {
            hand.dispose();
        }
        hand = null;
        particleRenderer.resetExposure();
        itemDisplayRenderer.clear();
        clearSecondaryVisuals();
        cleanupTaggedDisplayOrphans(Set.of());
        particleRenderTick = 0;
        auraTick = 0;
        displayMaintenanceTick = 0;
        polishRenderer.reset();
    }


    public int getKnownItemDisplayCount() {
        return ItemDisplayHandRenderer.getKnownDisplayCount();
    }

    public int getDisplayRunawayLimit() {
        return DISPLAY_RUNAWAY_LIMIT;
    }

    public int countTaggedItemDisplaysServerWide() {
        return ItemDisplayHandRenderer.countTaggedDisplaysServerWide();
    }

    /**
     * Emergency recovery path for future ItemDisplay renderer regressions.
     * It clears renderer ownership first, then scans loaded worlds by scoreboard tag
     * so leaked entities are removed even if they escaped the in-memory UUID registry.
     */
    public int emergencyPurgeItemDisplays(boolean rebuildCurrentHand) {
        int removed = ItemDisplayHandRenderer.purgeTaggedDisplaysServerWide();
        itemDisplayRenderer.clear();
        secondaryItemDisplayRenderer.clear();
        particleRenderer.resetExposure();
        secondaryParticleRenderer.resetExposure();
        particleRenderTick = 0;
        secondaryParticleRenderTick = 0;
        displayMaintenanceTick = 0;

        ParticleHand current = hand;
        if (rebuildCurrentHand && current != null && current.getRenderMode() == HandRenderMode.ITEM_DISPLAYS) {
            itemDisplayRenderer.render(current, current.hasActiveGrip(), current.getVisualRenderLocationFor(current));
            ParticleHand secondary = current.getSecondaryHand();
            if (secondary != null && secondary.getRenderMode() == HandRenderMode.ITEM_DISPLAYS) {
                secondaryItemDisplayRenderer.render(secondary, current.hasActiveGrip(), current.getVisualRenderLocationFor(secondary));
            }
        }
        return removed;
    }

    private Set<UUID> activeDisplayIds() {
        Set<UUID> ids = new HashSet<>(itemDisplayRenderer.getTrackedDisplayIds());
        ids.addAll(secondaryItemDisplayRenderer.getTrackedDisplayIds());
        return ids;
    }

    private void cleanupTaggedDisplayOrphans(Set<UUID> keep) {
        ItemDisplayHandRenderer.cleanupKnownOrphans(keep);
    }
}
