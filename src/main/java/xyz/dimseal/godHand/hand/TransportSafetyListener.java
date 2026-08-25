package xyz.dimseal.godHand.hand;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;

/**
 * Physical-grip collision protection.
 *
 * The grip carrier is intentionally allowed to pass through terrain. While a
 * player is physically mounted in any Hand action, cancel only damage caused by
 * forced collision/fall state. PvP, projectiles, explosions, lava, fire, void,
 * etc. remain normal damage sources.
 */
public final class TransportSafetyListener implements Listener {

    private final HandManager handManager;

    public TransportSafetyListener(HandManager handManager) {
        this.handManager = handManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransportCollisionDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ParticleHand hand = handManager.getHand();
        if (hand == null || !hand.isProtectedCarryTravel(player)) return;

        if (isTransportCollisionCause(event.getCause())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGripDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.getDismounted().getScoreboardTags().contains("godhand_grip_carrier")) return;

        ParticleHand hand = handManager.getHand();
        if (hand == null || !hand.hasActiveGrip()) return;
        Player held = hand.getActionTarget();
        if (held == null || !held.getUniqueId().equals(player.getUniqueId())) return;

        // Shift-dismount should not create a one-tick escape/pop outside the claw.
        // Intentional Hand releases clear the carrier reference before ejecting,
        // so those dismounts are not cancelled here.
        event.setCancelled(true);
    }

    static boolean isTransportCollisionCause(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case SUFFOCATION, FLY_INTO_WALL, CONTACT, CRAMMING, FALLING_BLOCK, FALL -> true;
            default -> false;
        };
    }
}
