package xyz.dimseal.godHand.hand;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Wolf-like Guard targeting: retaliate against anything that attacks the
 * guarded player, and assist against anything the guarded player attacks.
 */
public final class GuardCombatListener implements Listener {

    private final HandManager handManager;

    public GuardCombatListener(HandManager handManager) {
        this.handManager = handManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        ParticleHand hand = handManager.getHand();
        if (hand == null || !hand.isGuarding()) return;

        Player owner = hand.getGuardOwner();
        if (owner == null || !owner.isOnline()) return;

        LivingEntity damager = resolveLivingDamager(event.getDamager());
        Entity victim = event.getEntity();

        // Something hurt the guarded player: retaliate and stay locked until it dies.
        if (victim instanceof Player player
                && player.getUniqueId().equals(owner.getUniqueId())
                && damager != null) {
            hand.guardAggro(damager);
            return;
        }

        // Guarded player attacked something: assist exactly like a tamed wolf.
        if (damager instanceof Player player
                && player.getUniqueId().equals(owner.getUniqueId())
                && victim instanceof LivingEntity living) {
            hand.guardAggro(living);
        }
    }

    private static LivingEntity resolveLivingDamager(Entity damager) {
        if (damager instanceof LivingEntity living) return living;
        if (damager instanceof Projectile projectile) {
            Object shooter = projectile.getShooter();
            if (shooter instanceof LivingEntity living) return living;
        }
        return null;
    }
}
