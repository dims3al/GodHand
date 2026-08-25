package xyz.dimseal.godHand.hand;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Applies the custom death message recorded by the attack that actually killed the player. */
public final class HandDeathListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        HandDeathMessages.Style style = HandDeathMessages.consume(event.getEntity());
        if (style != null) {
            event.setDeathMessage(HandDeathMessages.message(event.getEntity(), style));
        }
    }
}
